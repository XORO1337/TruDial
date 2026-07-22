package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenedCallDao {
    @Insert
    suspend fun insertCall(call: ScreenedCall)

    @Query("SELECT * FROM screened_calls ORDER BY timestamp DESC")
    fun getAllCalls(): Flow<List<ScreenedCall>>
}
