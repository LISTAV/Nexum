package com.kairav.nexum.services

import android.app.IntentService
import android.content.Intent

/**
 * HeadlessSmsSendService allows other apps to request this app to send an SMS in the background.
 * Required for the app to be set as the default SMS handler.
 *
 * For non-Kotlin developers:
 * - An IntentService runs tasks in the background without a user interface.
 * - This specifically handles the 'RESPOND_VIA_MESSAGE' intent.
 */
class HeadlessSmsSendService : IntentService("HeadlessSmsSendService") {
    override fun onHandleIntent(intent: Intent?) {
        // Handle the background SMS sending request here
    }
}
