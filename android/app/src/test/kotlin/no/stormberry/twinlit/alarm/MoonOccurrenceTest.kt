package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.Clamp
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.parityRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The moon half of the occurrence engine (SPEC 7.4): moonrise, moonset and the moon at its highest
 * per local date, a day without the event, offsets across midnight, the clamp and DST days.
 *
 * ### Where the expected instants come from
 *
 * Never from the Kotlin under test. The moonrise and moonset of a local date are the rows of
 * `webparity-golden.csv`, and the moon's culmination of a local date the rows of
 * `transit-parity.csv`: both written from MoonApp's compiled web page (`tools/gen-webparity-golden.ts`
 * and `tools/gen-lunar-parity.ts`, run in a scratch copy of MoonApp 68c7596 with the one place the build
 * must not use swapped for Helsinki; `tools/golden/README.md`). So the engine is held to the
 * website's answer for the same place, zone and calendar date, not to a copy of its own output.
 */
class MoonOccurrenceTest {

    /** A row of `webparity-golden.csv`: the website's rise and set in the place's local day. */
    private data class WebRow(
        val place: String, val lat: Double, val lon: Double, val zone: String,
        val date: LocalDate, val rise: Instant?, val set: Instant?,
    )

    /** A row of `transit-parity.csv`: the website's culmination in the place's local day. */
    private data class TransitRow(
        val place: String, val lat: Double, val lon: Double, val zone: String,
        val date: LocalDate, val transit: Instant?,
    )

    private fun instantOrNull(text: String): Instant? = text.takeIf { it.isNotEmpty() }?.let(Instant::parse)

    private val webRows: List<WebRow> by lazy {
        parityRows("webparity-golden.csv").map { f ->
            WebRow(f[0], f[1].toDouble(), f[2].toDouble(), f[3], LocalDate.parse(f[4]), instantOrNull(f[5]), instantOrNull(f[6]))
        }
    }

    private val transitRows: List<TransitRow> by lazy {
        parityRows("transit-parity.csv").map { f ->
            TransitRow(f[0], f[1].toDouble(), f[2].toDouble(), f[3], LocalDate.parse(f[4]), instantOrNull(f[7]))
        }
    }

    private fun ruleAt(
        event: LunarEvent, lat: Double, lon: Double, zone: String,
        direction: Direction = Direction.AT, offset: Int = 0, clamp: Clamp? = null,
    ) = testMoonRule(
        event = event, direction = direction, offsetMinutes = offset, latDeg = lat, lonDeg = lon, zoneId = zone, clamp = clamp,
    )

    // -------------------------------------------------------------------------------------
    // The instant of an event on a local date equals the website's
    // -------------------------------------------------------------------------------------

    @Test
    fun `moonrise and moonset on a local date are the website's, and a day without one has no occurrence`() {
        assertTrue("rows in webparity-golden.csv", webRows.size == 91)
        var blank = 0
        for (row in webRows) {
            for ((event, want) in listOf(LunarEvent.MOONRISE to row.rise, LunarEvent.MOONSET to row.set)) {
                val occurrence = OccurrenceEngine.occurrenceFor(ruleAt(event, row.lat, row.lon, row.zone), row.date)
                val where = "${row.place} ${row.date} $event"
                if (want == null) {
                    blank++
                    assertNull("$where: the website has none", occurrence)
                } else {
                    assertNotNull("$where: the website has $want", occurrence)
                    assertEquals(where, want, occurrence!!.anchorAt)
                    assertEquals("$where: no offset, no clamp", want, occurrence.fireAt)
                    assertEquals("$where: filed under its own date", row.date, occurrence.anchorDate)
                    assertFalse("$where: a moon rule never falls back", occurrence.usedFallback)
                    assertFalse(where, occurrence.clamped)
                }
            }
        }
        assertTrue("the corpus holds days without a rise or a set (high latitudes)", blank > 0)
    }

