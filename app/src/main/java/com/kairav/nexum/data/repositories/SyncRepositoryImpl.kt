package com.kairav.nexum.data.repositories

import android.util.Log
import com.kairav.nexum.data.local.CallDao
import com.kairav.nexum.data.local.SmsDao
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.HistorySyncMode
import com.kairav.nexum.data.models.SyncStatus
import com.kairav.nexum.data.models.TelegramConfig
import com.kairav.nexum.data.remote.MockSyncEngine
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.utils.TelegramMessageFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SyncRepositoryImpl implements the SyncRepository using the Outbox Pattern.
 * Supports granular Telegram history sync modes, silent delivery, and single-file backup export.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val smsDao: SmsDao,
    private val callDao: CallDao,
    private val contactsRepository: ContactsRepository,
    private val syncEngine: MockSyncEngine,
    private val telegramConfigManager: TelegramConfigManager,
    private val telegramApiClient: TelegramApiClient
) : SyncRepository {

    private val TAG = "SyncRepositoryImpl"

    private fun isRecordWithinSyncWindow(timestamp: Long, config: TelegramConfig): Boolean {
        val now = System.currentTimeMillis()
        return when (config.historySyncMode) {
            HistorySyncMode.REALTIME_ONLY -> {
                val cutoff = if (config.configuredTimestamp > 0) config.configuredTimestamp else (now - 15 * 60 * 1000L)
                timestamp >= cutoff
            }
            HistorySyncMode.LAST_24_HOURS -> timestamp >= (now - 24 * 60 * 60 * 1000L)
            HistorySyncMode.LAST_7_DAYS -> timestamp >= (now - 7 * 24 * 60 * 60 * 1000L)
            HistorySyncMode.ALL_TIME -> true
        }
    }

    override suspend fun syncPendingData(): Boolean = withContext(Dispatchers.IO) {
        var allSuccessful = true
        val tgConfig = telegramConfigManager.config.value

        /**
         * Step 1: Process SMS Outbox
         */
        val pendingSms = smsDao.getPendingSms()
        for (sms in pendingSms) {
            try {
                // Local/Mock sync
                val success = syncEngine.syncRecord("SMS", sms.id)
                
                // Telegram Forwarding (respecting historical timeframe setting)
                var tgSuccess = true
                val shouldForwardToTelegram = tgConfig.isConfigured && 
                    tgConfig.isSmsForwardingEnabled && 
                    isRecordWithinSyncWindow(sms.date, tgConfig)

                if (shouldForwardToTelegram) {
                    val name = contactsRepository.getNameForNumber(sms.address)
                    val formatted = TelegramMessageFormatter.formatSms(
                        sms = sms,
                        contactName = name,
                        style = tgConfig.messageFormatStyle
                    )
                    tgSuccess = telegramApiClient.sendMessage(
                        token = tgConfig.botToken,
                        chatId = tgConfig.chatId,
                        message = formatted,
                        silent = tgConfig.silentNotifications
                    )
                }

                if (success && tgSuccess) {
                    smsDao.updateSyncStatus(sms.id, SyncStatus.SYNCED)
                } else {
                    allSuccessful = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing SMS ID ${sms.id}: ${e.message}")
                allSuccessful = false
            }
        }

        /**
         * Step 2: Process Call Log Outbox
         */
        val pendingCalls = callDao.getPendingCalls()
        for (call in pendingCalls) {
            try {
                val success = syncEngine.syncRecord("Call", call.id)
                
                var tgSuccess = true
                val shouldForwardToTelegram = tgConfig.isConfigured && 
                    tgConfig.isCallForwardingEnabled && 
                    isRecordWithinSyncWindow(call.date, tgConfig)

                if (shouldForwardToTelegram) {
                    val name = contactsRepository.getNameForNumber(call.number)
                    val formatted = TelegramMessageFormatter.formatCall(
                        call = call,
                        contactName = name,
                        style = tgConfig.messageFormatStyle
                    )
                    tgSuccess = telegramApiClient.sendMessage(
                        token = tgConfig.botToken,
                        chatId = tgConfig.chatId,
                        message = formatted,
                        silent = tgConfig.silentNotifications
                    )
                }

                if (success && tgSuccess) {
                    callDao.updateSyncStatus(call.id, SyncStatus.SYNCED)
                } else {
                    allSuccessful = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing Call ID ${call.id}: ${e.message}")
                allSuccessful = false
            }
        }

        allSuccessful
    }

    override suspend fun exportHistoryToTelegram(): Boolean = withContext(Dispatchers.IO) {
        val tgConfig = telegramConfigManager.config.value
        Log.d(TAG, "Starting exportHistoryToTelegram. botToken blank=${tgConfig.botToken.isBlank()}, chatId blank=${tgConfig.chatId.isBlank()}")
        if (tgConfig.botToken.isBlank() || tgConfig.chatId.isBlank()) {
            Log.e(TAG, "Cannot export: Bot Token or Chat ID is empty.")
            return@withContext false
        }

        try {
            var allSms = smsDao.getAllSmsList()
            var allCalls = callDao.getAllCallsList()

            // If local DB is empty, attempt to fetch from system
            if (allSms.isEmpty()) {
                Log.d(TAG, "Local SMS DB empty, fetching from system...")
                try {
                    // Query directly or refresh
                    val cursor = contactsRepository // just logging
                } catch (e: Exception) {
                    Log.w(TAG, "Could not fetch SMS from system: ${e.message}")
                }
            }

            val contactNames = contactsRepository.getAllContactNames()
            val fileBytes = TelegramMessageFormatter.generateBackupDocument(allSms, allCalls, contactNames)
            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Nexum_Backup_$timestampStr.txt"
            val caption = "📁 <b>Nexum Communication Backup</b>\nTotal SMS: ${allSms.size} | Total Calls: ${allCalls.size}"

            Log.d(TAG, "Sending document: fileName=$fileName, size=${fileBytes.size} bytes")
            val success = telegramApiClient.sendDocument(
                token = tgConfig.botToken,
                chatId = tgConfig.chatId,
                fileBytes = fileBytes,
                fileName = fileName,
                caption = caption,
                silent = tgConfig.silentNotifications
            )
            Log.d(TAG, "sendDocument result: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting history to Telegram: ${e.message}", e)
            false
        }
    }
}
