package com.kairav.nexum.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages donation prompts, cooldowns, and status.
 *
 * Rules:
 * - Ask user every 2 to 3 days (default 3 days on dismissal).
 * - If user donates or marks as donated, snooze prompts for 3 months (90 days).
 * - On brand new install, wait 2 days before the first prompt to ensure a smooth onboarding.
 */
@Singleton
class DonationManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val DONATION_URL = "https://buymeacoffee.com/xuvidhah"
        private const val PREFS_NAME = "nexum_donation_prefs"
        private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
        private const val KEY_NEXT_PROMPT_TIME = "next_prompt_time"
        private const val KEY_LAST_DONATION_TIME = "last_donation_time"
        private const val KEY_HAS_DONATED = "has_donated"

        val SNOOZE_DISMISSED_MS = TimeUnit.DAYS.toMillis(3) // 3 days
        val SNOOZE_DONATED_MS = TimeUnit.DAYS.toMillis(90)   // 3 months (90 days)
        val FIRST_RUN_DELAY_MS = TimeUnit.DAYS.toMillis(2)  // 2 days
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _shouldShowPrompt = MutableStateFlow(false)
    val shouldShowPrompt: StateFlow<Boolean> = _shouldShowPrompt.asStateFlow()

    private val _hasDonated = MutableStateFlow(prefs.getBoolean(KEY_HAS_DONATED, false))
    val hasDonated: StateFlow<Boolean> = _hasDonated.asStateFlow()

    init {
        initTiming()
    }

    private fun initTiming() {
        val now = System.currentTimeMillis()
        if (!prefs.contains(KEY_FIRST_LAUNCH_TIME)) {
            // First time running the app - delay first prompt by 2 days
            prefs.edit()
                .putLong(KEY_FIRST_LAUNCH_TIME, now)
                .putLong(KEY_NEXT_PROMPT_TIME, now + FIRST_RUN_DELAY_MS)
                .apply()
        }
    }

    /**
     * Checks if a donation prompt should be shown.
     */
    fun checkPromptEligibility(): Boolean {
        val now = System.currentTimeMillis()
        val nextPromptTime = prefs.getLong(KEY_NEXT_PROMPT_TIME, now + FIRST_RUN_DELAY_MS)
        val isEligible = now >= nextPromptTime
        _shouldShowPrompt.value = isEligible
        return isEligible
    }

    /**
     * Called when the user clicks "Maybe Later" or dismisses the prompt.
     * Snoozes the reminder for 3 days.
     */
    fun recordDismissed() {
        val nextTime = System.currentTimeMillis() + SNOOZE_DISMISSED_MS
        prefs.edit()
            .putLong(KEY_NEXT_PROMPT_TIME, nextTime)
            .apply()
        _shouldShowPrompt.value = false
    }

    /**
     * Called when user donates or confirms they have already donated.
     * Snoozes the prompt for 3 months (90 days).
     */
    fun recordDonated() {
        val now = System.currentTimeMillis()
        val nextTime = now + SNOOZE_DONATED_MS
        prefs.edit()
            .putBoolean(KEY_HAS_DONATED, true)
            .putLong(KEY_LAST_DONATION_TIME, now)
            .putLong(KEY_NEXT_PROMPT_TIME, nextTime)
            .apply()
        _hasDonated.value = true
        _shouldShowPrompt.value = false
    }

    /**
     * Returns how many days remain until the next reminder (for display in settings).
     */
    fun getNextPromptDateMillis(): Long {
        return prefs.getLong(KEY_NEXT_PROMPT_TIME, 0L)
    }

    fun dismissPromptDialog() {
        _shouldShowPrompt.value = false
    }
}
