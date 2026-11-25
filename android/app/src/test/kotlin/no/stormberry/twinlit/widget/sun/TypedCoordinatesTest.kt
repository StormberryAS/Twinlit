package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.TestFiles
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A place typed as coordinates never reaches a widget as numbers (1.1.9, see
 * [WidgetText.YOUR_COORDINATES]). The app labels such a place with the coordinates themselves, so
 * the widgets name it "Your coordinates" in the large header and in every spoken description.
 */
class TypedCoordinatesTest {

    private val osloPhone = ZoneId.of("Europe/Oslo")

    /** Oslo's catalogue pair, typed: what the app stores after the user enters the coordinates. */
    private val typed = Place(
        label = formatCoordinates(OSLO.latDeg, OSLO.lonDeg),
        latDeg = OSLO.latDeg,
        lonDeg = OSLO.lonDeg,
        zoneId = OSLO.zoneId,
        fromCatalogue = false,
    )

    /** Every text the three widget sizes show or speak for [s]. */
    private fun allTexts(s: WidgetState): List<String> {
        val out = mutableListOf(s.placeLabel, WidgetText.headerDate(s))
        WidgetText.small(s).let { out += listOf(it.label, it.value) }
        when (val t = WidgetText.timesLine(s)) {
            is WidgetText.TimesLine.Normal -> out += listOf(t.sunrise, t.sunset)
            is WidgetText.TimesLine.Polar -> out += t.label
        }
        for (withTime in listOf(false, true)) {
            when (val l = WidgetText.line(s, withTime)) {
                WidgetText.LineText.Hidden -> Unit
                is WidgetText.LineText.Static -> out += l.text
                is WidgetText.LineText.Ticking -> out += l.format
            }
        }
        for (b in listOf(WidgetText.golden(s), WidgetText.twilight(s.civil, s), WidgetText.twilight(s.nautical, s), WidgetText.twilight(s.astronomical, s))) {
            when (b) {
                is WidgetText.BandText.Cells -> out += listOf(b.first, b.second)
                is WidgetText.BandText.Span -> out += b.text
            }
        }
        out += listOf(WidgetText.describeSmall(s), WidgetText.describeMedium(s), WidgetText.describeLarge(s))
        return out
    }

    /** A year of renders, four a day once a week, ticking and not. */
    private fun states(place: Place): List<WidgetState> {
        val out = mutableListOf<WidgetState>()
        var day = Instant.parse("2026-01-05T00:00:00Z")
        while (day.isBefore(Instant.parse("2027-01-05T00:00:00Z"))) {
            for (hour in listOf(3L, 9L, 14L, 21L)) {
                for (ticking in listOf(true, false)) {
                    out += WidgetModel.build(day.plus(Duration.ofHours(hour)), place, osloPhone, ticking)!!
                }
            }
            day = day.plus(Duration.ofDays(7))
        }
        return out
    }

    @Test
    fun `a typed Oslo pair - no digit of the coordinates in any widget text or description`() {
        val label = typed.label
        // The fraction digits of each typed number, which no time or date can produce together.
        val fractions = Regex("""\d+[.,](\d+)""").findAll(label).map { it.groupValues[1] }.toList()
        assertEquals(2, fractions.size)
        val decimal = Regex("""\d[.,]\d""")
        for (s in states(typed)) {
            assertEquals(WidgetText.YOUR_COORDINATES, s.placeLabel)
            for (text in allTexts(s)) {
                assertFalse(text, text.contains(label))
                assertFalse(text, decimal.containsMatchIn(text))
                for (f in fractions) assertFalse("$f in $text", text.contains(f))
            }
            for (d in listOf(WidgetText.describeSmall(s), WidgetText.describeMedium(s), WidgetText.describeLarge(s))) {
                assertTrue(d, d.startsWith(WidgetText.YOUR_COORDINATES + ":"))
            }
        }
    }

    @Test
    fun `a catalogue place shows its city only, and the neutral name holds no digit`() {
        assertEquals("Oslo", WidgetText.placeName(OSLO))
        assertEquals("Your coordinates", WidgetText.placeName(typed))
        assertFalse(Regex("""\d""").containsMatchIn(WidgetText.YOUR_COORDINATES))
        val s = WidgetModel.build(Instant.parse("2026-11-16T12:00:00Z"), OSLO, osloPhone, true)!!
        assertTrue(WidgetText.describeLarge(s).startsWith("Oslo: "))
    }

    @Test
    fun `a label equal to the place's own coordinates is never shown, even when marked as catalogue`() {
        // A place stored by mistake with fromCatalogue = true (or read from a missing key).
        val mislabelled = typed.copy(fromCatalogue = true)
        assertEquals(WidgetText.YOUR_COORDINATES, WidgetText.placeName(mislabelled))
        assertEquals(WidgetText.YOUR_COORDINATES, WidgetText.placeName(mislabelled.copy(label = " ${typed.label} ")))
        val s = WidgetModel.build(Instant.parse("2026-11-16T12:00:00Z"), mislabelled, osloPhone, true)!!
        assertEquals(WidgetText.YOUR_COORDINATES, s.placeLabel)
        for (text in allTexts(s)) assertFalse(text, text.contains(typed.label))
        // Another place's coordinates are a label like any other: only the place's own are refused.
        val other = formatCoordinates(-OSLO.latDeg, -OSLO.lonDeg)
        assertEquals(other, WidgetText.placeName(mislabelled.copy(label = other)))
    }

    @Test
    fun `no widget source reads a place's label except through placeName`() {
        val dir = TestFiles.require("${TestFiles.KOTLIN}/widget")
        for (f in dir.listFiles { x -> x.extension == "kt" }!!) {
            // WidgetText.placeName delegates to PlaceDisplay.name (2.0.0), the one reader of the label.
            val uses = Regex("""\bplace\.label\b""").findAll(f.readText()).count()
            assertEquals(f.name, 0, uses)
        }
        val one = TestFiles.require("${TestFiles.KOTLIN}/data/PlaceDisplay.kt").readText()
        assertTrue(one.contains("place.label"))
    }
}
