package com.xgpu.engine.ui

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xgpu.engine.model.DeviceInfo

class MainViewModel : ViewModel() {

    val deviceInfo = MutableLiveData(DeviceInfo())

    fun updateDeviceInfo(info: DeviceInfo) {
        deviceInfo.value = info
    }

    fun updateImage(uri: Uri) {
        deviceInfo.value = deviceInfo.value?.copy(deviceImageUri = uri)
    }

    fun setFpsMode(mode: String) {
        deviceInfo.value = deviceInfo.value?.copy(fpsMode = mode)
    }
}
