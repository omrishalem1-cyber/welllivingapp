package com.wellliving.trainer

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Focused seconds on one day (or week), split by activity id. */
data class Bucket(val start: LocalDate, val secondsByActivity: Map<String, Int>) {
    val minutes: Int get() = secondsByActivity.values.sum() / 60
    fun minutesFor(activityId: String): Int = (secondsByActivity[activityId] ?: 0) / 60
}

data class PeriodSummary(
    val minutes: Int,
    val sessions: Int,
    val completed: Int,
) {
    val completionRate: Double? get() = if (sessions == 0) null else completed.toDouble() / sessions
}

/** Time-based aggregation for the progress charts. Weeks start on Sunday. */
object Analytics {
    fun day(session: Session, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(session.startMs).atZone(zone).toLocalDate()

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    /** One bucket per day for the [days] days ending with [today], oldest first. */
    fun daily(sessions: List<Session>, today: LocalDate, days: Int, zone: ZoneId): List<Bucket> {
        val first = today.minusDays(days - 1L)
        val byDay = sessions.groupBy { day(it, zone) }
        return (0 until days).map { offset ->
            val date = first.plusDays(offset.toLong())
            Bucket(date, secondsByActivity(byDay[date].orEmpty()))
        }
    }

    /** One bucket per week for the [weeks] weeks ending with the week of [today], oldest first. */
    fun weekly(sessions: List<Session>, today: LocalDate, weeks: Int, zone: ZoneId): List<Bucket> {
        val firstWeek = weekStart(today).minusWeeks(weeks - 1L)
        val byWeek = sessions.groupBy { weekStart(day(it, zone)) }
        return (0 until weeks).map { offset ->
            val start = firstWeek.plusWeeks(offset.toLong())
            Bucket(start, secondsByActivity(byWeek[start].orEmpty()))
        }
    }

    /** Sessions whose day falls in [from, toInclusive]. */
    fun between(sessions: List<Session>, from: LocalDate, toInclusive: LocalDate, zone: ZoneId): List<Session> =
        sessions.filter { val d = day(it, zone); !d.isBefore(from) && !d.isAfter(toInclusive) }

    fun summary(sessions: List<Session>): PeriodSummary =
        PeriodSummary(sessions.sumOf { it.actualSec } / 60, sessions.size, sessions.count { it.completed })

    /** Average completed-session length per week, null for weeks with no completed session. */
    fun weeklyAverageCompletedMin(sessions: List<Session>, today: LocalDate, weeks: Int, zone: ZoneId): List<Pair<LocalDate, Double?>> {
        val firstWeek = weekStart(today).minusWeeks(weeks - 1L)
        val byWeek = sessions.filter { it.completed }.groupBy { weekStart(day(it, zone)) }
        return (0 until weeks).map { offset ->
            val start = firstWeek.plusWeeks(offset.toLong())
            start to byWeek[start]?.map { it.actualSec / 60.0 }?.average()
        }
    }

    private fun secondsByActivity(sessions: List<Session>): Map<String, Int> =
        sessions.groupBy { it.activityId }.mapValues { (_, list) -> list.sumOf { it.actualSec } }
}
