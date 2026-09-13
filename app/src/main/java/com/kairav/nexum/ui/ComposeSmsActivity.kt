package com.kairav.nexum.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.kairav.nexum.MainActivity

/**
 * ComposeSmsActivity handles incoming system intents to compose or send an SMS
 * (such as tapping the 'Message' action in contacts, dialer, or sharing content).
 */
class ComposeSmsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var address: String? = null
        var body: String? = null

        val uri: Uri? = intent?.data
        if (uri != null) {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                val ssp = uri.schemeSpecificPart
                if (!ssp.isNullOrBlank()) {
                    val queryIndex = ssp.indexOf('?')
                    address = if (queryIndex != -1) ssp.substring(0, queryIndex) else ssp
                    if (queryIndex != -1) {
                        try {
                            body = uri.getQueryParameter("body") ?: uri.getQueryParameter("sms_body")
                        } catch (e: Exception) {
                            // ignore query parsing errors
                        }
                    }
                }
            }
        }

        if (address.isNullOrBlank()) {
            address = intent?.getStringExtra("address")
                ?: intent?.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: intent?.getStringExtra("recipient")
                ?: intent?.getStringArrayExtra(Intent.EXTRA_EMAIL)?.firstOrNull()
        }

        if (body.isNullOrBlank()) {
            body = intent?.getStringExtra("sms_body")
                ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        }

        // Forward to MainActivity and open the conversation or compose screen directly
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            action = intent?.action
            data = intent?.data
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!address.isNullOrBlank()) {
                putExtra("EXTRA_CONTACT_ADDRESS", address)
            }
            if (!body.isNullOrBlank()) {
                putExtra("EXTRA_MESSAGE_BODY", body)
            }
        }

        startActivity(mainIntent)
        finish()
    }
}
