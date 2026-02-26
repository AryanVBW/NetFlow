package com.netflow.predict.engine

import java.nio.ByteBuffer

/**
 * Parses raw IP packets read from the TUN file descriptor.
 * Supports IPv4 and IPv6, extracts TCP/UDP headers, and detects DNS queries.
 *
 * All parsing is zero-copy where possible, operating directly on the ByteBuffer
 * provided by the VPN read loop.
 */
object PacketParser {

    /** Parsed result from a raw IP packet */
    data class ParsedPacket(
        val ipVersion: Int,       // 4 or 6
        val protocol: Int,        // 6=TCP, 17=UDP
        val srcIp: String,
        val dstIp: String,
        val srcPort: Int,
        val dstPort: Int,
        val payloadSize: Int,
        val totalLength: Int,
        val tcpFlags: Int = 0,    // only for TCP
        val seqNum: Long = 0,     // TCP sequence number
        val ackNum: Long = 0,     // TCP acknowledgement number
        val windowSize: Int = 0,  // TCP window size
        val isOutbound: Boolean = true
    )

    /** Result of parsing a DNS packet */
    data class DnsResult(
        val queryDomain: String,
        val queryType: String,     // A, AAAA, CNAME, etc.
        val resolvedIps: List<String>,
        val isResponse: Boolean
    )

    private const val PROTO_TCP = 6
    private const val PROTO_UDP = 17

    /**
     * Parse a raw IP packet from the TUN interface.
     * Returns null if the packet is malformed or unsupported.
     */
    fun parse(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 20) return null
        
