package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.solar.DayKind
import no.stormberry.twinlit.solar.PlaceDay
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.solar.SunCrossings
import no.stormberry.twinlit.solar.computeSunTimes
import no.stormberry.twinlit.widget.WidgetTestPlaces.APIA
import no.stormberry.twinlit.widget.WidgetTestPlaces.KIRITIMATI
import no.stormberry.twinlit.widget.WidgetTestPlaces.LONGYEARBYEN
import no.stormberry.twinlit.widget.WidgetTestPlaces.LORD_HOWE
import no.stormberry.twinlit.widget.WidgetTestPlaces.NEAR_NORTH_POLE
import no.stormberry.twinlit.widget.WidgetTestPlaces.NORTH_POLE
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.ST_JOHNS
import no.stormberry.twinlit.widget.WidgetTestPlaces.SYDNEY
import no.stormberry.twinlit.widget.WidgetTestPlaces.TROMSO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The widget model (1.1.9). Expected values from suncalc.js under Node, as in SunCrossingsTest. */
class WidgetModelTest {

    private val osloPhone = ZoneId.of("Europe/Oslo")

    private fun state(p: Place, iso: String, ticking: Boolean = true, phone: ZoneId = osloPhone): WidgetState =
        WidgetModel.build(Instant.parse(iso), p, phone, ticking)!!

