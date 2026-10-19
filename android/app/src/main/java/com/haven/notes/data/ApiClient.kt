package com.haven.notes.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun validateHttpsOrigin(origin: String): String {
        val trimmed = origin.trim().trimEnd('/')
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid server address.")
        }
        if (uri.scheme != "https") {
            throw IllegalArgumentException("Server address must begin with https://")
        }
        if (uri.host.isNullOrEmpty()) {
            throw IllegalArgumentException("Server address requires a valid domain or IP.")
        }
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
            throw IllegalArgumentException("Server address cannot contain credentials, queries or fragments.")
        }
        return trimmed
    }

    suspend fun get(origin: String, path: String, token: String? = null): String = withContext(Dispatchers.IO) {
        val cleanOrigin = validateHttpsOrigin(origin)
        val url = "$cleanOrigin/${path.trimStart('/')}"
        val builder = Request.Builder().url(url)
        if (!token.isNullOrEmpty()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        val request = builder.build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(body) ?: "Server responded with status ${response.code}"
                throw Exception(errorMsg)
            }
            body
        }
    }

    suspend fun post(
        origin: String,
        path: String,
        token: String? = null,
        bodyJson: String? = null,
        idempotencyKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanOrigin = validateHttpsOrigin(origin)
        val url = "$cleanOrigin/${path.trimStart('/')}"
        val requestBody = (bodyJson ?: "{}").toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (!token.isNullOrEmpty()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        if (!idempotencyKey.isNullOrEmpty()) {
            builder.addHeader("Idempotency-Key", idempotencyKey)
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody) ?: "Server responded with status ${response.code}"
                throw Exception(errorMsg)
            }
            responseBody
        }
    }

    private fun parseErrorMessage(json: String): String? {
        return try {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, mapType)
            map["error"] as? String
        } catch (e: Exception) {
            null
        }
    }
}
