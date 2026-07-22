package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IncidentReport::class, ScreenedCall::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun screenedCallDao(): ScreenedCallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trudial_database"
                )
                .fallbackToDestructiveMigration()
                // In a real application, we would use SQLCipher here for E2E encryption of the local DB:
                // .openHelperFactory(SupportFactory(SQLiteDatabase.getBytes("encryption_key".toCharArray())))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
