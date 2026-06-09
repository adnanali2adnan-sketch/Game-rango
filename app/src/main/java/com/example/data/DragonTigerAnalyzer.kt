package com.example.data

object DragonTigerAnalyzer {
    
    data class DTResult(
        val trendLabel: String,     // "DRAGON STREAK" / "TIGER STREAK" / "ALTERNATING" / "BALANCED" / "COLLECTING"
        val trendEmoji: String,
        val confidence: Int,
        val predictedNext: String,  // "DRAGON" / "TIGER" / "TIE POSSIBLE" / "UNCERTAIN"
        val dragonPct: Int,         // % of recent rounds Dragon won
        val tigerPct: Int,
        val tiePct: Int,
        val currentStreak: String,  // "DRAGON x3" / "TIGER x2" / etc
        val streakCount: Int,
        val advice: String,
        val riskLevel: String,
        val suggestedBet: String,   // "BET DRAGON" / "BET TIGER" / "SKIP" / "WAIT"
        val hotSide: String,        // "DRAGON" / "TIGER" / "BALANCED"
        val tieWarning: Boolean     // true if tie probability elevated
    )
    
    fun analyze(rounds: List<DragonTigerRound>): DTResult {
        if (rounds.size < 3) return collectingResult()
        
        val recent = rounds.take(20)
        val dCount = recent.count { it.result == "D" }
        val tCount = recent.count { it.result == "T" }
        val tieCount = recent.count { it.result == "TIE" || it.result == "X" || it.result == "P" }
        val total = recent.size
        
        val dragonPct = (dCount * 100 / total)
        val tigerPct = (tCount * 100 / total)
        val tiePct = (tieCount * 100 / total)
        
        // Detect current streak (ignoring ties)
        var streakSide = recent.first().result
        var streakCount = 0
        for (r in recent) {
            if (r.result == streakSide && r.result != "TIE" && r.result != "X" && r.result != "P") streakCount++
            else if (r.result == "TIE" || r.result == "X" || r.result == "P") continue
            else break
        }
        
        // Detect alternating pattern (D,T,D,T or T,D,T,D)
        val isAlternating = recent.filter { it.result != "TIE" && it.result != "X" && it.result != "P" }.take(6).zipWithNext().all { (a, b) ->
            a.result != b.result
        }
        
        // Hot side
        val hotSide = when {
            dCount > tCount * 1.5 -> "DRAGON"
            tCount > dCount * 1.5 -> "TIGER"
            else -> "BALANCED"
        }
        
        // Tie warning: if no tie in last 12+ rounds, tie is overdue
        val roundsSinceTie = rounds.indexOfFirst { it.result == "TIE" || it.result == "X" || it.result == "P" }.let { if (it == -1) rounds.size else it }
        val tieWarning = roundsSinceTie >= 12
        val suggestSkipForTie = roundsSinceTie in 12..14
        
        // Trend label
        val trendLabel = when {
            streakCount >= 4 && streakSide == "D" -> "DRAGON STREAK"
            streakCount >= 4 && streakSide == "T" -> "TIGER STREAK"
            isAlternating -> "ALTERNATING"
            dragonPct > 60 -> "DRAGON DOMINANT"
            tigerPct > 60 -> "TIGER DOMINANT"
            else -> "BALANCED"
        }
        
        // Prediction (Only skip/TIE-possible on rounds 12, 13, 14. After that, resume predicting regular sides)
        val predictedNext = when {
            suggestSkipForTie -> "TIE POSSIBLE"
            streakCount >= 5 -> if (streakSide == "D") "TIGER" else "DRAGON" // streak break likely
            isAlternating -> if (recent.first().result == "D") "TIGER" else "DRAGON"
            hotSide == "DRAGON" -> "DRAGON"
            hotSide == "TIGER" -> "TIGER"
            else -> "UNCERTAIN"
        }
        
        // Suggested bet
        val suggestedBet = when {
            suggestSkipForTie -> "SKIP (TIE DUE)"
            predictedNext == "UNCERTAIN" -> "WAIT"
            else -> "BET $predictedNext"
        }
        
        val confidence = when {
            streakCount >= 5 -> 75
            isAlternating -> 70
            hotSide != "BALANCED" -> 60
            else -> 45
        }
        
        val advice = buildAdvice(trendLabel, streakCount, streakSide, predictedNext, tieWarning, hotSide, roundsSinceTie)
        
        return DTResult(
            trendLabel = trendLabel,
            trendEmoji = if (trendLabel.contains("DRAGON")) "🐉" else if (trendLabel.contains("TIGER")) "🐯" else "⚖️",
            confidence = confidence,
            predictedNext = predictedNext,
            dragonPct = dragonPct,
            tigerPct = tigerPct,
            tiePct = tiePct,
            currentStreak = if (streakCount > 1) "$streakSide x$streakCount" else "NONE",
            streakCount = streakCount,
            advice = advice,
            riskLevel = if (confidence > 65) "MED RISK" else "HIGH RISK",
            suggestedBet = suggestedBet,
            hotSide = hotSide,
            tieWarning = tieWarning
        )
    }
    
    private fun buildAdvice(trend: String, streak: Int, side: String, next: String, tie: Boolean, hot: String, roundsSinceTie: Int): String {
        return when {
            roundsSinceTie in 12..14 -> "Tie overdue. Consider skipping or small tie bet."
            streak >= 5 -> "$side streak at $streak. Reversal likely. Bet opposite."
            trend == "ALTERNATING" -> "Alternating pattern. Bet opposite of last result."
            hot != "BALANCED" -> "$hot side dominant. Follow the trend."
            tie -> "Tie overdue. Consider a small tie protection bet alongside."
            else -> "No clear pattern. Wait for streak to form."
        }
    }
    
    private fun collectingResult() = DTResult(
        "COLLECTING", "📊", 0, "UNCERTAIN",
        0, 0, 0, "NONE", 0,
        "Add Dragon/Tiger rounds to begin analysis.", "MED RISK",
        "WAIT", "BALANCED", false
    )
    
    fun getDragonWinRate(rounds: List<DragonTigerRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "D" }.toDouble() / rounds.size * 100
    
    fun getTigerWinRate(rounds: List<DragonTigerRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "T" }.toDouble() / rounds.size * 100
    
    fun getTieRate(rounds: List<DragonTigerRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "TIE" || it.result == "X" || it.result == "P" }.toDouble() / rounds.size * 100
}