    @Test
    fun `the moon at its highest on a local date is the website's, and a day without one has no occurrence`() {
        assertEquals("rows in transit-parity.csv", 820, transitRows.size)
        var blank = 0
        for (row in transitRows) {
            val occurrence = OccurrenceEngine.occurrenceFor(ruleAt(LunarEvent.LUNAR_TRANSIT, row.lat, row.lon, row.zone), row.date)
            val where = "${row.place} ${row.date}"
            if (row.transit == null) {
                blank++
                assertNull("$where: the website has no culmination that day", occurrence)
            } else {
                assertNotNull("$where: the website has ${row.transit}", occurrence)
                assertEquals(where, row.transit, occurrence!!.anchorAt)
                assertEquals(where, row.date, occurrence.anchorDate)
            }
        }
        assertTrue("the corpus holds days without a culmination", blank > 0)
    }

    // -------------------------------------------------------------------------------------
    // A day without the event is skipped, not guessed
    // -------------------------------------------------------------------------------------

    /**
     * Helsinki, no culmination on a day: the website has none and the alarm skips it. The next
     * occurrence from the start of that day is the next day's culmination, not a fallback to some
     * other hour and not a lookup into the next month (D16).
     */
    @Test
    fun `a day without the event is skipped by nextOccurrence and gets no fallback`() {
        val byDate = transitRows.filter { it.place == "Helsinki" }.associateBy { it.date }
        val blankDay = byDate.values.sortedBy { it.date }.first { row ->
            row.transit == null && byDate[row.date.plusDays(1)]?.transit != null
        }
        val rule = ruleAt(LunarEvent.LUNAR_TRANSIT, blankDay.lat, blankDay.lon, blankDay.zone)
        val zone = ZoneId.of(blankDay.zone)

        assertNull(OccurrenceEngine.occurrenceFor(rule, blankDay.date))

        val from = blankDay.date.atStartOfDay(zone).toInstant().minusSeconds(1)
        val next = OccurrenceEngine.nextOccurrence(rule, from)!!
        assertEquals(byDate.getValue(blankDay.date.plusDays(1)).transit, next.fireAt)
        assertEquals(blankDay.date.plusDays(1), next.anchorDate)
        assertFalse(next.usedFallback)
    }

    /** A place where the moon does not rise for days: the search goes on up to 366 days (MoonApp: 30). */
    @Test
    fun `a moonrise rule at a high latitude still finds the next moonrise`() {
        val tromso = webRows.first { it.place == "Tromso" && it.rise == null }
        val rule = ruleAt(LunarEvent.MOONRISE, tromso.lat, tromso.lon, tromso.zone)
        val from = tromso.date.atStartOfDay(ZoneId.of(tromso.zone)).toInstant()

        val next = OccurrenceEngine.nextOccurrence(rule, from)!!

        assertTrue("the next moonrise comes after $from", next.fireAt.isAfter(from))
        assertEquals(next.anchorAt, OccurrenceEngine.occurrenceFor(rule, next.anchorDate)!!.anchorAt)
    }

    // -------------------------------------------------------------------------------------
    // Offsets across midnight, the clamp and DST days
    // -------------------------------------------------------------------------------------

    /**
     * A moonrise at 00:35 UTC on 9 May in Helsinki is 03:35 local; three hours before it is the
     * previous local evening. The occurrence still belongs to 9 May (the anchor date), rings on the
     * 8th, and [OccurrenceEngine.nextOccurrence] finds it from a moment in between.
     */
    @Test
    fun `an offset before an early moonrise rings on the previous local day`() {
        val row = webRows.first { it.place == "Helsinki" && it.date == LocalDate.of(2026, 5, 9) }
        val rule = ruleAt(LunarEvent.MOONRISE, row.lat, row.lon, row.zone, Direction.BEFORE, 4 * 60)

        val occurrence = OccurrenceEngine.occurrenceFor(rule, row.date)!!
        val zone = ZoneId.of(row.zone)

        assertEquals(row.rise, occurrence.anchorAt)
        assertEquals(Instant.parse("2026-05-08T20:35:44.347Z"), occurrence.fireAt)
        assertEquals(row.date, occurrence.anchorDate)
        assertEquals(LocalDate.of(2026, 5, 8), occurrence.fireAt.atZone(zone).toLocalDate())
        assertEquals(LocalTime.parse("23:35:44.347"), occurrence.fireAt.atZone(zone).toLocalTime())

        val between = Instant.parse("2026-05-08T12:00:00Z")
        assertEquals(occurrence, OccurrenceEngine.nextOccurrence(rule, between))
    }

