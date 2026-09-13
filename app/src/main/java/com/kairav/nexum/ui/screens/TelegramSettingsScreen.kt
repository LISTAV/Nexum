package com.kairav.nexum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kairav.nexum.data.models.HistorySyncMode
import com.kairav.nexum.data.models.MessageFormatStyle
import com.kairav.nexum.ui.viewmodels.TelegramSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSettingsScreen(
    viewModel: TelegramSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    
    var showToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram Integration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Setup Instructions
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("How to Setup", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Create a bot via @BotFather to get a Bot Token.\n" +
                        "2. Start a chat with your bot and send any message.\n" +
                        "3. Use @userinfobot to get your Chat ID.\n" +
                        "4. Enter credentials below and test connection.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Credentials Section
            Text("API Credentials", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = config.botToken,
                onValueChange = { viewModel.updateConfig(botToken = it) },
                label = { Text("Bot Token") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = config.chatId,
                onValueChange = { viewModel.updateConfig(chatId = it) },
                label = { Text("Target Chat ID") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Save & Test Action
            Button(
                onClick = { viewModel.testConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && config.botToken.isNotBlank() && config.chatId.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save & Test")
                }
            }

            testStatus?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notification Toggles
            Text("Notification Toggles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Forward SMS") },
                supportingContent = { Text("Forward live incoming and outgoing SMS") },
                trailingContent = {
                    Switch(
                        checked = config.isSmsForwardingEnabled,
                        onCheckedChange = { viewModel.updateConfig(smsEnabled = it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Forward Call Logs") },
                supportingContent = { Text("Forward calls (incoming, outgoing, missed)") },
                trailingContent = {
                    Switch(
                        checked = config.isCallForwardingEnabled,
                        onCheckedChange = { viewModel.updateConfig(callEnabled = it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Silent Notifications") },
                supportingContent = { Text("Send to Telegram without sound or vibration") },
                trailingContent = {
                    Switch(
                        checked = config.silentNotifications,
                        onCheckedChange = { viewModel.updateConfig(silentNotifications = it) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Message Appearance
            Text("Message Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.messageFormatStyle == MessageFormatStyle.DETAILED,
                    onClick = { viewModel.updateConfig(messageFormatStyle = MessageFormatStyle.DETAILED) },
                    label = { Text("Detailed Card") },
                    leadingIcon = {
                        if (config.messageFormatStyle == MessageFormatStyle.DETAILED) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
                FilterChip(
                    selected = config.messageFormatStyle == MessageFormatStyle.COMPACT,
                    onClick = { viewModel.updateConfig(messageFormatStyle = MessageFormatStyle.COMPACT) },
                    label = { Text("Compact (1-Line)") },
                    leadingIcon = {
                        if (config.messageFormatStyle == MessageFormatStyle.COMPACT) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // History Sync Policy
            Text("Background Sync Scope", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Controls which pending/historical items get individual Telegram messages. Choose 'Real-Time Only' to avoid cluttering your chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    HistorySyncMode.REALTIME_ONLY to "Real-Time Only (Recommended)",
                    HistorySyncMode.LAST_24_HOURS to "Last 24 Hours",
                    HistorySyncMode.LAST_7_DAYS to "Last 7 Days",
                    HistorySyncMode.ALL_TIME to "All Time (Spam warning)"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = config.historySyncMode == mode,
                            onClick = { viewModel.updateConfig(historySyncMode = mode) }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Archive & Single Document Export Section
            Text("Full Archive Backup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Packages all local SMS and call history into a single clean report file and sends it to Telegram as 1 attached document with zero chat spam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.exportHistoryToTelegram() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && config.botToken.isNotBlank() && config.chatId.isNotBlank()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting Backup Document...")
                } else {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export History as Document to Telegram")
                }
            }

            exportStatus?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
