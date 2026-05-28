package com.example.util

import android.content.Context
import android.util.Base64

object SecurePrefs {
    private const val PREFS_NAME = "rango_secure_credentials"
    private const val KEY_GEMINI_API_KEY = "encrypted_gemini_api_key"

    fun saveGeminiApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obfuscated = Base64.encodeToString(key.toByteArray(), Base64.DEFAULT)
        prefs.edit().putString(KEY_GEMINI_API_KEY, obfuscated).apply()
    }

    fun getGeminiApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obfuscated = prefs.getString(KEY_GEMINI_API_KEY, null) ?: return ""
        return try {
            String(Base64.decode(obfuscated, Base64.DEFAULT)).trim()
        } catch (e: Exception) {
            ""
        }
    }
}