    /** A moonset after a late moonrise's own offset can fall on the next local day (the sun's rule, unchanged for the moon). */
    @Test
    fun `an offset after a late moonset rings on the next local day`() {
        val row = webRows.first { it.place == "Oslo" && it.set != null && it.set.atZone(ZoneId.of(it.zone)).hour >= 20 }
        val rule = ruleAt(LunarEvent.MOONSET, row.lat, row.lon, row.zone, Direction.AFTER, 5 * 60)
        val zone = ZoneId.of(row.zone)

        val occurrence = OccurrenceEngine.occurrenceFor(rule, row.date)!!

        assertEquals(row.set!!.plusSeconds(5 * 3600L), occurrence.fireAt)
        assertEquals(row.date, occurrence.anchorDate)
        assertEquals(row.date.plusDays(1), occurrence.fireAt.atZone(zone).toLocalDate())
    }

    @Test
    fun `a clamp bounds a moon alarm on the day it rings`() {
        val row = webRows.first { it.place == "Sydney" && it.date == LocalDate.of(2026, 2, 28) }
        val zone = ZoneId.of(row.zone)
        // The website's moonset on this date is at some local time; bound it at 00:30 local so the ceiling bites.
        assertNotNull(row.set)
        val ceiling = ruleAt(LunarEvent.MOONSET, row.lat, row.lon, row.zone, clamp = Clamp(latest = LocalTime.of(0, 30)))
        val bitten = OccurrenceEngine.occurrenceFor(ceiling, row.date)!!
        assertTrue(bitten.clamped)
        assertEquals(row.date.atTime(0, 30).atZone(zone).toInstant(), bitten.fireAt)
        assertEquals(row.set, bitten.anchorAt)

        val loose = ruleAt(LunarEvent.MOONSET, row.lat, row.lon, row.zone, clamp = Clamp(latest = LocalTime.of(23, 59)))
        val untouched = OccurrenceEngine.occurrenceFor(loose, row.date)!!
        assertFalse(untouched.clamped)
        assertEquals(row.set, untouched.fireAt)
    }

    /**
     * Helsinki changes its clocks on the same two days as Oslo (29 March and 25 October 2026): across
     * both weekends every date gives the website's culmination or none, in strictly increasing real
     * time, and an offset is real time, not wall time.
     */
    @Test
    fun `both DST weekends keep one moon occurrence per date and offsets in real time`() {
        val rows = transitRows.filter { it.place == "Helsinki" }.sortedBy { it.date }
        val rule = ruleAt(LunarEvent.LUNAR_TRANSIT, rows[0].lat, rows[0].lon, rows[0].zone, Direction.AFTER, 5 * 60)
        val zone = ZoneId.of(rows[0].zone)
        var checked = 0
        for (spring in listOf(LocalDate.of(2026, 3, 28)..LocalDate.of(2026, 3, 30), LocalDate.of(2026, 10, 24)..LocalDate.of(2026, 10, 26))) {
            val fires = mutableListOf<Instant>()
            for (row in rows.filter { it.date in spring }) {
                val occurrence = OccurrenceEngine.occurrenceFor(rule, row.date)
                if (row.transit == null) {
                    assertNull("${row.date}", occurrence)
                } else {
                    assertEquals("${row.date}", Duration.ofHours(5), Duration.between(occurrence!!.anchorAt, occurrence.fireAt))
                    assertEquals("${row.date}", row.transit, occurrence.anchorAt)
                    fires += occurrence.fireAt
                    checked++
                }
            }
            assertEquals("strictly increasing around $spring", fires.sorted(), fires)
        }
        assertTrue("the DST weekends hold moon rows", checked >= 5)
        // The wall-clock length of five real hours differs from five hours on the day the clocks change.
        val dstDay = rows.first { it.date == LocalDate.of(2026, 3, 29) }
        if (dstDay.transit != null) {
            val occurrence = OccurrenceEngine.occurrenceFor(rule, dstDay.date)!!
            val wall = Duration.between(occurrence.anchorAt.atZone(zone).toLocalDateTime(), occurrence.fireAt.atZone(zone).toLocalDateTime())
            assertEquals("the offset is five hours of real time", Duration.ofHours(5), Duration.between(occurrence.anchorAt, occurrence.fireAt))
            assertTrue("and five hours of wall time unless the clocks changed in between (here: $wall)", wall.toHours() in 5L..6L)
        }
    }

