package com.netflow.predict.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.netflow.predict.data.model.*
import com.netflow.predict.ui.components.*
import com.netflow.predict.ui.navigation.NetFlowBottomBar
import com.netflow.predict.ui.navigation.Screen
import com.netflow.predict.ui.theme.*
import com.netflow.predict.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLive: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToPredictions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBlockRules: () -> Unit,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val vpnState     by viewModel.vpnState.collectAsState()
    val summary      by viewModel.trafficSummary.collectAsState()
    val prediction   by viewModel.prediction.collectAsState()
    val isLoading    by viewModel.isLoading.collectAsState()
    var showStopDialog by remember { mutableStateOf(false) }
    val snackbarHost   = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("NetFlow Predict",
                        style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(Screen.Permissions.route) }) {
                        Icon(
                            imageVector        = if (vpnState.status == VpnStatus.CONNECTED)
                                                    Icons.Filled.Shield else Icons.Filled.ShieldMoon,
                            contentDescription = "VPN status",
                            tint               = if (vpnState.status == VpnStatus.CONNECTED)
                                                    MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NetFlowBottomBar(
                navController  = navController,
                unreadAlerts   = prediction?.appsToWatch?.size ?: 0
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Protection status card ────────────────────────────────────────
            ProtectionCard(
                vpnState       = vpnState,
                onToggleOn     = { viewModel.startVpn() },
                onToggleOff    = { showStopDialog = true }
            )

            // ── Traffic summary ───────────────────────────────────────────────
            if (isLoading) {
                TrafficSummaryShimmer()
            } else {
                TrafficSummaryCard(
                    summary  = summary,
                    onClick  = onNavigateToLive
                )
            }

            // ── Risk forecast ─────────────────────────────────────────────────
            if (isLoading) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(120.dp))
            } else {
                RiskForecastCard(
                    prediction = prediction,
                    onClick    = onNavigateToPredictions
                )
            }

            // ── Quick actions ─────────────────────────────────────────────────
            QuickActionsRow(
                onLiveTraffic  = onNavigateToLive,
                onAppsView     = onNavigateToApps,
                onBlockRules   = onNavigateToBlockRules,
                onExportLogs   = { /* open export sheet */ }
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    // Stop VPN confirmation dialog
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title   = { Text("Stop Protection?") },
            text    = { Text("Monitoring will pause and your traffic will no longer be analyzed until you turn it back on.") },
            confirmButton = {
                TextButton(
                    onClick = { showStopDialog = false; viewModel.stopVpn() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = ErrorColor)
                ) { Text("Turn Off") }
            },
            dismissButton = {
                Button(onClick = { showStopDialog = false }) {
                    Text("Keep Protection ON")
                }
            }
        )
    }
}

// ── Protection status card ────────────────────────────────────────────────────

@Composable
private fun ProtectionCard(
    vpnState: VpnState,
    onToggleOn: () -> Unit,
    onToggleOff: () -> Unit
) {
    val isOn    = vpnState.status == VpnStatus.CONNECTED
    val barColor = when (vpnState.status) {
        VpnStatus.CONNECTED    -> Tertiary
        VpnStatus.RECONNECTING -> Warning
        VpnStatus.DISCONNECTED -> ErrorColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Left color bar
        Box(modifier = Modifier.width(4.dp).height(80.dp).background(barColor))

        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Protection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Row(
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val dotColor = when (vpnState.status) {
                        VpnStatus.CONNECTED    -> Tertiary
                        VpnStatus.RECONNECTING -> Warning
                        VpnStatus.DISCONNECTED -> ErrorColor
                    }
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dotColor))
                    Text(
                        text  = when (vpnState.status) {
                            VpnStatus.CONNECTED    -> "VPN Connected"
                            VpnStatus.RECONNECTING -> "Reconnecting…"
                            VpnStatus.DISCONNECTED -> "VPN Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Switch(
                checked         = isOn,
                onCheckedChange = { checked -> if (checked) onToggleOn() else onToggleOff() },
                thumbContent    = if (isOn) ({
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(14.dp))
                }) else null
            )
        }
    }
}

// ── Traffic summary card ──────────────────────────────────────────────────────

@Composable
private fun TrafficSummaryCard(
    summary: TrafficSummary?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape   = RoundedCornerShape(16.dp),
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today's Traffic",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)

            if (summary == null) {
                Text("Monitoring started — data will appear shortly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                // Stat chips
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip(
                        icon  = Icons.Filled.ArrowUpward,
                        label = "Sent",
                        value = formatBytes(summary.totalSentBytes),
                        color = Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        icon  = Icons.Filled.ArrowDownward,
                        label = "Received",
                        value = formatBytes(summary.totalReceivedBytes),
                        color = Primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Area chart
                AreaChart(
                    dataPoints = summary.hourlyDataPoints,
                    modifier   = Modifier.fillMaxWidth().height(60.dp)
                )

                // X-axis labels
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("12AM", "6AM", "12PM", "6PM", "Now").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

// ── Risk forecast card ────────────────────────────────────────────────────────

@Composable
private fun RiskForecastCard(
    prediction: PredictionResult?,
    onClick: () -> Unit
) {
    if (prediction == null) {
        // Building baseline state
        Card(
            colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape   = RoundedCornerShape(16.dp),
            border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier            = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.HourglassTop, null,
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                Text("Building your baseline…",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center)
                Text("Predictions become available after 24 hours of monitoring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    val barColor = riskColor(prediction.deviceRiskLevel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.width(4.dp)
            .defaultMinSize(minHeight = 80.dp)
            .background(barColor))

        Column(
            modifier = Modifier.padding(16.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Risk Forecast",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = onClick) { Text("Details") }
            }

            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Warning, null,
                    tint = barColor, modifier = Modifier.size(18.dp))
                Text(
                    text = when (prediction.deviceRiskLevel) {
                        RiskLevel.HIGH    -> "High Risk"
                        RiskLevel.MEDIUM  -> "Medium Risk"
                        RiskLevel.LOW     -> "All Clear"
                        RiskLevel.UNKNOWN -> "Analyzing…"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = barColor
                )
            }

            Text(prediction.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)

            prediction.appsToWatch.take(2).forEach { app ->
                Text("• ${app.appName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Prediction based on 7-day history",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────────

@Composable
private fun QuickActionsRow(
    onLiveTraffic: () -> Unit,
    onAppsView: () -> Unit,
    onBlockRules: () -> Unit,
    onExportLogs: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Filled.Wifi,     "Live\nTraffic",  onLiveTraffic),
        Triple(Icons.Filled.Apps,     "Apps\nView",     onAppsView),
        Triple(Icons.Filled.Block,    "Block\nRules",   onBlockRules),
        Triple(Icons.Filled.Upload,   "Export\nLogs",   onExportLogs)
    )

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (icon, label, action) ->
            Card(
                onClick = action,
                modifier = Modifier.weight(1f),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier            = Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Text(label,
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Shimmer placeholder ───────────────────────────────────────────────────────

@Composable
private fun TrafficSummaryShimmer() {
    Card(
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape   = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.height(16.dp).fillMaxWidth(0.4f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerBox(Modifier.weight(1f).height(48.dp), cornerRadius = 12)
                ShimmerBox(Modifier.weight(1f).height(48.dp), cornerRadius = 12)
            }
            ShimmerBox(Modifier.fillMaxWidth().height(60.dp))
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}
