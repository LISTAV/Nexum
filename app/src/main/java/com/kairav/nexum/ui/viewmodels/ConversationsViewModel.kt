package com.kairav.nexum.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.ui.models.SmsThreadUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Conversations screen.
 * Groups SMS records into threads by contact address.
 */
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val smsRepository: SmsRepository,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            smsRepository.fetchSmsFromSystem()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val contactNames = flow {
        emit(contactsRepository.getAllContactNames())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * StateFlow providing a list of latest messages for each contact (threads).
     */
    val threads: StateFlow<List<SmsThreadUiModel>> = combine(
        smsRepository.allSms,
        _searchQuery,
        contactNames
    ) { smsList, query, names ->
        // Logic to group individual SMS records into conversation threads
        val grouped = smsList.groupBy { it.address }
            .mapNotNull { (address, messages) -> 
                val lastMessage = messages.maxByOrNull { it.date } ?: return@mapNotNull null
                val normalizedAddress = address.replace("\\s".toRegex(), "").replace("-", "")
                val contactName = names[normalizedAddress] ?: names[address]
                val unreadCount = messages.count { it.read == 0 && it.type == 1 }
                SmsThreadUiModel(lastMessage, contactName, unreadCount)
            }
            .sortedByDescending { it.lastMessage.date }

        if (query.isBlank()) {
            grouped
        } else {
            grouped.filter { 
                it.lastMessage.address.contains(query, ignoreCase = true) || 
                it.lastMessage.body.contains(query, ignoreCase = true) ||
                it.contactName?.contains(query, ignoreCase = true) == true
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun deleteThread(address: String) {
        viewModelScope.launch {
            smsRepository.deleteThread(address)
        }
    }
}
