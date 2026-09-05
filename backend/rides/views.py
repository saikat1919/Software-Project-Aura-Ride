"""
Ride lifecycle + polling + payment endpoints — PROJECT_SPEC §12.3/§12.4/§12.5.
Biometric-gated actions reuse the accounts verifier (Issue 5); matching is lazy
(Issue 2); transitions are guarded first-accept-wins (§17.8).
"""
from datetime import timedelta

from django.conf import settings
from django.db.models import Q
from django.utils import timezone
from rest_framework import status as http
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from accounts.models import ActionType, PaymentMethod, Role
from accounts.permissions import IsFullyVerified
from accounts.services.biometric import BiometricError, verify_action_signature
from rides.models import (
    CancelledBy, Payment, PaymentMethodStored, PaymentStatus, Ride,
    RideLocationUpdate, RideStatus,
)
from rides.serializers import serialize_ride
from rides.services import fare, matching
from rides.services.fare import haversine_km


def _pt(data, key):
    """Accept nested {'pickup': {'lat','lng'}} or flat pickup_lat/pickup_lng."""
    node = data.get(key)
    if isinstance(node, dict):
        return float(node["lat"]), float(node["lng"]), node.get("address", "")
    return float(data[f"{key}_lat"]), float(data[f"{key}_lng"]), data.get(f"{key}_address", "")


def _is_driver(user):
    return user.role == Role.DRIVER


def _ride_or_none(ride_id):
    return Ride.objects.filter(pk=ride_id).select_related("driver", "passenger").first()


def _driver_far_from(dp, lat, lng) -> bool:
    """True if the driver has a FRESH fix that's beyond GEOFENCE_METERS of (lat,lng).
    A missing/stale fix returns False — we don't block a driver whose phone can't report
    GPS (demo devices), only one we can prove is somewhere else."""
    if dp is None or dp.current_lat is None or dp.last_location_at is None:
        return False
    if timezone.now() - dp.last_location_at > timedelta(minutes=5):
        return False
    meters = haversine_km(dp.current_lat, dp.current_lng, lat, lng) * 1000
    return meters > settings.GEOFENCE_METERS


# ---------------- Passenger ----------------

class EstimateView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        p_lat, p_lng, _ = _pt(request.data, "pickup")
        d_lat, d_lng, _ = _pt(request.data, "dropoff")
        dist, dur, f = fare.estimate(p_lat, p_lng, d_lat, d_lng)
        return Response({"estimated_fare": f, "distance": dist, "duration": dur})


class RequestRideView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request):
        d = request.data
        try:  # biometric (REQUEST_RIDE has no ride_id yet — Issue 3 footnote)
            verify_action_signature(request.user, ActionType.REQUEST_RIDE,
                                    d.get("nonce"), d.get("signature"))
        except BiometricError as e:
            return Response({"error": str(e)}, status=http.HTTP_401_UNAUTHORIZED)

        # One live ride per passenger: retire any prior non-terminal ride they abandoned,
        # so it can't linger as a stale MATCHING offer confusing the driver's queue.
        Ride.objects.filter(
            passenger=request.user,
            status__in=(RideStatus.REQUESTED, RideStatus.MATCHING),
        ).update(status=RideStatus.CANCELLED, cancelled_by=CancelledBy.PASSENGER,
                 cancelled_at=timezone.now(), offered_driver=None, offer_expires_at=None)

        p_lat, p_lng, p_addr = _pt(d, "pickup")
        dp_lat, dp_lng, dp_addr = _pt(d, "dropoff")
        dist, dur, f = fare.estimate(p_lat, p_lng, dp_lat, dp_lng)
        method = (d.get("payment_method") or PaymentMethod.BKASH).upper()
        ride = Ride.objects.create(
            passenger=request.user, status=RideStatus.REQUESTED,
            pickup_lat=p_lat, pickup_lng=p_lng, pickup_address=p_addr,
            dropoff_lat=dp_lat, dropoff_lng=dp_lng, dropoff_address=dp_addr,
            estimated_fare=f, estimated_distance_km=dist, estimated_duration_min=dur,
            payment_method=method if method in PaymentMethod.values else PaymentMethod.BKASH,
        )
        ride = matching.begin(ride)
        return Response(serialize_ride(ride), status=http.HTTP_201_CREATED)


class RideDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, ride_id):
        ride = _ride_or_none(ride_id)
        if not ride or request.user.id not in (ride.passenger_id, ride.driver_id, ride.offered_driver_id):
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        ride = matching.advance_offer_if_expired(ride)  # lazy tick (Issue 2)
        return Response(serialize_ride(ride))


