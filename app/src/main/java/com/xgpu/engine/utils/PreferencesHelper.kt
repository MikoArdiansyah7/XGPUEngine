package com.xgpu.engine.utils

import android.content.Context
import android.net.Uri
import com.xgpu.engine.model.DeviceInfo

object PreferencesHelper {

    private const val PREF_NAME = "xgpu_prefs"

    fun save(context: Context, info: DeviceInfo) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("deviceName", info.deviceName)
            putString("deviceCodename", info.deviceCodename)
            putString("romVersion", info.romVersion)
            putString("romType", info.romType)
            putString("processor", info.processor)
            putString("display", info.display)
            putString("battery", info.battery)
            putString("camera", info.camera)
            putString("ram", info.ram)
            putString("ramUsed", info.ramUsed)
            putString("ramTotal", info.ramTotal)
            putString("romStorage", info.romStorage)
            putString("romUsed", info.romUsed)
            putString("romTotal", info.romTotal)
            putString("maintainer", info.maintainer)
            putString("securityPatch", info.securityPatch)
            putString("kernel", info.kernel)
            putString("androidVersion", info.androidVersion)
            putString("deviceImageUri", info.deviceImageUri?.toString() ?: "")
            putString("fpsMode", info.fpsMode)
            putString("androidBadge", info.androidBadge)
            apply()
        }
    }

    fun load(context: Context): DeviceInfo {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString("deviceImageUri", "") ?: ""
        return DeviceInfo(
            deviceName = prefs.getString("deviceName", "POCO X3 Pro") ?: "POCO X3 Pro",
            deviceCodename = prefs.getString("deviceCodename", "vayu") ?: "vayu",
            romVersion = prefs.getString("romVersion", "v3.7") ?: "v3.7",
            romType = prefs.getString("romType", "Official") ?: "Official",
            processor = prefs.getString("processor", "Snapdragon 860") ?: "Snapdragon 860",
            display = prefs.getString("display", "1080 x 2400, 120 Hz") ?: "1080 x 2400, 120 Hz",
            battery = prefs.getString("battery", "5160 mAh") ?: "5160 mAh",
            camera = prefs.getString("camera", "48MP + 8MP + 2MP + 2MP") ?: "48MP + 8MP + 2MP + 2MP",
            ram = prefs.getString("ram", "8 GB") ?: "8 GB",
            ramUsed = prefs.getString("ramUsed", "2,4 GB") ?: "2,4 GB",
            ramTotal = prefs.getString("ramTotal", "8 GB") ?: "8 GB",
            romStorage = prefs.getString("romStorage", "20,59 GB / 256 GB") ?: "20,59 GB / 256 GB",
            romUsed = prefs.getString("romUsed", "20,59 GB") ?: "20,59 GB",
            romTotal = prefs.getString("romTotal", "256 GB") ?: "256 GB",
            maintainer = prefs.getString("maintainer", "tejas101k") ?: "tejas101k",
            securityPatch = prefs.getString("securityPatch", "1 Februari 2026") ?: "1 Februari 2026",
            kernel = prefs.getString("kernel", "4.14.271-perf+") ?: "4.14.271-perf+",
            androidVersion = prefs.getString("androidVersion", "16") ?: "16",
            deviceImageUri = if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null,
            fpsMode = prefs.getString("fpsMode", "off") ?: "off",
            androidBadge = prefs.getString("androidBadge", "android16") ?: "android16"
        )
    }
}
