package com.kairav.nexum.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kairav.nexum.data.models.HistorySyncMode
import com.kairav.nexum.data.models.MessageFormatStyle
import com.kairav.nexum.data.models.TelegramConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TelegramConfigManager securely manages Telegram Bot credentials using EncryptedSharedPreferences.
 */
@Singleton
class TelegramConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "telegram_config_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<TelegramConfig> = _config.asStateFlow()

    fun saveConfig(config: TelegramConfig) {
        val configuredTime = if (config.isConfigured && config.configuredTimestamp == 0L) {
            System.currentTimeMillis()
        } else {
            config.configuredTimestamp
        }
        val finalConfig = config.copy(configuredTimestamp = configuredTime)

        sharedPreferences.edit()
            .putString("bot_token", finalConfig.botToken)
            .putString("chat_id", finalConfig.chatId)
            .putBoolean("sms_enabled", finalConfig.isSmsForwardingEnabled)
            .putBoolean("call_enabled", finalConfig.isCallForwardingEnabled)
            .putBoolean("is_configured", finalConfig.isConfigured)
            .putString("history_sync_mode", finalConfig.historySyncMode.name)
            .putString("message_format_style", finalConfig.messageFormatStyle.name)
            .putBoolean("silent_notifications", finalConfig.silentNotifications)
            .putLong("configured_timestamp", finalConfig.configuredTimestamp)
            .apply()
        
        _config.value = finalConfig
    }

    private fun loadConfig(): TelegramConfig {
        val historyModeStr = sharedPreferences.getString("history_sync_mode", HistorySyncMode.REALTIME_ONLY.name)
            ?: HistorySyncMode.REALTIME_ONLY.name
        val historyMode = try {
            HistorySyncMode.valueOf(historyModeStr)
        } catch (e: Exception) {
            HistorySyncMode.REALTIME_ONLY
        }

        val formatStyleStr = sharedPreferences.getString("message_format_style", MessageFormatStyle.DETAILED.name)
            ?: MessageFormatStyle.DETAILED.name
        val formatStyle = try {
            MessageFormatStyle.valueOf(formatStyleStr)
        } catch (e: Exception) {
            MessageFormatStyle.DETAILED
        }

        return TelegramConfig(
            botToken = sharedPreferences.getString("bot_token", "") ?: "",
            chatId = sharedPreferences.getString("chat_id", "") ?: "",
            isSmsForwardingEnabled = sharedPreferences.getBoolean("sms_enabled", true),
            isCallForwardingEnabled = sharedPreferences.getBoolean("call_enabled", true),
            isConfigured = sharedPreferences.getBoolean("is_configured", false),
            historySyncMode = historyMode,
            messageFormatStyle = formatStyle,
            silentNotifications = sharedPreferences.getBoolean("silent_notifications", false),
            configuredTimestamp = sharedPreferences.getLong("configured_timestamp", 0L)
        )
    }
    
    fun clearConfig() {
        sharedPreferences.edit().clear().apply()
        _config.value = TelegramConfig()
    }
}
