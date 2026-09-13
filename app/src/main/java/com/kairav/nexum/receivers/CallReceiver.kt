package com.kairav.nexum.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.kairav.nexum.data.local.CallDao
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.CallRecord
import com.kairav.nexum.data.models.SyncStatus
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.utils.TelegramMessageFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CallReceiver listens for phone state changes (incoming/outgoing calls) and forwards to Telegram if configured.
 */
@AndroidEntryPoint
class CallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var callDao: CallDao
    
    @Inject
    lateinit var contactsRepository: ContactsRepository
    
    @Inject
    lateinit var telegramConfigManager: TelegramConfigManager
    
    @Inject
    lateinit var telegramApiClient: TelegramApiClient

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            Log.d("CallReceiver", "Phone state changed: $state, number: $number")

            if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                CoroutineScope(Dispatchers.IO).launch {
                    // Give Android OS ~600ms to finish committing the call to CallLog.Calls
                    kotlinx.coroutines.delay(600)
                    try {
                        val cursor = context.contentResolver.query(
                            android.provider.CallLog.Calls.CONTENT_URI,
                            arrayOf(
                                android.provider.CallLog.Calls._ID,
                                android.provider.CallLog.Calls.NUMBER,
                                android.provider.CallLog.Calls.DATE,
                                android.provider.CallLog.Calls.DURATION,
                                android.provider.CallLog.Calls.TYPE,
                                android.provider.CallLog.Calls.PHONE_ACCOUNT_ID
                            ),
                            null,
                            null,
                            "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
                        )

                        cursor?.use {
                            if (it.moveToFirst()) {
                                val id = it.getLong(it.getColumnIndexOrThrow(android.provider.CallLog.Calls._ID))
                                val num = it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER)) ?: number ?: "Unknown"
                                val date = it.getLong(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.DATE))
                                val duration = it.getLong(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.DURATION))
                                val type = it.getInt(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.TYPE))

                                val phoneAccountIdIdx = it.getColumnIndex(android.provider.CallLog.Calls.PHONE_ACCOUNT_ID)
                                val phoneAccountId = if (phoneAccountIdIdx != -1) it.getString(phoneAccountIdIdx) else null
                                val simSlot = com.kairav.nexum.utils.SimUtils.getSimSlotFromPhoneAccount(context, phoneAccountId)

                                val callRecord = CallRecord(
                                    id = id,
                                    number = num,
                                    date = date,
                                    duration = duration,
                                    type = type,
                                    syncStatus = SyncStatus.PENDING,
                                    simSlot = simSlot
                                )

                                Log.d("CallReceiver", "Persisting call record ID=$id, num=$num, dur=${duration}s, simSlot=$simSlot")
                                callDao.insertCall(callRecord)

                                // Telegram Forwarding
                                val tgConfig = telegramConfigManager.config.value
                                if (tgConfig.isConfigured && tgConfig.isCallForwardingEnabled) {
                                    val contactName = contactsRepository.getNameForNumber(num)
                                    val formattedMsg = TelegramMessageFormatter.formatCall(
                                        call = callRecord,
                                        contactName = contactName,
                                        style = tgConfig.messageFormatStyle
                                    )
                                    val sent = telegramApiClient.sendMessage(
                                        token = tgConfig.botToken,
                                        chatId = tgConfig.chatId,
                                        message = formattedMsg,
                                        silent = tgConfig.silentNotifications
                                    )
                                    if (sent) {
                                        callDao.updateSyncStatus(callRecord.id, SyncStatus.SYNCED)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CallReceiver", "Error retrieving real call log after call ended: ${e.message}", e)
                    }
                }
            }
        }
    }
}
