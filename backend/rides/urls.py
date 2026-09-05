from django.urls import path

from rides import views

urlpatterns = [
    # passenger / rides (§12.4)
    path("rides/estimate", views.EstimateView.as_view()),
    path("rides/request", views.RequestRideView.as_view()),
    path("rides/history", views.RideHistoryView.as_view()),
    path("rides/<int:ride_id>", views.RideDetailView.as_view()),
    path("rides/<int:ride_id>/driver-location", views.DriverLocationForRideView.as_view()),
    path("rides/<int:ride_id>/cancel", views.CancelRideView.as_view()),

    # driver (§12.3)
    path("driver/online", views.DriverOnlineView.as_view()),
    path("driver/location", views.DriverLocationView.as_view()),
    path("driver/offers", views.DriverOffersView.as_view()),
    path("driver/summary", views.DriverSummaryView.as_view()),
    path("driver/rides/<int:ride_id>/accept", views.AcceptRideView.as_view()),
    path("driver/rides/<int:ride_id>/arrive", views.ArriveView.as_view()),
    path("driver/rides/<int:ride_id>/start", views.StartView.as_view()),
    path("driver/rides/<int:ride_id>/complete", views.CompleteView.as_view()),
    path("driver/rides/<int:ride_id>/cash-paid", views.CashPaidView.as_view()),

    # payment (§12.5)
    path("payments/<int:ride_id>/bkash", views.BkashPayView.as_view()),
    path("payments/<int:ride_id>", views.PaymentStatusView.as_view()),
]
