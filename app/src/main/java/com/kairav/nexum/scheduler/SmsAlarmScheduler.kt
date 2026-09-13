package com.kairav.nexum.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kairav.nexum.data.local.ScheduledSmsDao
import com.kairav.nexum.data.models.ScheduledSmsRecord
import com.kairav.nexum.receivers.ScheduledSmsReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SmsAlarmScheduler manages Android AlarmManager registrations for scheduled SMS.
 */
@Singleton
class SmsAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduledSmsDao: ScheduledSmsDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules exact alarm for a ScheduledSmsRecord.
     */
    fun schedule(record: ScheduledSmsRecord) {
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, record.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            record.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    record.scheduledTimestamp,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    record.scheduledTimestamp,
                    pendingIntent
                )
            }
            Log.d("SmsAlarmScheduler", "Alarm scheduled for ID: ${record.id} at ${record.scheduledTimestamp}")
        } catch (e: SecurityException) {
            Log.e("SmsAlarmScheduler", "Exact alarm permission missing, falling back: ${e.message}")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                record.scheduledTimestamp,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e("SmsAlarmScheduler", "Error setting alarm: ${e.message}", e)
        }
    }

    /**
     * Cancels an alarm by ID.
     */
    fun cancel(recordId: Long) {
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, recordId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            recordId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d("SmsAlarmScheduler", "Alarm cancelled for ID: $recordId")
        }
    }

    /**
     * Reschedules all pending alarms (e.g. after phone boot or app restart).
     */
    fun rescheduleAllPending() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pendingList = scheduledSmsDao.getPendingScheduledSmsList()
                val now = System.currentTimeMillis()
                for (record in pendingList) {
                    if (record.scheduledTimestamp > now) {
                        schedule(record)
                    } else if (record.repeatIntervalHours > 0) {
                        // Calculate next occurrence if past
                        val nextTime = calculateNextTriggerTime(record.scheduledTimestamp, record.repeatIntervalHours)
                        scheduledSmsDao.reschedule(record.id, nextTime, null)
                        schedule(record.copy(scheduledTimestamp = nextTime))
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsAlarmScheduler", "Error rescheduling pending alarms: ${e.message}", e)
            }
        }
    }

    /**
     * Calculates the next trigger timestamp based on repeat interval in hours.
     */
    fun calculateNextTriggerTime(currentScheduledTime: Long, repeatHours: Int): Long {
        if (repeatHours <= 0) return currentScheduledTime
        val intervalMillis = repeatHours.toLong() * 3600_000L
        var nextTime = currentScheduledTime + intervalMillis
        val now = System.currentTimeMillis()
        while (nextTime <= now) {
            nextTime += intervalMillis
        }
        return nextTime
    }
}
