from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView

from accounts import views

urlpatterns = [
    # auth & registration (§12.1)
    path("auth/register", views.RegisterView.as_view()),
    path("auth/verify-phone", views.VerifyPhoneView.as_view()),
    path("auth/login", views.LoginView.as_view()),
    path("auth/refresh", TokenRefreshView.as_view()),
    path("auth/challenge", views.ChallengeView.as_view()),
    path("auth/step-up", views.StepUpView.as_view()),
    path("auth/resubmit", views.ResubmitView.as_view()),
    path("auth/enroll-key", views.EnrollKeyView.as_view()),  # re-bind a lost device key (§17.1)
    path("auth/verify-action", views.VerifyActionView.as_view()),  # demo: biometric core
    # profile & status (§12.2)
    path("me", views.MeView.as_view()),
    path("me/verification-status", views.VerificationStatusView.as_view()),
]
