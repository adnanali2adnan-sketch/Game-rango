package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import retrofit2.HttpException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeminiDebugReport(
    val savedKeyLength: Int = 0,
    val keyLoadedSuccessfully: String = "No",
    val requestSent: String = "No",
    val httpCode: String = "N/A",
    val responseBody: String = "N/A",
    val finalFailureReason: String = "N/A",
    val isSuccess: Boolean = false
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val _latestReport = MutableStateFlow(GeminiDebugReport())
    val latestReport: StateFlow<GeminiDebugReport> = _latestReport.asStateFlow()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Submits a structured prompt to Gemini model and gets its response text.
     */
    suspend fun getStrategyAdvice(promptText: String, customApiKey: String? = null): String {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "⚠️ Gemini API Key is missing. Please enter your Gemini API Key in the dashboard to unlock real-time tactical advice! (API key ka hona lazmi hai!)"
        }

        val request = GeminiRequest(
            contents = listOf(
                ContentPart(
                    parts = listOf(
                        TextPart(text = promptText)
                    )
                )
            )
        )

        return try {
            // Try gemini-3.5-flash
            val response = apiService.generateContent("gemini-3.5-flash", apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No advice returned. (Koi response nahi aaya)."
        } catch (e: HttpException) {
            if (e.code() == 404) {
                try {
                    // Fallback to gemini-1.5-flash if gemini-3.5-flash is not found (404)
                    val response = apiService.generateContent("gemini-1.5-flash", apiKey, request)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "No advice returned. (Koi response nahi aaya)."
                } catch (innerEx: HttpException) {
                    parseHttpError(innerEx)
                } catch (innerEx: Exception) {
                    parseGeneralError(innerEx)
                }
            } else {
                parseHttpError(e)
            }
        } catch (e: Exception) {
            parseGeneralError(e)
        }
    }

    private fun parseHttpError(e: HttpException): String {
        val code = e.code()
        val rawBody = e.response()?.errorBody()?.string() ?: ""
        val parsedReason = when (code) {
            400 -> {
                if (rawBody.contains("API_KEY_INVALID") || rawBody.contains("not valid")) {
                    "Invalid API Key (HTTP 400)"
                } else {
                    "Bad Request (HTTP 400)"
                }
            }
            403 -> "Permission Denied / Invalid API Key (HTTP 403)"
            404 -> "Model Not Found (HTTP 404)"
            429 -> "Rate Limited / Quota Exceeded (HTTP 429)"
            else -> "HTTP $code Error"
        }
        val errorMsg = if (rawBody.isNotEmpty()) {
            if (rawBody.contains("\"message\"")) {
                val msg = rawBody.substringAfter("\"message\"").substringAfter("\"").substringBefore("\"")
                "API Error ($parsedReason): $msg"
            } else {
                "API Error ($parsedReason): $rawBody"
            }
        } else {
            "API Error ($parsedReason)"
        }
        return "API Server connection issue: $errorMsg"
    }

    private fun parseGeneralError(e: Exception): String {
        return if (e is java.io.IOException) {
            "API Server connection issue: Network Error"
        } else {
            "API Server connection issue: ${e.localizedMessage ?: "Unknown Error"}"
        }
    }

    suspend fun analyzeGame(
        apiKey: String,
        gameType: String,  // "RANGO" / "DRAGON_TIGER" / "AVIATOR"
        data: String,      // multipliers for crash, D/T/TIE history for card game
        balance: Double,
        trendLabel: String
    ): String {
        val prompt = when (gameType) {
            "DRAGON_TIGER" -> """
                You are elite Dragon Tiger card game advisor.
                Recent results (newest first): $data (D=Dragon,T=Tiger,X/P=Tie)
                Balance: PKR $balance. Current trend: $trendLabel.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, extremely concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [DRAGON / TIGER / TIE / STANDBY]
                REASON: [Short 1-sentence explanation of why]
            """.trimIndent()
            
            "ANDAR_BAHAR" -> """
                You are elite Andar Bahar card game advisor.
                Recent results (newest first): $data (A=Andar, B=Bahar)
                Balance: PKR $balance. Current trend: $trendLabel.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, extremely concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [ANDAR / BAHAR / STANDBY]
                REASON: [Short 1-sentence explanation of why]
            """.trimIndent()
            
            "SEVEN_UP_DOWN" -> """
                You are elite 7 Up Down dice game advisor.
                Recent results (newest first): $data (U=7 Up, D=7 Down, 7=Seven)
                Balance: PKR $balance. Current trend: $trendLabel.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, extremely concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [UP / DOWN / SEVEN / STANDBY]
                REASON: [Short 1-sentence explanation of why]
            """.trimIndent()
            
            else -> """
                You are crash game advisor for $gameType.
                Recent multipliers: $data
                Balance: PKR $balance. Trend: $trendLabel.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, extremely concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [BET / CASHOUT @ [X]x / SKIP]
                REASON: [Short 1-sentence explanation of why]
            """.trimIndent()
        }
        return getStrategyAdvice(prompt, apiKey)
    }

    suspend fun testApiKey(apiKey: String): GeminiDebugReport {
        val trimmedKey = apiKey.trim()
        val keyLength = trimmedKey.length
        val keyLoaded = if (trimmedKey.isNotEmpty()) "Yes" else "No"
        
        if (trimmedKey.isEmpty()) {
            val report = GeminiDebugReport(
                savedKeyLength = 0,
                keyLoadedSuccessfully = "No",
                requestSent = "No",
                httpCode = "N/A",
                responseBody = "N/A",
                finalFailureReason = "API Key is empty.",
                isSuccess = false
            )
            _latestReport.value = report
            return report
        }

        val request = GeminiRequest(
            contents = listOf(
                ContentPart(
                    parts = listOf(
                        TextPart(text = "Reply with OK")
                    )
                )
            )
        )

        return try {
            // Try with gemini-3.5-flash
            val response = apiService.generateContent("gemini-3.5-flash", trimmedKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val isOk = responseText.isNotEmpty()
            
            val report = GeminiDebugReport(
                savedKeyLength = keyLength,
                keyLoadedSuccessfully = keyLoaded,
                requestSent = "Yes",
                httpCode = "200",
                responseBody = if (responseText.length > 100) responseText.take(100) + "..." else responseText,
                finalFailureReason = if (isOk) "None (Success)" else "Response text is empty",
                isSuccess = isOk
            )
            _latestReport.value = report
            report
        } catch (e: HttpException) {
            if (e.code() == 404) {
                // If gemini-3.5-flash fails with 404, try falling back to gemini-1.5-flash for the test!
                try {
                    val response = apiService.generateContent("gemini-1.5-flash", trimmedKey, request)
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    val isOk = responseText.isNotEmpty()
                    
                    val report = GeminiDebugReport(
                        savedKeyLength = keyLength,
                        keyLoadedSuccessfully = keyLoaded,
                        requestSent = "Yes",
                        httpCode = "200 (via gemini-1.5-flash fallback)",
                        responseBody = if (responseText.length > 100) responseText.take(100) + "..." else responseText,
                        finalFailureReason = if (isOk) "None (Success)" else "Response text is empty",
                        isSuccess = isOk
                    )
                    _latestReport.value = report
                    report
                } catch (innerEx: HttpException) {
                    buildReportFromHttpException(innerEx, keyLength, keyLoaded)
                } catch (innerEx: Exception) {
                    buildReportFromGeneralException(innerEx, keyLength, keyLoaded)
                }
            } else {
                buildReportFromHttpException(e, keyLength, keyLoaded)
            }
        } catch (e: Exception) {
            buildReportFromGeneralException(e, keyLength, keyLoaded)
        }
    }

    private fun buildReportFromHttpException(e: HttpException, keyLength: Int, keyLoaded: String): GeminiDebugReport {
        val code = e.code()
        val rawBody = e.response()?.errorBody()?.string() ?: "Empty error body"
        val failureReason = when (code) {
            400 -> {
                if (rawBody.contains("API_KEY_INVALID") || rawBody.contains("not valid")) {
                    "Invalid API Key"
                } else {
                    "Bad Request (HTTP 400)"
                }
            }
            403 -> "Permission Denied / Invalid API Key (HTTP 403)"
            404 -> "Model Not Found / Invalid Endpoint (HTTP 404). Your key might not support gemini-3.5-flash."
            429 -> "Rate Limited / Quota Exceeded (HTTP 429)"
            else -> "HTTP $code Error"
        }
        val report = GeminiDebugReport(
            savedKeyLength = keyLength,
            keyLoadedSuccessfully = keyLoaded,
            requestSent = "Yes",
            httpCode = code.toString(),
            responseBody = rawBody,
            finalFailureReason = failureReason,
            isSuccess = false
        )
        _latestReport.value = report
        return report
    }

    private fun buildReportFromGeneralException(e: Exception, keyLength: Int, keyLoaded: String): GeminiDebugReport {
        val failureReason = if (e is java.io.IOException) "Network Error" else "Unknown Exception: ${e.javaClass.simpleName}"
        val report = GeminiDebugReport(
            savedKeyLength = keyLength,
            keyLoadedSuccessfully = keyLoaded,
            requestSent = "Yes",
            httpCode = "N/A",
            responseBody = e.localizedMessage ?: e.toString(),
            finalFailureReason = failureReason,
            isSuccess = false
        )
        _latestReport.value = report
        return report
    }
}
