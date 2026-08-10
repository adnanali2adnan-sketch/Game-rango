package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.example.data.BaccaratRound
import com.example.data.BaccaratDao
import com.example.data.RouletteRound
import com.example.data.RouletteDao

@Database(entities = [CrashRound::class, DragonTigerRound::class, AndarBaharRound::class, SevenUpDownRound::class, BaccaratRound::class, RouletteRound::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun crashDao(): CrashDao
    abstract fun dragonTigerDao(): DragonTigerDao
    abstract fun andarBaharDao(): AndarBaharDao
    abstract fun sevenUpDownDao(): SevenUpDownDao
    abstract fun baccaratDao(): BaccaratDao
    abstract fun rouletteDao(): RouletteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS dragon_tiger_rounds (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        result TEXT NOT NULL,
                        betSide TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL,
                        sessionId TEXT NOT NULL DEFAULT ''
                    )
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rango_companion_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
