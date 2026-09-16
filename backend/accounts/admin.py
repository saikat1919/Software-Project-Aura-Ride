"""
Verification review — customized Django admin (§10). Human-in-the-loop: the admin
sees the face-match score + OCR flags and makes the call. On approval, raw NID/license
images are deleted (§14); only extracted fields remain.
"""
from django.apps import apps
from django.contrib import admin
from django.contrib.auth.models import Group
from django.utils.html import format_html

from accounts.models import (
    BiometricCredential, DriverProfile, PassengerProfile, User,
    VerificationSubmission, Vehicle,
)
from accounts.services.review import approve_submission, reject_submission


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ("id", "full_name", "email", "phone", "role", "status", "is_phone_verified")
    list_filter = ("role", "status")
    search_fields = ("full_name", "email", "phone")


@admin.register(VerificationSubmission)
class VerificationSubmissionAdmin(admin.ModelAdmin):
    list_display = ("id", "user", "status", "face_match_verdict", "face_match_score",
                    "ocr_gender", "ocr_nid_number", "liveness_passed", "created_at")
    list_filter = ("status", "face_match_verdict", "is_step_up")
    search_fields = ("user__full_name", "user__email")
    readonly_fields = (
        "selfie_preview", "nid_preview", "face_match_score", "face_match_verdict",
        "ocr_name", "ocr_gender", "ocr_nid_number", "ocr_license_name", "ocr_license_number",
        "flags", "liveness_passed", "created_at", "updated_at",
    )
    fieldsets = (
        ("Review", {"fields": ("user", "status", "review_reason")}),
        ("Images", {"fields": ("selfie_preview", "nid_preview")}),
        ("AI signals", {"fields": ("face_match_verdict", "face_match_score", "liveness_passed", "flags")}),
        ("OCR (on-device)", {"fields": ("ocr_name", "ocr_gender", "ocr_nid_number",
                                        "ocr_license_name", "ocr_license_number")}),
        ("Meta", {"fields": ("created_at", "updated_at")}),
    )
    actions = ("approve", "reject")

    @admin.display(description="Selfie")
    def selfie_preview(self, obj):
        if obj.selfie_image:
            return format_html('<img src="{}" style="max-height:280px;border-radius:8px"/>', obj.selfie_image.url)
        return "— (no selfie)"

    @admin.display(description="NID front")
    def nid_preview(self, obj):
        if obj.nid_image_front:
            return format_html('<img src="{}" style="max-height:280px;border-radius:8px"/>', obj.nid_image_front.url)
        return "— (deleted after approval, or not uploaded)"

    @admin.action(description="Approve — activate user, delete raw ID images")
    def approve(self, request, queryset):
        for sub in queryset:
            approve_submission(sub, request.user)
        self.message_user(request, f"Approved {queryset.count()} submission(s).")

    @admin.action(description="Reject — set reason in the change form first, or use default")
    def reject(self, request, queryset):
        for sub in queryset:
            reject_submission(sub, request.user, sub.review_reason)
        self.message_user(request, f"Rejected {queryset.count()} submission(s).")


admin.site.register(PassengerProfile)
admin.site.register(DriverProfile)
admin.site.register(Vehicle)


@admin.register(BiometricCredential)
class BiometricCredentialAdmin(admin.ModelAdmin):
    """Read-only view of each device's PUBLIC key (§17.1). The private key never leaves
    the phone — only this public key is stored, which is what verifies ride signatures."""
    list_display = ("user", "device_id", "is_active", "enrolled_at")
    search_fields = ("user__full_name", "user__email")
    fields = ("user", "device_id", "is_active", "enrolled_at", "public_key_view")
    readonly_fields = ("user", "device_id", "is_active", "enrolled_at", "public_key_view")

    @admin.display(description="Public key (PEM) — the private key never leaves the device")
    def public_key_view(self, obj):
        return format_html(
            '<pre style="white-space:pre-wrap;word-break:break-all;max-width:660px;'
            'font-size:12px;background:var(--darkened-bg,#f6f6f6);padding:12px;'
            'border-radius:8px">{}</pre>', obj.public_key)

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False  # view-only; a public key is never hand-edited

    # Deletable (directly, and so a User delete can cascade through it) — just not editable.


# --- Plain-English sidebar (§10): friendly labels, hide internal/technical models ---
# Hide the raw Django auth "Groups" the reviewer never touches.
try:
    admin.site.unregister(Group)
except admin.sites.NotRegistered:
    pass

apps.get_app_config("accounts").verbose_name = "People & Verification"

_LABELS = {  # (singular, plural) shown in the left nav
    User: ("Account", "Accounts"),
    DriverProfile: ("Driver", "Drivers"),
    PassengerProfile: ("Passenger", "Passengers"),
    Vehicle: ("Vehicle", "Vehicles"),
    VerificationSubmission: ("ID verification", "ID verifications"),
    BiometricCredential: ("Device key", "Device keys"),
}
for _model, (_sg, _pl) in _LABELS.items():
    _model._meta.verbose_name = _sg
    _model._meta.verbose_name_plural = _pl
