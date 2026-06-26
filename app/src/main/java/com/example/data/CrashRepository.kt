package com.example.data

import kotlinx.coroutines.flow.Flow

class CrashRepository(private val crashDao: CrashDao) {
    val allRounds: Flow<List<CrashRound>> = crashDao.getAllRounds()

    suspend fun insert(round: CrashRound) {
        crashDao.insertRound(round)
    }

    suspend fun clearAll() {
        crashDao.clearAll()
    }

    suspend fun deleteRound(id: Int) {
        crashDao.deleteRound(id)
    }

    suspend fun updateRoundStatus(id: Int, isWin: Boolean?) {
        crashDao.updateRoundStatus(id, isWin)
    }

    suspend fun getRecentLimit(limit: Int): List<CrashRound> {
        return crashDao.getRecentRounds(limit)
    }
}
