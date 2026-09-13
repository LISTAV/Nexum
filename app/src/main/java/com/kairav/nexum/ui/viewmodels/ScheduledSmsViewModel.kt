package com.kairav.nexum.ui.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.local.ScheduledSmsDao
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.ContactRecord
import com.kairav.nexum.data.models.ScheduleStatus
import com.kairav.nexum.data.models.ScheduledSmsRecord
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.scheduler.SmsAlarmScheduler
import com.kairav.nexum.utils.NotificationUtils
import com.kairav.nexum.utils.TelegramMessageFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduledSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduledSmsDao: ScheduledSmsDao,
    private val contactsRepository: ContactsRepository,
    private val smsRepository: SmsRepository,
    private val smsAlarmScheduler: SmsAlarmScheduler,
    private val telegramConfigManager: TelegramConfigManager,
    private val telegramApiClient: TelegramApiClient
) : ViewModel() {

    val scheduledSmsList: StateFlow<List<ScheduledSmsRecord>> = scheduledSmsDao.getAllScheduledSms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allContacts: StateFlow<List<ContactRecord>> = flow {
        emit(contactsRepository.getAllContactsList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun scheduleMessage(
        recipientAddress: String,
        recipientName: String?,
        messageBody: String,
        scheduledTimestamp: Long,
        repeatIntervalHours: Int = 0,
        simSlot: Int = -1,
        subId: Int = -1,
        onSuccess: () -> Unit = {}
    ) {
        val address = recipientAddress.trim()
        val text = messageBody.trim()

        if (address.isBlank()) {
            Toast.makeText(context, "Please provide a valid recipient", Toast.LENGTH_SHORT).show()
            return
        }
        if (text.isBlank()) {
            Toast.makeText(context, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (scheduledTimestamp <= System.currentTimeMillis()) {
            Toast.makeText(context, "Scheduled time must be in the future", Toast.LENGTH_SHORT).show()
            return
        }
        if (repeatIntervalHours < 0 || repeatIntervalHours > 500) {
            Toast.makeText(context, "Repeat interval must be between 1 and 500 hours", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolvedName = recipientName ?: contactsRepository.getNameForNumber(address)
                val record = ScheduledSmsRecord(
                    recipientAddress = address,
                    recipientName = resolvedName,
                    messageBody = text,
                    scheduledTimestamp = scheduledTimestamp,
                    repeatIntervalHours = repeatIntervalHours,
                    simSlot = simSlot,
                    subId = subId,
                    status = ScheduleStatus.PENDING
                )
                val id = scheduledSmsDao.insertScheduledSms(record)
                val insertedRecord = record.copy(id = id)
                smsAlarmScheduler.schedule(insertedRecord)

                viewModelScope.launch(Dispatchers.Main) {
                    val repeatMsg = if (repeatIntervalHours > 0) " (Repeats every ${repeatIntervalHours}h)" else ""
                    Toast.makeText(context, "SMS scheduled$repeatMsg", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to schedule: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun cancelSchedule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            smsAlarmScheduler.cancel(id)
            scheduledSmsDao.updateStatus(id, ScheduleStatus.CANCELLED, null)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Schedule cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            smsAlarmScheduler.cancel(id)
            scheduledSmsDao.deleteScheduledSms(id)
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendNow(record: ScheduledSmsRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sentRecord = smsRepository.sendSms(
                    address = record.recipientAddress,
                    body = record.messageBody,
                    subId = record.subId
                )

                val displayName = record.recipientName 
                    ?: contactsRepository.getNameForNumber(record.recipientAddress) 
                    ?: record.recipientAddress

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
                        // ignore tg error
                    }
                }

                NotificationUtils.showScheduledSmsSentNotification(
                    context = context,
                    recipient = displayName,
                    message = record.messageBody,
                    address = record.recipientAddress
                )

                val now = System.currentTimeMillis()
                if (record.repeatIntervalHours <= 0) {
                    smsAlarmScheduler.cancel(record.id)
                    scheduledSmsDao.updateStatus(record.id, ScheduleStatus.COMPLETED, now)
                } else {
                    val nextTime = smsAlarmScheduler.calculateNextTriggerTime(record.scheduledTimestamp, record.repeatIntervalHours)
                    scheduledSmsDao.reschedule(record.id, nextTime, now)
                    smsAlarmScheduler.schedule(record.copy(scheduledTimestamp = nextTime, lastExecutedAt = now))
                }

                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "SMS sent to $displayName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun resolveContactFromUri(uri: android.net.Uri) = contactsRepository.getContactFromUri(uri)
}
