"""
Device-bound biometric challenge-response — PROJECT_SPEC §17.1, the graded core.

Shared service (eng review Issue 5): every action endpoint calls verify_action_signature;
no endpoint re-implements the crypto. The signature is bound to user + action + ride
(eng review Issue 3), so a signature obtained for one ride cannot be replayed on another.
"""
import base64
import secrets
from datetime import timedelta

from django.conf import settings
from django.db import transaction
from django.utils import timezone

from accounts.models import ActionChallenge, BiometricCredential


class BiometricError(Exception):
    """Raised on any verification failure. Views map this to HTTP 401."""


def signed_message(action_type: str, nonce: str, ride_id=None) -> bytes:
    """
    Canonical bytes the client signs. The device must build the exact same string:
        "<ACTION>:<nonce>:<ride_id or empty>"
    ride_id binds the signature to a specific ride on the accept path (Issue 3).
    On the request path the ride does not exist yet, so ride_id is empty and the
    single-use user+action nonce is the binding.
    """
    return f"{action_type}:{nonce}:{'' if ride_id is None else ride_id}".encode("utf-8")


def issue_challenge(user, action_type: str, ride_id=None) -> ActionChallenge:
    return ActionChallenge.objects.create(
        user=user,
        action_type=action_type,
        ride_id=ride_id,
        nonce=secrets.token_urlsafe(32),
        expires_at=timezone.now() + timedelta(seconds=settings.NONCE_TTL_SECONDS),
    )


def _verify_signature(public_key_pem: str, message: bytes, signature_b64: str) -> None:
    # Imported lazily so the module loads even if cryptography isn't installed yet.
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.serialization import load_pem_public_key

    try:
        public_key = load_pem_public_key(public_key_pem.encode("utf-8"))
    except Exception as exc:
        raise BiometricError("bad_public_key") from exc
    try:
        signature = base64.b64decode(signature_b64)
    except Exception as exc:
        raise BiometricError("bad_signature_encoding") from exc
    try:
        public_key.verify(signature, message, ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise BiometricError("invalid_signature") from exc


@transaction.atomic
def verify_action_signature(user, action_type, nonce, signature_b64=None, ride_id=None):
    """
    Verify a biometric-signed action. Returns the consumed ActionChallenge on success;
    raises BiometricError on any failure. Single-use is enforced atomically so a replayed
    nonce loses even under concurrent requests.
    """
    try:
        challenge = ActionChallenge.objects.select_for_update().get(
            nonce=nonce, user=user, action_type=action_type
        )
    except ActionChallenge.DoesNotExist as exc:
        raise BiometricError("unknown_nonce") from exc

    if challenge.is_used:
        raise BiometricError("nonce_already_used")
    if challenge.expires_at < timezone.now():
        raise BiometricError("nonce_expired")
    if (challenge.ride_id or None) != (ride_id or None):
        raise BiometricError("ride_mismatch")

    if settings.DEMO_FAKE_AUTH:
        # Dev bypass (forced off outside DEBUG in settings, eng review OV4).
        pass
    else:
        try:
            cred = user.biometric
        except BiometricCredential.DoesNotExist as exc:
            raise BiometricError("no_enrolled_key") from exc
        if not cred.is_active:
            raise BiometricError("key_inactive")
        _verify_signature(cred.public_key, signed_message(action_type, nonce, ride_id), signature_b64)

    # Consume atomically: exactly one caller flips is_used False->True.
    claimed = ActionChallenge.objects.filter(pk=challenge.pk, is_used=False).update(is_used=True)
    if claimed != 1:
        raise BiometricError("nonce_already_used")
    challenge.is_used = True
    return challenge
