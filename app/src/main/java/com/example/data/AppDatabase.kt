package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CrashRound::class, DragonTigerRound::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun crashDao(): CrashDao
    abstract fun dragonTigerDao(): DragonTigerDao

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
