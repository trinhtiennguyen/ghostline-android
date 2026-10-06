package com.hashcott.ghostline

import android.app.Activity
import android.content.*
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private var connected = false
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        registerReceiver(vpnReceiver, IntentFilter(GhostlineVpnService.ACTION_STATUS), RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(vpnReceiver)
        super.onDestroy()
    }

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            connected = intent.getBooleanExtra("connected", false)
            renderStatus()
            intent.getStringExtra("log")?.let { appendLog(it) }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(18))
            setBackgroundColor(0xFF07090D.toInt())
        }

        val title = TextView(this).apply {
            text = "GHOSTLINE"
            textSize = 25f
            setTextColor(0xFF00E5FF.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(48)))

        status = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8C7D9.toInt())
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)))

        val resolverLabel = TextView(this).apply {
            text = "DNS RESOLVER"
            textSize = 12f
            setTextColor(0xFF6F849A.toInt())
            setPadding(0, dp(18), 0, dp(4))
        }
        root.addView(resolverLabel)

        val resolver = Spinner(this)
        val choices = arrayOf("Cloudflare • 1.1.1.1", "Google • 8.8.8.8", "Quad9 • 9.9.9.9")
        resolver.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        root.addView(resolver, LinearLayout.LayoutParams(-1, dp(48)))

        val power = Button(this).apply {
            text = "CONNECT"
            textSize = 16f
            setOnClickListener { toggle(resolver.selectedItemPosition) }
        }
        root.addView(power, LinearLayout.LayoutParams(-1, dp(54)))

        val note = TextView(this).apply {
            text = "Android mode: DNS-only VPN. WinDivert / GoodbyeDPI / zapret2 are Windows-only and are not loaded."
            textSize = 12f
            setTextColor(0xFF7E8D9D.toInt())
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(note)

        val logTitle = TextView(this).apply {
            text = "LOG"
            textSize = 12f
            setTextColor(0xFF6F849A.toInt())
        }
        root.addView(logTitle)

        logView = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFF9BE7F5.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFF0D1118.toInt())
            gravity = Gravity.TOP
        }
        root.addView(logView, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        renderStatus()
        appendLog("Android backend ready")
    }

    private fun toggle(resolverPosition: Int) {
        if (connected) {
            stopService(Intent(this, GhostlineVpnService::class.java))
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            startActivityForResult(prepare, 1001)
        } else {
            startVpn(resolverPosition)
        }
    }

    private fun startVpn(resolverPosition: Int) {
        val upstream = when (resolverPosition) {
            1 -> "8.8.8.8"
            2 -> "9.9.9.9"
            else -> "1.1.1.1"
        }
        val intent = Intent(this, GhostlineVpnService::class.java)
            .putExtra(GhostlineVpnService.EXTRA_UPSTREAM, upstream)
        ContextCompatCompat.start(this, intent)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) startVpn(0)
    }

    private fun renderStatus() {
        status.text = if (connected) "● PROTECTED" else "○ DISCONNECTED"
        status.setTextColor(if (connected) 0xFF55FFAA.toInt() else 0xFFB8C7D9.toInt())
    }

    private fun appendLog(msg: String) {
        val line = "[${timeFmt.format(Date())}] $msg\n"
        logView.append(line)
    }
}

private object ContextCompatCompat {
    fun start(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
        else context.startService(intent)
    }
}
