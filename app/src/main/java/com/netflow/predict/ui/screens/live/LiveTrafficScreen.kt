package com.netflow.predict.ui.screens.live

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netflow.predict.data.model.*
import com.netflow.predict.ui.components.*
import com.netflow.predict.ui.theme.*
import com.netflow.predict.ui.viewmodel.TrafficFilter
import com.netflow.predict.ui.viewmodel.LiveTrafficViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrafficScreen(
    onNavigateToDetail: (flowId: String, domain: String, pkg: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LiveTrafficViewModel = hiltViewModel()
) {
    val flows       by viewModel.flows.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("Live Traffic") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleCapture() }) {
                        Icon(
                            imageVector        = if (isCapturing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isCapturing) "Pause capture" else "Resume capture",
                            tint               = if (isCapturing) Tertiary else Warning
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showFilterSheet = true },
                icon    = { Icon(Icons.Filled.FilterList, "Filter") },
                text    = { Text("Filter") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh    = { /* re-query snapshot */ },
            state        = pullState,
            modifier     = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Capture status banner
                CaptureBanner(isCapturing = isCapturing)

                // Filter chips
                FilterChipsRow(
                    activeFilter = activeFilter,
                    onSelect     = { viewModel.setFilter(it) }
                )

                // Connection list
                if (flows.isEmpty()) {
                    EmptyTrafficState()
                } else {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(flows, key = { it.id }) { flow ->
                            AnimatedVisibility(
                                visible = true,
                                enter   = fadeIn() + slideInVertically()
                            ) {
                                TrafficFlowRow(
                                    flow    = flow,
                                    onClick = {
                                        onNavigateToDetail(flow.id, flow.domain, flow.appPackage)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Advanced filter sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            onDismiss = { showFilterSheet = false }
        )
    }
}

// ── Capture banner ────────────────────────────────────────────────────────────

@Composable
private fun CaptureBanner(isCapturing: Boolean) {
    AnimatedContent(
        targetState = isCapturing,
        label       = "banner"
    ) { capturing ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (capturing) Tertiary.copy(alpha = 0.12f)
                    else Warning.copy(alpha = 0.12f)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (capturing) Tertiary else Warning)
            )
            Text(
                text  = if (capturing) "Live capture: ON"
                        else "Capture paused — showing snapshot",
                style = MaterialTheme.typography.labelMedium,
                color = if (capturing) Tertiary else Warning
            )
        }
    }
}

// ── Filter chips ──────────────────────────────────────────────────────────────

@Composable
private fun FilterChipsRow(
    activeFilter: TrafficFilter,
    onSelect: (TrafficFilter) -> Unit
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrafficFilter.entries.forEach { filter ->
            FilterChip(
                selected = activeFilter == filter,
                onClick  = { onSelect(filter) },
                label    = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

// ── Flow row ──────────────────────────────────────────────────────────────────

@Composable
private fun TrafficFlowRow(
    flow: TrafficFlow,
    onClick: () -> Unit
) {
    val isRisky    = flow.riskLevel == RiskLevel.HIGH
    val rowBgColor = if (isRisky) ErrorColor.copy(alpha = 0.05f)
                    else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(rowBgColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App icon placeholder (real impl uses Coil AsyncImage)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text  = flow.appName.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Middle: app + domain
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text     = flow.appName,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isRisky) {
                    Icon(Icons.Filled.Warning, null,
                        tint = ErrorColor, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text     = flow.domain,
                style    = MaterialTheme.typography.bodyMedium,
                color    = if (isRisky) ErrorColor.copy(alpha = 0.8f)
                           else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProtocolChip(flow.protocol)
                Text(":${flow.port}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBytesPerSec(flow.bytesPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Right: direction + sparkline
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (dirIcon, dirColor) = when (flow.direction) {
                Direction.OUTBOUND      -> Icons.Filled.ArrowUpward to Primary
                Direction.INBOUND       -> Icons.Filled.ArrowDownward to Secondary
                Direction.BIDIRECTIONAL -> Icons.Filled.SwapVert to MaterialTheme.colorScheme.onSurface
            }
            Icon(dirIcon, null, tint = dirColor, modifier = Modifier.size(16.dp))
            Sparkline(data = flow.sparklineData, color = dirColor)
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun ProtocolChip(protocol: Protocol) {
    val (bg, text) = when (protocol) {
        Protocol.TCP     -> PrimaryContainer to OnPrimaryContainer
        Protocol.UDP     -> SecondaryContainer to OnSecondaryContainer
        Protocol.DNS     -> WarningContainer to OnWarning
        Protocol.UNKNOWN -> SurfaceVariant to OnSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(protocol.name, style = MaterialTheme.typography.labelSmall, color = text)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyTrafficState() {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.SignalWifiOff, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("No active connections",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Your device appears to be offline, or all apps are quiet right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Filter bottom sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Filter & Search",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)
            OutlinedTextField(
                value         = "",
                onValueChange = {},
                label         = { Text("Domain / IP") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )
            Text("Protocol", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Any", "TCP", "UDP", "DNS").forEach { p ->
                    FilterChip(
                        selected = p == "Any",
                        onClick  = {},
                        label    = { Text(p) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Apply Filters")
                }
            }
        }
    }
}

// ── Util ──────────────────────────────────────────────────────────────────────

private fun formatBytesPerSec(bps: Long): String = when {
    bps >= 1_048_576 -> "%.1f MB/s".format(bps / 1_048_576.0)
    bps >= 1_024     -> "%.1f KB/s".format(bps / 1_024.0)
    else             -> "$bps B/s"
}
