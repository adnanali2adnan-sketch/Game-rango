package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "baccarat_rounds")
data class BaccaratRound(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val result: String,  // "P" (Player), "B" (Banker), "T" (Tie)
    val betSide: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val prediction: String = "",
    val predictionSource: String = "LOCAL",
    val isWin: Boolean? = null
)
