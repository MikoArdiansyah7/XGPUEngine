package com.xgpu.engine.overlay

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import com.xgpu.engine.R
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Waifu Overlay Service
 * Menampilkan notif waifu untuk ping, baterai, suhu.
 *
 * CARA EDIT:
 * - Ganti server ping: ubah PING_HOST (sekarang Cloudflare 1.1.1.1)
 * - Interval ping: ubah PING_INTERVAL_MS (sekarang 1 menit)
 * - Durasi notif tampil: ubah SHOW_DURATION_MS (sekarang 5 detik)
 * - Threshold lowbat: ubah LOW_BATT_THRESHOLD (sekarang 34%)
 * - Countdown update: ubah CD_STEP_MS (sekarang tiap 5 detik)
 * - Kurangi suhu: cari "(tempRaw / 10f) - 10f" - ubah angka 10f
 *
 * CARA GANTI TEKS NOTIF:
 * - Cari showNotif( di dalam pingRunnable dan batteryReceiver
 *
 * CARA GANTI FOTO:
 * - Foto lowbat random: ganti file waifu_low_1.png sampai waifu_low_7.png di drawable
 * - Foto kondisi lain: ganti imgPingGood, imgPingMid, dst di bawah companion object
 */
class WaifuOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "waifu_overlay"
        // Cloudflare DNS - lebih cepat dari Google
        // === GANTI SERVER PING DI SINI (1.1.1.1 = Cloudflare, 8.8.8.8 = Google) ===
        const val PING_HOST = "1.1.1.1"
        const val PING_INTERVAL_MS = 60 * 1000L  // 1 menit
        const val SHOW_DURATION_MS = 10000L        // 10 detik notif
        const val CD_STEP_MS = 5000L               // countdown update tiap 5 detik
        const val LOW_BATT_THRESHOLD = 34
    }

    private lateinit var windowManager: WindowManager

    // Tiga overlay: cd (selalu ada), notif (reload), battPct (reload)
    private var cdView: View? = null
    private var notifView: View? = null
    private var battPctView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var lastBatteryLevel = -1
    private var lastChargingState = false
    private var batteryReceiverRegistered = false
    private var lowBatteryAlertRunnable: Runnable? = null
    private var cdSecondsLeft = 60  // tidak dipakai tapi tetap ada
    private var cdVisible = true  // true = tampil, false = transparan
    // Tracking tiap 5% baterai
    private var lastNotifiedBattPct5 = -1  // terakhir notif di kelipatan 5% berapa

    // 14 foto baterai per 5% untuk CD overlay - random
    private val imagesBattPct = intArrayOf(
        R.drawable.waifu_batt_pct_100, R.drawable.waifu_batt_pct_95,
        R.drawable.waifu_batt_pct_90,  R.drawable.waifu_batt_pct_85,
        R.drawable.waifu_batt_pct_80,  R.drawable.waifu_batt_pct_75,
        R.drawable.waifu_batt_pct_70,  R.drawable.waifu_batt_pct_65,
        R.drawable.waifu_batt_pct_60,  R.drawable.waifu_batt_pct_55,
        R.drawable.waifu_batt_pct_50,  R.drawable.waifu_batt_pct_45,
        R.drawable.waifu_batt_pct_40,  R.drawable.waifu_batt_pct_35
    )
    private fun randBattPct() = imagesBattPct[Random.nextInt(imagesBattPct.size)]

    // 21 foto untuk lowbat alert - random
    private val imagesLow = intArrayOf(
        R.drawable.waifu_low_1,  R.drawable.waifu_low_2,  R.drawable.waifu_low_3,
        R.drawable.waifu_low_4,  R.drawable.waifu_low_5,  R.drawable.waifu_low_6,
        R.drawable.waifu_low_7,  R.drawable.waifu_low_8,  R.drawable.waifu_low_9,
        R.drawable.waifu_low_10, R.drawable.waifu_low_11, R.drawable.waifu_low_12,
        R.drawable.waifu_low_13, R.drawable.waifu_low_14, R.drawable.waifu_low_15,
        R.drawable.waifu_low_16, R.drawable.waifu_low_17, R.drawable.waifu_low_18,
        R.drawable.waifu_low_19, R.drawable.waifu_low_20, R.drawable.waifu_low_21
    )

    // Foto tetap per kondisi
    private val imgPingGood   = R.drawable.waifu_ping_good
    private val imgPingMid    = R.drawable.waifu_ping_mid
    private val imgPingBad    = R.drawable.waifu_ping_bad
    private val imgBattFull   = R.drawable.waifu_batt_full
    private val imgCharging   = R.drawable.waifu_batt_charging
    private val imgUnplug     = R.drawable.waifu_batt_unplug

    private fun randLow() = imagesLow[Random.nextInt(imagesLow.size)]

    // ==================== BATTERY RECEIVER ====================
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct    = if (scale > 0) level * 100 / scale else 0
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL
            val isFull = pct >= 100 || status == BatteryManager.BATTERY_STATUS_FULL

            // Baterai penuh
            if (isFull && lastBatteryLevel < 100)
                showNotif("🔋 Baterai Penuh Sayanggg 💚", "100% - Cabut charger ya~", "#00FF88", imgBattFull)

            // Mulai cas
            if (isCharging && !lastChargingState)
                showNotif("⚡ Sedang Mengisi Daya 💙", "$pct% - Aktifin bypass charging ya sayang~", "#00CFFF", imgCharging)

            // Cabut cas
            if (!isCharging && lastChargingState && !isFull)
                showNotif("🔌 Charger Dicabut Sayang 🤍", "$pct% - Jangan lupa hemat ya~", "#FFFFFF", imgUnplug)

            // Lowbat pertama kali
            if (pct <= LOW_BATT_THRESHOLD && lastBatteryLevel > LOW_BATT_THRESHOLD && !isCharging)
                startLowBatteryAlert(pct)

            // Stop alert kalau sudah cas
            if (isCharging && lowBatteryAlertRunnable != null) stopLowBatteryAlert()

            // Lowbat turun 1% lagi
            if (!isCharging && pct <= LOW_BATT_THRESHOLD && pct < lastBatteryLevel && lastBatteryLevel <= LOW_BATT_THRESHOLD)
                startLowBatteryAlert(pct)

            // Notif tiap turun 5% (100,95,90,...,35) - hanya saat tidak cas
            if (!isCharging) {
                val pct5 = (pct / 5) * 5  // bulatkan ke bawah ke kelipatan 5
                if (pct5 != lastNotifiedBattPct5 && pct5 in 35..100) {
                    lastNotifiedBattPct5 = pct5
                    showBattPctNotif(pct5)
                }
            }

            lastBatteryLevel  = pct
            lastChargingState = isCharging
        }
    }

    private fun startLowBatteryAlert(pct: Int) {
        stopLowBatteryAlert()
        lowBatteryAlertRunnable = object : Runnable {
            override fun run() {
                if (lastBatteryLevel > LOW_BATT_THRESHOLD || lastChargingState) {
                    stopLowBatteryAlert(); return
                }
                showNotif("🔋 Baterai Lowbat Sayang! 💛",
                    "${lastBatteryLevel}% - Cas sekarang ya~", "#FFB800", randLow())
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(lowBatteryAlertRunnable!!)
    }

    private fun stopLowBatteryAlert() {
        lowBatteryAlertRunnable?.let { handler.removeCallbacks(it) }
        lowBatteryAlertRunnable = null
    }

    // ==================== PING CHECK ====================
    private val pingRunnable = object : Runnable {
        override fun run() {
            // Reset countdown
            cdSecondsLeft = (PING_INTERVAL_MS / 1000).toInt()

            // Jalankan ping di background thread
            executor.execute {
                val ping = measurePing()
                val (battPct, _, tempC) = getBatteryInfo()
                val infoLine = "${battPct}%  🌡️${"%.0f".format(tempC)}°C"

                handler.post {
                    when {
                        ping < 0   -> showNotif("❌ Tidak Ada Jaringan Sayang",
                            "Cek koneksi  •  $infoLine", "#FF4444", imgPingBad)
                        ping < 50  -> showNotif("✅ PING Stabil Sayanggg 💚",
                            "${ping}ms  •  🔋$infoLine", "#00FF88", imgPingGood)
                        ping < 150 -> showNotif("⚠️ PING Sedikit Lag Yankk 💛",
                            "${ping}ms  •  🔋$infoLine", "#FFB800", imgPingMid)
                        else       -> showNotif("🔴 PING Lag Parah Yankk ❤️",
                            "${ping}ms  •  🔋$infoLine", "#FF4444", imgPingBad)
                    }
                }
            }
            handler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    // Countdown update tiap 5 detik
    private val cdRunnable = object : Runnable {
        override fun run() {
            // Update foto CD tanpa countdown detik
            updateCdView()
            handler.postDelayed(this, CD_STEP_MS)
        }
    }

    private fun updateCdView() {
        cdView ?: return
        // Update countdown text
        // Random foto dari 14 foto baterai per 5%
        cdView?.findViewById<ImageView>(R.id.ivCdWaifu)?.setImageResource(randBattPct())
    }

    // ==================== OVERLAY MANAGEMENT ====================

    // Buat notif view saat akan ditampilkan (reload = ringan di memori)
    private fun createNotifView(): View {
        return LayoutInflater.from(this).inflate(R.layout.overlay_waifu, null)
    }

    private fun showNotif(message: String, detail: String, color: String, imgRes: Int) {
        // Buat view baru setiap kali (reload - lebih ringan dari keep di memori)
        if (notifView != null) {
            try { windowManager.removeView(notifView) } catch (e: Exception) {}
            notifView = null
        }

        val view = createNotifView()
        view.findViewById<TextView>(R.id.tvWaifuMessage)?.apply {
            text = message
            setTextColor(android.graphics.Color.parseColor(color))
        }
        view.findViewById<TextView>(R.id.tvWaifuDetail)?.text = detail
        view.findViewById<ImageView>(R.id.ivWaifu)?.setImageResource(imgRes)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 120 }

        try {
            windowManager.addView(view, params)
            notifView = view
        } catch (e: Exception) { return }

        // Auto hide + hapus dari memory setelah 5 detik
        handler.removeCallbacksAndMessages("hide_notif")
        handler.postAtTime({
            try { windowManager.removeView(notifView) } catch (e: Exception) {}
            notifView = null
        }, "hide_notif", SystemClock.uptimeMillis() + SHOW_DURATION_MS)
    }

    // Toggle tampil/transparan CD overlay - service tetap jalan
    fun toggleCdVisibility() {
        cdVisible = !cdVisible
        cdView?.alpha = if (cdVisible) 1f else 0f
    }

    fun setCdVisible(visible: Boolean) {
        cdVisible = visible
        cdView?.alpha = if (visible) 1f else 0f
    }

    private fun showCdOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        cdView = inflater.inflate(R.layout.overlay_waifu_cd, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 200 }

        windowManager.addView(cdView, params)
        updateCdView()
    }

    // ==================== BATTERY PCT PER 5% ====================
    // Map foto per persentase - tiap 5% punya foto sendiri
    private fun getBattPctImage(pct: Int): Int {
        return when(pct) {
            100  -> R.drawable.waifu_batt_pct_100
            95   -> R.drawable.waifu_batt_pct_95
            90   -> R.drawable.waifu_batt_pct_90
            85   -> R.drawable.waifu_batt_pct_85
            80   -> R.drawable.waifu_batt_pct_80
            75   -> R.drawable.waifu_batt_pct_75
            70   -> R.drawable.waifu_batt_pct_70
            65   -> R.drawable.waifu_batt_pct_65
            60   -> R.drawable.waifu_batt_pct_60
            55   -> R.drawable.waifu_batt_pct_55
            50   -> R.drawable.waifu_batt_pct_50
            45   -> R.drawable.waifu_batt_pct_45
            40   -> R.drawable.waifu_batt_pct_40
            35   -> R.drawable.waifu_batt_pct_35
            else -> R.drawable.waifu_batt_pct_35
        }
    }

    private fun showBattPctNotif(pct: Int) {
        // Hapus view lama
        battPctView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        battPctView = null

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_batt_pct, null)

        // Warna berdasarkan level
        val (color, emoji) = when {
            pct >= 80 -> Pair("#00FF88", "🔋")
            pct >= 60 -> Pair("#AAFF44", "🔋")
            pct >= 40 -> Pair("#FFB800", "🪫")
            else      -> Pair("#FF5252", "🪫")
        }

        // Pesan berdasarkan level
        val detail = when {
            pct == 100 -> "Penuh banget sayang~"
            pct >= 80  -> "Masih banyak, santai~"
            pct >= 60  -> "Masih oke sayang~"
            pct >= 40  -> "Lumayan nih sayang~"
            else       -> "Mulai dikit nih sayang~"
        }

        view.findViewById<TextView>(R.id.tvBattPctMsg)?.apply {
            text = "$emoji Baterai $pct%"
            setTextColor(android.graphics.Color.parseColor(color))
        }
        view.findViewById<TextView>(R.id.tvBattPctDetail)?.text = detail
        view.findViewById<ImageView>(R.id.ivBattPct)?.setImageResource(getBattPctImage(pct))

        // Y position: portrait=80, landscape lebih bawah=140
        val isLandscapeBatt = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val battY = if (isLandscapeBatt) 20 else 8

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            y = battY
        }

        try {
            windowManager.addView(view, params)
            battPctView = view
        } catch (e: Exception) { return }

        // Auto hide 5 detik
        handler.removeCallbacksAndMessages("hide_batt_pct")
        handler.postAtTime({
            battPctView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
            battPctView = null
        }, "hide_batt_pct", android.os.SystemClock.uptimeMillis() + 10000L)
    }

    // ==================== BATTERY INFO ====================
    private fun getBatteryInfo(): Triple<Int, Boolean, Float> {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct   = if (scale > 0) level * 100 / scale else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        // Kurangi 10°C dari suhu asli
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = (tempRaw / 10f) - 10f
        return Triple(pct, charging, tempC)
    }

    private fun measurePing(): Long {
        return try {
            val start = System.currentTimeMillis()
            val ok = InetAddress.getByName(PING_HOST).isReachable(3000)
            if (ok) System.currentTimeMillis() - start else -1L
        } catch (e: Exception) { -1L }
    }

    // ==================== LIFECYCLE ====================
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle CD visibility toggle dari MainActivity
        if (intent?.hasExtra("cd_visible") == true) {
            val visible = intent.getBooleanExtra("cd_visible", true)
            cdVisible = visible
            handler.post { cdView?.alpha = if (visible) 1f else 0f }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, buildNotification())
        showCdOverlay()
        registerBatteryReceiver()
        // Langsung ping saat start
        handler.post(pingRunnable)
        // Countdown update tiap 5 detik
        handler.postDelayed(cdRunnable, CD_STEP_MS)
    }

    private fun registerBatteryReceiver() {
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiverRegistered = true
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        lastBatteryLevel = if (scale > 0) level * 100 / scale else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        lastChargingState = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stopLowBatteryAlert()
        executor.shutdown()
        if (batteryReceiverRegistered) try { unregisterReceiver(batteryReceiver) } catch (e: Exception) {}
        cdView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        notifView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        battPctView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Waifu Notifier", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Settings - Waifu Notifier")
            .setContentText("Memantau ping & baterai")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
