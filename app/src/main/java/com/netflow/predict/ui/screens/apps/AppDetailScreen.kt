package com.netflow.predict.ui.screens.apps

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netflow.predict.data.model.*
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.ui.components.*
import com.netflow.predict.ui.screens.home.formatBytes
import com.netflow.predict.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    private val _app    = MutableStateFlow<AppNetworkInfo?>(null)
    val app: StateFlow<AppNetworkInfo?> = _app

    private val _domains = MutableStateFlow<List<DomainInfo>>(emptyList())
    val domains: StateFlow<List<DomainInfo>> = _domains

    fun load(packageName: String) {
        viewModelScope.launch {
            trafficRepo.getApps().collect { apps ->
                _app.value = apps.firstOrNull { it.packageName == packageName }
            }
        }
        viewModelScope.launch {
            trafficRepo.getAppDomains(packageName).collect { _domains.value = it }
        }
    }

    fun updateStatus(pkg: String, status: AppMonitorStatus) {
        _app.value = _app.value?.copy(monitorStatus = status)
        viewModelScope.launch {
            trafficRepo.updateAppMonitorStatus(pkg, status)
        }
    }

    fun updateRules(rules: AppRules) {
        val pkg = _app.value?.packageName ?: return
        _app.value = _app.value?.copy(rules = rules)
        viewModelScope.launch {
            trafficRepo.updateAppRules(pkg, rules)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onNavigateToConnectionDetail: (flowId: String, domain: String) -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val app     by viewModel.app.collectAsState()
    val domains by viewModel.domains.collectAsState()

    LaunchedEffect(packageName) { viewModel.load(packageName) }

    val tabs   = listOf("Overview", "Domains", "Timeline", "Rules")
    val pager  = rememberPagerState(pageCount = { tabs.size })
    val scope  = rememberCoroutineScope()
    var showStatusMenu by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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

            // ── App header ────────────────────────────────────────────────────
            if (app != null) {
                AppDetailHeader(
                    app            = app!!,
                    onStatusChange = { status ->
                        if (status == AppMonitorStatus.BLOCKED) showBlockDialog = true
                        else viewModel.updateStatus(packageName, status)
                    }
                )
            }

            // ── Tab row ───────────────────────────────────────────────────────
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick  = { scope.launch { pager.animateScrollToPage(i) } },
                        text     = { Text(title) }
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            HorizontalPager(
                state    = pager,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> OverviewTab(app = app)
                    1 -> DomainsTab(domains = domains, onDomainClick = { dom ->
                            onNavigateToConnectionDetail("d_${dom.domain}", dom.domain)
                         })
                    2 -> TimelineTab(onConnectionClick = { flowId, domain ->
                            onNavigateToConnectionDetail(flowId, domain)
                         })
                    3 -> RulesTab(
                            rules    = app?.rules ?: AppRules(),
                            onUpdate = { viewModel.updateRules(it) }
                         )
                }
            }
        }
    }

    // Block confirmation dialog
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text("Block all traffic for ${app?.appName}?") },
            text  = { Text("This will prevent ${app?.appName} from accessing the network until you change this setting.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStatus(packageName, AppMonitorStatus.BLOCKED)
                        showBlockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun AppDetailHeader(
    app: AppNetworkInfo,
    onStatusChange: (AppMonitorStatus) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(app.appName.take(2).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text(app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Status chip with dropdown
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(when (app.monitorStatus) {
                            AppMonitorStatus.MONITORED -> PrimaryContainer
                            AppMonitorStatus.IGNORED   -> SurfaceVariant
                            AppMonitorStatus.BLOCKED   -> ErrorContainer
                        })
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(app.monitorStatus.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (app.monitorStatus) {
                            AppMonitorStatus.MONITORED -> OnPrimaryContainer
                            AppMonitorStatus.IGNORED   -> OnSurfaceVariant
                            AppMonitorStatus.BLOCKED   -> OnErrorContainer
                        })
                    Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded        = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppMonitorStatus.values().forEach { s ->
                        DropdownMenuItem(
                            text    = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { onStatusChange(s); menuExpanded = false }
                        )
                    }
                }
            }
        }
    }
}

