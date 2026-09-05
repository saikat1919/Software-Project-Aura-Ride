"""
Ride, location updates, payment — PROJECT_SPEC §8, §9, §11.
Locations are plain lat/lng floats (no PostGIS). Matching = haversine in Python.
"""
from django.conf import settings
from django.db import models

from accounts.models import PaymentMethod


class RideStatus(models.TextChoices):
    REQUESTED = "REQUESTED"
    MATCHING = "MATCHING"
    ACCEPTED = "ACCEPTED"
    ARRIVING = "ARRIVING"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"


class CancelledBy(models.TextChoices):
    PASSENGER = "PASSENGER"
    DRIVER = "DRIVER"
    SYSTEM = "SYSTEM"


class Ride(models.Model):
    passenger = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="rides_as_passenger")
    driver = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name="rides_as_driver")
    status = models.CharField(max_length=12, choices=RideStatus.choices, default=RideStatus.REQUESTED)

    pickup_lat = models.FloatField()
    pickup_lng = models.FloatField()
    pickup_address = models.CharField(max_length=255, blank=True)
    dropoff_lat = models.FloatField()
    dropoff_lng = models.FloatField()
    dropoff_address = models.CharField(max_length=255, blank=True)

    estimated_fare = models.IntegerField(default=0)
    final_fare = models.IntegerField(null=True, blank=True)
    estimated_distance_km = models.FloatField(default=0)
    estimated_duration_min = models.IntegerField(default=0)

    payment_method = models.CharField(max_length=6, choices=PaymentMethod.choices, default=PaymentMethod.BKASH)

    # Current sequential offer (§8.2). tried_driver_ids avoids re-offering a driver.
    offered_driver = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name="ride_offers")
    offer_expires_at = models.DateTimeField(null=True, blank=True)
    tried_driver_ids = models.JSONField(default=list, blank=True)

    requested_at = models.DateTimeField(auto_now_add=True)
    accepted_at = models.DateTimeField(null=True, blank=True)
    started_at = models.DateTimeField(null=True, blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)
    cancelled_at = models.DateTimeField(null=True, blank=True)
    cancelled_by = models.CharField(max_length=10, choices=CancelledBy.choices, null=True, blank=True)


class RideLocationUpdate(models.Model):
    ride = models.ForeignKey(Ride, on_delete=models.CASCADE, related_name="location_updates")
    lat = models.FloatField()
    lng = models.FloatField()
    recorded_at = models.DateTimeField(auto_now_add=True)


class PaymentStatus(models.TextChoices):
    PENDING = "PENDING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class PaymentMethodStored(models.TextChoices):
    BKASH_MOCK = "BKASH_MOCK"
    CASH = "CASH"


class Payment(models.Model):
    ride = models.OneToOneField(Ride, on_delete=models.CASCADE, related_name="payment")
    amount = models.IntegerField()
    method = models.CharField(max_length=12, choices=PaymentMethodStored.choices)
    status = models.CharField(max_length=10, choices=PaymentStatus.choices, default=PaymentStatus.PENDING)
    gateway_reference = models.CharField(max_length=64, blank=True)  # mock txn id; blank for cash
    confirmed_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name="cash_confirmations")
    paid_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
