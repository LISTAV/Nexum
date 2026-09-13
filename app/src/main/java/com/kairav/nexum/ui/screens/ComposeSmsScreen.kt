package com.kairav.nexum.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairav.nexum.ui.viewmodels.ComposeSmsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSmsScreen(
    viewModel: ComposeSmsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val contactResults by viewModel.contactResults.collectAsState()
    val recipient by viewModel.recipient.collectAsState()
    val message by viewModel.message.collectAsState()
    val isManualNumber by viewModel.isManualNumber.collectAsState()

    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    var showInAppContactDialog by remember { mutableStateOf(false) }

    // System Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            viewModel.attachContactFromUri(uri)
        }
    }

    // Listen for successful send and automatically open conversation
    LaunchedEffect(Unit) {
        viewModel.sendSuccessEvent.collect { contactAddress ->
            onNavigateToChat(contactAddress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Message") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Recipient Section
            if (recipient == null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    label = { Text("To (Name or Number)") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                
                if (contactResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(contactResults) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                supportingContent = { Text(contact.phoneNumber) },
                                modifier = Modifier.clickable { 
                                    viewModel.onRecipientSelected(contact) 
                                }
                            )
                        }
                    }
                } else if (searchQuery.isNotBlank() && !isManualNumber) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No contacts found", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                InputChip(
                    selected = true,
                    onClick = { viewModel.onRecipientSelected(null) },
                    label = { Text(recipient!!.displayName) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove") },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            // Message Input Section with Attachment Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Attachment menu button
                Box {
                    IconButton(
                        onClick = { isAttachmentMenuExpanded = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Add Attachment",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = isAttachmentMenuExpanded,
                        onDismissRequest = { isAttachmentMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Attach Contact") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                isAttachmentMenuExpanded = false
                                try {
                                    contactPickerLauncher.launch(null)
                                } catch (e: Exception) {
                                    showInAppContactDialog = true
                                }
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = { viewModel.onMessageChange(it) },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    shape = MaterialTheme.shapes.large
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.sendSms() },
                    enabled = (recipient != null || isManualNumber) && message.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    // In-App Contact Picker Fallback Dialog
    if (showInAppContactDialog) {
        val allContacts by viewModel.allContacts.collectAsState()
        var contactFilter by remember { mutableStateOf("") }
        val filteredContacts = remember(allContacts, contactFilter) {
            if (contactFilter.isBlank()) allContacts
            else allContacts.filter {
                it.displayName.contains(contactFilter, ignoreCase = true) ||
                it.phoneNumber.contains(contactFilter, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showInAppContactDialog = false },
            title = { Text("Select Contact to Attach") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    OutlinedTextField(
                        value = contactFilter,
                        onValueChange = { contactFilter = it },
                        placeholder = { Text("Search contacts...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )
                    if (filteredContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No contacts found", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredContacts, key = { it.id + it.phoneNumber }) { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.displayName) },
                                    supportingContent = { Text(contact.phoneNumber) },
                                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.attachContact(contact.displayName, contact.phoneNumber)
                                        showInAppContactDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInAppContactDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
