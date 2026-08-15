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

    // Preferred models with high daily quota on Google AI Studio Free Tier
    val SUPPORTED_MODELS = listOf(
        "AUTO" to "Auto Smart Fallback (High Quota 500 RPD)",
        "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite (500 RPD)",
        "gemini-3.1-flash-lite" to "Gemini 3.1 Flash Lite (500 RPD)",
        "gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite (20 RPD)",
        "gemini-2.5-flash" to "Gemini 2.5 Flash (20 RPD)",
        "gemini-2.0-flash" to "Gemini 2.0 Flash",
        "gemini-1.5-flash" to "Gemini 1.5 Flash"
    )

    private val _selectedModelPref = MutableStateFlow("AUTO")
    val selectedModelPref: StateFlow<String> = _selectedModelPref.asStateFlow()

    fun setSelectedModel(model: String) {
        _selectedModelPref.value = model
    }

    private val _shareBalanceWithAi = MutableStateFlow(false)
    val shareBalanceWithAi: StateFlow<Boolean> = _shareBalanceWithAi.asStateFlow()

    fun setShareBalanceWithAi(enabled: Boolean) {
        _shareBalanceWithAi.value = enabled
    }

    private val _latestReport = MutableStateFlow(GeminiDebugReport())
    val latestReport: StateFlow<GeminiDebugReport> = _latestReport.asStateFlow()

    private val totalRequestsCount = AtomicInteger(0)
    
    // Throttling & Deduplication states
    private var lastRequestTime = 0L
    private const val THROTTLE_MS = 1_000L // 1 second responsive throttle
    private var lastPromptText: String? = null

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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

    private fun getCandidateModels(): List<String> {
        val userPref = _selectedModelPref.value
        return if (userPref != "AUTO" && userPref.isNotBlank()) {
            listOf(userPref, "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash").distinct()
        } else {
            listOf(
                "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite",
                "gemini-2.5-flash-lite",
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash"
            )
        }
    }

    /**
     * Submits a structured prompt to Gemini model and gets its response text.
     * Uses automatic fallback on rate limit / quota exhaustion across high-quota models.
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
            return "⚠️ Gemini API Key is missing. Please enter your Gemini API Key in the dashboard to unlock real-time tactical advice!"
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime

        // Deduplication: if the exact prompt is sent again in less than 1 second, drop it
        if (promptText == lastPromptText && elapsed < 1000L) {
            return "⚠️ Duplicate request ignored."
        }

        // Strict throttle
        if (elapsed < THROTTLE_MS) {
            val waitSecs = ((THROTTLE_MS - elapsed) / 1000) + 1
            val report = GeminiDebugReport(
                savedKeyLength = keyLength,
                keyLoadedSuccessfully = keyLoaded,
                requestSent = "No (Throttled)",
                httpCode = "N/A",
                responseBody = "N/A",
                errorMessage = "Throttled: Please wait $waitSecs seconds.",
                modelNameUsed = getCandidateModels().firstOrNull() ?: "AUTO",
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
            return "⚠️ Local App Rate Limit: Wait ${waitSecs}s before next call to protect your API quota."
        }

        lastRequestTime = now
        lastPromptText = promptText

        val modelsToTry = getCandidateModels()
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
        
        for ((index, model) in modelsToTry.withIndex()) {
            val cleanModel = model.removePrefix("models/")
            val urlPath = "v1beta/models/$cleanModel:generateContent"
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
                        val modelLabel = if (index > 0) "$cleanModel (Fallback from ${modelsToTry.first()})" else cleanModel
                        val report = GeminiDebugReport(
                            savedKeyLength = keyLength,
                            keyLoadedSuccessfully = keyLoaded,
                            requestSent = "Yes",
                            httpCode = code.toString(),
                            responseBody = rawBody,
                            errorMessage = "None (Success)",
                            modelNameUsed = modelLabel,
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
                        val report = GeminiDebugReport(
                            savedKeyLength = keyLength,
                            keyLoadedSuccessfully = keyLoaded,
                            requestSent = "Yes",
                            httpCode = code.toString(),
                            responseBody = rawBody,
                            errorMessage = "Response has no text candidates",
                            modelNameUsed = cleanModel,
                            totalRequestsCount = count,
                            finalFailureReason = "Unparseable response structure",
                            endpointUsed = fullUrl,
                            requestPayload = requestJson,
                            headersUsed = headersDesc,
                            isSuccess = false,
                            keyLoadedTruncated = truncatedKey
                        )
                        _latestReport.value = report
                        lastErrorAdvice = "⚠️ Unexpected empty response format."
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val mappedReason = when {
                        code == 400 && (errorBody.contains("API_KEY_INVALID") || errorBody.contains("not valid")) -> "Invalid API Key"
                        code == 403 -> "Invalid API Key / Access Denied"
                        code == 429 && (errorBody.contains("quota") || errorBody.contains("Quota") || errorBody.contains("exhausted")) -> "Quota Exceeded on $cleanModel"
                        code == 429 -> "Rate Limited on $cleanModel"
                        code == 404 -> "Model Not Found ($cleanModel)"
                        code == 503 -> "Service Unavailable ($cleanModel)"
                        else -> "HTTP $code Error ($cleanModel)"
                    }

                    val report = GeminiDebugReport(
                        savedKeyLength = keyLength,
                        keyLoadedSuccessfully = keyLoaded,
                        requestSent = "Yes",
                        httpCode = code.toString(),
                        responseBody = errorBody,
                        errorMessage = mappedReason,
                        modelNameUsed = cleanModel,
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
                    
                    // If the API Key itself is invalid, do not spam other models
                    if (mappedReason.contains("Invalid API Key")) {
                        return "⚠️ Invalid API Key. Please check your Gemini API key in the dashboard."
                    }
                    
                    // If it's 429 quota exhaustion or 404 model not found, try NEXT model in chain!
                    lastErrorAdvice = "⚠️ Quota limit on $cleanModel. Falling back to alternative model..."
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
                    modelNameUsed = cleanModel,
                    totalRequestsCount = count,
                    finalFailureReason = mappedReason,
                    endpointUsed = fullUrl,
                    requestPayload = requestJson,
                    headersUsed = headersDesc,
                    isSuccess = false,
                    keyLoadedTruncated = truncatedKey
                )
                _latestReport.value = report
                lastErrorAdvice = "⚠️ Connection issue on $cleanModel: $mappedReason"
            }
        }
        return lastErrorAdvice.ifBlank { "⚠️ All Gemini models reached quota limit. Set up billing or wait for quota reset." }
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

        val modelsToTry = getCandidateModels()
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

        for ((index, model) in modelsToTry.withIndex()) {
            val cleanModel = model.removePrefix("models/")
            val urlPath = "v1beta/models/$cleanModel:generateContent"
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
                val modelLabel = if (index > 0) "$cleanModel (Fallback from ${modelsToTry.first()})" else cleanModel

                val reason = if (isOk) "None (Success)" else when {
                    code == 400 && (rawBody.contains("API_KEY_INVALID") || rawBody.contains("not valid")) -> "Invalid API Key"
                    code == 403 -> "Invalid API Key / Access Denied"
                    code == 429 && (rawBody.contains("quota") || rawBody.contains("Quota") || rawBody.contains("exhausted")) -> "Quota Exceeded on $cleanModel"
                    code == 429 -> "Rate Limited on $cleanModel"
                    code == 404 -> "Model Not Found ($cleanModel)"
                    code == 503 -> "Service Unavailable ($cleanModel)"
                    else -> "HTTP $code Error ($cleanModel)"
                }

                finalReport = GeminiDebugReport(
                    savedKeyLength = keyLength,
                    keyLoadedSuccessfully = keyLoaded,
                    requestSent = "Yes",
                    httpCode = code.toString(),
                    responseBody = rawBody,
                    errorMessage = if (isOk) "None (Success)" else reason,
                    modelNameUsed = modelLabel,
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
                
                // Stop early only if the API key itself is bad
                if (reason.contains("Invalid API Key")) {
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
                    modelNameUsed = cleanModel,
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
        gameType: String,  // "RANGO" / "DRAGON_TIGER" / "AVIATOR" / "ANDAR_BAHAR" / "SEVEN_UP_DOWN" / "BACCARAT" / "ROULETTE" / "MINES" / "CAR_ROULETTE"
        data: String,      // multipliers or history logs
        balance: Double,
        trendLabel: String,
        includeBalance: Boolean = _shareBalanceWithAi.value
    ): String {
        val balanceInfo = if (includeBalance && balance > 0.0) "\nWALLET BALANCE: PKR $balance" else ""

        val prompt = when (gameType) {
            "BACCARAT" -> """
                You are an advanced, open-minded AI Casino Intelligence Engine.
                You have FULL AUTONOMY: do NOT lock yourself into any single rigid strategy or legacy road rule.
                
                GAME: Baccarat$balanceInfo
                RECENT ROUNDS (Newest first): $data (P=Player, B=Banker, T/TIE=Tie)
                CURRENT FLOW: $trendLabel
                
                ANALYTICAL INSTRUCTIONS:
                - Autonomously evaluate the real-time sequence for streaks, alternating ping-pong patterns, clustering, momentum shifts, and probability deviations.
                - Determine whether the current table trend will continue, reverse, or if the market is too choppy.
                - Make a decisive tactical recommendation with high statistical confidence.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [PLAYER / BANKER / TIE / STANDBY]
                REASON: [Sharp 1-sentence explanation of the dynamic pattern and why this is the best move]
            """.trimIndent()

            "DRAGON_TIGER" -> """
                You are an advanced, open-minded AI Casino Intelligence Engine.
                You have FULL AUTONOMY: do NOT lock yourself into any single rigid strategy or fixed formula.
                
                GAME: Dragon Tiger$balanceInfo
                RECENT ROUNDS (Newest first): $data (D=Dragon, T=Tiger, X/P/TIE=Tie)
                CURRENT FLOW: $trendLabel
                
                ANALYTICAL INSTRUCTIONS:
                - Freely analyze the natural sequence for streak continuation, zigzag alternation, clumping, breakout transitions, or tie probability.
                - Understand how the table is behaving right now without rigid preconceptions.
                - Select the most probable winning side based on dynamic pattern intelligence.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [DRAGON / TIGER / TIE / STANDBY]
                REASON: [Sharp 1-sentence explanation of the dynamic pattern and why this is the best move]
            """.trimIndent()
            
            "ANDAR_BAHAR" -> """
                You are an advanced, open-minded AI Casino Intelligence Engine.
                You have FULL AUTONOMY: do NOT lock yourself into any single rigid strategy.
                
                GAME: Andar Bahar$balanceInfo
                RECENT ROUNDS (Newest first): $data (A=Andar, B=Bahar)
                CURRENT FLOW: $trendLabel
                
                ANALYTICAL INSTRUCTIONS:
                - Autonomously analyze side repetition, streak momentum, balance distribution, and reversal frequency.
                - Formulate the sharpest prediction for the upcoming deal based on live table dynamics.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [ANDAR / BAHAR / STANDBY]
                REASON: [Sharp 1-sentence explanation of the dynamic pattern and why this is the best move]
            """.trimIndent()
            
            "SEVEN_UP_DOWN" -> """
                You are an advanced, open-minded AI Casino Intelligence Engine.
                You have FULL AUTONOMY: do NOT lock yourself into any single rigid strategy.
                
                GAME: 7 Up Down (Dice)$balanceInfo
                RECENT ROUNDS (Newest first): $data (U=7 Up [8-12], D=7 Down [2-6], 7=Lucky Seven)
                CURRENT FLOW: $trendLabel
                
                ANALYTICAL INSTRUCTIONS:
                - Freely evaluate high vs low dice distribution, streak clustering, and mean reversion likelihood.
                - Deliver a decisive recommendation for the next roll based on dynamic pattern intelligence.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [UP / DOWN / SEVEN / STANDBY]
                REASON: [Sharp 1-sentence explanation of the dynamic pattern and why this is the best move]
            """.trimIndent()

            "ROULETTE" -> """
                You are an advanced, open-minded AI Casino Intelligence Engine.
                You have FULL AUTONOMY: do NOT lock yourself into any single rigid strategy.
                
                GAME: European Roulette$balanceInfo
                RECENT SPINS (Newest first): $data
                CURRENT FLOW: $trendLabel
                
                ANALYTICAL INSTRUCTIONS:
                - Autonomously analyze color runs (Red/Black), parity (Even/Odd), range (High/Low), dozens, and column momentum.
                - Spot cold-to-hot transitions and wheel distribution patterns dynamically.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [BET RED / BET BLACK / BET EVEN / BET ODD / BET 1ST DOZEN / BET 2ND DOZEN / BET 3RD DOZEN / STANDBY]
                REASON: [Sharp 1-sentence explanation of the dynamic pattern and why this is the best move]
            """.trimIndent()
            
            else -> """
                You are an advanced, open-minded AI Game Intelligence Engine.
                You have FULL AUTONOMY: do NOT lock yourself into any single rigid strategy.
                
                GAME: $gameType (Multiplier / Crash / Rocket)$balanceInfo
                RECENT MULTIPLIERS (Newest first): $data
                CURRENT FLOW: $trendLabel
                
                ANALYTICAL INSTRUCTIONS:
                - Autonomously evaluate multiplier volatility, bust clusters, peak cycle intervals, and risk-to-reward ratio.
                - Advise whether to bet with an optimal safe cashout target (e.g. 1.85x, 2.10x) or skip this round.
                
                Your response MUST be formatted exactly as below (maximum 2 lines total, concise, sharp and direct, no disclaimers):
                RECOMMENDATION: [BET / CASHOUT @ [X]x / SKIP]
                REASON: [Sharp 1-sentence explanation of the dynamic pattern and why this is the best move]
            """.trimIndent()
        }
        return getStrategyAdvice(prompt, apiKey)
    }
}
