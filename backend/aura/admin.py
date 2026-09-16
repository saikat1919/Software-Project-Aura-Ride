"""
Aura Ride verification console — a purpose-built admin landing page (§10) on top of the
Django admin, so we keep its auth/permissions/model pages but replace the bland index with
an operations dashboard: live stats + a one-click verification queue.

No SPA (project constraint) — server-rendered. Approve/reject reuse accounts.services.review
so the dashboard buttons and the changelist actions do exactly the same thing.
"""
from django.contrib import admin, messages
from django.db.models import Sum
from django.shortcuts import get_object_or_404, redirect
from django.urls import path
from django.utils import timezone

from accounts.models import (
    DriverProfile, Role, SubmissionStatus, User, UserStatus, VerificationSubmission,
)
from accounts.services.review import approve_submission, reject_submission
from rides.models import Ride, RideStatus


def _humanize_flag(key: str) -> str:
    """'nid_number_missing' -> 'NID number missing' for the dashboard (no raw dicts)."""
    label = str(key).replace("_", " ")
    for acr in ("nid", "ocr"):
        label = label.replace(acr, acr.upper())
    return label[:1].upper() + label[1:]


class AuraAdminSite(admin.AdminSite):
    site_header = "Aura Ride — Verification Console"
    site_title = "Aura Ride Admin"
    index_title = "Operations dashboard"
    index_template = "admin/aura_dashboard.html"

    # --- extra URLs: one-click review actions from the dashboard queue ---
    def get_urls(self):
        custom = [
            path("verify/<int:pk>/approve", self.admin_view(self.approve_view),
                 name="verify_approve"),
            path("verify/<int:pk>/reject", self.admin_view(self.reject_view),
                 name="verify_reject"),
        ]
        return custom + super().get_urls()

    def approve_view(self, request, pk):
        if request.method == "POST":
            sub = get_object_or_404(VerificationSubmission, pk=pk)
            approve_submission(sub, request.user)
            messages.success(request, f"Approved {sub.user.full_name} — account activated.")
        return redirect("admin:index")

    def reject_view(self, request, pk):
        if request.method == "POST":
            sub = get_object_or_404(VerificationSubmission, pk=pk)
            reject_submission(sub, request.user, request.POST.get("reason", ""))
            messages.success(request, f"Rejected {sub.user.full_name}.")
        return redirect("admin:index")

    # --- dashboard data ---
    def index(self, request, extra_context=None):
        today = timezone.localdate()
        completed = Ride.objects.filter(status=RideStatus.COMPLETED)
        queue = list(VerificationSubmission.objects
                     .filter(status=SubmissionStatus.PENDING_REVIEW)
                     .select_related("user").order_by("-created_at")[:25])
        for s in queue:  # annotate for the template (cross-checks the admin would do by eye)
            acct = (s.user.full_name or "").strip().lower()
            ocr = (s.ocr_name or "").strip().lower()
            s.name_mismatch = bool(ocr) and ocr not in acct and acct not in ocr
            s.score_pct = int(round((s.face_match_score or 0) * 100))
            s.flag_list = [_humanize_flag(k) for k, v in (s.flags or {}).items() if v]
        ctx = {
            "stat_pending": VerificationSubmission.objects.filter(
                status=SubmissionStatus.PENDING_REVIEW).count(),
            "stat_active": User.objects.filter(status=UserStatus.ACTIVE).count(),
            "stat_drivers": User.objects.filter(role=Role.DRIVER).count(),
            "stat_online": DriverProfile.objects.filter(is_online=True).count(),
            "stat_rides_today": Ride.objects.filter(requested_at__date=today).count(),
            "stat_completed": completed.count(),
            "stat_revenue": completed.aggregate(s=Sum("final_fare"))["s"] or 0,
            "recent": (VerificationSubmission.objects
                       .exclude(status=SubmissionStatus.PENDING_REVIEW)
                       .select_related("user", "reviewed_by").order_by("-updated_at")[:8]),
            "queue": queue,
        }
        ctx.update(extra_context or {})
        return super().index(request, ctx)
