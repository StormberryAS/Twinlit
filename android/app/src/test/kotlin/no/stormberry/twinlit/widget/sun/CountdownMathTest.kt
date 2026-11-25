package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.WidgetTestPlaces
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class CountdownMathTest {

    private val target = Instant.parse("2026-11-16T14:48:50Z")

    @Test
    fun `the base is the elapsed-realtime instant at which the wall clock reaches the target`() {
        val wall = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
        val base = CountdownMath.base(target, ClockPair(elapsedMs = 86_400_000L, wallMs = wall))
        assertEquals(86_400_000L + (2 * 3600 + 48 * 60 + 50) * 1000L, base)
    }

    @Test
    fun `just after boot the elapsed clock is near zero and the base is still the time left`() {
        val wall = target.toEpochMilli() - 7_200_000L
        assertEquals(1_200L + 7_200_000L, CountdownMath.base(target, ClockPair(1_200L, wall)))
    }

    @Test
    fun `a wall clock change between two renders moves the base by the change`() {
        val wall = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
        val before = CountdownMath.base(target, ClockPair(50_000L, wall))
        // The user sets the clock 5 minutes ahead; elapsed realtime does not jump.
        val after = CountdownMath.base(target, ClockPair(50_000L, wall + 300_000L))
        assertEquals(-300_000L, after - before)
    }

    @Test
    fun `the digit probe accepts only Latin digits with colons`() {
        assertEquals(3723L, CountdownMath.DIGIT_PROBE_SECONDS)
        assertTrue(CountdownMath.digitsOk("1:02:03"))
        assertFalse(CountdownMath.digitsOk("١:٠٢:٠٣"))
        assertFalse(CountdownMath.digitsOk("1.02.03"))
        assertFalse(CountdownMath.digitsOk(null))
    }

    /** The levels that never tick (P17, owner's decision of 2026-11-18). */
    private val neverTickLevels = 28..30

    @Test
    fun `ticking - allowed with nothing in the way, except on Android 9 to 11`() {
        for (sdk in 24..36) {
            val allowed = CountdownMath.tickingAllowed(sdk, backgroundRestricted = false, digitsOk = true)
            assertEquals("$sdk", sdk !in neverTickLevels, allowed)
        }
    }

    @Test
    fun `ticking - never on Android 9 to 11, whatever else is true`() {
        for (sdk in neverTickLevels) {
            assertTrue("$sdk", CountdownMath.neverTicks(sdk))
            for (restricted in listOf(false, true)) {
                for (digits in listOf(false, true)) {
                    assertFalse("$sdk", CountdownMath.tickingAllowed(sdk, restricted, digits))
                }
            }
        }
        for (sdk in listOf(24, 25, 26, 27, 31, 32, 33, 34, 35, 36)) assertFalse("$sdk", CountdownMath.neverTicks(sdk))
    }

    @Test
    fun `ticking - refused when background restricted or when the digits are not Latin, at any API level`() {
        for (sdk in 24..36) {
            assertFalse("$sdk", CountdownMath.tickingAllowed(sdk, backgroundRestricted = true, digitsOk = true))
            assertFalse("$sdk", CountdownMath.tickingAllowed(sdk, backgroundRestricted = false, digitsOk = false))
        }
    }

    @Test
    fun `on Android 9 to 11 every render is a fixed line and no countdown wake-up is planned`() {
        val zone = ZoneId.of("Europe/Oslo")
        // Day and night, short and long way to the next event: every hour of two days.
        var now = Instant.parse("2026-11-16T00:10:00Z")
        val end = now.plus(Duration.ofDays(2))
        while (now.isBefore(end)) {
            for (sdk in neverTickLevels) {
                val allowed = CountdownMath.tickingAllowed(sdk, backgroundRestricted = false, digitsOk = true)
                val state = WidgetModel.build(now, WidgetTestPlaces.OSLO, zone, allowed)!!
                assertFalse("$sdk $now ${state.line}", state.line is CountdownLine.Ticking)
                val plan = WidgetSchedule.plan(now, state.zone, state.target, ticking = state.line is CountdownLine.Ticking)
                assertNull("$sdk $now", plan.handover)
            }
            now = now.plus(Duration.ofMinutes(55))
        }
    }

    @Test
    fun `on Android 12 and later the same renders do tick, and arm the handover`() {
        val zone = ZoneId.of("Europe/Oslo")
        // Sunset in Oslo on 2026-11-16 is about 14:48 UTC; 10:00 UTC is 4 h 48 min before it.
        val now = Instant.parse("2026-11-16T10:00:00Z")
        val allowed = CountdownMath.tickingAllowed(34, backgroundRestricted = false, digitsOk = true)
        val state = WidgetModel.build(now, WidgetTestPlaces.OSLO, zone, allowed)!!
        assertTrue(state.line.toString(), state.line is CountdownLine.Ticking)
        assertNotNull(WidgetSchedule.plan(now, state.zone, state.target, ticking = true).handover)
    }

    @Test
    fun `the widget code asks for the SDK level and never reads Battery Saver`() {
        val code = TestFiles.require("${TestFiles.KOTLIN}/widget/sun/SunWidgets.kt").readText()
        assertTrue(code.contains("CountdownMath.tickingAllowed(sdk, restricted, digits)"))
        assertFalse(code.contains("isPowerSaveMode"))
        assertFalse(code.contains("registerReceiver"))
    }
}
