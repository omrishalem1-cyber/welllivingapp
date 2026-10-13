package com.wellliving.trainer

data class ActivityStats(
    val progress: ProgressState,
    val totalSessions: Int,
    val completedSessions: Int,
    /** Share of the last [RECENT] sessions that were completed; null with no sessions. */
    val recentSuccessRate: Double?,
    val minutesLast7Days: Int,
    val longestCompletedMin: Int,
    /** Distractions per 10 focused minutes over the last [RECENT] sessions; null with no sessions. */
    val recentDistractionsPer10Min: Double?,
) {
    companion object {
        const val RECENT = 10
    }
}

object Stats {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun forActivity(
        sessions: List<Session>,
        nowMs: Long,
        policy: ProgressionPolicy = ProgressionPolicy(),
    ): ActivityStats {
        val sorted = sessions.sortedBy { it.startMs }
        val recent = sorted.takeLast(ActivityStats.RECENT)
        val recentSec = recent.sumOf { it.actualSec }
        return ActivityStats(
            progress = Progression.state(sorted, policy),
            totalSessions = sorted.size,
            completedSessions = sorted.count { it.completed },
            recentSuccessRate = if (recent.isEmpty()) null else recent.count { it.completed }.toDouble() / recent.size,
            minutesLast7Days = sorted.filter { it.startMs >= nowMs - 7 * DAY_MS }.sumOf { it.actualSec } / 60,
            longestCompletedMin = sorted.filter { it.completed }.maxOfOrNull { it.actualSec / 60 } ?: 0,
            recentDistractionsPer10Min = if (recentSec <= 0) null else recent.sumOf { it.distractions } * 600.0 / recentSec,
        )
    }

    /** Focused minutes across all activities since [sinceMs]. */
    fun minutesSince(sessions: List<Session>, sinceMs: Long): Int =
        sessions.filter { it.startMs >= sinceMs }.sumOf { it.actualSec } / 60
}
