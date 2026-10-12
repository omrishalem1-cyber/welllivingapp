package com.wellliving.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Apps that are never blocked: this app, the home screen, system UI, the keyboard,
 * the phone app and the alarm clock (module 6.2: calls and alarm always available).
 */
class EssentialPackages(private val context: Context) {

    private var cached: Set<String> = emptySet()
    private var cachedAt = 0L

    fun get(nowMillis: Long = System.currentTimeMillis()): Set<String> {
        if (nowMillis - cachedAt > REFRESH_MILLIS) {
            cached = resolve()
            cachedAt = nowMillis
        }
        return cached
    }

    private fun resolve(): Set<String> {
        val pm = context.packageManager
        val result = HashSet<String>(STATIC)
        result.add(context.packageName)

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { result.add(it.activityInfo.packageName) }

        pm.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            .forEach { result.add(it.activityInfo.packageName) }

        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(result::add)

        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.forEach { result.add(it.packageName) }

        return result
    }

    private companion object {
        const val REFRESH_MILLIS = 10 * 60_000L
        val STATIC = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock",
            "com.android.emergency",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
        )
    }
}
