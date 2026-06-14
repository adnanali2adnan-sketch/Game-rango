package com.example.data

object DragonTigerAnalyzer {
    
    data class DTResult(
        val trendLabel: String,     // "DRAGON STREAK" / "TIGER STREAK" / "ALTERNATING" / "BALANCED" / "COLLECTING"
        val trendEmoji: String,
        val confidence: Int,
        val predictedNext: String,  // "DRAGON" / "TIGER" / "UNCERTAIN"
        val dragonPct: Int,         // % of recent rounds Dragon won
        val tigerPct: Int,
        val tiePct: Int,
        val currentStreak: String,  // "DRAGON x3" / "TIGER x2" / etc
        val streakCount: Int,
        val advice: String,
        val riskLevel: String,
        val suggestedBet: String,   // "BET DRAGON" / "BET TIGER" / "WAIT"
        val hotSide: String,        // "DRAGON" / "TIGER" / "BALANCED"
        val tieWarning: Boolean = false, // Always false now as tie warning is removed
        val bigEyeBoySignal: String = "N/A",
        val smallRoadSignal: String = "N/A",
        val cockroachRoadSignal: String = "N/A",
        val finalRoadDecision: String = "UNCERTAIN"
    )

    fun analyze(rounds: List<DragonTigerRound>): DTResult {
        if (rounds.size < 6) return collectingResult()
        
        val recent = rounds.take(20)
        val dCount = recent.count { it.result == "D" }
        val tCount = recent.count { it.result == "T" }
        val tieCount = recent.count { it.result == "TIE" || it.result == "X" || it.result == "P" }
        val total = recent.size
        
        val dragonPct = (dCount * 100 / total)
        val tigerPct = (tCount * 100 / total)
        val tiePct = (tieCount * 100 / total)
        
        // Detect current streak of non-ties
        val nonTieRounds = rounds.filter { it.result == "D" || it.result == "T" }
        val currentSide = nonTieRounds.firstOrNull()?.result ?: "UNCERTAIN"
        var streakCount = 0
        for (r in nonTieRounds) {
            if (r.result == currentSide) streakCount++ else break
        }
        
        // Hot side
        val hotSide = when {
            dCount > tCount * 1.5 -> "DRAGON"
            tCount > dCount * 1.5 -> "TIGER"
            else -> "BALANCED"
        }
        
        // ----------------------------------------------------
        // CASINO-STYLE CHRONOLOGICAL MULTI-ROAD GENERATOR
        // ----------------------------------------------------
        val chronologicalNonTies = nonTieRounds.reversed()
        val columns = mutableListOf<MutableList<String>>()
        for (round in chronologicalNonTies) {
            val res = round.result
            if (columns.isEmpty() || columns.last().first() != res) {
                columns.add(mutableListOf(res))
            } else {
                columns.last().add(res)
            }
        }
        
        val bigEyeBoyMarks = calculateDerivedRoad(columns, 1)
        val smallRoadMarks = calculateDerivedRoad(columns, 2)
        val cockroachRoadMarks = calculateDerivedRoad(columns, 3)

        val bigEyeBoySignal = bigEyeBoyMarks.lastOrNull() ?: "N/A"
        val smallRoadSignal = smallRoadMarks.lastOrNull() ?: "N/A"
        val cockroachRoadSignal = cockroachRoadMarks.lastOrNull() ?: "N/A"

        // Votes
        var continuationVotes = 0
        var reversalVotes = 0

        if (bigEyeBoySignal == "RED") continuationVotes++ else if (bigEyeBoySignal == "BLUE") reversalVotes++
        if (smallRoadSignal == "RED") continuationVotes++ else if (smallRoadSignal == "BLUE") reversalVotes++
        if (cockroachRoadSignal == "RED") continuationVotes++ else if (cockroachRoadSignal == "BLUE") reversalVotes++

        val finalRoadDecision = when {
            continuationVotes >= 2 -> "CONTINUE"
            reversalVotes >= 2 -> "REVERSAL"
            else -> "UNCERTAIN"
        }

        val predictedNext = when {
            finalRoadDecision == "CONTINUE" && currentSide != "UNCERTAIN" -> currentSide
            finalRoadDecision == "REVERSAL" && currentSide != "UNCERTAIN" -> if (currentSide == "D") "TIGER" else "DRAGON"
            else -> "UNCERTAIN"
        }

        val suggestedBet = when (predictedNext) {
            "D" -> "DRAGON"
            "T" -> "TIGER"
            "DRAGON" -> "BET DRAGON"
            "TIGER" -> "BET TIGER"
            else -> "WAIT"
        }

        val confidence = when {
            finalRoadDecision != "UNCERTAIN" && (continuationVotes == 3 || reversalVotes == 3) -> 82
            finalRoadDecision != "UNCERTAIN" -> 68
            else -> 45
        }

        val riskLevel = when {
            confidence >= 80 -> "LOW RISK"
            confidence >= 65 -> "MED RISK"
            else -> "HIGH RISK"
        }

        val trendLabel = when {
            streakCount >= 4 && currentSide == "D" -> "DRAGON STREAK"
            streakCount >= 4 && currentSide == "T" -> "TIGER STREAK"
            else -> "BALANCED ROAD"
        }

        val advice = buildAdvice(bigEyeBoySignal, smallRoadSignal, cockroachRoadSignal, finalRoadDecision, predictedNext, streakCount, currentSide)

        return DTResult(
            trendLabel = trendLabel,
            trendEmoji = if (trendLabel.contains("DRAGON")) "🐉" else if (trendLabel.contains("TIGER")) "🐯" else "⚖️",
            confidence = confidence,
            predictedNext = if (predictedNext == "D") "DRAGON" else if (predictedNext == "T") "TIGER" else predictedNext,
            dragonPct = dragonPct,
            tigerPct = tigerPct,
            tiePct = tiePct,
            currentStreak = if (streakCount > 1) "$currentSide x$streakCount" else "NONE",
            streakCount = streakCount,
            advice = advice,
            riskLevel = riskLevel,
            suggestedBet = if (suggestedBet == "D" || suggestedBet == "DRAGON") "BET DRAGON" else if (suggestedBet == "T" || suggestedBet == "TIGER") "BET TIGER" else suggestedBet,
            hotSide = hotSide,
            tieWarning = false,
            bigEyeBoySignal = bigEyeBoySignal,
            smallRoadSignal = smallRoadSignal,
            cockroachRoadSignal = cockroachRoadSignal,
            finalRoadDecision = finalRoadDecision
        )
    }

