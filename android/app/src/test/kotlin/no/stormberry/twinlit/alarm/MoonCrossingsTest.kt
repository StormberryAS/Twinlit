package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A local day can hold two moonrises or two moonsets at high latitude, and a moon alarm must not lose
 * the second (fix round 2). The expected crossings come from sampling the moon's altitude every
 * minute and finding where it crosses the rise threshold, which is a search of its own and not the
 * hourly parabola under test.
 */
class MoonCrossingsTest {

    private val tromso = Triple(69.6492, 18.9553, ZoneId.of("Europe/Oslo"))

    /** Minute by minute: the instants at which the moon's centre crosses the rise altitude upward (rise) or downward (set). */
    private fun sampled(
        event: LunarEvent, date: LocalDate, lat: Double, lon: Double, zone: ZoneId,
    ): List<Instant> {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val out = ArrayList<Instant>()
        var t = start
        var prev = MoonCalc.position(t, lat, lon).altitudeRad - MoonCalc.RISE_ALTITUDE_RAD
        while (t.isBefore(end)) {
            val next = t.plusSeconds(60)
            val alt = MoonCalc.position(next, lat, lon).altitudeRad - MoonCalc.RISE_ALTITUDE_RAD
            if (event == LunarEvent.MOONRISE && prev < 0 && alt >= 0) out += next
            if (event == LunarEvent.MOONSET && prev >= 0 && alt < 0) out += next
            prev = alt
            t = next
        }
        return out
    }

    private fun assertClose(where: String, want: List<Instant>, got: List<Instant>, seconds: Long = 150) {
        assertEquals("$where: how many ($got)", want.size, got.size)
        want.zip(got).forEach { (w, g) ->
            assertTrue("$where: $w against $g", Duration.between(w, g).abs().seconds <= seconds)
        }
    }

    @Test
    fun `Tromso 2026-06-11 has two moonrises in the local day and both are found`() {
        val (lat, lon, zone) = tromso
        val date = LocalDate.of(2026, 6, 11)
        val got = MoonAnchors.instantsOn(LunarEvent.MOONRISE, date, lat, lon, zone)
        assertEquals(2, got.size)
        val local = got.map { it.atZone(zone).toLocalTime() }
        assertTrue("00:11 (${local[0]})", Duration.between(java.time.LocalTime.of(0, 11), local[0]).abs().toMinutes() <= 3)
        assertTrue("23:15 (${local[1]})", Duration.between(java.time.LocalTime.of(23, 15), local[1]).abs().toMinutes() <= 3)
        assertClose("sampled", sampled(LunarEvent.MOONRISE, date, lat, lon, zone), got)
    }

    @Test
    fun `Tromso 2026-06-23 has two moonsets in the local day and both are found`() {
        val (lat, lon, zone) = tromso
        val date = LocalDate.of(2026, 6, 23)
        val got = MoonAnchors.instantsOn(LunarEvent.MOONSET, date, lat, lon, zone)
        assertEquals(2, got.size)
        assertClose("sampled", sampled(LunarEvent.MOONSET, date, lat, lon, zone), got)
    }

    @Test
    fun `the alarm rings at the second moonrise of the day, not eight days later`() {
        val (lat, lon, zone) = tromso
        val rule = testMoonRule(event = LunarEvent.MOONRISE, latDeg = lat, lonDeg = lon, zoneId = zone.id)
        val after = LocalDate.of(2026, 6, 11).atTime(0, 30).atZone(zone).toInstant()
        val next = OccurrenceEngine.nextOccurrence(rule, after)
        assertNotNull(next)
        val local = next!!.fireAt.atZone(zone)
        assertEquals(LocalDate.of(2026, 6, 11), local.toLocalDate())
        assertTrue("23:15 (${local.toLocalTime()})", Duration.between(java.time.LocalTime.of(23, 15), local.toLocalTime()).abs().toMinutes() <= 3)
        // Before the first one, the first one.
        val before = LocalDate.of(2026, 6, 10).atTime(12, 0).atZone(zone).toInstant()
        val first = OccurrenceEngine.nextOccurrence(rule, before)!!.fireAt.atZone(zone)
        assertEquals(LocalDate.of(2026, 6, 11), first.toLocalDate())
        assertTrue(first.toLocalTime().isBefore(java.time.LocalTime.of(1, 0)))
    }

