package com.kairav.nexum.data.repositories

import android.content.Context
import android.provider.Telephony
import com.kairav.nexum.data.local.SmsDao
import com.kairav.nexum.data.models.SmsRecord
import com.kairav.nexum.data.models.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.kairav.nexum.utils.SimUtils

/**
 * SmsRepository acts as a bridge between the data sources (ContentProvider and local Room DB)
 * and the rest of the application.
 *
 * For non-Kotlin developers:
 * - It abstracts the logic of where the data comes from.
 * - 'ContentResolver' is used to query data from other apps (like the system's SMS storage).
 */
@Singleton
class SmsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsDao: SmsDao
) {
    // Observable list of SMS from local database
    val allSms: Flow<List<SmsRecord>> = smsDao.getAllSms()

    // Observable list of Sent SMS from local database sorted latest to older
    val sentSms: Flow<List<SmsRecord>> = smsDao.getSentSms()

    /**
     * Returns a Flow of messages for a specific contact address.
     */
    fun getMessagesForAddress(address: String): Flow<List<SmsRecord>> {
        return smsDao.getSmsForAddress(address)
    }

    /**
     * Fetches SMS messages from the system's Telephony ContentProvider and saves them to local DB.
     */
    suspend fun fetchSmsFromSystem() = withContext(Dispatchers.IO) {
        try {
            val subMap = SimUtils.getSubIdToSimSlotMap(context)

            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.SUBSCRIPTION_ID,
                Telephony.Sms.READ
            )

            val cursor = try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
            } catch (e: Exception) {
                try {
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                        null,
                        null,
                        "${Telephony.Sms.DATE} DESC"
                    )
                } catch (e2: Exception) {
                    null
                }
            }

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val subIdIndex = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)

                val smsList = mutableListOf<SmsRecord>()
                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getLong(idIndex) else System.currentTimeMillis()
                    val address = if (addressIndex != -1) it.getString(addressIndex) ?: "Unknown" else "Unknown"
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                    val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                    val type = if (typeIndex != -1) it.getInt(typeIndex) else 1
                    val subId = if (subIdIndex != -1) it.getInt(subIdIndex) else -1
                    val simSlot = subMap[subId] ?: -1
                    val read = if (readIndex != -1) it.getInt(readIndex) else 1

                    smsList.add(SmsRecord(id, address, body, date, type, SyncStatus.SYNCED, subId, simSlot, read))
                }
                
                // Deduplicate by ID and insert
                val distinctList = smsList.distinctBy { it.id }
                if (distinctList.isNotEmpty()) {
                    smsDao.insertAllSms(distinctList)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsRepository", "Error fetching SMS from system: ${e.message}", e)
        }
    }

    /**
     * Marks all messages for a specific contact address as read in Room DB and system telephony provider.
     */
    suspend fun markAddressAsRead(address: String) = withContext(Dispatchers.IO) {
        try {
            smsDao.markAsReadByAddress(address)
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(address)
            )
        } catch (e: Exception) {
            // Ignored if permissions or non-default
        }
    }

    /**
     * Inserts a new SMS record into the local database.
     */
    suspend fun insertSms(sms: SmsRecord) = withContext(Dispatchers.IO) {
        smsDao.insertSms(sms)
    }

    /**
     * Sends an SMS and records it into both system provider and local Room database.
     */
    suspend fun sendSms(
        address: String,
        body: String,
        subId: Int = -1
    ): SmsRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val simSlot = if (subId >= 0) SimUtils.getSimSlotIndex(context, subId) else -1

        // 1. Send SMS via SmsManager
        val smsManager: android.telephony.SmsManager = if (subId >= 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(android.telephony.SmsManager::class.java).createForSubscriptionId(subId)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(android.telephony.SmsManager::class.java)
        } else if (subId >= 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getSmsManagerForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(body)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(address, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(address, null, body, null, null)
        }

        // 2. Save into system SMS provider (content://sms/sent)
        var systemSmsId: Long? = null
        try {
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, now)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                if (subId >= 0) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                }
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            systemSmsId = uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            android.util.Log.w("SmsRepository", "Could not write to system sent SMS provider: ${e.message}")
        }

        val recordId = systemSmsId ?: now
        val sentRecord = SmsRecord(
            id = recordId,
            address = address,
            body = body,
            date = now,
            type = 2, // Sent
            syncStatus = SyncStatus.PENDING,
            subId = subId,
            simSlot = simSlot
        )

        // 3. Insert into local Room database
        smsDao.insertSms(sentRecord)

        sentRecord
    }

    /**
     * Deletes specific messages by their IDs.
     */
    suspend fun deleteMessages(ids: List<Long>) = withContext(Dispatchers.IO) {
        smsDao.deleteSmsByIds(ids)
    }

    /**
     * Deletes all messages for a specific address (thread).
     */
    suspend fun deleteThread(address: String) = withContext(Dispatchers.IO) {
        smsDao.deleteSmsByAddress(address)
    }
}