    private fun calculateDerivedRoad(columns: List<List<String>>, k: Int): List<String> {
        val marks = mutableListOf<String>()
        for (c in columns.indices) {
            val col = columns[c]
            for (r in col.indices) {
                // Check starting eligibility:
                // Big Eye Boy (k=1): starts at col 1 row 1, or col 2 row 0.
                // Small Road (k=2): starts at col 2 row 1, or col 3 row 0.
                // Cockroach Road (k=3): starts at col 3 row 1, or col 4 row 0.
                val isEligible = (c == k && r >= 1) || (c > k)
                if (!isEligible) continue
                
                val mark = if (r > 0) {
                    val compColIndex = c - k
                    if (compColIndex in columns.indices) {
                        val compColHeight = columns[compColIndex].size
                        when {
                            compColHeight > r -> "RED"
                            compColHeight == r -> "BLUE"
                            else -> "RED"
                        }
                    } else {
                        "BLUE"
                    }
                } else {
                    val prevColIndex = c - 1
                    val compColIndex = c - 1 - k
                    if (prevColIndex in columns.indices && compColIndex in columns.indices) {
                        val h1 = columns[prevColIndex].size
                        val h2 = columns[compColIndex].size
                        if (h1 == h2) "RED" else "BLUE"
                    } else {
                        "BLUE"
                    }
                }
                marks.add(mark)
            }
        }
        return marks
    }

    private fun buildAdvice(
        bigEye: String,
        small: String,
        cockroach: String,
        decision: String,
        predicted: String,
        streak: Int,
        side: String
    ): String {
        if (decision == "UNCERTAIN") {
            return "Derived roads have mixed patterns. Standby and wait for a clear pattern to form."
        }
        
        val cleanPredicted = if (predicted == "D") "DRAGON" else if (predicted == "T") "TIGER" else predicted
        val actionText = if (decision == "CONTINUE") {
            "stabilizing towards trend continuation. Bet $cleanPredicted for positive consistency."
        } else {
            "exhibiting reversal. Bet opposite side ($cleanPredicted)."
        }
        
        return "🎰 Road Voting: Big Eye [$bigEye], Small [$small], Cockroach [$cockroach]. Decision is $decision, $actionText"
    }
    
    private fun collectingResult() = DTResult(
        trendLabel = "COLLECTING",
        trendEmoji = "📊",
        confidence = 0,
        predictedNext = "UNCERTAIN",
        dragonPct = 0,
        tigerPct = 0,
        tiePct = 0,
        currentStreak = "NONE",
        streakCount = 0,
        advice = "Capture at least 6 rounds to initialize baccarat road analysis.",
        riskLevel = "MED RISK",
        suggestedBet = "WAIT",
        hotSide = "BALANCED",
        tieWarning = false,
        bigEyeBoySignal = "N/A",
        smallRoadSignal = "N/A",
        cockroachRoadSignal = "N/A",
        finalRoadDecision = "UNCERTAIN"
    )
    
    fun getDragonWinRate(rounds: List<DragonTigerRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "D" }.toDouble() / rounds.size * 100
    
    fun getTigerWinRate(rounds: List<DragonTigerRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "T" }.toDouble() / rounds.size * 100
    
    fun getTieRate(rounds: List<DragonTigerRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "TIE" || it.result == "X" || it.result == "P" }.toDouble() / rounds.size * 100
}
