package com.netflow.predict.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

// ── Supported languages ───────────────────────────────────────────────────────

private data class LanguageOption(val code: String, val displayName: String, val nativeName: String)

private val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("system", "System default", "System default"),
    LanguageOption("en",     "English",        "English"),
    LanguageOption("hi",     "Hindi",          "हिन्दी"),
    LanguageOption("es",     "Spanish",        "Español"),
    LanguageOption("fr",     "French",         "Français"),
    LanguageOption("de",     "German",         "Deutsch"),
    LanguageOption("pt",     "Portuguese",     "Português"),
    LanguageOption("ja",     "Japanese",       "日本語"),
    LanguageOption("zh",     "Chinese",        "中文"),
    LanguageOption("ar",     "Arabic",         "العربية"),
)

// ── Default PIN (hashed as plain string for demo; upgrade to PBKDF2 in prod) ──
private const val DEFAULT_PIN = "1234"

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val trafficRepo: TrafficRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, AppSettings())

    // ── Theme ──────────────────────────────────────────────────────────────
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setTheme(mode) }
    }

    // ── Language ───────────────────────────────────────────────────────────
    fun setLanguage(lang: String) {
        viewModelScope.launch { settingsRepo.setLanguage(lang) }
    }

    // ── Monitoring ────────────────────────────────────────────────────────
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

    // ── Developer / Auth-gated ────────────────────────────────────────────
    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDeveloperMode(enabled) }
    }

    // ── Data ──────────────────────────────────────────────────────────────
    fun clearLogs() {
        viewModelScope.launch { trafficRepo.clearAllData() }
    }

    // ── PIN authentication ────────────────────────────────────────────────
    /**
     * Validates the PIN against the stored value.
     * In production this should use a secure hash (PBKDF2/bcrypt).
     * For this demo the default PIN is [DEFAULT_PIN].
     */
    fun validatePin(enteredPin: String): Boolean = enteredPin == DEFAULT_PIN
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToPermissions: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    // Dialog visibility states
    var showClearLogsDialog   by remember { mutableStateOf(false) }
    var showExportSheet       by remember { mutableStateOf(false) }
    var showThemeDialog       by remember { mutableStateOf(false) }
    var showRetentionDialog   by remember { mutableStateOf(false) }
    var showLanguageDialog    by remember { mutableStateOf(false) }

    // Auth guard state — holds the action to execute after successful PIN entry
    var pendingAuthAction: (() -> Unit)? by remember { mutableStateOf(null) }
    var showAuthDialog        by remember { mutableStateOf(false) }
    var authError             by remember { mutableStateOf(false) }

    // Helper: request auth before executing sensitive action
    fun requireAuth(action: () -> Unit) {
        pendingAuthAction = action
        authError = false
        showAuthDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
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

            // ── Appearance ────────────────────────────────────────────────
            item { SettingsSectionHeader("Appearance") }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.DarkMode,
                    title    = "Theme",
                    subtitle = settings.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick  = { showThemeDialog = true }
                )
            }

            item {
                val langDisplay = SUPPORTED_LANGUAGES
                    .firstOrNull { it.code == settings.language }?.displayName ?: "System default"
                SettingsClickableRow(
                    icon     = Icons.Filled.Language,
                    title    = "Language",
                    subtitle = langDisplay,
                    onClick  = { showLanguageDialog = true }
                )
            }

            item { SettingsDivider() }

            // ── Monitoring ────────────────────────────────────────────────
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

            // ── Privacy & AI ──────────────────────────────────────────────
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
                    icon       = Icons.Filled.DeleteSweep,
                    title      = "Clear all logs",
                    subtitle   = "Permanently delete all captured traffic data.",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick    = {
                        requireAuth { showClearLogsDialog = true }
                    }
                )
            }

            item {
                SettingsClickableRow(
                    icon     = Icons.Filled.FileDownload,
                    title    = "Export logs",
                    subtitle = "Save captured traffic to a file on this device.",
                    onClick  = {
                        requireAuth { showExportSheet = true }
                    }
                )
            }

            item { SettingsDivider() }

            // ── VPN ───────────────────────────────────────────────────────
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

            // ── Developer ─────────────────────────────────────────────────
            item { SettingsSectionHeader("Developer") }

            item {
                SettingsToggleRow(
                    icon        = Icons.Filled.Code,
                    title       = "Developer mode",
                    subtitle    = "Show extra debug info in Live Traffic screen.",
                    checked     = settings.developerMode,
                    onToggle    = { newValue ->
                        // Turning ON requires auth; turning OFF is always allowed
                        if (newValue) {
                            requireAuth { viewModel.setDeveloperMode(true) }
                        } else {
                            viewModel.setDeveloperMode(false)
                        }
                    }
                )
            }

            item { SettingsDivider() }

            // ── Security ──────────────────────────────────────────────────
            item { SettingsSectionHeader("Security") }

            item {
                SettingsInfoRow(
                    icon     = Icons.Filled.Pin,
                    title    = "Settings PIN",
                    subtitle = "Default PIN: 1234. Protects export, data clear & developer mode.",
                )
            }

            item { SettingsDivider() }

            // ── About ─────────────────────────────────────────────────────
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
                    subtitle = "All data stays on your device.",
                    onClick  = onNavigateToPrivacyPolicy
                )
            }
        }
    }

    // ── Auth PIN guard dialog ─────────────────────────────────────────────────
    if (showAuthDialog) {
        AuthPinDialog(
            hasError  = authError,
            onConfirm = { pin ->
                if (viewModel.validatePin(pin)) {
                    showAuthDialog = false
                    pendingAuthAction?.invoke()
                    pendingAuthAction = null
                } else {
                    authError = true
                }
            },
            onDismiss = {
                showAuthDialog    = false
                pendingAuthAction = null
                authError         = false
            }
        )
    }

    // ── Theme dialog ──────────────────────────────────────────────────────────
    if (showThemeDialog) {
        SelectionDialog(
            title      = "Choose theme",
            onDismiss  = { showThemeDialog = false }
        ) {
            ThemeMode.entries.forEach { mode ->
                SelectionRow(
                    label     = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected  = settings.themeMode == mode,
                    onClick   = {
                        viewModel.setTheme(mode)
                        showThemeDialog = false
                    }
                )
            }
        }
    }

    // ── Language dialog ───────────────────────────────────────────────────────
    if (showLanguageDialog) {
        SelectionDialog(
            title     = "Language",
            onDismiss = { showLanguageDialog = false }
        ) {
            SUPPORTED_LANGUAGES.forEach { lang ->
                SelectionRow(
                    label     = "${lang.displayName}  ·  ${lang.nativeName}",
                    selected  = settings.language == lang.code,
                    onClick   = {
                        viewModel.setLanguage(lang.code)
                        showLanguageDialog = false
                    }
                )
            }
        }
    }

    // ── Retention dialog ──────────────────────────────────────────────────────
    if (showRetentionDialog) {
        SelectionDialog(
            title     = "Log retention",
            onDismiss = { showRetentionDialog = false }
        ) {
            listOf(7, 14, 30, 60, 90).forEach { days ->
                SelectionRow(
                    label    = "$days days",
                    selected = settings.retentionDays == days,
                    onClick  = {
                        viewModel.setRetentionDays(days)
                        showRetentionDialog = false
                    }
                )
            }
        }
    }

    // ── Clear logs confirmation ────────────────────────────────────────────────
    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            icon  = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear all logs?") },
            text  = {
                Text("All captured traffic data will be permanently deleted. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearLogs(); showClearLogsDialog = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
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

// ── Auth PIN dialog ───────────────────────────────────────────────────────────

@Composable
private fun AuthPinDialog(
    hasError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Enter PIN to continue") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This action requires your Settings PIN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value         = pin,
                    onValueChange = { if (it.length <= 6) { pin = it } },
                    label         = { Text("PIN") },
                    singleLine    = true,
                    isError       = hasError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = {
                        if (hasError) Text("Incorrect PIN. Try again.", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Generic selection dialog ──────────────────────────────────────────────────

@Composable
private fun SelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column(content = content)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ── Export bottom sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportLogsSheet(onDismiss: () -> Unit) {
    val sheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Text(
                "Export logs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Choose a format to export your captured traffic data:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            ExportFormat.entries.forEach { format ->
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
                    verticalAlignment     = Alignment.CenterVertically,
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
                onClick  = onDismiss,   // real impl: triggers file write + share intent
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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint     = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor)
            if (subtitle.isNotBlank()) {
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsInfoRow(
    icon:     ImageVector,
    title:    String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint     = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
