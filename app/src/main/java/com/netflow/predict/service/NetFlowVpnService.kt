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
import com.netflow.predict.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
    @Inject lateinit var settingsRepository: SettingsRepository

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
            
            // Start coroutine scope first
            serviceScope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + CoroutineName("VpnServiceScope")
            )
            val scope = serviceScope ?: return

            // Initialize the app resolver
            val resolver = AppResolver(this)
            appResolver = resolver
            
            // Preload cache on IO thread to avoid ANR
            scope.launch {
                resolver.preloadCache()
            }

            // Initialize the flow tracker with real DAOs
            val tracker = FlowTracker(
                connectionDao = connectionDao,
                dnsQueryDao = dnsQueryDao,
                appDao = appDao,
                domainDao = domainDao,
                appResolver = resolver
            )
            flowTracker = tracker
            activeFlowTracker = tracker

            // Build the VPN interface.
            //
            // Two routing modes controlled by the dnsOnlyMode setting:
            //
            // FULL PROXY (default, dnsOnlyMode=false):
            //   - Routes 0.0.0.0/0 through the TUN — captures ALL device traffic.
            //   - VpnPacketLoop proxies TCP via TcpProxySession (NIO SocketChannel),
            //     UDP via UdpProxySession (bidirectional DatagramSocket), and DNS via
            //     direct interception — all through protect()-ed sockets.
            //   - Device's real IP, location, and speed are preserved because all
            //     relay sockets use the real network interface.
            //
            // DNS-ONLY (dnsOnlyMode=true):
            //   - Routes only 5 specific DNS server IPs through the TUN.
            //   - All other traffic bypasses the TUN natively via allowBypass().
            //   - Lightweight mode for DNS monitoring only.
            //
            val dnsOnly = try {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    settingsRepository.settings.first().dnsOnlyMode
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read DNS-only setting, defaulting to false", e)
                false
            }

            val builder = Builder()
                .setSession("NetFlow")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)

            if (dnsOnly) {
                // DNS-only mode — route only DNS servers, bypass everything else
                builder.allowBypass()
                builder.addRoute("8.8.8.8", 32)
                builder.addRoute("8.8.4.4", 32)
                builder.addRoute("1.1.1.1", 32)
                builder.addRoute("1.0.0.1", 32)
                builder.addRoute("9.9.9.9", 32)
                Log.i(TAG, "VPN running in DNS-only mode")
            } else {
                // Full proxy mode — capture ALL traffic
                builder.addRoute("0.0.0.0", 0)   // IPv4 catch-all
                Log.i(TAG, "VPN running in full proxy mode")
            }

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

            // Start the flow tracker (periodic flush to DB)
            tracker.start(scope)

            // Start the packet processing loop
            val fd = tunFd ?: return
            // Pass `this` (VpnService) so VpnPacketLoop can protect() its sockets
            val loop = VpnPacketLoop(fd, tracker, resolver, this)
            packetLoop = loop
            scope.launch {
                try {
                    loop.run()
                } catch (e: Exception) {
                    Log.e(TAG, "Packet loop crashed", e)
                    stopSelf()
                }
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
            .setContentTitle("NetFlow")
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
