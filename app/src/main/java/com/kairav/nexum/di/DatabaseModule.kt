package com.kairav.nexum.di

import android.content.Context
import androidx.room.Room
import com.kairav.nexum.data.local.AppDatabase
import com.kairav.nexum.data.local.CallDao
import com.kairav.nexum.data.local.SmsDao
import com.kairav.nexum.data.repositories.SyncRepository
import com.kairav.nexum.data.repositories.SyncRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule provides Hilt with instructions on how to create database-related objects.
 *
 * For non-Kotlin developers:
 * - '@Module' and '@InstallIn' tell Hilt that this class contains "recipes" for creating objects.
 * - '@Provides' indicates a specific function that creates an object Hilt can inject elsewhere.
 * - '@Singleton' ensures that only one instance of the database exists throughout the app's life.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private fun createTablesIfNotExist(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sms_records` (
                `id` INTEGER NOT NULL,
                `address` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `type` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `subId` INTEGER NOT NULL,
                `simSlot` INTEGER NOT NULL,
                `read` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_records_address` ON `sms_records` (`address`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_records_date` ON `sms_records` (`date`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `call_records` (
                `id` INTEGER NOT NULL,
                `number` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `duration` INTEGER NOT NULL,
                `type` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `subId` INTEGER NOT NULL,
                `simSlot` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_number` ON `call_records` (`number`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_records_date` ON `call_records` (`date`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `scheduled_sms` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipientAddress` TEXT NOT NULL,
                `recipientName` TEXT,
                `messageBody` TEXT NOT NULL,
                `scheduledTimestamp` INTEGER NOT NULL,
                `repeatIntervalHours` INTEGER NOT NULL,
                `simSlot` INTEGER NOT NULL,
                `subId` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastExecutedAt` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_sms_scheduledTimestamp` ON `scheduled_sms` (`scheduledTimestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_sms_status` ON `scheduled_sms` (`status`)")
    }

    private val MIGRATION_1_7 = object : androidx.room.migration.Migration(1, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = createTablesIfNotExist(db)
    }
    private val MIGRATION_2_7 = object : androidx.room.migration.Migration(2, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = createTablesIfNotExist(db)
    }
    private val MIGRATION_3_7 = object : androidx.room.migration.Migration(3, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = createTablesIfNotExist(db)
    }
    private val MIGRATION_4_7 = object : androidx.room.migration.Migration(4, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = createTablesIfNotExist(db)
    }
    private val MIGRATION_5_7 = object : androidx.room.migration.Migration(5, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = createTablesIfNotExist(db)
    }
    private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = createTablesIfNotExist(db)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "nexum_database"
        )
        .addMigrations(
            MIGRATION_1_7,
            MIGRATION_2_7,
            MIGRATION_3_7,
            MIGRATION_4_7,
            MIGRATION_5_7,
            MIGRATION_6_7
        )
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

    @Provides
    fun provideSmsDao(database: AppDatabase): SmsDao {
        return database.smsDao()
    }

    @Provides
    fun provideCallDao(database: AppDatabase): CallDao {
        return database.callDao()
    }

    @Provides
    fun provideScheduledSmsDao(database: AppDatabase): com.kairav.nexum.data.local.ScheduledSmsDao {
        return database.scheduledSmsDao()
    }

    @Provides
    @Singleton
    fun provideSyncRepository(impl: SyncRepositoryImpl): SyncRepository {
        return impl
    }
}
