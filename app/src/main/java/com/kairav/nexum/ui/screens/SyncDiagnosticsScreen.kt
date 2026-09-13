package com.kairav.nexum.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairav.nexum.ui.viewmodels.SyncDiagnosticsViewModel

/**
 * SyncDiagnosticsScreen provides a dashboard for monitoring sync status.
 * 
 * Requirement 1: Dashboard showing counts of PENDING vs SYNCED records and a "Sync Now" button.
 *
 * @param viewModel The state holder for sync metrics and operations.
 */
@Composable
fun SyncDiagnosticsScreen(viewModel: SyncDiagnosticsViewModel) {
    // Observe sync counts from ViewModel
    val counts by viewModel.syncCounts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header Section ---
        Text(
            text = "Sync Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // --- Metrics Grid ---
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item { StatusCard("Pending SMS", counts.pendingSms, MaterialTheme.colorScheme.tertiaryContainer) }
            item { StatusCard("Synced SMS", counts.syncedSms, MaterialTheme.colorScheme.primaryContainer) }
            item { StatusCard("Pending Calls", counts.pendingCalls, MaterialTheme.colorScheme.tertiaryContainer) }
            item { StatusCard("Synced Calls", counts.syncedCalls, MaterialTheme.colorScheme.primaryContainer) }
            item { StatusCard("Failed SMS", counts.failedSms, MaterialTheme.colorScheme.errorContainer) }
            item { StatusCard("Failed Calls", counts.failedCalls, MaterialTheme.colorScheme.errorContainer) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Action Section ---
        Button(
            onClick = { viewModel.syncNow() },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Sync, contentDescription = "Manual sync icon")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sync Now", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Small card showing a count for a specific status.
 * 
 * @param label Description of the metric.
 * @param count The numeric value to display.
 * @param containerColor Background color for the card.
 */
@Composable
fun StatusCard(label: String, count: Int, containerColor: androidx.compose.ui.graphics.Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(), 
                style = MaterialTheme.typography.displaySmall,
                color = contentColorFor(containerColor)
            )
            Text(
                text = label, 
                style = MaterialTheme.typography.labelMedium,
                color = contentColorFor(containerColor).copy(alpha = 0.7f)
            )
        }
    }
}
