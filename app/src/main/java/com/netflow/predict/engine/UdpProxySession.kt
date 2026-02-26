package com.netflow.predict.engine

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Manages a single proxied UDP "session" between a device app and a remote server.
 *
 * Unlike TCP, UDP is connectionless, so a "session" is identified by the 4-tuple
 * (srcIp, srcPort, dstIp, dstPort). The session:
 *   1. Opens a protected DatagramSocket on a random local port.
 *   2. Forwards outbound payloads from the TUN to the real network.
 *   3. Runs a background coroutine that polls for response datagrams and writes
 *      them back to the TUN as IP+UDP packets addressed to the app.
 *
 * All sockets are protected via VpnService.protect() so the real device IP,
 * location, and speed are preserved.
 */
class UdpProxySession(
    val key: String,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    private val vpnService: VpnService,
    private val tunOutput: FileOutputStream,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "UdpProxySession"
        private const val RECV_BUF_SIZE = 4096
        private const val SOCKET_TIMEOUT_MS = 10_000

        fun sessionKey(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int): String {
            return "$srcIp:$srcPort->$dstIp:$dstPort/udp"
        }
    }

    val socket: DatagramSocket = DatagramSocket().also { vpnService.protect(it) }

    var lastActivity: Long = System.currentTimeMillis()
        private set
    var totalBytesSent: Long = 0L
        private set
    var totalBytesReceived: Long = 0L
        private set

    @Volatile
    private var active = true
    private var listenerJob: Job? = null

    init {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        startResponseListener()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Send a UDP payload to the remote destination.
     */
    fun send(payload: ByteArray) {
        if (!active) return

        try {
            val packet = DatagramPacket(
                payload, payload.size,
                InetAddress.getByName(dstIp), dstPort
            )
            socket.send(packet)
            totalBytesSent += payload.size
            lastActivity = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.d(TAG, "UDP send to $dstIp:$dstPort failed: ${e.message}")
        }
    }

    /**
     * Check if this session is still active.
     */
    fun isActive(): Boolean = active

    /**
     * Close the session and release resources.
     */
    fun close() {
        active = false
        listenerJob?.cancel()
        try { socket.close() } catch (_: Exception) {}
    }

    // ── Response listener ─────────────────────────────────────────────────────

    /**
     * Background coroutine that reads incoming responses from the remote server
     * and writes them back to the TUN interface as IP+UDP packets.
     */
    private fun startResponseListener() {
        listenerJob = scope.launch(Dispatchers.IO) {
            val recvBuf = ByteArray(RECV_BUF_SIZE)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)

            try {
                while (active && isActive) {
                    try {
                        socket.receive(recvPacket)

                        val responseData = ByteArray(recvPacket.length)
                        System.arraycopy(recvBuf, 0, responseData, 0, recvPacket.length)

                        // Build IP+UDP response packet: remote → app
                        val tunPacket = IpPacketBuilder.buildUdpPacket(
                            srcIp = dstIp,       // remote server address
                            srcPort = dstPort,   // remote server port
                            dstIp = srcIp,       // app on VPN interface
                            dstPort = srcPort,   // app's source port
                            payload = responseData
                        )

                        synchronized(tunOutput) {
                            tunOutput.write(tunPacket)
                        }

                        totalBytesReceived += recvPacket.length
                        lastActivity = System.currentTimeMillis()
                    } catch (e: java.net.SocketTimeoutException) {
                        // Normal timeout — just keep polling
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                if (active) Log.d(TAG, "UDP listener closed: ${e.message}")
            }
        }
    }
}
