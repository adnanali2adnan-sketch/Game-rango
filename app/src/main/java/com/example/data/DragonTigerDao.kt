package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DragonTigerDao {
    @Query("SELECT * FROM dragon_tiger_rounds ORDER BY timestamp DESC")
    fun getAllRounds(): Flow<List<DragonTigerRound>>
    
    @Query("SELECT * FROM dragon_tiger_rounds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRounds(limit: Int = 30): List<DragonTigerRound>
    
    @Insert
    suspend fun insertRound(round: DragonTigerRound)
    
    @Query("DELETE FROM dragon_tiger_rounds")
    suspend fun clearAll()

    @Query("DELETE FROM dragon_tiger_rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: Int)

    @Query("UPDATE dragon_tiger_rounds SET isWin = :isWin WHERE id = :roundId")
    suspend fun updateRoundStatus(roundId: Int, isWin: Boolean?)

    @Query("DELETE FROM dragon_tiger_rounds WHERE id = (SELECT id FROM dragon_tiger_rounds ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastRound()
}
