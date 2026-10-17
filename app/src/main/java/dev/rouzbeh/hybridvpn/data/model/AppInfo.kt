package dev.rouzbeh.hybridvpn.data.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable,
    val isSystemApp: Boolean,
    val isSelected: Boolean = false
)
