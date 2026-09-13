package com.kairav.nexum.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kairav.nexum.data.models.CallRecord
import com.kairav.nexum.data.models.ScheduledSmsRecord
import com.kairav.nexum.data.models.SmsRecord

/**
 * AppDatabase is the main database class for the application.
 */
@Database(
    entities = [SmsRecord::class, CallRecord::class, ScheduledSmsRecord::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun callDao(): CallDao
    abstract fun scheduledSmsDao(): ScheduledSmsDao
}
