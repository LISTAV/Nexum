package com.kairav.nexum.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for Nexum app.
 * Each route is a serializable object or class that implements NavKey.
 * This ensures type-safe navigation and automatic state restoration.
 */
sealed interface NavRoute : NavKey {
    /** Route for the Conversations (Threads) list screen. */
    @Serializable
    data object Conversations : NavRoute

    /** Route for the Call History screen. */
    @Serializable
    data object CallHistory : NavRoute

    /** Route for the Sent SMS screen. */
    @Serializable
    data object SentSms : NavRoute

    /** Route for the Scheduled SMS screen. */
    @Serializable
    data object ScheduledSms : NavRoute

    /** Route for the Chat screen.
     * @property contactAddress The phone number of the contact to chat with.
     */
    @Serializable
    data class Chat(val contactAddress: String) : NavRoute

    /** Route for the Settings screen. */
    @Serializable
    data object Settings : NavRoute

    /** Route for the Compose SMS screen. */
    @Serializable
    data object ComposeSms : NavRoute

    /** Route for the Telegram Settings screen. */
    @Serializable
    data object TelegramSettings : NavRoute

    /** Route for the Donation / Support screen. */
    @Serializable
    data object Donation : NavRoute
}
