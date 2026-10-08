package com.xgpu.engine.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.xgpu.engine.R
import com.xgpu.engine.databinding.ActivityMainBinding
import com.xgpu.engine.model.DeviceInfo
import com.xgpu.engine.overlay.FpsOverlayService
import com.xgpu.engine.overlay.TompelOverlayService
import com.xgpu.engine.overlay.WaifuOverlayService
import com.xgpu.engine.utils.PreferencesHelper

/**
 * MainActivity - Layar utama app
 *
 * STRUKTUR LAYAR:
 * - Tombol back (atas) → hide/show semua fitur
 * - Card device (nama, foto, v3.7) → selalu tampil
 * - Card specs (prosesor, RAM, dll) → selalu tampil
 * - Card maintainer/kernel → selalu tampil
 * - [TERSEMBUNYI saat hide]:
 *   - XGPU ENGINE (FPS overlay)
 *   - Waifu Notifier
 *   - Dynamic Island
 *   - Edit Info
 *
 * CARA TAMBAH FITUR BARU:
 * - Tambah layout di activity_main.xml dalam layoutAllFeatures
 * - Tambah logika di setupClickListeners()
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var pendingFpsMode: String = "off"
    private var tompelImage: String = "active"
    private var tompelPortX: Int = 0; private var tompelPortY: Int = 0
    private var tompelLandX: Int = 0; private var tompelLandY: Int = 0
    private var tompelLandRX: Int = 0; private var tompelLandRY: Int = 0
    private var tompelSize: Int = 100; private var tompelFlip: Boolean = false
    private var featuresVisible: Boolean = true

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.updateImage(uri)
                viewModel.deviceInfo.value?.let { PreferencesHelper.save(this, it) }
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            if (pendingFpsMode != "off" && pendingFpsMode != "waifu" && pendingFpsMode != "tompel") {
                startFpsOverlay(pendingFpsMode)
            } else if (pendingFpsMode == "waifu") {
                startWaifuService()
            }
        } else {
            binding.switchFps185.isChecked = false
            binding.switchFps145.isChecked = false
            binding.switchFps120.isChecked = false
            Toast.makeText(this, "Izin overlay diperlukan", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "Izin notifikasi diperlukan untuk overlay", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.ivDeviceImage.clipToOutline = true

        val saved = PreferencesHelper.load(this)
        viewModel.updateDeviceInfo(saved)

        setupObservers()
        setupClickListeners()
        updateEditFields(saved)
    }

    private fun setupObservers() {
        viewModel.deviceInfo.observe(this) { info -> renderDeviceInfo(info) }
    }

    private fun parseSize(s: String): Float {
        val c = s.replace(",", ".").replace(" ", "")
        return c.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    }

    private fun formatStorage(value: String): String {
        val clean = value.trim().uppercase()
        if (clean.endsWith("GB") || clean.endsWith(" GB")) {
            val num = parseSize(value)
            if (num >= 1000f) {
                val tb = num / 1024f
                return if (tb == tb.toLong().toFloat()) "${tb.toLong()} TB" else "%.1f TB".format(tb)
            }
        }
        return value.trim()
    }

    private fun calcProgress(used: String, total: String): Int {
        val u = parseSize(used); val t = parseSize(total)
        if (t <= 0f) return 0
        return ((u / t) * 100).toInt().coerceIn(0, 100)
    }

    private fun renderDeviceInfo(info: DeviceInfo) {
        binding.apply {
            tvDeviceName.text = info.deviceName
            tvCodename.text = "(${info.deviceCodename})"
            tvRomVersion.text = "${info.romVersion} - ${info.romType}"
            tvProcessor.text = info.processor
            tvDisplay.text = info.display
            tvBattery.text = info.battery
            tvCamera.text = info.camera
            tvRam.text = "${formatStorage(info.ramUsed)} / ${formatStorage(info.ramTotal)} (Aktif)"
            tvRom.text = "${formatStorage(info.romUsed)} / ${formatStorage(info.romTotal)}"
            pbRam.progress = calcProgress(info.ramUsed, info.ramTotal)
            pbRom.progress = calcProgress(info.romUsed, info.romTotal)
            tvMaintainer.text = info.maintainer
            tvSecurityPatch.text = info.securityPatch
            tvKernel.text = info.kernel

            switchFps185.setOnCheckedChangeListener(null)
            switchFps145.setOnCheckedChangeListener(null)
            switchFps120.setOnCheckedChangeListener(null)
            switchFps185.isChecked = info.fpsMode == FpsOverlayService.MODE_185
            switchFps145.isChecked = info.fpsMode == FpsOverlayService.MODE_145
            switchFps120.isChecked = info.fpsMode == FpsOverlayService.MODE_120
            setupFpsSwitchListeners()

            val badgeRes = if (info.androidBadge == "android17") R.drawable.android_badge_17 else R.drawable.android_badge_16
            ivAndroidBadge.setImageResource(badgeRes)
            btnBadge16.background = if (info.androidBadge != "android17")
                resources.getDrawable(R.drawable.btn_badge_selected, null)
                else resources.getDrawable(R.drawable.btn_secondary_bg, null)
            btnBadge17.background = if (info.androidBadge == "android17")
                resources.getDrawable(R.drawable.btn_badge_selected, null)
                else resources.getDrawable(R.drawable.btn_secondary_bg, null)

            if (info.deviceImageUri != null) {
                Glide.with(this@MainActivity).load(info.deviceImageUri).centerCrop().into(ivDeviceImage)
            } else {
                ivDeviceImage.setImageResource(R.drawable.ic_phone_placeholder)
            }
        }
    }

    private fun setupFpsSwitchListeners() {
        binding.switchFps185.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { turnOffAllSwitchesSilently(); binding.switchFps185.isChecked = true; setupFpsSwitchListeners(); activateFpsMode(FpsOverlayService.MODE_185) } else deactivateFps()
        }
        binding.switchFps145.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { turnOffAllSwitchesSilently(); binding.switchFps145.isChecked = true; setupFpsSwitchListeners(); activateFpsMode(FpsOverlayService.MODE_145) } else deactivateFps()
        }
        binding.switchFps120.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { turnOffAllSwitchesSilently(); binding.switchFps120.isChecked = true; setupFpsSwitchListeners(); activateFpsMode(FpsOverlayService.MODE_120) } else deactivateFps()
        }
    }

    private fun turnOffAllSwitchesSilently() {
        binding.switchFps185.setOnCheckedChangeListener(null)
        binding.switchFps145.setOnCheckedChangeListener(null)
        binding.switchFps120.setOnCheckedChangeListener(null)
        binding.switchFps185.isChecked = false
        binding.switchFps145.isChecked = false
        binding.switchFps120.isChecked = false
    }

    private fun activateFpsMode(mode: String) {
        viewModel.setFpsMode(mode)
        viewModel.deviceInfo.value?.let { PreferencesHelper.save(this, it.copy(fpsMode = mode)) }
        pendingFpsMode = mode
        stopFpsOverlay()
        checkOverlayPermissionAndStart(mode)
    }

    private fun deactivateFps() {
        viewModel.setFpsMode("off")
        viewModel.deviceInfo.value?.let { PreferencesHelper.save(this, it.copy(fpsMode = "off")) }
        stopFpsOverlay()
    }

    private fun setupClickListeners() {
        binding.apply {
            // Back button - toggle hide/show features
            btnBack.setOnClickListener {
                featuresVisible = !featuresVisible
                layoutAllFeatures.isVisible = featuresVisible
                val msg = if (featuresVisible) "Fitur ditampilkan" else "Fitur disembunyikan"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }

            btnExpandEdit.setOnClickListener {
                val v = layoutEditPanel.isVisible
                layoutEditPanel.isVisible = !v
                btnExpandEdit.text = if (v) "✎  Edit Info" else "✕  Tutup Edit"
                if (!v) viewModel.deviceInfo.value?.let { updateEditFields(it) }
            }

            ivDeviceImage.setOnClickListener { pickDeviceImage() }
            btnPickImage.setOnClickListener { pickDeviceImage() }
            btnSaveInfo.setOnClickListener { saveDeviceInfo() }

            btnBadge16.setOnClickListener {
                val u = viewModel.deviceInfo.value!!.copy(androidBadge = "android16")
                viewModel.updateDeviceInfo(u); PreferencesHelper.save(this@MainActivity, u)
            }
            btnBadge17.setOnClickListener {
                val u = viewModel.deviceInfo.value!!.copy(androidBadge = "android17")
                viewModel.updateDeviceInfo(u); PreferencesHelper.save(this@MainActivity, u)
            }

            switchWaifu.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checkOverlayPermissionAndStartWaifu()
                else { stopService(Intent(this@MainActivity, WaifuOverlayService::class.java)); Toast.makeText(this@MainActivity, "Waifu Notifier dimatikan", Toast.LENGTH_SHORT).show() }
            }

            // CD overlay toggle - transparan saat OFF, service tetap jalan
            switchCdVisible.setOnCheckedChangeListener { _, isChecked ->
                // Kirim intent ke service untuk update alpha
                val intent = Intent(this@MainActivity, WaifuOverlayService::class.java).apply {
                    putExtra("cd_visible", isChecked)
                }
                startService(intent)
                val msg = if (isChecked) "CD overlay ditampilkan" else "CD overlay disembunyikan (transparan)"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }

            switchTompel.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) startTompelService()
                else { stopService(Intent(this@MainActivity, TompelOverlayService::class.java)); Toast.makeText(this@MainActivity, "Dynamic Island dimatikan", Toast.LENGTH_SHORT).show() }
            }

            switchTompelFlip.setOnCheckedChangeListener { _, isChecked ->
                tompelFlip = isChecked
                if (switchTompel.isChecked) startTompelService()
            }

            btnTompelActive.setOnClickListener {
                tompelImage = "active"
                btnTompelActive.background = resources.getDrawable(R.drawable.btn_badge_selected, null)
                btnTompelInactive.background = resources.getDrawable(R.drawable.btn_secondary_bg, null)
                if (switchTompel.isChecked) startTompelService()
            }
            btnTompelInactive.setOnClickListener {
                tompelImage = "inactive"
                btnTompelInactive.background = resources.getDrawable(R.drawable.btn_badge_selected, null)
                btnTompelActive.background = resources.getDrawable(R.drawable.btn_secondary_bg, null)
                if (switchTompel.isChecked) startTompelService()
            }

            binding.seekTompelSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val pct = progress + 50
                    binding.tvTompelSizeVal.text = "$pct%"
                    tompelSize = pct
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { if (switchTompel.isChecked) startTompelService() }
            })

            btnTompelApply.setOnClickListener {
                tompelPortX = etTompelPortX.text.toString().toIntOrNull() ?: 0
                tompelPortY = etTompelPortY.text.toString().toIntOrNull() ?: 0
                tompelLandX = etTompelLandX.text.toString().toIntOrNull() ?: 0
                tompelLandY = etTompelLandY.text.toString().toIntOrNull() ?: 0
                tompelLandRX = etTompelLandRX.text.toString().toIntOrNull() ?: 0
                tompelLandRY = etTompelLandRY.text.toString().toIntOrNull() ?: 0
                if (switchTompel.isChecked) startTompelService()
                Toast.makeText(this@MainActivity, "Posisi diterapkan!", Toast.LENGTH_SHORT).show()
            }
        }
        setupFpsSwitchListeners()
    }

    private fun pickDeviceImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        pickImageLauncher.launch(intent)
    }

    private fun saveDeviceInfo() {
        val c = viewModel.deviceInfo.value!!
        val u = c.copy(
            deviceName = binding.etDeviceName.text.toString().ifBlank { c.deviceName },
            deviceCodename = binding.etCodename.text.toString().ifBlank { c.deviceCodename },
            romVersion = binding.etRomVersion.text.toString().ifBlank { c.romVersion },
            romType = binding.etRomType.text.toString().ifBlank { c.romType },
            processor = binding.etProcessor.text.toString().ifBlank { c.processor },
            display = binding.etDisplay.text.toString().ifBlank { c.display },
            battery = binding.etBattery.text.toString().ifBlank { c.battery },
            camera = binding.etCamera.text.toString().ifBlank { c.camera },
            ram = binding.etRamTotal.text.toString().ifBlank { c.ram },
            ramUsed = binding.etRamUsed.text.toString().ifBlank { c.ramUsed },
            ramTotal = binding.etRamTotal.text.toString().ifBlank { c.ramTotal },
            romStorage = "${formatStorage(binding.etRomUsed.text.toString().ifBlank { c.romUsed })} / ${formatStorage(binding.etRomTotal.text.toString().ifBlank { c.romTotal })}",
            romUsed = binding.etRomUsed.text.toString().ifBlank { c.romUsed },
            romTotal = binding.etRomTotal.text.toString().ifBlank { c.romTotal },
            maintainer = binding.etMaintainer.text.toString().ifBlank { c.maintainer },
            securityPatch = binding.etSecurityPatch.text.toString().ifBlank { c.securityPatch },
            kernel = binding.etKernel.text.toString().ifBlank { c.kernel },
            androidVersion = binding.etAndroidVersion.text.toString().ifBlank { c.androidVersion }
        )
        viewModel.updateDeviceInfo(u); PreferencesHelper.save(this, u)
        Toast.makeText(this, "Info tersimpan!", Toast.LENGTH_SHORT).show()
        binding.layoutEditPanel.isVisible = false
        binding.btnExpandEdit.text = "✎  Edit Info"
    }

    private fun updateEditFields(info: DeviceInfo) {
        binding.apply {
            etDeviceName.setText(info.deviceName); etCodename.setText(info.deviceCodename)
            etRomVersion.setText(info.romVersion); etRomType.setText(info.romType)
            etProcessor.setText(info.processor); etDisplay.setText(info.display)
            etBattery.setText(info.battery); etCamera.setText(info.camera)
            etRamUsed.setText(info.ramUsed); etRamTotal.setText(info.ramTotal)
            etRomUsed.setText(info.romUsed); etRomTotal.setText(info.romTotal)
            etMaintainer.setText(info.maintainer); etSecurityPatch.setText(info.securityPatch)
            etKernel.setText(info.kernel); etAndroidVersion.setText(info.androidVersion)
        }
    }

    private fun checkOverlayPermissionAndStartWaifu() {
        if (!Settings.canDrawOverlays(this)) {
            pendingFpsMode = "waifu"
            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else startWaifuService()
    }

    private fun startWaifuService() {
        val intent = Intent(this, WaifuOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Waifu Notifier aktif! 💕", Toast.LENGTH_SHORT).show()
    }

    private fun startTompelService() {
        stopService(Intent(this, TompelOverlayService::class.java))
        val intent = Intent(this, TompelOverlayService::class.java).apply {
            putExtra(TompelOverlayService.EXTRA_IMAGE, tompelImage)
            putExtra(TompelOverlayService.EXTRA_PORT_X, tompelPortX)
            putExtra(TompelOverlayService.EXTRA_PORT_Y, tompelPortY)
            putExtra(TompelOverlayService.EXTRA_LAND_X, tompelLandX)
            putExtra(TompelOverlayService.EXTRA_LAND_Y, tompelLandY)
            putExtra(TompelOverlayService.EXTRA_LAND_RX, tompelLandRX)
            putExtra(TompelOverlayService.EXTRA_LAND_RY, tompelLandRY)
            putExtra(TompelOverlayService.EXTRA_SIZE, tompelSize)
            putExtra(TompelOverlayService.EXTRA_FLIP, tompelFlip)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun checkOverlayPermissionAndStart(mode: String) {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        } else startFpsOverlay(mode)
    }

    private fun startFpsOverlay(mode: String) {
        val intent = Intent(this, FpsOverlayService::class.java).apply { putExtra(FpsOverlayService.EXTRA_MODE, mode) }
        stopFpsOverlay()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        val label = when (mode) { FpsOverlayService.MODE_185 -> "185 FPS"; FpsOverlayService.MODE_145 -> "145 FPS"; else -> "Grafik FPS" }
        Toast.makeText(this, "FPS Monitor aktif! Mode $label", Toast.LENGTH_SHORT).show()
    }

    private fun stopFpsOverlay() { stopService(Intent(this, FpsOverlayService::class.java)) }
}
