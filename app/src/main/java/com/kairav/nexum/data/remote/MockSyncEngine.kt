package com.kairav.nexum.data.remote

import android.util.Log
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MockSyncEngine simulates a remote server API for syncing data.
 * In a real app, this would use Retrofit or another networking library.
 */
@Singleton
class MockSyncEngine @Inject constructor() {

    private val TAG = "MockSyncEngine"

    /**
     * Simulates syncing a record to the server.
     * @param recordType "SMS" or "Call"
     * @param recordId The local ID of the record
     * @return Boolean indicating success or failure
     */
    suspend fun syncRecord(recordType: String, recordId: Long): Boolean {
        Log.d(TAG, "Syncing $recordType record ID: $recordId...")
        
        // Simulate network latency
        delay(1000)
        
        // Simulate success (90% success rate for realism)
        val isSuccess = (1..100).random() <= 90
        
        if (isSuccess) {
            Log.d(TAG, "Successfully synced $recordType record ID: $recordId")
        } else {
            Log.e(TAG, "Failed to sync $recordType record ID: $recordId")
        }
        
        return isSuccess
    }
}
