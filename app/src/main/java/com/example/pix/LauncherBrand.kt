package com.example.pix

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherBrand {
    fun apply(context: Context, dark: Boolean) {
        val pm = context.packageManager
        val selected =
            ComponentName(
                context,
                context.packageName + if (dark) ".LauncherDark" else ".LauncherLight",
            )
        val other =
            ComponentName(
                context,
                context.packageName + if (dark) ".LauncherLight" else ".LauncherDark",
            )
        if (
            pm.getComponentEnabledSetting(selected) !=
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
            pm.setComponentEnabledSetting(
                selected,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        if (pm.getComponentEnabledSetting(other) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
            pm.setComponentEnabledSetting(
                other,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
    }
}
