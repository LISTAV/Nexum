package com.kairav.nexum.ui.viewmodels

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.SmsRecord
import com.kairav.nexum.data.models.SyncStatus
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.ui.navigation.NavRoute
import com.kairav.nexum.utils.TelegramMessageFormatter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Chat screen.
 * Manages messages for a specific contact address.
 */
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val smsRepository: SmsRepository,
    private val contactsRepository: ContactsRepository,
    private val telegramConfigManager: TelegramConfigManager,
    private val telegramApiClient: TelegramApiClient,
    @Assisted val navRoute: NavRoute.Chat
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(navRoute: NavRoute.Chat): ChatViewModel
    }

    init {
        if (navRoute.contactAddress.isNotBlank()) {
            viewModelScope.launch {
                smsRepository.markAddressAsRead(navRoute.contactAddress)
            }
        }
    }

    val messages: StateFlow<List<SmsRecord>> = smsRepository.getMessagesForAddress(navRoute.contactAddress)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allContacts = kotlinx.coroutines.flow.flow {
        emit(contactsRepository.getAllContactsList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    suspend fun resolveContactFromUri(uri: android.net.Uri) = contactsRepository.getContactFromUri(uri)

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val address = navRoute.contactAddress

        viewModelScope.launch {
            try {
                // Send SMS via SmsRepository (saves to system provider & Room DB)
                val sentRecord = smsRepository.sendSms(address = address, body = text)

                // Telegram Forwarding
                val tgConfig = telegramConfigManager.config.value
                if (tgConfig.isConfigured && tgConfig.isSmsForwardingEnabled) {
                    try {
                        val contactName = contactsRepository.getNameForNumber(address)
                        val formattedMsg = TelegramMessageFormatter.formatSms(
                            sms = sentRecord,
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
                            smsRepository.insertSms(sentRecord.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    } catch (tgEx: Exception) {
                        // ignore tg error
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteMessages(ids: List<Long>) {
        viewModelScope.launch {
            smsRepository.deleteMessages(ids)
        }
    }
}
