package com.netflow.predict.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.netflow.predict.MainActivity
import com.netflow.predict.data.local.dao.*
import com.netflow.predict.engine.AppResolver
import com.netflow.predict.engine.FlowTracker
import com.netflow.predict.engine.VpnPacketLoop
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Real VPN capture service.
 *
 * This foreground service manages the VPN tunnel interface and runs the
 * packet processing loop that captures, parses, and logs all device traffic.
 *
 * The service uses Hilt injection to access the Room DAOs and writes
 * captured data to the local database for the UI and prediction engine.
 */
@AndroidEntryPoint
class NetFlowVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.netflow.predict.START_VPN"
        const val ACTION_STOP  = "com.netflow.predict.STOP_VPN"

        private const val TAG = "NetFlowVpnService"
        private const val NOTIFICATION_ID      = 1001
        private const val NOTIFICATION_CHANNEL = "netflow_vpn"

        /** Shared reference so VpnRepository can access the flow tracker */
        @Volatile
        var activeFlowTracker: FlowTracker? = null
            private set

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    @Inject lateinit var connectionDao: ConnectionDao
    @Inject lateinit var dnsQueryDao: DnsQueryDao
    @Inject lateinit var appDao: AppDao
    @Inject lateinit var domainDao: DomainDao

    private var tunFd: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var packetLoop: VpnPacketLoop? = null
    private var flowTracker: FlowTracker? = null
    private var appResolver: AppResolver? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startVpn()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        // User revoked VPN permission from system settings
        stopVpn()
    }

    // ── VPN control ───────────────────────────────────────────────────────────

    private fun startVpn() {
        try {
            tunFd?.close()

            // Initialize the app resolver and preload the package cache
            appResolver = AppResolver(this).also { it.preloadCache() }

            // Initialize the flow tracker with real DAOs
            flowTracker = FlowTracker(
                connectionDao = connectionDao,
                dnsQueryDao = dnsQueryDao,
                appDao = appDao,
                domainDao = domainDao,
                appResolver = appResolver!!
            )
            activeFlowTracker = flowTracker

            // Build the VPN interface
            val builder = Builder()
                .setSession("NetFlow Predict")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)
                .setBlocking(true) // blocking mode for the read loop

            // Exclude our own app from VPN to prevent loopback
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not exclude self from VPN", e)
            }

            tunFd = builder.establish()

            if (tunFd == null) {
                Log.e(TAG, "Failed to establish VPN tunnel — user may not have granted permission")
                stopSelf()
                return
            }

            isRunning = true
            Log.i(TAG, "VPN tunnel established")

            // Start coroutine scope for the packet loop
            serviceScope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + CoroutineName("VpnServiceScope")
            )

            // Start the flow tracker (periodic flush to DB)
            flowTracker!!.start(serviceScope!!)

            // Start the packet processing loop
            packetLoop = VpnPacketLoop(tunFd!!, flowTracker!!, appResolver!!)
            serviceScope!!.launch {
                packetLoop!!.run()
            }

            // Update notification to show active state
            updateNotification("Monitoring — capturing traffic")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN")
        isRunning = false
        activeFlowTracker = null

        // Stop the packet loop
        packetLoop?.stop()
        packetLoop = null

        // Stop the flow tracker (triggers final DB flush)
        flowTracker?.stop()
        flowTracker = null

        // Cancel the coroutine scope
        serviceScope?.cancel()
        serviceScope = null

        // Close the TUN file descriptor
        try {
            tunFd?.close()
        } catch (_: Exception) {}
        tunFd = null

        appResolver = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "NetFlow VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while NetFlow is monitoring traffic"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String = "Monitoring network traffic"): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, NetFlowVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("NetFlow Predict")
            .setContentText(contentText)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }
}
