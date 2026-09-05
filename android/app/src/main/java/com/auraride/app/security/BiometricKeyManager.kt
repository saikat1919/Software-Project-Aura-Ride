package com.auraride.app.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Device-bound biometric key — PROJECT_SPEC §17.1. The graded core on the app side.
 *
 * Generates a hardware-backed EC P-256 key in the Android Keystore. The private key is
 * non-exportable and biometric-gated; only the PUBLIC key is sent to the backend. Each
 * sensitive action signs a server nonce, proving "same device + fresh biometric" without
 * ever transmitting a fingerprint.
 */
object BiometricKeyManager {
    private const val ALIAS = "aura_ride_biometric_key"
    private const val PROVIDER = "AndroidKeyStore"

    /** Thrown when the device key is gone (reinstall, or invalidated by a fingerprint change,
     *  §17.1) so callers can regenerate + re-enroll instead of crashing on a null cast. */
    class NoDeviceKeyException : Exception("no_enrolled_key")

    fun hasKey(): Boolean = keyStore().containsAlias(ALIAS)

    /** Generate the key pair once at registration. StrongBox if the device has it, else TEE. */
    fun generateKeyPair() {
        try {
            build(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        } catch (_: StrongBoxUnavailableException) {
            build(strongBox = false)   // eng review: StrongBox is preferred, not required
        }
    }

    private fun build(strongBox: Boolean) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)          // usable only after a biometric
            .setInvalidatedByBiometricEnrollment(true)    // new fingerprint destroys the key
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    /** Public key as PEM (X.509 SubjectPublicKeyInfo) — sent to POST /auth/register. */
    fun publicKeyPem(): String {
        val cert = keyStore().getCertificate(ALIAS)
            ?: error("No biometric key — call generateKeyPair() first")
        val b64 = Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n" +
            b64.chunked(64).joinToString("\n") +
            "\n-----END PUBLIC KEY-----\n"
    }

    /**
     * A Signature initialized for signing, to wrap in a BiometricPrompt.CryptoObject.
     * Because the key requires user authentication, the actual sign() happens only after
     * BiometricPrompt succeeds (see BiometricSigner).
     */
    fun signingSignature(): Signature {
        val priv = keyStore().getKey(ALIAS, null) as? PrivateKey ?: throw NoDeviceKeyException()
        return try {
            Signature.getInstance("SHA256withECDSA").apply { initSign(priv) }
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            throw NoDeviceKeyException()   // fingerprint changed -> key destroyed
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(PROVIDER).apply { load(null) }
}
