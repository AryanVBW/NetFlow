package com.netflow.predict.engine

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Core VPN packet processing loop.
 *
 * Reads raw IP packets from the TUN file descriptor, parses them,
 * tracks flows, resolves DNS, and forwards traffic to the real network.
 *
 * Architecture:
 * - Reads from TUN fd using non-blocking I/O on a dedicated coroutine.
 * - Parses IP/TCP/UDP headers via PacketParser.
 * - Tracks connections via FlowTracker.
 * - DNS-only interception: all DNS queries to the 5 routed DNS IPs are
 *   intercepted, forwarded through a **protected** socket (bypassing the
 *   VPN itself to prevent routing loops), and the responses written back
 *   through the TUN interface to the originating app.
 * - Non-DNS UDP: relayed via session-keyed protected DatagramSockets so
 *   responses can be returned to the originating app port.
 *
 * Connectivity guarantee:
 * - All sockets opened inside this loop are protected via VpnService.protect()
 *   so they use the real network interface, never re-entering the TUN.
 * - Non-routed traffic (TCP, etc.) bypasses the TUN entirely because we only
 *   add routes for known DNS server IPs and call allowBypass() in the builder.
 */
class VpnPacketLoop(
    private val tunFd: ParcelFileDescriptor,
    private val flowTracker: FlowTracker,
    private val appResolver: AppResolver,
    private val vpnService: VpnService
) {
    companion object {
        private const val TAG = "VpnPacketLoop"
        private const val MTU = 1500
        private const val DNS_PORT = 53
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_PORT = 53

        /** Max UDP DNS response size (EDNS0 supports up to 4096 B) */
        private const val DNS_RESPONSE_BUF_SIZE = 4096

        /** Max concurrent UDP relay sessions tracked in memory */
        private const val MAX_UDP_SESSIONS = 128

        /** Timeout for DNS socket reads */
        private const val DNS_SOCKET_TIMEOUT_MS = 5_000
    }

    private val readBuffer = ByteBuffer.allocate(MTU)
    private val writeBuffer = ByteBuffer.allocate(DNS_RESPONSE_BUF_SIZE + 28) // IP+UDP header overhead

    @Volatile
    private var running = false

    private var tunInput: FileInputStream? = null
    private var tunOutput: FileOutputStream? = null

    /**
     * Session-keyed UDP relay sockets.
     * Key = srcPort of the originating packet (unique per UDP "connection").
     * Each socket is protected so it bypasses the VPN tunnel.
     */
    private val udpSessions = ConcurrentHashMap<Int, DatagramSocket>(MAX_UDP_SESSIONS)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start the packet processing loop. Runs until stop() is called.
     * Must be called from a background coroutine.
     */
    suspend fun run() = withContext(Dispatchers.IO) {
        running = true
        tunInput = FileInputStream(tunFd.fileDescriptor)
        tunOutput = FileOutputStream(tunFd.fileDescriptor)

        Log.i(TAG, "VPN packet loop started")

        try {
            while (running && isActive) {
                readBuffer.clear()

                val bytesRead = try {
                    tunInput?.read(readBuffer.array()) ?: -1
                } catch (e: Exception) {
                    if (running) Log.e(TAG, "TUN read error", e)
                    -1
                }

                if (bytesRead <= 0) {
                    // No data — yield to avoid busy-spin without blocking the thread
                    delay(1)
                    continue
                }

                readBuffer.limit(bytesRead)
                processPacket(readBuffer, bytesRead)
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "Packet loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Packet loop error", e)
        } finally {
            closeAllUdpSessions()
            Log.i(TAG, "VPN packet loop stopped")
        }
    }

    fun stop() {
        running = false
        try {
            tunInput?.close()
            tunOutput?.close()
        } catch (_: Exception) {}
        closeAllUdpSessions()
    }

    // ── Packet processing ─────────────────────────────────────────────────────

    private suspend fun processPacket(buffer: ByteBuffer, length: Int) {
        try {
            if (length > buffer.capacity()) {
                Log.w(TAG, "Packet length $length exceeds buffer capacity ${buffer.capacity()}")
                return
            }

            // Reset position so parsing always starts from the beginning of the packet
            buffer.position(0)
            val packet = PacketParser.parse(buffer, length) ?: return

            val isOutbound = isOutboundPacket(packet)

            val protocol = if (packet.protocol == 6) "tcp" else "udp"
            val localPort = if (isOutbound) packet.srcPort else packet.dstPort
            val remoteIp = if (isOutbound) packet.dstIp else packet.srcIp
            val remotePort = if (isOutbound) packet.dstPort else packet.srcPort

            val uid = appResolver.findUidForConnection(protocol, localPort, remoteIp, remotePort)
            flowTracker.onPacket(packet, uid, isOutbound)

            // Intercept DNS (UDP port 53) — all DNS IPs are routed through us
            if (packet.protocol == 17 && (packet.dstPort == DNS_PORT || packet.srcPort == DNS_PORT)) {
                handleDnsPacket(buffer, packet, length, uid, isOutbound)
                return
            }

            // Relay other UDP traffic via protected session sockets
            if (packet.protocol == 17 && isOutbound) {
                relayUdpPacket(buffer, packet, length)
            }
            // TCP and ICMP: not routed through TUN (only DNS IPs are in the route table)
            // so they bypass the VPN natively. Nothing to do here.

        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet: ${e.message}", e)
        }
    }

    // ── DNS interception ──────────────────────────────────────────────────────

    private suspend fun handleDnsPacket(
        buffer: ByteBuffer,
        packet: PacketParser.ParsedPacket,
        length: Int,
        uid: Int,
        isOutbound: Boolean
    ) {
        if (!isOutbound) return // Only intercept outbound DNS queries

        val ipHeaderSize = if (packet.ipVersion == 4) {
            // Reset position to read IHL from byte 0
            ((buffer.get(0).toInt() and 0x0F) * 4)
        } else 40
        val udpHeaderSize = 8
        val dnsOffset = ipHeaderSize + udpHeaderSize
        val dnsLength = length - dnsOffset

        if (dnsLength <= 12) return

        // Reset buffer position before parsing DNS payload
        buffer.position(0)
        val dns = PacketParser.parseDns(buffer, dnsOffset, dnsLength)

        if (dns != null) {
            try {
                val dnsPayload = ByteArray(dnsLength)
                buffer.position(dnsOffset)
                buffer.get(dnsPayload, 0, dnsLength)

                withContext(Dispatchers.IO) {
                    val socket = DatagramSocket()
                    try {
                        // *** Critical: protect the socket so it uses the real network
                        //     interface and NOT the VPN TUN, preventing a routing loop. ***
                        vpnService.protect(socket)

                        val sendPacket = DatagramPacket(
                            dnsPayload, dnsLength,
                            InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT
                        )
                        socket.soTimeout = DNS_SOCKET_TIMEOUT_MS
                        socket.send(sendPacket)

                        // Use a larger buffer to accommodate EDNS0 responses
                        val responseBuf = ByteArray(DNS_RESPONSE_BUF_SIZE)
                        val receivePacket = DatagramPacket(responseBuf, responseBuf.size)
                        socket.receive(receivePacket)

                        val responseBuffer = ByteBuffer.wrap(responseBuf, 0, receivePacket.length)
                        val dnsResponse = PacketParser.parseDns(responseBuffer, 0, receivePacket.length)

                        if (dnsResponse != null) {
                            flowTracker.onDns(dnsResponse, uid)
                        }

                        writeDnsResponse(packet, dnsPayload, responseBuf, receivePacket.length)
                    } finally {
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DNS forward failed for ${dns.queryDomain}", e)
            }
        }
    }

    private fun writeDnsResponse(
        originalPacket: PacketParser.ParsedPacket,
        @Suppress("UNUSED_PARAMETER") originalDnsPayload: ByteArray,
        responsePayload: ByteArray,
        responseLength: Int
    ) {
        try {
            val ipHeaderSize = 20 // IPv4 simple header
            val udpHeaderSize = 8
            val totalLength = ipHeaderSize + udpHeaderSize + responseLength

            val response = ByteBuffer.allocate(totalLength)

            // IPv4 header — swap src/dst from the original query
            response.put(0x45.toByte())           // version=4, IHL=5
            response.put(0.toByte())              // TOS
            response.putShort(totalLength.toShort())
            response.putShort(0.toShort())        // identification
            response.putShort(0x4000.toShort())   // flags: don't fragment
            response.put(64.toByte())             // TTL
            response.put(17.toByte())             // protocol: UDP
            response.putShort(0.toShort())        // checksum (kernel fills this)

            // Src = original destination (DNS server IP), Dst = original source (device)
            for (part in originalPacket.dstIp.split(".")) response.put(part.toInt().toByte())
            for (part in originalPacket.srcIp.split(".")) response.put(part.toInt().toByte())

            // UDP header — swap ports
            response.putShort(originalPacket.dstPort.toShort()) // src port (DNS = 53)
            response.putShort(originalPacket.srcPort.toShort()) // dst port (client ephemeral)
            response.putShort((udpHeaderSize + responseLength).toShort())
            response.putShort(0.toShort()) // checksum (optional for UDP over IPv4)

            response.put(responsePayload, 0, responseLength)

            response.flip()
            tunOutput?.write(response.array(), 0, response.limit())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write DNS response to TUN", e)
        }
    }

    // ── UDP relay (non-DNS) ───────────────────────────────────────────────────

    /**
     * Relay a non-DNS outbound UDP packet to the real network via a
     * protected DatagramSocket keyed by the source port.
     *
     * Responses from the remote host are NOT written back to the TUN here
     * because non-DNS UDP flows are typically unicast fire-and-forget
     * (QUIC, DTLS, etc.) and the OS handles the response path via the
     * real network stack since only DNS IPs are routed through us.
     *
     * The primary purpose of this method is to ensure that any UDP packets
     * that *do* arrive at the TUN (e.g. due to transient route overlap) are
     * forwarded rather than silently dropped.
     */
    private suspend fun relayUdpPacket(
        buffer: ByteBuffer,
        packet: PacketParser.ParsedPacket,
        length: Int
    ) {
        try {
            val ipHeaderSize = if (packet.ipVersion == 4) {
                ((buffer.get(0).toInt() and 0x0F) * 4)
            } else 40
            val udpHeaderSize = 8
            val payloadOffset = ipHeaderSize + udpHeaderSize
            val payloadLength = length - payloadOffset
            if (payloadLength <= 0) return

            val payload = ByteArray(payloadLength)
            buffer.position(payloadOffset)
            buffer.get(payload, 0, payloadLength)

            // Bound the session map size to prevent unbounded memory growth
            if (udpSessions.size >= MAX_UDP_SESSIONS) {
                val oldest = udpSessions.entries.firstOrNull()
                oldest?.let {
                    it.value.close()
                    udpSessions.remove(it.key)
                }
            }

            val socket = udpSessions.getOrPut(packet.srcPort) {
                DatagramSocket().also { vpnService.protect(it) }
            }

            withContext(Dispatchers.IO) {
                try {
                    val sendPacket = DatagramPacket(
                        payload, payloadLength,
                        InetAddress.getByName(packet.dstIp), packet.dstPort
                    )
                    socket.send(sendPacket)
                } catch (e: Exception) {
                    Log.d(TAG, "UDP relay failed for ${packet.dstIp}:${packet.dstPort} — ${e.message}")
                    udpSessions.remove(packet.srcPort)?.close()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "UDP relay error: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isOutboundPacket(packet: PacketParser.ParsedPacket): Boolean {
        // VPN interface is assigned 10.0.0.2; packets from that prefix are outbound
        return packet.srcIp.startsWith("10.0.0.")
    }

    private fun closeAllUdpSessions() {
        udpSessions.values.forEach { socket ->
            try { socket.close() } catch (_: Exception) {}
        }
        udpSessions.clear()
    }
}
