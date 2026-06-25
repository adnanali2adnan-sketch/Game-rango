package com.example.data

object SevenUpDownAnalyzer {

    data class SevenResult(
        val trendLabel: String,     // "UP STREAK" / "DOWN STREAK" / "BALANCED ROAD" / "COLLECTING"
        val trendEmoji: String,
        val confidence: Int,
        val predictedNext: String,  // "UP" / "DOWN" / "SEVEN" / "UNCERTAIN"
        val upPct: Int,
        val downPct: Int,
        val sevenPct: Int,
        val currentStreak: String,
        val streakCount: Int,
        val advice: String,
        val riskLevel: String,
        val suggestedBet: String,   // "BET UP" / "BET DOWN" / "BET SEVEN" / "WAIT"
        val hotSide: String         // "UP" / "DOWN" / "BALANCED"
    )

    fun analyze(rounds: List<SevenUpDownRound>): SevenResult {
        if (rounds.size < 6) return collectingResult()

        val recent = rounds.take(20)
        val upCount = recent.count { it.result == "U" }
        val downCount = recent.count { it.result == "D" }
        val sevenCount = recent.count { it.result == "7" }
        val total = recent.size

        val upPct = (upCount * 100 / total)
        val downPct = (downCount * 100 / total)
        val sevenPct = (sevenCount * 100 / total)

        // Streak detection (non-7)
        val nonSevenRounds = rounds.filter { it.result == "U" || it.result == "D" }
        val currentSide = nonSevenRounds.firstOrNull()?.result ?: "UNCERTAIN"
        var streakCount = 0
        for (r in nonSevenRounds) {
            if (r.result == currentSide) streakCount++ else break
        }

        // Hot side
        val hotSide = when {
            upCount > downCount * 1.5 -> "UP"
            downCount > upCount * 1.5 -> "DOWN"
            else -> "BALANCED"
        }

        // Probability/Ratio analysis
        var upVotes = 0
        var downVotes = 0

        // Heuristic 1: Inverse law (deviation from mean)
        if (upPct > 55) downVotes++
        if (downPct > 55) upVotes++

        // Heuristic 2: Streak counter
        if (streakCount >= 3) {
            // High streak usually reverses in dice games
            if (currentSide == "U") downVotes += 2 else upVotes += 2
        } else {
            // Low streak usually continues
            if (currentSide == "U") upVotes++ else downVotes++
        }

        // Heuristic 3: Recent frequency
        val superRecent = rounds.take(5)
        val recentUp = superRecent.count { it.result == "U" }
        val recentDown = superRecent.count { it.result == "D" }
        if (recentUp >= 3) {
            downVotes++
        } else if (recentDown >= 3) {
            upVotes++
        }

        val predictedNext = when {
            upVotes > downVotes -> "UP"
            downVotes > upVotes -> "DOWN"
            else -> "UNCERTAIN"
        }

        val suggestedBet = when (predictedNext) {
            "UP" -> "BET UP"
            "DOWN" -> "BET DOWN"
            else -> "WAIT"
        }

        val confidence = when {
            predictedNext != "UNCERTAIN" && Math.abs(upVotes - downVotes) >= 2 -> 78
            predictedNext != "UNCERTAIN" -> 64
            else -> 46
        }

        val riskLevel = when {
            confidence >= 75 -> "LOW RISK"
            confidence >= 60 -> "MED RISK"
            else -> "HIGH RISK"
        }

        val trendLabel = when {
            streakCount >= 4 && currentSide == "U" -> "UP STREAK"
            streakCount >= 4 && currentSide == "D" -> "DOWN STREAK"
            else -> "BALANCED ROAD"
        }

        val displayCurrent = if (currentSide == "U") "Up" else "Down"
        val displayPredicted = predictedNext

        val advice = when {
            sevenCount >= 3 -> "🎲 Lucky 7 frequency is high ($sevenPct%). Consider placing a small hedge bet on SEVEN."
            streakCount >= 3 -> "🔥 Long $displayCurrent streak ($streakCount rounds). Dice distribution suggests high probability of reversal to $displayPredicted soon."
            predictedNext == "UP" -> "📈 Upward bias detected in dice distribution analysis. Target: 7 UP."
            predictedNext == "DOWN" -> "📉 Downward bias detected in dice distribution analysis. Target: 7 DOWN."
            else -> "⚖️ Equilibrium reached. Safe strategy: Standby or minimum stake on balanced distribution."
        }

        return SevenResult(
            trendLabel = trendLabel,
            trendEmoji = if (trendLabel.contains("UP")) "📈" else if (trendLabel.contains("DOWN")) "📉" else "🎲",
            confidence = confidence,
            predictedNext = displayPredicted,
            upPct = upPct,
            downPct = downPct,
            sevenPct = sevenPct,
            currentStreak = if (streakCount > 1) "${if (currentSide == "U") "UP" else "DOWN"} x$streakCount" else "NONE",
            streakCount = streakCount,
            advice = advice,
            riskLevel = riskLevel,
            suggestedBet = suggestedBet,
            hotSide = hotSide
        )
    }

    private fun collectingResult() = SevenResult(
        trendLabel = "COLLECTING",
        trendEmoji = "📊",
        confidence = 0,
        predictedNext = "UNCERTAIN",
        upPct = 0,
        downPct = 0,
        sevenPct = 0,
        currentStreak = "NONE",
        streakCount = 0,
        advice = "Capture at least 6 rounds to initialize 7 Up Down statistics and distribution models.",
        riskLevel = "MED RISK",
        suggestedBet = "WAIT",
        hotSide = "BALANCED"
    )
}
