"""
Security-core tests — the biometric challenge-signature path (§17.1).
These are the graded core; they must pass. Run: python manage.py test accounts
"""
import base64

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from django.test import TestCase
from django.utils import timezone
from datetime import timedelta

from accounts.models import ActionType, BiometricCredential, User
from accounts.services.biometric import (
    BiometricError, issue_challenge, signed_message, verify_action_signature,
)


def make_keypair():
    priv = ec.generate_private_key(ec.SECP256R1())
    pem = priv.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode()
    return priv, pem


def sign(priv, message: bytes) -> str:
    return base64.b64encode(priv.sign(message, ec.ECDSA(hashes.SHA256()))).decode()


class BiometricSignatureTests(TestCase):
    def setUp(self):
        self.priv, pem = make_keypair()
        self.user = User.objects.create_user(
            email="a@b.com", phone="017000", full_name="Nusrat", password="pw")
        BiometricCredential.objects.create(user=self.user, public_key=pem)

    def _challenge_and_sign(self, action=ActionType.REQUEST_RIDE, ride_id=None):
        ch = issue_challenge(self.user, action, ride_id=ride_id)
        sig = sign(self.priv, signed_message(action, ch.nonce, ride_id))
        return ch, sig

    def test_valid_signature_passes(self):
        ch, sig = self._challenge_and_sign()
        verify_action_signature(self.user, ch.action_type, ch.nonce, sig)  # no raise
        ch.refresh_from_db()
        self.assertTrue(ch.is_used)

    def test_replayed_nonce_rejected(self):
        ch, sig = self._challenge_and_sign()
        verify_action_signature(self.user, ch.action_type, ch.nonce, sig)
        with self.assertRaises(BiometricError):
            verify_action_signature(self.user, ch.action_type, ch.nonce, sig)

    def test_tampered_signature_rejected(self):
        ch, sig = self._challenge_and_sign()
        bad = base64.b64encode(b"\x00" + base64.b64decode(sig)[1:]).decode()
        with self.assertRaises(BiometricError):
            verify_action_signature(self.user, ch.action_type, ch.nonce, bad)

    def test_cross_ride_replay_rejected(self):
        # Signature obtained for ride 1 must not verify against ride 2 (Issue 3).
        ch = issue_challenge(self.user, ActionType.ACCEPT_RIDE, ride_id=1)
        sig = sign(self.priv, signed_message(ActionType.ACCEPT_RIDE, ch.nonce, 1))
        with self.assertRaises(BiometricError):
            verify_action_signature(self.user, ActionType.ACCEPT_RIDE, ch.nonce, sig, ride_id=2)

    def test_expired_nonce_rejected(self):
        ch, sig = self._challenge_and_sign()
        ch.expires_at = timezone.now() - timedelta(seconds=1)
        ch.save(update_fields=["expires_at"])
        with self.assertRaises(BiometricError):
            verify_action_signature(self.user, ch.action_type, ch.nonce, sig)
