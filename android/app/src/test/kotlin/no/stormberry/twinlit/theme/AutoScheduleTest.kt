package no.stormberry.twinlit.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The Auto schedule (SPEC 4.4, approved by Marcos 2026-11-17): Sun within 5 h of solar noon,
 * Twilight from 5 h to 7 h, Moon beyond 7 h, on both sides of the noon at the chosen place.
 *
 * The solar noons are the ones the vendored `tools/golden/suncalc.js` gives under Node, for the
 * date anchored at 12:00 UTC (the Sun tab's rule), copied here as literals; the Kotlin engine is
 * never the source of an expected value:
 *
 *     node -e "…new Function('window',src)(win); win.SunCalc.getTimes(new Date(Date.UTC(2026,5,21,12)), 59.9139, 10.7522).solarNoon"
 */
class AutoScheduleTest {

    private data class Spot(val lat: Double, val lon: Double)

    private val oslo = Spot(59.9139, 10.7522)
    private val longyearbyen = Spot(78.2233, 15.6469)
    private val kiritimati = Spot(1.8721, -157.4278)
    private val honolulu = Spot(21.3069, -157.8583)
    private val auckland = Spot(-36.8485, 174.7635)

    // Node, suncalc.js: solarNoon for the UTC date anchored at 12:00 UTC.
    private val osloNoonJun21 = Instant.parse("2026-06-21T11:19:58.863Z")
    private val osloNoonDec21 = Instant.parse("2026-12-21T11:16:13.487Z")
    private val longyearbyenNoonJun21 = Instant.parse("2026-06-21T11:00:23.969Z")
    private val longyearbyenNoonDec21 = Instant.parse("2026-12-21T10:56:38.367Z")
    private val kiritimatiNoonJun21 = Instant.parse("2026-06-21T22:32:47.761Z")
    private val honoluluNoonJun21 = Instant.parse("2026-06-21T22:34:31.095Z")
    private val aucklandNoonJun22 = Instant.parse("2026-06-22T00:24:02.790Z")

    private fun phase(at: Instant, s: Spot) = AutoSchedule.phaseAt(at, s.lat, s.lon)

    private val h = { n: Long -> Duration.ofHours(n) }
    private val ms = Duration.ofMillis(1)

    @Test
    fun `solar noon itself is Sun and the Sun phase lasts to 5 h on both sides`() {
        val n = osloNoonJun21
        assertEquals(Theme.SUN, phase(n, oslo))
        assertEquals(Theme.SUN, phase(n.plus(h(5)).minus(ms), oslo))
        assertEquals(Theme.SUN, phase(n.minus(h(5)).plus(ms), oslo))
    }

    @Test
    fun `exactly 5 h from noon is already Twilight, on both sides`() {
        val n = osloNoonJun21
        assertEquals(Theme.TWILIGHT, phase(n.plus(h(5)), oslo))
        assertEquals(Theme.TWILIGHT, phase(n.minus(h(5)), oslo))
    }

    @Test
    fun `Twilight lasts to just before 7 h and exactly 7 h from noon is already Moon, on both sides`() {
        val n = osloNoonDec21
        assertEquals(Theme.TWILIGHT, phase(n.plus(h(7)).minus(ms), oslo))
        assertEquals(Theme.TWILIGHT, phase(n.minus(h(7)).plus(ms), oslo))
        assertEquals(Theme.MOON, phase(n.plus(h(7)), oslo))
        assertEquals(Theme.MOON, phase(n.minus(h(7)), oslo))
    }

    @Test
    fun `the rest of the day is Moon, up to the next noon`() {
        val n = osloNoonJun21
        assertEquals(Theme.MOON, phase(n.plus(h(8)), oslo))
        assertEquals(Theme.MOON, phase(n.plus(h(12)), oslo))
        assertEquals(Theme.MOON, phase(n.plus(h(16)), oslo))
        assertEquals(Theme.MOON, phase(n.plus(h(17)), oslo))
        // Seventeen hours after noon is seven hours before the next one: the boundary is at 17 h (plus the day's drift).
        assertEquals(Theme.TWILIGHT, phase(n.plus(h(19)), oslo))
        assertEquals(Theme.SUN, phase(n.plus(h(24)), oslo))
    }

    @Test
    fun `Oslo at midsummer and at midwinter both follow solar noon, not the clock on the wall`() {
        // Midsummer: the sun is up for about 18 hours, midwinter about 6; the schedule is the same 10, 2, 2, 10.
        for (n in listOf(osloNoonJun21, osloNoonDec21)) {
            assertEquals(Theme.SUN, phase(n.plus(h(4)), oslo))
            assertEquals(Theme.TWILIGHT, phase(n.plus(h(6)), oslo))
            assertEquals(Theme.MOON, phase(n.plus(h(8)), oslo))
        }
    }

    @Test
    fun `Longyearbyen in midnight sun and in polar night still follows solar noon`() {
        for (n in listOf(longyearbyenNoonJun21, longyearbyenNoonDec21)) {
            assertEquals(Theme.SUN, phase(n, longyearbyen))
            assertEquals(Theme.TWILIGHT, phase(n.plus(h(6)), longyearbyen))
            assertEquals(Theme.TWILIGHT, phase(n.minus(h(6)), longyearbyen))
            assertEquals(Theme.MOON, phase(n.plus(h(12)), longyearbyen))
        }
    }

    @Test
    fun `west of the date line the nearest noon belongs to the previous UTC date`() {
        // Kiritimati's solar noon is at about 22:33 UTC. At 01:00 UTC on the 22nd the nearest noon is
        // the one of the 21st, 2 h 27 min back, which is two dates of the clock away from "today".
        val now = Instant.parse("2026-06-22T01:00:00Z")
        assertEquals(Theme.SUN, phase(now, kiritimati))
        assertEquals(Theme.SUN, phase(Instant.parse("2026-06-22T01:00:00Z"), honolulu))
        // Honolulu at 03:34:31.095 is 5 h after its noon: Twilight, and exactly so.
        assertEquals(Theme.TWILIGHT, phase(honoluluNoonJun21.plus(h(5)), honolulu))
        assertEquals(Theme.SUN, phase(honoluluNoonJun21.plus(h(5)).minus(ms), honolulu))
        assertEquals(Theme.SUN, phase(kiritimatiNoonJun21, kiritimati))
    }

    @Test
    fun `east of the date line the nearest noon belongs to the next UTC date`() {
        // Auckland's solar noon is at about 00:24 UTC, so at 23:00 UTC on the 21st the nearest noon is
        // the one of the 22nd, 1 h 24 min ahead.
        val now = Instant.parse("2026-06-21T23:00:00Z")
        assertEquals(Theme.SUN, phase(now, auckland))
        assertEquals(Theme.TWILIGHT, phase(aucklandNoonJun22.minus(h(5)), auckland))
        assertEquals(Theme.SUN, phase(aucklandNoonJun22.minus(h(5)).plus(ms), auckland))
        assertEquals(Theme.MOON, phase(aucklandNoonJun22.minus(h(7)), auckland))
    }

    @Test
    fun `nextChange is strictly after now, and the phase flips exactly there`() {
        val spots = listOf(oslo, longyearbyen, kiritimati, honolulu, auckland)
        var checked = 0
        for (spot in spots) {
            var at = Instant.parse("2026-06-20T22:00:00Z")
            repeat(24) {
                val next = AutoSchedule.nextChange(at, spot.lat, spot.lon)
                assertTrue("$spot $at -> $next", next > at)
                // next is the first instant of the new phase.
                assertNotEquals("$spot flips at $next", phase(next.minus(ms), spot), phase(next, spot))
                // Nothing changes between now and then.
                assertEquals(phase(at, spot), phase(next.minus(ms), spot))
                at = next
                checked++
            }
        }
        assertEquals(24 * spots.size, checked)
    }

    @Test
    fun `the phases follow each other Sun, Twilight, Moon, Twilight, Sun, with the approved lengths`() {
        var at = osloNoonJun21.minus(h(1))
        val seen = mutableListOf<Pair<Theme, Duration>>()
        var phase = phase(at, oslo)
        var since = at
        repeat(8) {
            val next = AutoSchedule.nextChange(at, oslo.lat, oslo.lon)
            seen += phase to Duration.between(since, next)
            at = next
            since = next
            phase = phase(next, oslo)
        }
        val order = seen.map { it.first }
        assertEquals(
            listOf(Theme.SUN, Theme.TWILIGHT, Theme.MOON, Theme.TWILIGHT, Theme.SUN, Theme.TWILIGHT, Theme.MOON, Theme.TWILIGHT),
            order,
        )
        // The whole phases (not the first, which started before the scan) last 2 h, 10 h and 2 h, give or take the day's drift.
        val twilight = seen[1].second
        val moon = seen[2].second
        assertTrue(twilight.toMinutes() in 119..121)
        assertTrue(moon.toMinutes() in 597..603)
    }
}
