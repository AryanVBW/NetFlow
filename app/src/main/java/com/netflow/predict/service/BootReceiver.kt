package com.netflow.predict.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the VPN service automatically after the device boots,
 * if the user had previously enabled monitoring.
 *
 * Registered in AndroidManifest with RECEIVE_BOOT_COMPLETED permission.
 * In the real implementation, check SharedPreferences / DataStore for
 * "autoStart" flag before launching the service.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // TODO: Read user preference — only start if auto-start was enabled.
        // val prefs = context.getSharedPreferences("netflow_prefs", Context.MODE_PRIVATE)
        // val autoStart = prefs.getBoolean("auto_start_vpn", false)
        // if (!autoStart) return

        val serviceIntent = Intent(context, NetFlowVpnService::class.java).apply {
            action = NetFlowVpnService.ACTION_START
        }
        context.startForegroundService(serviceIntent)
    }
}
