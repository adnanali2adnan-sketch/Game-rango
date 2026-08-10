package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roulette_rounds")
data class RouletteRound(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val result: String, // "RED", "BLACK", "0", or number "1".."36"
    val number: Int? = null,
    val color: String = "", // "RED", "BLACK", "GREEN"
    val betSide: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val prediction: String = "",
    val predictionSource: String = "LOCAL",
    val isWin: Boolean? = null
)