    // -------------------------------------------------------------------------------------
    // The preview and nextOccurrence agree; at most one culmination per day
    // -------------------------------------------------------------------------------------

    @Test
    fun `preview rows for a moon rule are occurrenceFor results and leave out the days without the event`() {
        val rule = ruleAt(LunarEvent.LUNAR_TRANSIT, 60.1699, 24.9384, "Europe/Helsinki")
        val from = LocalDate.of(2026, 1, 1)

        val rows = OccurrenceEngine.preview(rule, from, 30)

        assertTrue("the website has no culmination on 2026-01-02 at Helsinki", rows.none { it.anchorDate == LocalDate.of(2026, 1, 2) })
        assertEquals(29, rows.size)
        for (row in rows) assertEquals(OccurrenceEngine.occurrenceFor(rule, row.anchorDate), row)
    }

    /**
     * Consecutive occurrences of each daily event, chained the way the receiver re-arms them, are a
     * lunar day apart at least: MoonApp 1.0.3 put a transit alarm at the edge of a UTC day and again
     * at the real transit just after it (2026-10-28 00:00 and 00:33 UTC at Sydney), so it rang twice
     * within an hour (M-2). In local days this cannot happen, because each local day holds one.
     */
    @Test
    fun `chained over a year the daily moon events are a lunar day apart and never ring twice in an hour`() {
        val places = listOf(
            Triple("Oslo", 59.9139 to 10.7522, "Europe/Oslo"),
            Triple("Helsinki", 60.1699 to 24.9384, "Europe/Helsinki"),
            Triple("Sydney", -33.8688 to 151.2093, "Australia/Sydney"),
        )
        val failures = mutableListOf<String>()
        for ((name, coordinates, zone) in places) {
            for (event in listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET, LunarEvent.LUNAR_TRANSIT)) {
                val rule = ruleAt(event, coordinates.first, coordinates.second, zone)
                var t = Instant.parse("2026-01-01T00:00:00Z")
                val end = Instant.parse("2027-01-01T00:00:00Z")
                var count = 0
                while (t.isBefore(end)) {
                    val next = OccurrenceEngine.nextOccurrence(rule, t)?.fireAt ?: break
                    if (count > 0 && Duration.between(t, next) < Duration.ofHours(20)) failures += "$name $event: $t then $next"
                    t = next
                    count++
                }
                if (count < 300) failures += "$name $event: only $count occurrences in 2026"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the planner drops a switched-off moon rule though the engine still computes it`() {
        val off = ruleAt(LunarEvent.MOONRISE, 59.9139, 10.7522, "Europe/Oslo").copy(enabled = false)
        assertTrue(AlarmPlanner.plan(listOf(off), Instant.parse("2026-06-21T00:00:00Z")).isEmpty())
        assertNotNull(OccurrenceEngine.nextOccurrence(off, Instant.parse("2026-06-21T00:00:00Z")))
    }
}
