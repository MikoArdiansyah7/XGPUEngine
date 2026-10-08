package com.xgpu.engine.overlay

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.xgpu.engine.R

/**
 * Tompel / Dynamic Island Overlay Service
 * Menampilkan tompel mirip Dynamic Island di posisi kamera.
 *
 * CARA EDIT POSISI DEFAULT:
 * - Portrait: atur dari app (field Geser X/Y portrait)
 * - Landscape kiri: atur dari app (field Landscape Kiri)
 * - Landscape kanan: atur dari app (field Landscape Kanan)
 *
 * CARA EDIT UKURAN DEFAULT:
 * - Cari dpToPx((56 * scale) dan dpToPx((120 * scale) - ubah 56/120
 *
 * CARA GANTI GAMBAR TOMPEL:
 * - Ganti file di drawable: tompel_active.png, tompel_inactive.png
 * - Dan versi landscape: tompel_active_land.png, tompel_inactive_land.png
 * - Dan versi flip: tompel_active_flip.png, dst
 *
 * DETEKSI ORIENTASI:
 * - ROTATION_0 = portrait
 * - ROTATION_90 = landscape kiri (kamera di kiri layar)
 * - ROTATION_270 = landscape kanan (kamera di kanan layar)
 */
class TompelOverlayService : Service() {

    companion object {
        const val CHANNEL_ID   = "tompel_overlay"
        const val EXTRA_IMAGE  = "tompel_image"
        const val EXTRA_PORT_X = "port_x"
        const val EXTRA_PORT_Y = "port_y"
        const val EXTRA_LAND_X  = "land_x"   // landscape kiri
        const val EXTRA_LAND_Y  = "land_y"
        const val EXTRA_LAND_RX = "land_rx"  // landscape kanan
        const val EXTRA_LAND_RY = "land_ry"
        const val EXTRA_SIZE   = "tompel_size"
        const val EXTRA_FLIP   = "tompel_flip"  // flip vertikal (180 derajat)
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentImage = "active"
    private var portX = 0;  private var portY = 0
    private var landX = 0;  private var landY = 0
    private var landRX = 0; private var landRY = 0
    private var tompelSize = 100
    private var tompelFlip = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            currentImage = it.getStringExtra(EXTRA_IMAGE) ?: currentImage
            portX  = it.getIntExtra(EXTRA_PORT_X,  portX)
            portY  = it.getIntExtra(EXTRA_PORT_Y,  portY)
            landX  = it.getIntExtra(EXTRA_LAND_X,  landX)
            landY  = it.getIntExtra(EXTRA_LAND_Y,  landY)
            landRX = it.getIntExtra(EXTRA_LAND_RX, landRX)
            landRY = it.getIntExtra(EXTRA_LAND_RY, landRY)
            tompelSize = it.getIntExtra(EXTRA_SIZE, tompelSize)
        }
        overlayView?.let { updateAll() }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(3, buildNotification())
        showOverlay()
    }

    private fun getRotation(): Int {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return wm.defaultDisplay.rotation
    }

    // Surface.ROTATION_0 = portrait
    // Surface.ROTATION_90 = landscape kiri (home kanan) — kamera biasanya di kanan layar
    // Surface.ROTATION_270 = landscape kanan (home kiri) — kamera biasanya di kiri layar
    private fun isPortrait() = getRotation() == Surface.ROTATION_0 || getRotation() == Surface.ROTATION_180
    private fun isLandLeft() = getRotation() == Surface.ROTATION_90   // miring kiri
    private fun isLandRight() = getRotation() == Surface.ROTATION_270  // miring kanan

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_tompel, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, params)
        updateAll()
    }

    private fun updateAll() {
        updateImage()
        updatePosition()
    }

    private fun updateImage() {
        val iv = overlayView?.findViewById<ImageView>(R.id.ivTompel) ?: return
        val scale = tompelSize / 100f
        val isLand = !isPortrait()

        // Pakai gambar yang sudah di-flip dari awal, tidak pakai rotation runtime
        // Landscape kiri (ROTATION_90) pakai _land, landscape kanan (ROTATION_270) pakai _land_right
        val isLandRight = isLandRight()
        val res = when {
            // Portrait
            !isLand && !tompelFlip && currentImage == "active"  -> R.drawable.tompel_active
            !isLand &&  tompelFlip && currentImage == "active"  -> R.drawable.tompel_active_flip
            !isLand && !tompelFlip                              -> R.drawable.tompel_inactive
            !isLand &&  tompelFlip                              -> R.drawable.tompel_inactive_flip
            // Landscape kanan (gambar baru)
            isLandRight && !tompelFlip && currentImage == "active"  -> R.drawable.tompel_active_land_right
            isLandRight &&  tompelFlip && currentImage == "active"  -> R.drawable.tompel_active_land_right_flip
            isLandRight && !tompelFlip                              -> R.drawable.tompel_inactive_land_right
            isLandRight &&  tompelFlip                              -> R.drawable.tompel_inactive_land_right_flip
            // Landscape kiri (gambar lama)
            !tompelFlip && currentImage == "active"             -> R.drawable.tompel_active_land
             tompelFlip && currentImage == "active"             -> R.drawable.tompel_active_land_flip
            !tompelFlip                                         -> R.drawable.tompel_inactive_land
            else                                                -> R.drawable.tompel_inactive_land_flip
        }
        iv.setImageResource(res)
        iv.rotation = 0f

        val lp = iv.layoutParams
        when {
            !isLand -> {
                // Portrait: horizontal shape
                lp.width  = dpToPx((120 * scale).toInt())
                lp.height = dpToPx((56 * scale).toInt())
            }
            isLandRight() -> {
                // Landscape kanan: gambar vertikal sama seperti kiri
                lp.width  = dpToPx((56 * scale).toInt())
                lp.height = dpToPx((120 * scale).toInt())
            }
            else -> {
                // Landscape kiri: gambar vertikal
                lp.width  = dpToPx((56 * scale).toInt())
                lp.height = dpToPx((120 * scale).toInt())
            }
        }
        iv.layoutParams = lp
    }

    private fun updatePosition() {
        val p = params ?: return
        val view = overlayView ?: return

        when {
            isPortrait() -> {
                // Portrait - atas tengah
                p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                p.x = dpToPx(portX)
                p.y = dpToPx(portY)
            }
            isLandLeft() -> {
                // Miring kiri (ROTATION_90) - kamera ada di KIRI layar
                p.gravity = Gravity.TOP or Gravity.START
                p.x = dpToPx(landX)
                p.y = dpToPx(landY)
            }
            isLandRight() -> {
                // Miring kanan (ROTATION_270) - kamera ada di KANAN layar
                p.gravity = Gravity.TOP or Gravity.END
                p.x = dpToPx(landRX)
                p.y = dpToPx(landRY)
            }
        }

        try { windowManager.updateViewLayout(view, p) } catch (e: Exception) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.postDelayed({ updateAll() }, 200)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Dynamic Island", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Settings - Dynamic Island")
            .setContentText("Tompel aktif")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
