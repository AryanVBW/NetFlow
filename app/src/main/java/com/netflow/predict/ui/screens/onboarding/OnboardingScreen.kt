package com.netflow.predict.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
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
import com.netflow.predict.ui.components.ShieldLogoAnimated
import com.netflow.predict.ui.theme.*
import kotlinx.coroutines.launch

// ── Data ─────────────────────────────────────────────────────────────────────

data class OnboardingSlide(
    val icon: ImageVector,
    val headline: String,
    val body: String,
    val accentColor: Color
)

private val slides = listOf(
    OnboardingSlide(
        icon        = Icons.Filled.Wifi,
        headline    = "See all network activity on your phone.",
        body        = "NetFlow Predict monitors every connection — from social apps to background services — so nothing happens without your knowledge.",
        accentColor = Primary
    ),
    OnboardingSlide(
        icon        = Icons.Filled.Psychology,
        headline    = "Get AI predictions about risky apps and domains.",
        body        = "Our on-device model learns your device's normal behavior and flags unusual patterns — before a data leak or privacy breach occurs.",
        accentColor = Secondary
    ),
    OnboardingSlide(
        icon        = Icons.Filled.Lock,
        headline    = "Your data never leaves your device.",
        body        = "All traffic analysis and AI predictions run entirely on-device. NetFlow Predict never uploads your connection data to any server. Ever.",
        accentColor = Tertiary
    ),
    OnboardingSlide(
        icon        = Icons.Filled.Shield,
        headline    = "Ready to take control?",
        body        = "Set up takes about 30 seconds. We'll ask for a VPN permission — it stays local, on your device.",
        accentColor = Primary
    )
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onSetupProtection: () -> Unit,
    onBasicMode: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope      = rememberCoroutineScope()
    var showBasicModeSheet by remember { mutableStateOf(false) }

    val isLastSlide = pagerState.currentPage == slides.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !isLastSlide) {
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(slides.lastIndex) }
                }) { Text("Skip") }
            }
            Spacer(Modifier.weight(1f))
            if (!isLastSlide) {
                Text(
                    text  = "${pagerState.currentPage + 1} / ${slides.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Pager ─────────────────────────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPage(
                slide      = slides[page],
                isLast     = page == slides.lastIndex,
                onNext     = {
                    if (page < slides.lastIndex)
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                },
                onSetup    = onSetupProtection,
                onBasic    = { showBasicModeSheet = true }
            )
        }

        // ── Page indicator ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(slides.size) { i ->
                val isSelected = pagerState.currentPage == i
                val width by animateDpAsState(
                    targetValue   = if (isSelected) 24.dp else 8.dp,
                    animationSpec = tween(300),
                    label         = "dot_width"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }

    // ── Basic mode bottom sheet ───────────────────────────────────────────────
    if (showBasicModeSheet) {
        BasicModeSheet(
            onContinueBasic   = { showBasicModeSheet = false; onBasicMode() },
            onSetupProtection = { showBasicModeSheet = false; onSetupProtection() },
            onDismiss         = { showBasicModeSheet = false }
        )
    }
}

// ── Page ──────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPage(
    slide: OnboardingSlide,
    isLast: Boolean,
    onNext: () -> Unit,
    onSetup: () -> Unit,
    onBasic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(slide.accentColor.copy(alpha = 0.12f))
        ) {
            if (isLast) {
                ShieldLogoAnimated(modifier = Modifier.size(96.dp))
            } else {
                Icon(
                    imageVector         = slide.icon,
                    contentDescription  = null,
                    tint                = slide.accentColor,
                    modifier            = Modifier.size(72.dp)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text      = slide.headline,
            style     = MaterialTheme.typography.headlineMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text      = slide.body,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(56.dp))

        if (isLast) {
            Button(
                onClick  = onSetup,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Set Up Protection") }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick  = onBasic,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Continue in Basic Mode",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Button(
                onClick  = onNext,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Next") }
        }
    }
}

// ── Basic mode sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicModeSheet(
    onContinueBasic: () -> Unit,
    onSetupProtection: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Basic Mode limits some features",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Without VPN access, NetFlow Predict cannot monitor live traffic or make real-time predictions. You can enable full protection at any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick  = onContinueBasic,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Warning, contentColor = Color.Black)
            ) { Text("Continue in Basic Mode") }
            OutlinedButton(
                onClick  = onSetupProtection,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Set Up Protection") }
        }
    }
}
