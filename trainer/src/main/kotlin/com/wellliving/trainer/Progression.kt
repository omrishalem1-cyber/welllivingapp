package com.wellliving.trainer

/**
 * How the session length grows with success. Each activity has its own ladder:
 * [streakToRaise] completed sessions in a row raise the target by [stepMin],
 * [streakToLower] sessions in a row ended early lower it by [stepMin].
 */
data class ProgressionPolicy(
    val startMin: Int = 20,
    val minMin: Int = 10,
    val maxMin: Int = 90,
    val stepMin: Int = 5,
    val streakToRaise: Int = 2,
    val streakToLower: Int = 2,
)

data class ProgressState(
    val targetMin: Int,
    /** Completed sessions in a row at the current target. */
    val successStreak: Int,
    /** Sessions ended early in a row at the current target. */
    val failStreak: Int,
) {
    fun successesToRaise(policy: ProgressionPolicy): Int? =
        if (targetMin >= policy.maxMin) null else policy.streakToRaise - successStreak
}

object Progression {
    /** Replays an activity's sessions in time order to get its current target. */
    fun state(sessions: List<Session>, policy: ProgressionPolicy = ProgressionPolicy()): ProgressState {
        var target = policy.startMin
        var successes = 0
        var failures = 0
        for (session in sessions.sortedBy { it.startMs }) {
            if (session.completed) {
                successes++
                failures = 0
                if (successes >= policy.streakToRaise) {
                    target = minOf(policy.maxMin, target + policy.stepMin)
                    successes = 0
                }
            } else {
                failures++
                successes = 0
                if (failures >= policy.streakToLower) {
                    target = maxOf(policy.minMin, target - policy.stepMin)
                    failures = 0
                }
            }
        }
        return ProgressState(target, successes, failures)
    }
}
