package com.wellliving.app.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.wellliving.rules.UsageAggregator
import com.wellliving.rules.UsageEvent
import java.time.LocalDate
import java.time.ZoneId

/** Today's foreground time per app, read from Android's usage statistics. */
class UsageTracker(context: Context) {

    private val manager = context.getSystemService(UsageStatsManager::class.java)
    private var cache: Map<String, Long> = emptyMap()
    private var cachedAt = 0L

    fun todayMillis(nowMillis: Long = System.currentTimeMillis()): Map<String, Long> {
        if (nowMillis - cachedAt < CACHE_MILLIS) return cache
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = manager.queryEvents(dayStart, nowMillis)
        val list = ArrayList<UsageEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Type.PAUSED
                else -> continue
            }
            list.add(UsageEvent(event.packageName, type, event.timeStamp))
        }
        cache = UsageAggregator.foregroundMillis(list, dayStart, nowMillis)
        cachedAt = nowMillis
        return cache
    }

    private companion object {
        const val CACHE_MILLIS = 5_000L
    }
}
