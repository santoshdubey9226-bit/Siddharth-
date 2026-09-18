package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ===================================================================================
 * GEMINI API CONNECTION & CONFIGURATION
 * ===================================================================================
 *
 * This class manages network communications with the Google Gemini API.
 *
 * 1. BASE URL:
 *    https://generativelanguage.googleapis.com/v1beta/
 *
 * 2. AUTHENTICATION & SECURITY:
 *    The API key is accessed securely at runtime via `BuildConfig.GEMINI_API_KEY`.
 *    The key is injected into BuildConfig by the Secrets Gradle Plugin from the project's
 *    `.env` file (configured in `.env.example`). The key is never committed directly
 *    into client source code.
 *
 * 3. SUPPORTED MODELS:
 *    - gemini-3.5-flash: Fast, high-intelligence model for general reasoning & vision
 *    - gemini-3.1-pro-preview: Deep reasoning and complex programming/academic tasks
 *    (Older models such as gemini-1.5 or gemini-2.0 are not used per current standards).
 *
 * 4. TIMEOUTS:
 *    Configured with 60-second connect/read/write timeouts to handle long reasoning
 *    and multimodal vision generation safely.
 * ===================================================================================
 */
class GeminiApiService {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Executes content generation using Google Gemini API.
     *
     * @param model Model identifier (e.g. "gemini-3.5-flash")
     * @param request The structured request payload containing conversation & system instructions
     * @return Result containing either generated response text or an informative error
     */
    suspend fun generateContent(
        model: String,
        request: GeminiRequest
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException(
                    "Gemini API key is not configured. Please set your GEMINI_API_KEY in the AI Studio Secrets panel."
                )
            )
        }

        val url = "$BASE_URL$model:generateContent?key=$apiKey"
        val jsonPayload = requestAdapter.toJson(request)

        val httpRequest = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            val response = okHttpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMessage = parseErrorOrFallback(response.code, responseBody)
                return@withContext Result.failure(IOException(errorMessage))
            }

            val parsedResponse = responseAdapter.fromJson(responseBody)
            val text = parsedResponse?.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")

            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else if (parsedResponse?.candidates?.firstOrNull()?.finishReason != null) {
                val reason = parsedResponse.candidates.firstOrNull()?.finishReason
                Result.failure(IllegalStateException("Response generation stopped: $reason"))
            } else {
                Result.failure(IllegalStateException("Gemini returned an empty response. Please try again."))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error: Unable to connect to Gemini. Please check your internet connection.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorOrFallback(statusCode: Int, responseBody: String?): String {
        if (!responseBody.isNullOrBlank()) {
            try {
                val parsed = responseAdapter.fromJson(responseBody)
                val msg = parsed?.error?.message
                if (!msg.isNullOrBlank()) {
                    return when (statusCode) {
                        400 -> "Request error: $msg"
                        401, 403 -> "Authentication error: Please check your Gemini API key ($msg)"
                        429 -> "Rate limit reached: Gemini API quota exceeded. Please wait a moment and try again."
                        500, 503 -> "Google Gemini server is currently busy ($statusCode). Please try again shortly."
                        else -> "Gemini API ($statusCode): $msg"
                    }
                }
            } catch (_: Exception) {}
        }

        return when (statusCode) {
            400 -> "Bad request to Gemini API (HTTP 400)."
            401, 403 -> "Invalid or unauthorized API key (HTTP $statusCode)."
            429 -> "Too many requests. Please wait a few moments before sending another prompt."
            500, 503 -> "Google Gemini servers are temporarily unavailable. Please retry shortly."
            else -> "Unexpected error from Gemini service (HTTP $statusCode)."
        }
    }
}
