package com.kairav.nexum.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairav.nexum.data.models.SmsRecord
import com.kairav.nexum.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChatScreen displays a message bubble interface for a specific contact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onNavigateBack: () -> Unit) {
    if (viewModel.navRoute.contactAddress.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Select a conversation to start messaging.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val messages by viewModel.messages.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Selection State
    var selectedMessageIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode = selectedMessageIds.isNotEmpty()

    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    var showInAppContactDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Helper to append contact snippet to inputText
    fun appendContactSnippet(name: String, phone: String) {
        val snippet = if (name.isNotBlank() && phone.isNotBlank()) {
            "Contact: $name\nPhone: $phone"
        } else if (name.isNotBlank()) {
            "Contact: $name"
        } else {
            "Phone: $phone"
        }
        inputText = if (inputText.isBlank()) snippet else "$inputText\n$snippet"
    }

    // System Contact Picker Launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val contact = viewModel.resolveContactFromUri(uri)
                if (contact != null) {
                    appendContactSnippet(contact.displayName, contact.phoneNumber)
                }
            }
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Automatically scroll to the latest message at the bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "${selectedMessageIds.size} selected" else viewModel.navRoute.contactAddress) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) selectedMessageIds = emptySet()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteMessages(selectedMessageIds.toList())
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isSelected = selectedMessageIds.contains(message.id)
                    MessageBubble(
                        message = message,
                        timeStr = dateFormat.format(Date(message.date)),
                        isSelected = isSelected,
                        onLongClick = {
                            selectedMessageIds = selectedMessageIds + message.id
                        },
                        onClick = {
                            if (isSelectionMode) {
                                selectedMessageIds = if (isSelected) {
                                    selectedMessageIds - message.id
                                } else {
                                    selectedMessageIds + message.id
                                }
                            }
                        }
                    )
                }
            }

            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment button with dropdown menu
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

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
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
                                        appendContactSnippet(contact.displayName, contact.phoneNumber)
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

@Composable
fun MessageBubble(
    message: SmsRecord,
    timeStr: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isMe = message.type == 2
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val color = when {
        isSelected -> MaterialTheme.colorScheme.tertiaryContainer
        isMe -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onColor = when {
        isSelected -> MaterialTheme.colorScheme.onTertiaryContainer
        isMe -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = if (isMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() }
                )
            },
        contentAlignment = alignment
    ) {
        Surface(
            color = color,
            shape = shape,
            tonalElevation = if (isSelected) 8.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.body, style = MaterialTheme.typography.bodyLarge, color = onColor)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val simLabel = com.kairav.nexum.utils.SimUtils.getSimLabel(simSlot = message.simSlot, subId = message.subId)
                    if (simLabel != null) {
                        Text(
                            text = simLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                fontSize = 9.sp
                            ),
                            color = onColor.copy(alpha = 0.75f)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = onColor.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = onColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
