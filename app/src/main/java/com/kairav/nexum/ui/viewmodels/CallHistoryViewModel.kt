package com.kairav.nexum.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairav.nexum.data.repositories.CallLogRepository
import com.kairav.nexum.data.repositories.ContactsRepository
import com.kairav.nexum.ui.models.CallRecordUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Call History screen.
 */
@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            callLogRepository.fetchCallsFromSystem()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val contactNames = flow {
        emit(contactsRepository.getAllContactNames())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Formatter used to group calls by day
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    /**
     * StateFlow providing call records grouped by their date string.
     */
    val groupedCalls: StateFlow<Map<String, List<CallRecordUiModel>>> = combine(
        callLogRepository.allCalls,
        _searchQuery,
        contactNames
    ) { calls, query, names ->
        val mapped = calls.map { call ->
            val normalizedNumber = call.number.replace("\\s".toRegex(), "").replace("-", "")
            val contactName = names[normalizedNumber] ?: names[call.number]
            CallRecordUiModel(call, contactName)
        }

        val filtered = if (query.isBlank()) {
            mapped
        } else {
            mapped.filter { 
                it.call.number.contains(query, ignoreCase = true) || 
                it.contactName?.contains(query, ignoreCase = true) == true
            }
        }
        // Group the flat list of calls into a map keyed by date string
        filtered.groupBy { dateFormat.format(Date(it.call.date)) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }
}
