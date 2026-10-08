package com.xgpu.engine.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.xgpu.engine.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * FPS Overlay Service
 * Menampilkan fake FPS di overlay yang mengambang di atas semua app.
 *
 * CARA EDIT FPS RANGE:
 * - Mode 185: cari buildPool185() - ubah angka nextInt(165,175), nextInt(175,180), nextInt(180,186)
 * - Mode 145: cari buildPool145() - ubah angka nextInt(140,143), 143, nextInt(144,146)
 * - Interval update FPS: cari "(800 + Random.nextInt(400))" - ubah angka dalam ms
 *
 * CARA EDIT WARNA FPS:
 * - Cari getFakeColor() - ubah nilai hex warna
 */
class FpsOverlayService : Service() {

    companion object {
        const val EXTRA_MODE = "fps_mode"
        const val MODE_185 = "mode_185"
        const val MODE_145 = "mode_145"
        const val MODE_120 = "mode_120"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fpsMode = MODE_185

    private var currentFakeFps = 180
    private var currentGameFps = 120f
    private var dipCountdown = 0

    private val fpsPool185 by lazy { buildPool185() }
    private val fpsPool145 by lazy { buildPool145() }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // === POOL FPS MODE 185: ubah angka di sini untuk ganti range FPS ===
    private fun buildPool185(): IntArray {
        val p = mutableListOf<Int>()
        repeat(5)  { p.add(Random.nextInt(165, 175)) }
        repeat(15) { p.add(Random.nextInt(175, 180)) }
        repeat(80) { p.add(Random.nextInt(180, 186)) }
        return p.toIntArray()
    }

    // === POOL FPS MODE 145: ubah angka di sini untuk ganti range FPS ===
    private fun buildPool145(): IntArray {
        val p = mutableListOf<Int>()
        repeat(5)  { p.add(Random.nextInt(140, 143)) }
        repeat(10) { p.add(143) }
        repeat(85) { p.add(if (Random.nextBoolean()) 144 else 145) }
        return p.toIntArray()
    }

    private fun nextGameFps(): Float {
        if (dipCountdown > 0) { dipCountdown--; return 119f }
        if (Random.nextInt(100) < 3) { dipCountdown = Random.nextInt(1, 3); return 119f }
        return 120f
    }

    // Get real battery info
    private fun getBatteryInfo(): Triple<Int, Boolean, Float> {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Temperature: real value in tenths of degree Celsius, minus 10°C offset
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = (tempRaw / 10f) - 10f  // subtract 10°C as requested

        return Triple(pct, charging, tempC)
    }

    private fun formatBattery(pct: Int, charging: Boolean): Pair<String, Int> {
        return when {
            charging -> Pair("🔋$pct%⚡charging", Color.WHITE)
            pct <= 19 -> Pair("🔋$pct%⚠️low power", Color.parseColor("#FF4444"))
            pct <= 39 -> Pair("🔋$pct%", Color.parseColor("#FFB800"))
            else -> Pair("🔋$pct%", Color.WHITE)
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            when (fpsMode) {
                MODE_185, MODE_145 -> updateSimpleFps()
                MODE_120 -> updateGraphFps()
            }
            handler.postDelayed(this, (800 + Random.nextInt(400)).toLong())
        }
    }

    private fun updateSimpleFps() {
        val pool = if (fpsMode == MODE_185) fpsPool185 else fpsPool145
        val target = pool[Random.nextInt(pool.size)]
        currentFakeFps = when {
            target > currentFakeFps + 3 -> currentFakeFps + Random.nextInt(1, 4)
            target < currentFakeFps - 3 -> currentFakeFps - Random.nextInt(1, 4)
            else -> target
        }.coerceIn(if (fpsMode == MODE_185) 165 else 140, if (fpsMode == MODE_185) 185 else 145)

        overlayView?.findViewById<TextView>(R.id.tvFpsValue)?.apply {
            text = "$currentFakeFps"
            setTextColor(getFakeColor(currentFakeFps))
        }
    }

    private fun updateGraphFps() {
        currentGameFps = nextGameFps()

        val targetFake = if (currentGameFps < 120f) {
            (currentFakeFps - Random.nextInt(2, 8)).coerceAtLeast(165)
        } else {
            fpsPool185[Random.nextInt(fpsPool185.size)]
        }

        currentFakeFps = when {
            targetFake > currentFakeFps + 3 -> currentFakeFps + Random.nextInt(1, 4)
            targetFake < currentFakeFps - 3 -> currentFakeFps - Random.nextInt(2, 5)
            else -> targetFake
        }.coerceIn(165, 185)

        overlayView?.findViewById<FpsGraphView>(R.id.fpsGraphView)
            ?.addDataPoint(currentGameFps, currentFakeFps.toFloat())
        overlayView?.findViewById<TextView>(R.id.tvGameFps)?.text = "${currentGameFps.toInt()}"
        overlayView?.findViewById<TextView>(R.id.tvFakeFps)?.text = "$currentFakeFps"

        // Battery + temp + time
        try {
            val (pct, charging, tempC) = getBatteryInfo()
            val (battText, battColor) = formatBattery(pct, charging)

            overlayView?.findViewById<TextView>(R.id.tvBatteryInfo)?.apply {
                text = battText
                setTextColor(battColor)
            }

            val tempDisplay = "🌡️${"%.0f".format(tempC)}°C"
            overlayView?.findViewById<TextView>(R.id.tvTempInfo)?.text = tempDisplay

        } catch (e: Exception) { /* ignore */ }

        // Real time 24h
        val timeStr = "🕔${timeFormat.format(Date())}"
        overlayView?.findViewById<TextView>(R.id.tvTimeInfo)?.text = timeStr
    }

    private fun getFakeColor(fps: Int): Int = when {
        fpsMode == MODE_185 -> when {
            fps >= 180 -> Color.parseColor("#00FF88")
            fps >= 175 -> Color.parseColor("#AAFF44")
            else       -> Color.parseColor("#FFB800")
        }
        else -> when {
            fps >= 144 -> Color.parseColor("#00CFFF")
            fps >= 143 -> Color.parseColor("#44AAFF")
            else       -> Color.parseColor("#FFB800")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        fpsMode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_185
        currentFakeFps = if (fpsMode == MODE_185) 180 else 144
        currentGameFps = 120f
        dipCountdown = 0

        overlayView?.post {
            val simple = overlayView?.findViewById<View>(R.id.layoutSimpleFps)
            val graph = overlayView?.findViewById<View>(R.id.layoutGraphFps)
            if (fpsMode == MODE_120) {
                simple?.visibility = View.GONE
                graph?.visibility = View.VISIBLE
                overlayView?.findViewById<FpsGraphView>(R.id.fpsGraphView)?.clear()
            } else {
                simple?.visibility = View.VISIBLE
                graph?.visibility = View.GONE
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        showOverlay()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fps, null)

        val isGraph = fpsMode == MODE_120
        overlayView?.findViewById<View>(R.id.layoutSimpleFps)?.visibility =
            if (isGraph) View.GONE else View.VISIBLE
        overlayView?.findViewById<View>(R.id.layoutGraphFps)?.visibility =
            if (isGraph) View.VISIBLE else View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = 80 }

        windowManager.addView(overlayView, params)
        setupDrag(overlayView!!, params)
        handler.post(updateRunnable)
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var sx = 0f; var sy = 0f; var spx = 0; var spy = 0
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; spx = params.x; spy = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = spx + (e.rawX - sx).toInt()
                    params.y = spy + (e.rawY - sy).toInt()
                    windowManager.updateViewLayout(view, params); true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        overlayView?.let { windowManager.removeView(it) }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel("fps_overlay", "XGPU Engine", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val label = when (fpsMode) { MODE_185 -> "185 FPS"; MODE_145 -> "145 FPS"; else -> "Grafik FPS" }
        return NotificationCompat.Builder(this, "fps_overlay")
            .setContentTitle("Settings - XGPU Engine")
            .setContentText("$label aktif")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
