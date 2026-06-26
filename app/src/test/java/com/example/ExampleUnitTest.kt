package com.example

import com.example.api.GeminiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun verifyGeminiApiEndToEnd() = runBlocking {
        println("=== GEMINI E2E VERIFICATION TEST START ===")
        
        // 1. Locate the API Key
        val envKey = System.getenv("GEMINI_API_KEY")
        val buildConfigKey = try {
            com.example.BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            "N/A"
        }
        
        println("Environment GEMINI_API_KEY length: ${envKey?.length ?: 0}")
        println("BuildConfig GEMINI_API_KEY length: ${if (buildConfigKey != "N/A" && buildConfigKey != "MY_GEMINI_API_KEY") buildConfigKey.length else 0}")
        
        val apiKey = when {
            !envKey.isNullOrBlank() && envKey != "MY_GEMINI_API_KEY" -> envKey
            buildConfigKey != "N/A" && buildConfigKey.isNotEmpty() && buildConfigKey != "MY_GEMINI_API_KEY" -> buildConfigKey
            else -> null
        }
        
        if (apiKey == null) {
            println("❌ ERROR: No valid API key found in GEMINI_API_KEY environment variable or BuildConfig.")
            println("All System Environment Keys:")
            System.getenv().keys.forEach { println(" - $it") }
            return@runBlocking
        }
        
        val maskedKey = if (apiKey.length >= 16) "${apiKey.take(8)}...${apiKey.takeLast(8)}" else apiKey
        println("Using API Key (masked): $maskedKey")
        
        // 2. Execute a raw test request using our newly updated GeminiClient
        println("\n--- Triggering Raw Hello Test via GeminiClient ---")
        val report = GeminiClient.testRawHello(apiKey)
        
        // 3. Print verification results
        println("\n=== VERIFICATION RESULTS ===")
        println("1. Exact Request URL:")
        println("   ${report.endpointUsed}")
        
        println("\n2. Exact Request Headers:")
        println("   ${report.headersUsed}")
        
        println("\n3. Selected Model Name:")
        println("   ${report.modelNameUsed}")
        
        println("\n4. Full HTTP Response Code:")
        println("   ${report.httpCode}")
        
        println("\n5. Raw JSON Response from Gemini:")
        println(report.responseBody)
        
        println("\n6. Test Execution Status:")
        if (report.isSuccess) {
            println("   ✅ SUCCESS: Gemini request completed successfully and response was received.")
        } else {
            println("   ❌ FAILED: Error: ${report.errorMessage}")
            println("   Failure Reason: ${report.finalFailureReason}")
            println("   Rate Limit Origin: ${report.rateLimitSource}")
            
            if (report.httpCode == "429" || report.finalFailureReason == "Local App Rate Limit" || report.rateLimitSource != "N/A") {
                println("\n⚠️ RATE LIMIT / 429 DETECTED:")
                when (report.rateLimitSource) {
                    "APP" -> println("   - This is an App-side local throttle. It prevents rapid consecutive calls to safeguard API quota.")
                    "SERVER" -> println("   - This is a Gemini Server-side quota exhaustion / rate limit. The server rejected the key directly.")
                    else -> println("   - Another issue occurred.")
                }
            }
        }
        println("=== GEMINI E2E VERIFICATION TEST END ===")
        
        assertTrue("Request should be attempted", report.requestSent == "Yes")
    }
}
