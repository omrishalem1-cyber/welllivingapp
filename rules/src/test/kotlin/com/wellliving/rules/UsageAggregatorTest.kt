package com.wellliving.rules

import com.wellliving.rules.UsageEvent.Type.PAUSED
import com.wellliving.rules.UsageEvent.Type.RESUMED
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageAggregatorTest {

    private val dayStart = 0L
    private val a = "app.a"
    private val b = "app.b"

    @Test
    fun `sums closed sessions per package`() {
        val events = listOf(
            UsageEvent(a, RESUMED, 100), UsageEvent(a, PAUSED, 300),
            UsageEvent(b, RESUMED, 300), UsageEvent(b, PAUSED, 350),
            UsageEvent(a, RESUMED, 400), UsageEvent(a, PAUSED, 500),
        )
        assertEquals(mapOf(a to 300L, b to 50L), UsageAggregator.foregroundMillis(events, dayStart, 1_000))
    }

    @Test
    fun `an app still open counts until now`() {
        val events = listOf(UsageEvent(a, RESUMED, 100))
        assertEquals(mapOf(a to 900L), UsageAggregator.foregroundMillis(events, dayStart, 1_000))
    }

    @Test
    fun `an app open at midnight counts from the start of the day`() {
        val events = listOf(UsageEvent(a, PAUSED, 200))
        assertEquals(mapOf(a to 200L), UsageAggregator.foregroundMillis(events, dayStart, 1_000))
    }

    @Test
    fun `overlapping activities of one app are not double counted or lost`() {
        val events = listOf(
            UsageEvent(a, RESUMED, 100),
            UsageEvent(a, RESUMED, 150),
            UsageEvent(a, PAUSED, 160),
            UsageEvent(a, PAUSED, 400),
        )
        assertEquals(mapOf(a to 300L), UsageAggregator.foregroundMillis(events, dayStart, 1_000))
    }

    @Test
    fun `a stray pause later in the day adds nothing`() {
        val events = listOf(
            UsageEvent(a, RESUMED, 100), UsageEvent(a, PAUSED, 200),
            UsageEvent(a, PAUSED, 900),
        )
        assertEquals(mapOf(a to 100L), UsageAggregator.foregroundMillis(events, dayStart, 1_000))
    }
}
