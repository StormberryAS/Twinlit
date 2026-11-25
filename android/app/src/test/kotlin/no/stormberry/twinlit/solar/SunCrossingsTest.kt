package no.stormberry.twinlit.solar

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.widget.WidgetTestPlaces.APIA
import no.stormberry.twinlit.widget.WidgetTestPlaces.KIRITIMATI
import no.stormberry.twinlit.widget.WidgetTestPlaces.LONGYEARBYEN
import no.stormberry.twinlit.widget.WidgetTestPlaces.NEAR_NORTH_POLE
import no.stormberry.twinlit.widget.WidgetTestPlaces.NEAR_SOUTH_POLE
import no.stormberry.twinlit.widget.WidgetTestPlaces.NORTH_POLE
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.POLE_FAR_ZONE
import no.stormberry.twinlit.widget.WidgetTestPlaces.SUVA
import no.stormberry.twinlit.widget.WidgetTestPlaces.TROMSO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Is the sun up, and when does it next cross (1.1.9). Every expected instant was computed with the
 * bundled suncalc.js under Node (12:00 UTC anchor, as `SunCalc.times(date)`), never copied from
 * Kotlin output, and is compared to the second.
 */
class SunCrossingsTest {

    private fun up(p: Place, iso: String) = SunCrossings.isUp(Instant.parse(iso), p.latDeg, p.lonDeg, PlaceDay.zoneOf(p.zoneId))

    private fun next(event: SolarEvent, p: Place, iso: String): Instant? =
        SunCrossings.next(event, Instant.parse(iso), p.latDeg, p.lonDeg, PlaceDay.zoneOf(p.zoneId))

    private fun target(p: Place, iso: String): Instant? =
        next(if (up(p, iso)) SolarEvent.SUNSET else SolarEvent.SUNRISE, p, iso)

    private fun sec(i: Instant?) = i?.truncatedTo(ChronoUnit.SECONDS)

    @Test
    fun `Oslo 16 November, up exactly from sunrise and down exactly from sunset`() {
        val t = SunCalc.times(LocalDate.of(2026, 11, 16), OSLO.latDeg, OSLO.lonDeg)
        val rise = t.getValue(SolarEvent.SUNRISE)!!
        val set = t.getValue(SolarEvent.SUNSET)!!
        assertEquals(Instant.parse("2026-11-16T07:17:29Z"), sec(rise))
        assertEquals(Instant.parse("2026-11-16T14:48:50Z"), sec(set))
        val zone = PlaceDay.zoneOf(OSLO.zoneId)
        assertFalse(SunCrossings.isUp(rise.minusSeconds(1), OSLO.latDeg, OSLO.lonDeg, zone))
        assertTrue(SunCrossings.isUp(rise, OSLO.latDeg, OSLO.lonDeg, zone))
        assertTrue(SunCrossings.isUp(set.minusSeconds(1), OSLO.latDeg, OSLO.lonDeg, zone))
        assertFalse(SunCrossings.isUp(set, OSLO.latDeg, OSLO.lonDeg, zone))
        val nextRise = SunCrossings.next(SolarEvent.SUNRISE, set.plusSeconds(1), OSLO.latDeg, OSLO.lonDeg, zone)
        assertEquals(Instant.parse("2026-11-17T07:19:58Z"), sec(nextRise))
    }

    @Test
    fun `Longyearbyen through the year, including the end and the onset of the midnight sun`() {
        // Midnight sun, 21 June 12:00 local: up, the sun next sets on Wed 26 Aug 00:28.
        assertTrue(up(LONGYEARBYEN, "2026-06-21T10:00:00Z"))
        assertEquals(Instant.parse("2026-08-25T22:28:35Z"), sec(target(LONGYEARBYEN, "2026-06-21T10:00:00Z")))
        // Polar night, 21 December and 16 November: down, the sun next rises on Tue 16 Feb 2027 11:39.
        assertFalse(up(LONGYEARBYEN, "2026-12-21T11:00:00Z"))
        assertEquals(Instant.parse("2027-02-16T10:39:21Z"), sec(target(LONGYEARBYEN, "2026-12-21T11:00:00Z")))
        assertEquals(Instant.parse("2027-02-16T10:39:21Z"), sec(target(LONGYEARBYEN, "2026-11-16T12:00:00Z")))
        // 15 Feb 2027 13:00 local: less than 24 hours before that sunrise.
        assertFalse(up(LONGYEARBYEN, "2027-02-15T12:00:00Z"))
        // The end of the midnight sun: Tue 25 Aug 00:30 (no event yet today, yesterday
        // MIDNIGHT_SUN) and 02:00 (after the mirrored 01:33 "sunrise") are both up.
        assertTrue(up(LONGYEARBYEN, "2026-08-24T22:30:00Z"))
        assertEquals(Instant.parse("2026-08-25T22:28:35Z"), sec(target(LONGYEARBYEN, "2026-08-24T22:30:00Z")))
        assertTrue(up(LONGYEARBYEN, "2026-08-25T00:00:00Z"))
        // Wed 26 Aug 00:40: after the first real sunset, down, the next sunrise is 02:05:57.
        assertFalse(up(LONGYEARBYEN, "2026-08-25T22:40:00Z"))
        assertEquals(Instant.parse("2026-08-26T00:05:57Z"), sec(target(LONGYEARBYEN, "2026-08-25T22:40:00Z")))
        // The onset, Sun 19 Apr 00:30: a MIDNIGHT_SUN date, so up, as the card says.
        assertTrue(up(LONGYEARBYEN, "2026-04-18T22:30:00Z"))
    }

