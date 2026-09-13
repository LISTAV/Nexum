package com.kairav.nexum.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SmsRecord represents an individual SMS message stored in the local database.
 *
 * For non-Kotlin developers:
 * - '@Entity' tells Room (our database library) that this class represents a table.
 * - 'data class' is a concise way to create a class that primarily holds data.
 * - '@PrimaryKey' specifies the unique identifier for each record.
 */
@Entity(
    tableName = "sms_records",
    indices = [Index(value = ["address"]), Index(value = ["date"])]
)
data class SmsRecord(
    @PrimaryKey val id: Long,
    val address: String, // The phone number of the sender/receiver
    val body: String,    // The content of the message
    val date: Long,      // Timestamp of the message
    val type: Int,       // 1 for Inbox, 2 for Sent, etc.
    val syncStatus: SyncStatus = SyncStatus.PENDING, // Tracks if this record is synced with a server
    val subId: Int = -1, // Subscription ID from system telephony provider
    val simSlot: Int = -1, // 0 for SIM 1, 1 for SIM 2, -1 if unknown
    val read: Int = 1 // 1 for Read, 0 for Unread
)
