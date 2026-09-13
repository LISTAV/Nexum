package com.kairav.nexum.data.local

import androidx.room.*
import com.kairav.nexum.data.models.CallRecord
import kotlinx.coroutines.flow.Flow

/**
 * CallDao provides methods for accessing and modifying Call Log data in the database.
 */
@Dao
interface CallDao {
    @Query("SELECT * FROM call_records ORDER BY date DESC")
    fun getAllCalls(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records ORDER BY date ASC")
    suspend fun getAllCallsList(): List<CallRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCalls(calls: List<CallRecord>)

    @Delete
    suspend fun deleteCall(call: CallRecord)

    @Query("SELECT * FROM call_records WHERE syncStatus = 'PENDING'")
    suspend fun getPendingCalls(): List<CallRecord>

    @Query("UPDATE call_records SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: com.kairav.nexum.data.models.SyncStatus)
}
