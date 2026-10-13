package com.wellliving.trainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressionTest {
    private var clock = 0L

    private fun session(completed: Boolean, target: Int = 20, actualMin: Int = if (completed) target else 8, distractions: Int = 0) =
        Session("s${clock}", "a", clock++, target, actualMin * 60, completed, distractions)

    @Test
    fun newActivityStartsAtTwentyMinutes() {
        assertEquals(ProgressState(20, 0, 0), Progression.state(emptyList()))
    }

    @Test
    fun twoSuccessesRaiseTheTarget() {
        val state = Progression.state(listOf(session(true), session(true)))
        assertEquals(25, state.targetMin)
        assertEquals(0, state.successStreak)
    }

    @Test
    fun aFailureBreaksTheSuccessStreak() {
        val state = Progression.state(listOf(session(true), session(false), session(true)))
        assertEquals(20, state.targetMin)
        assertEquals(1, state.successStreak)
    }

    @Test
    fun twoFailuresLowerTheTarget() {
        val ups = List(4) { session(true) }
        val state = Progression.state(ups + session(false) + session(false))
        assertEquals(25, state.targetMin)
    }

    @Test
    fun targetStaysWithinBounds() {
        assertEquals(90, Progression.state(List(100) { session(true) }).targetMin)
        assertEquals(10, Progression.state(List(100) { session(false) }).targetMin)
    }

    @Test
    fun sessionsAreReplayedInTimeOrder() {
        val a = session(false); val b = session(false); val c = session(true); val d = session(true)
        assertEquals(20, Progression.state(listOf(d, c, b, a)).targetMin)
    }

    @Test
    fun noFurtherRaiseAtTheCeiling() {
        val state = Progression.state(List(100) { session(true) })
        assertNull(state.successesToRaise(ProgressionPolicy()))
    }

    @Test
    fun statsSummariseRecentSessions() {
        val sessions = listOf(
            session(true, actualMin = 20, distractions = 2),
            session(false, actualMin = 10, distractions = 4),
        )
        val stats = Stats.forActivity(sessions, nowMs = clock)
        assertEquals(2, stats.totalSessions)
        assertEquals(1, stats.completedSessions)
        assertEquals(0.5, stats.recentSuccessRate)
        assertEquals(30, stats.minutesLast7Days)
        assertEquals(20, stats.longestCompletedMin)
        assertEquals(2.0, stats.recentDistractionsPer10Min)
    }
}
