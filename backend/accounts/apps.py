import logging

from django.apps import AppConfig
from django.conf import settings

log = logging.getLogger(__name__)


class AccountsConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "accounts"

    def ready(self):
        # Loud startup log so a stale DEMO_FAKE_AUTH can't hide (eng review OV4).
        if getattr(settings, "DEMO_FAKE_AUTH", False):
            log.warning("SECURITY: DEMO_FAKE_AUTH is ON — biometric signatures are NOT verified. DEV ONLY.")
        # Warm the face-match model so the first real registration isn't a cold cliff
        # (eng review Issue 4). No-op when DEMO_FAKE_AI is on.
        try:
            from accounts.services import face_match
            face_match.warm()
        except Exception:
            pass
