package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.parityRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The editor's preview per event group (SPEC 7.7, D47), as the pure engine gives it.
 *
 * Daily moon rules (moonrise, moonset, the moon at its highest): thirty days, the days without the
 * event kept and marked. Monthly moon rules (full and new moon night): the next three nights. Every
 * row, in both, is an [OccurrenceEngine.occurrenceFor] result and nothing else.
 */
class MoonPreviewTest {

    private val helsinki = Triple(60.1699, 24.9384, "Europe/Helsinki")

    /** The website's culmination per Helsinki date: `transit-parity.csv`, blank for a day without one. */
    private val helsinkiTransit: Map<LocalDate, Instant?> by lazy {
        parityRows("transit-parity.csv").filter { it[0] == "Helsinki" }
            .associate { LocalDate.parse(it[4]) to it[7].takeIf(String::isNotEmpty)?.let(Instant::parse) }
    }

    private fun helsinkiRule(event: LunarEvent) =
        testMoonRule(event = event, latDeg = helsinki.first, lonDeg = helsinki.second, zoneId = helsinki.third, placeName = "Helsinki")

    @Test
    fun `a daily rule previews thirty days and marks the days without the event`() {
        val rule = helsinkiRule(LunarEvent.LUNAR_TRANSIT)
        val from = LocalDate.of(2026, 1, 1)

        val days = OccurrenceEngine.previewDays(rule, from, 30)

        assertEquals(30, days.size)
        assertEquals((0L until 30L).map { from.plusDays(it) }, days.map { it.date })
        // Every row is an occurrenceFor result, missing days included.
        for (day in days) assertEquals("${day.date}", OccurrenceEngine.occurrenceFor(rule, day.date), day.occurrence)
        // The website's own days against the marks: where the corpus knows the date, the mark agrees with it.
        var known = 0
        for (day in days) {
            if (!helsinkiTransit.containsKey(day.date)) continue
            known++
            assertEquals("${day.date}", helsinkiTransit.getValue(day.date), day.occurrence?.anchorAt)
        }
        assertTrue("the corpus holds several of these dates", known >= 3)
        // 2026-01-02 has no culmination in Helsinki: the day is there, and marked.
        assertNull(days.single { it.date == LocalDate.of(2026, 1, 2) }.occurrence)
        assertEquals(29, days.count { it.occurrence != null })
        // preview() is the same list without the marked days.
        assertEquals(days.mapNotNull { it.occurrence }, OccurrenceEngine.preview(rule, from, 30))
    }

    @Test
    fun `a daily rule's rows are in time order and carry no fallback`() {
        for (event in listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET, LunarEvent.LUNAR_TRANSIT)) {
            val rows = OccurrenceEngine.previewDays(helsinkiRule(event), LocalDate.of(2026, 10, 1), 30).mapNotNull { it.occurrence }
            assertTrue("$event: rows", rows.size >= 28)
            assertEquals("$event: in order", rows.map { it.anchorAt }.sorted(), rows.map { it.anchorAt })
            assertTrue("$event: no fallback for the moon", rows.none { it.usedFallback })
        }
    }

    @Test
    fun `a monthly rule previews the next three nights`() {
        for (event in listOf(LunarEvent.FULL_MOON, LunarEvent.NEW_MOON)) {
            val rule = helsinkiRule(event)
            val from = LocalDate.of(2027, 1, 10)

            val nights = OccurrenceEngine.nextNights(rule, from, 3)

            assertEquals("$event", 3, nights.size)
            // Every row is an occurrenceFor result for its own date.
            for (night in nights) assertEquals("$event ${night.anchorDate}", OccurrenceEngine.occurrenceFor(rule, night.anchorDate), night)
            // About a lunation apart (29.5 days, give or take a day for the night's filing), strictly later each time.
            val gaps = nights.zipWithNext { a, b -> Duration.between(a.anchorAt, b.anchorAt).toHours() / 24.0 }
            assertTrue("$event gaps $gaps", gaps.all { it in 28.5..30.7 })
            assertTrue("$event starts on or after $from", !nights.first().anchorDate.isBefore(from))
            // And there is no other night between them.
            val dates = nights.map { it.anchorDate }
            var d = from
            while (!d.isAfter(dates.last())) {
                assertEquals("$event on $d", d in dates, OccurrenceEngine.occurrenceFor(rule, d) != null)
                d = d.plusDays(1)
            }
        }
    }

    @Test
    fun `nextNights agrees with nextOccurrence on the first night`() {
        val rule = helsinkiRule(LunarEvent.FULL_MOON)
        val from = LocalDate.of(2027, 2, 1)
        val first = OccurrenceEngine.nextNights(rule, from, 1).single()
        val start = from.atStartOfDay(java.time.ZoneId.of(helsinki.third)).toInstant().minusSeconds(1)
        assertEquals(first, OccurrenceEngine.nextOccurrence(rule, start))
        assertNotNull(first)
    }

    @Test
    fun `zero or fewer days or nights is an empty preview`() {
        val rule = helsinkiRule(LunarEvent.MOONRISE)
        assertTrue(OccurrenceEngine.previewDays(rule, LocalDate.of(2026, 6, 21), 0).isEmpty())
        assertTrue(OccurrenceEngine.previewDays(rule, LocalDate.of(2026, 6, 21), -3).isEmpty())
        assertTrue(OccurrenceEngine.nextNights(rule, LocalDate.of(2026, 6, 21), 0).isEmpty())
    }
}
