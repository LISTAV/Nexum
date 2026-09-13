package com.kairav.nexum.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.data.repositories.SmsRepository
import com.kairav.nexum.ui.models.SentSmsUiModel
import com.kairav.nexum.utils.SimUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SentSmsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsRepository: SmsRepository,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val contactNames = flow {
        emit(contactsRepository.getAllContactNames())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val sentMessages: StateFlow<List<SentSmsUiModel>> = combine(
        smsRepository.sentSms,
        _searchQuery,
        contactNames
    ) { sentList, query, names ->
        val uiModels = sentList.map { sms ->
            val normalizedAddress = sms.address.replace("\\s".toRegex(), "").replace("-", "")
            val name = names[normalizedAddress] ?: names[sms.address]
            val simLabel = SimUtils.getSimLabel(simSlot = sms.simSlot, subId = sms.subId)
            SentSmsUiModel(
                sms = sms,
                contactName = name,
                simLabel = simLabel
            )
        }

        if (query.isBlank()) {
            uiModels
        } else {
            uiModels.filter { item ->
                val nameMatch = item.contactName?.contains(query, ignoreCase = true) == true
                val addressMatch = item.sms.address.contains(query, ignoreCase = true)
                val bodyMatch = item.sms.body.contains(query, ignoreCase = true)
                nameMatch || addressMatch || bodyMatch
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun deleteMessages(ids: List<Long>) {
        viewModelScope.launch {
            smsRepository.deleteMessages(ids)
        }
    }
}
