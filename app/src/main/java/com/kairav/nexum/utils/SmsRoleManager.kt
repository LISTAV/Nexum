package com.kairav.nexum.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher

/**
 * SmsRoleManager helps in checking and requesting the default SMS app role.
 *
 * For non-Kotlin developers:
 * - Starting from Android 10, the 'RoleManager' is used to request specific system roles
 *   like the default SMS or Phone app.
 */
class SmsRoleManager(private val context: Context) {

    /**
     * Checks if Nexum is already the default SMS application.
     */
    fun isDefaultSmsApp(): Boolean {
        val packageName = context.packageName
        val defaultPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        if (defaultPackage != null && defaultPackage == packageName) {
            return true
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) ?: false
        } else {
            false
        }
    }

    /**
     * Launches the system dialog to request the SMS role.
     */
    fun requestSmsRole(launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
            if (intent != null) {
                launcher.launch(intent)
            }
        }
    }
}
