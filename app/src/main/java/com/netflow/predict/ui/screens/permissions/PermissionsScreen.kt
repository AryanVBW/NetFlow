package com.netflow.predict.ui.screens.permissions

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netflow.predict.data.repository.SettingsRepository
import com.netflow.predict.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Permission step state ─────────────────────────────────────────────────────

enum class PermissionState { PENDING, GRANTED, DENIED }

data class PermissionStep(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val isRequired: Boolean,
    val description: String,
    var state: PermissionState = PermissionState.PENDING
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    /** Persist VPN granted state so Splash can route correctly next launch. */
    fun onVpnGranted() {
        viewModelScope.launch {
            settingsRepo.setVpnPermissionGranted(true)
        }
    }

    /** True if the OS considers VPN permission already prepared. */
    fun isVpnAlreadyGranted(): Boolean = VpnService.prepare(context) == null

    /** True if POST_NOTIFICATIONS is already granted (Android 13+). */
    fun isNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true  // not required below Android 13
    }

    /** True if Usage Access (PACKAGE_USAGE_STATS) is allowed. */
    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onProtectionStarted: () -> Unit,
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var vpnState   by remember { mutableStateOf(PermissionState.PENDING) }
    var notifState by remember { mutableStateOf(PermissionState.PENDING) }
    var usageState by remember { mutableStateOf(PermissionState.PENDING) }

    var showVpnSheet     by remember { mutableStateOf(false) }
    var isStartingVpn    by remember { mutableStateOf(false) }

    // Pre-populate states based on real OS permission status on first composition.
    LaunchedEffect(Unit) {
        if (viewModel.isVpnAlreadyGranted())     vpnState   = PermissionState.GRANTED
        if (viewModel.isNotificationGranted())   notifState = PermissionState.GRANTED
        if (viewModel.isUsageAccessGranted())    usageState = PermissionState.GRANTED
    }

    // VPN permission launcher
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnState = PermissionState.GRANTED
            viewModel.onVpnGranted()   // persist so Splash routes to Home next launch
        } else {
            vpnState = PermissionState.DENIED
        }
    }

    // Notification permission launcher (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifState = if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    val grantedCount = listOf(vpnState, notifState, usageState)
        .count { it == PermissionState.GRANTED }
    val progress     = grantedCount / 3f

    Scaffold(
        topBar = {
            TopAppBar(
                title  = {},
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress         = { progress },
                modifier         = Modifier.fillMaxWidth().height(3.dp),
                color            = MaterialTheme.colorScheme.primary,
                trackColor       = MaterialTheme.colorScheme.surfaceVariant
            )

            Column(
                modifier            = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Set Up Protection",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Complete the steps below to enable full monitoring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                // VPN step
                PermissionCard(
                    icon        = Icons.Filled.VpnKey,
                    title       = "VPN Access",
                    isRequired  = true,
                    description = "Lets NetFlow analyze traffic locally. No data leaves your device.",
                    state       = vpnState,
                    onGrant     = { showVpnSheet = true }
                )

                // Notification step
                PermissionCard(
                    icon        = Icons.Filled.Notifications,
                    title       = "Notifications",
                    isRequired  = false,
                    description = "Get alerts when risky activity is detected.",
                    state       = notifState,
                    onGrant     = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifState = PermissionState.GRANTED
                        }
                    }
                )

                // Usage access step
                PermissionCard(
                    icon        = Icons.Filled.BarChart,
                    title       = "Usage Access",
                    isRequired  = false,
                    description = "Shows per-app data stats in the Apps view.",
                    state       = usageState,
                    onGrant     = {
                        // Opens system Usage Access settings
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        )
                        usageState = PermissionState.GRANTED // optimistic
                    }
                )

                // Privacy note card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier            = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment   = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                        Text(
                            "All traffic is analyzed on this device only. Nothing is sent to our servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Start button
                Button(
                    onClick  = {
                        isStartingVpn = true
                        onProtectionStarted()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled  = vpnState == PermissionState.GRANTED && !isStartingVpn
                ) {
                    if (isStartingVpn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color    = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isStartingVpn) "Starting…" else "Start Protection")
                }
            }
        }
    }

    // VPN explanation bottom sheet
    if (showVpnSheet) {
        VpnExplanationSheet(
            onAllow   = {
                showVpnSheet = false
                val intent = VpnService.prepare(context)
                if (intent != null) vpnLauncher.launch(intent)
                else vpnState = PermissionState.GRANTED  // already prepared
            },
            onDismiss = { showVpnSheet = false }
        )
    }
}

// ── Permission card ───────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    isRequired: Boolean,
    description: String,
    state: PermissionState,
    onGrant: () -> Unit
) {
    val borderColor = when (state) {
        PermissionState.GRANTED -> Tertiary.copy(alpha = 0.5f)
        PermissionState.DENIED  -> ErrorColor.copy(alpha = 0.5f)
        else                    -> MaterialTheme.colorScheme.outline
    }
    val leftBarColor = when (state) {
        PermissionState.GRANTED -> Tertiary
        PermissionState.DENIED  -> ErrorColor
        else                    -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
    ) {
        // Left color bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(120.dp)
                .background(leftBarColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
        )

        Column(modifier = Modifier.padding(16.dp).weight(1f)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (isRequired) "Required" else "Optional",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRequired) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Status icon
                AnimatedContent(targetState = state, label = "perm_state") { s ->
                    when (s) {
                        PermissionState.GRANTED ->
                            Icon(Icons.Filled.CheckCircle, "Granted",
                                tint = Tertiary, modifier = Modifier.size(22.dp))
                        PermissionState.DENIED  ->
                            Icon(Icons.Filled.Cancel, "Denied",
                                tint = ErrorColor, modifier = Modifier.size(22.dp))
                        else ->
                            Icon(Icons.Filled.RadioButtonUnchecked, "Pending",
                                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface)

            Spacer(Modifier.height(12.dp))

            when (state) {
                PermissionState.GRANTED ->
                    Text("Granted", style = MaterialTheme.typography.labelMedium, color = Tertiary)
                PermissionState.DENIED  ->
                    TextButton(onClick = onGrant, contentPadding = PaddingValues(0.dp)) {
                        Text("Open Settings", color = ErrorColor)
                    }
                else ->
                    OutlinedButton(onClick = onGrant,
                        modifier = Modifier.align(Alignment.End)) { Text("Grant") }
            }
        }
    }
}

// ── VPN explanation sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VpnExplanationSheet(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Why does NetFlow need VPN access?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Android's VPN API lets us intercept traffic on your device — entirely locally. No data is routed to a remote server. Your internet works normally.\n\nYou can stop the VPN at any time from the Home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
                Text("Allow VPN Access")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Not Now")
            }
        }
    }
}
