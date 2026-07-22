package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentReport>>

    @Insert
    suspend fun insertIncident(incident: IncidentReport)

    @Query("UPDATE incidents SET isReported = 1 WHERE id = :id")
    suspend fun markAsReported(id: Int)
    
    @Query("SELECT COUNT(*) FROM incidents WHERE riskLevel IN ('High', 'Extreme')")
    fun getHighRiskCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM incidents WHERE callerId = :callerId AND riskLevel IN ('High', 'Extreme')")
    suspend fun getHighRiskCountForCaller(callerId: String): Int
}
