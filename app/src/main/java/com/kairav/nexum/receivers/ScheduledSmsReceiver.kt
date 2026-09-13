package com.kairav.nexum.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairav.nexum.data.local.ScheduledSmsDao
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.ScheduleStatus
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.scheduler.SmsAlarmScheduler
import com.kairav.nexum.utils.NotificationUtils
import com.kairav.nexum.utils.TelegramMessageFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ScheduledSmsReceiver is invoked by AlarmManager when a scheduled message is due.
 */
@AndroidEntryPoint
class ScheduledSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduledSmsDao: ScheduledSmsDao

    @Inject
    lateinit var smsRepository: SmsRepository

    @Inject
    lateinit var contactsRepository: ContactsRepository

    @Inject
    lateinit var telegramConfigManager: TelegramConfigManager

    @Inject
    lateinit var telegramApiClient: TelegramApiClient

    @Inject
    lateinit var smsAlarmScheduler: SmsAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_SCHEDULED_SMS) return

        val scheduledId = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (scheduledId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val record = scheduledSmsDao.getScheduledSmsById(scheduledId)
                if (record == null || record.status != ScheduleStatus.PENDING) {
                    Log.d("ScheduledSmsReceiver", "Scheduled record $scheduledId not pending, skipping")
                    return@launch
                }

                Log.d("ScheduledSmsReceiver", "Executing scheduled SMS for ${record.recipientAddress}")

                // Send SMS through SmsRepository (saves to system provider & Room DB)
                val sentRecord = smsRepository.sendSms(
                    address = record.recipientAddress,
                    body = record.messageBody,
                    subId = record.subId
                )

                val displayName = record.recipientName 
                    ?: contactsRepository.getNameForNumber(record.recipientAddress) 
                    ?: record.recipientAddress

                // Telegram forwarding if enabled
                val tgConfig = telegramConfigManager.config.value
                if (tgConfig.isConfigured && tgConfig.isSmsForwardingEnabled) {
                    try {
                        val formattedMsg = TelegramMessageFormatter.formatSms(
                            sms = sentRecord,
                            contactName = displayName,
                            style = tgConfig.messageFormatStyle
                        )
                        telegramApiClient.sendMessage(
                            token = tgConfig.botToken,
                            chatId = tgConfig.chatId,
                            message = formattedMsg,
                            silent = tgConfig.silentNotifications
                        )
                    } catch (e: Exception) {
                        Log.w("ScheduledSmsReceiver", "Telegram forwarding failed: ${e.message}")
                    }
                }

                // Show notification
                NotificationUtils.showScheduledSmsSentNotification(
                    context = context,
                    recipient = displayName,
                    message = record.messageBody,
                    address = record.recipientAddress
                )

                // Handle repeat or complete
                val now = System.currentTimeMillis()
                if (record.repeatIntervalHours <= 0) {
                    scheduledSmsDao.updateStatus(record.id, ScheduleStatus.COMPLETED, now)
                } else {
                    val nextTime = smsAlarmScheduler.calculateNextTriggerTime(record.scheduledTimestamp, record.repeatIntervalHours)
                    scheduledSmsDao.reschedule(record.id, nextTime, now)
                    smsAlarmScheduler.schedule(record.copy(scheduledTimestamp = nextTime, lastExecutedAt = now))
                }
            } catch (e: Exception) {
                Log.e("ScheduledSmsReceiver", "Failed to send scheduled SMS $scheduledId: ${e.message}", e)
                try {
                    scheduledSmsDao.updateStatus(scheduledId, ScheduleStatus.FAILED, System.currentTimeMillis())
                } catch (dbEx: Exception) {}
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEND_SCHEDULED_SMS = "com.kairav.nexum.ACTION_SEND_SCHEDULED_SMS"
        const val EXTRA_SCHEDULED_ID = "EXTRA_SCHEDULED_ID"
    }
}
