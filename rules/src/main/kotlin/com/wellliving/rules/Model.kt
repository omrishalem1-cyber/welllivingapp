package com.wellliving.rules

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** User configuration that the rules read. Stored on the phone; never sent anywhere. */
data class BlockingConfig(
    /** Daily quota in minutes per package. A quota of 0 means every opening goes through the gate. */
    val quotas: Map<String, Int> = emptyMap(),
    /** Minutes of use granted after the user completes the 60-second gate. */
    val gatePassMinutes: Int = 5,
    val night: NightConfig = NightConfig(),
)

data class NightConfig(
    val enabled: Boolean = true,
    val start: LocalTime = LocalTime.of(22, 30),
    val end: LocalTime = LocalTime.of(8, 0),
    /** Apps that stay open during the night lock, in addition to the essential system apps. */
    val allowedPackages: Set<String> = DEFAULT_NIGHT_ALLOWED,
    /** Apps that stay blocked at night even after an urgent unlock. */
    val alwaysBlockedPackages: Set<String> = setOf(YOUTUBE),
    val urgentUnlockMinutes: Int = 15,
    /** From this hour until [end], the user may explicitly declare that the day has started. */
    val morningDeclarationFrom: LocalTime = LocalTime.of(5, 0),
) {
    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        val DEFAULT_NIGHT_ALLOWED = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.waze",
        )
    }
}

/** Facts that change during the day. */
data class RuntimeState(
    /** Foreground time today, in milliseconds, per package. */
    val usageTodayMillis: Map<String, Long> = emptyMap(),
    /** Epoch millis until which a gate pass is valid, per package. */
    val gatePassUntil: Map<String, Long> = emptyMap(),
    /** Epoch millis until which an urgent night unlock is valid. */
    val urgentUnlockUntil: Long = 0L,
    /** The date on which the user declared an early start of the day, if any. */
    val morningDeclaredOn: LocalDate? = null,
)

data class RuleContext(
    val packageName: String,
    val now: LocalDateTime,
    val nowMillis: Long,
    val config: BlockingConfig,
    val state: RuntimeState,
    /** Launcher, dialer, alarm clock, keyboard, system UI and this app itself: never blocked. */
    val essentialPackages: Set<String>,
)

/** What should happen when [RuleContext.packageName] is in the foreground. */
sealed interface Decision {
    /** Higher wins when several rules apply. */
    val severity: Int

    data object Allow : Decision {
        override val severity = 0
    }

    /** The 60-second breathing gate; after it the user may continue for a limited time. */
    data class Gate(val reason: GateReason) : Decision {
        override val severity = 1
    }

    /** The night lock; can be opened by an urgent unlock or, in the morning, by a declaration. */
    data class NightLock(val canDeclareMorning: Boolean) : Decision {
        override val severity = 2
    }

    /** No way through from inside the app. */
    data class HardBlock(val reason: HardBlockReason) : Decision {
        override val severity = 3
    }
}

enum class GateReason { BLOCKED_APP, QUOTA_EXCEEDED }

enum class HardBlockReason { ALWAYS_BLOCKED_AT_NIGHT }

/** One source of blocking (quota, night lock, later deep work). Returns null when it has nothing to say. */
fun interface Rule {
    fun evaluate(ctx: RuleContext): Decision?
}
