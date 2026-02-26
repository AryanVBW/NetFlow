package com.netflow.predict

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.netflow.predict.data.model.AppSettings
import com.netflow.predict.data.repository.SettingsRepository
import com.netflow.predict.ui.navigation.AppNavigation
import com.netflow.predict.ui.theme.NetFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity entry point.
 *
 * Edge-to-edge display is enabled so that the status bar and
 * navigation bar are drawn over the app's background.
 * The theme mode is collected from DataStore settings and passed
 * to [NetFlowTheme] so that the user's Dark / Light / System
 * preference is respected across the entire app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for first run consent
        checkConsent()

        setContent {
            // Collect settings from DataStore as Compose State.
            // This ensures recomposition whenever the user changes theme in Settings.
            val settings by settingsRepository.settings
                .collectAsState(initial = AppSettings())

            NetFlowTheme(themeMode = settings.themeMode) {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }

    private fun checkConsent() {
        val prefs = getSharedPreferences("netflow_prefs", Context.MODE_PRIVATE)
        val hasConsented = prefs.getBoolean("has_consented_v1", false)

        if (!hasConsented) {
            AlertDialog.Builder(this)
                .setTitle("Privacy & Data Usage")
                .setMessage(
                    "NetFlow Predict uses a local VPN to monitor your network traffic. " +
                    "All data is processed locally on your device and is never uploaded to our servers.\n\n" +
                    "By continuing, you agree to our Privacy Policy and Terms of Service."
                )
                .setPositiveButton("I Agree") { _, _ ->
                    prefs.edit().putBoolean("has_consented_v1", true).apply()
                }
                .setNegativeButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }
}
