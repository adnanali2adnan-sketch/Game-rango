package com.example.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<ContentPart>
)

@JsonClass(generateAdapter = true)
data class ContentPart(
    val parts: List<TextPart>
)

@JsonClass(generateAdapter = true)
data class TextPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentPart? = null
)
