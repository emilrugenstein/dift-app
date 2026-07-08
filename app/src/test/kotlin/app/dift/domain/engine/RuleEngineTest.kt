package app.dift.domain.engine

import app.dift.domain.model.BlockReason
import app.dift.domain.model.DebtState
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness
import app.dift.domain.model.UnblockGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngineTest {

    private val zone = ZoneId.of("UTC")

    private fun at(iso: String): ZonedDateTime = ZonedDateTime.parse(iso).withZoneSameInstant(zone)

    private fun ms(iso: String): Long = at(iso).toInstant().toEpochMilli()

    private fun rule(
        id: Long = 1,
        type: RuleType = RuleType.ALWAYS,
        strictness: Strictness = Strictness.TAP_THROUGH,
        packages: Set<String> = setOf("com.target"),
        deviceWide: Boolean = false,
        enabled: Boolean = true,
        limitMinutes: Int? = null,
        scheduleStart: Int? = null,
        scheduleEnd: Int? = null,
        daysMask: Int? = null,
    ) = Rule(
        id = id,
        name = "r$id",
        type = type,
        enabled = enabled,
        strictness = strictness,
        packages = packages,
        deviceWide = deviceWide,
        limitMinutes = limitMinutes,
        scheduleStartMinuteOfDay = scheduleStart,
        scheduleEndMinuteOfDay = scheduleEnd,
        scheduleDaysMask = daysMask,
    )

    private fun input(
        packageName: String = "com.target",
        rules: List<Rule> = emptyList(),
        usedTodayMs: Long = 0,
        grants: List<UnblockGrant> = emptyList(),
        debtState: DebtState = DebtState.IDLE,
        runtimeDenylist: Set<String> = emptySet(),
        now: ZonedDateTime = at("2026-07-07T12:00:00Z"), // a Tuesday
    ) = RuleEngine.EvaluationInput(
        packageName = packageName,
        rules = rules,
        usedTodayMs = usedTodayMs,
        activeGrants = grants,
        debtState = debtState,
        runtimeDenylist = runtimeDenylist,
        now = now,
    )

    // --- denylist ---

    @Test
    fun `denylisted packages are never blocked even by device-wide rules`() {
        val rules = listOf(rule(type = RuleType.ALWAYS, deviceWide = true, strictness = Strictness.HARD))
        assertEquals(Verdict.Allow, RuleEngine.evaluate(input(packageName = "com.android.phone", rules = rules)))
        assertEquals(
            Verdict.Allow,
            RuleEngine.evaluate(
                input(packageName = "com.fp.launcher", rules = rules, runtimeDenylist = setOf("com.fp.launcher")),
            ),
        )
    }

    // --- ALWAYS ---

    @Test
    fun `always rule blocks its packages and nothing else`() {
        val rules = listOf(rule(type = RuleType.ALWAYS))
        val blocked = RuleEngine.evaluate(input(rules = rules)) as Verdict.Block
        assertEquals(BlockReason.ALWAYS, blocked.reason)
        assertEquals(Verdict.Allow, RuleEngine.evaluate(input(packageName = "com.other", rules = rules)))
    }

    @Test
    fun `disabled rules are ignored`() {
        val rules = listOf(rule(enabled = false))
        assertEquals(Verdict.Allow, RuleEngine.evaluate(input(rules = rules)))
    }

    // --- DAILY_LIMIT ---

    @Test
    fun `limit not reached allows, exactly reached blocks until local midnight`() {
        val rules = listOf(rule(type = RuleType.DAILY_LIMIT, limitMinutes = 30))
        assertEquals(
            Verdict.Allow,
            RuleEngine.evaluate(input(rules = rules, usedTodayMs = 29 * 60_000L)),
        )
        val blocked = RuleEngine.evaluate(input(rules = rules, usedTodayMs = 30 * 60_000L)) as Verdict.Block
        assertEquals(BlockReason.LIMIT_EXHAUSTED, blocked.reason)
        assertEquals(
            at("2026-07-08T00:00:00Z").toInstant().toEpochMilli(),
            blocked.blockedUntilMs,
        )
    }

    // --- SCHEDULE ---

    @Test
    fun `non-wrapping schedule blocks inside the window only`() {
        val rules = listOf(rule(type = RuleType.SCHEDULE, scheduleStart = 9 * 60, scheduleEnd = 17 * 60))
        assertTrue(RuleEngine.evaluate(input(rules = rules, now = at("2026-07-07T12:00:00Z"))) is Verdict.Block)
        assertEquals(
            Verdict.Allow,
            RuleEngine.evaluate(input(rules = rules, now = at("2026-07-07T18:00:00Z"))),
        )
    }

    @Test
    fun `wrapping schedule spans midnight and belongs to the start day's mask`() {
        // 22:30 -> 06:00, Tuesdays only (bit 1). 2026-07-07 is a Tuesday.
        val tuesdayMask = 1 shl 1
        val rules = listOf(
            rule(type = RuleType.SCHEDULE, scheduleStart = 22 * 60 + 30, scheduleEnd = 6 * 60, daysMask = tuesdayMask),
        )
        // Tuesday 23:00 -> active
        assertTrue(RuleEngine.evaluate(input(rules = rules, now = at("2026-07-07T23:00:00Z"))) is Verdict.Block)
        // Wednesday 05:00 (still the Tuesday window) -> active
        assertTrue(RuleEngine.evaluate(input(rules = rules, now = at("2026-07-08T05:00:00Z"))) is Verdict.Block)
        // Wednesday 23:00 -> Wednesday is not in the mask -> allow
        assertEquals(
            Verdict.Allow,
            RuleEngine.evaluate(input(rules = rules, now = at("2026-07-08T23:00:00Z"))),
        )
        // Tuesday 05:00 belongs to MONDAY's window (not in mask) -> allow
        assertEquals(
            Verdict.Allow,
            RuleEngine.evaluate(input(rules = rules, now = at("2026-07-07T05:00:00Z"))),
        )
    }

    @Test
    fun `wrapping schedule blockedUntil points at the window end across midnight`() {
        val rules = listOf(
            rule(
                type = RuleType.SCHEDULE,
                strictness = Strictness.HARD,
                scheduleStart = 22 * 60 + 30,
                scheduleEnd = 6 * 60,
            ),
        )
        val before = RuleEngine.evaluate(input(rules = rules, now = at("2026-07-07T23:00:00Z"))) as Verdict.Block
        assertEquals(ms("2026-07-08T06:00:00Z"), before.blockedUntilMs)
        val after = RuleEngine.evaluate(input(rules = rules, now = at("2026-07-08T05:00:00Z"))) as Verdict.Block
        assertEquals(ms("2026-07-08T06:00:00Z"), after.blockedUntilMs)
    }

    // --- strictness resolution + grants ---

    @Test
    fun `most strict matching rule wins`() {
        val rules = listOf(
            rule(id = 1, strictness = Strictness.TAP_THROUGH),
            rule(id = 2, strictness = Strictness.HARD),
            rule(id = 3, strictness = Strictness.FRICTION),
        )
        val blocked = RuleEngine.evaluate(input(rules = rules)) as Verdict.Block
        assertEquals(2, blocked.ruleId)
        assertEquals(Strictness.HARD, blocked.strictness)
    }

    @Test
    fun `grant defeats tap and friction blocks but never hard`() {
        val grant = listOf(UnblockGrant("com.target", 1, expiresAtMs = ms("2026-07-07T12:10:00Z")))
        val soft = listOf(rule(strictness = Strictness.FRICTION))
        assertEquals(Verdict.Allow, RuleEngine.evaluate(input(rules = soft, grants = grant)))

        val hard = listOf(rule(strictness = Strictness.HARD))
        assertTrue(RuleEngine.evaluate(input(rules = hard, grants = grant)) is Verdict.Block)
    }

    @Test
    fun `expired grants do not unblock`() {
        val grant = listOf(UnblockGrant("com.target", 1, expiresAtMs = ms("2026-07-07T11:00:00Z")))
        val rules = listOf(rule(strictness = Strictness.TAP_THROUGH))
        assertTrue(RuleEngine.evaluate(input(rules = rules, grants = grant)) is Verdict.Block)
    }

    // --- USAGE_DEBT ---

    @Test
    fun `active debt blocks any package device-wide as HARD`() {
        val debtRule = rule(
            id = 9,
            type = RuleType.USAGE_DEBT,
            deviceWide = true,
            packages = emptySet(),
            strictness = Strictness.HARD,
        )
        val debt = DebtState(debtUntilMs = at("2026-07-07T12:01:00Z").toInstant().toEpochMilli())
        val blocked = RuleEngine.evaluate(
            input(packageName = "com.random.app", rules = listOf(debtRule), debtState = debt),
        ) as Verdict.Block
        assertEquals(BlockReason.USAGE_DEBT, blocked.reason)
        assertEquals(Strictness.HARD, blocked.strictness)
        assertEquals(debt.debtUntilMs, blocked.blockedUntilMs)
    }

    @Test
    fun `served debt no longer blocks`() {
        val debtRule = rule(id = 9, type = RuleType.USAGE_DEBT, deviceWide = true, packages = emptySet())
        val debt = DebtState(debtUntilMs = at("2026-07-07T11:59:00Z").toInstant().toEpochMilli())
        assertEquals(
            Verdict.Allow,
            RuleEngine.evaluate(input(packageName = "com.random.app", rules = listOf(debtRule), debtState = debt)),
        )
    }

    @Test
    fun `grants never defeat active debt`() {
        val debtRule = rule(id = 9, type = RuleType.USAGE_DEBT, deviceWide = true, packages = emptySet())
        val debt = DebtState(debtUntilMs = at("2026-07-07T12:05:00Z").toInstant().toEpochMilli())
        val grant = listOf(UnblockGrant("com.random.app", 9, at("2026-07-07T13:00:00Z").toInstant().toEpochMilli()))
        assertTrue(
            RuleEngine.evaluate(
                input(packageName = "com.random.app", rules = listOf(debtRule), debtState = debt, grants = grant),
            ) is Verdict.Block,
        )
    }
}
