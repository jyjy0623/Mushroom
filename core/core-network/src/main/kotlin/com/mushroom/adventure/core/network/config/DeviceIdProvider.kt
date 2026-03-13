package com.mushroom.adventure.core.network.config

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceIdProvider {
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            "device-${androidId.hashCode().toUInt()}"
        } else {
            "device-${(Build.BOARD + Build.BRAND + Build.DEVICE + Build.MANUFACTURER).hashCode().toUInt()}"
        }
    }
}
