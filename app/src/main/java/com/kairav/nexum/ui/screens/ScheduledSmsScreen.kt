package com.kairav.nexum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScheduleSend
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairav.nexum.data.models.ScheduleStatus
import com.kairav.nexum.data.models.ScheduledSmsRecord
import com.kairav.nexum.ui.components.ScheduleSmsSheet
import com.kairav.nexum.ui.viewmodels.ScheduledSmsViewModel
import com.kairav.nexum.utils.SimUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledSmsScreen(viewModel: ScheduledSmsViewModel) {
    val scheduledList by viewModel.scheduledSmsList.collectAsState()
    var showScheduleSheet by remember { mutableStateOf(false) }

    val dateTimeFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showScheduleSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Schedule New SMS")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (scheduledList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ScheduleSend,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No Scheduled Messages",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Schedule automated SMS to be sent at any future date and time with custom repeat intervals (1 - 500 hours).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showScheduleSheet = true },
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.AlarmAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Schedule a Message")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(scheduledList, key = { it.id }) { item ->
                        ScheduledSmsCard(
                            item = item,
                            formattedTime = dateTimeFormat.format(Date(item.scheduledTimestamp)),
                            onSendNow = { viewModel.sendNow(item) },
                            onCancel = { viewModel.cancelSchedule(item.id) },
                            onDelete = { viewModel.deleteSchedule(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (showScheduleSheet) {
        ScheduleSmsSheet(
            viewModel = viewModel,
            onDismiss = { showScheduleSheet = false }
        )
    }
}

@Composable
fun ScheduledSmsCard(
    item: ScheduledSmsRecord,
    formattedTime: String,
    onSendNow: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    val isPending = item.status == ScheduleStatus.PENDING
    val isCompleted = item.status == ScheduleStatus.COMPLETED
    val isFailed = item.status == ScheduleStatus.FAILED
    val isCancelled = item.status == ScheduleStatus.CANCELLED

    val containerColor = when {
        isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        isCancelled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Recipient Info & Status Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (isPending) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val initial = (item.recipientName ?: item.recipientAddress)
                                .filter { it.isLetter() }.take(1).ifEmpty { "#" }.uppercase()
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isPending) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.recipientName ?: item.recipientAddress,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.recipientName != null) {
                            Text(
                                text = item.recipientAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primaryContainer
                        isFailed -> MaterialTheme.colorScheme.error
                        isCancelled -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when (item.status) {
                            ScheduleStatus.PENDING -> "Scheduled"
                            ScheduleStatus.COMPLETED -> "Sent"
                            ScheduleStatus.CANCELLED -> "Cancelled"
                            ScheduleStatus.FAILED -> "Failed"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = when {
                            isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
                            isFailed -> MaterialTheme.colorScheme.onError
                            isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Message Body Snippet
            Text(
                text = item.messageBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isPending) 1f else 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Row: Time, Repeat Badge, SIM, and Menu Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isPending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isPending) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isPending) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Repeat Pill
                    if (item.repeatIntervalHours > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            val label = if (item.repeatIntervalHours == 24) {
                                "🔁 Daily (24h)"
                            } else if (item.repeatIntervalHours == 168) {
                                "🔁 Weekly (168h)"
                            } else if (item.repeatIntervalHours >= 24 && item.repeatIntervalHours % 24 == 0) {
                                "🔁 Every ${item.repeatIntervalHours / 24}d (${item.repeatIntervalHours}h)"
                            } else {
                                "🔁 Every ${item.repeatIntervalHours}h"
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // SIM Badge
                    val simLabel = SimUtils.getSimLabel(simSlot = item.simSlot, subId = item.subId)
                    if (simLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = simLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Actions Menu
                Box {
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        if (isPending) {
                            DropdownMenuItem(
                                text = { Text("Send Now") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                                onClick = {
                                    isMenuExpanded = false
                                    onSendNow()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cancel Schedule") },
                                leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null) },
                                onClick = {
                                    isMenuExpanded = false
                                    onCancel()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                isMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
