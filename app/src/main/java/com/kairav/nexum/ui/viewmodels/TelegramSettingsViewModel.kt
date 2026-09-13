package com.kairav.nexum.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.HistorySyncMode
import com.kairav.nexum.data.models.MessageFormatStyle
import com.kairav.nexum.data.models.TelegramConfig
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TelegramSettingsViewModel @Inject constructor(
    private val configManager: TelegramConfigManager,
    private val apiClient: TelegramApiClient,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val config: StateFlow<TelegramConfig> = configManager.config

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun updateConfig(
        botToken: String? = null,
        chatId: String? = null,
        smsEnabled: Boolean? = null,
        callEnabled: Boolean? = null,
        historySyncMode: HistorySyncMode? = null,
        messageFormatStyle: MessageFormatStyle? = null,
        silentNotifications: Boolean? = null
    ) {
        val current = config.value
        val next = current.copy(
            botToken = botToken ?: current.botToken,
            chatId = chatId ?: current.chatId,
            isSmsForwardingEnabled = smsEnabled ?: current.isSmsForwardingEnabled,
            isCallForwardingEnabled = callEnabled ?: current.isCallForwardingEnabled,
            historySyncMode = historySyncMode ?: current.historySyncMode,
            messageFormatStyle = messageFormatStyle ?: current.messageFormatStyle,
            silentNotifications = silentNotifications ?: current.silentNotifications
        )
        configManager.saveConfig(next)
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTesting.value = true
            _testStatus.value = "Testing connection..."
            
            val token = config.value.botToken
            val chatId = config.value.chatId
            
            val isTokenValid = apiClient.testBot(token)
            if (isTokenValid) {
                val messageSent = apiClient.sendMessage(
                    token = token,
                    chatId = chatId,
                    message = "✅ *Nexum Test Message*\nYour bot is successfully configured\\!",
                    silent = config.value.silentNotifications
                )
                if (messageSent) {
                    _testStatus.value = "Success! Test message sent to Telegram."
                    configManager.saveConfig(config.value.copy(isConfigured = true))
                } else {
                    _testStatus.value = "Token valid, but failed to send message. Check Chat ID."
                }
            } else {
                _testStatus.value = "Invalid Bot Token."
            }
            _isTesting.value = false
        }
    }

    fun exportHistoryToTelegram() {
        viewModelScope.launch {
            _isExporting.value = true
            _exportStatus.value = "Packaging and sending backup archive..."

            val success = syncRepository.exportHistoryToTelegram()
            if (success) {
                _exportStatus.value = "Success! Backup document sent to Telegram chat."
            } else {
                _exportStatus.value = "Failed to send backup document. Ensure bot is configured."
            }
            _isExporting.value = false
        }
    }
    
    fun clearStatus() {
        _testStatus.value = null
        _exportStatus.value = null
    }
}
