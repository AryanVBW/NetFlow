package com.netflow.predict.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.netflow.predict.data.repository.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starts the VPN service automatically after the device boots,
 * if the user had previously enabled monitoring.
 *
 * Registered in AndroidManifest with RECEIVE_BOOT_COMPLETED permission.
 * Reads the auto-start preference from DataStore before launching.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoStart = context.dataStore.data
                    .map { prefs -> prefs[booleanPreferencesKey("auto_start_vpn")] ?: false }
                    .first()

                if (autoStart) {
                    val serviceIntent = Intent(context, NetFlowVpnService::class.java).apply {
                        action = NetFlowVpnService.ACTION_START
                    }
                    context.startForegroundService(serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
