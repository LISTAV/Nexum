package com.kairav.nexum.data.models

/**
 * ContactRecord represents a contact from the system's address book.
 */
data class ContactRecord(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String? = null
)
