package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incidents")
data class IncidentReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val callerId: String,
    val riskLevel: String, // "Low", "High", "Extreme"
    val transcriptSummary: String,
    val isReported: Boolean = false
)
