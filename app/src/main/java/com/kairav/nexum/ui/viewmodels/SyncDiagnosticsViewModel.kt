package com.kairav.nexum.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kairav.nexum.data.models.SyncStatus
import com.kairav.nexum.data.repositories.CallLogRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.workers.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import android.app.Application
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncCounts(
    val pendingSms: Int = 0,
    val syncedSms: Int = 0,
    val failedSms: Int = 0,
    val pendingCalls: Int = 0,
    val syncedCalls: Int = 0,
    val failedCalls: Int = 0
)

/**
 * ViewModel for the Sync Diagnostics screen.
 * Provides counts of records in different sync states and triggers manual sync.
 * 
 * Requirement 2: Fetching data from repositories.
 * State management logic: Combines multiple flows from repositories to create a unified status dashboard.
 */
@HiltViewModel
class SyncDiagnosticsViewModel @Inject constructor(
    private val application: Application,
    private val smsRepository: SmsRepository,
    private val callLogRepository: CallLogRepository
) : ViewModel() {

    /**
     * StateFlow providing aggregated counts of records by their sync status.
     * Uses [combine] to react to changes in either SMS or Call Log tables.
     */
    val syncCounts: StateFlow<SyncCounts> = combine(
        smsRepository.allSms,
        callLogRepository.allCalls
    ) { sms, calls ->
        // Calculate counts for each status locally from the observed lists
        SyncCounts(
            pendingSms = sms.count { it.syncStatus == SyncStatus.PENDING },
            syncedSms = sms.count { it.syncStatus == SyncStatus.SYNCED },
            failedSms = sms.count { it.syncStatus == SyncStatus.FAILED },
            pendingCalls = calls.count { it.syncStatus == SyncStatus.PENDING },
            syncedCalls = calls.count { it.syncStatus == SyncStatus.SYNCED },
            failedCalls = calls.count { it.syncStatus == SyncStatus.FAILED }
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncCounts()
    )

    /**
     * Manually triggers the sync process.
     * 1. Fetches latest data from system providers.
     * 2. Enqueues a WorkManager task to push PENDING data to the remote server.
     * 
     * Hilt/WorkManager Note: 
     * The SyncWorker is instantiated by Hilt's DelegatingWorkerFactory, which is configured in NexumApp.
     */
    fun syncNow() {
        viewModelScope.launch {
            // First fetch from system to ensure local DB reflects current state of the device
            try {
                smsRepository.fetchSmsFromSystem()
            } catch (e: Exception) {
                android.util.Log.e("SyncDiagnosticsVM", "Error fetching SMS: ${e.message}", e)
            }
            try {
                callLogRepository.fetchCallsFromSystem()
            } catch (e: Exception) {
                android.util.Log.e("SyncDiagnosticsVM", "Error fetching Calls: ${e.message}", e)
            }

            // Trigger WorkManager sync task
            try {
                val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(application).enqueue(syncRequest)
            } catch (e: Exception) {
                android.util.Log.e("SyncDiagnosticsVM", "Error scheduling WorkManager: ${e.message}", e)
            }
        }
    }
}
