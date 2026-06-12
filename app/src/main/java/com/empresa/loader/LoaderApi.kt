package com.empresa.loader

import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * API client — matches KidGuard's InstallerApi pattern.
 * Handles: bind, download APK, validate MD5, install.
 */
object LoaderApi {

    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private var authToken: String? = null

    /**
     * Step 1: Bind device with installation code.
     * POST /api/loader/bind with { "code": "63028428" }
     */
    suspend fun bindDevice(code: String): Result<BindResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(BindRequest(code))
                val body = json.toRequestBody(JSON)

                val request = Request.Builder()
                    .url(BuildConfig.API_BASE_URL + "/api/loader/bind")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val result = gson.fromJson(responseBody, BindResponse::class.java)
                    result?.let {
                        authToken = it.token
                        Result.success(it)
                    } ?: Result.failure(Exception("Invalid response from server"))
                } else {
                    Result.failure(Exception("Bind failed: ${response.code} - $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Step 2: Download the APK file from server.
     */
    suspend fun downloadApk(url: String, destFile: File): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder().url(url)
                authToken?.let { requestBuilder.header("Authorization", "Bearer $it") }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))

                FileOutputStream(destFile).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }

                Result.success(destFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Step 3: Validate MD5 checksum of downloaded APK.
     */
    fun validateMd5(file: File, expectedMd5: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val input = file.inputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            input.close()
            val md5Hex = digest.digest().joinToString("") { "%02x".format(it) }
            md5Hex.equals(expectedMd5, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the current auth token.
     */
    fun getAuthToken(): String? = authToken
}
