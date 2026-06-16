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
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

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
            return "Note: Gemini API Key is missing. Please enter your Gemini API Key in the dashboard to unlock real-time tactical advice! (API key ka hona lazmi hai!)"
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
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No advice returned. (Koi response nahi aaya)."
        } catch (e: Exception) {
            "API Server connection issue: ${e.localizedMessage}\n\nPlease check your Internet and ensure your API Key is valid."
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
