"""
Matching engine + ride transitions (§8.2, §17.4, §17.8) and the fake-routing fare.
These cover the ride-half logic without the biometric layer (covered in accounts.tests).
"""
from datetime import timedelta

from django.test import TestCase
from django.utils import timezone

from accounts.models import DriverProfile, Role, User, UserStatus
from rides.models import Ride, RideStatus
from rides.services import fare, matching

PICKUP = (23.7500, 90.3900)


def active(email, phone, role=Role.PASSENGER):
    return User.objects.create_user(email=email, phone=phone, full_name=email.split("@")[0],
                                    password="pw", role=role, status=UserStatus.ACTIVE)


def online_driver(email, phone, lat, lng):
    u = active(email, phone, role=Role.DRIVER)
    DriverProfile.objects.create(user=u, is_online=True, is_available=True,
                                 current_lat=lat, current_lng=lng, last_location_at=timezone.now())
    return u


def new_ride(passenger):
    return Ride.objects.create(
        passenger=passenger, status=RideStatus.REQUESTED,
        pickup_lat=PICKUP[0], pickup_lng=PICKUP[1],
        dropoff_lat=23.78, dropoff_lng=90.40, estimated_fare=190)


class MatchingTests(TestCase):
    def setUp(self):
        self.passenger = active("p@x.com", "01700")
        self.near = online_driver("near@x.com", "01711", 23.7520, 90.3900)   # ~0.22 km
        self.far = online_driver("far@x.com", "01722", 23.7800, 90.3900)     # ~3.3 km

    def test_begin_offers_nearest(self):
        ride = matching.begin(new_ride(self.passenger))
        self.assertEqual(ride.status, RideStatus.MATCHING)
        self.assertEqual(ride.offered_driver_id, self.near.id)

    def test_no_driver_cancels(self):
        DriverProfile.objects.update(is_online=False)
        ride = matching.begin(new_ride(self.passenger))
        self.assertEqual(ride.status, RideStatus.CANCELLED)

    def test_advance_on_expiry_offers_next(self):
        ride = matching.begin(new_ride(self.passenger))
        self.assertEqual(ride.offered_driver_id, self.near.id)
        ride.offer_expires_at = timezone.now() - timedelta(seconds=1)
        ride.save(update_fields=["offer_expires_at"])
        ride = matching.advance_offer_if_expired(ride)
        self.assertEqual(ride.offered_driver_id, self.far.id)   # advanced to next nearest
        self.assertIn(self.near.id, ride.tried_driver_ids)

    def test_advance_exhausted_cancels(self):
        ride = matching.begin(new_ride(self.passenger))
        for _ in range(3):  # expire + advance until drivers run out
            ride.offer_expires_at = timezone.now() - timedelta(seconds=1)
            ride.save(update_fields=["offer_expires_at"])
            ride = matching.advance_offer_if_expired(ride)
        self.assertEqual(ride.status, RideStatus.CANCELLED)

    def test_first_accept_wins(self):
        ride = matching.begin(new_ride(self.passenger))
        offered = ride.offered_driver  # the near driver

        def claim(driver):
            return Ride.objects.filter(
                pk=ride.id, status=RideStatus.MATCHING, offered_driver=driver).update(
                status=RideStatus.ACCEPTED, driver=driver, offered_driver=None) == 1

        self.assertTrue(claim(offered))       # first wins
        self.assertFalse(claim(offered))      # second (or a stale re-offer) loses

    def test_guarded_transition_sequence(self):
        ride = matching.begin(new_ride(self.passenger))
        Ride.objects.filter(pk=ride.id).update(status=RideStatus.ACCEPTED, driver=self.near)
        self.assertTrue(matching.guarded_transition(ride.id, RideStatus.ACCEPTED, RideStatus.ARRIVING))
        self.assertTrue(matching.guarded_transition(ride.id, RideStatus.ARRIVING, RideStatus.IN_PROGRESS))
        # wrong 'from' state must fail
        self.assertFalse(matching.guarded_transition(ride.id, RideStatus.ACCEPTED, RideStatus.COMPLETED))


class FareTests(TestCase):
    def test_estimate_positive(self):
        dist, dur, f = fare.estimate(*PICKUP, 23.78, 90.40)
        self.assertGreater(dist, 0)
        self.assertGreater(f, 0)
