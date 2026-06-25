package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "andar_bahar_rounds")
data class AndarBaharRound(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val result: String,  // "A" (Andar) or "B" (Bahar)
    val betSide: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = ""
)
