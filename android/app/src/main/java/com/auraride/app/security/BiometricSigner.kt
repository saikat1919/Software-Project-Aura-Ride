package com.auraride.app.security

import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs BiometricPrompt over the Keystore signing key and returns the base64 signature
 * of `message` (§17.1). The message must equal the backend's canonical form
 * "<ACTION>:<nonce>:<ride_id or empty>" so verification matches (eng review Issue 3).
 *
 * Requires a FragmentActivity host — make MainActivity extend FragmentActivity (it still
 * extends ComponentActivity, so Compose setContent keeps working).
 */
object BiometricSigner {

    class BiometricException(msg: String) : Exception(msg)

    suspend fun signMessage(activity: FragmentActivity, message: ByteArray): String =
        suspendCancellableCoroutine { cont ->
            val signature = BiometricKeyManager.signingSignature()
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val sig = result.cryptoObject!!.signature!!
                            sig.update(message)
                            cont.resume(Base64.encodeToString(sig.sign(), Base64.NO_WRAP))
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    }

                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        if (cont.isActive) cont.resumeWithException(BiometricException(msg.toString()))
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm it's you")
                .setSubtitle("Sign this action on your device")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
        }
}
