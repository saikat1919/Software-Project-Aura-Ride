package com.auraride.app.data

import com.auraride.app.net.ApiService
import com.auraride.app.net.MeResponse
import com.auraride.app.net.TokenStore
import com.auraride.app.net.VerificationStatusResponse
import com.auraride.app.security.BiometricKeyManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Auth + registration against the real backend. Registration generates the device
 * biometric key and sends only the public half (§17.1), plus the selfie + NID images
 * (multipart) so the admin has something to review and DeepFace has inputs.
 */
class AuthRepository(private val api: ApiService, private val store: TokenStore) {

    /** Returns the account status (PENDING_REVIEW on success). */
    suspend fun register(
        email: String, phone: String, fullName: String, password: String, role: String,
        ocrName: String = "", ocrGender: String = "", ocrNid: String = "",
        selfiePath: String? = null, nidPath: String? = null,
        licensePath: String? = null, licenseName: String = "", licenseNumber: String = "",
        vehMake: String = "", vehModel: String = "", vehPlate: String = "", vehColor: String = "",
    ): String {
        if (!BiometricKeyManager.hasKey()) BiometricKeyManager.generateKeyPair()
        val pem = BiometricKeyManager.publicKeyPem()

        val jpeg = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("email", email)
            .addFormDataPart("phone", phone)
            .addFormDataPart("full_name", fullName)
            .addFormDataPart("password", password)
            .addFormDataPart("role", role)
            .addFormDataPart("public_key", pem)
            .addFormDataPart("liveness_passed", "true")
            .addFormDataPart("ocr_name", ocrName)
            .addFormDataPart("ocr_gender", ocrGender)
            .addFormDataPart("ocr_nid_number", ocrNid)
            .addFormDataPart("ocr_license_name", licenseName)
            .addFormDataPart("ocr_license_number", licenseNumber)
            .addFormDataPart("vehicle_make", vehMake)
            .addFormDataPart("vehicle_model", vehModel)
            .addFormDataPart("vehicle_plate", vehPlate)
            .addFormDataPart("vehicle_color", vehColor)
        selfiePath?.let { File(it) }?.takeIf { it.exists() }?.let {
            body.addFormDataPart("selfie", it.name, it.asRequestBody(jpeg))
        }
        nidPath?.let { File(it) }?.takeIf { it.exists() }?.let {
            body.addFormDataPart("nid_front", it.name, it.asRequestBody(jpeg))
        }
        licensePath?.let { File(it) }?.takeIf { it.exists() }?.let {
            body.addFormDataPart("license", it.name, it.asRequestBody(jpeg))
        }

        val resp = api.register(body.build())
        store.save(resp.access, resp.refresh, role)   // limited pending JWT (eng review Issue 1)
        return resp.status
    }

    suspend fun verifyPhone(phone: String, otp: String) = api.verifyPhone(phone, otp)

    /** Logs in, stores full-scope tokens, caches role + name from /me. Returns the profile. */
    suspend fun login(email: String, password: String): MeResponse {
        val tokens = api.login(email, password)
        store.save(tokens.access, tokens.refresh)
        val me = api.me()
        store.role = me.role
        store.fullName = me.fullName
        return me
    }

    fun cachedName(): String? = store.fullName

    /** Step-up: face-match a fresh selfie against the registration selfie (§6.3). */
    suspend fun stepUp(selfiePath: String): com.auraride.app.net.StepUpResponse {
        val jpeg = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        File(selfiePath).takeIf { it.exists() }?.let {
            body.addFormDataPart("selfie", it.name, it.asRequestBody(jpeg))
        }
        return api.stepUp(body.build())
    }

    /** Resubmit verification after a rejection (§6.1 step 9). */
    suspend fun resubmit(
        selfiePath: String?, nidPath: String?,
        ocrName: String, ocrGender: String, ocrNid: String,
    ): com.auraride.app.net.StatusResponse {
        val jpeg = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("liveness_passed", "true")
            .addFormDataPart("ocr_name", ocrName)
            .addFormDataPart("ocr_gender", ocrGender)
            .addFormDataPart("ocr_nid_number", ocrNid)
        selfiePath?.let { File(it) }?.takeIf { it.exists() }?.let {
            body.addFormDataPart("selfie", it.name, it.asRequestBody(jpeg))
        }
        nidPath?.let { File(it) }?.takeIf { it.exists() }?.let {
            body.addFormDataPart("nid_front", it.name, it.asRequestBody(jpeg))
        }
        return api.resubmit(body.build())
    }

    suspend fun verificationStatus(): VerificationStatusResponse = api.verificationStatus()

    suspend fun me(): MeResponse = api.me()

    fun cachedRole(): String? = store.role

    fun isLoggedIn(): Boolean = store.access != null

    /**
     * Confirm a restored session still belongs to a usable account. The JWT is stateless, so a
     * deleted or rejected user keeps a syntactically valid token until it expires — the app must
     * ask the server. Returns false ONLY when the account is provably gone/unusable (so we log
     * out); a network error stays optimistic so we don't sign out a valid user who's offline.
     */
    suspend fun sessionStillValid(): Boolean = try {
        val me = api.me()
        store.role = me.role
        store.fullName = me.fullName
        me.status != "REJECTED"          // rejected accounts must not auto-restore to home
    } catch (e: retrofit2.HttpException) {
        e.code() !in intArrayOf(401, 403, 404)   // deleted / forbidden -> dead session
    } catch (e: Exception) {
        true                             // network/other -> keep session, validate again later
    }

    fun logout() = store.clear()
}
