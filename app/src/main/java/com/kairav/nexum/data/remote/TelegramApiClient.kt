package com.kairav.nexum.data.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val TAG = "TelegramApiClient"

    suspend fun testBot(token: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/getMe")
            .build()
        
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (!success) {
                    Log.w(TAG, "testBot failed with code ${response.code}: ${response.body?.string()}")
                }
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "testBot exception: ${e.message}", e)
            false
        }
    }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        message: String,
        silent: Boolean = false,
        parseMode: String = "MarkdownV2"
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
            if (parseMode.isNotEmpty()) {
                put("parse_mode", parseMode)
            }
            if (silent) {
                put("disable_notification", true)
            }
        }
        
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (!success) {
                    Log.e(TAG, "sendMessage failed with code ${response.code}: ${response.body?.string()}")
                }
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage exception: ${e.message}", e)
            false
        }
    }

    suspend fun sendDocument(
        token: String,
        chatId: String,
        fileBytes: ByteArray,
        fileName: String,
        caption: String = "",
        silent: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/sendDocument"
        val fileBody = fileBytes.toRequestBody("application/octet-stream".toMediaType())
        
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", fileName, fileBody)
        
        if (caption.isNotBlank()) {
            multipartBuilder.addFormDataPart("caption", caption)
            multipartBuilder.addFormDataPart("parse_mode", "HTML")
        }
        if (silent) {
            multipartBuilder.addFormDataPart("disable_notification", "true")
        }

        val request = Request.Builder()
            .url(url)
            .post(multipartBuilder.build())
            .build()

        val uploadClient = okHttpClient.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        try {
            uploadClient.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                val respBody = response.body?.string()
                if (success) {
                    Log.d(TAG, "sendDocument succeeded: code ${response.code}")
                } else {
                    Log.e(TAG, "sendDocument failed: code ${response.code}, response: $respBody")
                }
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendDocument exception: ${e.message}", e)
            false
        }
    }
}
