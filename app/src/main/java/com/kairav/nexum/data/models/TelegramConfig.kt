package com.kairav.nexum.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class HistorySyncMode {
    REALTIME_ONLY,
    LAST_24_HOURS,
    LAST_7_DAYS,
    ALL_TIME
}

@Serializable
enum class MessageFormatStyle {
    DETAILED,
    COMPACT
}

/**
 * TelegramConfig represents the user's personal Telegram Bot configuration.
 */
@Serializable
data class TelegramConfig(
    val botToken: String = "",
    val chatId: String = "",
    val isSmsForwardingEnabled: Boolean = true,
    val isCallForwardingEnabled: Boolean = true,
    val isConfigured: Boolean = false,
    val historySyncMode: HistorySyncMode = HistorySyncMode.REALTIME_ONLY,
    val messageFormatStyle: MessageFormatStyle = MessageFormatStyle.DETAILED,
    val silentNotifications: Boolean = false,
    val configuredTimestamp: Long = 0L
)
