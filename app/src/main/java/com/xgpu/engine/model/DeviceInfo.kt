package com.xgpu.engine.model

import android.net.Uri

data class DeviceInfo(
    val deviceName: String = "POCO X3 Pro",
    val deviceCodename: String = "vayu",
    val romVersion: String = "v3.7",
    val romType: String = "Official",
    val processor: String = "Snapdragon 860",
    val display: String = "1080 x 2400, 120 Hz",
    val battery: String = "5160 mAh",
    val camera: String = "48MP + 8MP + 2MP + 2MP",
    val ram: String = "8 GB",
    val ramUsed: String = "2,4 GB",   // used portion
    val ramTotal: String = "8 GB",    // total untuk progress bar
    val romStorage: String = "20,59 GB / 256 GB",
    val romUsed: String = "20,59 GB", // used portion
    val romTotal: String = "256 GB",  // total untuk progress bar
    val maintainer: String = "tejas101k",
    val securityPatch: String = "1 Februari 2026",
    val kernel: String = "4.14.271-perf+",
    val androidVersion: String = "16",
    val androidBadge: String = "android16", // "android16" or "android17"
    val deviceImageUri: Uri? = null,
    val fpsMode: String = "off" // "off", "mode_185", "mode_145"
)
