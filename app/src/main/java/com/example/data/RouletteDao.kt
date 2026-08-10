package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouletteDao {
    @Query("SELECT * FROM roulette_rounds ORDER BY timestamp DESC")
    fun getAllRounds(): Flow<List<RouletteRound>>
    
    @Query("SELECT * FROM roulette_rounds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRounds(limit: Int = 30): List<RouletteRound>
    
    @Insert
    suspend fun insertRound(round: RouletteRound)
    
    @Query("DELETE FROM roulette_rounds")
    suspend fun clearAll()

    @Query("DELETE FROM roulette_rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: Int)

    @Query("UPDATE roulette_rounds SET isWin = :isWin WHERE id = :roundId")
    suspend fun updateRoundStatus(roundId: Int, isWin: Boolean?)

    @Query("DELETE FROM roulette_rounds WHERE id = (SELECT id FROM roulette_rounds ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastRound()
}
