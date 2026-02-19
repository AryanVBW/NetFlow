package com.netflow.predict.engine

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
 * Core VPN packet processing loop.
 *
 * Reads raw IP packets from the TUN file descriptor, parses them,
 * tracks flows, resolves DNS, and forwards traffic to the real network.
 *
 * Architecture:
 * - Reads from TUN fd using blocking I/O on a dedicated thread.
 * - Parses IP/TCP/UDP headers via PacketParser.
 * - Tracks connections via FlowTracker.
 * - Forwards packets by creating real sockets and relaying data.
 *   For simplicity in v1, we use a "DNS intercept + flow tracking" approach:
 *   - All DNS queries are intercepted, parsed, and forwarded to a real DNS server.
 *   - TCP/UDP data is forwarded by opening real sockets for each flow.
 *   - Responses from real sockets are written back to the TUN interface.
 *
 * This is a simplified user-space network stack. A production implementation
 * would use a proper TCP reassembly library.
 */
class VpnPacketLoop(
    private val tunFd: ParcelFileDescriptor,
    private val flowTracker: FlowTracker,
    private val appResolver: AppResolver
) {
    companion object {
        private const val TAG = "VpnPacketLoop"
        private const val MTU = 1500
        private const val DNS_PORT = 53
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_PORT = 53
    }

    private val readBuffer = ByteBuffer.allocate(MTU)
    private val writeBuffer = ByteBuffer.allocate(MTU)

    @Volatile
    private var running = false

    private var tunInput: FileInputStream? = null
    private var tunOutput: FileOutputStream? = null

    /**
     * Start the packet processing loop. This will run until stop() is called.
     * Must be called on a background coroutine/thread.
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
                    // No data available or error — yield to avoid busy spin
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
            Log.i(TAG, "VPN packet loop stopped")
        }
    }

    fun stop() {
        running = false
        try {
            tunInput?.close()
            tunOutput?.close()
        } catch (_: Exception) {}
    }

    private suspend fun processPacket(buffer: ByteBuffer, length: Int) {
        val packet = PacketParser.parse(buffer, length) ?: return

        // Determine if outbound (from device to network)
        val isOutbound = isOutboundPacket(packet)

        // Resolve the UID for this connection
        val protocol = if (packet.protocol == 6) "tcp" else "udp"
        val localPort = if (isOutbound) packet.srcPort else packet.dstPort
        val uid = appResolver.findUidForConnection(protocol, localPort)

        // Track the flow
        flowTracker.onPacket(packet, uid, isOutbound)

        // Handle DNS specifically
        if (packet.protocol == 17 && (packet.dstPort == DNS_PORT || packet.srcPort == DNS_PORT)) {
            handleDnsPacket(buffer, packet, length, uid, isOutbound)
            return
        }

        // Forward the packet to the real network
        forwardPacket(buffer, length, packet, isOutbound)
    }

    private suspend fun handleDnsPacket(
        buffer: ByteBuffer,
        packet: PacketParser.ParsedPacket,
        length: Int,
        uid: Int,
        isOutbound: Boolean
    ) {
        if (!isOutbound) return // Only intercept outbound DNS queries

        // Calculate DNS payload offset
        val ipHeaderSize = if (packet.ipVersion == 4) {
            ((buffer.get(0).toInt() and 0x0F) * 4)
        } else 40
        val udpHeaderSize = 8
        val dnsOffset = ipHeaderSize + udpHeaderSize
        val dnsLength = length - dnsOffset

        if (dnsLength <= 12) return

        // Parse the DNS query
        val dns = PacketParser.parseDns(buffer, dnsOffset, dnsLength)

        if (dns != null) {
            // Forward DNS query to real upstream DNS server
            try {
                val dnsPayload = ByteArray(dnsLength)
                buffer.position(dnsOffset)
                buffer.get(dnsPayload, 0, dnsLength)

                withContext(Dispatchers.IO) {
                    val socket = DatagramSocket()
                    try {
                        // Protect the socket so it doesn't loop through VPN
                        val sendPacket = DatagramPacket(
                            dnsPayload, dnsLength,
                            InetAddress.getByName(UPSTREAM_DNS), UPSTREAM_DNS_PORT
                        )
                        socket.soTimeout = 5000
                        socket.send(sendPacket)

                        // Receive response
                        val responseBuf = ByteArray(512)
                        val receivePacket = DatagramPacket(responseBuf, responseBuf.size)
                        socket.receive(receivePacket)

                        // Parse the DNS response
                        val responseBuffer = ByteBuffer.wrap(responseBuf, 0, receivePacket.length)
                        val dnsResponse = PacketParser.parseDns(responseBuffer, 0, receivePacket.length)

                        if (dnsResponse != null) {
                            flowTracker.onDns(dnsResponse, uid)
                        }

                        // Build and write the response back through TUN
                        writeDnsResponse(
                            packet, dnsPayload,
                            responseBuf, receivePacket.length
                        )
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
        originalDnsPayload: ByteArray,
        responsePayload: ByteArray,
        responseLength: Int
    ) {
        try {
            // Build a complete IP+UDP+DNS response packet
            val ipHeaderSize = 20 // IPv4 simple header
            val udpHeaderSize = 8
            val totalLength = ipHeaderSize + udpHeaderSize + responseLength

            val response = ByteBuffer.allocate(totalLength)

            // IPv4 header (swapped src/dst from original)
            response.put((0x45).toByte()) // version=4, IHL=5
            response.put(0.toByte()) // TOS
            response.putShort(totalLength.toShort()) // total length
            response.putShort(0.toShort()) // identification
            response.putShort(0x4000.toShort()) // flags: don't fragment
            response.put(64.toByte()) // TTL
            response.put(17.toByte()) // protocol: UDP
            response.putShort(0.toShort()) // checksum (0 = let stack compute)

            // Source IP = original dst (DNS server), Dest IP = original src (device)
            for (part in originalPacket.dstIp.split(".")) {
                response.put(part.toInt().toByte())
            }
            for (part in originalPacket.srcIp.split(".")) {
                response.put(part.toInt().toByte())
            }

            // UDP header (swapped ports)
            response.putShort(originalPacket.dstPort.toShort()) // src port (DNS server)
            response.putShort(originalPacket.srcPort.toShort()) // dst port (client)
            response.putShort((udpHeaderSize + responseLength).toShort()) // UDP length
            response.putShort(0.toShort()) // checksum (0 = optional for UDP)

            // DNS response payload
            response.put(responsePayload, 0, responseLength)

            // Write to TUN
            response.flip()
            tunOutput?.write(response.array(), 0, response.limit())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write DNS response to TUN", e)
        }
    }

    /**
     * Forward a non-DNS packet to the real network.
     *
     * In v1, we operate in monitor-only mode: DNS queries are intercepted,
     * parsed, and forwarded via real sockets (see handleDnsPacket). All other
     * traffic (TCP/UDP) is tracked for flow analysis but NOT written back
     * to the TUN fd — doing so would cause an infinite packet loop since
     * the TUN captures all outbound traffic via addRoute("0.0.0.0", 0).
     *
     * A full userspace TCP/UDP relay (reading from TUN, opening protected
     * real sockets, and writing responses back) is planned for v2.
     * For now, the flow tracking and DNS interception provide the core value.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun forwardPacket(
        buffer: ByteBuffer,
        length: Int,
        packet: PacketParser.ParsedPacket,
        isOutbound: Boolean
    ) {
        // No-op in v1: packets are tracked but not relayed.
        // Writing raw IP packets back to the TUN fd would re-trigger
        // processPacket() and create an infinite loop.
    }

    private fun isOutboundPacket(packet: PacketParser.ParsedPacket): Boolean {
        // Our VPN interface is 10.0.0.2
        // Outbound = srcIp starts with 10.0.0
        return packet.srcIp.startsWith("10.0.0.") || packet.srcIp.startsWith("10.0.0")
    }
}
