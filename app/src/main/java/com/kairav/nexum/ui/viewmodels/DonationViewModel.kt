package com.kairav.nexum.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.kairav.nexum.data.local.DonationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DonationViewModel @Inject constructor(
    private val donationManager: DonationManager
) : ViewModel() {

    val shouldShowPrompt: StateFlow<Boolean> = donationManager.shouldShowPrompt
    val hasDonated: StateFlow<Boolean> = donationManager.hasDonated

    fun checkEligibility() {
        donationManager.checkPromptEligibility()
    }

    fun onDismissPrompt() {
        donationManager.recordDismissed()
    }

    fun onDonationConfirmed() {
        donationManager.recordDonated()
    }

    fun getNextPromptDateMillis(): Long {
        return donationManager.getNextPromptDateMillis()
    }
}