class DriverLocationForRideView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, ride_id):
        ride = _ride_or_none(ride_id)
        if not ride or request.user.id not in (ride.passenger_id, ride.driver_id):
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        dp = getattr(ride.driver, "driver_profile", None) if ride.driver_id else None
        if not dp or dp.current_lat is None:
            return Response({"lat": None, "lng": None, "ts": None})
        return Response({"lat": dp.current_lat, "lng": dp.current_lng, "ts": dp.last_location_at})


class CancelRideView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, ride_id):
        ride = _ride_or_none(ride_id)
        if not ride or request.user.id not in (ride.passenger_id, ride.driver_id, ride.offered_driver_id):
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        who = CancelledBy.PASSENGER if request.user.id == ride.passenger_id else CancelledBy.DRIVER
        cancellable = (RideStatus.REQUESTED, RideStatus.MATCHING, RideStatus.ACCEPTED, RideStatus.ARRIVING)
        rows = Ride.objects.filter(pk=ride.id, status__in=cancellable).update(
            status=RideStatus.CANCELLED, cancelled_by=who, cancelled_at=timezone.now(),
            offered_driver=None, offer_expires_at=None)
        if rows != 1:
            return Response({"error": "not_cancellable"}, status=http.HTTP_409_CONFLICT)
        return Response({"status": RideStatus.CANCELLED})


class RideHistoryView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        rides = Ride.objects.filter(
            Q(passenger=request.user) | Q(driver=request.user)
        ).order_by("-requested_at")[:50]
        return Response([serialize_ride(r) for r in rides])


# ---------------- Driver ----------------

class DriverOnlineView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        dp = request.user.driver_profile
        dp.is_online = bool(request.data.get("is_online", True))
        if request.data.get("lat") is not None:
            dp.current_lat = float(request.data["lat"])
            dp.current_lng = float(request.data["lng"])
            dp.last_location_at = timezone.now()
        dp.save()
        return Response({"is_online": dp.is_online})


class DriverLocationView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        dp = request.user.driver_profile
        dp.current_lat = float(request.data["lat"])
        dp.current_lng = float(request.data["lng"])
        dp.last_location_at = timezone.now()
        dp.save(update_fields=["current_lat", "current_lng", "last_location_at"])
        return Response({"ok": True})


class DriverOffersView(APIView):
    permission_classes = [IsFullyVerified]

    def get(self, request):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        # Advance any stale offers pointed at me, then return the one still live (Issue 2).
        for ride in Ride.objects.filter(offered_driver=request.user, status=RideStatus.MATCHING):
            matching.advance_offer_if_expired(ride)
        ride = Ride.objects.filter(
            offered_driver=request.user, status=RideStatus.MATCHING).order_by("-requested_at").first()
        return Response(serialize_ride(ride) if ride else None)


class AcceptRideView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request, ride_id):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        ride = _ride_or_none(ride_id)
        if not ride:
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        try:  # signature bound to THIS ride (Issue 3)
            verify_action_signature(request.user, ActionType.ACCEPT_RIDE,
                                    request.data.get("nonce"), request.data.get("signature"),
                                    ride_id=ride.id)
        except BiometricError as e:
            return Response({"error": str(e)}, status=http.HTTP_401_UNAUTHORIZED)
        # First-accept-wins: only the currently-offered driver, only while MATCHING (§17.8).
        rows = Ride.objects.filter(
            pk=ride.id, status=RideStatus.MATCHING, offered_driver=request.user).update(
            status=RideStatus.ACCEPTED, driver=request.user, accepted_at=timezone.now(),
            offered_driver=None, offer_expires_at=None)
        if rows != 1:
            return Response({"error": "offer_expired"}, status=http.HTTP_409_CONFLICT)
        dp = request.user.driver_profile
        dp.is_available = False
        dp.save(update_fields=["is_available"])
        return Response(serialize_ride(_ride_or_none(ride.id)))


class _Transition(APIView):
    permission_classes = [IsFullyVerified]
    frm = to = None

    def post(self, request, ride_id):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        updates = {}
        if self.to == RideStatus.IN_PROGRESS:
            updates["started_at"] = timezone.now()
        # Guarded transition (§17.8): only the assigned driver, only from the expected state.
        rows = Ride.objects.filter(
            pk=ride_id, status=self.frm, driver=request.user).update(status=self.to, **updates)
        if rows != 1:
            return Response({"error": "bad_transition"}, status=http.HTTP_409_CONFLICT)
        return Response({"status": self.to})


class ArriveView(_Transition):
    frm, to = RideStatus.ACCEPTED, RideStatus.ARRIVING

    def post(self, request, ride_id):
        # Can't claim "arrived" unless actually near the pickup (§8.1). Demo-safe: both test
        # phones sit together at the pickup, so this passes; a driver elsewhere is blocked.
        ride = _ride_or_none(ride_id)
        if ride and ride.driver_id == request.user.id:
            dp = getattr(request.user, "driver_profile", None)
            if _driver_far_from(dp, ride.pickup_lat, ride.pickup_lng):
                return Response({"error": "too_far", "detail": "Get to the pickup point first."},
                                status=http.HTTP_409_CONFLICT)
        return super().post(request, ride_id)


