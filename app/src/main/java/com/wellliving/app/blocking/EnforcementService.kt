package com.wellliving.app.blocking

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.wellliving.app.WellLivingApp
import com.wellliving.app.protection.PinGuardActivity
import com.wellliving.rules.Decision
import com.wellliving.rules.NightConfig
import com.wellliving.rules.RuleContext
import java.time.LocalDateTime

/**
 * Watches which app is in front and enforces the rules engine's decision.
 * Also hides YouTube Shorts and guards the screen that would switch off uninstall protection.
 */
class EnforcementService : AccessibilityService() {

    private val app get() = application as WellLivingApp
    private val handler = Handler(Looper.getMainLooper())
    private var foreground: String? = null
    private var lastLaunch: Pair<String, Long>? = null
    private var lastShortsCheck = 0L

    /** Quotas can run out while an app stays open, so the foreground app is re-checked periodically. */
    private val tick = object : Runnable {
        override fun run() {
            foreground?.let(::enforce)
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.postDelayed(tick, TICK_MILLIS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (guardAdminScreen(event)) return
                if (pkg in IGNORED_TRANSIENT || event.className?.contains("SoftInputWindow") == true) return
                foreground = pkg
                enforce(pkg)
                checkShorts(pkg, force = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkShorts(pkg, force = false)
        }
    }

    private fun enforce(pkg: String) {
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        val settings = app.settings
        val ctx = RuleContext(
            packageName = pkg,
            now = LocalDateTime.now(),
            nowMillis = now,
            config = settings.blockingConfig(),
            state = settings.runtimeState(app.usage.todayMillis(now)),
            essentialPackages = app.essentials.get(now),
        )
        val decision = app.engine.evaluate(ctx)
        if (decision == Decision.Allow) return

        // The block screen takes a moment to appear; don't stack several of them.
        val last = lastLaunch
        if (last != null && last.first == pkg && now - last.second < RELAUNCH_DEBOUNCE_MILLIS) return
        lastLaunch = pkg to now

        // The block screen opens in its own task on top; leaving it always leads home, never back.
        startActivity(BlockActivity.intent(this, pkg, decision))
    }

    /** Shorts in YouTube (module 1.2). View ids change with YouTube updates; see ShortsDetector. */
    private fun checkShorts(pkg: String, force: Boolean) {
        if (pkg != NightConfig.YOUTUBE || !app.settings.shortsBlockingEnabled) return
        val now = System.currentTimeMillis()
        if (!force && now - lastShortsCheck < SHORTS_CHECK_MILLIS) return
        lastShortsCheck = now

        val root = rootInActiveWindow ?: return
        if (ShortsDetector.isShowingShorts(root)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Toast.makeText(this, "Shorts חסומים", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The system screen that deactivates a device admin is the way to make the app removable.
     * Without the PIN, the user is sent back.
     */
    private fun guardAdminScreen(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: return false
        if (!className.contains("DeviceAdminAdd")) return false
        val settings = app.settings
        if (!settings.hasPin() || settings.hasSettingsPass(System.currentTimeMillis())) return false
        startActivity(PinGuardActivity.intent(this))
        return true
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
        const val RELAUNCH_DEBOUNCE_MILLIS = 2_000L
        const val SHORTS_CHECK_MILLIS = 700L

        /** Overlays that come and go without changing which app the user is in. */
        val IGNORED_TRANSIENT = setOf("com.android.systemui")
    }
}
