package com.example.data

object AndarBaharAnalyzer {

    data class ABResult(
        val trendLabel: String,     // "ANDAR STREAK" / "BAHAR STREAK" / "BALANCED ROAD" / "COLLECTING"
        val trendEmoji: String,
        val confidence: Int,
        val predictedNext: String,  // "ANDAR" / "BAHAR" / "UNCERTAIN"
        val andarPct: Int,
        val baharPct: Int,
        val currentStreak: String,
        val streakCount: Int,
        val advice: String,
        val riskLevel: String,
        val suggestedBet: String,   // "BET ANDAR" / "BET BAHAR" / "WAIT"
        val hotSide: String         // "ANDAR" / "BAHAR" / "BALANCED"
    )

    fun analyze(rounds: List<AndarBaharRound>): ABResult {
        if (rounds.size < 6) return collectingResult()

        val recent = rounds.take(20)
        val aCount = recent.count { it.result == "A" }
        val bCount = recent.count { it.result == "B" }
        val total = recent.size

        val andarPct = (aCount * 100 / total)
        val baharPct = (bCount * 100 / total)

        // Streak detection
        val currentSide = rounds.firstOrNull()?.result ?: "UNCERTAIN"
        var streakCount = 0
        for (r in rounds) {
            if (r.result == currentSide) streakCount++ else break
        }

        // Hot side
        val hotSide = when {
            aCount > bCount * 1.5 -> "ANDAR"
            bCount > aCount * 1.5 -> "BAHAR"
            else -> "BALANCED"
        }

        // Simple road columns generator for AB
        val chronological = rounds.take(30).reversed()
        val columns = mutableListOf<MutableList<String>>()
        for (round in chronological) {
            val res = round.result
            if (columns.isEmpty() || columns.last().first() != res) {
                columns.add(mutableListOf(res))
            } else {
                columns.last().add(res)
            }
        }

        // Apply a multi-road decision logic specifically tuned for Andar Bahar
        var continuationScore = 0
        var reversalScore = 0

        // Heuristic 1: Win rates deviation
        if (andarPct > 60 && currentSide == "A") continuationScore++
        if (baharPct > 60 && currentSide == "B") continuationScore++
        if (andarPct > 60 && currentSide == "B") reversalScore++
        if (baharPct > 60 && currentSide == "A") reversalScore++

        // Heuristic 2: Column size pattern
        if (columns.size >= 2) {
            val lastColSize = columns.last().size
            val prevColSize = columns[columns.size - 2].size
            if (lastColSize < prevColSize) {
                continuationScore++
            } else {
                reversalScore++
            }
        }

        // Heuristic 3: Alternating detection
        var isAlternating = true
        if (columns.size >= 4) {
            for (i in columns.size - 4 until columns.size) {
                if (columns[i].size > 2) {
                    isAlternating = false
                    break
                }
            }
        } else {
            isAlternating = false
        }
        if (isAlternating) {
            reversalScore += 2
        }

        val decision = when {
            continuationScore > reversalScore -> "CONTINUE"
            reversalScore > continuationScore -> "REVERSAL"
            else -> "UNCERTAIN"
        }

        val predictedNext = when {
            decision == "CONTINUE" && currentSide != "UNCERTAIN" -> currentSide
            decision == "REVERSAL" && currentSide != "UNCERTAIN" -> if (currentSide == "A") "B" else "A"
            else -> "UNCERTAIN"
        }

        val suggestedBet = when (predictedNext) {
            "A" -> "BET ANDAR"
            "B" -> "BET BAHAR"
            else -> "WAIT"
        }

        val confidence = when {
            decision != "UNCERTAIN" && Math.abs(continuationScore - reversalScore) >= 2 -> 80
            decision != "UNCERTAIN" -> 65
            else -> 48
        }

        val riskLevel = when {
            confidence >= 75 -> "LOW RISK"
            confidence >= 60 -> "MED RISK"
            else -> "HIGH RISK"
        }

        val trendLabel = when {
            streakCount >= 4 && currentSide == "A" -> "ANDAR STREAK"
            streakCount >= 4 && currentSide == "B" -> "BAHAR STREAK"
            isAlternating -> "ALTERNATING ROAD"
            else -> "BALANCED ROAD"
        }

        val displayCurrentSide = if (currentSide == "A") "Andar" else "Bahar"
        val displayPredicted = if (predictedNext == "A") "ANDAR" else if (predictedNext == "B") "BAHAR" else "UNCERTAIN"

        val advice = when {
            isAlternating -> "🔄 Alternating pattern detected! Recommend betting opposite of the last winner ($displayPredicted)."
            streakCount >= 3 -> "🔥 Long $displayCurrentSide streak ($streakCount rounds). Local engine suggests ${if (decision == "CONTINUE") "riding the streak" else "expecting a pattern breakdown soon"}."
            decision == "CONTINUE" -> "📈 Trend continuation indicated. Suggested play: Bet on $displayPredicted."
            decision == "REVERSAL" -> "📉 Signal indicates pattern reversal. Suggested play: Bet on $displayPredicted."
            else -> "⚖️ Evenly balanced signals. Consider waiting for the next round to confirm direction."
        }

        return ABResult(
            trendLabel = trendLabel,
            trendEmoji = if (trendLabel.contains("ANDAR")) "🚪" else if (trendLabel.contains("BAHAR")) "🌌" else "⚖️",
            confidence = confidence,
            predictedNext = displayPredicted,
            andarPct = andarPct,
            baharPct = baharPct,
            currentStreak = if (streakCount > 1) "${if (currentSide == "A") "ANDAR" else "BAHAR"} x$streakCount" else "NONE",
            streakCount = streakCount,
            advice = advice,
            riskLevel = riskLevel,
            suggestedBet = suggestedBet,
            hotSide = hotSide
        )
    }

    private fun collectingResult() = ABResult(
        trendLabel = "COLLECTING",
        trendEmoji = "📊",
        confidence = 0,
        predictedNext = "UNCERTAIN",
        andarPct = 0,
        baharPct = 0,
        currentStreak = "NONE",
        streakCount = 0,
        advice = "Capture at least 6 rounds to initialize Andar Bahar metrics and patterns.",
        riskLevel = "MED RISK",
        suggestedBet = "WAIT",
        hotSide = "BALANCED"
    )
}
