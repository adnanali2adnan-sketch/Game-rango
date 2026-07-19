package com.example.data

object BaccaratAnalyzer {
    
    data class BaccaratResult(
        val trendLabel: String,     // "PLAYER STREAK" / "BANKER STREAK" / "ALTERNATING" / "BALANCED" / "COLLECTING"
        val trendEmoji: String,
        val confidence: Int,
        val predictedNext: String,  // "PLAYER" / "BANKER" / "UNCERTAIN"
        val playerPct: Int,         // % of recent rounds Player won
        val bankerPct: Int,
        val tiePct: Int,
        val currentStreak: String,  // "PLAYER x3" / "BANKER x2" / etc
        val streakCount: Int,
        val advice: String,
        val riskLevel: String,
        val suggestedBet: String,   // "BET PLAYER" / "BET BANKER" / "WAIT"
        val hotSide: String,        // "PLAYER" / "BANKER" / "BALANCED"
        val tieWarning: Boolean = false,
        val bigEyeBoySignal: String = "N/A",
        val smallRoadSignal: String = "N/A",
        val cockroachRoadSignal: String = "N/A",
        val finalRoadDecision: String = "UNCERTAIN"
    )

    fun analyze(rounds: List<BaccaratRound>): BaccaratResult {
        if (rounds.size < 6) return collectingResult()
        
        val recent = rounds.take(20)
        val pCount = recent.count { it.result == "P" }
        val bCount = recent.count { it.result == "B" }
        val tieCount = recent.count { it.result == "T" || it.result == "X" || it.result == "TIE" }
        val total = recent.size
        
        val playerPct = (pCount * 100 / total)
        val bankerPct = (bCount * 100 / total)
        val tiePct = (tieCount * 100 / total)
        
        // Detect current streak of non-ties
        val nonTieRounds = rounds.filter { it.result == "P" || it.result == "B" }
        val currentSide = nonTieRounds.firstOrNull()?.result ?: "UNCERTAIN"
        var streakCount = 0
        for (r in nonTieRounds) {
            if (r.result == currentSide) streakCount++ else break
        }
        
        // Hot side
        val hotSide = when {
            pCount > bCount * 1.5 -> "PLAYER"
            bCount > pCount * 1.5 -> "BANKER"
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
            finalRoadDecision == "REVERSAL" && currentSide != "UNCERTAIN" -> if (currentSide == "P") "BANKER" else "PLAYER"
            else -> "UNCERTAIN"
        }

        val suggestedBet = when (predictedNext) {
            "P" -> "PLAYER"
            "B" -> "BANKER"
            "PLAYER" -> "BET PLAYER"
            "BANKER" -> "BET BANKER"
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
            streakCount >= 4 && currentSide == "P" -> "PLAYER STREAK"
            streakCount >= 4 && currentSide == "B" -> "BANKER STREAK"
            else -> "BALANCED ROAD"
        }

        val advice = buildAdvice(bigEyeBoySignal, smallRoadSignal, cockroachRoadSignal, finalRoadDecision, predictedNext, streakCount, currentSide)

        return BaccaratResult(
            trendLabel = trendLabel,
            trendEmoji = if (trendLabel.contains("PLAYER")) "🔵" else if (trendLabel.contains("BANKER")) "🔴" else "⚖️",
            confidence = confidence,
            predictedNext = if (predictedNext == "P") "PLAYER" else if (predictedNext == "B") "BANKER" else predictedNext,
            playerPct = playerPct,
            bankerPct = bankerPct,
            tiePct = tiePct,
            currentStreak = if (streakCount > 1) "$currentSide x$streakCount" else "NONE",
            streakCount = streakCount,
            advice = advice,
            riskLevel = riskLevel,
            suggestedBet = if (suggestedBet == "P" || suggestedBet == "PLAYER") "BET PLAYER" else if (suggestedBet == "B" || suggestedBet == "BANKER") "BET BANKER" else suggestedBet,
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
        
        val cleanPredicted = if (predicted == "P") "PLAYER" else if (predicted == "B") "BANKER" else predicted
        val actionText = if (decision == "CONTINUE") {
            "stabilizing towards trend continuation. Bet $cleanPredicted for positive consistency."
        } else {
            "exhibiting reversal. Bet opposite side ($cleanPredicted)."
        }
        
        return "🎰 Road Voting: Big Eye [$bigEye], Small [$small], Cockroach [$cockroach]. Decision is $decision, $actionText"
    }
    
    private fun collectingResult() = BaccaratResult(
        trendLabel = "COLLECTING",
        trendEmoji = "📊",
        confidence = 0,
        predictedNext = "UNCERTAIN",
        playerPct = 0,
        bankerPct = 0,
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
    
    fun getPlayerWinRate(rounds: List<BaccaratRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "P" }.toDouble() / rounds.size * 100
    
    fun getBankerWinRate(rounds: List<BaccaratRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "B" }.toDouble() / rounds.size * 100
    
    fun getTieRate(rounds: List<BaccaratRound>): Double =
        if (rounds.isEmpty()) 0.0 else rounds.count { it.result == "T" || it.result == "X" || it.result == "TIE" }.toDouble() / rounds.size * 100
}
