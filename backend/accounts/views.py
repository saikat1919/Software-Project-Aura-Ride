"""
Auth + registration + biometric endpoints — PROJECT_SPEC §12.1/§12.2.
Folds the eng review decisions: limited pending JWT (Issue 1), ride-bound signature
(Issue 3), shared verifier (Issue 5), synchronous AI behind a seam.
"""
from rest_framework import status as http
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from accounts.models import (
    ActionType, BiometricCredential, DriverProfile, PassengerProfile, Role,
    SubmissionStatus, User, UserStatus, VerificationSubmission, Vehicle,
)
from accounts.permissions import IsFullyVerified
from accounts.services import face_match
from accounts.services.biometric import BiometricError, issue_challenge, verify_action_signature


def run_face_checks(sub, user, selfie_path, nid_path, is_driver):
    """Face match + OCR cross-check + selfie gender estimate (§7.2/§7.3), all saved on `sub`.
    Shared by register and resubmit so the two paths can't drift."""
    sub.face_match_score, sub.face_match_verdict = face_match.score_faces(selfie_path, nid_path)
    sub.flags = face_match.cross_check(
        typed_name=user.full_name, ocr_name=sub.ocr_name, ocr_gender=sub.ocr_gender,
        nid_number=sub.ocr_nid_number, license_name=sub.ocr_license_name, is_driver=is_driver)
    label, conf, is_male = face_match.estimate_gender(selfie_path)
    if label:
        sub.gender_estimate = f"{label} ({conf}%)"
        if is_male:
            sub.flags["face_looks_male"] = True   # women-only (§7.3): admin verifies
    sub.save(update_fields=["face_match_score", "face_match_verdict", "flags", "gender_estimate"])


def make_tokens(user, scope="full"):
    refresh = RefreshToken.for_user(user)
    refresh["scope"] = scope
    access = refresh.access_token
    access["scope"] = scope
    return {"access": str(access), "refresh": str(refresh)}


class RegisterView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        d = request.data
        required = ("email", "phone", "full_name", "password", "role", "public_key")
        missing = [f for f in required if not d.get(f)]
        if missing:
            return Response({"error": "missing_fields", "fields": missing}, status=http.HTTP_400_BAD_REQUEST)
        role = d["role"].upper()
        if role not in Role.values:
            return Response({"error": "bad_role"}, status=http.HTTP_400_BAD_REQUEST)
        if User.objects.filter(email=d["email"]).exists() or User.objects.filter(phone=d["phone"]).exists():
            return Response({"error": "already_registered"}, status=http.HTTP_409_CONFLICT)

        user = User.objects.create_user(
            email=d["email"], phone=d["phone"], full_name=d["full_name"],
            password=d["password"], role=role, status=UserStatus.PENDING_REVIEW,
        )
        BiometricCredential.objects.create(
            user=user, public_key=d["public_key"], device_id=d.get("device_id", ""))

        is_driver = role == Role.DRIVER
        if is_driver:
            dp = DriverProfile.objects.create(user=user, license_image=request.FILES.get("license"))
            if d.get("vehicle_make"):
                Vehicle.objects.create(
                    driver=dp, make=d.get("vehicle_make", ""), model=d.get("vehicle_model", ""),
                    color=d.get("vehicle_color", ""), plate_number=d.get("vehicle_plate", ""))
        else:
            PassengerProfile.objects.create(user=user)

        # Synchronous AI (§17.5). Face match runs in-request behind the DEMO_FAKE_AI seam.
        selfie = request.FILES.get("selfie")
        nid_front = request.FILES.get("nid_front")
        sub = VerificationSubmission.objects.create(
            user=user,
            selfie_image=selfie,
            nid_image_front=nid_front,
            nid_image_back=request.FILES.get("nid_back"),
            liveness_passed=str(d.get("liveness_passed", "true")).lower() in ("1", "true", "yes"),
            ocr_name=d.get("ocr_name", ""), ocr_dob=d.get("ocr_dob", ""),
            ocr_nid_number=d.get("ocr_nid_number", ""), ocr_gender=d.get("ocr_gender", ""),
            ocr_license_name=d.get("ocr_license_name", ""), ocr_license_number=d.get("ocr_license_number", ""),
        )
        # Pass the SAVED absolute file paths (not upload filenames) so DeepFace can read them.
        selfie_path = sub.selfie_image.path if sub.selfie_image else ""
        nid_path = sub.nid_image_front.path if sub.nid_image_front else ""
        run_face_checks(sub, user, selfie_path, nid_path, is_driver)

        # Limited pending JWT so the app can poll /me/verification-status (Issue 1).
        tokens = make_tokens(user, scope="pending")
        return Response({"user_id": user.id, "status": user.status, **tokens},
                        status=http.HTTP_201_CREATED)


class VerifyPhoneView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        phone = request.data.get("phone")
        # Mock OTP (§16): any code passes. No real SMS.
        user = User.objects.filter(phone=phone).first()
        if not user:
            return Response({"error": "unknown_phone"}, status=http.HTTP_404_NOT_FOUND)
        user.is_phone_verified = True
        user.save(update_fields=["is_phone_verified"])
        return Response({"is_phone_verified": True})


class LoginView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        ident = request.data.get("email") or request.data.get("phone")
        password = request.data.get("password", "")
        user = User.objects.filter(email=ident).first() or User.objects.filter(phone=ident).first()
        if not user or not user.check_password(password):
            return Response({"error": "invalid_credentials"}, status=http.HTTP_401_UNAUTHORIZED)
        if user.status not in (UserStatus.ACTIVE, UserStatus.STEP_UP_REQUIRED):
            return Response({"error": "not_active", "status": user.status}, status=http.HTTP_403_FORBIDDEN)
        return Response({"status": user.status, **make_tokens(user, scope="full")})


