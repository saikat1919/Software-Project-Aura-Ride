"""
Aura Ride — Django settings. Zero-cost stack: sqlite by default, synchronous AI,
no Celery/Redis/Channels (see PROJECT_SPEC §16). WSGI is enough (no WebSockets).
"""
from datetime import timedelta
from pathlib import Path
import os

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass  # python-dotenv optional; env vars still read from the environment.

BASE_DIR = Path(__file__).resolve().parent.parent


def env_bool(key: str, default: str = "0") -> bool:
    return os.environ.get(key, default).strip() in ("1", "true", "True", "yes")


SECRET_KEY = os.environ.get("SECRET_KEY", "dev-insecure-change-me")
DEBUG = env_bool("DEBUG", "1")
ALLOWED_HOSTS = [h.strip() for h in os.environ.get(
    "ALLOWED_HOSTS", "localhost,127.0.0.1,10.0.2.2").split(",") if h.strip()]

INSTALLED_APPS = [
    "aura.apps.AuraAdminConfig",  # custom admin site (verification dashboard)
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "accounts",
    "rides",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "aura.urls"
WSGI_APPLICATION = "aura.wsgi.application"

TEMPLATES = [{
    "BACKEND": "django.template.backends.django.DjangoTemplates",
    "DIRS": [],
    "APP_DIRS": True,
    "OPTIONS": {"context_processors": [
        "django.template.context_processors.request",
        "django.contrib.auth.context_processors.auth",
        "django.contrib.messages.context_processors.messages",
    ]},
}]

# --- Database: sqlite by default so it runs with no server; Postgres is the spec target.
if os.environ.get("DB_ENGINE") == "postgres":
    DATABASES = {"default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ.get("DB_NAME", "aura"),
        "USER": os.environ.get("DB_USER", "aura"),
        "PASSWORD": os.environ.get("DB_PASSWORD", ""),
        "HOST": os.environ.get("DB_HOST", "localhost"),
        "PORT": os.environ.get("DB_PORT", "5432"),
    }}
else:
    DATABASES = {"default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": BASE_DIR / "db.sqlite3",
    }}

AUTH_USER_MODEL = "accounts.User"
AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
]

LANGUAGE_CODE = "en-us"
TIME_ZONE = "Asia/Dhaka"
USE_I18N = True
USE_TZ = True

STATIC_URL = "static/"
MEDIA_URL = "media/"
MEDIA_ROOT = BASE_DIR / "media"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.IsAuthenticated",
    ),
}

SIMPLE_JWT = {
    # 30min is production-safe but forces a refresh mid-session; a multi-hour demo/test then
    # depends on the refresh path working perfectly. Default to 12h for the demo (env-overridable).
    "ACCESS_TOKEN_LIFETIME": timedelta(hours=int(os.environ.get("ACCESS_TOKEN_HOURS", "12"))),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=7),
}

# --- Demo seams (eng review) ---
# Face matching: fake canned score unless real DeepFace is installed + this is off.
DEMO_FAKE_AI = env_bool("DEMO_FAKE_AI", "1")
# Biometric bypass. FORCED off outside DEBUG so a stale flag can't silently disable
# signature verification in a real build (eng review OV4).
DEMO_FAKE_AUTH = env_bool("DEMO_FAKE_AUTH", "0") and DEBUG

# Biometric params (§17.1 / eng review)
NONCE_TTL_SECONDS = 60

# Routing/ETA: 1 = fake (haversine estimate, no OSRM server), 0 = real OSRM.
DEMO_FAKE_ROUTING = env_bool("DEMO_FAKE_ROUTING", "1")
# Default to the public OSRM demo server so the backend fare uses the SAME road distance
# the app draws on the map (consistency). Point at a local Docker OSRM for reliability.
OSRM_BASE_URL = os.environ.get("OSRM_BASE_URL", "https://router.project-osrm.org")

# Fare model (§8.4, BDT) + matching (§17.4)
FARE_BASE_BDT = 50
FARE_PER_KM_BDT = 25
FARE_PER_MIN_BDT = 2
AVG_SPEED_KMH = 20            # for the faked duration estimate
# Demo: wide radius so co-located test phones match even when one falls back to the
# demo location (no GPS fix). Production would use ~5km. Override via env.
MATCH_RADIUS_KM = float(os.environ.get("MATCH_RADIUS_KM", "50"))
# Per-driver accept window (§8.2). Generous: a human must fetch a nonce, pass the
# BiometricPrompt, sign, and round-trip — 15s expires mid-accept, cancelling the
# passenger's ride and 409'ing the driver. 90s covers a real fingerprint tap.
OFFER_TIMEOUT_SECONDS = int(os.environ.get("OFFER_TIMEOUT_SECONDS", "90"))

# Geofence: a driver may only mark "arrived" when physically near the pickup, so she can't
# fake an arrival. Enforced only when her phone reports a fresh fix (demo phones without GPS
# aren't penalised). Radius is generous to absorb urban GPS drift.
GEOFENCE_METERS = int(os.environ.get("GEOFENCE_METERS", "300"))
# Also require the driver to be at the DROPOFF before "complete". OFF by default so the two
# test phones (sitting together, not at the dropoff) can still finish a trip; flip on to demo
# real enforcement.
ENFORCE_GEOFENCE = env_bool("ENFORCE_GEOFENCE", "0")
