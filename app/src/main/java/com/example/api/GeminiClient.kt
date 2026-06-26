package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeminiDebugReport(
    val savedKeyLength: Int = 0,
    val keyLoadedSuccessfully: String = "No",
    val requestSent: String = "No",
    val httpCode: String = "N/A",
    val responseBody: String = "N/A",
    val errorMessage: String = "",
    val modelNameUsed: String = "N/A",
    val totalRequestsCount: Int = 0,
    val finalFailureReason: String = "N/A",
    val endpointUsed: String = "N/A",
    val requestPayload: String = "N/A",
    val headersUsed: String = "N/A",
    val isSuccess: Boolean = false,
    val keyLoadedTruncated: String = "N/A",
    val rateLimitSource: String = "N/A" // "APP" or "SERVER" or "N/A"
)

interface GeminiApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("x-goog-api-key") xGoogApiKey: String?,
        @Header("Authorization") authorization: String?,
        @Query("key") apiKey: String?,
        @Body request: GeminiRequest
    ): GeminiResponse

    @POST
    suspend fun generateContentRaw(
        @Url url: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("x-goog-api-key") xGoogApiKey: String?,
        @Header("Authorization") authorization: String?,
        @Query("key") apiKey: String?,
        @Body request: GeminiRequest
    ): Response<ResponseBody>
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    const val SELECTED_MODEL = "models/gemini-2.5-flash"

    private val _latestReport = MutableStateFlow(GeminiDebugReport())
    val latestReport: StateFlow<GeminiDebugReport> = _latestReport.asStateFlow()

    private val totalRequestsCount = AtomicInteger(0)
    
    // Throttling & Deduplication states
    private var lastRequestTime = 0L
    private const val THROTTLE_MS = 1_000L // Reduced to 1 second for ultra-responsive testing
    private var lastPromptText: String? = null

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

    private fun extractTextFromJson(jsonString: String): String? {
        return try {
            val adapter = moshi.adapter(GeminiResponse::class.java)
            val response = adapter.fromJson(jsonString)
            response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Submits a structured prompt to Gemini model and gets its response text.
     * Uses sequential fallback for model: gemini-3.5-flash -> gemini-2.5-flash -> gemini-1.5-flash.
     */
    suspend fun getStrategyAdvice(promptText: String, customApiKey: String? = null): String {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        val keyLength = apiKey.length
        val keyLoaded = if (apiKey.isNotEmpty()) "Yes" else "No"
        val truncatedKey = if (apiKey.length >= 16) "${apiKey.take(8)}...${apiKey.takeLast(8)}" else apiKey

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val report = GeminiDebugReport(
                savedKeyLength = 0,
                keyLoadedSuccessfully = "No",
                requestSent = "No",
                httpCode = "N/A",
                responseBody = "N/A",
                errorMessage = "API Key is missing or empty.",
                modelNameUsed = "N/A",
                totalRequestsCount = totalRequestsCount.get(),
                finalFailureReason = "API Key is missing.",
                endpointUsed = "N/A",
                requestPayload = "N/A",
                headersUsed = "N/A",
                isSuccess = false,
                keyLoadedTruncated = "N/A"
            )
            _latestReport.value = report
            return "⚠️ Gemini API Key is missing. Please enter your Gemini API Key in the dashboard to unlock real-time tactical advice! (API key ka hona lazmi hai!)"
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime

        // Deduplication: if the exact prompt is sent again in less than 1 second, drop it
        if (promptText == lastPromptText && elapsed < 1000L) {
            return "⚠️ Duplicate request ignored."
        }

        // Strict throttle (unless direct manual test is triggered)
        if (elapsed < THROTTLE_MS) {
            val waitSecs = ((THROTTLE_MS - elapsed) / 1000) + 1
            val report = GeminiDebugReport(
                savedKeyLength = keyLength,
                keyLoadedSuccessfully = keyLoaded,
                requestSent = "No (Throttled)",
                httpCode = "N/A",
                responseBody = "N/A",
                errorMessage = "Throttled: Please wait $waitSecs seconds.",
                modelNameUsed = SELECTED_MODEL,
                totalRequestsCount = totalRequestsCount.get(),
                finalFailureReason = "Local App Rate Limit",
                endpointUsed = "N/A",
                requestPayload = "N/A",
                headersUsed = "N/A",
                isSuccess = false,
                keyLoadedTruncated = truncatedKey,
                rateLimitSource = "APP"
            )
            _latestReport.value = report
            return "⚠️ Local App Rate Limit: Wait ${waitSecs}s before next call to protect your API quota. (Double-click ya automatic entry control!)"
        }

        lastRequestTime = now
        lastPromptText = promptText

        val modelsToTry = listOf(SELECTED_MODEL)
        var lastErrorAdvice = ""
        
        val isOauthToken = apiKey.startsWith("ya29.") || apiKey.startsWith("Bearer ")
        val xGoogApiKeyHeader = if (isOauthToken) null else apiKey
        val apiKeyQueryParam = if (isOauthToken) null else apiKey
        val authHeader = if (isOauthToken) {
            if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"
        } else {
            null
        }

        val headersDesc = if (isOauthToken) {
            "Content-Type: application/json, Authorization: Bearer $truncatedKey"
        } else {
            "Content-Type: application/json, x-goog-api-key: $truncatedKey, key: $truncatedKey"
        }
        
        for (model in modelsToTry) {
            val urlPath = if (model.startsWith("models/")) {
                "v1beta/$model:generateContent"
            } else {
                "v1beta/models/$model:generateContent"
            }
            val fullUrl = "$BASE_URL$urlPath"
            val count = totalRequestsCount.incrementAndGet()

            val request = GeminiRequest(
                contents = listOf(
                    ContentPart(
                        parts = listOf(
                            TextPart(text = promptText)
                        )
                    )
                )
            )

            val requestAdapter = moshi.adapter(GeminiRequest::class.java)
            val requestJson = requestAdapter.toJson(request)

            try {
                val response = apiService.generateContentRaw(
                    url = urlPath,
                    contentType = "application/json",
                    xGoogApiKey = xGoogApiKeyHeader,
                    authorization = authHeader,
                    apiKey = apiKeyQueryParam,
                    request = request
                )
                val code = response.code()
                
                if (response.isSuccessful) {
                    val rawBody = response.body()?.string() ?: ""
                    val text = extractTextFromJson(rawBody)
                    if (text != null) {
                        val report = GeminiDebugReport(
                            savedKeyLength = keyLength,
                            keyLoadedSuccessfully = keyLoaded,
                            requestSent = "Yes",
                            httpCode = code.toString(),
                            responseBody = rawBody,
                            errorMessage = "None (Success)",
                            modelNameUsed = model,
                            totalRequestsCount = count,
                            finalFailureReason = "None (Success)",
                            endpointUsed = fullUrl,
                            requestPayload = requestJson,
                            headersUsed = headersDesc,
                            isSuccess = true,
                            keyLoadedTruncated = truncatedKey
                        )
                        _latestReport.value = report
                        return text
                    } else {
                        // Response body is successful but could not parse text candidates
                        val report = GeminiDebugReport(
                            savedKeyLength = keyLength,
                            keyLoadedSuccessfully = keyLoaded,
                            requestSent = "Yes",
                            httpCode = code.toString(),
                            responseBody = rawBody,
                            errorMessage = "Response has no text candidates",
                            modelNameUsed = model,
                            totalRequestsCount = count,
                            finalFailureReason = "Unparseable response structure",
                            endpointUsed = fullUrl,
                            requestPayload = requestJson,
                            headersUsed = headersDesc,
                            isSuccess = false,
                            keyLoadedTruncated = truncatedKey
                        )
                        _latestReport.value = report
                        lastErrorAdvice = "⚠️ Unexpected empty or invalid JSON schema."
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val mappedReason = when {
                        code == 400 && (errorBody.contains("API_KEY_INVALID") || errorBody.contains("not valid")) -> "Invalid API Key"
                        code == 403 -> "Invalid API Key"
                        code == 429 && (errorBody.contains("quota") || errorBody.contains("Quota") || errorBody.contains("exhausted")) -> "Quota Exceeded"
                        code == 429 -> "Rate Limited"
                        code == 404 -> "Model Not Found"
                        code == 503 -> "Service Unavailable"
                        else -> "HTTP $code Error"
                    }

                    val report = GeminiDebugReport(
                        savedKeyLength = keyLength,
                        keyLoadedSuccessfully = keyLoaded,
                        requestSent = "Yes",
                        httpCode = code.toString(),
                        responseBody = errorBody,
                        errorMessage = mappedReason,
                        modelNameUsed = model,
                        totalRequestsCount = count,
                        finalFailureReason = mappedReason,
                        endpointUsed = fullUrl,
                        requestPayload = requestJson,
                        headersUsed = headersDesc,
                        isSuccess = false,
                        keyLoadedTruncated = truncatedKey,
                        rateLimitSource = if (code == 429) "SERVER" else "N/A"
                    )
                    _latestReport.value = report
                    
                    // Stop trying other models if it's an API Key or billing/quota error
                    if (mappedReason == "Invalid API Key" || mappedReason == "Quota Exceeded" || mappedReason == "Rate Limited") {
                        return "⚠️ API Key/Quota issue: $mappedReason"
                    }
                    
                    lastErrorAdvice = "⚠️ API Server returned $code: $mappedReason"
                }
            } catch (e: Exception) {
                val isNetwork = e is java.io.IOException || e is java.net.ConnectException || e is java.net.UnknownHostException || e is java.net.SocketTimeoutException
                val mappedReason = if (isNetwork) "Network Error" else "Unknown: ${e.localizedMessage ?: e.javaClass.simpleName}"
                
                val report = GeminiDebugReport(
                    savedKeyLength = keyLength,
                    keyLoadedSuccessfully = keyLoaded,
                    requestSent = "Yes",
                    httpCode = "N/A",
                    responseBody = e.stackTraceToString().take(500),
                    errorMessage = mappedReason,
                    modelNameUsed = model,
                    totalRequestsCount = count,
                    finalFailureReason = mappedReason,
                    endpointUsed = fullUrl,
                    requestPayload = requestJson,
                    headersUsed = headersDesc,
                    isSuccess = false,
                    keyLoadedTruncated = truncatedKey
                )
                _latestReport.value = report
                lastErrorAdvice = "⚠️ Connection issue: $mappedReason"
            }
        }
        return lastErrorAdvice
    }

    /**
     * Dedicated Key Connection Test ("Reply with OK" simple prompt)
     * Walks through the sequence of models for diagnostic purposes.
     */
    suspend fun testApiKey(apiKey: String): GeminiDebugReport {
        return testRawHelloInternal(apiKey, "Reply with OK")
    }

    /**
     * Sends a simple "Hello Gemini! Reply with a short message." raw test to examine the response.
     */
    suspend fun testRawHello(apiKey: String): GeminiDebugReport {
        return testRawHelloInternal(apiKey, "Hello Gemini! Reply with a short message.")
    }

    /**
     * Dedicated API Validation Test with the simple prompt "Hello"
     */
    suspend fun testSimpleHello(apiKey: String): GeminiDebugReport {
        return testRawHelloInternal(apiKey, "Hello")
    }

    private suspend fun testRawHelloInternal(apiKey: String, prompt: String): GeminiDebugReport {
        val trimmedKey = apiKey.trim()
        val keyLength = trimmedKey.length
        val keyLoaded = if (trimmedKey.isNotEmpty()) "Yes" else "No"
        val count = totalRequestsCount.incrementAndGet()
        val truncatedKey = if (trimmedKey.length >= 16) "${trimmedKey.take(8)}...${trimmedKey.takeLast(8)}" else trimmedKey

        if (trimmedKey.isEmpty()) {
            val report = GeminiDebugReport(
                savedKeyLength = 0,
                keyLoadedSuccessfully = "No",
                requestSent = "No",
                httpCode = "N/A",
                responseBody = "N/A",
                errorMessage = "API Key is empty.",
                modelNameUsed = "N/A",
                totalRequestsCount = count,
                finalFailureReason = "API Key is empty.",
                endpointUsed = "N/A",
                requestPayload = "N/A",
                headersUsed = "N/A",
                isSuccess = false,
                keyLoadedTruncated = "N/A"
            )
            _latestReport.value = report
            return report
        }

        val modelsToTry = listOf(SELECTED_MODEL)
        var finalReport = GeminiDebugReport()

        val isOauthToken = trimmedKey.startsWith("ya29.") || trimmedKey.startsWith("Bearer ")
        val xGoogApiKeyHeader = if (isOauthToken) null else trimmedKey
        val apiKeyQueryParam = if (isOauthToken) null else trimmedKey
        val authHeader = if (isOauthToken) {
            if (trimmedKey.startsWith("Bearer ")) trimmedKey else "Bearer $trimmedKey"
        } else {
            null
        }

        val headersDesc = if (isOauthToken) {
            "Content-Type: application/json, Authorization: Bearer $truncatedKey"
        } else {
            "Content-Type: application/json, x-goog-api-key: $truncatedKey, key: $truncatedKey"
        }

        for (model in modelsToTry) {
            val urlPath = if (model.startsWith("models/")) {
                "v1beta/$model:generateContent"
            } else {
                "v1beta/models/$model:generateContent"
            }
            val fullUrl = "$BASE_URL$urlPath"

            val request = GeminiRequest(
                contents = listOf(
                    ContentPart(
                        parts = listOf(
                            TextPart(text = prompt)
                        )
                    )
                )
            )

            val requestAdapter = moshi.adapter(GeminiRequest::class.java)
            val requestJson = requestAdapter.toJson(request)

            try {
                val response = apiService.generateContentRaw(
                    url = urlPath,
                    contentType = "application/json",
                    xGoogApiKey = xGoogApiKeyHeader,
                    authorization = authHeader,
                    apiKey = apiKeyQueryParam,
                    request = request
                )
                val code = response.code()
                val rawBody = if (response.isSuccessful) {
                    response.body()?.string() ?: ""
                } else {
                    response.errorBody()?.string() ?: ""
                }

                val textParsed = if (response.isSuccessful) extractTextFromJson(rawBody) else null
                val isOk = response.isSuccessful && textParsed != null

                val reason = if (isOk) "None (Success)" else when {
                    code == 400 && (rawBody.contains("API_KEY_INVALID") || rawBody.contains("not valid")) -> "Invalid API Key"
                    code == 403 -> "Invalid API Key"
                    code == 429 && (rawBody.contains("quota") || rawBody.contains("Quota") || rawBody.contains("exhausted")) -> "Quota Exceeded"
                    code == 429 -> "Rate Limited"
                    code == 404 -> "Model Not Found"
                    code == 503 -> "Service Unavailable"
                    else -> "HTTP $code Error"
                }

                finalReport = GeminiDebugReport(
                    savedKeyLength = keyLength,
                    keyLoadedSuccessfully = keyLoaded,
                    requestSent = "Yes",
                    httpCode = code.toString(),
                    responseBody = rawBody,
                    errorMessage = if (isOk) "None (Success)" else reason,
                    modelNameUsed = model,
                    totalRequestsCount = count,
                    finalFailureReason = reason,
                    endpointUsed = fullUrl,
                    requestPayload = requestJson,
                    headersUsed = headersDesc,
                    isSuccess = isOk,
                    keyLoadedTruncated = truncatedKey,
                    rateLimitSource = if (code == 429) "SERVER" else "N/A"
                )

                _latestReport.value = finalReport
                
                // If it's fully successful, return immediately!
                if (isOk) {
                    return finalReport
                }
                
                // Stop early if the API key itself is bad/quota limited
                if (reason == "Invalid API Key" || reason == "Quota Exceeded" || reason == "Rate Limited") {
                    break
                }
            } catch (e: Exception) {
                val isNetwork = e is java.io.IOException || e is java.net.ConnectException || e is java.net.UnknownHostException || e is java.net.SocketTimeoutException
                val reason = if (isNetwork) "Network Error" else "Exception: ${e.localizedMessage ?: e.javaClass.simpleName}"

                finalReport = GeminiDebugReport(
                    savedKeyLength = keyLength,
                    keyLoadedSuccessfully = keyLoaded,
                    requestSent = "Yes",
                    httpCode = "N/A",
                    responseBody = e.stackTraceToString().take(500),
                    errorMessage = reason,
                    modelNameUsed = model,
                    totalRequestsCount = count,
                    finalFailureReason = reason,
                    endpointUsed = fullUrl,
                    requestPayload = requestJson,
                    headersUsed = headersDesc,
                    isSuccess = false,
                    keyLoadedTruncated = truncatedKey
                )
                _latestReport.value = finalReport
            }
        }
        return finalReport
    }

    suspend fun analyzeGame(
        apiKey: String,
        gameType: String,  // "RANGO" / "DRAGON_TIGER" / "AVIATOR" / "ANDAR_BAHAR" / "SEVEN_UP_DOWN"
        data: String,      // multipliers or history logs
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
}
