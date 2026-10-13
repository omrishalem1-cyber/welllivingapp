package com.wellliving.trainer

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AnalyticsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 13) // a Tuesday

    private fun at(date: LocalDate, hour: Int = 10) = date.atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun session(date: LocalDate, minutes: Int, activity: String = "a", completed: Boolean = true) =
        Session("$date-$activity-$minutes", activity, at(date), minutes, minutes * 60, completed, 0)

    @Test
    fun dailyBucketsCoverTheRangeOldestFirst() {
        val sessions = listOf(
            session(today, 20), session(today, 25, "b"),
            session(today.minusDays(6), 30), session(today.minusDays(7), 40),
        )
        val days = Analytics.daily(sessions, today, 7, zone)
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().start)
        assertEquals(30, days.first().minutes)
        assertEquals(45, days.last().minutes)
        assertEquals(25, days.last().minutesFor("b"))
    }

    @Test
    fun weeksStartOnSunday() {
        assertEquals(LocalDate.of(2026, 10, 11), Analytics.weekStart(today))
        val sessions = listOf(session(LocalDate.of(2026, 10, 11), 20), session(LocalDate.of(2026, 10, 10), 30))
        val weeks = Analytics.weekly(sessions, today, 2, zone)
        assertEquals(listOf(30, 20), weeks.map { it.minutes })
    }

    @Test
    fun summaryCountsCompletion() {
        val summary = Analytics.summary(listOf(session(today, 20), session(today, 10, completed = false)))
        assertEquals(30, summary.minutes)
        assertEquals(0.5, summary.completionRate)
    }

    @Test
    fun weeklyAverageIgnoresUnfinishedSessions() {
        val sessions = listOf(session(today, 20), session(today, 30), session(today, 5, completed = false))
        val avg = Analytics.weeklyAverageCompletedMin(sessions, today, 2, zone)
        assertNull(avg[0].second)
        assertEquals(25.0, avg[1].second)
    }

    @Test
    fun quotePickerNeverRepeatsThePrevious() {
        for (previous in 0 until 5) for (r in 0 until 4) {
            assertNotEquals(previous, QuotePicker.next(previous, 5) { r })
        }
        assertEquals(0, QuotePicker.next(3, 1))
    }

    @Test
    fun everyQuoteHasASource() {
        QUOTES.forEach { assert(it.text.isNotBlank() && it.author.isNotBlank() && it.source.isNotBlank()) }
    }
}
