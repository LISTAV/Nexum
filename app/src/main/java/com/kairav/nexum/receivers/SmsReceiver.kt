package com.kairav.nexum.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kairav.nexum.data.local.SmsDao
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.SmsRecord
import com.kairav.nexum.data.models.SyncStatus
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.utils.NotificationUtils
import com.kairav.nexum.utils.TelegramMessageFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SmsReceiver handles incoming SMS messages.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsDao: SmsDao

    @Inject
    lateinit var contactsRepository: ContactsRepository
    
    @Inject
    lateinit var telegramConfigManager: TelegramConfigManager
    
    @Inject
    lateinit var telegramApiClient: TelegramApiClient

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val subId = intent.getIntExtra("subscription", intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1))
            val simSlot = com.kairav.nexum.utils.SimUtils.getSimSlotIndex(context, subId)

            val senderAddress = messages[0].originatingAddress ?: "Unknown"
            val messageBody = messages.joinToString("") { it.messageBody ?: "" }
            val timestamp = messages[0].timestampMillis

            // 1. Write to Android's System Telephony SMS Inbox Provider
            var systemId: Long? = null
            try {
                val values = android.content.ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, senderAddress)
                    put(Telephony.Sms.BODY, messageBody)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.DATE_SENT, timestamp)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    if (subId >= 0) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                    }
                }
                val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                systemId = uri?.lastPathSegment?.toLongOrNull()
            } catch (e: Exception) {
                android.util.Log.e("SmsReceiver", "Failed to write incoming SMS to system provider: ${e.message}", e)
            }

            val recordId = systemId ?: System.currentTimeMillis()

            val smsRecord = SmsRecord(
                id = recordId,
                address = senderAddress,
                body = messageBody,
                date = timestamp,
                type = 1, // Inbox
                syncStatus = SyncStatus.PENDING,
                subId = subId,
                simSlot = simSlot,
                read = 0
            )
            
            // 2. Save to database, notify user, and forward to Telegram
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    smsDao.insertSms(smsRecord)
                    
                    val senderName = contactsRepository.getNameForNumber(senderAddress) ?: senderAddress
                    
                    NotificationUtils.showSmsNotification(
                        context,
                        senderName,
                        messageBody,
                        senderAddress
                    )
                    
                    // Telegram Forwarding
                    val tgConfig = telegramConfigManager.config.value
                    if (tgConfig.isConfigured && tgConfig.isSmsForwardingEnabled) {
                        val formattedMsg = TelegramMessageFormatter.formatSms(
                            sms = smsRecord,
                            contactName = senderName,
                            style = tgConfig.messageFormatStyle
                        )
                        val sent = telegramApiClient.sendMessage(
                            token = tgConfig.botToken,
                            chatId = tgConfig.chatId,
                            message = formattedMsg,
                            silent = tgConfig.silentNotifications
                        )
                        if (sent) {
                            smsDao.updateSyncStatus(smsRecord.id, SyncStatus.SYNCED)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SmsReceiver", "Error processing incoming SMS: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
