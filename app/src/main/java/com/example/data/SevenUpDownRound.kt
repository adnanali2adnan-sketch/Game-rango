package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seven_up_down_rounds")
data class SevenUpDownRound(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val result: String,  // "U" (Up/7 Up), "D" (Down/7 Down), or "7" (Seven)
    val betSide: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val prediction: String = "",
    val predictionSource: String = "LOCAL",
    val isWin: Boolean? = null
)
