package com.kairav.nexum.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * CallRecord represents an individual call log entry stored in the local database.
 *
 * For non-Kotlin developers:
 * - This class defines the structure of the 'call_records' table in our database.
 */
@Entity(
    tableName = "call_records",
    indices = [Index(value = ["number"]), Index(value = ["date"])]
)
data class CallRecord(
    @PrimaryKey val id: Long,
    val number: String,   // The phone number involved in the call
    val date: Long,       // Timestamp of the call
    val duration: Long,   // Duration of the call in seconds
    val type: Int,       // 1 for Incoming, 2 for Outgoing, 3 for Missed, etc.
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val subId: Int = -1,
    val simSlot: Int = -1
)
