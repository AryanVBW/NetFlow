package com.netflow.predict.ui.screens.connection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netflow.predict.data.model.*
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.ui.components.riskColor
import com.netflow.predict.ui.screens.home.formatBytes
import com.netflow.predict.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ConnectionDetailViewModel @Inject constructor(
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    private val _domain = MutableStateFlow<DomainInfo?>(null)
    val domain: StateFlow<DomainInfo?> = _domain

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked

    private val _isTrusted = MutableStateFlow(false)
    val isTrusted: StateFlow<Boolean> = _isTrusted

    private var loadedDomainName: String = ""

    fun load(flowId: String, domainName: String, appPackage: String) {
        loadedDomainName = domainName
        viewModelScope.launch {
            // First try domain-level lookup (across all apps)
            trafficRepo.getDomainInfo(domainName).collect { domainInfo ->
                if (domainInfo != null) {
                    _domain.value = domainInfo
                    _isBlocked.value = domainInfo.isBlocked
                    _isTrusted.value = domainInfo.isTrusted
                } else {
                    // Fallback: try app-specific domain list
                    trafficRepo.getAppDomains(appPackage).first().let { domains ->
                        val found = domains.firstOrNull { it.domain == domainName }
                        if (found != null) {
                            _domain.value = found
                            _isBlocked.value = found.isBlocked
                            _isTrusted.value = found.isTrusted
                        } else {
                            // Domain not in DB yet — create a minimal placeholder
                            _domain.value = DomainInfo(
                                domain             = domainName,
                                ipAddress          = "",
                                port               = 443,
                                category           = DomainCategory.UNKNOWN,
                                riskLevel          = RiskLevel.UNKNOWN,
                                requestCount       = 0,
                                totalBytesSent     = 0L,
                                totalBytesReceived = 0L,
                                firstSeen          = System.currentTimeMillis(),
                                lastSeen           = System.currentTimeMillis(),
                                securityAssessment = "No data available for this domain yet."
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleBlock() {
        val newBlocked = !_isBlocked.value
        _isBlocked.value = newBlocked
        viewModelScope.launch {
            trafficRepo.setDomainBlocked(loadedDomainName, newBlocked)
        }
    }

    fun toggleTrust() {
        val newTrusted = !_isTrusted.value
        _isTrusted.value = newTrusted
        viewModelScope.launch {
            trafficRepo.setDomainTrusted(loadedDomainName, newTrusted)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailScreen(
    flowId: String,
    domain: String,
    appPackage: String,
    onBack: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    viewModel: ConnectionDetailViewModel = hiltViewModel()
) {
    val domainInfo by viewModel.domain.collectAsState()
    val isBlocked  by viewModel.isBlocked.collectAsState()
    val isTrusted  by viewModel.isTrusted.collectAsState()

    var showBlockConfirm by remember { mutableStateOf(false) }
    var showTrustConfirm by remember { mutableStateOf(false) }
    var showReportSheet  by remember { mutableStateOf(false) }

    LaunchedEffect(flowId, domain, appPackage) {
        viewModel.load(flowId, domain, appPackage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Connection Detail",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (domainInfo == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        val info = domainInfo ?: return@Scaffold

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Risk banner ───────────────────────────────────────────────────
            item {
                RiskBannerCard(info)
            }

            // ── App identity ──────────────────────────────────────────────────
            item {
                AppIdentityCard(appPackage = appPackage, onAppClick = onNavigateToAppDetail)
            }

            // ── Connection info ────────────────────────────────────────────────
            item {
                ConnectionInfoCard(info)
            }

            // ── Geo / IP card ─────────────────────────────────────────────────
            item {
                GeoIpCard(info)
            }

            // ── Security assessment ───────────────────────────────────────────
            item {
                SecurityAssessmentCard(info)
            }

            // ── Action buttons ────────────────────────────────────────────────
            item {
                ActionButtonsRow(
                    isBlocked    = isBlocked,
                    isTrusted    = isTrusted,
                    onBlock      = { showBlockConfirm = true },
                    onTrust      = { showTrustConfirm = true },
                    onReport     = { showReportSheet  = true }
                )
            }
        }
    }

    // ── Block confirmation dialog ─────────────────────────────────────────────
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            icon  = { Icon(Icons.Filled.Block, null, tint = ErrorColor) },
            title = { Text(if (isBlocked) "Unblock this domain?" else "Block this domain?") },
            text  = {
                Text(
                    if (isBlocked)
                        "All apps will be allowed to connect to $domain again."
                    else
                        "All network connections to $domain will be dropped, for all apps."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.toggleBlock(); showBlockConfirm = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) MaterialTheme.colorScheme.primary else ErrorColor
                    )
                ) {
                    Text(if (isBlocked) "Unblock" else "Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // ── Trust confirmation dialog ─────────────────────────────────────────────
    if (showTrustConfirm) {
        AlertDialog(
            onDismissRequest = { showTrustConfirm = false },
            icon  = { Icon(Icons.Filled.VerifiedUser, null, tint = Tertiary) },
            title = { Text(if (isTrusted) "Remove trust for $domain?" else "Mark $domain as trusted?") },
            text  = {
                Text(
                    if (isTrusted)
                        "Alerts for this domain will resume."
                    else
                        "No alerts will be generated for connections to this domain."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.toggleTrust(); showTrustConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Tertiary)
                ) {
                    Text(if (isTrusted) "Remove trust" else "Mark trusted")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrustConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // ── Report bottom sheet ───────────────────────────────────────────────────
    if (showReportSheet) {
        ReportBottomSheet(
            domain    = domain,
            onDismiss = { showReportSheet = false }
        )
    }
}

// ── Risk banner card ──────────────────────────────────────────────────────────

@Composable
private fun RiskBannerCard(info: DomainInfo) {
    val risk   = info.riskLevel
    val color  = riskColor(risk)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        shape  = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = when (risk) {
                        RiskLevel.HIGH    -> Icons.Filled.Warning
                        RiskLevel.MEDIUM  -> Icons.Filled.Info
                        RiskLevel.LOW     -> Icons.Filled.CheckCircle
                        RiskLevel.UNKNOWN -> Icons.Filled.HelpOutline
                    },
                    contentDescription = null,
                    tint   = color,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.domain,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "${risk.name.lowercase().replaceFirstChar { it.uppercase() }} risk · ${info.category.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
            // category chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    risk.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}

// ── App identity card ─────────────────────────────────────────────────────────

@Composable
private fun AppIdentityCard(appPackage: String, onAppClick: (String) -> Unit) {
    val displayName = appPackage.substringAfterLast(".")
        .replaceFirstChar { it.uppercase() }

    SectionCard(title = "Source App") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    displayName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground)
                Text(appPackage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onAppClick(appPackage) }) {
                Text("View app")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Connection info card ──────────────────────────────────────────────────────

@Composable
private fun ConnectionInfoCard(info: DomainInfo) {
    SectionCard(title = "Connection") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("IP Address",   info.ipAddress)
            InfoRow("Port",         info.port.toString())
            InfoRow("Protocol",     "TCP/TLS")
            InfoRow("Requests",     info.requestCount.toString())
            InfoRow("Data sent",    formatBytes(info.totalBytesSent))
            InfoRow("Data received", formatBytes(info.totalBytesReceived))
            InfoRow("First seen",   formatRelativeTime(info.firstSeen))
            InfoRow("Last seen",    formatRelativeTime(info.lastSeen))
        }
    }
}

// ── Geo / IP card ─────────────────────────────────────────────────────────────

@Composable
private fun GeoIpCard(info: DomainInfo) {
    SectionCard(title = "Geo & Network") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (info.geoCountry != null) {
                InfoRow("Country", info.geoCountry)
            }
            if (info.geoRegion != null) {
                InfoRow("Region", info.geoRegion)
            }
            if (info.asn != null) {
                InfoRow("ASN", info.asn)
            }
            if (info.asnOrg != null) {
                InfoRow("Organization", info.asnOrg)
            }
            if (info.geoCountry == null && info.asn == null) {
                Text("No geo data available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Security assessment card ──────────────────────────────────────────────────

@Composable
private fun SecurityAssessmentCard(info: DomainInfo) {
    SectionCard(title = "Security Assessment") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint     = riskColor(info.riskLevel),
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Text(
                    info.securityAssessment.ifBlank { "No assessment data available." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── Action buttons row ────────────────────────────────────────────────────────

@Composable
private fun ActionButtonsRow(
    isBlocked: Boolean,
    isTrusted: Boolean,
    onBlock:   () -> Unit,
    onTrust:   () -> Unit,
    onReport:  () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Block / Unblock
        Button(
            onClick = onBlock,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBlocked) MaterialTheme.colorScheme.surfaceVariant else ErrorColor
            )
        ) {
            Icon(
                if (isBlocked) Icons.Filled.LockOpen else Icons.Filled.Block,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isBlocked) "Unblock domain" else "Block domain")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Mark trusted
            OutlinedButton(
                onClick  = onTrust,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isTrusted) Tertiary else MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isTrusted) Tertiary else MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(
                    if (isTrusted) Icons.Filled.VerifiedUser else Icons.Filled.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isTrusted) "Trusted" else "Trust")
            }

            // Report
            OutlinedButton(
                onClick  = onReport,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Report")
            }
        }
    }
}

// ── Report bottom sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportBottomSheet(domain: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedReason by remember { mutableStateOf<String?>(null) }
    val reasons = listOf("False positive", "Malware / phishing", "Unwanted tracker", "Data harvesting", "Other")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Report Domain",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text("Why are you reporting $domain?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)

            reasons.forEach { reason ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick  = { selectedReason = reason }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Button(
                onClick  = onDismiss,
                enabled  = selectedReason != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit report")
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape  = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun formatRelativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000     -> "just now"
        diff < 3_600_000  -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else              -> "${diff / 86_400_000}d ago"
    }
}