class MeView(APIView):
    permission_classes = [IsAuthenticated]  # pending JWT allowed here (Issue 1)

    def get(self, request):
        u = request.user
        return Response({"id": u.id, "full_name": u.full_name, "email": u.email,
                         "role": u.role, "status": u.status, "is_phone_verified": u.is_phone_verified})


class VerificationStatusView(APIView):
    permission_classes = [IsAuthenticated]  # pending JWT allowed here (Issue 1)

    def get(self, request):
        sub = request.user.submissions.order_by("-created_at").first()
        if not sub:
            return Response({"status": request.user.status})
        return Response({
            "status": sub.status,
            "account_status": request.user.status,
            "reason": sub.review_reason or None,
            "face_match_verdict": sub.face_match_verdict,
        })


class EnrollKeyView(APIView):
    """Re-bind this device's biometric key (§17.1) — used when the device key was lost
    (app reinstall, or a fingerprint change invalidated it). Authenticated, so only the
    account owner can update their own public key. (Prod hardening: gate behind step-up
    re-verification so a stolen session can't silently re-bind a new device.)"""
    permission_classes = [IsAuthenticated]

    def post(self, request):
        pk = request.data.get("public_key")
        if not pk:
            return Response({"error": "no_public_key"}, status=http.HTTP_400_BAD_REQUEST)
        BiometricCredential.objects.update_or_create(
            user=request.user,
            defaults={"public_key": pk, "device_id": request.data.get("device_id", ""),
                      "is_active": True},
        )
        return Response({"ok": True})


class ChallengeView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        action = request.data.get("action_type")
        if action not in ActionType.values:
            return Response({"error": "bad_action_type"}, status=http.HTTP_400_BAD_REQUEST)
        ride_id = request.data.get("ride_id")  # optional, binds signature (Issue 3)
        ch = issue_challenge(request.user, action, ride_id=int(ride_id) if ride_id else None)
        return Response({"nonce": ch.nonce, "expires_at": ch.expires_at})


class VerifyActionView(APIView):
    """
    Demo endpoint proving the biometric core (and the tamper-rejection money-shot,
    eng review OV4). A real ride request/accept endpoint calls the same verifier.
    """
    permission_classes = [IsFullyVerified]

    def post(self, request):
        d = request.data
        try:
            verify_action_signature(
                user=request.user,
                action_type=d.get("action_type"),
                nonce=d.get("nonce"),
                signature_b64=d.get("signature"),
                ride_id=int(d["ride_id"]) if d.get("ride_id") else None,
            )
        except BiometricError as e:
            return Response({"error": str(e)}, status=http.HTTP_401_UNAUTHORIZED)
        return Response({"ok": True})


class StepUpView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        selfie = request.FILES.get("selfie")
        if not selfie:
            return Response({"error": "no_selfie"}, status=http.HTTP_400_BAD_REQUEST)
        # Store the fresh step-up selfie, then face-match it against the registration selfie (§6.3).
        sub = VerificationSubmission.objects.create(
            user=request.user, selfie_image=selfie, is_step_up=True, liveness_passed=True)
        reg = (request.user.submissions.filter(is_step_up=False)
               .exclude(selfie_image="").order_by("-created_at").first())
        reg_path = reg.selfie_image.path if (reg and reg.selfie_image) else ""
        score, verdict = face_match.score_faces(sub.selfie_image.path, reg_path)
        sub.face_match_score, sub.face_match_verdict = score, verdict
        sub.status = (SubmissionStatus.APPROVED if verdict == face_match.PASS
                      else SubmissionStatus.PENDING_REVIEW)  # low scores reach the admin
        sub.save(update_fields=["face_match_score", "face_match_verdict", "status"])
        u = request.user
        u.status = UserStatus.ACTIVE if verdict == face_match.PASS else UserStatus.STEP_UP_REQUIRED
        u.save(update_fields=["status"])
        return Response({"face_match_score": score, "verdict": verdict, "status": u.status})


class ResubmitView(APIView):
    """
    Re-verification after a rejection (§6.1 step 9). The user (holding the limited pending
    JWT) uploads a fresh selfie + NID; a new submission is created and the account returns
    to PENDING_REVIEW for another admin pass.
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        u = request.user
        if u.status not in (UserStatus.REJECTED, UserStatus.PENDING_REVIEW, UserStatus.STEP_UP_REQUIRED):
            return Response({"error": "not_resubmittable", "status": u.status}, status=http.HTTP_403_FORBIDDEN)
        d = request.data
        sub = VerificationSubmission.objects.create(
            user=u,
            selfie_image=request.FILES.get("selfie"),
            nid_image_front=request.FILES.get("nid_front"),
            liveness_passed=str(d.get("liveness_passed", "true")).lower() in ("1", "true", "yes"),
            ocr_name=d.get("ocr_name", ""), ocr_gender=d.get("ocr_gender", ""),
            ocr_nid_number=d.get("ocr_nid_number", ""),
        )
        selfie_path = sub.selfie_image.path if sub.selfie_image else ""
        nid_path = sub.nid_image_front.path if sub.nid_image_front else ""
        run_face_checks(sub, u, selfie_path, nid_path, u.role == Role.DRIVER)
        u.status = UserStatus.PENDING_REVIEW
        u.save(update_fields=["status"])
        return Response({"status": u.status})
