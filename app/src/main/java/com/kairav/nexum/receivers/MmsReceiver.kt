package com.kairav.nexum.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * MmsReceiver handles incoming MMS messages.
 * Required for the app to be set as the default SMS handler.
 *
 * For non-Kotlin developers:
 * - Handles 'WAP_PUSH_DELIVER_ACTION' for MMS messages.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            // Handle the incoming MMS here
        }
    }
}
