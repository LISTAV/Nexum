package com.kairav.nexum

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.kairav.nexum.ui.components.DonationDialog
import com.kairav.nexum.ui.navigation.*
import com.kairav.nexum.ui.screens.*
import com.kairav.nexum.ui.theme.NexumTheme
import com.kairav.nexum.ui.viewmodels.*
import com.kairav.nexum.utils.SmsRoleManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import androidx.work.*
import com.kairav.nexum.workers.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * MainActivity is the core entry point for the Nexum app.
 * It manages permissions, schedules background sync, and hosts the navigation container.
 * 
 * Tech Stack:
 * - Jetpack Compose (UI)
 * - Navigation 3 (State-driven navigation)
 * - Hilt (Dependency Injection)
 * - Material 3 (Design system)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var smsRepository: com.kairav.nexum.data.repositories.SmsRepository

    @javax.inject.Inject
    lateinit var callLogRepository: com.kairav.nexum.data.repositories.CallLogRepository

    private val currentIntentState = mutableStateOf<Intent?>(null)

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntentState.value = intent
        
        // Requirement 5: Ensure responsive UI design (Edge-to-Edge is foundational)
        enableEdgeToEdge()

        val smsRoleManager = SmsRoleManager(this)
        
        // Schedule periodic background sync
        scheduleSync()

        setContent {
            NexumTheme {
                // Requirement 6: State management logic for permissions
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.SEND_SMS,
                        android.Manifest.permission.RECEIVE_SMS,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                )

                // When permissions are granted, auto-sync SMS & Call logs from system
                LaunchedEffect(permissionsState.allPermissionsGranted) {
                    if (permissionsState.allPermissionsGranted) {
                        smsRepository.fetchSmsFromSystem()
                        callLogRepository.fetchCallsFromSystem()
                    }
                }

                // Decision: Only show main content if all critical permissions are granted
                if (permissionsState.allPermissionsGranted) {
                    MainContent(smsRoleManager)
                } else {
                    PermissionRequestScreen { permissionsState.launchMultiplePermissionRequest() }
                }
            }
        }
    }

    /**
     * Main UI container hosting the Bottom Navigation and Screen Content.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(smsRoleManager: SmsRoleManager) {
        // Navigation state setup for Nav 3
        val topLevelRoutes = remember {
            setOf(
                NavRoute.Conversations, 
                NavRoute.SentSms,
                NavRoute.CallHistory, 
                NavRoute.ScheduledSms
            )
        }
        val navigationState = rememberNavigationState(
            startRoute = NavRoute.Conversations,
            topLevelRoutes = topLevelRoutes
        )
        val navigator = remember { Navigator(navigationState) }
        
        val telegramViewModel: TelegramSettingsViewModel = hiltViewModel()
        val telegramConfig by telegramViewModel.config.collectAsState()

        val donationViewModel: DonationViewModel = hiltViewModel()
        val shouldShowDonationPrompt by donationViewModel.shouldShowPrompt.collectAsState()

        // Handle incoming intent (e.g. notifications, contacts app "Message" button, SMS links)
        val activeIntent by currentIntentState
        LaunchedEffect(activeIntent) {
            val targetIntent = activeIntent
            if (targetIntent != null) {
                val address = resolveAddressFromIntent(targetIntent)
                if (!address.isNullOrBlank()) {
                    navigator.navigate(NavRoute.Chat(address))
                    currentIntentState.value = null
                }
            }
        }

        // --- Initial Sync & Non-intrusive Donation check Logic ---
        // Requirement 3: Trigger an initial sync/import when the app is first opened
        val syncViewModel: SyncDiagnosticsViewModel = hiltViewModel()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            syncViewModel.syncNow()
            donationViewModel.checkEligibility()
        }
        
        // Reactive tracking for Default SMS App status and resume checks
        var isDefaultSmsApp by remember { mutableStateOf(smsRoleManager.isDefaultSmsApp()) }
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isDefaultSmsApp = smsRoleManager.isDefaultSmsApp()
                    donationViewModel.checkEligibility()
                    syncViewModel.syncNow()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // ActivityResult launcher for requesting Default SMS App role
        val roleLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { 
            isDefaultSmsApp = smsRoleManager.isDefaultSmsApp()
        }

        // --- Destination Mapping ---
        // Requirement 3: Use Navigation Compose (Nav 3) for switching tabs and screens.
        val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            // Conversations Tab
            entry<NavRoute.Conversations> {
                ConversationsScreen(
                    viewModel = hiltViewModel(),
                    isDefaultSmsApp = isDefaultSmsApp,
                    onRequestDefaultApp = { smsRoleManager.requestSmsRole(roleLauncher) },
                    onThreadClick = { address -> navigator.navigate(NavRoute.Chat(address)) },
                    onComposeClick = { navigator.navigate(NavRoute.ComposeSms) }
                )
            }
            // Sent SMS Tab
            entry<NavRoute.SentSms> {
                SentSmsScreen(
                    viewModel = hiltViewModel(),
                    onMessageClick = { address -> navigator.navigate(NavRoute.Chat(address)) },
                    onComposeClick = { navigator.navigate(NavRoute.ComposeSms) }
                )
            }
            // Call History Tab
            entry<NavRoute.CallHistory> {
                CallHistoryScreen(viewModel = hiltViewModel())
            }
            // Scheduled SMS Tab
            entry<NavRoute.ScheduledSms> {
                ScheduledSmsScreen(viewModel = hiltViewModel())
            }
            // Chat Detail Screen (Child route)
            entry<NavRoute.Chat> { key: NavRoute.Chat ->
                val viewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key) }
                )
                ChatScreen(viewModel = viewModel, onNavigateBack = { navigator.goBack() })
            }
            // Settings Screen
            entry<NavRoute.Settings> {
                SettingsScreen(
                    onNavigateBack = { navigator.goBack() },
                    onNavigateToTelegram = { navigator.navigate(NavRoute.TelegramSettings) },
                    onNavigateToDonation = { navigator.navigate(NavRoute.Donation) }
                )
            }
            // Compose SMS Screen
            entry<NavRoute.ComposeSms> {
                ComposeSmsScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navigator.goBack() },
                    onNavigateToChat = { address ->
                        navigator.goBack()
                        navigator.navigate(NavRoute.Chat(address))
                    }
                )
            }
            // Telegram Settings Screen
            entry<NavRoute.TelegramSettings> {
                TelegramSettingsScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navigator.goBack() }
                )
            }
            // Support / Donation Screen
            entry<NavRoute.Donation> {
                DonationScreen(
                    viewModel = donationViewModel,
                    onNavigateBack = { navigator.goBack() }
                )
            }
        }

        // --- Main Scaffold ---
        // Requirement 1: Implement a Scaffold with Bottom Navigation
        Scaffold(
            topBar = {
                val currentRoute = navigationState.topLevelRoute
                if (currentRoute in topLevelRoutes) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.main_icon),
                                    contentDescription = "Nexum Logo",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Text(
                                    text = "Nexum",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                )
                            }
                            IconButton(
                                onClick = { navigator.navigate(NavRoute.Settings) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                val currentRoute = navigationState.topLevelRoute
                // Only show bottom bar for top-level destinations
                if (currentRoute in topLevelRoutes) {
                    Column {
                        if (telegramConfig.isConfigured && (telegramConfig.isSmsForwardingEnabled || telegramConfig.isCallForwardingEnabled)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚡ Automated Forwarding Activated",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            TopLevelDestination.entries.forEach { destination ->
                                val isSelected = currentRoute == destination.route
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { navigator.navigate(destination.route) },
                                    icon = { 
                                        Icon(
                                            imageVector = destination.icon, 
                                            contentDescription = destination.label 
                                        ) 
                                    },
                                    label = { Text(destination.label) }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // --- Navigation Content Display ---
            NavDisplay(
                modifier = Modifier.padding(innerPadding),
                entries = navigationState.toEntries(entryProvider),
                onBack = { navigator.goBack() }
            )
        }

        // --- Non-intrusive Donation / Support Dialog ---
        if (shouldShowDonationPrompt) {
            DonationDialog(
                onDismiss = { donationViewModel.onDismissPrompt() },
                onDonated = { donationViewModel.onDonationConfirmed() }
            )
        }
    }

    /**
     * Fallback screen for requesting necessary permissions.
     */
    @Composable
    fun PermissionRequestScreen(onRequest: () -> Unit) {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nexum requires SMS and Call Log access to function as your local communications hub.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permissions")
                }
            }
        }
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NexumSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }

    private fun resolveAddressFromIntent(targetIntent: Intent?): String? {
        if (targetIntent == null) return null
        var addr = targetIntent.getStringExtra("EXTRA_CONTACT_ADDRESS")
        if (addr.isNullOrBlank()) {
            val uri = targetIntent.data
            if (uri != null) {
                val scheme = uri.scheme?.lowercase()
                if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                    val ssp = uri.schemeSpecificPart
                    if (!ssp.isNullOrBlank()) {
                        val queryIndex = ssp.indexOf('?')
                        addr = if (queryIndex != -1) ssp.substring(0, queryIndex) else ssp
                    }
                }
            }
        }
        if (addr.isNullOrBlank()) {
            addr = targetIntent.getStringExtra("address")
                ?: targetIntent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: targetIntent.getStringExtra("recipient")
                ?: targetIntent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.firstOrNull()
        }
        return addr?.trim()?.takeIf { it.isNotBlank() }
    }
}

enum class TopLevelDestination(
    val route: NavRoute,
    val icon: ImageVector,
    val label: String
) {
    Conversations(NavRoute.Conversations, Icons.AutoMirrored.Filled.Chat, "Chats"),
    SentSms(NavRoute.SentSms, Icons.AutoMirrored.Filled.Send, "Sent"),
    CallHistory(NavRoute.CallHistory, Icons.Default.History, "Calls"),
    ScheduledSms(NavRoute.ScheduledSms, Icons.Default.Schedule, "Schedule")
}
