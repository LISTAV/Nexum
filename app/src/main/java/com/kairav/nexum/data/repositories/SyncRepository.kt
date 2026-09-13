package com.kairav.nexum.data.repositories

/**
 * SyncRepository defines the operations for synchronizing local data with the remote server.
 */
interface SyncRepository {
    /**
     * Fetches all pending records (SMS and Calls) and attempts to sync them.
     */
    suspend fun syncPendingData(): Boolean

    /**
     * Packages all local SMS and Call records into a single formatted document
     * and sends it to the user's Telegram chat as an attached file.
     */
    suspend fun exportHistoryToTelegram(): Boolean
}