    @Test
    fun `the preview lists both moonrises of such a day`() {
        val (lat, lon, zone) = tromso
        val rule = testMoonRule(event = LunarEvent.MOONRISE, latDeg = lat, lonDeg = lon, zoneId = zone.id)
        val days = OccurrenceEngine.previewDays(rule, LocalDate.of(2026, 6, 10), 3)
        assertEquals(2, days[1].occurrences.size)
        assertEquals(days[1].occurrences.first(), days[1].occurrence)
    }

    @Test
    fun `the crossings are the sampled ones at ordinary and polar places over a whole year`() {
        val places = listOf(
            Triple(59.9139, 10.7522, ZoneId.of("Europe/Oslo")),
            Triple(69.6492, 18.9553, ZoneId.of("Europe/Oslo")),
            Triple(78.2233, 15.6469, ZoneId.of("Europe/Oslo")),
            Triple(64.1466, -21.9426, ZoneId.of("Atlantic/Reykjavik")),
            Triple(-33.8688, 151.2093, ZoneId.of("Australia/Sydney")),
        )
        var twoInADay = 0
        for ((lat, lon, zone) in places) {
            var date = LocalDate.of(2026, 1, 1)
            repeat(120) {
                for (event in listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET)) {
                    val got = MoonAnchors.instantsOn(event, date, lat, lon, zone)
                    val want = sampled(event, date, lat, lon, zone)
                    if (got.size > 1) twoInADay++
                    // A crossing within two minutes of midnight can be on either side: compare only when
                    // none sits that close.
                    val nearEdge = (want + got).any {
                        val l = it.atZone(zone)
                        val m = l.hour * 60 + l.minute
                        m < 2 || m > 1437
                    }
                    if (!nearEdge) assertClose("$lat $date $event", want, got)
                }
                date = date.plusDays(3)
            }
        }
        assertTrue("the corpus holds days with two crossings", twoInADay > 0)
    }

    @Test
    fun `for an ordinary day the crossing is the one times() returns`() {
        val places = listOf(
            Triple(59.9139, 10.7522, ZoneId.of("Europe/Oslo")),
            Triple(35.6762, 139.6503, ZoneId.of("Asia/Tokyo")),
            Triple(-33.8688, 151.2093, ZoneId.of("Australia/Sydney")),
            Triple(34.0522, -118.2437, ZoneId.of("America/Los_Angeles")),
            Triple(69.6492, 18.9553, ZoneId.of("Europe/Oslo")),
        )
        for ((lat, lon, zone) in places) {
            var date = LocalDate.of(2026, 1, 1)
            repeat(365) {
                val times = MoonCalc.times(date, lat, lon, zone)
                for (event in listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET)) {
                    val got = MoonAnchors.instantsOn(event, date, lat, lon, zone)
                    val old = times[event]
                    when {
                        old == null -> assertTrue("$lat $date $event: times() has none, so has crossingsIn (${got})", got.isEmpty())
                        got.size == 1 -> assertEquals("$lat $date $event", old, got.single())
                        else -> assertTrue("$lat $date $event: times() holds one of them", old in got)
                    }
                }
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun `phaseNight is the monthly half of times() and nothing else`() {
        var found = 0
        val lons = listOf(
            OSLO_LON,
            -118.2437,
            151.2093,
            -157.4278,
            -171.7667,
        )
        for (lon in lons) {
            var date = LocalDate.of(2026, 1, 1)
            repeat(400) {
                val times = MoonCalc.times(date, 0.0, lon, ZoneOffset.UTC)
                val night = MoonCalc.phaseNight(date, lon)
                val oldFull = times[LunarEvent.FULL_MOON]
                val oldNew = times[LunarEvent.NEW_MOON]
                when (night?.first) {
                    LunarEvent.FULL_MOON -> { assertEquals(oldFull, night.second); assertNull(oldNew); found++ }
                    LunarEvent.NEW_MOON -> { assertEquals(oldNew, night.second); assertNull(oldFull); found++ }
                    else -> { assertNull(oldFull); assertNull(oldNew) }
                }
                date = date.plusDays(1)
            }
        }
        assertTrue("phases found", found > 40)
    }
}
