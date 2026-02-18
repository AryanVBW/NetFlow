package com.netflow.predict.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.navigation.NavController
import com.netflow.predict.data.model.*
import com.netflow.predict.data.repository.SettingsRepository
import com.netflow.predict.data.repository.TrafficRepository
import com.netflow.predict.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettings())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setTheme(mode) }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepo.setRetentionDays(days) }
    }

    fun setDnsOnlyMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDnsOnlyMode(enabled) }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAiEnabled(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setNotificationsEnabled(enabled) }
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDeveloperMode(enabled) }
    }

    fun clearLogs() {
        viewModelScope.launch {
            trafficRepo.clearAllData()
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    var showClearLogsDialog   by remember { mutableStateOf(false) }
    var showExportSheet       by remember { mutableStateOf(false) }
    var showThemeDialog       by remember { mutableStateOf(false) }
    var showRetentionDialog   by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── Appearance ────────────────────────────────────────────────────
            item { SettingsSectionHeader("Appearance") }

            item {
                SettingsClickableRow(
                    icon        = Icons.Filled.DarkMode,
                    title       = "Theme",
                    subtitle    = settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick     = { showThemeDialog = true }
                )
            }

            item { SettingsDivider() }

            // ── Monitoring ────────────────────────────────────────────────────
            item { SettingsSectionHeader("Monitoring") }

            item {
                SettingsToggleRow(
                    icon     = Icons.Filled.Dns,
                    title    = "DNS-only mode",
                    subtitle = "Only capture DNS queries, not full traffic. Lower battery impact.",
                    checked  = settings.dnsOnlyMode,
                    onToggle = viewModel::setDnsOnlyMode
                )
            }

            item {
                SettingsToggleRow(
                    icon     = Icons.Filled.Notifications,
                    title    = "Notifications",
                    subtitle = "Show alerts for suspicious activity.",
                    checked  = settings.notificationsEnabled,
                    onToggle = viewModel::setNotificationsEnabled
                )
            }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.History,
                    title    = "Log retention",
                    subtitle = "${settings.retentionDays} days",
                    onClick  = { showRetentionDialog = true }
                )
            }

            item { SettingsDivider() }

            // ── Privacy & AI ──────────────────────────────────────────────────
            item { SettingsSectionHeader("Privacy & AI") }

            item {
                SettingsToggleRow(
                    icon     = Icons.Filled.Psychology,
                    title    = "AI predictions",
                    subtitle = "On-device model. No data leaves your phone.",
                    checked  = settings.aiEnabled,
                    onToggle = viewModel::setAiEnabled
                )
            }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.DeleteSweep,
                    title    = "Clear all logs",
                    subtitle = "Permanently delete all captured traffic data.",
                    onClick  = { showClearLogsDialog = true },
                    titleColor = ErrorColor
                )
            }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.FileDownload,
                    title    = "Export logs",
                    subtitle = "Save captured traffic to a file on this device.",
                    onClick  = { showExportSheet = true }
                )
            }

            item { SettingsDivider() }

            // ── VPN ───────────────────────────────────────────────────────────
            item { SettingsSectionHeader("VPN") }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.VpnKey,
                    title    = "VPN permissions",
                    subtitle = "Review or re-configure VPN access.",
                    onClick  = onNavigateToPermissions
                )
            }

            item { SettingsDivider() }

            // ── Developer ─────────────────────────────────────────────────────
            item { SettingsSectionHeader("Developer") }

            item {
                SettingsToggleRow(
                    icon     = Icons.Filled.Code,
                    title    = "Developer mode",
                    subtitle = "Show extra debug info in Live Traffic screen.",
                    checked  = settings.developerMode,
                    onToggle = viewModel::setDeveloperMode
                )
            }

            item { SettingsDivider() }

            // ── About ─────────────────────────────────────────────────────────
            item { SettingsSectionHeader("About") }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.Info,
                    title    = "Version",
                    subtitle = "1.0.0 (1)"
                )
            }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.PrivacyTip,
                    title    = "Privacy policy",
                    subtitle = "All data stays on your device."
                )
            }
        }
    }

    // ── Theme dialog ──────────────────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose theme") },
            text  = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTheme(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick  = {
                                    viewModel.setTheme(mode)
                                    showThemeDialog = false
                                }
                            )
                            Text(
                                mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Done") }
            }
        )
    }

    // ── Retention dialog ──────────────────────────────────────────────────────
    if (showRetentionDialog) {
        val options = listOf(7, 14, 30, 60, 90)
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text("Log retention") },
            text  = {
                Column {
                    options.forEach { days ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setRetentionDays(days)
                                    showRetentionDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = settings.retentionDays == days,
                                onClick  = {
                                    viewModel.setRetentionDays(days)
                                    showRetentionDialog = false
                                }
                            )
                            Text(
                                "$days days",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetentionDialog = false }) { Text("Done") }
            }
        )
    }

    // ── Clear logs confirmation ────────────────────────────────────────────────
    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            icon  = { Icon(Icons.Filled.DeleteForever, null, tint = ErrorColor) },
            title = { Text("Clear all logs?") },
            text  = {
                Text("All captured traffic data will be permanently deleted. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearLogs(); showClearLogsDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Export sheet ──────────────────────────────────────────────────────────
    if (showExportSheet) {
        ExportLogsSheet(onDismiss = { showExportSheet = false })
    }
}

// ── Export bottom sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportLogsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }

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
            Text("Export logs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)

            Text("Choose a format to export your captured traffic data:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)

            ExportFormat.values().forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedFormat == format)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedFormat = format }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedFormat == format,
                        onClick  = { selectedFormat = format }
                    )
                    Column {
                        Text(
                            format.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            when (format) {
                                ExportFormat.CSV  -> "Spreadsheet-compatible plain text"
                                ExportFormat.PCAP -> "Packet capture — open with Wireshark"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick  = onDismiss,  // stub: real impl triggers file write + share intent
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export as ${selectedFormat.name}")
            }
        }
    }
}

// ── Reusable setting row components ──────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        color     = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsToggleRow(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SettingsClickableRow(
    icon:       ImageVector,
    title:      String,
    subtitle:   String = "",
    onClick:    () -> Unit = {},
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
    }
}