        try {
            buffer.position(0)
            // Ensure we don't read past limit
            if (buffer.limit() < length) buffer.limit(length)
            
            val versionAndIhl = buffer.get(0).toInt() and 0xFF
            val ipVersion = versionAndIhl shr 4

            return when (ipVersion) {
                4 -> parseIPv4(buffer, length)
                6 -> parseIPv6(buffer, length)
                else -> null
            }
        } catch (e: Exception) {
            // Catch IndexOutOfBoundsException or others
            return null
        }
    }

    private fun parseIPv4(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 20) return null

        val versionAndIhl = buffer.get(0).toInt() and 0xFF
        val ihl = (versionAndIhl and 0x0F) * 4
        if (length < ihl) return null

        val totalLength = buffer.getShort(2).toInt() and 0xFFFF
        val protocol = buffer.get(9).toInt() and 0xFF

        val srcIp = formatIPv4(buffer, 12)
        val dstIp = formatIPv4(buffer, 16)

        if (protocol != PROTO_TCP && protocol != PROTO_UDP) {
            // We only care about TCP and UDP for flow tracking
            return ParsedPacket(
                ipVersion = 4,
                protocol = protocol,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = 0,
                dstPort = 0,
                payloadSize = totalLength - ihl,
                totalLength = totalLength
            )
        }

        if (length < ihl + 4) return null // Need at least src+dst port

        val srcPort = buffer.getShort(ihl).toInt() and 0xFFFF
        val dstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF

        var tcpFlags = 0
        var seqNum = 0L
        var ackNum = 0L
        var windowSize = 0
        var transportHeaderSize = 8 // UDP
        if (protocol == PROTO_TCP) {
            if (length < ihl + 14) return null
            tcpFlags = buffer.get(ihl + 13).toInt() and 0xFF
            val dataOffset = ((buffer.get(ihl + 12).toInt() and 0xFF) shr 4) * 4
            transportHeaderSize = dataOffset
            // Parse TCP sequence/ack numbers and window size
            if (length >= ihl + 16) {
                seqNum = buffer.getInt(ihl + 4).toLong() and 0xFFFFFFFFL
                ackNum = buffer.getInt(ihl + 8).toLong() and 0xFFFFFFFFL
                windowSize = buffer.getShort(ihl + 14).toInt() and 0xFFFF
            }
        }

        val payloadSize = totalLength - ihl - transportHeaderSize

        return ParsedPacket(
            ipVersion = 4,
            protocol = protocol,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            payloadSize = maxOf(0, payloadSize),
            totalLength = totalLength,
            tcpFlags = tcpFlags,
            seqNum = seqNum,
            ackNum = ackNum,
            windowSize = windowSize
        )
    }

    private fun parseIPv6(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 40) return null

        val payloadLength = buffer.getShort(4).toInt() and 0xFFFF
        val nextHeader = buffer.get(6).toInt() and 0xFF

        val srcIp = formatIPv6(buffer, 8)
        val dstIp = formatIPv6(buffer, 24)

        // nextHeader is the protocol for simple cases (no extension headers)
        val protocol = nextHeader
        val headerSize = 40

        if (protocol != PROTO_TCP && protocol != PROTO_UDP) {
            return ParsedPacket(
                ipVersion = 6,
                protocol = protocol,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = 0,
                dstPort = 0,
                payloadSize = payloadLength,
                totalLength = headerSize + payloadLength
            )
        }

        if (length < headerSize + 4) return null

        val srcPort = buffer.getShort(headerSize).toInt() and 0xFFFF
        val dstPort = buffer.getShort(headerSize + 2).toInt() and 0xFFFF

        var tcpFlags = 0
        var seqNum = 0L
        var ackNum = 0L
        var windowSize = 0
        var transportHeaderSize = 8
        if (protocol == PROTO_TCP && length >= headerSize + 14) {
            tcpFlags = buffer.get(headerSize + 13).toInt() and 0xFF
            val dataOffset = ((buffer.get(headerSize + 12).toInt() and 0xFF) shr 4) * 4
            transportHeaderSize = dataOffset
            if (length >= headerSize + 16) {
                seqNum = buffer.getInt(headerSize + 4).toLong() and 0xFFFFFFFFL
                ackNum = buffer.getInt(headerSize + 8).toLong() and 0xFFFFFFFFL
                windowSize = buffer.getShort(headerSize + 14).toInt() and 0xFFFF
            }
        }

        return ParsedPacket(
            ipVersion = 6,
            protocol = protocol,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            payloadSize = maxOf(0, payloadLength - transportHeaderSize),
            totalLength = headerSize + payloadLength,
            tcpFlags = tcpFlags,
            seqNum = seqNum,
            ackNum = ackNum,
            windowSize = windowSize
        )
    }

    /**
     * Parse a DNS payload from a UDP packet.
     * The buffer should be positioned at the start of the DNS payload.
     */
    fun parseDns(buffer: ByteBuffer, offset: Int, length: Int): DnsResult? {
        if (length < 12) return null

        try {
            val flags = buffer.getShort(offset + 2).toInt() and 0xFFFF
            val isResponse = (flags and 0x8000) != 0
            val qdCount = buffer.getShort(offset + 4).toInt() and 0xFFFF
            val anCount = buffer.getShort(offset + 6).toInt() and 0xFFFF

            if (qdCount < 1) return null

            // Parse the first question
            var pos = offset + 12
            val domainParts = mutableListOf<String>()

            while (pos < offset + length) {
                val labelLen = buffer.get(pos).toInt() and 0xFF
                if (labelLen == 0) {
                    pos++
                    break
                }
                if ((labelLen and 0xC0) == 0xC0) {
                    // Compression pointer - skip
                    pos += 2
                    break
                }
                if (pos + 1 + labelLen > offset + length) return null
                val label = ByteArray(labelLen)
                for (i in 0 until labelLen) {
                    label[i] = buffer.get(pos + 1 + i)
                }
                domainParts.add(String(label, Charsets.US_ASCII))
                pos += 1 + labelLen
            }

            if (domainParts.isEmpty()) return null
            val domain = domainParts.joinToString(".")

            // Query type (2 bytes after domain)
            val queryTypeCode = if (pos + 2 <= offset + length) {
                buffer.getShort(pos).toInt() and 0xFFFF
            } else 1

            val queryType = when (queryTypeCode) {
                1 -> "A"
                28 -> "AAAA"
                5 -> "CNAME"
                15 -> "MX"
                2 -> "NS"
                12 -> "PTR"
                6 -> "SOA"
                16 -> "TXT"
                33 -> "SRV"
                65 -> "HTTPS"
                else -> "TYPE$queryTypeCode"
            }

            pos += 4 // skip QTYPE + QCLASS

            // Parse answer records for resolved IPs
            val resolvedIps = mutableListOf<String>()
            if (isResponse && anCount > 0) {
                for (i in 0 until minOf(anCount, 10)) { // cap to 10 answers
                    if (pos >= offset + length) break

                    // Skip name (may be compressed)
                    val firstByte = buffer.get(pos).toInt() and 0xFF
                    if ((firstByte and 0xC0) == 0xC0) {
                        pos += 2
                    } else {
                        while (pos < offset + length) {
                            val l = buffer.get(pos).toInt() and 0xFF
                            if (l == 0) { pos++; break }
                            pos += 1 + l
                        }
                    }

                    if (pos + 10 > offset + length) break

                    val rType = buffer.getShort(pos).toInt() and 0xFFFF
                    // skip RCLASS (2), TTL (4)
                    val rdLength = buffer.getShort(pos + 8).toInt() and 0xFFFF
                    pos += 10

                    if (pos + rdLength > offset + length) break

                    when (rType) {
                        1 -> { // A record
                            if (rdLength == 4) {
                                resolvedIps.add(formatIPv4(buffer, pos))
                            }
                        }
                        28 -> { // AAAA record
                            if (rdLength == 16) {
                                resolvedIps.add(formatIPv6(buffer, pos))
                            }
                        }
                    }
                    pos += rdLength
                }
            }

            return DnsResult(
                queryDomain = domain,
                queryType = queryType,
                resolvedIps = resolvedIps,
                isResponse = isResponse
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun formatIPv4(buffer: ByteBuffer, offset: Int): String {
        return "${buffer.get(offset).toInt() and 0xFF}." +
               "${buffer.get(offset + 1).toInt() and 0xFF}." +
               "${buffer.get(offset + 2).toInt() and 0xFF}." +
               "${buffer.get(offset + 3).toInt() and 0xFF}"
    }

    private fun formatIPv6(buffer: ByteBuffer, offset: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 8) {
            if (i > 0) sb.append(':')
            val word = buffer.getShort(offset + i * 2).toInt() and 0xFFFF
            sb.append(String.format("%x", word))
        }
        return sb.toString()
    }
}
