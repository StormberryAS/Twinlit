package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.lunar.LunarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** The planner with moon rules: one alarm per rule in one request-code space, and MoonApp's M-2 case. */
class MoonPlannerTest {

    @Test
    fun `sun and moon rules share one request-code space safely`() {
        val rules = listOf(testRule(id = "sun-1"), testMoonRule(id = "moon-1"), testMoonRule(id = "moon-2", event = LunarEvent.MOONSET))
        val plan = AlarmPlanner.plan(rules, Instant.parse("2026-06-20T22:00:00Z"))
        assertEquals(3, plan.size)
        assertEquals(3, plan.map { it.requestCode }.toSet().size)
        assertTrue("every base code is even", plan.all { it.requestCode % 2 == 0 })
        for (id in listOf("sun-1", "moon-1", "moon-2")) {
            assertEquals(AlarmPlanner.requestCodeFor(id) or 1, AlarmPlanner.snoozeRequestCodeFor(id))
        }
        val sink = FakeAlarmSink()
        AlarmPlanner.apply(plan, sink)
        assertEquals(plan.map { it.requestCode }.toSet(), sink.armedCodes)
    }

    /**
     * A transit rule arms one alarm per transit across days without one (MoonApp's M-2 case, here in
     * local days). Sydney, around 2026-10-28: MoonApp 1.0.3 armed 00:00 UTC (the edge of the 27th) and
     * 00:33 UTC (the real transit). Walk the plan forwards as the receiver would, ring after ring.
     */
    @Test
    fun `a transit rule arms one alarm per transit across days without one`() {
        val rule = testMoonRule(
            event = LunarEvent.LUNAR_TRANSIT, id = "peak", placeName = "Sydney",
            latDeg = -33.8688, lonDeg = 151.2093, zoneId = "Australia/Sydney",
        )
        var t = Instant.parse("2026-10-20T00:00:00Z")
        val rings = mutableListOf<Instant>()
        repeat(30) {
            val last = rings.lastOrNull()?.let { mapOf("peak" to it.toEpochMilli()) } ?: emptyMap()
            val planned = AlarmPlanner.plan(listOf(rule), t, last).singleOrNull() ?: return@repeat
            rings += planned.occurrence.fireAt
            t = planned.occurrence.fireAt
        }
        val gaps = rings.zipWithNext { a, b -> Duration.between(a, b).toMinutes() }
        assertEquals(30, rings.size)
        assertTrue("transits $rings", gaps.all { it >= 20 * 60 })
        assertNull("no ring at the very start of a UTC day", rings.firstOrNull { it.toString().endsWith("T00:00:00Z") })
    }

    @Test
    fun `a moon rule that was just rung is not armed again with the clock set back`() {
        val rule = testMoonRule(event = LunarEvent.MOONRISE, id = "rise", direction = Direction.AT)
        val first = AlarmPlanner.plan(listOf(rule), Instant.parse("2026-09-04T00:00:00Z")).single().occurrence.fireAt
        val rungAt = first.toEpochMilli()
        val setBack = first.minusSeconds(30)
        // Without the record the same occurrence is armed again; with it, the next one.
        assertEquals(first, AlarmPlanner.plan(listOf(rule), setBack).single().occurrence.fireAt)
        assertTrue(AlarmPlanner.plan(listOf(rule), setBack, mapOf("rise" to rungAt)).single().occurrence.fireAt.isAfter(first))
    }
}
