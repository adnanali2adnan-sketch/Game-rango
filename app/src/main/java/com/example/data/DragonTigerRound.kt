package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dragon_tiger_rounds")
data class DragonTigerRound(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val result: String,  // "D", "T", "TIE"
    val betSide: String = "",  // which side user bet on
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val prediction: String = "",
    val predictionSource: String = "LOCAL",
    val isWin: Boolean? = null
)