    @Test
    fun `Tromso in polar night, in the midnight sun, and at the season edges`() {
        assertFalse(up(TROMSO, "2026-12-21T11:00:00Z"))
        assertEquals(Instant.parse("2027-01-16T10:24:32Z"), sec(target(TROMSO, "2026-12-21T11:00:00Z")))
        assertTrue(up(TROMSO, "2026-06-21T11:00:00Z"))
        assertEquals(Instant.parse("2026-07-26T22:37:30Z"), sec(target(TROMSO, "2026-06-21T11:00:00Z")))
        // 27 November is the last NORMAL day (sunrise 10:09:25Z, sunset 10:56:59Z); 28 November polar night.
        assertTrue(up(TROMSO, "2026-11-27T10:30:00Z"))
        assertFalse(up(TROMSO, "2026-11-28T10:30:00Z"))
        // 16 January 2027 is NORMAL again: up just after its sunrise.
        assertTrue(up(TROMSO, "2027-01-16T10:45:00Z"))
    }

    @Test
    fun `near the date line next returns the earliest crossing, whatever date SunCalc files it under`() {
        for (p in listOf(APIA, SUVA, KIRITIMATI)) {
            var now = Instant.parse("2026-11-14T00:00:00Z")
            while (now.isBefore(Instant.parse("2026-11-19T00:00:00Z"))) {
                for (event in listOf(SolarEvent.SUNRISE, SolarEvent.SUNSET)) {
                    val got = SunCrossings.next(event, now, p.latDeg, p.lonDeg, PlaceDay.zoneOf(p.zoneId))
                    val today = PlaceDay.today(PlaceDay.zoneOf(p.zoneId), now)
                    val brute = (-3L..4L).mapNotNull { SunCalc.times(today.plusDays(it), p.latDeg, p.lonDeg)[event] }
                        .filter { it.isAfter(now) }.minOrNull()
                    assertEquals("${p.label} $event after $now", brute, got)
                }
                now = now.plus(Duration.ofMinutes(97))
            }
        }
    }

    @Test
    fun `near the poles`() {
        // 89.99 N, 12 March: polar night with the nadir above -6 degrees. 19 March: midnight sun.
        assertFalse("89.99 N 12 Mar", up(NEAR_NORTH_POLE, "2026-03-12T12:00:00Z"))
        assertTrue("89.99 N 19 Mar", up(NEAR_NORTH_POLE, "2026-03-19T12:00:00Z"))
        // 89.99 S at the June solstice: polar night. So close to the pole the sun goes from never
        // rising to never setting around the equinox without SunCalc reporting a sunrise on any
        // day, so there is no target: the countdown line hides and the polar label stays.
        assertFalse("89.99 S June", up(NEAR_SOUTH_POLE, "2026-06-21T12:00:00Z"))
        assertNull("89.99 S June target", target(NEAR_SOUTH_POLE, "2026-06-21T12:00:00Z"))
        // At the pole itself SunCalc finds no crossing at all: no target, and the line is hidden.
        assertNull("90 N rise", next(SolarEvent.SUNRISE, NORTH_POLE, "2026-06-21T12:00:00Z"))
        assertNull("90 N set", next(SolarEvent.SUNSET, NORTH_POLE, "2026-06-21T12:00:00Z"))
    }

    @Test
    fun `known approximation, pinned - a pole point on a clock far from solar time`() {
        // 89.99 N, 170 W with an Oslo clock: more than 12 hours from solar time, so the city's date
        // asks SunCalc about a solar day that is mostly the adjacent one, and DayKindCalculator
        // judges that day. isUp follows the DayKind of the city's date; this test pins exactly that,
        // so a change of behaviour here is a decision, not an accident.
        for (iso in listOf("2026-03-18T23:30:00Z", "2026-03-19T23:30:00Z", "2026-09-24T23:30:00Z")) {
            val now = Instant.parse(iso)
            val zone = PlaceDay.zoneOf(POLE_FAR_ZONE.zoneId)
            val kind = DayKindCalculator.of(PlaceDay.today(zone, now), POLE_FAR_ZONE.latDeg, POLE_FAR_ZONE.lonDeg)
            if (kind != DayKind.NORMAL) assertEquals(iso, kind == DayKind.MIDNIGHT_SUN, up(POLE_FAR_ZONE, iso))
        }
    }

    @Test
    fun `the altitudes are the noon and nadir samples`() {
        val a = SunCrossings.altitudes(LocalDate.of(2026, 6, 21), LONGYEARBYEN.latDeg, LONGYEARBYEN.lonDeg)
        assertEquals(35.216, a.noonDeg, 0.001)
        assertEquals(11.661, a.nadirDeg, 0.001)
        val b = SunCrossings.altitudes(LocalDate.of(2026, 3, 12), NEAR_NORTH_POLE.latDeg, NEAR_NORTH_POLE.lonDeg)
        assertTrue(b.noonDeg < -0.833 && b.nadirDeg > -6.0)
    }
}
