package com.example.service

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*

// Colors
val RangoDangerRed = Color(0xFFD32F2F)
val RangoDesertGold = Color(0xFFFFB300)
val RangoLimeGreen = Color(0xFF00E676)
val RangoTextWhite = Color(0xFFFFFFFF)
val RangoTextMuted = Color(0xFFB0BEC5)
val RangoTealSky = Color(0xFF00B0FF)

@Composable
fun BoxMetricItem(
    title: String,
    value: String,
    bgColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    titleSize: androidx.compose.ui.unit.TextUnit = 6.sp,
    valueSize: androidx.compose.ui.unit.TextUnit = 8.5.sp
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(3.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = titleSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = valueSize,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BaccaratHudView(
    recentBaccaratList: List<BaccaratRound>,
    isLandscape: Boolean,
    onAddResult: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val bacResult = BaccaratAnalyzer.analyze(recentBaccaratList)
    
    // Trend status banner
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 1.dp, horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "${bacResult.trendEmoji} ${bacResult.trendLabel}",
            color = when (bacResult.riskLevel) {
                "HIGH RISK" -> RangoDangerRed
                "MED RISK" -> RangoDesertGold
                else -> RangoLimeGreen
            },
            fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Black
        )
    }
    
    // Prediction Grid
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "NEXT BET",
                value = bacResult.suggestedBet,
                bgColor = when {
                    bacResult.suggestedBet.contains("PLAYER") -> Color(0xFF1565C0)
                    bacResult.suggestedBet.contains("BANKER") -> Color(0xFFC62828)
                    else -> Color(0xFF37474F)
                },
                valueColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
            BoxMetricItem(
                title = "STREAK",
                value = bacResult.currentStreak,
                bgColor = Color(0xFF0D47A1),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "PLAYER%",
                value = "${bacResult.playerPct}%",
                bgColor = Color(0xFF0D47A1),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "BANKER%",
                value = "${bacResult.bankerPct}%",
                bgColor = Color(0xFFB71C1C),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "TIE%",
                value = "${bacResult.tiePct}%",
                bgColor = Color(0xFF4A148C),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Derived roads
    Column(
        verticalArrangement = Arrangement.spacedBy(0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 1.5.dp, horizontal = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BIG EYE BOY:", color = RangoTextMuted, fontSize = if (isLandscape) 5.5.sp else 7.sp, fontWeight = FontWeight.Bold)
            Text(
                text = bacResult.bigEyeBoySignal,
                color = if (bacResult.bigEyeBoySignal == "RED") RangoDangerRed else if (bacResult.bigEyeBoySignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SMALL ROAD:", color = RangoTextMuted, fontSize = if (isLandscape) 5.5.sp else 7.sp, fontWeight = FontWeight.Bold)
            Text(
                text = bacResult.smallRoadSignal,
                color = if (bacResult.smallRoadSignal == "RED") RangoDangerRed else if (bacResult.smallRoadSignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COCKROACH:", color = RangoTextMuted, fontSize = if (isLandscape) 5.5.sp else 7.sp, fontWeight = FontWeight.Bold)
            Text(
                text = bacResult.cockroachRoadSignal,
                color = if (bacResult.cockroachRoadSignal == "RED") RangoDangerRed else if (bacResult.cockroachRoadSignal == "BLUE") Color(0xFF1E88E5) else Color.Gray,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Last rounds
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST ROUNDS:",
                color = RangoTextMuted,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (recentBaccaratList.isEmpty()) {
                Text(
                    text = "None",
                    color = Color.Gray,
                    fontSize = if (isLandscape) 6.sp else 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentBaccaratList.take(8).forEach { round ->
                        val (letter, color) = when (round.result) {
                            "P" -> "P" to Color(0xFF1E88E5)
                            "B" -> "B" to RangoDangerRed
                            "T" -> "T" to Color(0xFF8E24AA)
                            else -> round.result to Color.White
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 9.dp else 11.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                color = Color.White,
                                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))
    
    Text(
        text = "QUICK ENTRY RESULT",
        color = RangoTextMuted,
        fontSize = if (isLandscape) 6.sp else 7.5.sp,
        fontWeight = FontWeight.Bold
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1E88E5))
                .clickable { onAddResult("P") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔵 PLAYER",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Box(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF8E24AA))
                .clickable { onAddResult("T") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TIE",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoDangerRed)
                .clickable { onAddResult("B") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔴 BANKER",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
                .clickable { onUndo() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶ UNDO",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFC62828))
                .clickable { onReset() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑️ RESET",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RouletteHudView(
    recentRouletteList: List<RouletteRound>,
    isLandscape: Boolean,
    onAddResult: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val roulResult = RouletteAnalyzer.analyze(recentRouletteList)
    
    // Trend status banner
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 1.dp, horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "${roulResult.trendEmoji} ${roulResult.trendLabel}",
            color = when (roulResult.riskLevel) {
                "HIGH RISK" -> RangoDangerRed
                "MED RISK" -> RangoDesertGold
                else -> RangoLimeGreen
            },
            fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Black
        )
    }

    // Prediction Grid
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "NEXT BET",
                value = roulResult.suggestedBet,
                bgColor = when {
                    roulResult.suggestedBet.contains("RED") -> RangoDangerRed
                    roulResult.suggestedBet.contains("BLACK") -> Color.Black
                    roulResult.suggestedBet.contains("EVEN") || roulResult.suggestedBet.contains("ODD") -> Color(0xFF1565C0)
                    else -> Color(0xFF37474F)
                },
                valueColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
            BoxMetricItem(
                title = "STREAK",
                value = roulResult.currentStreak,
                bgColor = Color(0xFF0D47A1),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "RED%",
                value = "${roulResult.redPct}%",
                bgColor = RangoDangerRed,
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "BLACK%",
                value = "${roulResult.blackPct}%",
                bgColor = Color.Black,
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "EVEN%",
                value = "${roulResult.evenPct}%",
                bgColor = Color(0xFF1565C0),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Last spins
    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST SPINS:",
                color = RangoTextMuted,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (recentRouletteList.isEmpty()) {
                Text(
                    text = "None",
                    color = Color.Gray,
                    fontSize = if (isLandscape) 6.sp else 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentRouletteList.take(7).forEach { round ->
                        val (lbl, bg) = when (round.color) {
                            "RED" -> (round.number?.toString() ?: "R") to RangoDangerRed
                            "BLACK" -> (round.number?.toString() ?: "B") to Color.Black
                            else -> (round.number?.toString() ?: "0") to Color(0xFF2E7D32)
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 10.dp else 12.dp)
                                .clip(CircleShape)
                                .background(bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl,
                                color = Color.White,
                                fontSize = if (isLandscape) 5.sp else 6.5.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))

    Text(
        text = "QUICK SPINS ENTRY",
        color = RangoTextMuted,
        fontSize = if (isLandscape) 6.sp else 7.5.sp,
        fontWeight = FontWeight.Bold
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoDangerRed)
                .clickable { onAddResult("RED") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔴 RED",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2E7D32))
                .clickable { onAddResult("0") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🟢 0",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .clickable { onAddResult("BLACK") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚫ BLACK",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
                .clickable { onUndo() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶ UNDO",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFC62828))
                .clickable { onReset() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑️ RESET",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DragonTigerHudView(
    recentDtList: List<DragonTigerRound>,
    isLandscape: Boolean,
    isScanning: Boolean,
    onAddResult: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onToggleOcr: () -> Unit
) {
    val dtResult = DragonTigerAnalyzer.analyze(recentDtList)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 1.dp, horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "${dtResult.trendEmoji} ${dtResult.trendLabel}",
            color = when (dtResult.riskLevel) {
                "HIGH RISK" -> RangoDangerRed
                "MED RISK" -> RangoDesertGold
                else -> RangoLimeGreen
            },
            fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Black
        )
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "NEXT BET",
                value = dtResult.suggestedBet,
                bgColor = when {
                    dtResult.suggestedBet.contains("DRAGON") -> Color(0xFF1B5E20)
                    dtResult.suggestedBet.contains("TIGER") -> Color(0xFFBF360C)
                    else -> Color(0xFF37474F)
                },
                valueColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
            BoxMetricItem(
                title = "STREAK",
                value = dtResult.currentStreak,
                bgColor = Color(0xFF1565C0),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "DRAGON%",
                value = "${dtResult.dragonPct}%",
                bgColor = Color(0xFF2E7D32),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "TIGER%",
                value = "${dtResult.tigerPct}%",
                bgColor = Color(0xFFC62828),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "TIE%",
                value = "${dtResult.tiePct}%",
                bgColor = Color(0xFF6A1B9A),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 1.5.dp, horizontal = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NEXT SIDE:", color = RangoTextMuted, fontSize = if (isLandscape) 6.sp else 7.5.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (dtResult.predictedNext == "UNCERTAIN") "STANDBY" else dtResult.predictedNext,
                color = if (dtResult.predictedNext == "DRAGON") Color(0xFF1E88E5) else if (dtResult.predictedNext == "TIGER") RangoDangerRed else Color.Gray,
                fontSize = if (isLandscape) 6.5.sp else 8.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST ROUNDS:",
                color = RangoTextMuted,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (recentDtList.isEmpty()) {
                Text(
                    text = "None",
                    color = Color.Gray,
                    fontSize = if (isLandscape) 6.sp else 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentDtList.take(8).forEach { round ->
                        val (letter, color) = when (round.result) {
                            "D" -> "D" to Color(0xFF1E88E5)
                            "T" -> "T" to RangoDangerRed
                            "X", "TIE", "P" -> "P" to Color(0xFF8E24AA)
                            else -> round.result to Color.White
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 9.dp else 11.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                color = Color.White,
                                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))
    
    Text(
        text = "QUICK ENTRY RESULT",
        color = RangoTextMuted,
        fontSize = if (isLandscape) 6.sp else 7.5.sp,
        fontWeight = FontWeight.Bold
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoLimeGreen)
                .clickable { onAddResult("D") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🐉 DRAGON",
                color = Color.Black,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Box(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF8E24AA))
                .clickable { onAddResult("X") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "TIE",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoDangerRed)
                .clickable { onAddResult("T") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🐯 TIGER",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
                .clickable { onUndo() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶ UNDO",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFC62828))
                .clickable { onReset() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑️ RESET",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isScanning) RangoDangerRed else RangoLimeGreen)
            .clickable { onToggleOcr() }
            .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isScanning) "STOP OCR" else "AUTO OCR",
            color = Color.Black,
            fontSize = if (isLandscape) 6.sp else 7.5.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun AndarBaharHudView(
    recentAbList: List<AndarBaharRound>,
    isLandscape: Boolean,
    onAddResult: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val abResult = AndarBaharAnalyzer.analyze(recentAbList)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 1.dp, horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "${abResult.trendEmoji} ${abResult.trendLabel}",
            color = when (abResult.riskLevel) {
                "HIGH RISK" -> RangoDangerRed
                "MED RISK" -> RangoDesertGold
                else -> RangoLimeGreen
            },
            fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Black
        )
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "NEXT BET",
                value = abResult.suggestedBet,
                bgColor = when {
                    abResult.suggestedBet.contains("ANDAR") -> Color(0xFF00796B)
                    abResult.suggestedBet.contains("BAHAR") -> Color(0xFFD84315)
                    else -> Color(0xFF37474F)
                },
                valueColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
            BoxMetricItem(
                title = "STREAK",
                value = abResult.currentStreak,
                bgColor = Color(0xFF0D47A1),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "ANDAR%",
                value = "${abResult.andarPct}%",
                bgColor = Color(0xFF004D40),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "BAHAR%",
                value = "${abResult.baharPct}%",
                bgColor = Color(0xFFBF360C),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 1.5.dp, horizontal = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PREDICTED:", color = RangoTextMuted, fontSize = if (isLandscape) 6.sp else 7.5.sp, fontWeight = FontWeight.Bold)
            Text(
                text = abResult.predictedNext,
                color = if (abResult.predictedNext == "ANDAR") Color(0xFF4DB6AC) else if (abResult.predictedNext == "BAHAR") Color(0xFFFF8A65) else Color.Gray,
                fontSize = if (isLandscape) 6.5.sp else 8.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST ROUNDS:",
                color = RangoTextMuted,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (recentAbList.isEmpty()) {
                Text(
                    text = "None",
                    color = Color.Gray,
                    fontSize = if (isLandscape) 6.sp else 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentAbList.take(8).forEach { round ->
                        val (letter, color) = when (round.result) {
                            "A" -> "A" to Color(0xFF00796B)
                            "B" -> "B" to Color(0xFFD84315)
                            else -> round.result to Color.White
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 9.dp else 11.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                color = Color.White,
                                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))
    
    Text(
        text = "QUICK ENTRY RESULT",
        color = RangoTextMuted,
        fontSize = if (isLandscape) 6.sp else 7.5.sp,
        fontWeight = FontWeight.Bold
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoTealSky)
                .clickable { onAddResult("A") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🚪 ANDAR",
                color = Color.Black,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoDangerRed)
                .clickable { onAddResult("B") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🌌 BAHAR",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
                .clickable { onUndo() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶ UNDO",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFC62828))
                .clickable { onReset() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑️ RESET",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SevenUpDownHudView(
    recentSevenList: List<SevenUpDownRound>,
    isLandscape: Boolean,
    onAddResult: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    val sevenResult = SevenUpDownAnalyzer.analyze(recentSevenList)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 1.dp, horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "${sevenResult.trendEmoji} ${sevenResult.trendLabel}",
            color = when (sevenResult.riskLevel) {
                "HIGH RISK" -> RangoDangerRed
                "MED RISK" -> RangoDesertGold
                else -> RangoLimeGreen
            },
            fontSize = if (isLandscape) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Black
        )
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "NEXT BET",
                value = sevenResult.suggestedBet,
                bgColor = when {
                    sevenResult.suggestedBet.contains("UP") -> Color(0xFF2E7D32)
                    sevenResult.suggestedBet.contains("DOWN") -> Color(0xFFC62828)
                    sevenResult.suggestedBet.contains("SEVEN") -> Color(0xFFEF6C00)
                    else -> Color(0xFF37474F)
                },
                valueColor = Color.White,
                modifier = Modifier.weight(1.3f)
            )
            BoxMetricItem(
                title = "STREAK",
                value = sevenResult.currentStreak,
                bgColor = Color(0xFF283593),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            BoxMetricItem(
                title = "UP %",
                value = "${sevenResult.upPct}%",
                bgColor = Color(0xFF1B5E20),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "DOWN %",
                value = "${sevenResult.downPct}%",
                bgColor = Color(0xFFB71C1C),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            BoxMetricItem(
                title = "7 %",
                value = "${sevenResult.sevenPct}%",
                bgColor = Color(0xFFE65100),
                valueColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = 1.5.dp, horizontal = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PREDICTED:", color = RangoTextMuted, fontSize = if (isLandscape) 6.sp else 7.5.sp, fontWeight = FontWeight.Bold)
            Text(
                text = sevenResult.predictedNext,
                color = if (sevenResult.predictedNext == "UP") Color(0xFF81C784) else if (sevenResult.predictedNext == "DOWN") Color(0xFFE57373) else if (sevenResult.predictedNext == "7" || sevenResult.predictedNext == "SEVEN") Color(0xFFFFB74D) else Color.Gray,
                fontSize = if (isLandscape) 6.5.sp else 8.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAST ROUNDS:",
                color = RangoTextMuted,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (recentSevenList.isEmpty()) {
                Text(
                    text = "None",
                    color = Color.Gray,
                    fontSize = if (isLandscape) 6.sp else 7.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentSevenList.take(8).forEach { round ->
                        val (letter, color) = when (round.result) {
                            "U" -> "U" to Color(0xFF2E7D32)
                            "D" -> "D" to Color(0xFFC62828)
                            "7" -> "7" to Color(0xFFEF6C00)
                            else -> round.result to Color.White
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 9.dp else 11.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                color = Color.White,
                                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(color = RangoTealSky.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 1.6.dp))
    
    Text(
        text = "QUICK ENTRY RESULT",
        color = RangoTextMuted,
        fontSize = if (isLandscape) 6.sp else 7.5.sp,
        fontWeight = FontWeight.Bold
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoLimeGreen)
                .clickable { onAddResult("U") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📈 7 UP",
                color = Color.Black,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
        
        Box(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFEF6C00))
                .clickable { onAddResult("7") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "7",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(4.dp))
                .background(RangoDangerRed)
                .clickable { onAddResult("D") }
                .padding(vertical = if (isLandscape) 4.5.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📉 7 DN",
                color = Color.White,
                fontSize = if (isLandscape) 6.sp else 7.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.DarkGray)
                .clickable { onUndo() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "↶ UNDO",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFC62828))
                .clickable { onReset() }
                .padding(vertical = if (isLandscape) 4.dp else 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🗑️ RESET",
                color = Color.White,
                fontSize = if (isLandscape) 5.5.sp else 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
