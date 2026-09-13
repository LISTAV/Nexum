package com.kairav.nexum.ui.models

import com.kairav.nexum.data.models.CallRecord
import com.kairav.nexum.data.models.SmsRecord

data class SmsThreadUiModel(
    val lastMessage: SmsRecord,
    val contactName: String?,
    val unreadCount: Int = 0
)

data class CallRecordUiModel(
    val call: CallRecord,
    val contactName: String?
)

data class SentSmsUiModel(
    val sms: SmsRecord,
    val contactName: String?,
    val simLabel: String?
)
