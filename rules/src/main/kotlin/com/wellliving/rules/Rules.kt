package com.wellliving.rules

import java.time.LocalTime

/**
 * The single rules engine. Every module that wants to block something adds a [Rule];
 * the engine asks all of them and the most severe answer wins.
 */
class RulesEngine(private val rules: List<Rule> = DEFAULT_RULES) {

    fun evaluate(ctx: RuleContext): Decision {
        if (ctx.packageName in ctx.essentialPackages) return Decision.Allow
        return rules.mapNotNull { it.evaluate(ctx) }.maxByOrNull { it.severity } ?: Decision.Allow
    }

    companion object {
        val DEFAULT_RULES: List<Rule> = listOf(QuotaRule, NightLockRule)
    }
}

/** Daily quotas and the 60-second gate (module 1, sections 1.1 and 1.3). */
object QuotaRule : Rule {
    override fun evaluate(ctx: RuleContext): Decision? {
        val quotaMinutes = ctx.config.quotas[ctx.packageName] ?: return null
        val passUntil = ctx.state.gatePassUntil[ctx.packageName] ?: 0L
        if (passUntil > ctx.nowMillis) return null

        if (quotaMinutes <= 0) return Decision.Gate(GateReason.BLOCKED_APP)
        val used = ctx.state.usageTodayMillis[ctx.packageName] ?: 0L
        return if (used >= quotaMinutes * 60_000L) Decision.Gate(GateReason.QUOTA_EXCEEDED) else null
    }
}

/** Night lock 22:30–08:00 (module 6, section 6.2; module 1, section 1.4). */
object NightLockRule : Rule {
    override fun evaluate(ctx: RuleContext): Decision? {
        val night = ctx.config.night
        if (!night.enabled) return null

        val time = ctx.now.toLocalTime()
        if (!isWithin(time, night.start, night.end)) return null

        val inMorningWindow = isWithin(time, night.morningDeclarationFrom, night.end)
        if (inMorningWindow && ctx.state.morningDeclaredOn == ctx.now.toLocalDate()) return null

        val pkg = ctx.packageName
        if (pkg in night.allowedPackages) return null
        if (pkg in night.alwaysBlockedPackages) {
            return Decision.HardBlock(HardBlockReason.ALWAYS_BLOCKED_AT_NIGHT)
        }
        if (ctx.state.urgentUnlockUntil > ctx.nowMillis) return null

        return Decision.NightLock(canDeclareMorning = inMorningWindow)
    }

    fun isNight(time: LocalTime, night: NightConfig): Boolean =
        night.enabled && isWithin(time, night.start, night.end)
}

/** True if [time] is in [start, end), where the interval may wrap past midnight. */
internal fun isWithin(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start <= end) {
        time >= start && time < end
    } else {
        time >= start || time < end
    }
