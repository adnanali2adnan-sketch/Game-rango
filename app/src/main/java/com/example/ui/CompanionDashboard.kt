package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CrashRound
import com.example.data.DragonTigerRound
import com.example.data.DragonTigerAnalyzer
import com.example.data.AndarBaharRound
import com.example.data.AndarBaharAnalyzer
import com.example.data.SevenUpDownRound
import com.example.data.SevenUpDownAnalyzer
import com.example.data.BaccaratRound
import com.example.data.BaccaratAnalyzer
import com.example.data.RouletteRound
import com.example.data.RouletteAnalyzer
import com.example.MainActivity
import com.example.ui.theme.*
import com.example.viewmodel.CompanionViewModel
import com.example.viewmodel.LocalMetrics
import java.text.DecimalFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionDashboard(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.historyState.collectAsStateWithLifecycle()
    val aiAdvice by viewModel.aiAdviceText.collectAsStateWithLifecycle()
    val isLoadingAdvice by viewModel.isLoadingAdvice.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    val multInput by viewModel.currentMultiplierInput.collectAsStateWithLifecycle()
    val betInput by viewModel.currentBetAmountInput.collectAsStateWithLifecycle()
    val cashOutInput by viewModel.currentCashOutInput.collectAsStateWithLifecycle()
    val balanceInput by viewModel.userBalanceInput.collectAsStateWithLifecycle()

    val currentGame by viewModel.currentGame.collectAsStateWithLifecycle()
    val dtRounds by viewModel.dtRounds.collectAsStateWithLifecycle()
    val dtResult by viewModel.dtResult.collectAsStateWithLifecycle()

    val abRounds by viewModel.abRounds.collectAsStateWithLifecycle()
    val abResult by viewModel.abResult.collectAsStateWithLifecycle()
    val sevenRounds by viewModel.sevenRounds.collectAsStateWithLifecycle()
    val sevenResult by viewModel.sevenResult.collectAsStateWithLifecycle()
    val baccaratRounds by viewModel.baccaratRounds.collectAsStateWithLifecycle()
    val baccaratResult by viewModel.baccaratResult.collectAsStateWithLifecycle()
    val rouletteRounds by viewModel.rouletteRounds.collectAsStateWithLifecycle()
    val rouletteResult by viewModel.rouletteResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    LaunchedEffect(currentGame) {
        when (currentGame) {
            "DRAGON_TIGER" -> {
                mainActivity?.updateHudMode("VERTICAL", "DRAGON_TIGER")
            }
            "BACCARAT" -> {
                mainActivity?.updateHudMode("VERTICAL", "BACCARAT")
            }
            "ROULETTE" -> {
                mainActivity?.updateHudMode("VERTICAL", "ROULETTE")
            }
            "AVIATOR" -> {
                mainActivity?.updateHudMode("AUTO", "AVIATOR")
            }
            else -> {
                mainActivity?.updateHudMode("HORIZONTAL", "RANGO")
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Local calculated metrics
    val localStats = viewModel.calculateLocalMetrics()

    // Screen tab selection state (0 = Dashboard, 1 = Gemini AI, 2 = Simulator)
    var selectedTab by remember { mutableStateOf(0) }

    // Handle snackbar messages
    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(RangoLimeGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "App Logo Action",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Rango Companion",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RangoTextWhite
                                )
                            )
                            Text(
                                "AI Strategy Cockpit",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RangoDesertGold,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                },
                actions = {
                    // Reset Database Icon
                    IconButton(
                        onClick = { viewModel.clearDatabase() },
                        modifier = Modifier.testTag("action_clear_db")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = RangoDangerRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RangoTealSky
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = RangoHorizon,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = RangoLimeGreen,
                        indicatorColor = RangoLimeGreen,
                        unselectedIconColor = RangoTextMuted,
                        unselectedTextColor = RangoTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "AI Strategies") },
                    label = { Text("Gemini AI", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = RangoLimeGreen,
                        indicatorColor = RangoLimeGreen,
                        unselectedIconColor = RangoTextMuted,
                        unselectedTextColor = RangoTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Calculate, contentDescription = "Martingale") },
                    label = { Text("Simulator", fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = RangoLimeGreen,
                        indicatorColor = RangoLimeGreen,
                        unselectedIconColor = RangoTextMuted,
                        unselectedTextColor = RangoTextMuted
                    )
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = RangoTealSky
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Horizontal Game Selector Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RangoHorizon)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val gameList = listOf(
                    Triple("RANGO", "🎮 RANGO", RangoLimeGreen),
                    Triple("DRAGON_TIGER", "🐉 DRAGON TIGER", RangoDangerRed),
                    Triple("BACCARAT", "🎰 BACCARAT", Color(0xFF1E88E5)),
                    Triple("ROULETTE", "🎡 ROULETTE", Color(0xFFE53935)),
                    Triple("AVIATOR", "✈️ AVIATOR", Color(0xFF1976D2)),
                    Triple("ANDAR_BAHAR", "🚪 ANDAR BAHAR", RangoTealSky),
                    Triple("SEVEN_UP_DOWN", "🎲 7 UP DOWN", RangoDesertGold)
                )
                gameList.forEach { (type, label, labelColor) ->
                    val isSelected = currentGame == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) labelColor else Color.Black.copy(alpha = 0.5f)
                            )
                            .clickable {
                                viewModel.setCurrentGame(type)
                            }
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else RangoTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // High-Contrast Multiplier Strip (Recent items) if not on Dragon Tiger
            if (currentGame != "DRAGON_TIGER") {
                MultiplierHistoryRibbon(history = history.take(15))
            }

            // Main Contents switching between tabs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        currentGame = currentGame,
                        metrics = localStats,
                        historyList = history,
                        onResultLogged = { mult, bet, cashout ->
                            viewModel.addRoundResult(mult, bet, cashout)
                        },
                        multInput = multInput,
                        onMultChange = { viewModel.currentMultiplierInput.value = it },
                        betInput = betInput,
                        onBetChange = { viewModel.currentBetAmountInput.value = it },
                        cashOutInput = cashOutInput,
                        onCashOutChange = { viewModel.currentCashOutInput.value = it },
                        balanceInput = balanceInput,
                        onBalanceChange = { viewModel.userBalanceInput.value = it },
                        dtRounds = dtRounds,
                        dtResult = dtResult,
                        onDTRoundLogged = { viewModel.addDTRound(it) },
                        onDTClear = { viewModel.clearDTRounds() },
                        onDTDelete = { viewModel.deleteDTRound(it) },
                        onDTStatusUpdate = { id, win -> viewModel.updateDTRoundStatus(id, win) },
                        onCrashDelete = { viewModel.deleteCrashRound(it) },
                        onCrashStatusUpdate = { id, win -> viewModel.updateCrashRoundStatus(id, win) },
                        abRounds = abRounds,
                        abResult = abResult,
                        onABRoundLogged = { viewModel.addABRound(it) },
                        onABClear = { viewModel.clearABRounds() },
                        onABDelete = { viewModel.deleteABRound(it) },
                        onABStatusUpdate = { id, win -> viewModel.updateABRoundStatus(id, win) },
                        sevenRounds = sevenRounds,
                        sevenResult = sevenResult,
                        onSevenRoundLogged = { viewModel.addSevenRound(it) },
                        onSevenClear = { viewModel.clearSevenRounds() },
                        onSevenDelete = { viewModel.deleteSevenRound(it) },
                        onSevenStatusUpdate = { id, win -> viewModel.updateSevenRoundStatus(id, win) },
                        baccaratRounds = baccaratRounds,
                        baccaratResult = baccaratResult,
                        onBaccaratRoundLogged = { viewModel.addBaccaratRound(it) },
                        onBaccaratClear = { viewModel.clearBaccaratRounds() },
                        onBaccaratDelete = { viewModel.deleteBaccaratRound(it) },
                        onBaccaratStatusUpdate = { id, win -> viewModel.updateBaccaratRoundStatus(id, win) },
                        rouletteRounds = rouletteRounds,
                        rouletteResult = rouletteResult,
                        onRouletteRoundLogged = { viewModel.addRouletteRound(it) },
                        onRouletteClear = { viewModel.clearRouletteRounds() },
                        onRouletteDelete = { viewModel.deleteRouletteRound(it) },
                        onRouletteStatusUpdate = { id, win -> viewModel.updateRouletteRoundStatus(id, win) }
                    )
                    1 -> {
                        val apiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
                        StrategicAiTab(
                            aiAdvice = aiAdvice,
                            isLoading = isLoadingAdvice,
                            apiKey = apiKey,
                            onApiKeyChange = { viewModel.setGeminiApiKey(it) },
                            onClearApiKey = { viewModel.clearGeminiApiKey() },
                            onRefresh = { viewModel.refreshAiStrategy() }
                        )
                    }
                    2 -> SimulatorTab()
                }
            }
        }
    }
}

