package com.wellliving.rules

/** A foreground transition, as reported by Android's UsageStatsManager. */
data class UsageEvent(val packageName: String, val type: Type, val timeMillis: Long) {
    enum class Type { RESUMED, PAUSED }
}

/**
 * Turns a day's resume/pause events into foreground time per package.
 * Events must be in chronological order (UsageStatsManager returns them that way).
 */
object UsageAggregator {

    fun foregroundMillis(events: List<UsageEvent>, dayStartMillis: Long, nowMillis: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val openSince = HashMap<String, Long>()
        // Several activities of one app can be resumed at once, so count them per package.
        val openActivities = HashMap<String, Int>()
        val seen = HashSet<String>()

        for (event in events) {
            val pkg = event.packageName
            val time = event.timeMillis.coerceIn(dayStartMillis, nowMillis)
            val open = openActivities[pkg] ?: 0
            val firstEvent = seen.add(pkg)
            when (event.type) {
                UsageEvent.Type.RESUMED -> {
                    if (open == 0) openSince[pkg] = time
                    openActivities[pkg] = open + 1
                }
                UsageEvent.Type.PAUSED -> when {
                    // A first pause with nothing open means the app was already in front at midnight.
                    open == 0 -> if (firstEvent) totals.merge(pkg, time - dayStartMillis, Long::plus)
                    open == 1 -> {
                        totals.merge(pkg, time - openSince.getValue(pkg), Long::plus)
                        openSince.remove(pkg)
                        openActivities[pkg] = 0
                    }
                    else -> openActivities[pkg] = open - 1
                }
            }
        }
        for ((pkg, since) in openSince) {
            totals.merge(pkg, nowMillis - since, Long::plus)
        }
        return totals
    }
}
