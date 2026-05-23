package com.example.data

import kotlinx.coroutines.flow.Flow

class TransferRepository(private val dao: TransferHistoryDao) {
    val allTransfers: Flow<List<TransferHistory>> = dao.getAllTransfers()

    suspend fun insertTransfer(transfer: TransferHistory) {
         dao.insertTransfer(transfer)
    }

    suspend fun deleteTransferById(id: Int) {
         dao.deleteTransferById(id)
    }

    suspend fun clearAll() {
         dao.clearAllHistory()
    }
}
