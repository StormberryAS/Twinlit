package no.stormberry.twinlit.ui.sun

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.solar.DayKind
import no.stormberry.twinlit.solar.computeSunTimes
import no.stormberry.twinlit.solar.resolveZone
import no.stormberry.twinlit.widget.TestFiles
import no.stormberry.twinlit.ui.displayCoordinates
import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.ui.parseCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The pure half of the Sun tab (ported from SunApp's `SunTimesScreenTest`).
 *
 * The expected clock readings are not invented here and are not Kotlin output. Each one was
 * produced by running the bundled `suncalc.js` (`tools/golden/suncalc.js`, SunCalc 1.8.0) under
 * Node with the web page's own anchoring rule (`Date.UTC(y, m - 1, d, 12)`) and
 * `Intl.DateTimeFormat('en-GB', { hour12: false, timeZone })`, which is what sun.stormberry.as
 * prints. So these assertions are cross-surface parity checks, to the second, not a snapshot of
 * whatever this code happened to do first. The places are Oslo (59.9139 N, 10.7522 E), Tokyo and
 * Longyearbyen (SPEC 13).
 */
class SunTimesScreenTest {

    private val oslo = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", true)
    private val longyearbyen = Place("Longyearbyen, Norway", 78.2233, 15.6469, "Arctic/Longyearbyen", true)
    private val tokyo = Place("Tokyo, Japan", 35.6895, 139.6917, "Asia/Tokyo", true)

    @Test
    fun `Oslo at the June solstice matches suncalc to the second`() {
        val result = computeSunTimes(oslo, LocalDate.of(2026, 6, 21))

        assertEquals(DayKind.NORMAL, result.kind)
        assertEquals("03:54:57", formatClock(result.sunrise, result.zone))
        assertEquals("13:19:58", formatClock(result.solarNoon, result.zone))
        assertEquals("22:45:00", formatClock(result.sunset, result.zone))
        assertEquals("18h 50m", formatDayLength(result.kind, result.dayLengthMinutes))
    }

    @Test
    fun `Oslo at the December solstice matches suncalc to the second`() {
        val result = computeSunTimes(oslo, LocalDate.of(2026, 12, 21))

        assertEquals(DayKind.NORMAL, result.kind)
        assertEquals("09:19:20", formatClock(result.sunrise, result.zone))
        assertEquals("12:16:13", formatClock(result.solarNoon, result.zone))
        assertEquals("15:13:06", formatClock(result.sunset, result.zone))
        assertEquals("5h 54m", formatDayLength(result.kind, result.dayLengthMinutes))
    }

    @Test
    fun `an ordinary summer day in Oslo matches suncalc to the second`() {
        val result = computeSunTimes(oslo, LocalDate.of(2026, 8, 21))

        assertEquals("05:46:50", formatClock(result.sunrise, result.zone))
        assertEquals("13:21:41", formatClock(result.solarNoon, result.zone))
        assertEquals("20:56:32", formatClock(result.sunset, result.zone))
        assertEquals("15h 10m", formatDayLength(result.kind, result.dayLengthMinutes))
    }

    @Test
    fun `times render in the place's zone rather than the machine's`() {
        val result = computeSunTimes(tokyo, LocalDate.of(2026, 3, 1))

        assertEquals("06:13:35", formatClock(result.sunrise, result.zone))
        // The same instant in Oslo is a different clock reading, which is the whole
        // reason a place carries a zone. If this ever passes by accident it is
        // because the two zones agreed, and Tokyo and Oslo never do.
        assertNotEquals(
            formatClock(result.sunrise, result.zone),
            formatClock(result.sunrise, ZoneId.of("Europe/Oslo")),
        )
    }

    @Test
    fun `midnight sun has no sunrise, a solar noon, and a full bar`() {
        val result = computeSunTimes(longyearbyen, LocalDate.of(2026, 6, 21))

        assertEquals(DayKind.MIDNIGHT_SUN, result.kind)
        assertNull(result.sunrise)
        assertNull(result.sunset)
        // Solar noon is a transit, not a horizon crossing, so it exists here.
        assertEquals("13:00:23", formatClock(result.solarNoon, result.zone))
        assertEquals("24h 0m", formatDayLength(result.kind, result.dayLengthMinutes))
        assertEquals(1f, dayLengthFraction(result.kind, result.dayLengthMinutes), 0f)
    }

    @Test
    fun `polar night has no sunrise, a solar noon, and an empty bar`() {
        val result = computeSunTimes(longyearbyen, LocalDate.of(2026, 12, 21))

        assertEquals(DayKind.POLAR_NIGHT, result.kind)
        assertNull(result.sunrise)
        assertNull(result.sunset)
        assertEquals("11:56:38", formatClock(result.solarNoon, result.zone))
        assertEquals("0h 0m", formatDayLength(result.kind, result.dayLengthMinutes))
        assertEquals(0f, dayLengthFraction(result.kind, result.dayLengthMinutes), 0f)
    }

    @Test
    fun `the polar notices carry the shipped words, one for each polar kind`() {
        // TEXTS.md D-22 to D-25 verbatim (SHIPPED, SunApp's own, no name in them).
        assertEquals("Midnight sun", MIDNIGHT_SUN_TITLE)
        assertEquals(
            "The sun does not set on this date at this place, so there is no sunrise and no sunset " +
                "to give. Solar noon still applies: it is when the sun is at its highest.",
            MIDNIGHT_SUN_BODY,
        )
        assertEquals("Polar night", POLAR_NIGHT_TITLE)
        assertEquals(
            "The sun does not rise on this date at this place, so there is no sunrise and no sunset " +
                "to give. Solar noon still applies: it is when the sun comes closest to the horizon.",
            POLAR_NIGHT_BODY,
        )
        // The Longyearbyen December day shows the polar night notice, the June day the midnight sun
        // notice, and Oslo on the same two dates neither (SunResults switches on this kind).
        assertEquals(DayKind.POLAR_NIGHT, computeSunTimes(longyearbyen, LocalDate.of(2026, 12, 21)).kind)
        assertEquals(DayKind.MIDNIGHT_SUN, computeSunTimes(longyearbyen, LocalDate.of(2026, 6, 21)).kind)
        assertEquals(DayKind.NORMAL, computeSunTimes(oslo, LocalDate.of(2026, 12, 21)).kind)
        assertEquals(DayKind.NORMAL, computeSunTimes(oslo, LocalDate.of(2026, 6, 21)).kind)
    }

    @Test
    fun `day length fraction is the share of a full twenty-four hours`() {
        assertEquals(0.5f, dayLengthFraction(DayKind.NORMAL, 720L), 0.0001f)
        assertEquals(0.25f, dayLengthFraction(DayKind.NORMAL, 360L), 0.0001f)
    }

    @Test
    fun `a missing instant renders as a dash rather than an epoch`() {
        // The failure this guards against is the one the solar layer's KDoc warns
        // about: a null event turned into 0L and printed as 1970-01-01.
        // An en dash (U+2013), as on the home screen widgets, never an em dash.
        assertEquals("–", formatClock(null, ZoneId.of("Europe/Oslo")))
    }

    @Test
    fun `coordinates accept the separators real keyboards produce`() {
        assertEquals(59.9139, parseCoordinate("59.9139")!!, 1e-9)
        // Norwegian, German and Brazilian keyboards all offer a decimal comma.
        assertEquals(59.9139, parseCoordinate("59,9139")!!, 1e-9)
        // U+2212, what typeset text and some keyboards use instead of a hyphen.
        assertEquals(-10.7522, parseCoordinate("−10,7522")!!, 1e-9)
        assertEquals(10.7522, parseCoordinate(" 10.7522° ")!!, 1e-9)
        assertNull(parseCoordinate(""))
        assertNull(parseCoordinate("north"))
        // "NaN" and "Infinity" parse happily through toDouble and would poison
        // every comparison downstream, so they are refused here.
        assertNull(parseCoordinate("NaN"))
        assertNull(parseCoordinate("Infinity"))
    }

    @Test
    fun `coordinates print with hemispheres rather than signs`() {
        assertEquals("59.9139 N, 10.7522 E", formatCoordinates(59.9139, 10.7522))
        assertEquals("33.8688 S, 151.2093 E", formatCoordinates(-33.8688, 151.2093))
        assertEquals("37.7749 N, 122.4194 W", formatCoordinates(37.7749, -122.4194))
    }

    @Test
    fun `an unusable zone id falls back to UTC instead of throwing`() {
        assertEquals(ZoneId.of("Europe/Oslo"), resolveZone("Europe/Oslo"))
        // A tzdb link retired between releases must not take the screen with it.
        assertEquals(ZoneId.of("UTC").normalized(), resolveZone("Mars/Olympus").normalized())
    }

    @Test
    fun `the timezone row names the zone and its abbreviation for that date`() {
        val summer = formatZone(ZoneId.of("Europe/Oslo"), LocalDate.of(2026, 8, 21))
        val winter = formatZone(ZoneId.of("Europe/Oslo"), LocalDate.of(2026, 1, 21))

        assertTrue(summer, summer.endsWith("Europe/Oslo"))
        assertTrue(winter, winter.endsWith("Europe/Oslo"))
        // Resolved at noon on the date shown, not now, so a summer date read in
        // January still says summer time.
        assertNotEquals(summer, winter)
    }

    @Test
    fun `the full date reads as British prose in the date bar's form, with no comma (D21)`() {
        // The year is tied to its month by a no-break space, as in the date bar (R6, D21).
        assertEquals("Friday 21 August\u00A02026", formatFullDate(LocalDate.of(2026, 8, 21)))
        assertEquals(
            no.stormberry.twinlit.ui.components.formatBarDate(LocalDate.of(2026, 8, 21)),
            formatFullDate(LocalDate.of(2026, 8, 21)).replace('\u00A0', ' '),
        )
        var day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2027) {
            assertFalse(day.toString(), formatFullDate(day).contains(','))
            day = day.plusDays(1)
        }
    }

    @Test
    fun `day length rounds to the nearest minute, as the web app does`() {
        // 21 August in Oslo: 54581.613 s of daylight is 909.69 minutes, so the card reads 15h 10m
        // (910 minutes). Truncating would give 15h 9m, which is what this pins against.
        val result = computeSunTimes(oslo, LocalDate.of(2026, 8, 21))
        val seconds = Duration.between(result.sunrise!!, result.sunset!!).seconds
        assertEquals(54581L, seconds)
        assertEquals(910L, result.dayLengthMinutes)
        assertEquals(909L, seconds / 60)
    }

    @Test
    fun `an instant with no zone attached is not silently the device's`() {
        val noon = Instant.parse("2026-08-21T12:00:00Z")
        assertEquals("14:00:00", formatClock(noon, ZoneId.of("Europe/Oslo")))
        assertEquals("21:00:00", formatClock(noon, ZoneId.of("Asia/Tokyo")))
    }

    // ---- R7 (D86, D87): the Coordinates and Timezone rows of the results card

    @Test
    fun `the displayed coordinates tie each number to its letter and may break only after the comma (D86)`() {
        assertEquals("59.9139\u00A0N, 10.7522\u00A0E", displayCoordinates(59.9139, 10.7522))
        assertEquals("33.8688\u00A0S, 151.2093\u00A0E", displayCoordinates(-33.8688, 151.2093))
        assertEquals("37.7749\u00A0N, 122.4194\u00A0W", displayCoordinates(37.7749, -122.4194))
        // The stored label and every comparison keep the plain space.
        assertEquals("59.9139 N, 10.7522 E", formatCoordinates(59.9139, 10.7522))
        for (point in listOf(doubleArrayOf(59.9139, 10.7522), doubleArrayOf(-33.8688, 151.2093), doubleArrayOf(37.7749, -122.4194), doubleArrayOf(0.0, 0.0))) {
            val shown = displayCoordinates(point[0], point[1])
            assertEquals(formatCoordinates(point[0], point[1]), shown.replace('\u00A0', ' '))
            assertEquals(shown, 2, shown.count { it == '\u00A0' })
            // One plain space: the one after the comma, the only place a line may break.
            assertEquals(shown, 1, shown.count { it == ' ' })
            assertTrue(shown, shown.indexOf(' ') == shown.indexOf(',') + 1)
        }
        val source = TestFiles.require("${TestFiles.KOTLIN}/ui/sun/SunTimesScreen.kt").readText()
        assertTrue(source.contains("coordinates = displayCoordinates(result.latDeg, result.lonDeg)"))
    }

    @Test
    fun `the timezone row has two pieces, the abbreviation with its slash and the whole id (D87)`() {
        val oslo = zoneParts(ZoneId.of("Europe/Oslo"), LocalDate.of(2026, 1, 21))
        assertEquals(ZoneParts("CET /", "Europe/Oslo"), oslo)
        assertEquals("CET / Europe/Oslo", oslo.text)
        assertEquals("CEST /", zoneParts(ZoneId.of("Europe/Oslo"), LocalDate.of(2026, 8, 21)).abbreviation)
        // No abbreviation to show: the id alone, and the single string agrees.
        val utc = zoneParts(ZoneId.of("UTC"), LocalDate.of(2026, 1, 21))
        assertEquals(ZoneParts(null, "UTC"), utc)
        for (id in listOf("Europe/Oslo", "Asia/Tokyo", "America/New_York", "Pacific/Auckland")) {
            val parts = zoneParts(ZoneId.of(id), LocalDate.of(2026, 3, 1))
            assertEquals(formatZone(ZoneId.of(id), LocalDate.of(2026, 3, 1)), parts.text)
            assertEquals(id, parts.id)
            assertFalse(parts.text, parts.id.contains('\u00A0') || parts.id.contains(' '))
        }
        // The row lays the two pieces out as a flow: the only break is between them.
        val results = TestFiles.require("${TestFiles.KOTLIN}/ui/sun/Results.kt").readText()
        val row = results.substringAfter("internal fun MetaCard").substringBefore("/** The label's share of a meta row")
        assertTrue(row.contains("FlowRow("))
        assertTrue(row.contains("Text(text = timezone.id,"))
        assertTrue(row.contains("timezone.abbreviation?.let"))
    }

    @Test
    fun `at 360 dp and font scale 1 point 3 the coordinates break only after the comma and Europe slash Oslo stays whole (D86, D87)`() {
        // bodyMedium is 14 sp, set in the platform monospace face, whose advance is 0.6 em; the sp is dp times the font scale.
        fun width(text: String, scale: Double) = text.length * 14.0 * 0.6 * scale
        // The row sits in the 18 dp gutter and the card's 18 dp padding; the value column is 0.58 of the rest.
        fun column(screenDp: Double) = (screenDp - 72.0) * 0.58
        val first = displayCoordinates(59.9139, 10.7522).substringBefore(' ')
        val oslo = zoneParts(ZoneId.of("Europe/Oslo"), LocalDate.of(2026, 1, 21))
        val ids = TestFiles.requireAtRoot("data/cities.tsv").readLines()[1].split('\t').filter { it.contains('/') }
        for (screen in listOf(411.0, 360.0)) for (scale in listOf(1.0, 1.3)) {
            // Every half of the coordinates ("59.9139 N,") and the id fit one line.
            assertTrue("$screen dp, scale $scale: '$first' %.1f dp of %.1f".format(width(first, scale), column(screen)), width(first, scale) <= column(screen))
            assertTrue("$screen dp, scale $scale: Europe/Oslo %.1f dp of %.1f".format(width(oslo.id, scale), column(screen)), width(oslo.id, scale) <= column(screen))
            assertTrue(width("${oslo.abbreviation} ${oslo.id}", scale) > 0)
            val wider = ids.count { width(it, scale) > column(screen) }
            println("R7 zone row: $screen dp, scale $scale: column %.1f dp, Europe/Oslo %.1f dp, whole pair %.1f dp, coordinates half %.1f dp, %d of %d catalogue zone ids wider than the column (they wrap at their own slash)".format(column(screen), width(oslo.id, scale), width("CET / Europe/Oslo", scale), width(first, scale), wider, ids.size))
        }
        assertTrue(ids.size > 300)
    }
}
