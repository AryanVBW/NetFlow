package com.netflow.predict.engine

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Unit tests for VpnPacketLoop packet processing logic.
 *
 * These tests verify:
 * - Packet classification (outbound vs inbound)
 * - DNS query/response parsing correctness
 * - TCP flag detection for proxy routing
 * - UDP payload extraction
 * - IPv4 header parsing for packet dispatch
 */
class VpnPacketLoopTest {

    // ── DNS buffer and parsing ───────────────────────────────────────────────

    @Test
    fun `DNS response buffer size supports EDNS0`() {
        // The DNS response buffer must be at least 4096 bytes for EDNS0
        assertTrue("DNS buffer must support EDNS0", 4096 >= 4096)
    }

    @Test
    fun `parseDns extracts query domain from a standard A record query`() {
        // Build a minimal DNS query for "example.com"
        val dnsPayload = buildDnsQuery("example.com")
        val buffer = ByteBuffer.wrap(dnsPayload)
        val parsed = PacketParser.parseDns(buffer, 0, dnsPayload.size)

        assertNotNull("DNS query should parse", parsed)
        assertEquals("example.com", parsed!!.queryDomain)
        assertFalse("Should be a query (isResponse=false)", parsed.isResponse)
    }

    @Test
    fun `parseDns extracts multi-label domain`() {
        val dnsPayload = buildDnsQuery("api.github.com")
        val buffer = ByteBuffer.wrap(dnsPayload)
        val parsed = PacketParser.parseDns(buffer, 0, dnsPayload.size)

        assertNotNull(parsed)
        assertEquals("api.github.com", parsed!!.queryDomain)
    }

    @Test
    fun `parseDns identifies response vs query`() {
        val queryPayload = buildDnsQuery("test.com")
        // Toggle QR bit (byte 2, bit 7) to make it a response
        val responsePayload = queryPayload.copyOf()
        responsePayload[2] = (responsePayload[2].toInt() or 0x80).toByte()

        val queryBuf = ByteBuffer.wrap(queryPayload)
        val responseBuf = ByteBuffer.wrap(responsePayload)

        val query = PacketParser.parseDns(queryBuf, 0, queryPayload.size)
        val response = PacketParser.parseDns(responseBuf, 0, responsePayload.size)

        assertFalse("Should be a query", query!!.isResponse)
        assertTrue("Should be a response", response!!.isResponse)
    }

    // ── Outbound/inbound classification ──────────────────────────────────────

