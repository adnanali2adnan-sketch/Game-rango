package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CrashRound
import com.example.data.DragonTigerRound
import com.example.data.DragonTigerAnalyzer
import com.example.MainActivity
import com.example.ui.theme.*
import com.example.viewmodel.CompanionViewModel
import com.example.viewmodel.LocalMetrics
import java.text.DecimalFormat

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

    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    LaunchedEffect(currentGame) {
        when (currentGame) {
            "DRAGON_TIGER" -> {
                mainActivity?.updateHudMode("VERTICAL", "DRAGON_TIGER")
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
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val gameList = listOf(
                    Triple("RANGO", "🎮 RANGO", RangoLimeGreen),
                    Triple("DRAGON_TIGER", "🐉 DRAGON TIGER", RangoDangerRed),
                    Triple("AVIATOR", "✈️ AVIATOR", Color(0xFF1976D2))
                )
                gameList.forEach { (type, label, labelColor) ->
                    val isSelected = currentGame == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) labelColor else Color.Black.copy(alpha = 0.5f)
                            )
                            .clickable {
                                viewModel.setCurrentGame(type)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else RangoTextMuted,
                            fontSize = 8.5.sp,
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
                        onDTClear = { viewModel.clearDTRounds() }
                    )
                    1 -> {
                        val apiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
                        StrategicAiTab(
                            aiAdvice = aiAdvice,
                            isLoading = isLoadingAdvice,
                            apiKey = apiKey,
                            onApiKeyChange = { viewModel.setGeminiApiKey(it) },
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
    onDTClear: () -> Unit
) {
    if (currentGame == "DRAGON_TIGER") {
        DragonTigerDashboardContent(
            dtRounds = dtRounds,
            dtResult = dtResult,
            onDTRoundLogged = onDTRoundLogged,
            onDTClear = onDTClear
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
            onBalanceChange = onBalanceChange
        )
    }
}

@Composable
fun DragonTigerDashboardContent(
    dtRounds: List<DragonTigerRound>,
    dtResult: DragonTigerAnalyzer.DTResult,
    onDTRoundLogged: (String) -> Unit,
    onDTClear: () -> Unit
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
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(dtRounds) { round ->
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
                        val txtColor = if (round.result == "D") Color.Black else Color.White
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                color = txtColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
    onBalanceChange: (String) -> Unit
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
                HistoryRowItem(round = round)
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
fun HistoryRowItem(round: CrashRound) {
    Card(
        colors = CardDefaults.cardColors(containerColor = RangoCardBg),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color Code bubble indicator
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

                    if (round.betAmount > 0.0) {
                        Text(
                            "Bet: PKR ${round.betAmount} | Target Cap: ${round.cashOutMultiplier}x",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    } else {
                        Text(
                            "Logged flight metric tracker",
                            style = MaterialTheme.typography.bodySmall.copy(color = RangoTextMuted)
                        )
                    }
                }
            }

            // Profit indicator
            if (round.betAmount > 0.0) {
                val isProfit = round.profitLoss >= 0.0
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isProfit) "+" else "") + String.format("%.2f", round.profitLoss),
                        color = if (isProfit) RangoLimeGreen else RangoDangerRed,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isProfit) "Success" else "Crashout",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isProfit) RangoLimeGreen else RangoTextMuted
                    )
                }
            } else {
                Text(
                    "Tracker Only",
                    color = RangoTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
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
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Secure API Key Input Card
        var showPassword by remember { mutableStateOf(false) }
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
                    value = apiKey,
                    onValueChange = onApiKeyChange,
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
                            "GEMINI 3.5 FLASH",
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
