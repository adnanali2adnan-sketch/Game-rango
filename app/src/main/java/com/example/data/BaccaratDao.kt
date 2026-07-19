package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BaccaratDao {
    @Query("SELECT * FROM baccarat_rounds ORDER BY timestamp DESC")
    fun getAllRounds(): Flow<List<BaccaratRound>>
    
    @Query("SELECT * FROM baccarat_rounds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRounds(limit: Int = 30): List<BaccaratRound>
    
    @Insert
    suspend fun insertRound(round: BaccaratRound)
    
    @Query("DELETE FROM baccarat_rounds")
    suspend fun clearAll()

    @Query("DELETE FROM baccarat_rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: Int)

    @Query("UPDATE baccarat_rounds SET isWin = :isWin WHERE id = :roundId")
    suspend fun updateRoundStatus(roundId: Int, isWin: Boolean?)

    @Query("DELETE FROM baccarat_rounds WHERE id = (SELECT id FROM baccarat_rounds ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastRound()
}
