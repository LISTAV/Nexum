package com.kairav.nexum.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kairav.nexum.data.repositories.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * SyncWorker is a background job managed by WorkManager.
 * It uses Hilt to inject the SyncRepository and performs the sync logic.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "SyncWorker starting work...")
        Log.d("SyncWorker", "Starting sync background task...")
        
        /**
         * The doWork() method is executed on a background thread provided by WorkManager.
         * CoroutineWorker allows us to use suspend functions (like syncPendingData) directly.
         */
        return try {
            // Attempt to sync all pending records from the local outbox
            val success = syncRepository.syncPendingData()
            
            if (success) {
                Log.d("SyncWorker", "Sync completed successfully.")
                // Result.success() tells WorkManager the task is done and doesn't need retry.
                Result.success()
            } else {
                Log.w("SyncWorker", "Sync completed with some failures. Retrying later.")
                /**
                 * Result.retry() tells WorkManager to reschedule this task based on 
                 * its backoff policy (e.g., exponential backoff).
                 */
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Critical error during sync: ${e.message}", e)
            /**
             * Result.failure() indicates a permanent failure. 
             * For a sync engine, we might prefer Result.retry() unless the error is unrecoverable.
             */
            Result.failure()
        }
    }
}