/**
 * Top ribbon showing multipliers just like in Rango game cockpit!
 */
@Composable
fun MultiplierHistoryRibbon(history: List<CrashRound>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RangoHorizon)
            .padding(vertical = 10.dp)
    ) {
        Text(
            "RECENT FLIGHT MULTIPLIERS (NEWEST ON LEFT)",
            style = MaterialTheme.typography.labelSmall.copy(
                color = RangoTextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        if (history.isEmpty()) {
            Text(
                "No entries logged. Add rounds below to build stats.",
                style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { round ->
                    val isHigh = round.multiplier >= 2.0
                    val isSuperHigh = round.multiplier >= 4.0
                    val isEpicHigh = round.multiplier >= 10.0

                    val bgBrush = when {
                        isEpicHigh -> Brush.linearGradient(listOf(Color(0xFFE040FB), Color(0xFFFF4081)))
                        isSuperHigh -> Brush.linearGradient(listOf(Color(0xFF8C34FF), Color(0xFF6E00FF)))
                        isHigh -> Brush.linearGradient(listOf(Color(0xFF3498DB), Color(0xFF1ABC9C)))
                        else -> Brush.linearGradient(listOf(RangoHorizon, Color(0xFF2C3E50)))
                    }

                    val txtColor = when {
                        isEpicHigh || isSuperHigh || isHigh -> Color.White
                        else -> RangoTextWhite
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgBrush)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%.2f", round.multiplier) + "x",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = txtColor,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * TAB 1: Dashboard with local math metrics & adding multipliers
 */
@Composable
fun DashboardTab(
    currentGame: String,
    metrics: LocalMetrics,
    historyList: List<CrashRound>,
    onResultLogged: (Double, Double, Double) -> Unit,
    multInput: String,
    onMultChange: (String) -> Unit,
    betInput: String,
    onBetChange: (String) -> Unit,
    cashOutInput: String,
    onCashOutChange: (String) -> Unit,
    balanceInput: String,
    onBalanceChange: (String) -> Unit,
    dtRounds: List<DragonTigerRound>,
    dtResult: DragonTigerAnalyzer.DTResult,
    onDTRoundLogged: (String) -> Unit,
    onDTClear: () -> Unit,
    onDTDelete: (Int) -> Unit,
    onDTStatusUpdate: (Int, Boolean?) -> Unit,
    onCrashDelete: (Int) -> Unit,
    onCrashStatusUpdate: (Int, Boolean?) -> Unit,
    abRounds: List<AndarBaharRound>,
    abResult: AndarBaharAnalyzer.ABResult,
    onABRoundLogged: (String) -> Unit,
    onABClear: () -> Unit,
    onABDelete: (Int) -> Unit,
    onABStatusUpdate: (Int, Boolean?) -> Unit,
    sevenRounds: List<SevenUpDownRound>,
    sevenResult: SevenUpDownAnalyzer.SevenResult,
    onSevenRoundLogged: (String) -> Unit,
    onSevenClear: () -> Unit,
    onSevenDelete: (Int) -> Unit,
    onSevenStatusUpdate: (Int, Boolean?) -> Unit,
    baccaratRounds: List<com.example.data.BaccaratRound>,
    baccaratResult: com.example.data.BaccaratAnalyzer.BaccaratResult,
    onBaccaratRoundLogged: (String) -> Unit,
    onBaccaratClear: () -> Unit,
    onBaccaratDelete: (Int) -> Unit,
    onBaccaratStatusUpdate: (Int, Boolean?) -> Unit,
    rouletteRounds: List<RouletteRound> = emptyList(),
    rouletteResult: RouletteAnalyzer.RouletteResult = RouletteAnalyzer.analyze(emptyList()),
    onRouletteRoundLogged: (String) -> Unit = {},
    onRouletteClear: () -> Unit = {},
    onRouletteDelete: (Int) -> Unit = {},
    onRouletteStatusUpdate: (Int, Boolean?) -> Unit = { _, _ -> }
) {
    if (currentGame == "BACCARAT") {
        BaccaratDashboardContent(
            baccaratRounds = baccaratRounds,
            baccaratResult = baccaratResult,
            onBaccaratRoundLogged = onBaccaratRoundLogged,
            onBaccaratClear = onBaccaratClear,
            onBaccaratDelete = onBaccaratDelete,
            onBaccaratStatusUpdate = onBaccaratStatusUpdate
        )
    } else if (currentGame == "ROULETTE") {
        RouletteDashboardContent(
            rouletteRounds = rouletteRounds,
            rouletteResult = rouletteResult,
            onRouletteRoundLogged = onRouletteRoundLogged,
            onRouletteClear = onRouletteClear,
            onRouletteDelete = onRouletteDelete,
            onRouletteStatusUpdate = onRouletteStatusUpdate
        )
    } else if (currentGame == "DRAGON_TIGER") {
        DragonTigerDashboardContent(
            dtRounds = dtRounds,
            dtResult = dtResult,
            onDTRoundLogged = onDTRoundLogged,
            onDTClear = onDTClear,
            onDTDelete = onDTDelete,
            onDTStatusUpdate = onDTStatusUpdate
        )
    } else if (currentGame == "ANDAR_BAHAR") {
        AndarBaharDashboardContent(
            abRounds = abRounds,
            abResult = abResult,
            onABRoundLogged = onABRoundLogged,
            onABClear = onABClear,
            onABDelete = onABDelete,
            onABStatusUpdate = onABStatusUpdate
        )
    } else if (currentGame == "SEVEN_UP_DOWN") {
        SevenUpDownDashboardContent(
            sevenRounds = sevenRounds,
            sevenResult = sevenResult,
            onSevenRoundLogged = onSevenRoundLogged,
            onSevenClear = onSevenClear,
            onSevenDelete = onSevenDelete,
            onSevenStatusUpdate = onSevenStatusUpdate
        )
    } else {
        CrashDashboardContent(
            currentGame = currentGame,
            metrics = metrics,
            historyList = historyList,
            onResultLogged = onResultLogged,
            multInput = multInput,
            onMultChange = onMultChange,
            betInput = betInput,
            onBetChange = onBetChange,
            cashOutInput = cashOutInput,
            onCashOutChange = onCashOutChange,
            balanceInput = balanceInput,
            onBalanceChange = onBalanceChange,
            onCrashDelete = onCrashDelete,
            onCrashStatusUpdate = onCrashStatusUpdate
        )
    }
}

@Composable
fun BaccaratDashboardContent(
    baccaratRounds: List<com.example.data.BaccaratRound>,
    baccaratResult: com.example.data.BaccaratAnalyzer.BaccaratResult,
    onBaccaratRoundLogged: (String) -> Unit,
    onBaccaratClear: () -> Unit,
    onBaccaratDelete: (Int) -> Unit,
    onBaccaratStatusUpdate: (Int, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Activation & Overlay Status Card
        item {
            val context = LocalContext.current
            val mainActivity = context as? MainActivity
            
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🎰 BACCARAT AI COCKPIT HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF1E88E5),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Run the background floating widget on top of the casino game. Tap quick entries to track trends instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mainActivity?.startFloatingCockpit("BACCARAT", "VERTICAL") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, "bubble", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ACTIVATE HUD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Baccarat Analytics Banner & Cards
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "📊 DYNAMIC RISK & TELEMETRY ENGINE",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("SUGGESTED NEXT", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = baccaratResult.suggestedBet,
                                color = if (baccaratResult.suggestedBet.contains("PLAYER")) Color(0xFF64B5F6) else if (baccaratResult.suggestedBet.contains("BANKER")) RangoDangerRed else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                              )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STREAK STATUS", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(baccaratResult.currentStreak, color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Player: ${baccaratResult.playerPct}%", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Banker: ${baccaratResult.bankerPct}%", color = RangoDangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Tie: ${baccaratResult.tiePct}%", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // 🎰 Advanced Baccarat Multi-Road Diagnostics Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "🎰 BACCARAT MULTI-ROAD COCKPIT",
                            color = RangoDesertGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEXT SIDE PREDICTION:", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (baccaratResult.predictedNext == "UNCERTAIN") "STANDBY (UNCERTAIN)" else baccaratResult.predictedNext,
                                color = if (baccaratResult.predictedNext == "PLAYER") Color(0xFF1E88E5) else if (baccaratResult.predictedNext == "BANKER") RangoDangerRed else Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ROAD VOTING DECISION:", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = baccaratResult.finalRoadDecision,
                                color = if (baccaratResult.finalRoadDecision == "CONTINUE") RangoLimeGreen else if (baccaratResult.finalRoadDecision == "REVERSAL") RangoDesertGold else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        HorizontalDivider(color = RangoTealSky.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Big Eye Boy Signal:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = baccaratResult.bigEyeBoySignal,
                                color = if (baccaratResult.bigEyeBoySignal == "RED") RangoDangerRed else if (baccaratResult.bigEyeBoySignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Small Road Signal:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = baccaratResult.smallRoadSignal,
                                color = if (baccaratResult.smallRoadSignal == "RED") RangoDangerRed else if (baccaratResult.smallRoadSignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cockroach Road Signal:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = baccaratResult.cockroachRoadSignal,
                                color = if (baccaratResult.cockroachRoadSignal == "RED") RangoDangerRed else if (baccaratResult.cockroachRoadSignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = baccaratResult.advice,
                            color = RangoTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Quick Interactive Actions Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "✏️ LOG CURRENT ROUND OUTCOME",
                        color = RangoLimeGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onBaccaratRoundLogged("P") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔵 PLAYER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onBaccaratRoundLogged("T") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                            modifier = Modifier.weight(0.7f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("TIE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onBaccaratRoundLogged("B") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoDangerRed),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔴 BANKER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Stats Card
        item {
            val totalRounds = baccaratRounds.size
            val wins = baccaratRounds.count { it.isWin == true }
            val losses = baccaratRounds.count { it.isWin == false }
            val waits = baccaratRounds.count { it.isWin == null }
            val winRate = if (totalRounds > 0) (wins * 100.0 / totalRounds) else 0.0

            val localRounds = baccaratRounds.filter { it.predictionSource == "LOCAL" }
            val localTotal = localRounds.size
            val localWins = localRounds.count { it.isWin == true }
            val localWinRate = if (localTotal > 0) (localWins * 100.0 / localTotal) else 0.0

            val aiRounds = baccaratRounds.filter { it.predictionSource == "AI" }
            val aiTotal = aiRounds.size
            val aiWins = aiRounds.count { it.isWin == true }
            val aiWinRate = if (aiTotal > 0) (aiWins * 100.0 / aiTotal) else 0.0

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📊 SESSION SCORECARD & WIN RATES",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$totalRounds", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WINS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$wins", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("LOSSES", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$losses", color = RangoDangerRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WAITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$waits", color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WIN RATE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", winRate)}%", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LOCAL PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", localWinRate)}%", color = RangoLimeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $localTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AI PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", aiWinRate)}%", color = RangoDesertGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $aiTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }

        // History Log and Reset Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTORICAL BACCARAT CARDS LOGS (${baccaratRounds.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = RangoTextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                IconButton(
                    onClick = onBaccaratClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear database", tint = RangoDangerRed)
                }
            }
        }

        if (baccaratRounds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No cards rounds logged yet. Tap outcomes above to trace trends.",
                        color = RangoTextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(baccaratRounds) { round ->
                BaccaratHistoryRowItem(
                    round = round,
                    onDelete = onBaccaratDelete,
                    onStatusUpdate = onBaccaratStatusUpdate
                )
            }
        }
    }
}

@Composable
fun BaccaratHistoryRowItem(
    round: com.example.data.BaccaratRound,
    onDelete: (Int) -> Unit,
    onStatusUpdate: (Int, Boolean?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = RangoCardBg,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bg = when (round.result) {
                        "P" -> Color(0xFF1E88E5)
                        "B" -> RangoDangerRed
                        else -> Color(0xFF8E24AA)
                    }
                    val label = when (round.result) {
                        "P" -> "PLAYER"
                        "B" -> "BANKER"
                        else -> "TIE"
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(bg, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (round.result == "P") "P" else if (round.result == "B") "B" else "T",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Column {
                        Text(
                            text = label,
                            color = RangoTextWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Pred: ${round.prediction.ifBlank { "None" }} (${round.predictionSource})",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeBg = when (round.isWin) {
                        true -> RangoLimeGreen.copy(alpha = 0.2f)
                        false -> RangoDangerRed.copy(alpha = 0.2f)
                        null -> RangoDesertGold.copy(alpha = 0.2f)
                    }
                    val badgeColor = when (round.isWin) {
                        true -> RangoLimeGreen
                        false -> RangoDangerRed
                        null -> RangoDesertGold
                    }
                    val badgeText = when (round.isWin) {
                        true -> "WIN"
                        false -> "LOSS"
                        null -> "WAIT"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onDelete(round.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Round",
                            tint = RangoDangerRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Feedback Buttons to manually override / set outcome
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feedback:",
                    color = RangoTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, true) },
                    border = BorderStroke(1.dp, if (round.isWin == true) RangoLimeGreen else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == true) RangoLimeGreen.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(end = 6.dp)
                ) {
                    Icon(Icons.Default.Check, "Win", tint = RangoLimeGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Win", color = RangoLimeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, false) },
                    border = BorderStroke(1.dp, if (round.isWin == false) RangoDangerRed else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == false) RangoDangerRed.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Close, "Loss", tint = RangoDangerRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Loss", color = RangoDangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, null) },
                    border = BorderStroke(1.dp, if (round.isWin == null) RangoDesertGold else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == null) RangoDesertGold.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(start = 6.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, "Wait", tint = RangoDesertGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Wait", color = RangoDesertGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RouletteDashboardContent(
    rouletteRounds: List<RouletteRound>,
    rouletteResult: RouletteAnalyzer.RouletteResult,
    onRouletteRoundLogged: (String) -> Unit,
    onRouletteClear: () -> Unit,
    onRouletteDelete: (Int) -> Unit,
    onRouletteStatusUpdate: (Int, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Activation & Overlay Status Card
        item {
            val context = LocalContext.current
            val mainActivity = context as? MainActivity
            
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🎡 ROULETTE AI COCKPIT HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Run the background floating widget on top of the casino game. Tap quick spin entries to calculate table bias & streaks instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mainActivity?.startFloatingCockpit("ROULETTE", "VERTICAL") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, "bubble", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ACTIVATE HUD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Roulette Analytics Banner & Cards
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "📊 DYNAMIC ROULETTE MATRIX",
                        color = Color(0xFFE53935),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("SUGGESTED NEXT", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = rouletteResult.suggestedBet,
                                color = when {
                                    rouletteResult.suggestedBet.contains("RED") -> RangoDangerRed
                                    rouletteResult.suggestedBet.contains("BLACK") -> Color.White
                                    else -> Color(0xFF64B5F6)
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STREAK STATUS", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(rouletteResult.currentStreak, color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Red: ${rouletteResult.redPct}%", color = RangoDangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Black: ${rouletteResult.blackPct}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Green: ${rouletteResult.greenPct}%", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Even: ${rouletteResult.evenPct}%", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "HOT: ${rouletteResult.hotColor}  |  COLD: ${rouletteResult.coldColor}",
                                color = RangoDesertGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = rouletteResult.advice,
                                color = RangoTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick Entry Buttons
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "🎡 QUICK SPIN ENTRY",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onRouletteRoundLogged("RED") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoDangerRed),
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔴 RED", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onRouletteRoundLogged("0") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(0.8f).height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🟢 0", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onRouletteRoundLogged("BLACK") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("⚫ BLACK", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Spin History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🎡 RECENT SPINS (${rouletteRounds.size})",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                if (rouletteRounds.isNotEmpty()) {
                    TextButton(onClick = onRouletteClear) {
                        Text("RESET ALL", color = RangoDangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (rouletteRounds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No roulette spins recorded yet. Tap RED / 0 / BLACK above.", color = RangoTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(rouletteRounds, key = { it.id }) { round ->
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (round.color) {
                                            "RED" -> RangoDangerRed
                                            "BLACK" -> Color.Black
                                            else -> Color(0xFF2E7D32)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = round.number?.toString() ?: round.result.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Column {
                                Text(
                                    text = "Result: ${round.result} (${round.color})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Prediction: ${round.prediction.ifBlank { "NONE" }}",
                                    color = RangoTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onRouletteStatusUpdate(round.id, true) },
                                border = BorderStroke(1.dp, if (round.isWin == true) RangoLimeGreen else Color.Gray.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (round.isWin == true) RangoLimeGreen.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.Check, "Win", tint = RangoLimeGreen, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Win", color = RangoLimeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onRouletteStatusUpdate(round.id, false) },
                                border = BorderStroke(1.dp, if (round.isWin == false) RangoDangerRed else Color.Gray.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (round.isWin == false) RangoDangerRed.copy(alpha = 0.1f) else Color.Transparent
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.Close, "Loss", tint = RangoDangerRed, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Loss", color = RangoDangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(onClick = { onRouletteDelete(round.id) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = RangoTextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DragonTigerDashboardContent(
    dtRounds: List<DragonTigerRound>,
    dtResult: DragonTigerAnalyzer.DTResult,
    onDTRoundLogged: (String) -> Unit,
    onDTClear: () -> Unit,
    onDTDelete: (Int) -> Unit,
    onDTStatusUpdate: (Int, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Activation & Overlay Status Card
        item {
            val context = LocalContext.current
            val mainActivity = context as? MainActivity
            
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🐉 DRAGON TIGER AI COCKPIT HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = RangoDangerRed,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Run the background floating widget on top of Dragon Tiger card screen. Tap quick entries to track trends instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mainActivity?.startFloatingCockpit("DRAGON_TIGER", "VERTICAL") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, "bubble", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ACTIVATE HUD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Dragon Tiger Analytics Banner & Cards
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "📊 DYNAMIC RISK & TELEMETRY ENGINE",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("SUGGESTED NEXT", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = dtResult.suggestedBet,
                                color = if (dtResult.suggestedBet.contains("DRAGON")) RangoLimeGreen else if (dtResult.suggestedBet.contains("TIGER")) RangoDangerRed else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STREAK STATUS", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(dtResult.currentStreak, color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dragon: ${dtResult.dragonPct}%", color = RangoLimeGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Tiger: ${dtResult.tigerPct}%", color = RangoDangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Tie: ${dtResult.tiePct}%", color = RangoDesertGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // 🎰 Advanced Baccarat Multi-Road Diagnostics Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "🎰 CASINO-STYLE ROAD COCKPIT",
                            color = RangoDesertGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEXT SIDE PREDICTION:", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (dtResult.predictedNext == "UNCERTAIN") "STANDBY (UNCERTAIN)" else dtResult.predictedNext,
                                color = if (dtResult.predictedNext == "DRAGON") Color(0xFF1E88E5) else if (dtResult.predictedNext == "TIGER") RangoDangerRed else Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ROAD VOTING DECISION:", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = dtResult.finalRoadDecision,
                                color = if (dtResult.finalRoadDecision == "CONTINUE") RangoLimeGreen else if (dtResult.finalRoadDecision == "REVERSAL") RangoDesertGold else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        HorizontalDivider(color = RangoTealSky.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Big Eye Boy Signal:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = dtResult.bigEyeBoySignal,
                                color = if (dtResult.bigEyeBoySignal == "RED") RangoDangerRed else if (dtResult.bigEyeBoySignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Small Road Signal:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = dtResult.smallRoadSignal,
                                color = if (dtResult.smallRoadSignal == "RED") RangoDangerRed else if (dtResult.smallRoadSignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cockroach Road Signal:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = dtResult.cockroachRoadSignal,
                                color = if (dtResult.cockroachRoadSignal == "RED") RangoDangerRed else if (dtResult.cockroachRoadSignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = dtResult.advice,
                            color = RangoTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Quick Interactive Actions Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "✏️ LOG CURRENT ROUND OUTCOME",
                        color = RangoLimeGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onDTRoundLogged("D") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🐉 DRAGON", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onDTRoundLogged("X") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                            modifier = Modifier.weight(0.7f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("TIE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onDTRoundLogged("T") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoDangerRed),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🐯 TIGER", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Stats Card
        item {
            val totalRounds = dtRounds.size
            val wins = dtRounds.count { it.isWin == true }
            val losses = dtRounds.count { it.isWin == false }
            val waits = dtRounds.count { it.isWin == null }
            val winRate = if (totalRounds > 0) (wins * 100.0 / totalRounds) else 0.0

            val localRounds = dtRounds.filter { it.predictionSource == "LOCAL" }
            val localTotal = localRounds.size
            val localWins = localRounds.count { it.isWin == true }
            val localWinRate = if (localTotal > 0) (localWins * 100.0 / localTotal) else 0.0

            val aiRounds = dtRounds.filter { it.predictionSource == "AI" }
            val aiTotal = aiRounds.size
            val aiWins = aiRounds.count { it.isWin == true }
            val aiWinRate = if (aiTotal > 0) (aiWins * 100.0 / aiTotal) else 0.0

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📊 SESSION SCORECARD & WIN RATES",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$totalRounds", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WINS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$wins", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("LOSSES", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$losses", color = RangoDangerRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WAITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$waits", color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WIN RATE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", winRate)}%", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LOCAL PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", localWinRate)}%", color = RangoLimeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $localTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AI PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", aiWinRate)}%", color = RangoDesertGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $aiTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }

        // History Log and Reset Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTORICAL CARDS LOGS (${dtRounds.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = RangoTextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                IconButton(
                    onClick = onDTClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear database", tint = RangoDangerRed)
                }
            }
        }

        if (dtRounds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No cards rounds logged yet. Tap outcomes above to trace trends.",
                        color = RangoTextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(dtRounds) { round ->
                DTHistoryRowItem(
                    round = round,
                    onDelete = onDTDelete,
                    onStatusUpdate = onDTStatusUpdate
                )
            }
        }
    }
}

@Composable
fun DTHistoryRowItem(
    round: DragonTigerRound,
    onDelete: (Int) -> Unit,
    onStatusUpdate: (Int, Boolean?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RangoCardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bg = when (round.result) {
                        "D" -> RangoLimeGreen
                        "T" -> RangoDangerRed
                        else -> Color(0xFF8E24AA)
                    }
                    val label = when (round.result) {
                        "D" -> "DRAGON"
                        "T" -> "TIGER"
                        else -> "TIE"
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(bg, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (round.result == "D") "🐉" else if (round.result == "T") "🐯" else "T",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Column {
                        Text(
                            text = label,
                            color = RangoTextWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Pred: ${round.prediction.ifBlank { "None" }} (${round.predictionSource})",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeBg = when (round.isWin) {
                        true -> RangoLimeGreen.copy(alpha = 0.2f)
                        false -> RangoDangerRed.copy(alpha = 0.2f)
                        null -> RangoDesertGold.copy(alpha = 0.2f)
                    }
                    val badgeColor = when (round.isWin) {
                        true -> RangoLimeGreen
                        false -> RangoDangerRed
                        null -> RangoDesertGold
                    }
                    val badgeText = when (round.isWin) {
                        true -> "WIN"
                        false -> "LOSS"
                        null -> "WAIT"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onDelete(round.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Round",
                            tint = RangoDangerRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Feedback Buttons to manually override / set outcome
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feedback:",
                    color = RangoTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, true) },
                    border = BorderStroke(1.dp, if (round.isWin == true) RangoLimeGreen else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == true) RangoLimeGreen.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(end = 6.dp)
                ) {
                    Icon(Icons.Default.Check, "Win", tint = RangoLimeGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Win", color = RangoLimeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, false) },
                    border = BorderStroke(1.dp, if (round.isWin == false) RangoDangerRed else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == false) RangoDangerRed.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Close, "Loss", tint = RangoDangerRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Loss", color = RangoDangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, null) },
                    border = BorderStroke(1.dp, if (round.isWin == null) RangoDesertGold else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == null) RangoDesertGold.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(start = 6.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, "Wait", tint = RangoDesertGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Wait", color = RangoDesertGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AndarBaharDashboardContent(
    abRounds: List<AndarBaharRound>,
    abResult: AndarBaharAnalyzer.ABResult,
    onABRoundLogged: (String) -> Unit,
    onABClear: () -> Unit,
    onABDelete: (Int) -> Unit,
    onABStatusUpdate: (Int, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Activation & Overlay Status Card
        item {
            val context = LocalContext.current
            val mainActivity = context as? MainActivity
            
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🚪 ANDAR BAHAR AI COCKPIT HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = RangoLimeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Run the background floating widget on top of Andar Bahar card screen. Tap quick entries to track trends instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mainActivity?.startFloatingCockpit("ANDAR_BAHAR", "VERTICAL") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, "bubble", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ACTIVATE HUD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Analytics Banner & Cards
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "🚪 ANDAR BAHAR TREND ANALYSIS",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("SUGGESTED NEXT", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = abResult.suggestedBet,
                                color = if (abResult.suggestedBet.contains("ANDAR")) RangoLimeGreen else if (abResult.suggestedBet.contains("BAHAR")) RangoDangerRed else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STREAK STATUS", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(abResult.currentStreak, color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Andar: ${abResult.andarPct}%", color = RangoLimeGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Bahar: ${abResult.baharPct}%", color = RangoDangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Hot: ${abResult.hotSide}", color = RangoDesertGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = abResult.advice,
                            color = RangoTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }



        // Stats Card
        item {
            val totalRounds = abRounds.size
            val wins = abRounds.count { it.isWin == true }
            val losses = abRounds.count { it.isWin == false }
            val waits = abRounds.count { it.isWin == null }
            val winRate = if (totalRounds > 0) (wins * 100.0 / totalRounds) else 0.0

            val localRounds = abRounds.filter { it.predictionSource == "LOCAL" }
            val localTotal = localRounds.size
            val localWins = localRounds.count { it.isWin == true }
            val localWinRate = if (localTotal > 0) (localWins * 100.0 / localTotal) else 0.0

            val aiRounds = abRounds.filter { it.predictionSource == "AI" }
            val aiTotal = aiRounds.size
            val aiWins = aiRounds.count { it.isWin == true }
            val aiWinRate = if (aiTotal > 0) (aiWins * 100.0 / aiTotal) else 0.0

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📊 SESSION SCORECARD & WIN RATES",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$totalRounds", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WINS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$wins", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("LOSSES", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$losses", color = RangoDangerRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WAITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$waits", color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WIN RATE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", winRate)}%", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LOCAL PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", localWinRate)}%", color = RangoLimeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $localTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AI PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", aiWinRate)}%", color = RangoDesertGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $aiTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }

        // History list Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTORICAL ROUNDS LOGS (${abRounds.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = RangoTextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                IconButton(
                    onClick = onABClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear database", tint = RangoDangerRed)
                }
            }
        }

        if (abRounds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No Andar Bahar rounds logged yet. Tap outcomes above to trace trends.",
                        color = RangoTextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(abRounds) { round ->
                ABHistoryRowItem(
                    round = round,
                    onDelete = onABDelete,
                    onStatusUpdate = onABStatusUpdate
                )
            }
        }
    }
}

@Composable
fun ABHistoryRowItem(
    round: AndarBaharRound,
    onDelete: (Int) -> Unit,
    onStatusUpdate: (Int, Boolean?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RangoCardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (round.result == "A") RangoLimeGreen else RangoDangerRed,
                                RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = round.result,
                            color = if (round.result == "A") Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Column {
                        Text(
                            text = if (round.result == "A") "Andar" else "Bahar",
                            color = RangoTextWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Pred: ${round.prediction.ifBlank { "None" }} (${round.predictionSource})",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeBg = when (round.isWin) {
                        true -> RangoLimeGreen.copy(alpha = 0.2f)
                        false -> RangoDangerRed.copy(alpha = 0.2f)
                        null -> RangoDesertGold.copy(alpha = 0.2f)
                    }
                    val badgeColor = when (round.isWin) {
                        true -> RangoLimeGreen
                        false -> RangoDangerRed
                        null -> RangoDesertGold
                    }
                    val badgeText = when (round.isWin) {
                        true -> "WIN"
                        false -> "LOSS"
                        null -> "WAIT"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onDelete(round.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Round",
                            tint = RangoDangerRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Feedback Buttons to manually override / set outcome
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feedback:",
                    color = RangoTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, true) },
                    border = BorderStroke(1.dp, if (round.isWin == true) RangoLimeGreen else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == true) RangoLimeGreen.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(end = 6.dp)
                ) {
                    Icon(Icons.Default.Check, "Win", tint = RangoLimeGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Win", color = RangoLimeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, false) },
                    border = BorderStroke(1.dp, if (round.isWin == false) RangoDangerRed else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == false) RangoDangerRed.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Close, "Loss", tint = RangoDangerRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Loss", color = RangoDangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, null) },
                    border = BorderStroke(1.dp, if (round.isWin == null) RangoDesertGold else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == null) RangoDesertGold.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(start = 6.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, "Wait", tint = RangoDesertGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Wait", color = RangoDesertGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SevenUpDownDashboardContent(
    sevenRounds: List<SevenUpDownRound>,
    sevenResult: SevenUpDownAnalyzer.SevenResult,
    onSevenRoundLogged: (String) -> Unit,
    onSevenClear: () -> Unit,
    onSevenDelete: (Int) -> Unit,
    onSevenStatusUpdate: (Int, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Activation & Overlay Status Card
        item {
            val context = LocalContext.current
            val mainActivity = context as? MainActivity
            
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🎲 7 UP DOWN AI COCKPIT HUD",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = RangoDesertGold,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Run the background floating widget on top of 7 Up Down card screen. Tap quick entries to track trends instantly.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mainActivity?.startFloatingCockpit("SEVEN_UP_DOWN", "VERTICAL") },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, "bubble", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ACTIVATE HUD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Analytics Banner & Cards
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "🎲 7 UP DOWN TREND ANALYSIS",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("SUGGESTED NEXT", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = sevenResult.suggestedBet,
                                color = if (sevenResult.suggestedBet.contains("UP")) RangoLimeGreen else if (sevenResult.suggestedBet.contains("DOWN")) RangoDangerRed else RangoDesertGold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("STREAK STATUS", color = RangoTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(sevenResult.currentStreak, color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("7 Up: ${sevenResult.upPct}%", color = RangoLimeGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("7 Down: ${sevenResult.downPct}%", color = RangoDangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Lucky 7: ${sevenResult.sevenPct}%", color = RangoDesertGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = sevenResult.advice,
                            color = RangoTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }



        // Stats Card
        item {
            val totalRounds = sevenRounds.size
            val wins = sevenRounds.count { it.isWin == true }
            val losses = sevenRounds.count { it.isWin == false }
            val waits = sevenRounds.count { it.isWin == null }
            val winRate = if (totalRounds > 0) (wins * 100.0 / totalRounds) else 0.0

            val localRounds = sevenRounds.filter { it.predictionSource == "LOCAL" }
            val localTotal = localRounds.size
            val localWins = localRounds.count { it.isWin == true }
            val localWinRate = if (localTotal > 0) (localWins * 100.0 / localTotal) else 0.0

            val aiRounds = sevenRounds.filter { it.predictionSource == "AI" }
            val aiTotal = aiRounds.size
            val aiWins = aiRounds.count { it.isWin == true }
            val aiWinRate = if (aiTotal > 0) (aiWins * 100.0 / aiTotal) else 0.0

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📊 SESSION SCORECARD & WIN RATES",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$totalRounds", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WINS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$wins", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("LOSSES", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$losses", color = RangoDangerRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WAITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$waits", color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WIN RATE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", winRate)}%", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LOCAL PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", localWinRate)}%", color = RangoLimeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $localTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AI PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", aiWinRate)}%", color = RangoDesertGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $aiTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }

        // History list Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HISTORICAL ROUNDS LOGS (${sevenRounds.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = RangoTextMuted,
                        letterSpacing = 0.5.sp
                    )
                )
                IconButton(
                    onClick = onSevenClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear database", tint = RangoDangerRed)
                }
            }
        }

        if (sevenRounds.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No 7 Up Down rounds logged yet. Tap outcomes above to trace trends.",
                        color = RangoTextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(sevenRounds) { round ->
                SevenHistoryRowItem(
                    round = round,
                    onDelete = onSevenDelete,
                    onStatusUpdate = onSevenStatusUpdate
                )
            }
        }
    }
}

@Composable
fun SevenHistoryRowItem(
    round: SevenUpDownRound,
    onDelete: (Int) -> Unit,
    onStatusUpdate: (Int, Boolean?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RangoCardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bg = when (round.result) {
                        "U" -> RangoLimeGreen
                        "D" -> RangoDangerRed
                        else -> RangoDesertGold
                    }
                    val label = when (round.result) {
                        "U" -> "UP"
                        "D" -> "DOWN"
                        else -> "7"
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(bg, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (round.result == "U" || round.result == "7") Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }

                    Column {
                        Text(
                            text = when (round.result) {
                                "U" -> "7 Up"
                                "D" -> "7 Down"
                                else -> "Lucky 7"
                            },
                            color = RangoTextWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Pred: ${round.prediction.ifBlank { "None" }} (${round.predictionSource})",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeBg = when (round.isWin) {
                        true -> RangoLimeGreen.copy(alpha = 0.2f)
                        false -> RangoDangerRed.copy(alpha = 0.2f)
                        null -> RangoDesertGold.copy(alpha = 0.2f)
                    }
                    val badgeColor = when (round.isWin) {
                        true -> RangoLimeGreen
                        false -> RangoDangerRed
                        null -> RangoDesertGold
                    }
                    val badgeText = when (round.isWin) {
                        true -> "WIN"
                        false -> "LOSS"
                        null -> "WAIT"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onDelete(round.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Round",
                            tint = RangoDangerRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Feedback Buttons to manually override / set outcome
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feedback:",
                    color = RangoTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, true) },
                    border = BorderStroke(1.dp, if (round.isWin == true) RangoLimeGreen else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == true) RangoLimeGreen.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(end = 6.dp)
                ) {
                    Icon(Icons.Default.Check, "Win", tint = RangoLimeGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Win", color = RangoLimeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, false) },
                    border = BorderStroke(1.dp, if (round.isWin == false) RangoDangerRed else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == false) RangoDangerRed.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Close, "Loss", tint = RangoDangerRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Loss", color = RangoDangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, null) },
                    border = BorderStroke(1.dp, if (round.isWin == null) RangoDesertGold else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == null) RangoDesertGold.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(start = 6.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, "Wait", tint = RangoDesertGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Wait", color = RangoDesertGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CrashDashboardContent(
    currentGame: String,
    metrics: LocalMetrics,
    historyList: List<CrashRound>,
    onResultLogged: (Double, Double, Double) -> Unit,
    multInput: String,
    onMultChange: (String) -> Unit,
    betInput: String,
    onBetChange: (String) -> Unit,
    cashOutInput: String,
    onCashOutChange: (String) -> Unit,
    balanceInput: String,
    onBalanceChange: (String) -> Unit,
    onCrashDelete: (Int) -> Unit,
    onCrashStatusUpdate: (Int, Boolean?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Floating Service Controls Card
        item {
            val context = LocalContext.current
            val mainActivity = context as? MainActivity
            
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🎛️ COCKPIT HUD & SCREEN SCRAPER PLATFORM",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = RangoLimeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Run the background floating widget on top of Rango/Aviator or authorize real-time OCR screen text readers.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                mainActivity?.startFloatingCockpit(
                                    game = currentGame, 
                                    mode = if (currentGame == "AVIATOR") "AUTO" else "HORIZONTAL"
                                ) 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Dashboard, "bubble", tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ACTIVATE HUD", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { mainActivity?.startOcrScreenCapture() },
                            colors = ButtonDefaults.buttonColors(containerColor = RangoTealSky),
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, "ocr", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AUTH OCR CAPTURE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Local Smart Metrics Row
        item {
            QuickStatsRow(metrics = metrics)
        }

        // Dynamic Money Management Dashboard Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "💼 SMART BANKROLL & BET MANAGER",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RangoDesertGold
                        )
                    )
                    
                    Text(
                        "Suggested bankroll allocations & recovery steps calculated in real-time.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = balanceInput,
                            onValueChange = onBalanceChange,
                            label = { Text("Running Balance (PKR)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = RangoTextWhite,
                                unfocusedTextColor = RangoTextWhite,
                                focusedContainerColor = RangoTealSky,
                                unfocusedContainerColor = RangoTealSky
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Base Bet Column
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("SUGGESTED BASE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "PKR ${String.format("%.2f", metrics.suggestedBaseBet)}",
                                    color = RangoLimeGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text("1% of wallet", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }

                        // Next Bet Column
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("STRATEGIC BET", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "PKR ${String.format("%.2f", metrics.suggestedNextBet)}",
                                    color = RangoDesertGold,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    if (metrics.suggestedNextBet > metrics.suggestedBaseBet) "Recovery mode" else "Base mode",
                                    color = if (metrics.suggestedNextBet > metrics.suggestedBaseBet) RangoDangerRed else RangoTextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Target Column
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("SAFE TARGET", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${String.format("%.2f", metrics.localRecommendedTarget)}x",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Auto Cashout",
                                    color = RangoTextMuted,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Round form
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "LOG NEW FLIGHT OUTCOME",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = RangoLimeGreen,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Text(
                        "When Rango crash round finishes, enter the multiplier and view target simulations.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = multInput,
                            onValueChange = onMultChange,
                            label = { Text("Crash Mult (e.g. 1.54)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = RangoTextWhite,
                                unfocusedTextColor = RangoTextWhite,
                                focusedContainerColor = RangoTealSky,
                                unfocusedContainerColor = RangoTealSky
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_multiplier"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = betInput,
                            onValueChange = onBetChange,
                            label = { Text("Bet Size (Optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = RangoTextWhite,
                                unfocusedTextColor = RangoTextWhite,
                                focusedContainerColor = RangoTealSky,
                                unfocusedContainerColor = RangoTealSky
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_bet_amount"),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = cashOutInput,
                        onValueChange = onCashOutChange,
                        label = { Text("Simulation Cash-Out Limit (Optional, e.g. 1.5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = RangoTextWhite,
                            unfocusedTextColor = RangoTextWhite,
                            focusedContainerColor = RangoTealSky,
                            unfocusedContainerColor = RangoTealSky
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_cashout_limit"),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val mult = multInput.toDoubleOrNull() ?: 1.0
                            val bet = betInput.toDoubleOrNull() ?: 0.0
                            val cashout = cashOutInput.toDoubleOrNull() ?: 0.0
                            onResultLogged(mult, bet, cashout)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("button_submit_log")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Log", tint = Color.Black)
                            Text("LOG & RECALCULATE PROFILE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Mathematical Smart advisor on device (Locally determined algorithms)
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📲 LOCAL REAL-TIME TREND ENGINE",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = RangoDesertGold,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RangoLimeGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "LOCAL INSTANT",
                                color = RangoLimeGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = RangoHorizon)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Trend Icon",
                            tint = RangoLimeGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Current Calculated Streak: ${metrics.localRiskScore}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RangoTextWhite
                                )
                            )
                            Text(
                                text = "Target recommendation: ${metrics.localRecommendedTarget}x",
                                style = MaterialTheme.typography.bodySmall.copy(color = RangoDesertGold),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = metrics.localAdvisory,
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Stats Card for Crash
        item {
            val totalRounds = historyList.size
            val hits = historyList.count { it.isWin == true }
            val misses = historyList.count { it.isWin == false }
            val waits = historyList.count { it.isWin == null }
            val hitRate = if (totalRounds > 0) (hits * 100.0 / totalRounds) else 0.0

            val localRounds = historyList.filter { it.predictionSource == "LOCAL" }
            val localTotal = localRounds.size
            val localWins = localRounds.count { it.isWin == true }
            val localWinRate = if (localTotal > 0) (localWins * 100.0 / localTotal) else 0.0

            val aiRounds = historyList.filter { it.predictionSource == "AI" }
            val aiTotal = aiRounds.size
            val aiWins = aiRounds.count { it.isWin == true }
            val aiWinRate = if (aiTotal > 0) (aiWins * 100.0 / aiTotal) else 0.0

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📊 SESSION SCORECARD & HIT RATES",
                        color = RangoDesertGold,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TOTAL", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$totalRounds", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("HITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$hits", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("NOT HITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$misses", color = RangoDangerRed, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("WAITS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("$waits", color = RangoDesertGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Column {
                            Text("HIT RATE", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", hitRate)}%", color = RangoLimeGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LOCAL PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", localWinRate)}%", color = RangoLimeGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $localTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AI PREDICTIONS", color = RangoTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${String.format("%.1f", aiWinRate)}%", color = RangoDesertGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Rounds: $aiTotal", color = RangoTextMuted, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }

        // History Log List header/controls
        item {
            Text(
                text = "HISTORICAL ROUND STATISTICS LOGS",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = RangoTextMuted,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (historyList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No rounds logs added yet. Add a multiplier above to view history timeline.",
                        color = RangoTextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(historyList) { round ->
                HistoryRowItem(
                    round = round,
                    onDelete = onCrashDelete,
                    onStatusUpdate = onCrashStatusUpdate
                )
            }
        }
    }
}

@Composable
fun QuickStatsRow(metrics: LocalMetrics) {
    val df = DecimalFormat("#.##")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Safe Zone Win Rate
        Card(
            colors = CardDefaults.cardColors(containerColor = RangoHorizon),
            modifier = Modifier
                .weight(1f)
                .height(95.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "WIN RATE (>=2x)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RangoTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    "${df.format(metrics.highLaunchRate)}%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = RangoLimeGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                )
                Text(
                    "Avg: ${df.format(metrics.averageMultiplier)}x",
                    style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted)
                )
            }
        }

        // Fast Crash (<1.3x) Danger Zones
        Card(
            colors = CardDefaults.cardColors(containerColor = RangoHorizon),
            modifier = Modifier
                .weight(1f)
                .height(95.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "COLD CRASH (<1.3x)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RangoTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    "${df.format(metrics.lowCrashRate)}%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = RangoDangerRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                )
                Text(
                    "Total: ${metrics.totalCount} Rds",
                    style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted)
                )
            }
        }

        // Calculated P/L simulated
        Card(
            colors = CardDefaults.cardColors(containerColor = RangoHorizon),
            modifier = Modifier
                .weight(1f)
                .height(95.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SIMULATED NET PL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RangoTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    metrics.netProfitLoss.let { if (it >= 0) "+${df.format(it)}" else df.format(it) },
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = if (metrics.netProfitLoss >= 0) RangoDesertGold else RangoDangerRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                )
                Text(
                    "Max Soar: ${df.format(metrics.maxMultiplier)}x",
                    style = MaterialTheme.typography.labelSmall.copy(color = RangoDesertGold)
                )
            }
        }
    }
}

@Composable
fun HistoryRowItem(
    round: CrashRound,
    onDelete: (Int) -> Unit,
    onStatusUpdate: (Int, Boolean?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RangoCardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                when {
                                    round.multiplier >= 4.0 -> Color(0xFF8C34FF)
                                    round.multiplier >= 2.0 -> RangoLimeGreen
                                    round.multiplier < 1.3 -> RangoDangerRed
                                    else -> RangoSandWarm
                                },
                                CircleShape
                            )
                    )

                    Column {
                        Text(
                            "${round.multiplier}x Flight",
                            color = RangoTextWhite,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Target: ${if (round.cashOutMultiplier > 0.0) "${round.cashOutMultiplier}x" else "None"} | Pred: ${round.prediction.ifBlank { "None" }} (${round.predictionSource})",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    }
                }

                // Status Badge & Delete Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeBg = when (round.isWin) {
                        true -> RangoLimeGreen.copy(alpha = 0.2f)
                        false -> RangoDangerRed.copy(alpha = 0.2f)
                        null -> RangoDesertGold.copy(alpha = 0.2f)
                    }
                    val badgeColor = when (round.isWin) {
                        true -> RangoLimeGreen
                        false -> RangoDangerRed
                        null -> RangoDesertGold
                    }
                    val badgeText = when (round.isWin) {
                        true -> "HIT"
                        false -> "NOT HIT"
                        null -> "WAIT"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { onDelete(round.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Round",
                            tint = RangoDangerRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Feedback Buttons to manually override / set outcome
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feedback:",
                    color = RangoTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, true) },
                    border = BorderStroke(1.dp, if (round.isWin == true) RangoLimeGreen else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == true) RangoLimeGreen.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(end = 6.dp)
                ) {
                    Icon(Icons.Default.Check, "Hit", tint = RangoLimeGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Hit", color = RangoLimeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, false) },
                    border = BorderStroke(1.dp, if (round.isWin == false) RangoDangerRed else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == false) RangoDangerRed.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Close, "Not Hit", tint = RangoDangerRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Not Hit", color = RangoDangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onStatusUpdate(round.id, null) },
                    border = BorderStroke(1.dp, if (round.isWin == null) RangoDesertGold else Color.Gray.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (round.isWin == null) RangoDesertGold.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(26.dp)
                        .padding(start = 6.dp)
                ) {
                    Icon(Icons.Default.HourglassEmpty, "Wait", tint = RangoDesertGold, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Wait", color = RangoDesertGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * TAB 2: Strategic AI advisor tab powered by Google Gemini API
 */
@Composable
fun StrategicAiTab(
    aiAdvice: String,
    isLoading: Boolean,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onClearApiKey: () -> Unit = {},
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Secure API Key Input Card
        var showPassword by remember { mutableStateOf(false) }
        var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
        var isSavingAndTesting by remember { mutableStateOf(false) }
        var isTestingValidation by remember { mutableStateOf(false) }
        var isTestingRawHello by remember { mutableStateOf(false) }
        val isAnyTesting = isSavingAndTesting || isTestingValidation || isTestingRawHello

        var testResult by remember { mutableStateOf<com.example.api.GeminiDebugReport?>(null) }
        val liveReport by com.example.api.GeminiClient.latestReport.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current

        val decryptedSharedPrefKey = remember(apiKey, localApiKey) { com.example.util.SecurePrefs.getGeminiApiKey(context) }
        val truncatedSharedPrefKey = if (decryptedSharedPrefKey.length >= 16) "${decryptedSharedPrefKey.take(8)}...${decryptedSharedPrefKey.takeLast(8)}" else decryptedSharedPrefKey
        val memoryKeyTruncated = if (apiKey.length >= 16) "${apiKey.take(8)}...${apiKey.takeLast(8)}" else apiKey

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "🔐 SECURE GEMINI INTEGRATION PROFILE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = RangoLimeGreen,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    "Enter your private Google Gemini API Key. Stored securely in Encrypted SharedPreferences.",
                    style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                )
                OutlinedTextField(
                    value = localApiKey,
                    onValueChange = { localApiKey = it },
                    label = { Text("Gemini API Key") },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = RangoTextWhite,
                        unfocusedTextColor = RangoTextWhite,
                        focusedContainerColor = RangoTealSky,
                        unfocusedContainerColor = RangoTealSky
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_gemini_api_key"),
                    singleLine = true,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Key Unmasking",
                                tint = RangoLimeGreen
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Profile Sync Status & Live Checks
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "🔍 LIVE KEY INTEGRITY AUDIT",
                            style = MaterialTheme.typography.labelSmall.copy(color = RangoLimeGreen, fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("📁 SecurePrefs Key:", style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
                                Text(
                                    text = if (decryptedSharedPrefKey.isNotEmpty()) "$truncatedSharedPrefKey (${decryptedSharedPrefKey.length} chars)" else "Not Configured (Empty)",
                                    color = if (decryptedSharedPrefKey.isNotEmpty()) RangoTextWhite else Color(0xFFE57373),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("🧠 ViewModel Memory:", style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
                                Text(
                                    text = if (apiKey.isNotEmpty()) "$memoryKeyTruncated (${apiKey.length} chars)" else "Not Loaded (Empty)",
                                    color = if (apiKey.isNotEmpty()) RangoTextWhite else Color(0xFFE57373),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // AI Model Selection & Quota Protection
                val currentModelPref by com.example.api.GeminiClient.selectedModelPref.collectAsStateWithLifecycle()
                var modelDropdownExpanded by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⚡ GEMINI MODEL & QUOTA OPTIMIZER",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "FREE TIER: 500 RPD",
                                style = MaterialTheme.typography.labelSmall.copy(color = RangoLimeGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            )
                        }

                        Text(
                            "Flash Lite models give 500 requests/day (vs 20 on Flash). Auto mode automatically falls back if a quota limit is reached.",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted, fontSize = 11.sp)
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { modelDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentLabel = com.example.api.GeminiClient.SUPPORTED_MODELS.find { it.first == currentModelPref }?.second ?: currentModelPref
                                    Text(
                                        text = currentLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Model",
                                        tint = Color(0xFF64B5F6)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                com.example.api.GeminiClient.SUPPORTED_MODELS.forEach { (modelKey, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (modelKey == currentModelPref) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (modelKey == currentModelPref) RangoLimeGreen else Color.Unspecified
                                                )
                                            }
                                        },
                                        onClick = {
                                            com.example.api.GeminiClient.setSelectedModel(modelKey)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // SAVE & TEST button
                Button(
                    onClick = {
                        // Immediately save to SecurePrefs via VM
                        onApiKeyChange(localApiKey)
                        
                        isSavingAndTesting = true
                        testResult = null
                        scope.launch {
                            val report = com.example.api.GeminiClient.testApiKey(localApiKey)
                            testResult = report
                            isSavingAndTesting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("save_api_key_button"),
                    enabled = !isAnyTesting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSavingAndTesting) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVING & TESTING...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save Key Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE & TEST API KEY", fontWeight = FontWeight.Bold)
                    }
                }

                // Dedicated simple "Hello" validation test button
                Button(
                    onClick = {
                        onApiKeyChange(localApiKey)
                        isTestingValidation = true
                        testResult = null
                        scope.launch {
                            val report = com.example.api.GeminiClient.testSimpleHello(localApiKey)
                            testResult = report
                            isTestingValidation = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RangoDesertGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("api_validation_test_button"),
                    enabled = !isAnyTesting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTestingValidation) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TESTING VALIDATION...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Validation Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("API VALIDATION TEST (PROMPT: \"HELLO\")", fontWeight = FontWeight.Bold)
                    }
                }

                // Raw Hello Test Button
                Button(
                    onClick = {
                        onApiKeyChange(localApiKey)
                        isTestingRawHello = true
                        testResult = null
                        scope.launch {
                            val report = com.example.api.GeminiClient.testRawHello(localApiKey)
                            testResult = report
                            isTestingRawHello = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6), contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("raw_hello_test_button"),
                    enabled = !isAnyTesting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTestingRawHello) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RUNNING RAW HELLO TEST...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.BugReport, contentDescription = "Debug Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RAW HELLO TEST (JSON RESPONSE)", fontWeight = FontWeight.Bold)
                    }
                }

                // Clear Saved API Key Button
                Button(
                    onClick = {
                        onClearApiKey()
                        localApiKey = ""
                        testResult = null
                        onRefresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().testTag("clear_api_key_button"),
                    enabled = !isAnyTesting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CLEAR SAVED API KEY & RELOAD", fontWeight = FontWeight.Bold)
                }

                // Complete Debug & Diagnostics Report Display
                val activeReport = testResult ?: if (liveReport.requestSent != "No") liveReport else null
                activeReport?.let { report ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (report.isSuccess) Color(0xFF1B5E20).copy(alpha = 0.35f) else Color(0xFFB71C1C).copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (report.isSuccess) Color(0xFF81C784).copy(alpha = 0.5f) else Color(0xFFE57373).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (report.isSuccess) "✅ AUDIT: API Key Working Successfully" else "❌ AUDIT: Connection / Config Issue",
                                color = if (report.isSuccess) Color(0xFF81C784) else Color(0xFFE57373),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("📋 DETAILED DIAGNOSTICS:", color = RangoTextWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("• API Key Detected: ${if (report.savedKeyLength > 0) "Yes (${report.savedKeyLength} chars)" else "No"}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Endpoint Used: ${report.endpointUsed}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Model Used: ${report.modelNameUsed}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Request Sent: ${report.requestSent}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• HTTP Status Code: ${report.httpCode}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Headers Mapped: ${report.headersUsed}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Error Message: ${report.errorMessage}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Final Failure Reason: ${report.finalFailureReason}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            Text("• Total Request Count: ${report.totalRequestsCount}", color = RangoTextMuted, style = MaterialTheme.typography.bodySmall)
                            
                            val originLabel = when (report.rateLimitSource) {
                                "APP" -> "📱 App-Side Local Throttle (Preventing key/billing spam)"
                                "SERVER" -> "☁️ Gemini API Server 429 (Too Many Requests / Quota Exhausted)"
                                else -> "N/A"
                            }
                            if (report.rateLimitSource != "N/A") {
                                Text("• Rate Limit Origin: $originLabel", color = if (report.rateLimitSource == "APP") Color(0xFFFFB74D) else Color(0xFFE57373), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text("📡 SENT PAYLOAD:", color = RangoLimeGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                             ) {
                                 Box(modifier = Modifier.padding(6.dp)) {
                                     androidx.compose.foundation.text.selection.SelectionContainer {
                                         Text(
                                             text = report.requestPayload,
                                             fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                             color = Color(0xFFA5D6A7),
                                             style = MaterialTheme.typography.bodySmall
                                         )
                                     }
                                 }
                             }

                             Spacer(modifier = Modifier.height(4.dp))

                             Text("📥 RECEIVED RESPONSE BODY:", color = RangoLimeGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                             Card(
                                 colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                 shape = RoundedCornerShape(4.dp),
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 Box(
                                     modifier = Modifier
                                         .padding(6.dp)
                                         .heightIn(max = 200.dp)
                                         .verticalScroll(rememberScrollState())
                                 ) {
                                     androidx.compose.foundation.text.selection.SelectionContainer {
                                         Text(
                                             text = report.responseBody,
                                             fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                             color = Color(0xFF90CAF9),
                                             style = MaterialTheme.typography.bodySmall
                                         )
                                     }
                                 }
                             }
                        }
                    }
                }
            }
        }

        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cockpit Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "Gemini Logo",
                            tint = RangoDesertGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "GOOGLE GEMINI STRATEGY",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RangoTextWhite
                                )
                            )
                            Text(
                                "Streak Pattern Analyzer",
                                style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RangoDesertGold.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "GEMINI 2.5 FLASH",
                            color = RangoDesertGold,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = RangoTealSky)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RangoCardBg)
                        .padding(12.dp)
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = RangoLimeGreen)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Analyzing recent Rango patterns via Google Cloud...",
                                color = RangoTextWhite,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Har round ke breakdown ka jaiza liye ja raha hai...",
                                color = RangoDesertGold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else if (aiAdvice.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information",
                                tint = RangoTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "AI Advisor is ready to formulate strategies.",
                                color = RangoTextMuted,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ACTIVATE AI ADVISOR", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Display Gemini output in a scrollable, elegantly styled body
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = aiAdvice,
                                    color = RangoTextWhite,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // AI Key Warning
                Text(
                    text = "🔐 Note: AI models detect statistical ratios only and do not guarantee future outcome results. Practice self-discipline and bankroll control! (Zyada lalach se bachen!).",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RangoTextMuted,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Trigger analysis button
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(containerColor = RangoLimeGreen),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("button_refresh_ai_strategy")
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Psychology, contentDescription = "Brain", tint = Color.Black)
                Text("REFRESH DYNAMIC GEMINI MODEL", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * TAB 3: Strategy Simulator (Martingale calculator & systems tracker)
 */
@Composable
fun SimulatorTab() {
    var initialBetStr by remember { mutableStateOf("10") }
    var targetMultiplierStr by remember { mutableStateOf("2.0") }
    var safetySteps by remember { mutableStateOf(5) }

    val initialBet = initialBetStr.toDoubleOrNull() ?: 10.0
    val targetMultiplier = targetMultiplierStr.toDoubleOrNull() ?: 2.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Martingale explanation Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(RangoDesertGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingUp, "trending icon", tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            "MARTINGALE STEPS CALCULATOR",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = RangoTextWhite
                            )
                        )
                    }

                    HorizontalDivider(color = RangoHorizon)

                    Text(
                        "The Martingale recovery system multiplies your bet on sequential early flight losses model. Once the target multiplier is hit, past losses are instantly countered and a clean profit is recovered.",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                    )
                }
            }
        }

        // Configuration inputs
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "SIMULATION PARAMETERS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = RangoLimeGreen
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = initialBetStr,
                            onValueChange = { initialBetStr = it },
                            label = { Text("Base Bet") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = RangoTextWhite,
                                unfocusedTextColor = RangoTextWhite,
                                focusedContainerColor = RangoTealSky,
                                unfocusedContainerColor = RangoTealSky
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = targetMultiplierStr,
                            onValueChange = { targetMultiplierStr = it },
                            label = { Text("Target Cap") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = RangoTextWhite,
                                unfocusedTextColor = RangoTextWhite,
                                focusedContainerColor = RangoTealSky,
                                unfocusedContainerColor = RangoTealSky
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Stepper configuration for max length safety steps
                    Column {
                        Text(
                            "Survival Safety Steps (Sequential crashes: $safetySteps)",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextWhite),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { if (safetySteps > 3) safetySteps-- },
                                colors = ButtonDefaults.buttonColors(containerColor = RangoHorizon),
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RangoTextWhite)
                            }
                            Text(
                                "$safetySteps Steps",
                                color = RangoDesertGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Button(
                                onClick = { if (safetySteps < 8) safetySteps++ },
                                colors = ButtonDefaults.buttonColors(containerColor = RangoHorizon),
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RangoTextWhite)
                            }
                        }
                    }
                }
            }
        }

        // Table headers info
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("SEQUENCE LEVEL", style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted))
                Text("BET (PKR)", style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted))
                Text("ACC. COST (PKR)", style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted))
                Text("NET WIN (PKR)", style = MaterialTheme.typography.labelSmall.copy(color = RangoTextMuted))
            }
        }

        // Dynamic rows output
        var cumulativeCost = 0.0
        for (i in 1..safetySteps) {
            val stepMultiplier = if (targetMultiplier <= 1.0) 2.0 else targetMultiplier
            val multiplierFactor = stepMultiplier / (stepMultiplier - 1.0)
            val stepBet = if (i == 1) initialBet else initialBet * Math.pow(multiplierFactor, (i - 1).toDouble())
            
            val formattedStepBet = String.format("%.2f", stepBet)
            cumulativeCost += stepBet
            val profitOnWin = stepBet * stepMultiplier - cumulativeCost
            val formattedProfit = String.format("%.2f", profitOnWin)
            val formattedCumulative = String.format("%.2f", cumulativeCost)

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = RangoCardBg),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(RangoHorizon, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$i",
                                    color = RangoTextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Text("Round $i", color = RangoTextWhite, fontWeight = FontWeight.Bold)
                        }

                        Text(formattedStepBet, color = RangoTextWhite, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(formattedCumulative, color = RangoTextMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "+$formattedProfit",
                            color = RangoLimeGreen,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = RangoHorizon),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Tip", tint = RangoDesertGold)
                    Text(
                        text = "To stay safe, ensure your overall bankroll is at least equivalent to the Accumulated Cost of Round $safetySteps. Play wisely!",
                        style = MaterialTheme.typography.bodySmall.copy(color = RangoTextWhite)
                    )
                }
            }
        }
    }
}