// ── Overview tab ─────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(app: AppNetworkInfo?) {
    if (app == null) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); return }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // 7-day bar chart card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape  = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Data Usage — Last 7 Days",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)

                    WeeklyRiskChart(
                        riskLevels = app.weeklyDataBytes.map { bytes ->
                            when {
                                bytes > 50_000_000 -> RiskLevel.HIGH
                                bytes > 20_000_000 -> RiskLevel.MEDIUM
                                else               -> RiskLevel.LOW
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )

                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text("Total this week: ${formatBytes(app.weeklyDataBytes.sum())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item {
            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Domains"  to "${app.domainCount}",
                    "Requests" to "${app.requestCountToday}",
                    "Risk"     to app.riskLevel.name.lowercase().replaceFirstChar { it.uppercase() }
                ).forEach { (label, value) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape  = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            // AI prediction card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, riskColor(app.riskLevel).copy(alpha = 0.4f)),
                shape  = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Assessment",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Warning, null,
                            tint = riskColor(app.riskLevel), modifier = Modifier.size(18.dp))
                        Text("Risk: ${app.riskLevel.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.titleSmall,
                            color = riskColor(app.riskLevel))
                    }
                    Text(app.predictionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ── Domains tab ───────────────────────────────────────────────────────────────

@Composable
private fun DomainsTab(
    domains: List<DomainInfo>,
    onDomainClick: (DomainInfo) -> Unit
) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(domains) { domain ->
            DomainRow(domain = domain, onClick = { onDomainClick(domain) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun DomainRow(domain: DomainInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(domain.domain,
                style = MaterialTheme.typography.titleSmall,
                color = if (domain.riskLevel == RiskLevel.HIGH) ErrorColor
                        else MaterialTheme.colorScheme.onBackground)
            Text("Last seen ${formatRelativeTime(domain.lastSeen)} · ${domain.requestCount} requests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
            Text(formatBytes(domain.totalBytesSent + domain.totalBytesReceived) + " total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CategoryChip(domain.category)
    }
}

@Composable
private fun CategoryChip(category: DomainCategory) {
    val (bg, text) = when (category) {
        DomainCategory.CDN       -> PrimaryContainer to OnPrimaryContainer
        DomainCategory.ADS       -> WarningContainer to OnWarning
        DomainCategory.TRACKING  -> SecondaryContainer to OnSecondaryContainer
        DomainCategory.SUSPICIOUS-> ErrorContainer to OnErrorContainer
        DomainCategory.TRUSTED   -> TertiaryContainer to OnTertiaryContainer
        DomainCategory.UNKNOWN   -> SurfaceVariant to OnSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(category.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall, color = text)
    }
}

// ── Timeline tab ─────────────────────────────────────────────────────────────

@Composable
private fun TimelineTab(
    onConnectionClick: (flowId: String, domain: String) -> Unit
) {
    // Static stub — real impl pulls from Room
    val fakeEntries = listOf(
        "2:47 PM" to "graph.facebook.com",
        "2:31 PM" to "api.whatsapp.net",
        "1:15 PM" to "doubleclick.net",
        "11:02 AM" to "maps.googleapis.com"
    )
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Text("Today", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        items(fakeEntries) { (time, domain) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConnectionClick("flow_$domain", domain) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Timeline dot + line
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary))
                }
                Text(time, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(60.dp))
                Icon(Icons.Filled.ArrowUpward, null,
                    tint = Primary, modifier = Modifier.size(14.dp))
                Text(domain, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

// ── Rules tab ─────────────────────────────────────────────────────────────────

@Composable
private fun RulesTab(rules: AppRules, onUpdate: (AppRules) -> Unit) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            RuleToggleItem(
                title       = "Block background traffic",
                description = "When your screen is off, this app cannot send or receive data.",
                checked     = rules.blockBackground,
                onToggle    = { onUpdate(rules.copy(blockBackground = it)) }
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            RuleToggleItem(
                title       = "Block known trackers",
                description = "Block domains classified as trackers or advertising.",
                checked     = rules.blockTrackers,
                onToggle    = { onUpdate(rules.copy(blockTrackers = it)) }
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            RuleToggleItem(
                title       = "Always allow on Wi-Fi",
                description = "Traffic is allowed on Wi-Fi even if other rules restrict it.",
                checked     = rules.alwaysAllowOnWifi,
                onToggle    = { onUpdate(rules.copy(alwaysAllowOnWifi = it)) }
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            TextButton(onClick = {}) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add custom rule")
            }
        }
    }
}

@Composable
private fun RuleToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatRelativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000        -> "just now"
        diff < 3_600_000     -> "${diff / 60_000} min ago"
        diff < 86_400_000    -> "${diff / 3_600_000}h ago"
        else                 -> "${diff / 86_400_000}d ago"
    }
}
