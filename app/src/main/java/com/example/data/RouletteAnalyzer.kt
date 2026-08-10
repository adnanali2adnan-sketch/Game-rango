package com.example.data

object RouletteAnalyzer {

    val RED_NUMBERS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
    val BLACK_NUMBERS = setOf(2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35)

    fun getColorForNumber(num: Int): String {
        return when {
            num == 0 -> "GREEN"
            RED_NUMBERS.contains(num) -> "RED"
            BLACK_NUMBERS.contains(num) -> "BLACK"
            else -> "GREEN"
        }
    }

    data class RouletteResult(
        val redPct: Int,
        val blackPct: Int,
        val greenPct: Int,
        val evenPct: Int,
        val oddPct: Int,
        val lowPct: Int,
        val highPct: Int,
        val dozen1Pct: Int,
        val dozen2Pct: Int,
        val dozen3Pct: Int,
        val suggestedBet: String,
        val currentStreak: String,
        val riskLevel: String,
        val advice: String,
        val trendLabel: String,
        val trendEmoji: String,
        val hotColor: String,
        val coldColor: String
    )

    fun analyze(rounds: List<RouletteRound>): RouletteResult {
        if (rounds.isEmpty()) {
            return RouletteResult(
                redPct = 0, blackPct = 0, greenPct = 0,
                evenPct = 0, oddPct = 0,
                lowPct = 0, highPct = 0,
                dozen1Pct = 0, dozen2Pct = 0, dozen3Pct = 0,
                suggestedBet = "STANDBY",
                currentStreak = "NO DATA",
                riskLevel = "MED RISK",
                advice = "Log at least 3-5 roulette spins to calculate table bias and streak momentum.",
                trendLabel = "NO DATA LOGGED",
                trendEmoji = "🔄",
                hotColor = "NONE",
                coldColor = "NONE"
            )
        }

        val total = rounds.size
        
        // Extract colors for each round
        val colors = rounds.map { round ->
            val num = round.number ?: round.result.toIntOrNull()
            if (num != null) {
                getColorForNumber(num)
            } else {
                when {
                    round.result.contains("RED", ignoreCase = true) || round.color == "RED" -> "RED"
                    round.result.contains("BLACK", ignoreCase = true) || round.color == "BLACK" -> "BLACK"
                    round.result.contains("GREEN", ignoreCase = true) || round.color == "GREEN" || round.result == "0" -> "GREEN"
                    else -> "RED"
                }
            }
        }

        val redCount = colors.count { it == "RED" }
        val blackCount = colors.count { it == "BLACK" }
        val greenCount = colors.count { it == "GREEN" }

        val redPct = (redCount * 100) / total
        val blackPct = (blackCount * 100) / total
        val greenPct = (greenCount * 100) / total

        // Parity and High/Low analysis on rounds with valid numbers
        val validNumbers = rounds.mapNotNull { round ->
            round.number ?: round.result.toIntOrNull()
        }.filter { it != 0 }

        val numTotal = validNumbers.size
        val evenCount = validNumbers.count { it % 2 == 0 }
        val oddCount = validNumbers.count { it % 2 != 0 }
        val evenPct = if (numTotal > 0) (evenCount * 100) / numTotal else 0
        val oddPct = if (numTotal > 0) (oddCount * 100) / numTotal else 0

        val lowCount = validNumbers.count { it in 1..18 }
        val highCount = validNumbers.count { it in 19..36 }
        val lowPct = if (numTotal > 0) (lowCount * 100) / numTotal else 0
        val highPct = if (numTotal > 0) (highCount * 100) / numTotal else 0

        val dozen1Count = validNumbers.count { it in 1..12 }
        val dozen2Count = validNumbers.count { it in 13..24 }
        val dozen3Count = validNumbers.count { it in 25..36 }
        val dozen1Pct = if (numTotal > 0) (dozen1Count * 100) / numTotal else 0
        val dozen2Pct = if (numTotal > 0) (dozen2Count * 100) / numTotal else 0
        val dozen3Pct = if (numTotal > 0) (dozen3Count * 100) / numTotal else 0

        // Streak detection on colors (newest first)
        val firstColor = colors.firstOrNull() ?: "NONE"
        var streakLen = 0
        for (c in colors) {
            if (c == firstColor) streakLen++ else break
        }
        val currentStreak = if (firstColor != "NONE") "$firstColor x$streakLen" else "NONE"

        // Recommendation & Risk level algorithm
        var suggested = "STANDBY"
        var risk = "MED RISK"
        var emoji = "⚖️"
        var label = "BALANCED WHEEL"
        var adviceText = ""

        when {
            streakLen >= 4 && (firstColor == "RED" || firstColor == "BLACK") -> {
                suggested = "BET $firstColor"
                risk = "LOW RISK"
                emoji = "🔥"
                label = "$firstColor STREAK DOMINANCE (x$streakLen)"
                adviceText = "Strong $firstColor streak in progress. Follow momentum until a reversal signal appears."
            }
            streakLen == 3 && (firstColor == "RED" || firstColor == "BLACK") -> {
                suggested = "BET $firstColor"
                risk = "LOW RISK"
                emoji = "⚡"
                label = "BUILDING $firstColor PATTERN"
                adviceText = "3 consecutive $firstColor rounds. High probability trend continuation."
            }
            redPct >= 65 -> {
                suggested = "BET RED"
                risk = "LOW RISK"
                emoji = "🔴"
                label = "RED TABLE BIAS ($redPct%)"
                adviceText = "Wheel heavily leaning Red ($redPct%). Ride the color momentum."
            }
            blackPct >= 65 -> {
                suggested = "BET BLACK"
                risk = "LOW RISK"
                emoji = "⚫"
                label = "BLACK TABLE BIAS ($blackPct%)"
                adviceText = "Wheel heavily leaning Black ($blackPct%). Ride the color momentum."
            }
            evenPct >= 70 -> {
                suggested = "BET EVEN"
                risk = "MED RISK"
                emoji = "🔢"
                label = "EVEN PARITY DOMINANCE ($evenPct%)"
                adviceText = "Even numbers dominating ($evenPct%). Consider Even parity bet or color combo."
            }
            oddPct >= 70 -> {
                suggested = "BET ODD"
                risk = "MED RISK"
                emoji = "🔢"
                label = "ODD PARITY DOMINANCE ($oddPct%)"
                adviceText = "Odd numbers dominating ($oddPct%). Consider Odd parity bet."
            }
            greenCount > 0 && streakLen == 1 && firstColor == "GREEN" -> {
                suggested = "STANDBY"
                risk = "HIGH RISK"
                emoji = "🟢"
                label = "ZERO RECENTLY HIT"
                adviceText = "Zero landed on last spin. Re-aligning color frequency."
            }
            else -> {
                suggested = if (redPct > blackPct) "BET RED" else if (blackPct > redPct) "BET BLACK" else "STANDBY"
                risk = "MED RISK"
                emoji = "⚖️"
                label = "STANDARD WHEEL DISTRIBUTION"
                adviceText = "Wheel output balanced. Trade small stakes or wait for a 3-spin streak."
            }
        }

        val hotColor = if (redPct > blackPct) "RED" else if (blackPct > redPct) "BLACK" else "EQUAL"
        val coldColor = if (redPct < blackPct) "RED" else if (blackPct < redPct) "BLACK" else "EQUAL"

        return RouletteResult(
            redPct = redPct,
            blackPct = blackPct,
            greenPct = greenPct,
            evenPct = evenPct,
            oddPct = oddPct,
            lowPct = lowPct,
            highPct = highPct,
            dozen1Pct = dozen1Pct,
            dozen2Pct = dozen2Pct,
            dozen3Pct = dozen3Pct,
            suggestedBet = suggested,
            currentStreak = currentStreak,
            riskLevel = risk,
            advice = adviceText,
            trendLabel = label,
            trendEmoji = emoji,
            hotColor = hotColor,
            coldColor = coldColor
        )
    }
}
