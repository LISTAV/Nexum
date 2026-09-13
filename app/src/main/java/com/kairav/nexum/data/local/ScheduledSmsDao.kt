package com.kairav.nexum.data.local

import androidx.room.*
import com.kairav.nexum.data.models.ScheduleStatus
import com.kairav.nexum.data.models.ScheduledSmsRecord
import kotlinx.coroutines.flow.Flow

/**
 * ScheduledSmsDao provides database access methods for scheduled SMS records.
 */
@Dao
interface ScheduledSmsDao {

    @Query("SELECT * FROM scheduled_sms ORDER BY scheduledTimestamp ASC")
    fun getAllScheduledSms(): Flow<List<ScheduledSmsRecord>>

    @Query("SELECT * FROM scheduled_sms WHERE status = 'PENDING' ORDER BY scheduledTimestamp ASC")
    fun getPendingScheduledSmsFlow(): Flow<List<ScheduledSmsRecord>>

    @Query("SELECT * FROM scheduled_sms WHERE status = 'PENDING' ORDER BY scheduledTimestamp ASC")
    suspend fun getPendingScheduledSmsList(): List<ScheduledSmsRecord>

    @Query("SELECT * FROM scheduled_sms WHERE id = :id")
    suspend fun getScheduledSmsById(id: Long): ScheduledSmsRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledSms(sms: ScheduledSmsRecord): Long

    @Update
    suspend fun updateScheduledSms(sms: ScheduledSmsRecord)

    @Query("UPDATE scheduled_sms SET status = :status, lastExecutedAt = :lastExecutedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ScheduleStatus, lastExecutedAt: Long?)

    @Query("UPDATE scheduled_sms SET scheduledTimestamp = :newTimestamp, lastExecutedAt = :lastExecutedAt, status = 'PENDING' WHERE id = :id")
    suspend fun reschedule(id: Long, newTimestamp: Long, lastExecutedAt: Long?)

    @Query("DELETE FROM scheduled_sms WHERE id = :id")
    suspend fun deleteScheduledSms(id: Long)
}
