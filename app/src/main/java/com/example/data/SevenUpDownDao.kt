package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SevenUpDownDao {
    @Query("SELECT * FROM seven_up_down_rounds ORDER BY timestamp DESC")
    fun getAllRounds(): Flow<List<SevenUpDownRound>>
    
    @Query("SELECT * FROM seven_up_down_rounds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRounds(limit: Int = 30): List<SevenUpDownRound>
    
    @Insert
    suspend fun insertRound(round: SevenUpDownRound)
    
    @Query("DELETE FROM seven_up_down_rounds")
    suspend fun clearAll()

    @Query("DELETE FROM seven_up_down_rounds WHERE id = (SELECT id FROM seven_up_down_rounds ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastRound()
}
