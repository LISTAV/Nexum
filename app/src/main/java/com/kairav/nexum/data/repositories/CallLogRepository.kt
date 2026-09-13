package com.kairav.nexum.data.repositories

import android.content.Context
import android.provider.CallLog
import com.kairav.nexum.data.local.CallDao
import com.kairav.nexum.data.models.CallRecord
import com.kairav.nexum.data.models.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.kairav.nexum.utils.SimUtils

/**
 * CallLogRepository manages call log data by syncing with the system's CallLog ContentProvider.
 */
@Singleton
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callDao: CallDao
) {
    // Observable list of calls from local database
    val allCalls: Flow<List<CallRecord>> = callDao.getAllCalls()

    /**
     * Fetches call logs from the system and saves them to the local database.
     */
    suspend fun fetchCallsFromSystem() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )

            val cursor = try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
            } catch (e: Exception) {
                try {
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE),
                        null,
                        null,
                        "${CallLog.Calls.DATE} DESC"
                    )
                } catch (e2: Exception) {
                    null
                }
            }

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val phoneAccountIdIndex = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                val callList = mutableListOf<CallRecord>()
                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getLong(idIndex) else System.currentTimeMillis()
                    val number = if (numberIndex != -1) it.getString(numberIndex) ?: "Unknown" else "Unknown"
                    val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                    val duration = if (durationIndex != -1) it.getLong(durationIndex) else 0L
                    val type = if (typeIndex != -1) it.getInt(typeIndex) else 1
                    val phoneAccountId = if (phoneAccountIdIndex != -1) it.getString(phoneAccountIdIndex) else null
                    val simSlot = SimUtils.getSimSlotFromPhoneAccount(context, phoneAccountId)

                    callList.add(CallRecord(id, number, date, duration, type, SyncStatus.SYNCED, -1, simSlot))
                }

                val distinctList = callList.distinctBy { it.id }
                if (distinctList.isNotEmpty()) {
                    callDao.insertAllCalls(distinctList)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CallLogRepository", "Error fetching calls from system: ${e.message}", e)
        }
    }
}
