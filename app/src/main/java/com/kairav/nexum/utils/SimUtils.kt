package com.kairav.nexum.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Utility helper to identify SIM slot (SIM 1 / SIM 2) and carrier information.
 */
object SimUtils {

    /**
     * Pre-fetches subscriptionId to SIM slot index mapping in a single call to avoid per-row IPC.
     */
    fun getSubIdToSimSlotMap(context: Context): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                subscriptionManager?.activeSubscriptionInfoList?.forEach { subInfo ->
                    map[subInfo.subscriptionId] = subInfo.simSlotIndex
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return map
    }

    /**
     * Resolves subscriptionId into a 0-indexed SIM slot index (0 for SIM 1, 1 for SIM 2).
     * Returns -1 if unknown or unable to resolve.
     */
    fun getSimSlotIndex(context: Context, subId: Int): Int {
        if (subId < 0) return -1
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subInfoList = subscriptionManager?.activeSubscriptionInfoList
                val found = subInfoList?.find { it.subscriptionId == subId }
                if (found != null) {
                    return found.simSlotIndex
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return -1
    }

    /**
     * Resolves CallLog.Calls.PHONE_ACCOUNT_ID or subscription string into a 0-indexed SIM slot index.
     */
    fun getSimSlotFromPhoneAccount(context: Context, phoneAccountId: String?, subId: Int = -1): Int {
        if (subId >= 0) {
            val slot = getSimSlotIndex(context, subId)
            if (slot >= 0) return slot
        }
        if (!phoneAccountId.isNullOrBlank()) {
            phoneAccountId.toIntOrNull()?.let { id ->
                val slot = getSimSlotIndex(context, id)
                if (slot >= 0) return slot
                if (id in 0..3) return id
            }

            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val list = sm?.activeSubscriptionInfoList
                    list?.forEach { info ->
                        if (info.subscriptionId.toString() == phoneAccountId || 
                            info.iccId == phoneAccountId || 
                            info.simSlotIndex.toString() == phoneAccountId) {
                            return info.simSlotIndex
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return -1
    }

    /**
     * Returns a user-friendly label such as "SIM 1" or "SIM 2".
     * If simSlot is available (>=0), it uses that. Otherwise attempts resolution via subId.
     */
    fun getSimLabel(context: Context? = null, simSlot: Int, subId: Int = -1): String? {
        if (simSlot >= 0) {
            return "SIM ${simSlot + 1}"
        }
        if (subId >= 0 && context != null) {
            val slot = getSimSlotIndex(context, subId)
            if (slot >= 0) {
                return "SIM ${slot + 1}"
            }
        }
        return null
    }

    /**
     * Returns list of currently active SIM subscriptions (useful for multi-SIM selection).
     */
    fun getActiveSubscriptions(context: Context): List<SubscriptionInfo> {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                sm?.activeSubscriptionInfoList ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
