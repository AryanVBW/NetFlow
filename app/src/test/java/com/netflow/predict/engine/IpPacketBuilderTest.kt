package com.netflow.predict.engine

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Unit tests for IpPacketBuilder — verifies correct construction of
 * raw IPv4+TCP and IPv4+UDP packets for TUN writeback.
 */
class IpPacketBuilderTest {

    // ── TCP packet construction ──────────────────────────────────────────────

    @Test
    fun `buildTcpPacket produces correct IP version and header length`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 100, ackNum = 200,
            flags = IpPacketBuilder.TCP_FLAG_SYN or IpPacketBuilder.TCP_FLAG_ACK
        )

        // Byte 0: version (4) | IHL (5) = 0x45
        assertEquals(0x45.toByte(), packet[0])
    }

    @Test
    fun `buildTcpPacket has correct total length`() {
        val payload = ByteArray(100) { 0xAB.toByte() }
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_ACK,
            payload = payload
        )

        // Total length: 20 (IP) + 20 (TCP) + 100 (payload) = 140
        assertEquals(140, packet.size)

        // Bytes 2-3: total length in network byte order
        val buf = ByteBuffer.wrap(packet)
        assertEquals(140.toShort(), buf.getShort(2))
    }

    @Test
    fun `buildTcpPacket has correct protocol field`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_SYN
        )

        // Byte 9: protocol = 6 (TCP)
        assertEquals(6.toByte(), packet[9])
    }

    @Test
    fun `buildTcpPacket has correct source and destination IP`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "192.168.1.10", 443, "10.0.0.2", 50000,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_SYN
        )

        // Src IP at offset 12-15
        assertEquals(192.toByte(), packet[12])
        assertEquals(168.toByte(), packet[13])
        assertEquals(1.toByte(), packet[14])
        assertEquals(10.toByte(), packet[15])

        // Dst IP at offset 16-19
        assertEquals(10.toByte(), packet[16])
        assertEquals(0.toByte(), packet[17])
        assertEquals(0.toByte(), packet[18])
        assertEquals(2.toByte(), packet[19])
    }

    @Test
    fun `buildTcpPacket has correct ports`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 443, "10.0.0.2", 50000,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_ACK
        )

        val buf = ByteBuffer.wrap(packet)
        val tcpOffset = 20 // after IP header
        assertEquals(443.toShort(), buf.getShort(tcpOffset))     // src port
        assertEquals(50000.toShort(), buf.getShort(tcpOffset + 2)) // dst port (wraps to short but bits are correct)
    }

    @Test
    fun `buildTcpPacket has correct sequence and ack numbers`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0x12345678L, ackNum = 0xABCDEF01L,
            flags = IpPacketBuilder.TCP_FLAG_ACK
        )

        val buf = ByteBuffer.wrap(packet)
        val tcpOffset = 20
        assertEquals(0x12345678, buf.getInt(tcpOffset + 4))
        assertEquals(0xABCDEF01.toInt(), buf.getInt(tcpOffset + 8))
    }

    @Test
    fun `buildTcpPacket has correct flags`() {
        val synAck = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_SYN or IpPacketBuilder.TCP_FLAG_ACK
        )

        // Flags at TCP offset + 13
        val flags = synAck[20 + 13].toInt() and 0xFF
        assertTrue("SYN should be set", (flags and IpPacketBuilder.TCP_FLAG_SYN) != 0)
        assertTrue("ACK should be set", (flags and IpPacketBuilder.TCP_FLAG_ACK) != 0)
        assertTrue("FIN should NOT be set", (flags and IpPacketBuilder.TCP_FLAG_FIN) == 0)
    }

    @Test
    fun `buildTcpPacket data offset is 5 (20 bytes)`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_ACK
        )

        // TCP byte 12 (offset 20+12=32): upper nibble = data offset in 32-bit words
        val dataOffset = (packet[32].toInt() and 0xFF) shr 4
        assertEquals(5, dataOffset) // 5 * 4 = 20 bytes
    }

    @Test
    fun `buildTcpPacket includes payload at correct offset`() {
        val payload = byteArrayOf(0x48, 0x54, 0x54, 0x50) // "HTTP"
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_ACK or IpPacketBuilder.TCP_FLAG_PSH,
            payload = payload
        )

        // Payload starts at offset 40 (20 IP + 20 TCP)
        assertEquals(0x48.toByte(), packet[40])
        assertEquals(0x54.toByte(), packet[41])
        assertEquals(0x54.toByte(), packet[42])
        assertEquals(0x50.toByte(), packet[43])
    }

    @Test
    fun `buildTcpPacket has non-zero IP checksum`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_SYN
        )

        // IP checksum at bytes 10-11 should be non-zero
        val checksum = ((packet[10].toInt() and 0xFF) shl 8) or (packet[11].toInt() and 0xFF)
        assertTrue("IP checksum should be non-zero", checksum != 0)
    }

    @Test
    fun `IP checksum validates to zero`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_SYN
        )

        // Verification: computing checksum over IP header (including checksum field) should give 0
        val verification = IpPacketBuilder.computeIpChecksum(packet, 0, 20)
        assertEquals("IP header checksum verification should be 0", 0, verification)
    }

    // ── UDP packet construction ──────────────────────────────────────────────

    @Test
    fun `buildUdpPacket has correct total length`() {
        val payload = ByteArray(50) { 0xCD.toByte() }
        val packet = IpPacketBuilder.buildUdpPacket(
            "8.8.8.8", 53, "10.0.0.2", 12345,
            payload = payload
        )

        // Total: 20 (IP) + 8 (UDP) + 50 (payload) = 78
        assertEquals(78, packet.size)
    }

    @Test
    fun `buildUdpPacket has protocol 17`() {
        val packet = IpPacketBuilder.buildUdpPacket(
            "8.8.8.8", 53, "10.0.0.2", 12345,
            payload = byteArrayOf(0)
        )

        assertEquals(17.toByte(), packet[9]) // protocol field
    }

    @Test
    fun `buildUdpPacket has correct ports`() {
        val packet = IpPacketBuilder.buildUdpPacket(
            "8.8.8.8", 53, "10.0.0.2", 12345,
            payload = byteArrayOf(0)
        )

        val buf = ByteBuffer.wrap(packet)
        assertEquals(53.toShort(), buf.getShort(20))      // src port
        assertEquals(12345.toShort(), buf.getShort(22))    // dst port
    }

    @Test
    fun `buildUdpPacket has correct UDP length field`() {
        val payload = ByteArray(100)
        val packet = IpPacketBuilder.buildUdpPacket(
            "8.8.8.8", 53, "10.0.0.2", 12345,
            payload = payload
        )

        val buf = ByteBuffer.wrap(packet)
        val udpLen = buf.getShort(24).toInt() and 0xFFFF
        assertEquals(108, udpLen) // 8 + 100
    }

    @Test
    fun `buildUdpPacket IP checksum validates to zero`() {
        val packet = IpPacketBuilder.buildUdpPacket(
            "8.8.8.8", 53, "10.0.0.2", 12345,
            payload = byteArrayOf(1, 2, 3, 4)
        )

        val verification = IpPacketBuilder.computeIpChecksum(packet, 0, 20)
        assertEquals("IP header checksum verification should be 0", 0, verification)
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `buildTcpPacket with empty payload produces minimal packet`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "1.2.3.4", 80, "10.0.0.2", 12345,
            seqNum = 0, ackNum = 0,
            flags = IpPacketBuilder.TCP_FLAG_SYN
        )

        // IP header (20) + TCP header (20) + no payload = 40
        assertEquals(40, packet.size)
    }

    @Test
    fun `buildUdpPacket with single byte payload`() {
        val packet = IpPacketBuilder.buildUdpPacket(
            "1.2.3.4", 53, "10.0.0.2", 12345,
            payload = byteArrayOf(0xFF.toByte())
        )

        assertEquals(29, packet.size) // 20 + 8 + 1
        assertEquals(0xFF.toByte(), packet[28]) // payload at last byte
    }

    @Test
    fun `TCP checksum is non-zero and computable`() {
        val packet = IpPacketBuilder.buildTcpPacket(
            "192.168.0.1", 80, "10.0.0.2", 44444,
            seqNum = 12345, ackNum = 67890,
            flags = IpPacketBuilder.TCP_FLAG_ACK,
            payload = byteArrayOf(0x41, 0x42, 0x43)  // "ABC"
        )

        // TCP checksum at offset 36-37 (IP:20 + TCP:16-17)
        val tcpChecksum = ((packet[36].toInt() and 0xFF) shl 8) or (packet[37].toInt() and 0xFF)
        assertTrue("TCP checksum should be non-zero", tcpChecksum != 0)
    }
}
