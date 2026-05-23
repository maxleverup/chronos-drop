package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferHistory)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteTransferById(id: Int)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAllHistory()
}
