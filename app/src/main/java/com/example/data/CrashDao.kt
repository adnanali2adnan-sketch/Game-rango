package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CrashDao {
    @Query("SELECT * FROM crash_rounds ORDER BY timestamp DESC")
    fun getAllRounds(): Flow<List<CrashRound>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRound(round: CrashRound)

    @Query("DELETE FROM crash_rounds")
    suspend fun clearAll()

    @Query("SELECT * FROM crash_rounds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRounds(limit: Int): List<CrashRound>
}
