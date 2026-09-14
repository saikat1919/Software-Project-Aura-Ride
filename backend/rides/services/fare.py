"""
Distance / duration / fare — PROJECT_SPEC §8.4, §17.10.

Behind the DEMO_FAKE_ROUTING seam so the backend runs with no OSRM server. Real path
calls a self-hosted OSRM /route (one call returns geometry + distance + duration,
replacing Directions AND Distance Matrix). Fake path uses haversine + average speed.
"""
import json
import math
import urllib.request

from django.conf import settings


def haversine_km(lat1, lng1, lat2, lng2) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return r * 2 * math.asin(math.sqrt(a))


def fare_for(distance_km: float, duration_min: float) -> int:
    return round(
        settings.FARE_BASE_BDT
        + distance_km * settings.FARE_PER_KM_BDT
        + duration_min * settings.FARE_PER_MIN_BDT
    )


def estimate(pickup_lat, pickup_lng, dropoff_lat, dropoff_lng):
    """Return (distance_km, duration_min, fare_bdt)."""
    if not settings.DEMO_FAKE_ROUTING:
        try:
            url = (f"{settings.OSRM_BASE_URL}/route/v1/driving/"
                   f"{pickup_lng},{pickup_lat};{dropoff_lng},{dropoff_lat}"
                   f"?overview=false")
            with urllib.request.urlopen(url, timeout=5) as resp:
                data = json.loads(resp.read())
            route = data["routes"][0]
            distance_km = route["distance"] / 1000.0
            duration_min = route["duration"] / 60.0
            return round(distance_km, 2), round(duration_min), fare_for(distance_km, duration_min)
        except Exception:
            pass  # fall through to the haversine estimate if OSRM is unreachable
    distance_km = haversine_km(pickup_lat, pickup_lng, dropoff_lat, dropoff_lng)
    duration_min = distance_km / settings.AVG_SPEED_KMH * 60.0
    return round(distance_km, 2), round(duration_min), fare_for(distance_km, duration_min)
