package com.netflow.predict.ui.screens.splash

import android.content.Context
import android.net.VpnService
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.netflow.predict.data.repository.SettingsRepository
import com.netflow.predict.ui.components.ShieldLogoAnimated
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    /**
     * Resolve the start destination.
     *
     * VPN permission is checked via the real Android API
     * (VpnService.prepare() returns null when already granted).
     * We also sync that result back to DataStore so future reads stay accurate.
     */
    suspend fun resolveDestination(): SplashDestination {
        val isFirstRun = settingsRepo.isFirstRun.first()
        if (isFirstRun) return SplashDestination.ONBOARDING

        // Check the real Android VPN permission — never rely solely on our stored flag.
        val vpnAlreadyPrepared = VpnService.prepare(context) == null
        if (vpnAlreadyPrepared) {
            // Sync DataStore so the flag reflects reality.
            settingsRepo.setVpnPermissionGranted(true)
        }

        return if (vpnAlreadyPrepared) SplashDestination.HOME
               else SplashDestination.PERMISSIONS
    }
}

enum class SplashDestination { ONBOARDING, PERMISSIONS, HOME }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    // Animate: logo scales in, text fades in, then navigate
    val logoScale by animateFloatAsState(
        targetValue    = 1f,
        animationSpec  = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label          = "logo_scale"
    )
    var textAlpha by remember { mutableFloatStateOf(0f) }
    val animatedTextAlpha by animateFloatAsState(
        targetValue   = textAlpha,
        animationSpec = tween(600),
        label         = "text_alpha"
    )

    LaunchedEffect(Unit) {
        delay(300)
        textAlpha = 1f
        delay(1000)
        when (viewModel.resolveDestination()) {
            SplashDestination.ONBOARDING  -> onNavigateToOnboarding()
            SplashDestination.PERMISSIONS -> onNavigateToPermissions()
            SplashDestination.HOME        -> onNavigateToHome()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ShieldLogoAnimated(
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(animatedTextAlpha)
            ) {
                Text(
                    text  = "NetFlow",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text      = "See every connection. Predict every risk.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}
