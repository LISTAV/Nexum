package com.kairav.nexum.data.local

import androidx.room.*
import com.kairav.nexum.data.models.SmsRecord
import kotlinx.coroutines.flow.Flow

/**
 * SmsDao provides methods for accessing and modifying SMS data in the database.
 *
 * For non-Kotlin developers:
 * - '@Dao' stands for Data Access Object. It defines the interface for database operations.
 * - 'Flow' is a stream of data that can be observed for changes.
 */
@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_records ORDER BY date DESC")
    fun getAllSms(): Flow<List<SmsRecord>>

    @Query("SELECT * FROM sms_records WHERE type = 2 ORDER BY date DESC")
    fun getSentSms(): Flow<List<SmsRecord>>

    @Query("SELECT * FROM sms_records ORDER BY date ASC")
    suspend fun getAllSmsList(): List<SmsRecord>

    @Query("SELECT * FROM sms_records WHERE address = :address ORDER BY date ASC")
    fun getSmsForAddress(address: String): Flow<List<SmsRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSms(sms: SmsRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSms(smsList: List<SmsRecord>)

    @Delete
    suspend fun deleteSms(sms: SmsRecord)

    @Query("SELECT * FROM sms_records WHERE syncStatus = 'PENDING'")
    suspend fun getPendingSms(): List<SmsRecord>

    @Query("UPDATE sms_records SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: com.kairav.nexum.data.models.SyncStatus)

    @Query("UPDATE sms_records SET read = 1 WHERE address = :address AND read = 0")
    suspend fun markAsReadByAddress(address: String)

    @Query("DELETE FROM sms_records WHERE id IN (:ids)")
    suspend fun deleteSmsByIds(ids: List<Long>)

    @Query("DELETE FROM sms_records WHERE address = :address")
    suspend fun deleteSmsByAddress(address: String)
}
