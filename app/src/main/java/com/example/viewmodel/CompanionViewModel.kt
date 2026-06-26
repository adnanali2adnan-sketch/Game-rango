package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CrashRound
import com.example.data.CrashRepository
import com.example.data.DragonTigerRound
import com.example.data.DragonTigerDao
import com.example.data.DragonTigerAnalyzer
import com.example.data.AndarBaharRound
import com.example.data.AndarBaharAnalyzer
import com.example.data.SevenUpDownRound
import com.example.data.SevenUpDownAnalyzer
import com.example.api.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CrashRepository
    private val dtDao: DragonTigerDao
    private val abDao: com.example.data.AndarBaharDao
    private val sevenDao: com.example.data.SevenUpDownDao
    private val context = application.applicationContext

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _currentGame = MutableStateFlow("RANGO")
    val currentGame: StateFlow<String> = _currentGame.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CrashRepository(database.crashDao())
        dtDao = database.dragonTigerDao()
        abDao = database.andarBaharDao()
        sevenDao = database.sevenUpDownDao()
        _geminiApiKey.value = com.example.util.SecurePrefs.getGeminiApiKey(context)
        
        // Load default game setting safely
        _currentGame.value = context.getSharedPreferences("RangoPrefs", Context.MODE_PRIVATE)
            .getString("current_game", "RANGO") ?: "RANGO"
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        com.example.util.SecurePrefs.saveGeminiApiKey(context, key)
    }

    fun clearGeminiApiKey() {
        _geminiApiKey.value = ""
        com.example.util.SecurePrefs.clearGeminiApiKey(context)
    }

    fun setCurrentGame(game: String) {
        _currentGame.value = game
        context.getSharedPreferences("RangoPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("current_game", game)
            .apply()
    }

    // Connect to Room flows
    val historyState: StateFlow<List<CrashRound>> = repository.allRounds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dtRounds: StateFlow<List<DragonTigerRound>> = dtDao.getAllRounds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dtResult: StateFlow<DragonTigerAnalyzer.DTResult> = dtRounds
        .map { rounds -> DragonTigerAnalyzer.analyze(rounds) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DragonTigerAnalyzer.analyze(emptyList())
        )

    private fun parseAiRecommendation(adviceText: String): String {
        if (adviceText.contains("RECOMMENDATION:", ignoreCase = true)) {
            val rec = adviceText.substringAfter("RECOMMENDATION:")
                .substringBefore("\n")
                .trim()
                .uppercase()
            val clean = rec.replace("[", "").replace("]", "").trim()
            if (clean.contains("DRAGON") || clean == "D") return "DRAGON"
            if (clean.contains("TIGER") || clean == "T") return "TIGER"
            if (clean.contains("ANDAR") || clean == "A") return "ANDAR"
            if (clean.contains("BAHAR") || clean == "B") return "BAHAR"
            if (clean.contains("UP") || clean == "U") return "UP"
            if (clean.contains("DOWN") || clean == "D") return "DOWN"
            if (clean.contains("SEVEN") || clean == "7") return "SEVEN"
        }
        return "UNCERTAIN"
    }

    private fun parseCrashAiTarget(adviceText: String): Double? {
        if (adviceText.contains("RECOMMENDATION:", ignoreCase = true)) {
            val rec = adviceText.substringAfter("RECOMMENDATION:")
                .substringBefore("\n")
                .trim()
                .lowercase()
            val regex = """(\d+\.?\d*)""".toRegex()
            val match = regex.find(rec)
            if (match != null) {
                return match.value.toDoubleOrNull()
            }
        }
        return null
    }

    fun addDTRound(result: String) {
        viewModelScope.launch {
            val pred = dtResult.value.predictedNext
            val source = if (_geminiApiKey.value.isNotBlank() && _aiAdviceText.value.isNotBlank() && _currentGame.value == "DRAGON_TIGER") "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(_aiAdviceText.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "DRAGON" && result == "D") true 
                      else if (finalPred == "TIGER" && result == "T") true 
                      else if (finalPred == "UNCERTAIN" || result == "X") null 
                      else false
            dtDao.insertRound(DragonTigerRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            if (_geminiApiKey.value.isNotBlank()) {
                refreshAiStrategy()
            }
        }
    }

    fun clearDTRounds() {
        viewModelScope.launch {
            dtDao.clearAll()
        }
    }

    fun deleteDTRound(id: Int) {
        viewModelScope.launch {
            dtDao.deleteRound(id)
        }
    }

    fun updateDTRoundStatus(id: Int, isWin: Boolean?) {
        viewModelScope.launch {
            dtDao.updateRoundStatus(id, isWin)
        }
    }

    val abRounds: StateFlow<List<AndarBaharRound>> = abDao.getAllRounds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val abResult: StateFlow<AndarBaharAnalyzer.ABResult> = abRounds
        .map { rounds -> AndarBaharAnalyzer.analyze(rounds) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AndarBaharAnalyzer.analyze(emptyList())
        )

    fun addABRound(result: String) {
        viewModelScope.launch {
            val pred = abResult.value.predictedNext
            val source = if (_geminiApiKey.value.isNotBlank() && _aiAdviceText.value.isNotBlank() && _currentGame.value == "ANDAR_BAHAR") "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(_aiAdviceText.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "ANDAR" && result == "A") true 
                      else if (finalPred == "BAHAR" && result == "B") true 
                      else if (finalPred == "UNCERTAIN") null 
                      else false
            abDao.insertRound(AndarBaharRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            if (_geminiApiKey.value.isNotBlank()) {
                refreshAiStrategy()
            }
        }
    }

    fun clearABRounds() {
        viewModelScope.launch {
            abDao.clearAll()
        }
    }

    fun deleteABRound(id: Int) {
        viewModelScope.launch {
            abDao.deleteRound(id)
        }
    }

    fun updateABRoundStatus(id: Int, isWin: Boolean?) {
        viewModelScope.launch {
            abDao.updateRoundStatus(id, isWin)
        }
    }

    val sevenRounds: StateFlow<List<SevenUpDownRound>> = sevenDao.getAllRounds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sevenResult: StateFlow<SevenUpDownAnalyzer.SevenResult> = sevenRounds
        .map { rounds -> SevenUpDownAnalyzer.analyze(rounds) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SevenUpDownAnalyzer.analyze(emptyList())
        )

    fun addSevenRound(result: String) {
        viewModelScope.launch {
            val pred = sevenResult.value.predictedNext
            val source = if (_geminiApiKey.value.isNotBlank() && _aiAdviceText.value.isNotBlank() && _currentGame.value == "SEVEN_UP_DOWN") "AI" else "LOCAL"
            val finalPred = if (source == "AI") {
                val parsed = parseAiRecommendation(_aiAdviceText.value)
                if (parsed != "UNCERTAIN") parsed else pred
            } else {
                pred
            }
            val win = if (finalPred == "UP" && result == "U") true 
                      else if (finalPred == "DOWN" && result == "D") true 
                      else if (finalPred == "SEVEN" && result == "7") true 
                      else if (finalPred == "UNCERTAIN") null 
                      else false
            sevenDao.insertRound(SevenUpDownRound(
                result = result,
                prediction = finalPred,
                predictionSource = source,
                isWin = win
            ))
            if (_geminiApiKey.value.isNotBlank()) {
                refreshAiStrategy()
            }
        }
    }

    fun clearSevenRounds() {
        viewModelScope.launch {
            sevenDao.clearAll()
        }
    }

    fun deleteSevenRound(id: Int) {
        viewModelScope.launch {
            sevenDao.deleteRound(id)
        }
    }

    fun updateSevenRoundStatus(id: Int, isWin: Boolean?) {
        viewModelScope.launch {
            sevenDao.updateRoundStatus(id, isWin)
        }
    }

    // State of AI Strategy Advisor
    private val _aiAdviceText = MutableStateFlow<String>("")
    val aiAdviceText: StateFlow<String> = _aiAdviceText.asStateFlow()

    private val _isLoadingAdvice = MutableStateFlow(false)
    val isLoadingAdvice: StateFlow<Boolean> = _isLoadingAdvice.asStateFlow()

    // Form inputs
    val currentMultiplierInput = MutableStateFlow("1.5")
    val currentBetAmountInput = MutableStateFlow("10")
    val currentCashOutInput = MutableStateFlow("1.5")
    val userBalanceInput = MutableStateFlow("280.89")

    // UI Feedback messages
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    // Initialize database default rounds for a richer first-time user experience
    fun populateDefaultHistoryIfEmpty() {
        // Default history population disabled for a completely empty first launch clean state.
    }

    /**
     * Add a round record into Room DB.
     */
    fun addRoundResult(multiplier: Double, betSize: Double, cashOutVal: Double) {
        viewModelScope.launch {
            if (multiplier <= 0.0) {
                _uiMessage.value = "Please enter a valid multiplier above 0.0"
                return@launch
            }

            val source = if (_geminiApiKey.value.isNotBlank() && _aiAdviceText.value.isNotBlank() && (_currentGame.value == "RANGO" || _currentGame.value == "AVIATOR")) "AI" else "LOCAL"
            val localTarget = calculateLocalMetrics().localRecommendedTarget
            val target = if (source == "AI") {
                parseCrashAiTarget(_aiAdviceText.value) ?: localTarget
            } else {
                localTarget
            }

            // HIT or NOT HIT determination
            val isWinVal = multiplier >= target

            var profit = 0.0
            if (betSize > 0.0) {
                if (isWinVal) {
                    profit = betSize * (target - 1.0)
                } else {
                    profit = -betSize
                }
            }

            val round = CrashRound(
                multiplier = multiplier,
                betAmount = betSize,
                cashOutMultiplier = if (isWinVal) target else 0.0,
                profitLoss = profit,
                prediction = "${String.format("%.2f", target)}x",
                predictionSource = source,
                isWin = isWinVal
            )

            repository.insert(round)
            _uiMessage.value = "Multiplier $multiplier x logged successfully (${if (isWinVal) "HIT" else "NOT HIT"} @ target ${String.format("%.2f", target)}x)"
            
            // Recalculate local heuristics & trigger quick AI advice refreshing if keys exist
            refreshAiStrategy()
        }
    }

    fun deleteCrashRound(id: Int) {
        viewModelScope.launch {
            repository.deleteRound(id)
        }
    }

    fun updateCrashRoundStatus(id: Int, isWin: Boolean?) {
        viewModelScope.launch {
            repository.updateRoundStatus(id, isWin)
        }
    }

    /**
     * Delete stats & database log
     */
    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAll()
            _aiAdviceText.value = ""
            _uiMessage.value = "All historic records cleared from app database."
        }
    }

    /**
     * Fetch strategies dynamically from Gemini AI
     */
    fun refreshAiStrategy() {
        viewModelScope.launch {
            _isLoadingAdvice.value = true
            val game = _currentGame.value
            val customKey = _geminiApiKey.value
            val balanceValue = userBalanceInput.value.toDoubleOrNull() ?: 280.89

            val answer = when (game) {
                "DRAGON_TIGER" -> {
                    val recentList = dtDao.getRecentRounds(30)
                    if (recentList.isEmpty()) {
                        _aiAdviceText.value = "Lobby history is currently empty. Please log some Dragon Tiger rounds to begin analysis."
                        _isLoadingAdvice.value = false
                        return@launch
                    }
                    
                    val recent20 = recentList.take(20)
                    val dtResult = DragonTigerAnalyzer.analyze(recentList)
                    
                    val nonTieRounds = recentList.filter { it.result == "D" || it.result == "T" }
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
                    val bigRoadStr = columns.joinToString(" | ") { col -> col.joinToString(",") }

                    val customPrompt = """
                        You are an elite, professional Casino Dragon Tiger Game Analyzer.
                        
                        CURRENT SESSION DATA:
                        - Current Game Mode: DRAGON_TIGER
                        - Wallet Balance: PKR $balanceValue
                        - Last 20 Rounds (Newest first): ${recent20.joinToString(", ") { it.result }} (D=Dragon, T=Tiger, X/P/TIE=Tie)
                        
                        CASINO MULTI-ROAD MATRIX ANALYSIS:
                        - Big Road Structure: $bigRoadStr
                        - Big Eye Boy State: ${dtResult.bigEyeBoySignal} (RED = continuation, BLUE = reversal)
                        - Small Road State: ${dtResult.smallRoadSignal} (RED = continuation, BLUE = reversal)
                        - Cockroach Road State: ${dtResult.cockroachRoadSignal} (RED = continuation, BLUE = reversal)
                        - Current Streak: ${dtResult.currentStreak} (Streak length: ${dtResult.streakCount})
                        - Road Signals Summary: Big Eye Boy = ${dtResult.bigEyeBoySignal}, Small Road = ${dtResult.smallRoadSignal}, Cockroach = ${dtResult.cockroachRoadSignal}
                        - Offline Road Voting Decision: ${dtResult.finalRoadDecision}

                        Analyze this layout and formulate your tactical strategy.
                        Your response MUST be formatted exactly as below (maximum 2 lines total, extremely concise, sharp and direct, no disclaimers):
                        RECOMMENDATION: [DRAGON / TIGER / TIE / STANDBY]
                        REASON: [Short 1-sentence explanation of why]
                    """.trimIndent()

                    GeminiClient.getStrategyAdvice(customPrompt, customKey)
                }
                "ANDAR_BAHAR" -> {
                    val recentList = abDao.getRecentRounds(30)
                    if (recentList.isEmpty()) {
                        _aiAdviceText.value = "Lobby history is currently empty. Please log some Andar Bahar rounds to begin analysis."
                        _isLoadingAdvice.value = false
                        return@launch
                    }
                    val dataStr = recentList.take(20).joinToString(", ") { it.result }
                    val abResult = AndarBaharAnalyzer.analyze(recentList)
                    GeminiClient.analyzeGame(customKey, "ANDAR_BAHAR", dataStr, balanceValue, abResult.trendLabel)
                }
                "SEVEN_UP_DOWN" -> {
                    val recentList = sevenDao.getRecentRounds(30)
                    if (recentList.isEmpty()) {
                        _aiAdviceText.value = "Lobby history is currently empty. Please log some 7 Up Down rounds to begin analysis."
                        _isLoadingAdvice.value = false
                        return@launch
                    }
                    val dataStr = recentList.take(20).joinToString(", ") { it.result }
                    val sevenResult = SevenUpDownAnalyzer.analyze(recentList)
                    GeminiClient.analyzeGame(customKey, "SEVEN_UP_DOWN", dataStr, balanceValue, sevenResult.trendLabel)
                }
                else -> {
                    val recentList = repository.getRecentLimit(15)
                    if (recentList.isEmpty()) {
                        _aiAdviceText.value = "Lobby history is currently empty. Please log some round outcomes to get AI advisory support. (Lobby history khali hai!)"
                        _isLoadingAdvice.value = false
                        return@launch
                    }
                    val multipliersStr = recentList.joinToString(", ") { "${it.multiplier}x" }
                    val localStats = calculateLocalMetrics()
                    val trend = localStats.localRiskScore
                    GeminiClient.analyzeGame(customKey, game, multipliersStr, balanceValue, trend)
                }
            }

            _aiAdviceText.value = answer
            _isLoadingAdvice.value = false
        }
    }

    // Secondary mathematical calculations handled dynamically on the device (Local Heuristics Engine)
    fun calculateLocalMetrics(): LocalMetrics {
        val list = historyState.value
        val balance = userBalanceInput.value.toDoubleOrNull() ?: 280.89

        if (list.isEmpty()) {
            val baseBet = (balance * 0.01).coerceAtLeast(1.0)
            return LocalMetrics(
                suggestedBaseBet = baseBet,
                suggestedNextBet = baseBet,
                localAdvisory = "Ready to analyze. Input flight multipliers to initialize the pattern engine."
            )
        }

        val count = list.size
        val avgMultiplier = list.map { it.multiplier }.average()
        val maxMultiplier = list.maxOfOrNull { it.multiplier } ?: 1.0

        // Low crashes (< 1.30x)
        val lowCrashCount = list.count { it.multiplier < 1.30 }
        val lowCrashRate = (lowCrashCount.toFloat() / count) * 100

        // High wins (> 2.0x)
        val highLaunchCount = list.count { it.multiplier >= 2.0 }
        val highLaunchRate = (highLaunchCount.toFloat() / count) * 100

        // Total Net Profit/Loss if user was logging bet outcomes
        val totalProfit = list.sumOf { it.profitLoss }

        // 1. STREAK DETECTION & RISK ANALYSIS LOGIC
        // Fetch up to 100 rounds
        val scanList = list.take(100)

        // Cold Streak: If 4+ consecutive rounds crash under 1.20x
        val last4 = scanList.take(4)
        val isColdStreakActive = last4.size >= 4 && last4.all { it.multiplier < 1.20 }

        // Hot/Inflation Streak: If multiple high multipliers (3x+) occur closely
        // Let's define it as 3 or more high multipliers (>= 3.0x) within the last 10 rounds
        val last10 = scanList.take(10)
        val isHotStreakActive = last10.count { it.multiplier >= 3.0 } >= 3

        // Determine Risk Score & recommended targets
        val riskLevel: String // LOW, MEDIUM, HIGH
        val localControlScore: String
        val recommendedMinTarget: Double
        val trendAdvice: String

        if (isHotStreakActive) {
            riskLevel = "HIGH"
            localControlScore = "HIGH RISK (Inflation Active)"
            recommendedMinTarget = 1.15
            trendAdvice = "🚨 HOT INFLATION STREAK! 3+ high flights detected in the last 10 rounds. Expect an early crash correction immediately. Recommending to SKIP or cashout very low at 1.15x!"
        } else if (isColdStreakActive) {
            riskLevel = "MEDIUM"
            localControlScore = "MEDIUM RISK (Cold Streak)"
            recommendedMinTarget = 1.30
            trendAdvice = "⚠️ COLD STREAK! 4+ early crashes below 1.20x in a row. A rebound soar is overdue, but do not chase high. Keep targets conservative at 1.30x with managed recover bets."
        } else if (scanList.take(5).any { it.multiplier < 1.10 }) {
            riskLevel = "MEDIUM"
            localControlScore = "MEDIUM RISK (Unstable Plane)"
            recommendedMinTarget = 1.40
            trendAdvice = "📊 Moderate uncertainty. Early crash (< 1.10x) detected in recent rounds. Bet with normal precautions, target 1.40x auto-cashout."
        } else {
            riskLevel = "LOW"
            localControlScore = "LOW RISK (Steady Flight)"
            recommendedMinTarget = 1.50
            trendAdvice = "✅ STABLE PATTERN! No dangerous clusters detected. Plane is flying steadily in moderate zones. Suggested cashout is a comfortable 1.50x."
        }

        // 2. SMART RISK & MONEY MANAGEMENT CALCULATOR
        val suggestedBaseBet = (balance * 0.01).coerceAtLeast(1.0)
        
        // Calculate the next bet if the previous one loses (Martingale recovery)
        var suggestedNextBet = suggestedBaseBet
        val lastRound = list.firstOrNull()
        if (lastRound != null) {
            // If the last round was logged with a bet, and was a loss (profitLoss < 0)
            if (lastRound.betAmount > 0 && lastRound.profitLoss < 0) {
                // Modified Martingale multiplier based on target
                val targetMul = lastRound.cashOutMultiplier.coerceAtLeast(1.5)
                val recoverMultiplier = (targetMul / (targetMul - 1.0)).coerceIn(1.5, 3.0)
                suggestedNextBet = lastRound.betAmount * recoverMultiplier
            }
        }
        
        // Safety cap: bet should not exceed balance
        if (suggestedNextBet > balance) {
            suggestedNextBet = balance
        }

        return LocalMetrics(
            totalCount = count,
            averageMultiplier = avgMultiplier,
            maxMultiplier = maxMultiplier,
            lowCrashRate = lowCrashRate,
            highLaunchRate = highLaunchRate,
            netProfitLoss = totalProfit,
            localAdvisory = trendAdvice,
            localRecommendedTarget = recommendedMinTarget,
            localRiskScore = localControlScore,
            isColdStreakActive = isColdStreakActive,
            isHotStreakActive = isHotStreakActive,
            riskLevel = riskLevel,
            suggestedBaseBet = suggestedBaseBet,
            suggestedNextBet = suggestedNextBet
        )
    }
}

data class LocalMetrics(
    val totalCount: Int = 0,
    val averageMultiplier: Double = 1.0,
    val maxMultiplier: Double = 1.0,
    val lowCrashRate: Float = 0f,
    val highLaunchRate: Float = 0f,
    val netProfitLoss: Double = 0.0,
    val localAdvisory: String = "No historical rounds registered.",
    val localRecommendedTarget: Double = 1.20,
    val localRiskScore: String = "UNKNOWN",
    val isColdStreakActive: Boolean = false,
    val isHotStreakActive: Boolean = false,
    val riskLevel: String = "LOW",
    val suggestedBaseBet: Double = 0.0,
    val suggestedNextBet: Double = 0.0
)

class CompanionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompanionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CompanionViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
