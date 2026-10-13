package com.wellliving.attention

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.wellliving.trainer.MIN_COUNTED_SEC
import com.wellliving.trainer.Session
import java.util.UUID

/**
 * Keeps the countdown alive while the screen is off, shows it in the notification,
 * and records the session when it completes or is ended early.
 */
class SessionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val finishRunnable = Runnable { finish(completed = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = ActiveSessionState.current.value
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_DISTRACTION -> ActiveSessionState.set(session.copy(distractions = session.distractions + 1))
            ACTION_STOP -> {
                finish(completed = false)
                return START_NOT_STICKY
            }
        }
        val current = ActiveSessionState.current.value ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_RUNNING, runningNotification(current), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        schedule(current)
        return START_STICKY
    }

    private fun schedule(session: ActiveSession) {
        handler.removeCallbacks(finishRunnable)
        val remaining = session.endMs - System.currentTimeMillis()
        if (remaining <= 0) {
            finish(completed = true)
            return
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "attention:session")
                .apply { acquire(remaining + 60_000L) }
        }
        handler.postDelayed(finishRunnable, remaining)
    }

    private fun finish(completed: Boolean) {
        handler.removeCallbacks(finishRunnable)
        val session = ActiveSessionState.current.value
        if (session != null) {
            ActiveSessionState.set(null)
            val now = System.currentTimeMillis()
            val actualSec = if (completed) session.targetMin * 60 else ((now - session.startMs) / 1000).toInt()
            if (completed || actualSec >= MIN_COUNTED_SEC) {
                val id = UUID.randomUUID().toString()
                Store.addSession(
                    Session(id, session.activityId, session.startMs, session.targetMin, actualSec, completed, session.distractions)
                )
                ActiveSessionState.setPendingRating(id)
            }
            if (completed) notifyDone(session)
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runningNotification(session: ActiveSession): Notification {
        val name = activityName(session.activityId)
        return Notification.Builder(this, AttentionApp.CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("$name · ${session.targetMin} דק'")
            .setContentText("הסחות שנרשמו: ${session.distractions}")
            .setWhen(session.endMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(Notification.Action.Builder(null, "הוסחתי", serviceIntent(this, ACTION_DISTRACTION, 1)).build())
            .addAction(Notification.Action.Builder(null, "סיום מוקדם", serviceIntent(this, ACTION_STOP, 2)).build())
            .build()
    }

    private fun notifyDone(session: ActiveSession) {
        val notification = Notification.Builder(this, AttentionApp.CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("הפרק הושלם: ${session.targetMin} דקות")
            .setContentText("${activityName(session.activityId)} · אפשר לדרג את איכות הריכוז")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_DONE, notification)
    }

    private fun activityName(id: String) =
        Store.data.value.activities.firstOrNull { it.id == id }?.name ?: "פרק עבודה"

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISTRACTION = "distraction"
        const val ACTION_STOP = "stop"
        private const val NOTIFICATION_RUNNING = 1
        private const val NOTIFICATION_DONE = 2

        fun start(context: Context, activityId: String, targetMin: Int) {
            ActiveSessionState.setPendingRating(null)
            ActiveSessionState.set(ActiveSession(activityId, System.currentTimeMillis(), targetMin, 0))
            context.startForegroundService(Intent(context, SessionService::class.java))
        }

        fun send(context: Context, action: String) =
            context.startService(Intent(context, SessionService::class.java).setAction(action))

        private fun serviceIntent(context: Context, action: String, code: Int) = PendingIntent.getService(
            context, code, Intent(context, SessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
