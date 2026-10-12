package com.wellliving.rules

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesEngineTest {

    private val engine = RulesEngine()
    private val instagram = "com.instagram.android"
    private val youtube = NightConfig.YOUTUBE
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val day = LocalDate.of(2026, 10, 12)
    private val nowMillis = 1_000_000_000L

    private fun ctx(
        pkg: String,
        time: LocalTime,
        config: BlockingConfig = BlockingConfig(),
        state: RuntimeState = RuntimeState(),
        date: LocalDate = day,
    ) = RuleContext(
        packageName = pkg,
        now = LocalDateTime.of(date, time),
        nowMillis = nowMillis,
        config = config,
        state = state,
        essentialPackages = setOf(launcher),
    )

    private val noon = LocalTime.NOON

    @Test
    fun `app without rules is allowed during the day`() {
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, noon)))
    }

    @Test
    fun `app under its quota is allowed`() {
        val config = BlockingConfig(quotas = mapOf(instagram to 30))
        val state = RuntimeState(usageTodayMillis = mapOf(instagram to 29 * 60_000L))
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, noon, config, state)))
    }

    @Test
    fun `app over its quota goes through the gate`() {
        val config = BlockingConfig(quotas = mapOf(instagram to 30))
        val state = RuntimeState(usageTodayMillis = mapOf(instagram to 30 * 60_000L))
        assertEquals(Decision.Gate(GateReason.QUOTA_EXCEEDED), engine.evaluate(ctx(instagram, noon, config, state)))
    }

    @Test
    fun `zero quota gates every opening`() {
        val config = BlockingConfig(quotas = mapOf(instagram to 0))
        assertEquals(Decision.Gate(GateReason.BLOCKED_APP), engine.evaluate(ctx(instagram, noon, config)))
    }

    @Test
    fun `valid gate pass lets the app through and an expired one does not`() {
        val config = BlockingConfig(quotas = mapOf(instagram to 0))
        val valid = RuntimeState(gatePassUntil = mapOf(instagram to nowMillis + 1))
        val expired = RuntimeState(gatePassUntil = mapOf(instagram to nowMillis))
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, noon, config, valid)))
        assertEquals(Decision.Gate(GateReason.BLOCKED_APP), engine.evaluate(ctx(instagram, noon, config, expired)))
    }

    @Test
    fun `night lock starts at 22-30 and ends at 08-00`() {
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, LocalTime.of(22, 29))))
        assertEquals(Decision.NightLock(canDeclareMorning = false), engine.evaluate(ctx(instagram, LocalTime.of(22, 30))))
        assertEquals(Decision.NightLock(canDeclareMorning = false), engine.evaluate(ctx(instagram, LocalTime.of(3, 0))))
        assertEquals(Decision.NightLock(canDeclareMorning = true), engine.evaluate(ctx(instagram, LocalTime.of(7, 59))))
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, LocalTime.of(8, 0))))
    }

    @Test
    fun `allowed and essential apps stay open at night`() {
        assertEquals(Decision.Allow, engine.evaluate(ctx("com.whatsapp", LocalTime.of(23, 0))))
        assertEquals(Decision.Allow, engine.evaluate(ctx("com.waze", LocalTime.of(23, 0))))
        assertEquals(Decision.Allow, engine.evaluate(ctx(launcher, LocalTime.of(23, 0))))
    }

    @Test
    fun `urgent unlock opens the night lock but not youtube`() {
        val state = RuntimeState(urgentUnlockUntil = nowMillis + 60_000L)
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, LocalTime.of(23, 0), state = state)))
        assertEquals(
            Decision.HardBlock(HardBlockReason.ALWAYS_BLOCKED_AT_NIGHT),
            engine.evaluate(ctx(youtube, LocalTime.of(23, 0), state = state)),
        )
    }

    @Test
    fun `morning declaration ends the night lock for that morning only`() {
        val declared = RuntimeState(morningDeclaredOn = day)
        assertEquals(Decision.Allow, engine.evaluate(ctx(instagram, LocalTime.of(6, 30), state = declared)))
        assertEquals(Decision.Allow, engine.evaluate(ctx(youtube, LocalTime.of(6, 30), state = declared)))
        // Yesterday's declaration does not open this morning.
        assertEquals(
            Decision.NightLock(canDeclareMorning = true),
            engine.evaluate(ctx(instagram, LocalTime.of(6, 30), state = declared, date = day.plusDays(1))),
        )
        // A declaration does not open the lock before the morning window.
        assertEquals(
            Decision.NightLock(canDeclareMorning = false),
            engine.evaluate(ctx(instagram, LocalTime.of(4, 0), state = declared)),
        )
    }

    @Test
    fun `night lock outranks a gate pass`() {
        val config = BlockingConfig(quotas = mapOf(instagram to 0))
        val state = RuntimeState(gatePassUntil = mapOf(instagram to nowMillis + 60_000L))
        assertEquals(Decision.NightLock(canDeclareMorning = false), engine.evaluate(ctx(instagram, LocalTime.of(23, 0), config, state)))
    }

    @Test
    fun `quota gate still applies at night after an urgent unlock`() {
        val config = BlockingConfig(quotas = mapOf(instagram to 0))
        val state = RuntimeState(urgentUnlockUntil = nowMillis + 60_000L)
        assertEquals(Decision.Gate(GateReason.BLOCKED_APP), engine.evaluate(ctx(instagram, LocalTime.of(23, 0), config, state)))
    }

    @Test
    fun `disabled night lock never blocks`() {
        val config = BlockingConfig(night = NightConfig(enabled = false))
        assertEquals(Decision.Allow, engine.evaluate(ctx(youtube, LocalTime.of(23, 0), config)))
    }
}
