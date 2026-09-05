"""Plain dict serializers — small surface, explicit over a full DRF serializer layer."""
from rides.models import Ride, RideStatus


def driver_block(ride: Ride):
    if not ride.driver_id or ride.status not in (
        RideStatus.ACCEPTED, RideStatus.ARRIVING, RideStatus.IN_PROGRESS, RideStatus.COMPLETED
    ):
        return None
    dp = getattr(ride.driver, "driver_profile", None)
    vehicle = dp.vehicles.first() if dp else None
    return {
        "name": ride.driver.full_name,
        "lat": dp.current_lat if dp else None,
        "lng": dp.current_lng if dp else None,
        "vehicle": (f"{vehicle.make} {vehicle.model}" if vehicle else ""),
        "plate": (vehicle.plate_number if vehicle else ""),
    }


def serialize_ride(ride: Ride) -> dict:
    return {
        "id": ride.id,
        "status": ride.status,
        "passenger": ride.passenger.full_name,
        "pickup": {"lat": ride.pickup_lat, "lng": ride.pickup_lng, "address": ride.pickup_address},
        "dropoff": {"lat": ride.dropoff_lat, "lng": ride.dropoff_lng, "address": ride.dropoff_address},
        "estimated_fare": ride.estimated_fare,
        "final_fare": ride.final_fare,
        "estimated_distance_km": ride.estimated_distance_km,
        "estimated_duration_min": ride.estimated_duration_min,
        "payment_method": ride.payment_method,
        "offer_expires_at": ride.offer_expires_at,
        "driver": driver_block(ride),
    }
