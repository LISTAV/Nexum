package com.kairav.nexum.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairav.nexum.scheduler.SmsAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BootReceiver restores all pending scheduled SMS alarms when the device reboots.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsAlarmScheduler: SmsAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d("BootReceiver", "Device restarted, restoring pending scheduled SMS alarms")
            smsAlarmScheduler.rescheduleAllPending()
        }
    }
}
