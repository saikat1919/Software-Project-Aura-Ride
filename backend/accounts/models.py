"""
Data model — PROJECT_SPEC §11. Locations are plain lat/lng floats (no PostGIS).
Raw NID/license images are deleted on approval (§14); only extracted fields +
face embedding are retained.
"""
from django.contrib.auth.base_user import AbstractBaseUser, BaseUserManager
from django.contrib.auth.models import PermissionsMixin
from django.db import models


class Role(models.TextChoices):
    PASSENGER = "PASSENGER"
    DRIVER = "DRIVER"


class UserStatus(models.TextChoices):
    PENDING_REVIEW = "PENDING_REVIEW"
    ACTIVE = "ACTIVE"
    REJECTED = "REJECTED"
    SUSPENDED = "SUSPENDED"
    STEP_UP_REQUIRED = "STEP_UP_REQUIRED"


class PaymentMethod(models.TextChoices):
    BKASH = "BKASH"
    CASH = "CASH"


class UserManager(BaseUserManager):
    def create_user(self, email, phone, full_name, password=None, **extra):
        if not email:
            raise ValueError("Email is required")
        user = self.model(
            email=self.normalize_email(email), phone=phone, full_name=full_name, **extra
        )
        user.set_password(password)
        user.save(using=self._db)
        return user

    def create_superuser(self, email, phone, full_name, password=None, **extra):
        extra.setdefault("is_staff", True)
        extra.setdefault("is_superuser", True)
        extra.setdefault("status", UserStatus.ACTIVE)
        extra.setdefault("role", Role.PASSENGER)
        return self.create_user(email, phone, full_name, password, **extra)


class User(AbstractBaseUser, PermissionsMixin):
    email = models.EmailField(unique=True)
    phone = models.CharField(max_length=20, unique=True)
    full_name = models.CharField(max_length=120)
    role = models.CharField(max_length=12, choices=Role.choices, default=Role.PASSENGER)
    status = models.CharField(
        max_length=20, choices=UserStatus.choices, default=UserStatus.PENDING_REVIEW
    )
    is_phone_verified = models.BooleanField(default=False)
    is_active = models.BooleanField(default=True)
    is_staff = models.BooleanField(default=False)
    date_joined = models.DateTimeField(auto_now_add=True)

    objects = UserManager()
    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["phone", "full_name"]

    def __str__(self):
        return f"{self.full_name} <{self.email}> [{self.status}]"


class PassengerProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="passenger_profile")
    default_payment_method = models.CharField(
        max_length=6, choices=PaymentMethod.choices, default=PaymentMethod.BKASH
    )


class DriverProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="driver_profile")
    license_image = models.FileField(upload_to="licenses/", null=True, blank=True)  # deleted on approval
    is_online = models.BooleanField(default=False)
    is_available = models.BooleanField(default=True)
    current_lat = models.FloatField(null=True, blank=True)
    current_lng = models.FloatField(null=True, blank=True)
    last_location_at = models.DateTimeField(null=True, blank=True)


class Vehicle(models.Model):
    driver = models.ForeignKey(DriverProfile, on_delete=models.CASCADE, related_name="vehicles")
    make = models.CharField(max_length=40)
    model = models.CharField(max_length=40)
    color = models.CharField(max_length=24)
    plate_number = models.CharField(max_length=24)


class VerificationVerdict(models.TextChoices):
    PASS = "PASS"
    REVIEW = "REVIEW"
    FAIL = "FAIL"


class SubmissionStatus(models.TextChoices):
    PENDING_REVIEW = "PENDING_REVIEW"
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


class VerificationSubmission(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="submissions")
    selfie_image = models.FileField(upload_to="selfies/", null=True, blank=True)
    nid_image_front = models.FileField(upload_to="nid/", null=True, blank=True)   # deleted on approval
    nid_image_back = models.FileField(upload_to="nid/", null=True, blank=True)    # deleted on approval
    liveness_passed = models.BooleanField(default=False)
    face_match_score = models.FloatField(null=True, blank=True)
    face_match_verdict = models.CharField(
        max_length=8, choices=VerificationVerdict.choices, null=True, blank=True
    )
    ocr_name = models.CharField(max_length=120, blank=True)
    ocr_dob = models.CharField(max_length=32, blank=True)
    ocr_nid_number = models.CharField(max_length=40, blank=True)
    ocr_gender = models.CharField(max_length=16, blank=True)
    ocr_license_name = models.CharField(max_length=120, blank=True)
    ocr_license_number = models.CharField(max_length=40, blank=True)
    gender_estimate = models.CharField(max_length=32, blank=True)  # AI guess from selfie (§7.3)
    flags = models.JSONField(default=dict, blank=True)
    status = models.CharField(
        max_length=20, choices=SubmissionStatus.choices, default=SubmissionStatus.PENDING_REVIEW
    )
    reviewed_by = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True, related_name="reviews"
    )
    review_reason = models.CharField(max_length=255, blank=True)
    is_step_up = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)


class BiometricCredential(models.Model):
    """Public key only — the private key never leaves the device (§17.1)."""
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="biometric")
    public_key = models.TextField()  # PEM (X.509 SubjectPublicKeyInfo)
    device_id = models.CharField(max_length=128, blank=True)
    is_active = models.BooleanField(default=True)
    enrolled_at = models.DateTimeField(auto_now_add=True)


class FaceEmbedding(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="embeddings")
    embedding = models.BinaryField()
    source = models.CharField(max_length=32, default="REGISTRATION_SELFIE")
    created_at = models.DateTimeField(auto_now_add=True)


class ActionType(models.TextChoices):
    REQUEST_RIDE = "REQUEST_RIDE"
    ACCEPT_RIDE = "ACCEPT_RIDE"
    STEP_UP = "STEP_UP"


class ActionChallenge(models.Model):
    """Single-use, short-lived nonce bound to user + action (+ ride, eng review Issue 3)."""
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="challenges")
    nonce = models.CharField(max_length=64, unique=True)
    action_type = models.CharField(max_length=16, choices=ActionType.choices)
    ride_id = models.IntegerField(null=True, blank=True)
    is_used = models.BooleanField(default=False)
    expires_at = models.DateTimeField()
    created_at = models.DateTimeField(auto_now_add=True)
