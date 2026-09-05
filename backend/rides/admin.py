from django.apps import apps
from django.contrib import admin

from rides.models import Payment, Ride


@admin.register(Ride)
class RideAdmin(admin.ModelAdmin):
    list_display = ("id", "passenger", "driver", "status", "payment_method",
                    "estimated_fare", "final_fare", "requested_at")
    list_filter = ("status", "payment_method")
    search_fields = ("passenger__full_name", "driver__full_name")


@admin.register(Payment)
class PaymentAdmin(admin.ModelAdmin):
    list_display = ("id", "ride", "amount", "method", "status", "paid_at")
    list_filter = ("status", "method")


# Friendly sidebar labels; RideLocationUpdate stays hidden (internal live-tracking trail).
apps.get_app_config("rides").verbose_name = "Trips & Payments"
Ride._meta.verbose_name, Ride._meta.verbose_name_plural = "Trip", "Trips"
Payment._meta.verbose_name, Payment._meta.verbose_name_plural = "Payment", "Payments"
