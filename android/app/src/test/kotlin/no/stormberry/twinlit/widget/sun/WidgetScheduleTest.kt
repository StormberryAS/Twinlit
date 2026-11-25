package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.solar.PlaceDay
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The two widget alarms (1.1.9): the refresh and the handover are separate, and the line never
 * ticks into its last 70 minutes.
 */
class WidgetScheduleTest {

    private val oslo = ZoneId.of("Europe/Oslo")

    @Test
    fun `constants - a 10 minute window, and the handover 70 minutes before the event`() {
        assertEquals(Duration.ofMinutes(10), WidgetSchedule.WINDOW)
        assertEquals(Duration.ofHours(3), WidgetSchedule.MAX_GAP)
        assertEquals(Duration.ofMinutes(60), WidgetSchedule.LAST_STRETCH)
        assertEquals(Duration.ofMinutes(70), WidgetSchedule.TICK_MIN)
        // The handover's window ends at the start of the last stretch.
        val t = Instant.parse("2026-11-16T14:48:50Z")
        assertEquals(t.minus(WidgetSchedule.LAST_STRETCH), WidgetSchedule.handoverAt(t).plus(WidgetSchedule.WINDOW))
    }

    @Test
    fun `Oslo 13 00, ticking towards sunset - the refresh at the event, the handover 70 minutes before it`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val sunset = Instant.parse("2026-11-16T14:48:50Z")
        val plan = WidgetSchedule.plan(now, oslo, sunset, ticking = true)
        assertEquals(sunset.plusSeconds(1), plan.refresh)
        assertEquals(Instant.parse("2026-11-16T13:38:50Z"), plan.handover)
    }

    @Test
    fun `overnight the handover is armed although the midnight refresh comes first`() {
        val now = Instant.parse("2026-11-16T20:48:00Z") // 21:48 local
        val sunrise = Instant.parse("2026-11-17T07:19:58Z")
        val plan = WidgetSchedule.plan(now, oslo, sunrise, ticking = true)
        assertEquals(Instant.parse("2026-11-16T23:00:01Z"), plan.refresh) // the city's midnight + 1 s
        assertEquals(Instant.parse("2026-11-17T06:09:58Z"), plan.handover)
    }

    @Test
    fun `screen off from T minus 4 hours - every render while ticking keeps the same wakeup handover`() {
        val sunset = Instant.parse("2026-11-16T14:48:50Z")
        var now = sunset.minus(Duration.ofHours(4))
        var renders = 0
        while (now.isBefore(sunset.minus(WidgetSchedule.TICK_MIN))) {
            val s = WidgetModel.build(now, OSLO, oslo, tickingAllowed = true)!!
            val plan = WidgetSchedule.plan(now, s.zone, s.target, ticking = s.line is CountdownLine.Ticking)
            assertEquals(sunset, s.target!!.truncatedTo(ChronoUnit.SECONDS))
            assertTrue(s.line is CountdownLine.Ticking)
            assertEquals(WidgetSchedule.handoverAt(s.target!!), plan.handover)
            assertEquals(Instant.parse("2026-11-16T13:38:50Z"), plan.handover!!.truncatedTo(ChronoUnit.SECONDS))
            renders++
            now = now.plus(Duration.ofMinutes(5))
        }
        assertEquals(34, renders)
    }

    @Test
    fun `no handover unless the line ticks, and none once it is past`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val sunset = Instant.parse("2026-11-16T14:48:50Z")
        assertNull(WidgetSchedule.plan(now, oslo, sunset, ticking = false).handover)
        assertNull(WidgetSchedule.plan(now, oslo, null, ticking = true).handover)
        val late = sunset.minus(Duration.ofMinutes(65))
        assertNull(WidgetSchedule.plan(late, oslo, sunset, ticking = true).handover)
        assertEquals(sunset.plusSeconds(1), WidgetSchedule.plan(late, oslo, sunset, ticking = false).refresh)
    }

    @Test
    fun `the date form changes to the countdown 24 hours before the target`() {
        val now = Instant.parse("2027-02-14T12:00:00Z")
        val sunrise = Instant.parse("2027-02-16T10:39:21Z")
        val plan = WidgetSchedule.plan(now, ZoneId.of("Arctic/Longyearbyen"), sunrise, ticking = false)
        // Candidates: now + 3 h (15:00Z), the city's midnight (23:00:01Z), target - 24 h (15 Feb 10:39:21Z).
        assertEquals(Instant.parse("2027-02-14T15:00:00Z"), plan.refresh)
        val later = Instant.parse("2027-02-15T09:00:00Z")
        assertEquals(Instant.parse("2027-02-15T10:39:21Z"), WidgetSchedule.plan(later, ZoneId.of("Arctic/Longyearbyen"), sunrise, false).refresh)
    }

    @Test
    fun `the city's midnight on 23 and 25 hour days, in UTC+14, and on a day that starts at 01 00`() {
        fun midnight(now: Instant, zone: ZoneId) = WidgetSchedule.plan(now, zone, null, ticking = false).refresh
        // Late in the evening, so midnight comes before now + 3 h.
        assertEquals(Instant.parse("2026-03-28T23:00:01Z"), midnight(Instant.parse("2026-03-28T21:30:00Z"), oslo))
        assertEquals(Instant.parse("2026-03-29T22:00:01Z"), midnight(Instant.parse("2026-03-29T20:30:00Z"), oslo))
        assertEquals(Instant.parse("2026-10-25T23:00:01Z"), midnight(Instant.parse("2026-10-25T21:30:00Z"), oslo))
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        assertEquals(Instant.parse("2026-11-17T10:00:01Z"), midnight(Instant.parse("2026-11-17T08:30:00Z"), kiritimati))
        // A zone whose day starts at 01:00 once a year (no local midnight): the refresh is at 01:00:01.
        val havana = ZoneId.of("America/Havana")
        val gap = generateSequence(LocalDate.of(2026, 1, 1)) { it.plusDays(1) }.take(365)
            .first { it.atStartOfDay(havana).toLocalTime() != LocalTime.MIDNIGHT }
        val evening = gap.minusDays(1).atTime(22, 30).atZone(havana).toInstant()
        val r = midnight(evening, havana)
        assertEquals(gap, r.atZone(havana).toLocalDate())
        assertEquals(LocalTime.of(1, 0, 1), r.atZone(havana).toLocalTime())
    }

    @Test
    fun `with no target the refresh is midnight or three hours, whichever is first`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        assertEquals(now.plus(Duration.ofHours(3)), WidgetSchedule.plan(now, oslo, null, false).refresh)
    }

    @Test
    fun `the refresh is always in the future and at most three hours away`() {
        var now = Instant.parse("2026-11-16T00:00:00Z")
        repeat(200) {
            val s = WidgetModel.build(now, OSLO, oslo, true)!!
            val plan = WidgetSchedule.plan(now, PlaceDay.zoneOf(OSLO.zoneId), s.target, s.line is CountdownLine.Ticking)
            assertTrue(plan.refresh.isAfter(now))
            assertTrue(Duration.between(now, plan.refresh) <= WidgetSchedule.MAX_GAP)
            if (s.line is CountdownLine.Ticking) assertNotNull(plan.handover)
            now = now.plus(Duration.ofMinutes(23))
        }
    }
}
