"""
Verification review actions (§10, §14). Single source of truth for what "approve" and
"reject" DO, so the admin dashboard buttons and the changelist actions can't drift apart.

Approve activates the user and deletes raw ID images (data minimization, §14); reject
sets the user to REJECTED with a reason. Both record who reviewed.
"""
from django.utils import timezone

from accounts.models import (
    DriverProfile, SubmissionStatus, UserStatus, VerificationSubmission,
)


def _delete_raw_images(sub: VerificationSubmission):
    """Drop raw ID images after approval — keep only extracted fields + embedding (§14)."""
    for field in ("nid_image_front", "nid_image_back"):
        f = getattr(sub, field, None)
        if f:
            f.delete(save=False)
            setattr(sub, field, None)
    dp = DriverProfile.objects.filter(user=sub.user).first()
    if dp and dp.license_image:
        dp.license_image.delete(save=False)
        dp.license_image = None
        dp.save(update_fields=["license_image"])


def approve_submission(sub: VerificationSubmission, admin_user) -> None:
    sub.status = SubmissionStatus.APPROVED
    sub.reviewed_by = admin_user
    sub.review_reason = f"Approved by {admin_user} at {timezone.now():%Y-%m-%d %H:%M}"
    _delete_raw_images(sub)
    sub.save()
    u = sub.user
    u.status = UserStatus.ACTIVE
    u.save(update_fields=["status"])


def reject_submission(sub: VerificationSubmission, admin_user, reason: str = "") -> None:
    sub.status = SubmissionStatus.REJECTED
    sub.reviewed_by = admin_user
    sub.review_reason = reason.strip() or "Rejected by admin review."
    sub.save()
    u = sub.user
    u.status = UserStatus.REJECTED
    u.save(update_fields=["status"])
