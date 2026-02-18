package com.netflow.predict.ui.screens.predictions

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.netflow.predict.data.model.*
import com.netflow.predict.ui.components.*
import com.netflow.predict.ui.theme.*
import com.netflow.predict.ui.viewmodel.PredictionsViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionsScreen(
    onBack: () -> Unit,
    onNavigateToApp: (packageName: String) -> Unit,
    onNavigateToLive: () -> Unit,
    viewModel: PredictionsViewModel = hiltViewModel()
) {
    val prediction   by viewModel.prediction.collectAsState()
    val alerts       by viewModel.visibleAlerts.collectAsState()

    val tabs  = listOf("Forecast", "Alerts History")
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Predictions & Alerts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            val unread = alerts.count { !it.isRead }
                            if (unread > 0) Badge { Text(unread.toString()) }
                        }
                    ) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Notifications, "Alerts")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick  = { scope.launch { pager.animateScrollToPage(i) } },
                        text     = { Text(title) }
                    )
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ForecastTab(
                            prediction     = prediction,
                            onViewApp      = onNavigateToApp,
                            onViewDomain   = { onNavigateToLive() }
                         )
                    1 -> AlertsHistoryTab(
                            alerts        = alerts,
                            onDismiss     = { viewModel.dismissAlert(it) },
                            onAlertClick  = { alert ->
                                alert.packageName?.let { onNavigateToApp(it) }
                            }
                         )
                }
            }
        }
    }
}

// ── Forecast tab ──────────────────────────────────────────────────────────────

@Composable
private fun ForecastTab(
    prediction: PredictionResult?,
    onViewApp: (String) -> Unit,
    onViewDomain: (String) -> Unit
) {
    if (prediction == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading predictions…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Weekly risk chart
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape  = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("7-Day Risk Forecast",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)

                    WeeklyRiskChart(
                        riskLevels = prediction.weeklyRisk,
                        modifier   = Modifier.fillMaxWidth().height(80.dp)
                    )

                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach { day ->
                            Text(day, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text("Future days shown at reduced opacity (predicted).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            // Apps to watch
            SectionCard(title = "Apps to Watch") {
                prediction.appsToWatch.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(entry.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground)
                                RiskBadge(entry.riskLevel)
                            }
                            Text(entry.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(
                            onClick = { onViewApp(entry.packageName) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("View App") }
                    }
                }
            }
        }

        item {
            // Domains to watch
            SectionCard(title = "Domains to Watch") {
                prediction.domainsToWatch.forEachIndexed { i, entry ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(entry.domain,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (entry.riskLevel == RiskLevel.HIGH) ErrorColor
                                            else MaterialTheme.colorScheme.onBackground)
                                RiskBadge(entry.riskLevel)
                            }
                            Text(entry.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(
                            onClick = { onViewDomain(entry.domain) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("View") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape  = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ── Alerts history tab ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsHistoryTab(
    alerts: List<NetworkAlert>,
    onDismiss: (String) -> Unit,
    onAlertClick: (NetworkAlert) -> Unit
) {
    if (alerts.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.CheckCircle, null,
                tint = Tertiary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("No alerts", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("NetFlow Predict hasn't detected any issues yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text("Today",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        items(alerts, key = { it.id }) { alert ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != Settled) { onDismiss(alert.id); true } else false
                }
            )
            SwipeToDismissBox(
                state            = dismissState,
                backgroundContent = {
                    Box(
                        modifier         = Modifier.fillMaxSize()
                            .background(ErrorColor.copy(alpha = 0.15f))
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(Icons.Filled.Delete, "Dismiss",
                            tint = ErrorColor, modifier = Modifier.size(24.dp))
                    }
                }
            ) {
                AlertRow(alert = alert, onClick = { onAlertClick(alert) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun AlertRow(alert: NetworkAlert, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val (icon, color) = alertIconAndColor(alert.type)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.15f))
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(alert.title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text(alert.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
            Text(formatAlertTime(alert.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun alertIconAndColor(type: AlertType): Pair<ImageVector, androidx.compose.ui.graphics.Color> =
    when (type) {
        AlertType.SUSPICIOUS_DOMAIN       -> Icons.Filled.Warning    to Warning
        AlertType.DATA_SPIKE              -> Icons.Filled.TrendingUp to Primary
        AlertType.NEW_APP                 -> Icons.Filled.Smartphone to Secondary
        AlertType.BLOCK_RULE_TRIGGERED    -> Icons.Filled.Block      to ErrorColor
        AlertType.RISK_LEVEL_CHANGED      -> Icons.Filled.Assessment to Warning
    }

private fun formatAlertTime(ms: Long): String {
    val instant   = Instant.ofEpochMilli(ms)
    val formatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
