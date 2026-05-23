package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileSize: Long,
    val code: String,
    val spaceId: String,
    val type: String, // "SEND" or "RECEIVE"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // "Pending", "Completed", "Failed"
)
