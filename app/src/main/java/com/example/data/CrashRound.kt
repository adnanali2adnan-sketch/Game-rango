package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crash_rounds")
data class CrashRound(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val multiplier: Double, // The multiplier at which the plane crashed (e.g., 1.23)
    val betAmount: Double = 0.0, // Optional bet size placed by user
    val cashOutMultiplier: Double = 0.0, // Multiplier when cashed out, or 0.0 if plane flew away
    val profitLoss: Double = 0.0, // Profit earned (or negative on loss)
    val timestamp: Long = System.currentTimeMillis(),
    val prediction: String = "",
    val predictionSource: String = "LOCAL",
    val isWin: Boolean? = null
)
