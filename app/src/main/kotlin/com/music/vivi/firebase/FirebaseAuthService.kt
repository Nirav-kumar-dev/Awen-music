package com.music.vivi.firebase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

data class FirebaseUser(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String? = null
)

data class UserProfile(
    val name: String,
    val username: String,
    val email: String,
    val birthdate: String = "",
    val preferences: Map<String, Boolean> = emptyMap(),
    val youtubeConnected: Boolean = false,
    val youtubeAccountName: String = "",
    val youtubeChannelHandle: String = ""
)

object FirebaseAuthService {
    private const val API_KEY = "AIzaSyDORWH5fEeNe7KWyWPMO17OfsluRwR7XCM"
    private const val DATABASE_URL = "https://exams-mc-default-rtdb.firebaseio.com"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Signs up a new user with email and password via Firebase Auth REST API
     */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY"
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val user = FirebaseUser(
                    uid = json.getString("localId"),
                    email = json.getString("email"),
                    idToken = json.getString("idToken"),
                    refreshToken = json.optString("refreshToken", null)
                )
                Result.success(user)
            } else {
                val errorMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Sign-up failed with status code ${response.code}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Firebase signUp error")
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing user with email and password via Firebase Auth REST API
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$API_KEY"
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val user = FirebaseUser(
                    uid = json.getString("localId"),
                    email = json.getString("email"),
                    idToken = json.getString("idToken"),
                    refreshToken = json.optString("refreshToken", null)
                )
                Result.success(user)
            } else {
                val errorMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Sign-in failed with status code ${response.code}"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Firebase signIn error")
            Result.failure(e)
        }
    }

    /**
     * Sends password reset email via Firebase Auth REST API
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$API_KEY"
            val payload = JSONObject().apply {
                put("requestType", "PASSWORD_RESET")
                put("email", email.trim())
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val responseBody = response.body?.string() ?: ""
                val errorMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Password reset failed"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Firebase sendPasswordReset error")
            Result.failure(e)
        }
    }

    /**
     * Saves user profile to Firebase Realtime Database at /users/$uid
     * Requires auth idToken matching $uid per security rules
     */
    suspend fun saveUserProfile(
        uid: String,
        idToken: String,
        name: String,
        username: String,
        email: String,
        password: String,
        birthdate: String = "",
        preferences: Map<String, Boolean> = emptyMap(),
        youtubeConnected: Boolean = false,
        youtubeAccountName: String = "",
        youtubeChannelHandle: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$DATABASE_URL/users/$uid.json?auth=$idToken"

            val prefsJson = JSONObject().apply {
                preferences.forEach { (k, v) -> put(k, v) }
            }

            val ytJson = JSONObject().apply {
                put("connected", youtubeConnected)
                put("accountName", youtubeAccountName)
                put("channelHandle", youtubeChannelHandle)
            }

            val payload = JSONObject().apply {
                // Mandatory fields per security rules
                put("name", name.trim())
                put("username", username.trim())
                put("email", email.trim())
                put("password", password)
                // Optional extra fields
                put("birthdate", birthdate.trim())
                put("preferences", prefsJson)
                put("youtube", ytJson)
                put("updatedAt", System.currentTimeMillis())
            }

            val request = Request.Builder()
                .url(url)
                .put(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val err = response.body?.string() ?: "Failed to save profile: ${response.code}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Timber.e(e, "Firebase saveUserProfile error")
            Result.failure(e)
        }
    }

    /**
     * Fetches user profile from Firebase Realtime Database at /users/$uid
     */
    suspend fun fetchUserProfile(uid: String, idToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val url = "$DATABASE_URL/users/$uid.json?auth=$idToken"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank() && responseBody != "null") {
                val json = JSONObject(responseBody)
                val prefs = mutableMapOf<String, Boolean>()
                json.optJSONObject("preferences")?.let { pObj ->
                    pObj.keys().forEach { k -> prefs[k] = pObj.optBoolean(k, false) }
                }

                val ytObj = json.optJSONObject("youtube")

                val profile = UserProfile(
                    name = json.optString("name", ""),
                    username = json.optString("username", ""),
                    email = json.optString("email", ""),
                    birthdate = json.optString("birthdate", ""),
                    preferences = prefs,
                    youtubeConnected = ytObj?.optBoolean("connected", false) ?: false,
                    youtubeAccountName = ytObj?.optString("accountName", "") ?: "",
                    youtubeChannelHandle = ytObj?.optString("channelHandle", "") ?: ""
                )
                Result.success(profile)
            } else {
                Result.failure(Exception("User profile not found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Firebase fetchUserProfile error")
            Result.failure(e)
        }
    }
}
