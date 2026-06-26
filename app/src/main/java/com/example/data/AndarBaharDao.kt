package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AndarBaharDao {
    @Query("SELECT * FROM andar_bahar_rounds ORDER BY timestamp DESC")
    fun getAllRounds(): Flow<List<AndarBaharRound>>
    
    @Query("SELECT * FROM andar_bahar_rounds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRounds(limit: Int = 30): List<AndarBaharRound>
    
    @Insert
    suspend fun insertRound(round: AndarBaharRound)
    
    @Query("DELETE FROM andar_bahar_rounds")
    suspend fun clearAll()

    @Query("DELETE FROM andar_bahar_rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: Int)

    @Query("UPDATE andar_bahar_rounds SET isWin = :isWin WHERE id = :roundId")
    suspend fun updateRoundStatus(roundId: Int, isWin: Boolean?)

    @Query("DELETE FROM andar_bahar_rounds WHERE id = (SELECT id FROM andar_bahar_rounds ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastRound()
}
