package com.hashcott.ghostline

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import java.io.FileDescriptor
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class GhostlineVpnService : VpnService() {
    companion object {
        const val ACTION_STATUS = "com.hashcott.ghostline.STATUS"
        const val EXTRA_UPSTREAM = "upstream"
        private const val CHANNEL = "ghostline_vpn"
        private const val DEFAULT_UPSTREAM = "1.1.1.1"
    }

    private var iface: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var upstream: String = DEFAULT_UPSTREAM
    private val running = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Ghostline", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.get()) return START_STICKY
        upstream = intent?.getStringExtra(EXTRA_UPSTREAM) ?: DEFAULT_UPSTREAM
        startForeground(7, notification("Ghostline DNS protection is active"))
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            iface = Builder()
                .setSession("Ghostline")
                .setMtu(1500)
                .addAddress("10.0.0.1", 32)
                .addDnsServer("10.0.0.2")
                .addRoute("10.0.0.2", 32)
                .setBlocking(true)
                .establish()

            if (iface == null) throw IllegalStateException("VPN establish returned null")
            running.set(true)
            broadcast(true, "VPN started; DNS -> 10.0.0.2")
            worker = Thread({ loop(iface!!.fileDescriptor) }, "Ghostline-DNS")
            worker!!.start()
        } catch (e: Exception) {
            broadcast(false, "Start failed: ${e.message}")
            stopSelf()
        }
    }

    private fun loop(fd: FileDescriptor) {
        val input = FileInputStreamCompat.open(fd)
        val output = FileOutputStreamCompat.open(fd)
        val packet = ByteArray(32767)
        val dns = DatagramSocket()
        protect(dns)
        dns.soTimeout = 2500

        try {
            while (running.get()) {
                val n = input.read(packet)
                if (n <= 0) continue
                val q = DnsPacket.parse(packet, n) ?: continue
                if (q.protocol != 17 || q.dstPort != 53) continue

                val request = DatagramPacket(packet, q.payloadOffset, q.payloadLength, InetAddress.getByName(upstream), 53)
                dns.send(request)
                val buf = ByteArray(4096)
                val response = DatagramPacket(buf, buf.size)
                try {
                    dns.receive(response)
                    val out = DnsPacket.response(q, response.data, response.length)
                    output.write(out)
                } catch (_: SocketTimeoutException) {
                    broadcast(true, "DNS timeout")
                }
            }
        } catch (_: Exception) {
        } finally {
            dns.close()
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Ghostline")
            .setContentText(text)
            .setSmallIcon(com.hashcott.ghostline.R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        worker?.interrupt()
        iface?.close()
        iface = null
        broadcast(false, "VPN stopped")
        super.onDestroy()
    }

    private fun broadcast(connected: Boolean, log: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName)
            .putExtra("connected", connected).putExtra("log", log))
    }

}

private object FileInputStreamCompat {
    fun open(fd: FileDescriptor) = java.io.FileInputStream(fd)
}
private object FileOutputStreamCompat {
    fun open(fd: FileDescriptor) = java.io.FileOutputStream(fd)
}
