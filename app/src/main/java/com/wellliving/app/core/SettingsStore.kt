package com.wellliving.app.core

import android.content.Context
import android.content.SharedPreferences
import com.wellliving.rules.BlockingConfig
import com.wellliving.rules.NightConfig
import com.wellliving.rules.RuntimeState
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

/**
 * Everything the user configured, plus short-lived passes. Kept on the phone in SharedPreferences;
 * a Room database will replace it once later modules need history.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // --- Configuration -------------------------------------------------------------------------

    fun quotas(): Map<String, Int> = readIntMap(KEY_QUOTAS)

    fun setQuota(packageName: String, minutes: Int?) {
        val map = quotas().toMutableMap()
        if (minutes == null) map.remove(packageName) else map[packageName] = minutes
        writeIntMap(KEY_QUOTAS, map)
    }

    var nightLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_ENABLED, value).apply()

    var shortsBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHORTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SHORTS_ENABLED, value).apply()

    fun extraNightAllowed(): Set<String> = prefs.getStringSet(KEY_NIGHT_ALLOWED, emptySet())!!.toSet()

    fun setNightAllowed(packageName: String, allowed: Boolean) {
        val set = extraNightAllowed().toMutableSet()
        if (allowed) set.add(packageName) else set.remove(packageName)
        prefs.edit().putStringSet(KEY_NIGHT_ALLOWED, set).apply()
    }

    fun nightConfig(): NightConfig = NightConfig(
        enabled = nightLockEnabled,
        allowedPackages = NightConfig.DEFAULT_NIGHT_ALLOWED + extraNightAllowed(),
    )

    fun blockingConfig(): BlockingConfig = BlockingConfig(quotas = quotas(), night = nightConfig())

    // --- Passes and declarations ---------------------------------------------------------------

    fun grantGatePass(packageName: String, nowMillis: Long) {
        val until = nowMillis + blockingConfig().gatePassMinutes * 60_000L
        val map = readLongMap(KEY_GATE_PASSES).filterValues { it > nowMillis }.toMutableMap()
        map[packageName] = until
        writeLongMap(KEY_GATE_PASSES, map)
    }

    fun grantUrgentUnlock(nowMillis: Long) {
        val until = nowMillis + nightConfig().urgentUnlockMinutes * 60_000L
        prefs.edit().putLong(KEY_URGENT_UNTIL, until).apply()
    }

    fun declareMorning(today: LocalDate) {
        prefs.edit().putString(KEY_MORNING_DECLARED, today.toString()).apply()
    }

    fun runtimeState(usageTodayMillis: Map<String, Long>): RuntimeState = RuntimeState(
        usageTodayMillis = usageTodayMillis,
        gatePassUntil = readLongMap(KEY_GATE_PASSES),
        urgentUnlockUntil = prefs.getLong(KEY_URGENT_UNTIL, 0L),
        morningDeclaredOn = prefs.getString(KEY_MORNING_DECLARED, null)?.let(LocalDate::parse),
    )

    // --- PIN (uninstall protection and loosening settings) -------------------------------------

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit().putString(KEY_PIN_SALT, salt).putString(KEY_PIN_HASH, hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        return hash(salt, pin) == prefs.getString(KEY_PIN_HASH, null)
    }

    /** After the PIN is entered on a guarded system screen, leave that screen alone for a while. */
    fun grantSettingsPass(nowMillis: Long) {
        prefs.edit().putLong(KEY_SETTINGS_PASS_UNTIL, nowMillis + SETTINGS_PASS_MILLIS).apply()
    }

    fun hasSettingsPass(nowMillis: Long): Boolean = prefs.getLong(KEY_SETTINGS_PASS_UNTIL, 0L) > nowMillis

    // --- Helpers -------------------------------------------------------------------------------

    private fun readIntMap(key: String): Map<String, Int> {
        val json = JSONObject(prefs.getString(key, "{}")!!)
        return json.keys().asSequence().associateWith { json.getInt(it) }
    }

    private fun writeIntMap(key: String, map: Map<String, Int>) {
        prefs.edit().putString(key, JSONObject(map).toString()).apply()
    }

    private fun readLongMap(key: String): Map<String, Long> {
        val json = JSONObject(prefs.getString(key, "{}")!!)
        return json.keys().asSequence().associateWith { json.getLong(it) }
    }

    private fun writeLongMap(key: String, map: Map<String, Long>) {
        prefs.edit().putString(key, JSONObject(map).toString()).apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_QUOTAS = "quotas"
        const val KEY_NIGHT_ENABLED = "night_enabled"
        const val KEY_NIGHT_ALLOWED = "night_allowed"
        const val KEY_SHORTS_ENABLED = "shorts_enabled"
        const val KEY_GATE_PASSES = "gate_passes"
        const val KEY_URGENT_UNTIL = "urgent_until"
        const val KEY_MORNING_DECLARED = "morning_declared"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_SETTINGS_PASS_UNTIL = "settings_pass_until"
        const val SETTINGS_PASS_MILLIS = 2 * 60_000L
    }
}
