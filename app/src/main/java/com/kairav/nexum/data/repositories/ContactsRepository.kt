package com.kairav.nexum.data.repositories

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.kairav.nexum.data.models.ContactRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContactsRepository manages fetching and searching contacts from the system's ContactsContract.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Searches for contacts by name or phone number.
     */
    suspend fun searchContacts(query: String): List<ContactRecord> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val contacts = mutableListOf<ContactRecord>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )
            
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$query%", "%$query%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIndex != -1) cursor.getString(idIndex) else ""
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex != -1) cursor.getString(numberIndex) ?: "" else ""
                    val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null

                    contacts.add(
                        ContactRecord(
                            id = id,
                            displayName = name,
                            phoneNumber = number,
                            photoUri = photo
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Error searching contacts: ${e.message}")
        }
        // Deduplicate by number
        contacts.distinctBy { it.phoneNumber.replace("\\s".toRegex(), "") }
    }

    /**
     * Resolves a phone number to a display name.
     */
    suspend fun getNameForNumber(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    return@withContext cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Error resolving contact name: ${e.message}")
        }
        null
    }

    /**
     * Fetches all contacts and returns a map of normalized phone number to display name.
     */
    suspend fun getAllContactNames(): Map<String, String> = withContext(Dispatchers.IO) {
        val namesMap = mutableMapOf<String, String>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val rawNumber = if (numberIndex != -1) cursor.getString(numberIndex) else null
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    if (!rawNumber.isNullOrBlank() && !name.isNullOrBlank()) {
                        val number = rawNumber.replace("\\s".toRegex(), "").replace("-", "")
                        namesMap[number] = name
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Error getting all contact names: ${e.message}")
        }
        namesMap
    }

    /**
     * Resolves a ContactRecord from a contact Uri returned by system contact picker.
     */
    suspend fun getContactFromUri(uri: Uri): ContactRecord? = withContext(Dispatchers.IO) {
        try {
            var contactId: String? = null
            var displayName: String = ""
            var phoneNumber: String = ""

            // Query contact record
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (idCol != -1) contactId = cursor.getString(idCol)
                    if (nameCol != -1) displayName = cursor.getString(nameCol) ?: ""
                }
            }

            // If phone number is empty, look up phone number using contactId
            if (phoneNumber.isBlank() && !contactId.isNullOrBlank()) {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val numCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameCol = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numCol != -1) phoneNumber = phoneCursor.getString(numCol) ?: ""
                        if (displayName.isBlank() && nameCol != -1) displayName = phoneCursor.getString(nameCol) ?: ""
                    }
                }
            }

            // Direct query if uri was already a CommonDataKinds.Phone URI
            if (phoneNumber.isBlank()) {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { directCursor ->
                    if (directCursor.moveToFirst()) {
                        val numCol = directCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameCol = directCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (numCol != -1) phoneNumber = directCursor.getString(numCol) ?: ""
                        if (displayName.isBlank() && nameCol != -1) displayName = directCursor.getString(nameCol) ?: ""
                    }
                }
            }

            if (displayName.isNotBlank() || phoneNumber.isNotBlank()) {
                return@withContext ContactRecord(
                    id = contactId ?: "",
                    displayName = displayName.ifBlank { phoneNumber },
                    phoneNumber = phoneNumber,
                    photoUri = null
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Error getting contact from URI: ${e.message}", e)
        }
        null
    }

    /**
     * Fetches all contacts sorted by display name.
     */
    suspend fun getAllContactsList(): List<ContactRecord> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactRecord>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val id = if (idIndex != -1) cursor.getString(idIndex) else ""
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) ?: "" else ""
                    val number = if (numberIndex != -1) cursor.getString(numberIndex) ?: "" else ""
                    val photo = if (photoIndex != -1) cursor.getString(photoIndex) else null

                    contacts.add(ContactRecord(id, name, number, photo))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Error fetching all contacts: ${e.message}")
        }
        contacts.distinctBy { it.phoneNumber.replace("\\s".toRegex(), "") }
    }
}