class StartView(_Transition):
    frm, to = RideStatus.ARRIVING, RideStatus.IN_PROGRESS


class CompleteView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request, ride_id):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        ride = _ride_or_none(ride_id)
        if not ride or ride.driver_id != request.user.id:
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        # Optional real enforcement: only complete when at the dropoff. OFF by default so the
        # co-located demo phones (not at the dropoff) can still finish — flip ENFORCE_GEOFENCE on.
        if settings.ENFORCE_GEOFENCE:
            dp = getattr(request.user, "driver_profile", None)
            if _driver_far_from(dp, ride.dropoff_lat, ride.dropoff_lng):
                return Response({"error": "too_far", "detail": "Reach the dropoff before completing."},
                                status=http.HTTP_409_CONFLICT)
        rows = Ride.objects.filter(pk=ride.id, status=RideStatus.IN_PROGRESS).update(
            status=RideStatus.COMPLETED, completed_at=timezone.now(), final_fare=ride.estimated_fare)
        if rows != 1:
            return Response({"error": "bad_transition"}, status=http.HTTP_409_CONFLICT)
        method = (PaymentMethodStored.BKASH_MOCK if ride.payment_method == PaymentMethod.BKASH
                  else PaymentMethodStored.CASH)
        Payment.objects.get_or_create(
            ride=ride, defaults={"amount": ride.estimated_fare, "method": method})
        dp = request.user.driver_profile
        dp.is_available = True
        dp.save(update_fields=["is_available"])
        return Response({"status": RideStatus.COMPLETED, "final_fare": ride.estimated_fare})


class CashPaidView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request, ride_id):
        ride = _ride_or_none(ride_id)
        if not ride or ride.driver_id != request.user.id:
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        pay = getattr(ride, "payment", None)
        if not pay or pay.method != PaymentMethodStored.CASH:
            return Response({"error": "not_a_cash_ride"}, status=http.HTTP_400_BAD_REQUEST)
        pay.status = PaymentStatus.COMPLETED
        pay.confirmed_by = request.user
        pay.paid_at = timezone.now()
        pay.save(update_fields=["status", "confirmed_by", "paid_at"])
        return Response({"status": PaymentStatus.COMPLETED})


class DriverSummaryView(APIView):
    permission_classes = [IsFullyVerified]

    def get(self, request):
        if not _is_driver(request.user):
            return Response({"error": "not_a_driver"}, status=http.HTTP_403_FORBIDDEN)
        completed = Payment.objects.filter(
            ride__driver=request.user, status=PaymentStatus.COMPLETED)
        # localdate() matches the timezone the __date lookup uses (Asia/Dhaka), so a
        # payment made "today" locally isn't miscounted across the UTC boundary.
        today = timezone.localdate()
        today_sum = sum(p.amount for p in completed.filter(paid_at__date=today))
        total = sum(p.amount for p in completed)
        return Response({
            "today_earnings": today_sum, "total_earnings": total,
            "completed_trips": completed.count(),
            "is_online": request.user.driver_profile.is_online,
        })


# ---------------- Payment ----------------

class BkashPayView(APIView):
    permission_classes = [IsFullyVerified]

    def post(self, request, ride_id):
        ride = _ride_or_none(ride_id)
        if not ride or ride.passenger_id != request.user.id:
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        pay = getattr(ride, "payment", None)
        if not pay or pay.method != PaymentMethodStored.BKASH_MOCK:
            return Response({"error": "not_a_bkash_ride"}, status=http.HTTP_400_BAD_REQUEST)
        # Fully internal mock behind the PaymentGateway concept (§9.1). No real money.
        pay.status = PaymentStatus.COMPLETED
        pay.gateway_reference = f"MOCK-{ride.id}-{timezone.now():%H%M%S}"
        pay.paid_at = timezone.now()
        pay.save(update_fields=["status", "gateway_reference", "paid_at"])
        return Response({"status": PaymentStatus.COMPLETED, "reference": pay.gateway_reference})


class PaymentStatusView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, ride_id):
        ride = _ride_or_none(ride_id)
        if not ride or request.user.id not in (ride.passenger_id, ride.driver_id):
            return Response({"error": "not_found"}, status=http.HTTP_404_NOT_FOUND)
        pay = getattr(ride, "payment", None)
        if not pay:
            return Response({"status": None})
        return Response({"status": pay.status, "amount": pay.amount, "method": pay.method,
                         "reference": pay.gateway_reference or None})
