package com.netflow.predict.engine

import android.net.VpnService
import android.util.Log
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

/**
 * Manages a single proxied TCP connection between a device app and a remote server.
 *
 * When the app sends a TCP SYN through the TUN, the VpnPacketLoop creates a
 * TcpProxySession. This session:
 *   1. Opens a protected SocketChannel to the real destination (using the device's
 *      real IP — not a VPN IP).
 *   2. Synthesizes a SYN-ACK back into the TUN so the app "sees" an established connection.
 *   3. Relays data bidirectionally: app→remote via SocketChannel.write(),
 *      remote→app via SocketChannel.read() + IpPacketBuilder + TUN write.
 *   4. Handles FIN/RST for clean teardown.
 *
 * The protected socket uses the real network interface, so the original device IP,
 * location, and speed are fully preserved.
 */
class TcpProxySession(
    val key: String,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    private val vpnService: VpnService,
    private val tunOutput: FileOutputStream
) {
    companion object {
        private const val TAG = "TcpProxySession"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_BUF_SIZE = 32_768 // 32 KB read buffer
        private const val INITIAL_WINDOW = 65535

        fun sessionKey(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int): String {
            return "$srcIp:$srcPort->$dstIp:$dstPort"
        }
    }

    /** TCP proxy state machine (simplified — kernel handles real TCP on the remote side) */
    enum class TcpState {
        SYN_RECEIVED,   // received SYN from TUN, connecting to remote
        ESTABLISHED,    // connected, relaying data
        FIN_WAIT,       // received FIN from app, closing
        CLOSED          // session done
    }

    var state: TcpState = TcpState.SYN_RECEIVED
        private set

    var lastActivity: Long = System.currentTimeMillis()
        private set

    var totalBytesSent: Long = 0L
        private set
    var totalBytesReceived: Long = 0L
        private set

    private var channel: SocketChannel? = null

    // Sequence/acknowledgement tracking for the TUN-side TCP conversation
    // We generate our own seq numbers for packets we send back to the TUN
    private var mySeqNum: Long = (System.nanoTime() and 0xFFFFFFFFL) // initial SEQ
    private var myAckNum: Long = 0L // tracks what we have ACKed from the app

    private val readBuffer = ByteBuffer.allocate(READ_BUF_SIZE)

    // ── Connection lifecycle ──────────────────────────────────────────────────

    /**
     * Handle the initial SYN from the app. Opens a protected connection to
     * the real server and sends SYN-ACK back to the app via TUN.
     *
     * @param appSeqNum the sequence number from the app's SYN packet
     * @return true if connection was established successfully
     */
    fun onSyn(appSeqNum: Long): Boolean {
        myAckNum = (appSeqNum + 1) and 0xFFFFFFFFL

        return try {
            val ch = SocketChannel.open()
            ch.configureBlocking(false)
            vpnService.protect(ch.socket())
            ch.connect(InetSocketAddress(dstIp, dstPort))

            // Wait for connection with timeout
            val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
            while (!ch.finishConnect()) {
                if (System.currentTimeMillis() > deadline) {
                    ch.close()
                    sendRst()
                    state = TcpState.CLOSED
                    return false
                }
                Thread.sleep(10)
            }

            channel = ch
            state = TcpState.ESTABLISHED
            lastActivity = System.currentTimeMillis()

            // Send SYN-ACK back to app through TUN
            sendSynAck()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Connect failed to $dstIp:$dstPort — ${e.message}")
            sendRst()
            state = TcpState.CLOSED
            false
        }
    }

    /**
     * Handle data from the app (payload received from the TUN).
     * Relay it to the remote server via the protected SocketChannel.
     *
     * @param payload the TCP payload bytes
     * @param appSeqNum sequence number from the app's packet
     */
    fun onData(payload: ByteArray, appSeqNum: Long) {
        if (state != TcpState.ESTABLISHED) return
        lastActivity = System.currentTimeMillis()

        // Update our ACK to reflect data received from the app
        myAckNum = (appSeqNum + payload.size) and 0xFFFFFFFFL

        try {
            val ch = channel ?: return
            val buf = ByteBuffer.wrap(payload)
            while (buf.hasRemaining()) {
                ch.write(buf)
            }
            totalBytesSent += payload.size

            // ACK the data back to the app
            sendAck()
        } catch (e: Exception) {
            Log.d(TAG, "Write to remote failed: ${e.message}")
            sendRst()
            close()
        }
    }

    /**
     * Handle ACK-only packets from the app (no payload).
     */
    fun onAck(appSeqNum: Long, appAckNum: Long) {
        if (state == TcpState.FIN_WAIT) {
            // App acknowledged our FIN
            state = TcpState.CLOSED
        }
        lastActivity = System.currentTimeMillis()
    }

    /**
     * Handle FIN from the app (app is closing the connection).
     */
    fun onFin(appSeqNum: Long) {
        myAckNum = (appSeqNum + 1) and 0xFFFFFFFFL
        sendAck()
        sendFin()
        close()
    }

    /**
     * Handle RST from the app.
     */
    fun onRst() {
        close()
    }

    /**
     * Read available data from the remote server and write it back
     * to the TUN as an IP+TCP packet destined for the app.
     *
     * @return number of bytes read, 0 if nothing available, -1 if EOF/error
     */
    fun readFromRemote(): Int {
        if (state != TcpState.ESTABLISHED) return -1
        val ch = channel ?: return -1

        return try {
            readBuffer.clear()
            val bytesRead = ch.read(readBuffer)

            when {
                bytesRead > 0 -> {
                    readBuffer.flip()
                    val data = ByteArray(bytesRead)
                    readBuffer.get(data)

                    // Build response IP+TCP packet and write to TUN
                    val packet = IpPacketBuilder.buildTcpPacket(
                        srcIp = dstIp,       // remote server → app
                        srcPort = dstPort,
                        dstIp = srcIp,       // → VPN interface
                        dstPort = srcPort,
                        seqNum = mySeqNum,
                        ackNum = myAckNum,
                        flags = IpPacketBuilder.TCP_FLAG_ACK or IpPacketBuilder.TCP_FLAG_PSH,
                        payload = data
                    )

                    synchronized(tunOutput) {
                        tunOutput.write(packet)
                    }

                    mySeqNum = (mySeqNum + bytesRead) and 0xFFFFFFFFL
                    totalBytesReceived += bytesRead
                    lastActivity = System.currentTimeMillis()

                    bytesRead
                }
                bytesRead == 0 -> 0
                else -> {
                    // Remote closed connection — send FIN to app
                    sendFin()
                    state = TcpState.FIN_WAIT
                    -1
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Read from remote failed: ${e.message}")
            sendRst()
            close()
            -1
        }
    }

    /**
     * Check if this session is active and eligible for polling.
     */
    fun isActive(): Boolean = state == TcpState.ESTABLISHED

    /**
     * Check if this session is closed and should be removed.
     */
    fun isClosed(): Boolean = state == TcpState.CLOSED

    /**
     * Get the underlying SocketChannel for NIO selector registration.
     */
    fun getChannel(): SocketChannel? = channel

    /**
     * Close the session and release resources.
     */
    fun close() {
        state = TcpState.CLOSED
        try {
            channel?.close()
        } catch (_: Exception) {}
        channel = null
    }

    // ── Packet synthesis ──────────────────────────────────────────────────────

    private fun sendSynAck() {
        val packet = IpPacketBuilder.buildTcpPacket(
            srcIp = dstIp, srcPort = dstPort,
            dstIp = srcIp, dstPort = srcPort,
            seqNum = mySeqNum,
            ackNum = myAckNum,
            flags = IpPacketBuilder.TCP_FLAG_SYN or IpPacketBuilder.TCP_FLAG_ACK,
            windowSize = INITIAL_WINDOW
        )
        mySeqNum = (mySeqNum + 1) and 0xFFFFFFFFL // SYN consumes one sequence number
        writeTun(packet)
    }

    private fun sendAck() {
        val packet = IpPacketBuilder.buildTcpPacket(
            srcIp = dstIp, srcPort = dstPort,
            dstIp = srcIp, dstPort = srcPort,
            seqNum = mySeqNum,
            ackNum = myAckNum,
            flags = IpPacketBuilder.TCP_FLAG_ACK,
            windowSize = INITIAL_WINDOW
        )
        writeTun(packet)
    }

    private fun sendFin() {
        val packet = IpPacketBuilder.buildTcpPacket(
            srcIp = dstIp, srcPort = dstPort,
            dstIp = srcIp, dstPort = srcPort,
            seqNum = mySeqNum,
            ackNum = myAckNum,
            flags = IpPacketBuilder.TCP_FLAG_FIN or IpPacketBuilder.TCP_FLAG_ACK
        )
        mySeqNum = (mySeqNum + 1) and 0xFFFFFFFFL // FIN consumes one sequence number
        writeTun(packet)
    }

    private fun sendRst() {
        try {
            val packet = IpPacketBuilder.buildTcpPacket(
                srcIp = dstIp, srcPort = dstPort,
                dstIp = srcIp, dstPort = srcPort,
                seqNum = mySeqNum,
                ackNum = myAckNum,
                flags = IpPacketBuilder.TCP_FLAG_RST or IpPacketBuilder.TCP_FLAG_ACK
            )
            writeTun(packet)
        } catch (_: Exception) {}
    }

    private fun writeTun(packet: ByteArray) {
        try {
            synchronized(tunOutput) {
                tunOutput.write(packet)
            }
        } catch (e: Exception) {
            Log.d(TAG, "TUN write failed: ${e.message}")
        }
    }
}
