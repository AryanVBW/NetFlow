package com.netflow.predict.engine

import java.nio.ByteBuffer

/**
 * Constructs raw IPv4+TCP and IPv4+UDP packets for writing back to the TUN
 * interface. Used by proxy sessions to return response data to apps.
 *
 * All packets are built with valid IP headers and proper checksums so the
 * kernel TUN driver accepts them without dropping.
 */
object IpPacketBuilder {

    private const val IP_HEADER_SIZE = 20
    private const val TCP_HEADER_SIZE = 20  // without options
    private const val UDP_HEADER_SIZE = 8

    // ── TCP flags ─────────────────────────────────────────────────────────────

    const val TCP_FLAG_FIN = 0x01
    const val TCP_FLAG_SYN = 0x02
    const val TCP_FLAG_RST = 0x04
    const val TCP_FLAG_PSH = 0x08
    const val TCP_FLAG_ACK = 0x10

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Build a complete IPv4+TCP packet.
     *
     * @param srcIp      source IP (e.g. the remote server)
     * @param srcPort    source port (remote)
     * @param dstIp      destination IP (e.g. 10.0.0.2, the VPN interface)
     * @param dstPort    destination port (local app port)
     * @param seqNum     TCP sequence number
     * @param ackNum     TCP acknowledgement number
     * @param flags      TCP flags bitmask (SYN, ACK, PSH, FIN, RST)
     * @param windowSize TCP window size
     * @param payload    TCP payload data (may be empty)
     * @return complete raw IP packet bytes
     */
    fun buildTcpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        seqNum: Long, ackNum: Long,
        flags: Int, windowSize: Int = 65535,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val totalLength = IP_HEADER_SIZE + TCP_HEADER_SIZE + payload.size
        val buf = ByteBuffer.allocate(totalLength)

        // ── IPv4 header ───────────────────────────────────────────────────────
        writeIpHeader(buf, totalLength, 6 /* TCP */, srcIp, dstIp)

        // ── TCP header ────────────────────────────────────────────────────────
        val tcpStart = IP_HEADER_SIZE
        buf.putShort(tcpStart, srcPort.toShort())
        buf.putShort(tcpStart + 2, dstPort.toShort())
        buf.putInt(tcpStart + 4, seqNum.toInt())
        buf.putInt(tcpStart + 8, ackNum.toInt())
        // Data offset (5 = 20 bytes / 4) in upper nibble + reserved
        buf.put(tcpStart + 12, (0x50).toByte()) // data offset = 5 (20 bytes)
        buf.put(tcpStart + 13, flags.toByte())
        buf.putShort(tcpStart + 14, windowSize.toShort())
        buf.putShort(tcpStart + 16, 0.toShort()) // checksum placeholder
        buf.putShort(tcpStart + 18, 0.toShort()) // urgent pointer

        // Payload
        if (payload.isNotEmpty()) {
            buf.position(tcpStart + TCP_HEADER_SIZE)
            buf.put(payload)
        }

        val packet = buf.array()

        // Compute TCP checksum (with pseudo-header)
        val tcpChecksum = computeTcpChecksum(
            packet, srcIp, dstIp,
            tcpStart, TCP_HEADER_SIZE + payload.size
        )
        packet[tcpStart + 16] = ((tcpChecksum shr 8) and 0xFF).toByte()
        packet[tcpStart + 17] = (tcpChecksum and 0xFF).toByte()

        // Compute IP header checksum
        val ipChecksum = computeIpChecksum(packet, 0, IP_HEADER_SIZE)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        return packet
    }

    /**
     * Build a complete IPv4+UDP packet.
     */
    fun buildUdpPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = UDP_HEADER_SIZE + payload.size
        val totalLength = IP_HEADER_SIZE + udpLength
        val buf = ByteBuffer.allocate(totalLength)

        // ── IPv4 header ───────────────────────────────────────────────────────
        writeIpHeader(buf, totalLength, 17 /* UDP */, srcIp, dstIp)

        // ── UDP header ────────────────────────────────────────────────────────
        val udpStart = IP_HEADER_SIZE
        buf.putShort(udpStart, srcPort.toShort())
        buf.putShort(udpStart + 2, dstPort.toShort())
        buf.putShort(udpStart + 4, udpLength.toShort())
        buf.putShort(udpStart + 6, 0.toShort()) // checksum 0 = optional for IPv4 UDP

        // Payload
        buf.position(udpStart + UDP_HEADER_SIZE)
        buf.put(payload)

        val packet = buf.array()

        // Compute IP header checksum
        val ipChecksum = computeIpChecksum(packet, 0, IP_HEADER_SIZE)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        return packet
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Write a standard 20-byte IPv4 header into [buf] at position 0.
     */
    private fun writeIpHeader(
        buf: ByteBuffer, totalLength: Int,
        protocol: Int, srcIp: String, dstIp: String
    ) {
        buf.put(0, 0x45.toByte())                          // version=4, IHL=5
        buf.put(1, 0.toByte())                             // DSCP/ECN
        buf.putShort(2, totalLength.toShort())              // total length
        buf.putShort(4, 0.toShort())                        // identification
        buf.putShort(6, 0x4000.toShort())                   // flags: DF
        buf.put(8, 64.toByte())                             // TTL
        buf.put(9, protocol.toByte())                       // protocol
        buf.putShort(10, 0.toShort())                       // checksum placeholder

        writeIpAddress(buf, 12, srcIp)
        writeIpAddress(buf, 16, dstIp)
    }

    private fun writeIpAddress(buf: ByteBuffer, offset: Int, ip: String) {
        val parts = ip.split(".")
        for (i in 0 until 4) {
            buf.put(offset + i, parts[i].toInt().toByte())
        }
    }

    /**
     * One's complement checksum over [length] bytes starting at [offset].
     */
    fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        var remaining = length
        while (remaining > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            remaining -= 2
        }
        if (remaining == 1) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.toInt().inv()) and 0xFFFF
    }

    /**
     * TCP checksum including the pseudo-header (srcIp, dstIp, protocol=6, tcpLength).
     */
    fun computeTcpChecksum(
        packet: ByteArray, srcIp: String, dstIp: String,
        tcpOffset: Int, tcpLength: Int
    ): Int {
        var sum = 0L

        // Pseudo-header: src IP
        for (part in srcIp.split(".")) {
            sum += part.toInt() and 0xFF
        }
        // Re-fold: src IP is 4 bytes = 2 shorts
        val srcBytes = ipToBytes(srcIp)
        val dstBytes = ipToBytes(dstIp)
        sum = 0L
        sum += ((srcBytes[0].toInt() and 0xFF) shl 8) or (srcBytes[1].toInt() and 0xFF)
        sum += ((srcBytes[2].toInt() and 0xFF) shl 8) or (srcBytes[3].toInt() and 0xFF)
        sum += ((dstBytes[0].toInt() and 0xFF) shl 8) or (dstBytes[1].toInt() and 0xFF)
        sum += ((dstBytes[2].toInt() and 0xFF) shl 8) or (dstBytes[3].toInt() and 0xFF)
        sum += 6 // protocol TCP
        sum += tcpLength

        // TCP segment
        var i = tcpOffset
        var remaining = tcpLength
        while (remaining > 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
            remaining -= 2
        }
        if (remaining == 1) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }

        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.toInt().inv()) and 0xFFFF
    }

    private fun ipToBytes(ip: String): ByteArray {
        val parts = ip.split(".")
        return ByteArray(4) { parts[it].toInt().toByte() }
    }
}