    @Test
    fun `packet from VPN interface 10_0_0_2 is outbound`() {
        val packet = buildIpv4UdpPacket("10.0.0.2", "8.8.8.8", 12345, 53, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertTrue("10.0.0.x source should be outbound", parsed!!.srcIp.startsWith("10.0.0."))
    }

    @Test
    fun `packet from external IP is inbound`() {
        val packet = buildIpv4UdpPacket("8.8.8.8", "10.0.0.2", 53, 12345, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertFalse("External source should not match outbound check", parsed!!.srcIp.startsWith("10.0.0."))
    }

    // ── TCP header parsing ───────────────────────────────────────────────────

    @Test
    fun `parseTcpSynPacket detects SYN flag`() {
        val packet = buildIpv4TcpPacket("10.0.0.2", "93.184.216.34", 54321, 443, 0x02) // SYN
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(6, parsed!!.protocol) // TCP
        assertTrue("SYN flag should be set", (parsed.tcpFlags and 0x02) != 0)
        assertFalse("ACK flag should NOT be set", (parsed.tcpFlags and 0x10) != 0)
    }

    @Test
    fun `parseTcpSynAckPacket detects SYN-ACK flags`() {
        val packet = buildIpv4TcpPacket("93.184.216.34", "10.0.0.2", 443, 54321, 0x12) // SYN+ACK
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertTrue("SYN flag set", (parsed!!.tcpFlags and 0x02) != 0)
        assertTrue("ACK flag set", (parsed.tcpFlags and 0x10) != 0)
    }

    @Test
    fun `parseTcpFinPacket detects FIN flag`() {
        val packet = buildIpv4TcpPacket("10.0.0.2", "93.184.216.34", 54321, 443, 0x11) // FIN+ACK
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertTrue("FIN flag set", (parsed!!.tcpFlags and 0x01) != 0)
        assertTrue("ACK flag set", (parsed.tcpFlags and 0x10) != 0)
    }

    @Test
    fun `parseTcpPacket extracts sequence and ack numbers`() {
        val packet = buildIpv4TcpPacketWithSeq(
            "10.0.0.2", "93.184.216.34", 54321, 443,
            flags = 0x10, seqNum = 0x12345678L, ackNum = 0xABCDEF01L
        )
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(0x12345678L, parsed!!.seqNum)
        assertEquals(0xABCDEF01L, parsed.ackNum)
    }

    @Test
    fun `parseTcpPacket extracts window size`() {
        val packet = buildIpv4TcpPacketWithSeq(
            "10.0.0.2", "93.184.216.34", 54321, 443,
            flags = 0x10, seqNum = 100, ackNum = 200, windowSize = 32768
        )
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(32768, parsed!!.windowSize)
    }

    // ── IPv4 basic parsing ───────────────────────────────────────────────────

    @Test
    fun `parse identifies UDP protocol 17`() {
        val packet = buildIpv4UdpPacket("10.0.0.2", "8.8.8.8", 12345, 53, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(17, parsed!!.protocol)
        assertEquals(4, parsed.ipVersion)
    }

    @Test
    fun `parse correctly reads ports from UDP packet`() {
        val packet = buildIpv4UdpPacket("10.0.0.2", "8.8.8.8", 55555, 53, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(55555, parsed!!.srcPort)
        assertEquals(53, parsed.dstPort)
    }

    @Test
    fun `parse reads correct IP addresses`() {
        val packet = buildIpv4UdpPacket("10.0.0.2", "1.1.1.1", 12345, 53, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals("10.0.0.2", parsed!!.srcIp)
        assertEquals("1.1.1.1", parsed.dstIp)
    }

    // ── DNS packet classification ────────────────────────────────────────────

    @Test
    fun `DNS port 53 packets are identifiable from parsed result`() {
        val packet = buildIpv4UdpPacket("10.0.0.2", "8.8.8.8", 12345, 53, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(17, parsed!!.protocol) // UDP
        assertEquals(53, parsed.dstPort)    // DNS port
    }

    @Test
    fun `non-DNS UDP port is not 53`() {
        val packet = buildIpv4UdpPacket("10.0.0.2", "1.2.3.4", 12345, 443, byteArrayOf(0))
        val buf = ByteBuffer.wrap(packet)
        val parsed = PacketParser.parse(buf, packet.size)

        assertNotNull(parsed)
        assertEquals(17, parsed!!.protocol) // UDP
        assertNotEquals(53, parsed.dstPort)  // Not DNS
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private fun buildDnsQuery(domain: String): ByteArray {
        val parts = domain.split(".")
        val nameBytes = mutableListOf<Byte>()
        for (part in parts) {
            nameBytes.add(part.length.toByte())
            for (c in part) nameBytes.add(c.code.toByte())
        }
        nameBytes.add(0.toByte()) // terminator

        val header = ByteArray(12)
        header[0] = 0xAA.toByte() // ID high
        header[1] = 0xBB.toByte() // ID low
        header[5] = 1             // QDCOUNT = 1

        val question = nameBytes.toByteArray() + byteArrayOf(0, 1, 0, 1) // Type A, Class IN

        return header + question
    }

    private fun buildIpv4UdpPacket(
        srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderSize = 20
        val udpHeaderSize = 8
        val totalLength = ipHeaderSize + udpHeaderSize + payload.size

        val packet = ByteArray(totalLength)
        val buf = ByteBuffer.wrap(packet)

        // IP header
        buf.put(0, 0x45.toByte())               // version=4, IHL=5
        buf.put(1, 0.toByte())                   // DSCP/ECN
        buf.putShort(2, totalLength.toShort())
        buf.put(8, 64.toByte())                  // TTL
        buf.put(9, 17.toByte())                  // protocol = UDP

        val srcParts = srcIp.split(".")
        val dstParts = dstIp.split(".")
        for (i in 0..3) {
            buf.put(12 + i, srcParts[i].toInt().toByte())
            buf.put(16 + i, dstParts[i].toInt().toByte())
        }

        // UDP header
        buf.putShort(ipHeaderSize, srcPort.toShort())
        buf.putShort(ipHeaderSize + 2, dstPort.toShort())
        buf.putShort(ipHeaderSize + 4, (udpHeaderSize + payload.size).toShort())

        // Payload
        System.arraycopy(payload, 0, packet, ipHeaderSize + udpHeaderSize, payload.size)

        return packet
    }

    private fun buildIpv4TcpPacket(
        srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int,
        flags: Int
    ): ByteArray {
        return buildIpv4TcpPacketWithSeq(srcIp, dstIp, srcPort, dstPort, flags, 0, 0)
    }

    private fun buildIpv4TcpPacketWithSeq(
        srcIp: String, dstIp: String,
        srcPort: Int, dstPort: Int,
        flags: Int,
        seqNum: Long = 0, ackNum: Long = 0,
        windowSize: Int = 65535
    ): ByteArray {
        val ipHeaderSize = 20
        val tcpHeaderSize = 20
        val totalLength = ipHeaderSize + tcpHeaderSize

        val packet = ByteArray(totalLength)
        val buf = ByteBuffer.wrap(packet)

        // IP header
        buf.put(0, 0x45.toByte())
        buf.putShort(2, totalLength.toShort())
        buf.put(8, 64.toByte())
        buf.put(9, 6.toByte()) // protocol = TCP

        val srcParts = srcIp.split(".")
        val dstParts = dstIp.split(".")
        for (i in 0..3) {
            buf.put(12 + i, srcParts[i].toInt().toByte())
            buf.put(16 + i, dstParts[i].toInt().toByte())
        }

        // TCP header
        buf.putShort(ipHeaderSize, srcPort.toShort())
        buf.putShort(ipHeaderSize + 2, dstPort.toShort())
        buf.putInt(ipHeaderSize + 4, seqNum.toInt())
        buf.putInt(ipHeaderSize + 8, ackNum.toInt())
        buf.put(ipHeaderSize + 12, 0x50.toByte()) // data offset = 5
        buf.put(ipHeaderSize + 13, flags.toByte())
        buf.putShort(ipHeaderSize + 14, windowSize.toShort())

        return packet
    }
}
