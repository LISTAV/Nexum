package com.kairav.nexum.ui.viewmodels

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.local.TelegramConfigManager
import com.kairav.nexum.data.models.ContactRecord
import com.kairav.nexum.data.models.SyncStatus
import com.kairav.nexum.data.remote.TelegramApiClient
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.utils.TelegramMessageFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposeSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val smsRepository: SmsRepository,
    private val telegramConfigManager: TelegramConfigManager,
    private val telegramApiClient: TelegramApiClient
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _recipient = MutableStateFlow<ContactRecord?>(null)
    val recipient: StateFlow<ContactRecord?> = _recipient.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _sendSuccessEvent = MutableSharedFlow<String>()
    val sendSuccessEvent: SharedFlow<String> = _sendSuccessEvent.asSharedFlow()

    // To track if we can send based on search query as a manual number
    val isManualNumber: StateFlow<Boolean> = _searchQuery
        .map { it.isNotBlank() && it.all { char -> char.isDigit() || char == '+' || char == '-' || char == ' ' } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val contactResults: StateFlow<List<ContactRecord>> = _searchQuery
        .debounce(300)
        .mapLatest { query ->
            if (query.isBlank()) emptyList()
            else contactsRepository.searchContacts(query)
        }
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onRecipientSelected(contact: ContactRecord?) {
        _recipient.value = contact
        if (contact != null) {
            _searchQuery.value = ""
        }
    }

    fun onMessageChange(text: String) {
        _message.value = text
    }

    fun attachContact(name: String, phone: String) {
        val snippet = if (name.isNotBlank() && phone.isNotBlank()) {
            "Contact: $name\nPhone: $phone"
        } else if (name.isNotBlank()) {
            "Contact: $name"
        } else {
            "Phone: $phone"
        }

        val current = _message.value
        _message.value = if (current.isBlank()) {
            snippet
        } else {
            "$current\n$snippet"
        }
    }

    fun attachContactFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val contact = contactsRepository.getContactFromUri(uri)
            if (contact != null) {
                attachContact(contact.displayName, contact.phoneNumber)
                Toast.makeText(context, "Contact attached: ${contact.displayName}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to load selected contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendSms() {
        val address = _recipient.value?.phoneNumber ?: _searchQuery.value.trim()
        val text = _message.value.trim()
        
        if (address.isBlank() || text.isBlank()) {
            Toast.makeText(context, "Recipient and message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                // Send SMS via SmsRepository (saves to system provider & Room DB)
                val sentRecord = smsRepository.sendSms(address = address, body = text)

                // Optional Telegram Forwarding
                val tgConfig = telegramConfigManager.config.value
                if (tgConfig.isConfigured && tgConfig.isSmsForwardingEnabled) {
                    try {
                        val contactName = contactsRepository.getNameForNumber(address) ?: _recipient.value?.displayName
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
                        Log.w("ComposeSms", "Telegram forwarding error: ${tgEx.message}")
                    }
                }

                _message.value = ""
                Toast.makeText(context, "SMS Sent", Toast.LENGTH_SHORT).show()
                Log.d("ComposeSms", "SMS sent to $address")

                // Emit event to open conversation
                _sendSuccessEvent.emit(address)
            } catch (e: Exception) {
                Log.e("ComposeSms", "Failed to send SMS", e)
                Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
