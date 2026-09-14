"""
Driver matching + ride-state transitions — PROJECT_SPEC §8.2, §17.4, §17.8, §17.9.

No task queue (constraint): the poll endpoints drive the clock. advance_offer_if_expired()
is called on every relevant poll and moves a stale offer to the next nearest driver
(eng review Issue 2). guarded_transition() enforces first-accept-wins (§17.8, Issue 5).
"""
from datetime import timedelta

from django.conf import settings
from django.db import transaction
from django.utils import timezone

from accounts.models import DriverProfile
from rides.models import CancelledBy, Ride, RideStatus
from rides.services.fare import haversine_km


def _nearest_candidate(ride: Ride):
    """Nearest online+available driver within radius, not already tried, not the passenger."""
    tried = set(ride.tried_driver_ids or [])
    tried.add(ride.passenger_id)
    best, best_d = None, None
    qs = DriverProfile.objects.select_related("user").filter(
        is_online=True, is_available=True,
        current_lat__isnull=False, current_lng__isnull=False,
    )
    for dp in qs:
        if dp.user_id in tried:
            continue
        d = haversine_km(ride.pickup_lat, ride.pickup_lng, dp.current_lat, dp.current_lng)
        if d <= settings.MATCH_RADIUS_KM and (best_d is None or d < best_d):
            best, best_d = dp.user, d
    return best


def _offer_to(ride: Ride, driver_user):
    ride.offered_driver = driver_user
    ride.offer_expires_at = timezone.now() + timedelta(seconds=settings.OFFER_TIMEOUT_SECONDS)
    ride.status = RideStatus.MATCHING
    tried = list(ride.tried_driver_ids or [])
    if driver_user.id not in tried:
        tried.append(driver_user.id)
    ride.tried_driver_ids = tried
    ride.save(update_fields=["offered_driver", "offer_expires_at", "status", "tried_driver_ids"])


def begin(ride: Ride) -> Ride:
    """First offer after a request. Cancels immediately if no driver is in range."""
    with transaction.atomic():
        ride = Ride.objects.select_for_update().get(pk=ride.pk)
        driver = _nearest_candidate(ride)
        if driver is None:
            _cancel_no_driver(ride)
        else:
            _offer_to(ride, driver)
    return ride


def advance_offer_if_expired(ride: Ride) -> Ride:
    """Lazy tick, called from GET /rides/{id} and GET /driver/offers (Issue 2)."""
    if ride.status != RideStatus.MATCHING:
        return ride
    now = timezone.now()
    if ride.offered_driver_id and ride.offer_expires_at and ride.offer_expires_at > now:
        return ride  # current offer still live
    with transaction.atomic():
        ride = Ride.objects.select_for_update().get(pk=ride.pk)
        if ride.status != RideStatus.MATCHING:
            return ride
        if ride.offered_driver_id and ride.offer_expires_at and ride.offer_expires_at > timezone.now():
            return ride
        driver = _nearest_candidate(ride)
        if driver is None:
            _cancel_no_driver(ride)
        else:
            _offer_to(ride, driver)
    return ride


def _cancel_no_driver(ride: Ride):
    ride.status = RideStatus.CANCELLED
    ride.cancelled_by = CancelledBy.SYSTEM
    ride.cancelled_at = timezone.now()
    ride.offered_driver = None
    ride.offer_expires_at = None
    ride.save(update_fields=["status", "cancelled_by", "cancelled_at", "offered_driver", "offer_expires_at"])


def guarded_transition(ride_id, expected_status, new_status, **updates) -> bool:
    """
    Atomic conditional status change (§17.8). Returns True iff exactly one row moved
    from expected_status -> new_status. Used so exactly one accept wins and late
    accepts get "offer expired".
    """
    rows = Ride.objects.filter(pk=ride_id, status=expected_status).update(
        status=new_status, **updates)
    return rows == 1
