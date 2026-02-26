package com.netflow.predict.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netflow.predict.data.repository.SettingsRepository
import com.netflow.predict.ui.components.ShieldLogoAnimated
import com.netflow.predict.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Data ─────────────────────────────────────────────────────────────────────

data class OnboardingSlide(
    val icon: ImageVector,
    val headline: String,
    val body: String,
    val accentColor: Color,
    val gradientEnd: Color
)

private val slides = listOf(
    OnboardingSlide(
        icon        = Icons.Filled.Wifi,
        headline    = "See every connection on your phone",
        body        = "NetFlow monitors all traffic — from social apps to background services — so nothing happens without your knowledge.",
        accentColor = Primary,
        gradientEnd = PrimaryContainer
    ),
    OnboardingSlide(
        icon        = Icons.Filled.Psychology,
        headline    = "AI predictions for risky apps & domains",
        body        = "Our on-device model learns your device's normal behavior and flags unusual patterns before a data leak occurs.",
        accentColor = Secondary,
        gradientEnd = SecondaryContainer
    ),
    OnboardingSlide(
        icon        = Icons.Filled.Lock,
        headline    = "Your data never leaves your device",
        body        = "All traffic analysis and AI predictions run entirely on-device. NetFlow never uploads your connection data to any server.",
        accentColor = Tertiary,
        gradientEnd = TertiaryContainer
    ),
    OnboardingSlide(
        icon        = Icons.Filled.Shield,
        headline    = "Ready to take control?",
        body        = "Set up takes about 30 seconds. We'll ask for a VPN permission — it stays local, entirely on your device.",
        accentColor = Primary,
        gradientEnd = PrimaryContainer
    )
)

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepo.setFirstRun(false)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onSetupProtection: () -> Unit,
    onBasicMode: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope      = rememberCoroutineScope()
    var showBasicModeSheet by remember { mutableStateOf(false) }

    val isLastSlide = pagerState.currentPage == slides.lastIndex
    val currentSlide = slides[pagerState.currentPage]

    // Persist state and invoke callbacks
    val handleSetup = {
        viewModel.completeOnboarding()
        onSetupProtection()
    }
    val handleBasic = {
        viewModel.completeOnboarding()
        onBasicMode()
    }

    // Animate background gradient per page
    val bgGradientColor by animateColorAsState(
        targetValue   = currentSlide.accentColor.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 500),
        label         = "bg_gradient"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(bgGradientColor, MaterialTheme.colorScheme.background),
                    radius = 900f
                )
            )
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0f)) // blend base
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top status bar ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // App name / logo mark
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "NetFlow",
                        style     = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color     = MaterialTheme.colorScheme.primary
                    )
                }

                // Skip button (hidden on last slide)
                AnimatedVisibility(
                    visible = !isLastSlide,
                    enter   = fadeIn(),
                    exit    = fadeOut()
                ) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(slides.lastIndex) }
                        }
                    ) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Pager — fills remaining space minus bottom controls ────────
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPage(
                    slide  = slides[page],
                    isLast = page == slides.lastIndex
                )
            }

            // ── Page indicator dots ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(slides.size) { i ->
                    val isSelected = pagerState.currentPage == i
                    val widthDp by animateDpAsState(
                        targetValue   = if (isSelected) 28.dp else 8.dp,
                        animationSpec = tween(300),
                        label         = "dot_w"
                    )
                    val dotColor by animateColorAsState(
                        targetValue   = if (isSelected) currentSlide.accentColor
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        animationSpec = tween(300),
                        label         = "dot_c"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(widthDp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // ── Bottom CTA area ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                if (isLastSlide) {
                    // Final slide CTAs
                    Button(
                        onClick  = handleSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape  = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Shield, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Set Up Protection",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick  = { showBasicModeSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Continue in Basic Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                } else {
                    // Mid-slide Next button
                    Button(
                        onClick  = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape  = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentSlide.accentColor,
                            contentColor   = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            "Next",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                    }

                    // Page counter
                    Text(
                        "${pagerState.currentPage + 1} / ${slides.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ── Basic mode bottom sheet ───────────────────────────────────────────────
    if (showBasicModeSheet) {
        BasicModeSheet(
            onContinueBasic   = { showBasicModeSheet = false; handleBasic() },
            onSetupProtection = { showBasicModeSheet = false; handleSetup() },
            onDismiss         = { showBasicModeSheet = false }
        )
    }
}

// ── Individual slide page ─────────────────────────────────────────────────────

@Composable
private fun OnboardingPage(
    slide: OnboardingSlide,
    isLast: Boolean
) {
    // Entrance animation for the illustration
    val scale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label         = "icon_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Illustration container ────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .clip(RoundedCornerShape(48.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            slide.accentColor.copy(alpha = 0.22f),
                            slide.gradientEnd.copy(alpha = 0.06f)
                        )
                    )
                )
        ) {
            // Outer decorative ring
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(slide.accentColor.copy(alpha = 0.06f))
            )

            if (isLast) {
                ShieldLogoAnimated(modifier = Modifier.size(110.dp))
            } else {
                Icon(
                    imageVector        = slide.icon,
                    contentDescription = null,
                    tint               = slide.accentColor,
                    modifier           = Modifier.size(88.dp)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // ── Headline ──────────────────────────────────────────────────────
        Text(
            text      = slide.headline,
            style     = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // ── Body ──────────────────────────────────────────────────────────
        Text(
            text      = slide.body,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6
        )
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning icon
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint     = Warning,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Text(
                "Basic Mode limits some features",
                style     = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color     = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureLimitRow("Real-time traffic monitoring", limited = true)
                    FeatureLimitRow("AI threat predictions", limited = true)
                    FeatureLimitRow("App data usage stats", limited = false)
                    FeatureLimitRow("DNS query history", limited = false)
                }
            }

            Text(
                "You can enable full protection at any time in Settings.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            Button(
                onClick  = onSetupProtection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Shield, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Set Up Full Protection", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick  = onContinueBasic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(14.dp)
            ) {
                Text("Continue in Basic Mode")
            }
        }
    }
}

// ── Feature limit indicator ───────────────────────────────────────────────────

@Composable
private fun FeatureLimitRow(feature: String, limited: Boolean) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector        = if (limited) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint     = if (limited) MaterialTheme.colorScheme.error else Tertiary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            feature,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
