package com.netflow.predict.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.netflow.predict.MainActivity
import com.netflow.predict.R

/**
 * Stub VPN service.
 *
 * This foreground service holds the VPN tunnel interface open.
 * In the real implementation this is where packets are read from
 * [tunFd] via a background thread / coroutine, parsed, classified,
 * and forwarded through a real (or loopback) socket while being
 * stored in the Room database.
 */
class NetFlowVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.netflow.predict.START_VPN"
        const val ACTION_STOP  = "com.netflow.predict.STOP_VPN"

        private const val NOTIFICATION_ID      = 1001
        private const val NOTIFICATION_CHANNEL = "netflow_vpn"
    }

    private var tunFd: ParcelFileDescriptor? = null

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
        stopVpn()
    }

    // ── VPN control ───────────────────────────────────────────────────────────

    private fun startVpn() {
        try {
            tunFd?.close()

            val builder = Builder()
                .setSession("NetFlow Predict")
                .addAddress("10.0.0.2", 32)          // virtual client address
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)               // route all IPv4 traffic
                .setMtu(1500)
                .setBlocking(false)

            tunFd = builder.establish()

            // TODO: Start packet-capture loop here.
            //   val fd = tunFd!!.fileDescriptor
            //   while (isActive) {
            //       val bytesRead = Os.read(fd, packetBuffer, 0, bufferSize)
            //       if (bytesRead > 0) parseAndForward(packetBuffer, bytesRead)
            //   }

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            tunFd?.close()
        } catch (_: Exception) {}
        tunFd = null
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

    private fun buildNotification(): Notification {
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
            .setSmallIcon(android.R.drawable.ic_lock_lock)  // replace with custom icon
            .setContentTitle("NetFlow Predict")
            .setContentText("Monitoring network traffic")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
