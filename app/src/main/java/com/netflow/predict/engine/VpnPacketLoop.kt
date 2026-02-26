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

/**
 * Core VPN packet processing loop — full transparent proxy mode.
 *
 * Reads ALL raw IP packets from the TUN file descriptor (0.0.0.0/0 routing),
 * parses them, tracks flows, and proxies traffic to the real network through
 * protected sockets that preserve the device's original IP/location/speed.
 *
 * Traffic handling:
 *   - **DNS (UDP port 53):** Intercepted, forwarded via a protected DatagramSocket,
 *     response written back to TUN. Used by FlowTracker for domain resolution.
 *   - **TCP:** Proxied via TcpProxySession (NIO SocketChannel). The loop manages
 *     the TUN-side TCP state machine (SYN/SYN-ACK/DATA/FIN/RST) while the kernel
 *     handles real TCP on the protected socket side.
 *   - **UDP (non-DNS):** Relayed via UdpProxySession (bidirectional DatagramSocket
 *     with a background response listener).
 *
 * All relay sockets are protected via VpnService.protect() — traffic leaves
 * the device on the real network interface. The VPN is invisible middleware.
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
        private const val DNS_RESPONSE_BUF_SIZE = 4096
        private const val DNS_SOCKET_TIMEOUT_MS = 5_000
    }

    private val readBuffer = ByteBuffer.allocate(MTU)

    @Volatile
    private var running = false

    private var tunInput: FileInputStream? = null
    private var tunOutput: FileOutputStream? = null
    private var sessionManager: SessionManager? = null

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun run() = withContext(Dispatchers.IO) {
        running = true
        tunInput = FileInputStream(tunFd.fileDescriptor)
        tunOutput = FileOutputStream(tunFd.fileDescriptor)

        // Initialize the session manager for TCP/UDP proxy sessions
        val output = tunOutput ?: return@withContext
        val sm = SessionManager(vpnService, output, this)
        sessionManager = sm
        sm.start()

        Log.i(TAG, "VPN packet loop started (full proxy mode)")

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
            sm.stop()
            Log.i(TAG, "VPN packet loop stopped")
        }
    }

    fun stop() {
        running = false
        sessionManager?.stop()
        try {
            tunInput?.close()
            tunOutput?.close()
        } catch (_: Exception) {}
    }

    // ── Packet dispatch ───────────────────────────────────────────────────────

    private suspend fun processPacket(buffer: ByteBuffer, length: Int) {
        try {
            if (length > buffer.capacity()) return

            buffer.position(0)
            val packet = PacketParser.parse(buffer, length) ?: return
            val isOutbound = isOutboundPacket(packet)

            // Flow tracking (lightweight in-memory)
            val protocol = if (packet.protocol == 6) "tcp" else "udp"
            val localPort = if (isOutbound) packet.srcPort else packet.dstPort
            val remoteIp = if (isOutbound) packet.dstIp else packet.srcIp
            val remotePort = if (isOutbound) packet.dstPort else packet.srcPort
            val uid = appResolver.findUidForConnection(protocol, localPort, remoteIp, remotePort)
            flowTracker.onPacket(packet, uid, isOutbound)

            // ── DNS interception (UDP port 53) ────────────────────────────────
            if (packet.protocol == 17 && (packet.dstPort == DNS_PORT || packet.srcPort == DNS_PORT)) {
                if (isOutbound) handleDnsPacket(buffer, packet, length, uid)
                return
            }

            // ── TCP proxy ─────────────────────────────────────────────────────
            if (packet.protocol == 6 && isOutbound) {
                handleTcpPacket(buffer, packet, length)
                return
            }

            // ── UDP relay (non-DNS) ───────────────────────────────────────────
            if (packet.protocol == 17 && isOutbound) {
                handleUdpPacket(buffer, packet, length)
                return
            }

            // Other protocols (ICMP, etc.) — not proxied, tracked only
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet: ${e.message}", e)
        }
    }

    // ── TCP handling ──────────────────────────────────────────────────────────

    private fun handleTcpPacket(buffer: ByteBuffer, packet: PacketParser.ParsedPacket, length: Int) {
        val sm = sessionManager ?: return
        val flags = packet.tcpFlags

        val isSyn = (flags and IpPacketBuilder.TCP_FLAG_SYN) != 0
        val isAck = (flags and IpPacketBuilder.TCP_FLAG_ACK) != 0
        val isFin = (flags and IpPacketBuilder.TCP_FLAG_FIN) != 0
        val isRst = (flags and IpPacketBuilder.TCP_FLAG_RST) != 0
        val hasPush = (flags and IpPacketBuilder.TCP_FLAG_PSH) != 0

        if (isSyn && !isAck) {
            // ── New connection: SYN ───────────────────────────────────────
            val session = sm.getOrCreateTcpSession(
                packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort
            )
            if (session.state == TcpProxySession.TcpState.SYN_RECEIVED) {
                session.onSyn(packet.seqNum)
            }
        } else if (isRst) {
            // ── Connection reset ──────────────────────────────────────────
            val session = sm.getTcpSession(
                packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort
            ) ?: return
            session.onRst()
            sm.removeTcpSession(session.key)
        } else if (isFin) {
            // ── Connection close ──────────────────────────────────────────
            val session = sm.getTcpSession(
                packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort
            ) ?: return
            session.onFin(packet.seqNum)
            sm.removeTcpSession(session.key)
        } else if (isAck) {
            // ── Data or ACK-only ──────────────────────────────────────────
            val session = sm.getTcpSession(
                packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort
            ) ?: return

            if (packet.payloadSize > 0) {
                // Extract TCP payload
                val payload = extractTcpPayload(buffer, packet, length)
                if (payload != null && payload.isNotEmpty()) {
                    session.onData(payload, packet.seqNum)
                }
            } else {
                session.onAck(packet.seqNum, packet.ackNum)
            }
        }
    }

    /**
     * Extract the TCP payload from a raw IP packet buffer.
     */
    private fun extractTcpPayload(
        buffer: ByteBuffer,
        packet: PacketParser.ParsedPacket,
        length: Int
    ): ByteArray? {
        val ipHeaderSize = if (packet.ipVersion == 4) {
            ((buffer.get(0).toInt() and 0x0F) * 4)
        } else 40

        val tcpDataOffset = ((buffer.get(ipHeaderSize + 12).toInt() and 0xFF) shr 4) * 4
        val payloadOffset = ipHeaderSize + tcpDataOffset
        val payloadLength = length - payloadOffset

        if (payloadLength <= 0) return null

        val payload = ByteArray(payloadLength)
        buffer.position(payloadOffset)
        buffer.get(payload, 0, payloadLength)
        return payload
    }

    // ── UDP handling (non-DNS) ────────────────────────────────────────────────

    private fun handleUdpPacket(buffer: ByteBuffer, packet: PacketParser.ParsedPacket, length: Int) {
        val sm = sessionManager ?: return

        val ipHeaderSize = if (packet.ipVersion == 4) {
            ((buffer.get(0).toInt() and 0x0F) * 4)
        } else 40
        val payloadOffset = ipHeaderSize + 8 // UDP header = 8 bytes
        val payloadLength = length - payloadOffset
        if (payloadLength <= 0) return

        val payload = ByteArray(payloadLength)
        buffer.position(payloadOffset)
        buffer.get(payload, 0, payloadLength)

        val session = sm.getOrCreateUdpSession(
            packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort
        )
        session.send(payload)
    }

    // ── DNS interception ──────────────────────────────────────────────────────

    private suspend fun handleDnsPacket(
        buffer: ByteBuffer,
        packet: PacketParser.ParsedPacket,
        length: Int,
        uid: Int
    ) {
        val ipHeaderSize = if (packet.ipVersion == 4) {
            ((buffer.get(0).toInt() and 0x0F) * 4)
        } else 40
        val dnsOffset = ipHeaderSize + 8
        val dnsLength = length - dnsOffset
        if (dnsLength <= 12) return

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
                        vpnService.protect(socket)

                        val sendPacket = DatagramPacket(
                            dnsPayload, dnsLength,
                            InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT
                        )
                        socket.soTimeout = DNS_SOCKET_TIMEOUT_MS
                        socket.send(sendPacket)

                        val responseBuf = ByteArray(DNS_RESPONSE_BUF_SIZE)
                        val receivePacket = DatagramPacket(responseBuf, responseBuf.size)
                        socket.receive(receivePacket)

                        val responseBuffer = ByteBuffer.wrap(responseBuf, 0, receivePacket.length)
                        val dnsResponse = PacketParser.parseDns(responseBuffer, 0, receivePacket.length)
                        if (dnsResponse != null) {
                            flowTracker.onDns(dnsResponse, uid)
                        }

                        writeDnsResponse(packet, responseBuf, receivePacket.length)
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
        responsePayload: ByteArray,
        responseLength: Int
    ) {
        try {
            val packet = IpPacketBuilder.buildUdpPacket(
                srcIp = originalPacket.dstIp,
                srcPort = originalPacket.dstPort,
                dstIp = originalPacket.srcIp,
                dstPort = originalPacket.srcPort,
                payload = responsePayload.copyOfRange(0, responseLength)
            )
            val output = tunOutput ?: return
            synchronized(output) {
                output.write(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write DNS response to TUN", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isOutboundPacket(packet: PacketParser.ParsedPacket): Boolean {
        return packet.srcIp.startsWith("10.0.0.")
    }
}