    private val hms = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.UK)

    @Test
    fun `card parity - sunrise, solar noon and sunset are the card's times cut to minutes, every day of 2026`() {
        for (p in listOf(OSLO, LONGYEARBYEN, TROMSO, SYDNEY, APIA)) {
            val zone = PlaceDay.zoneOf(p.zoneId)
            var d = LocalDate.of(2026, 1, 1)
            while (d.year == 2026) {
                val now = d.atTime(LocalTime.NOON).atZone(zone).toInstant()
                val s = WidgetModel.build(now, p, osloPhone, true)!!
                val card = computeSunTimes(p, d)
                fun cut(i: Instant?) = i?.let { hms.format(it.atZone(zone)).substring(0, 5) } ?: WidgetText.MISSING
                assertEquals(d, s.today)
                assertEquals(card.kind, s.kind)
                assertEquals("${p.label} $d sunrise", cut(card.sunrise), WidgetText.time(s.sunrise, s.zone))
                assertEquals("${p.label} $d noon", cut(card.solarNoon), WidgetText.time(s.solarNoon, s.zone))
                assertEquals("${p.label} $d sunset", cut(card.sunset), WidgetText.time(s.sunset, s.zone))
                d = d.plusDays(1)
            }
        }
    }

    @Test
    fun `Oslo 16 November, twilight and golden hour`() {
        val s = state(OSLO, "2026-11-16T12:00:00Z")
        assertEquals(WidgetText.BandText.Cells("until 09:37", "from 14:29"), WidgetText.golden(s))
        assertEquals(WidgetText.BandText.Cells("07:28", "16:37"), WidgetText.twilight(s.civil, s))
        assertEquals(WidgetText.BandText.Cells("06:36", "17:29"), WidgetText.twilight(s.nautical, s))
        assertEquals(WidgetText.BandText.Cells("05:47", "18:18"), WidgetText.twilight(s.astronomical, s))
        assertEquals("12:03", WidgetText.time(s.solarNoon, s.zone))
    }

    @Test
    fun `Oslo 21 June, a white night - civil dusk after midnight carries +1, the rest is All night`() {
        val s = state(OSLO, "2026-06-21T11:00:00Z")
        assertEquals(DayKind.NORMAL, s.kind)
        assertEquals(WidgetText.BandText.Cells("until 05:14", "from 21:25"), WidgetText.golden(s))
        assertEquals(WidgetText.BandText.Cells("02:10", "00:29 +1"), WidgetText.twilight(s.civil, s))
        assertEquals(Band.AllNight, s.nautical)
        assertEquals(Band.AllNight, s.astronomical)
        assertEquals(WidgetText.BandText.Span("All night"), WidgetText.twilight(s.nautical, s))
    }

    @Test
    fun `Longyearbyen and Tromso, every polar state of the rows`() {
        val june = state(LONGYEARBYEN, "2026-06-21T10:00:00Z")
        assertEquals(Band.None, june.golden) // the sun never drops below 6 degrees
        assertEquals(WidgetText.BandText.Cells("–", "–"), WidgetText.golden(june))
        for (b in listOf(june.civil, june.nautical, june.astronomical)) assertEquals(Band.AllNight, b)
        assertEquals("13:00", WidgetText.time(june.solarNoon, june.zone))

        val dec = state(LONGYEARBYEN, "2026-12-21T11:00:00Z")
        assertEquals(Band.None, dec.golden)
        assertEquals(Band.None, dec.civil)
        assertEquals(WidgetText.BandText.Cells("10:59", "12:53"), WidgetText.twilight(dec.nautical, dec))
        assertEquals(WidgetText.BandText.Cells("07:38", "16:15"), WidgetText.twilight(dec.astronomical, dec))
        assertEquals("11:56", WidgetText.time(dec.solarNoon, dec.zone))

        val oct = state(LONGYEARBYEN, "2026-10-20T10:00:00Z")
        assertEquals(DayKind.NORMAL, oct.kind)
        assertEquals(Band.AllDay, oct.golden)
        assertEquals(WidgetText.BandText.Span("All day"), WidgetText.golden(oct))
        assertEquals(WidgetText.BandText.Cells("08:04", "17:21"), WidgetText.twilight(oct.civil, oct))

        val trJune = state(TROMSO, "2026-06-21T11:00:00Z")
        assertEquals(WidgetText.BandText.Cells("until 02:58", "from 22:36"), WidgetText.golden(trJune))
        for (b in listOf(trJune.civil, trJune.nautical, trJune.astronomical)) assertEquals(Band.AllNight, b)

        val trDec = state(TROMSO, "2026-12-21T11:00:00Z")
        assertEquals("11:43", WidgetText.time(trDec.solarNoon, trDec.zone))
        assertEquals(Band.None, trDec.golden)
        assertEquals(WidgetText.BandText.Cells("09:32", "13:54"), WidgetText.twilight(trDec.civil, trDec))
        assertEquals(WidgetText.BandText.Cells("07:47", "15:38"), WidgetText.twilight(trDec.nautical, trDec))
        assertEquals(WidgetText.BandText.Cells("06:29", "16:57"), WidgetText.twilight(trDec.astronomical, trDec))
    }

    @Test
    fun `89_99 N in March - polar night inside civil twilight is All day and night`() {
        val s = state(NEAR_NORTH_POLE, "2026-03-12T12:00:00Z")
        assertEquals(DayKind.POLAR_NIGHT, s.kind)
        assertEquals(Band.None, s.golden)
        for (b in listOf(s.civil, s.nautical, s.astronomical)) assertEquals(Band.AllDayAndNight, b)
        // 19 March, midnight sun with the sun below 6 degrees all day and night.
        val m = state(NEAR_NORTH_POLE, "2026-03-19T12:00:00Z")
        assertEquals(DayKind.MIDNIGHT_SUN, m.kind)
        assertEquals(Band.AllNight, m.civil)
    }

    @Test
    fun `places far east of their zone's meridian get no marker on any cell`() {
        // Apia and Kiritimati: SunCalc files the whole day under the next local date; the marker is
        // relative to that day's solar noon, so nothing is marked, as sunrise and sunset are not.
        for (p in listOf(APIA, KIRITIMATI)) {
            val s = state(p, "2026-11-16T09:00:00Z")
            assertEquals(LocalDate.of(2026, 11, 16), s.today)
            assertEquals(LocalDate.of(2026, 11, 17), s.noonDate)
            val cells = listOf(WidgetText.golden(s)) + listOf(s.civil, s.nautical, s.astronomical).map { WidgetText.twilight(it, s) }
            for (c in cells) assertFalse("${p.label}: $c", c.toString().contains("+1") || c.toString().contains("−1"))
            assertFalse(WidgetText.describeLarge(s).contains("the next day"))
        }
    }

    @Test
    fun `the countdown line at each threshold, and never ticking when not allowed`() {
        val zone = ZoneId.of("Europe/Oslo")
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val today = LocalDate.of(2026, 11, 16)
        fun at(left: Duration, allowed: Boolean = true) =
            WidgetModel.countdownLine(now, today, zone, up = true, target = now.plus(left), tickingAllowed = allowed)
        assertTrue(at(Duration.ofHours(24).plusSeconds(1)) is CountdownLine.DateForm)
        assertTrue(at(Duration.ofHours(24)) is CountdownLine.Ticking)
        assertTrue(at(Duration.ofMinutes(70).plusSeconds(1)) is CountdownLine.Ticking)
        assertTrue(at(Duration.ofMinutes(70)) is CountdownLine.AtToday)
        assertTrue(at(Duration.ofMinutes(60)) is CountdownLine.AtToday)
        assertTrue(at(Duration.ofSeconds(1)) is CountdownLine.AtToday)
        assertTrue(at(Duration.ofHours(14)) is CountdownLine.Ticking)
        assertTrue(at(Duration.ofHours(14), allowed = false) is CountdownLine.AtTomorrow)
        for (left in listOf(Duration.ofHours(24).plusSeconds(1), Duration.ofHours(24), Duration.ofMinutes(71), Duration.ofMinutes(70), Duration.ofSeconds(1))) {
            assertFalse(at(left, allowed = false) is CountdownLine.Ticking)
        }
        assertEquals(CountdownLine.Hidden, WidgetModel.countdownLine(now, today, zone, true, null, true))
    }

    @Test
    fun `property - the line ticks only between 70 minutes and 24 hours, and then a handover is always armed`() {
        for (p in listOf(OSLO, LONGYEARBYEN, TROMSO)) {
            val zone = PlaceDay.zoneOf(p.zoneId)
            var now = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2027-01-01T00:00:00Z")
            while (now.isBefore(end)) {
                val s = WidgetModel.build(now, p, osloPhone, true)!!
                val line = s.line
                if (line is CountdownLine.Ticking) {
                    val left = Duration.between(now, line.target)
                    assertTrue("${p.label} $now $left", left > WidgetSchedule.TICK_MIN && left <= WidgetSchedule.DATE_FORM_AFTER)
                    val plan = WidgetSchedule.plan(now, zone, s.target, ticking = true)
                    assertEquals(WidgetSchedule.handoverAt(line.target), plan.handover)
                }
                now = now.plus(Duration.ofMinutes(173))
            }
        }
    }

    @Test
    fun `daylight saving days count instants, not wall clock differences`() {
        fun left(iso: String): Duration {
            val s = state(OSLO, iso)
            val l = s.line as CountdownLine.Ticking
            return Duration.between(Instant.parse(iso), l.target).truncatedTo(ChronoUnit.SECONDS)
        }
        assertEquals(Duration.parse("PT4H23M26S"), left("2026-03-29T00:30:00Z"))
        assertEquals(Duration.parse("PT5H51M21S"), left("2026-10-25T00:30:00Z"))
        assertEquals(Duration.parse("PT4H51M21S"), left("2026-10-25T01:30:00Z"))
    }

    @Test
    fun `the zone note shows only when the clocks differ`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val oslo = ZoneId.of("Europe/Oslo")
        val sydney = ZoneId.of("Australia/Sydney")
        assertTrue(WidgetModel.zoneDiffers(sydney, oslo, now))
        assertTrue(WidgetModel.zoneDiffers(oslo, sydney, now))
        assertTrue(WidgetModel.zoneDiffers(ZoneId.of(LORD_HOWE.zoneId), sydney, now))
        assertTrue(WidgetModel.zoneDiffers(ZoneId.of(ST_JOHNS.zoneId), oslo, now))
        assertFalse(WidgetModel.zoneDiffers(ZoneId.of("Arctic/Longyearbyen"), oslo, now))
        assertFalse(WidgetModel.zoneDiffers(oslo, oslo, now))
        assertTrue(state(SYDNEY, "2026-11-16T12:00:00Z").zoneDiffers)
        assertFalse(state(LONGYEARBYEN, "2026-11-16T12:00:00Z").zoneDiffers)
    }

    @Test
    fun `no place gives no state, and the pole gives a hidden line`() {
        assertNull(WidgetModel.build(Instant.parse("2026-11-16T12:00:00Z"), null, osloPhone, true))
        val pole = state(NORTH_POLE, "2026-06-21T12:00:00Z")
        assertNull(pole.target)
        assertEquals(CountdownLine.Hidden, pole.line)
        assertEquals(WidgetText.LineText.Hidden, WidgetText.line(pole, withTime = true))
    }

    @Test
    fun `the target is the next sunset when up and the next sunrise when down`() {
        val s = state(OSLO, "2026-11-16T12:00:00Z")
        assertTrue(s.up)
        assertEquals(
            SunCrossings.next(SolarEvent.SUNSET, s.now, OSLO.latDeg, OSLO.lonDeg, s.zone),
            s.target,
        )
        val n = state(OSLO, "2026-11-16T15:30:00Z")
        assertFalse(n.up)
        assertEquals(Instant.parse("2026-11-17T07:19:58Z"), n.target!!.truncatedTo(ChronoUnit.SECONDS))
    }
}
