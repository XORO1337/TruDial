package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screened_calls")
data class ScreenedCall(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val callerId: String,
    val timestamp: Long,
    val isFlagged: Boolean,
    val description: String = ""
)
