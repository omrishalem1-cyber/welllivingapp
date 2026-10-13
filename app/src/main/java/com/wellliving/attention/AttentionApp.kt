package com.wellliving.attention

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class AttentionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        ActiveSessionState.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "פרק עבודה פעיל", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "סיום פרק", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_DONE = "done"
    }
}
