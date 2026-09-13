package com.kairav.nexum.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Execution status of a scheduled SMS.
 */
enum class ScheduleStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    FAILED
}

/**
 * ScheduledSmsRecord represents an automated SMS message queued for future delivery.
 *
 * @param repeatIntervalHours 0 for Once (no repeat), or 1..500 for repeating every N hours.
 */
@Entity(
    tableName = "scheduled_sms",
    indices = [Index(value = ["scheduledTimestamp"]), Index(value = ["status"])]
)
data class ScheduledSmsRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipientAddress: String,
    val recipientName: String? = null,
    val messageBody: String,
    val scheduledTimestamp: Long, // Epoch millis of when the message should be sent
    val repeatIntervalHours: Int = 0, // 0 = Once, 1..500 = Repeat every N hours
    val simSlot: Int = -1, // 0 for SIM 1, 1 for SIM 2, -1 for default
    val subId: Int = -1,
    val status: ScheduleStatus = ScheduleStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val lastExecutedAt: Long? = null
)
