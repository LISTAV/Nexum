package com.kairav.nexum.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairav.nexum.ui.viewmodels.ScheduledSmsViewModel
import com.kairav.nexum.utils.SimUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSmsSheet(
    viewModel: ScheduledSmsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allContacts by viewModel.allContacts.collectAsState()

    var recipientAddress by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf<String?>(null) }
    var messageBody by remember { mutableStateOf("") }

    // Repeat in Hours: 0 = Once, 1..500 = Repeat every N hours
    var repeatHours by remember { mutableIntStateOf(0) }
    var customHoursText by remember { mutableStateOf("") }
    var isCustomHourMode by remember { mutableStateOf(false) }

    var selectedSimSlot by remember { mutableIntStateOf(-1) }

    // Time setup
    val calendar = remember {
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, 30) // Default: 30 minutes from now
        }
    }
    var scheduledEpoch by remember { mutableLongStateOf(calendar.timeInMillis) }

    var showContactSearchDialog by remember { mutableStateOf(false) }
    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    var showAttachContactDialog by remember { mutableStateOf(false) }

    val dateTimeFormat = remember { SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()) }

    // Contact Picker Launcher for Recipient
    val recipientContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val contact = viewModel.resolveContactFromUri(uri)
                if (contact != null) {
                    recipientAddress = contact.phoneNumber
                    recipientName = contact.displayName
                }
            }
        }
    }

    // Contact Picker Launcher for Attachment
    val attachContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val contact = viewModel.resolveContactFromUri(uri)
                if (contact != null) {
                    val snippet = "Contact: ${contact.displayName}\nPhone: ${contact.phoneNumber}"
                    messageBody = if (messageBody.isBlank()) snippet else "$messageBody\n$snippet"
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Text(
                text = "Schedule Message",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Recipient Section ---
            OutlinedTextField(
                value = if (recipientName != null) "$recipientName ($recipientAddress)" else recipientAddress,
                onValueChange = {
                    recipientAddress = it
                    recipientName = null
                },
                label = { Text("Recipient (Name or Number)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            try {
                                recipientContactPickerLauncher.launch(null)
                            } catch (e: Exception) {
                                showContactSearchDialog = true
                            }
                        }) {
                            Icon(Icons.Default.Contacts, contentDescription = "Pick Contact", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (recipientAddress.isNotEmpty() || recipientName != null) {
                            IconButton(onClick = {
                                recipientAddress = ""
                                recipientName = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Message Body Section ---
            OutlinedTextField(
                value = messageBody,
                onValueChange = { messageBody = it },
                label = { Text("Message") },
                placeholder = { Text("Type message to send...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    Box(modifier = Modifier.align(Alignment.End)) {
                        IconButton(onClick = { isAttachmentMenuExpanded = true }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = isAttachmentMenuExpanded,
                            onDismissRequest = { isAttachmentMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Attach Contact") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    isAttachmentMenuExpanded = false
                                    try {
                                        attachContactPickerLauncher.launch(null)
                                    } catch (e: Exception) {
                                        showAttachContactDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Date & Time Schedule Section ---
            Text(
                text = "When to Send",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Quick Preset Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 15) }
                            scheduledEpoch = cal.timeInMillis
                        },
                        label = { Text("+15 Mins") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                            scheduledEpoch = cal.timeInMillis
                        },
                        label = { Text("+1 Hour") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 9)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                            }
                            scheduledEpoch = cal.timeInMillis
                        },
                        label = { Text("Tomorrow 9 AM") }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 20)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                if (timeInMillis <= System.currentTimeMillis()) {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }
                            }
                            scheduledEpoch = cal.timeInMillis
                        },
                        label = { Text("Tonight 8 PM") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Date & Time Picker Card
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val currentCal = Calendar.getInstance().apply { timeInMillis = scheduledEpoch }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                currentCal.set(Calendar.YEAR, year)
                                currentCal.set(Calendar.MONTH, month)
                                currentCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        currentCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        currentCal.set(Calendar.MINUTE, minute)
                                        currentCal.set(Calendar.SECOND, 0)
                                        scheduledEpoch = currentCal.timeInMillis
                                    },
                                    currentCal.get(Calendar.HOUR_OF_DAY),
                                    currentCal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            currentCal.get(Calendar.YEAR),
                            currentCal.get(Calendar.MONTH),
                            currentCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dateTimeFormat.format(Date(scheduledEpoch)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap to change date or time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.EditCalendar, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Repeat Interval in Hours (1 - 500 Hours) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Repeat Interval",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (repeatHours > 0) {
                    Text(
                        text = "Every ${repeatHours}h" + if (repeatHours >= 24 && repeatHours % 24 == 0) " (${repeatHours / 24}d)" else "",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Preset Hour Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = repeatHours == 0 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 0
                            isCustomHourMode = false
                            customHoursText = ""
                        },
                        label = { Text("Once") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 1 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 1
                            isCustomHourMode = false
                            customHoursText = "1"
                        },
                        label = { Text("1 Hour") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 2 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 2
                            isCustomHourMode = false
                            customHoursText = "2"
                        },
                        label = { Text("2 Hours") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 6 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 6
                            isCustomHourMode = false
                            customHoursText = "6"
                        },
                        label = { Text("6 Hours") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 12 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 12
                            isCustomHourMode = false
                            customHoursText = "12"
                        },
                        label = { Text("12 Hours") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 24 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 24
                            isCustomHourMode = false
                            customHoursText = "24"
                        },
                        label = { Text("24 Hours (Daily)") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 48 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 48
                            isCustomHourMode = false
                            customHoursText = "48"
                        },
                        label = { Text("48 Hours (2 Days)") }
                    )
                }
                item {
                    FilterChip(
                        selected = repeatHours == 168 && !isCustomHourMode,
                        onClick = {
                            repeatHours = 168
                            isCustomHourMode = false
                            customHoursText = "168"
                        },
                        label = { Text("168 Hours (Weekly)") }
                    )
                }
                item {
                    FilterChip(
                        selected = isCustomHourMode,
                        onClick = {
                            isCustomHourMode = true
                        },
                        label = { Text("Custom Hours (1 - 500h)") }
                    )
                }
            }

            // Custom Hours Input Box
            if (isCustomHourMode) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = customHoursText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 4) {
                            customHoursText = digits
                            val parsed = digits.toIntOrNull() ?: 0
                            if (parsed in 1..500) {
                                repeatHours = parsed
                            } else if (parsed == 0) {
                                repeatHours = 0
                            }
                        }
                    },
                    label = { Text("Enter repeat interval (1 - 500 hours)") },
                    placeholder = { Text("e.g. 8, 36, 120") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = { Text("hours", modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- SIM Selection (Dual-SIM) ---
            val simMap = remember { SimUtils.getSubIdToSimSlotMap(context) }
            if (simMap.size > 1) {
                Text(
                    text = "Send via SIM",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSimSlot == -1,
                        onClick = { selectedSimSlot = -1 },
                        label = { Text("Default") }
                    )
                    FilterChip(
                        selected = selectedSimSlot == 0,
                        onClick = { selectedSimSlot = 0 },
                        label = { Text("SIM 1") }
                    )
                    FilterChip(
                        selected = selectedSimSlot == 1,
                        onClick = { selectedSimSlot = 1 },
                        label = { Text("SIM 2") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Actions ---
            Button(
                onClick = {
                    viewModel.scheduleMessage(
                        recipientAddress = recipientAddress,
                        recipientName = recipientName,
                        messageBody = messageBody,
                        scheduledTimestamp = scheduledEpoch,
                        repeatIntervalHours = repeatHours,
                        simSlot = selectedSimSlot,
                        onSuccess = onDismiss
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                enabled = recipientAddress.isNotBlank() && messageBody.isNotBlank() && scheduledEpoch > System.currentTimeMillis() && repeatHours in 0..500
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule Message", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // In-App Contact Picker for Recipient
    if (showContactSearchDialog) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(allContacts, query) {
            if (query.isBlank()) allContacts
            else allContacts.filter {
                it.displayName.contains(query, ignoreCase = true) || it.phoneNumber.contains(query, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showContactSearchDialog = false },
            title = { Text("Select Recipient") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search...") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered, key = { it.id + it.phoneNumber }) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                supportingContent = { Text(contact.phoneNumber) },
                                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    recipientAddress = contact.phoneNumber
                                    recipientName = contact.displayName
                                    showContactSearchDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContactSearchDialog = false }) { Text("Cancel") }
            }
        )
    }

    // In-App Contact Picker for Attachment
    if (showAttachContactDialog) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(allContacts, query) {
            if (query.isBlank()) allContacts
            else allContacts.filter {
                it.displayName.contains(query, ignoreCase = true) || it.phoneNumber.contains(query, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showAttachContactDialog = false },
            title = { Text("Attach Contact Card") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search...") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered, key = { it.id + it.phoneNumber }) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                supportingContent = { Text(contact.phoneNumber) },
                                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier.clickable {
                                    val snippet = "Contact: ${contact.displayName}\nPhone: ${contact.phoneNumber}"
                                    messageBody = if (messageBody.isBlank()) snippet else "$messageBody\n$snippet"
                                    showAttachContactDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachContactDialog = false }) { Text("Cancel") }
            }
        )
    }
}
