package com.kairav.nexum.utils

import com.kairav.nexum.data.models.CallRecord
import com.kairav.nexum.data.models.MessageFormatStyle
import com.kairav.nexum.data.models.SmsRecord
import java.text.SimpleDateFormat
import java.util.*

object TelegramMessageFormatter {

    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun escapeMarkdownV2(text: String): String {
        val reservedCharacters = listOf(
            "\\", "_", "*", "[", "]", "(", ")", "~", "`", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"
        )
        var escapedText = text
        for (char in reservedCharacters) {
            escapedText = escapedText.replace(char, "\\$char")
        }
        return escapedText
    }

    fun formatSms(
        sms: SmsRecord,
        contactName: String?,
        style: MessageFormatStyle = MessageFormatStyle.DETAILED
    ): String {
        val sender = if (!contactName.isNullOrBlank() && contactName != sms.address) {
            "$contactName (${sms.address})"
        } else {
            contactName ?: sms.address
        }

        val simLabel = if (sms.simSlot >= 0) " `[SIM ${sms.simSlot + 1}]`" else ""
        return when (style) {
            MessageFormatStyle.COMPACT -> {
                val icon = if (sms.type == 1) "📥" else "📤"
                val time = timeOnlyFormat.format(Date(sms.date))
                val displayName = contactName ?: sms.address
                val escapedDisplayName = escapeMarkdownV2(displayName)
                val escapedBody = escapeMarkdownV2(sms.body)
                val escapedTime = escapeMarkdownV2(time)
                "$icon *$escapedDisplayName*$simLabel: $escapedBody `[$escapedTime]`"
            }
            MessageFormatStyle.DETAILED -> {
                val direction = if (sms.type == 1) "📥 *Incoming SMS*" else "📤 *Outgoing SMS*"
                val time = fullDateFormat.format(Date(sms.date))
                val simLine = if (sms.simSlot >= 0) "\n*SIM:* SIM ${sms.simSlot + 1}" else ""
                """
                    $direction
                    *From/To:* ${escapeMarkdownV2(sender)}
                    *Time:* ${escapeMarkdownV2(time)}$simLine
                    
                    *Message:*
                    ${escapeMarkdownV2(sms.body)}
                """.trimIndent()
            }
        }
    }

    fun formatCall(
        call: CallRecord,
        contactName: String?,
        style: MessageFormatStyle = MessageFormatStyle.DETAILED
    ): String {
        val caller = if (!contactName.isNullOrBlank() && contactName != call.number) {
            "$contactName (${call.number})"
        } else {
            contactName ?: call.number
        }
        val durationStr = if (call.duration > 0) "${call.duration / 60}m ${call.duration % 60}s" else "0s"
        val simLabel = if (call.simSlot >= 0) " `[SIM ${call.simSlot + 1}]`" else ""

        return when (style) {
            MessageFormatStyle.COMPACT -> {
                val (icon, typeStr) = when (call.type) {
                    1 -> "📥" to "Incoming"
                    2 -> "📤" to "Outgoing"
                    3 -> "❌" to "Missed"
                    else -> "📞" to "Call"
                }
                val time = timeOnlyFormat.format(Date(call.date))
                val displayName = contactName ?: call.number
                val escapedDisplayName = escapeMarkdownV2(displayName)
                val escapedType = escapeMarkdownV2(typeStr)
                val escapedDuration = escapeMarkdownV2(durationStr)
                val escapedTime = escapeMarkdownV2(time)
                "📞 *$escapedDisplayName*$simLabel $icon $escapedType \\($escapedDuration\\) `[$escapedTime]`"
            }
            MessageFormatStyle.DETAILED -> {
                val type = when (call.type) {
                    1 -> "📥 *Incoming Call*"
                    2 -> "📤 *Outgoing Call*"
                    3 -> "❌ *Missed Call*"
                    else -> "📞 *Call Log*"
                }
                val time = fullDateFormat.format(Date(call.date))
                val simLine = if (call.simSlot >= 0) "\n*SIM:* SIM ${call.simSlot + 1}" else ""
                """
                    $type
                    *Number:* ${escapeMarkdownV2(caller)}
                    *Time:* ${escapeMarkdownV2(time)}
                    *Duration:* ${escapeMarkdownV2(durationStr)}$simLine
                """.trimIndent()
            }
        }
    }

    /**
     * Generates a clean, comprehensive backup report file of all SMS and Call logs.
     */
    fun generateBackupDocument(
        smsList: List<SmsRecord>,
        callList: List<CallRecord>,
        contactNames: Map<String, String>
    ): ByteArray {
        val sb = StringBuilder()
        val nowFormatted = fullDateFormat.format(Date())

        sb.appendLine("================================================================================")
        sb.appendLine("                       NEXUM COMMUNICATION BACKUP REPORT                        ")
        sb.appendLine("================================================================================")
        sb.appendLine("Generated At: $nowFormatted")
        sb.appendLine("Total SMS Records:  ${smsList.size}")
        sb.appendLine("Total Call Records: ${callList.size}")
        sb.appendLine("================================================================================")
        sb.appendLine()

        // PART 1: SMS LOGS
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("PART 1: SMS LOGS (${smsList.size} Messages)")
        sb.appendLine("--------------------------------------------------------------------------------")
        if (smsList.isEmpty()) {
            sb.appendLine("No SMS records found.")
        } else {
            smsList.sortedBy { it.date }.forEachIndexed { index, sms ->
                val dateStr = fullDateFormat.format(Date(sms.date))
                val direction = if (sms.type == 1) "INCOMING" else "OUTGOING"
                val normalizedAddress = sms.address.replace("\\s".toRegex(), "").replace("-", "")
                val name = contactNames[normalizedAddress] ?: contactNames[sms.address]
                val contactInfo = if (name != null) "$name (${sms.address})" else sms.address

                sb.appendLine("[#${index + 1}] $dateStr | $direction | $contactInfo")
                sb.appendLine("Message: ${sms.body}")
                sb.appendLine()
            }
        }

        // PART 2: CALL LOGS
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("PART 2: CALL LOGS (${callList.size} Calls)")
        sb.appendLine("--------------------------------------------------------------------------------")
        if (callList.isEmpty()) {
            sb.appendLine("No Call records found.")
        } else {
            callList.sortedBy { it.date }.forEachIndexed { index, call ->
                val dateStr = fullDateFormat.format(Date(call.date))
                val typeStr = when (call.type) {
                    1 -> "INCOMING"
                    2 -> "OUTGOING"
                    3 -> "MISSED"
                    else -> "CALL"
                }
                val durationStr = if (call.duration > 0) "${call.duration / 60}m ${call.duration % 60}s" else "0s"
                val normalizedNumber = call.number.replace("\\s".toRegex(), "").replace("-", "")
                val name = contactNames[normalizedNumber] ?: contactNames[call.number]
                val contactInfo = if (name != null) "$name (${call.number})" else call.number

                sb.appendLine("[#${index + 1}] $dateStr | $typeStr | $contactInfo | Duration: $durationStr")
            }
        }

        sb.appendLine()
        sb.appendLine("================================================================================")
        sb.appendLine("                              END OF BACKUP REPORT                              ")
        sb.appendLine("================================================================================")

        return sb.toString().toByteArray(Charsets.UTF_8)
    }
}
