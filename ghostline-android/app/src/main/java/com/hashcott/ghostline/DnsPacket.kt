package com.hashcott.ghostline

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DnsQuery(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
    val identification: Int,
    val protocol: Int
)

object DnsPacket {
    fun parse(p: ByteArray, n: Int): DnsQuery? {
        if (n < 28) return null
        val version = (p[0].toInt() ushr 4) and 15
        val ihl = (p[0].toInt() and 15) * 4
        if (version != 4 || ihl < 20 || n < ihl + 8) return null
        if ((p[9].toInt() and 255) != 17) return null
        val src = p.copyOfRange(12, 16)
        val dst = p.copyOfRange(16, 20)
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        val udpLen = u16(p, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > n) return null
        return DnsQuery(src, dst, srcPort, dstPort, ihl + 8, udpLen - 8, u16(p, 4), 17)
    }

    fun response(q: DnsQuery, dns: ByteArray, dnsLen: Int): ByteArray {
        val ipLen = 20
        val udpLen = 8 + dnsLen
        val total = ipLen + udpLen
        val out = ByteArray(total)
        out[0] = 0x45
        out[1] = 0
        put16(out, 2, total)
        put16(out, 4, q.identification)
        put16(out, 6, 0)
        out[8] = 64
        out[9] = 17
        System.arraycopy(q.dstIp, 0, out, 12, 4)
        System.arraycopy(q.srcIp, 0, out, 16, 4)
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, q.dstPort)
        put16(out, 22, q.srcPort)
        put16(out, 24, udpLen)
        put16(out, 26, 0)
        System.arraycopy(dns, 0, out, 28, dnsLen)
        put16(out, 26, udpChecksum(out, 20, udpLen, q.dstIp, q.srcIp))
        return out
    }

    private fun udpChecksum(p: ByteArray, off: Int, len: Int, src: ByteArray, dst: ByteArray): Int {
        var sum = 0L
        sum += ((src[0].toInt() and 255) shl 8) or (src[1].toInt() and 255)
        sum += ((dst[0].toInt() and 255) shl 8) or (dst[1].toInt() and 255)
        sum += ((src[2].toInt() and 255) shl 8) or (src[3].toInt() and 255)
        sum += ((dst[2].toInt() and 255) shl 8) or (dst[3].toInt() and 255)
        sum += 17
        sum += len
        var i = off
        while (i + 1 < off + len) {
            sum += ((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)
            i += 2
        }
        if (i < off + len) sum += (p[i].toInt() and 255) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        val v = sum.inv().toInt() and 0xffff
        return if (v == 0) 0xffff else v
    }

    private fun checksum(p: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i + 1 < off + len) {
            sum += ((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)
            i += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun u16(p: ByteArray, i: Int) = ((p[i].toInt() and 255) shl 8) or (p[i + 1].toInt() and 255)
    private fun put16(p: ByteArray, i: Int, v: Int) {
        p[i] = (v ushr 8).toByte(); p[i + 1] = v.toByte()
    }
}
