package no.stormberry.twinlit.ui.components

import androidx.compose.ui.graphics.Color
import no.stormberry.twinlit.ui.sun.SunFieldValue
import no.stormberry.twinlit.ui.sun.TILE_CLOCK_FLOOR
import no.stormberry.twinlit.ui.sun.TILE_GAP
import no.stormberry.twinlit.ui.sun.TILE_PADDING
import no.stormberry.twinlit.ui.sun.sunTilesSideBySide
import no.stormberry.twinlit.ui.sun.tileTextWidthPx
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * R8 (D90 to D97): no word breaks, at 411 and 360 dp and font scale 1.0, 1.3 and 2.0. The decisions
 * of [WordFit] are pure functions of measured widths, so they are tested here with the advance widths
 * of the bundled Inter (the same measure as [DateBarTest]); `LayoutAuditTest` (androidTest) repeats the
 * proof on a device with the real text engine, for every screen the root reaches.
 *
 * A "word" is what a line cannot break: the no-break space joins, a slash and a number do not split.
 */
class WordFitTest {

    private val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())

    /** Inter, [sp] with [tracking] sp after each character, at [scale]; the result in dp (1 sp is `scale` dp). */
    private fun inter(text: String, sp: Double, tracking: Double, scale: Double) =
        text.sumOf { (font.advance(it) * sp / font.unitsPerEm + tracking) * scale }

    /** The platform monospace face: 0.6 em a character. */
    private fun mono(text: String, sp: Double, scale: Double) = text.length * sp * 0.6 * scale

    private fun widest(text: String, width: (String) -> Double) = wordsOf(text).maxOf(width)

    @Test
    fun `a word is cut at a plain space or a line feed only`() {
        assertEquals(listOf("Sunday", "22", "November 2026"), wordsOf("Sunday 22 November 2026"))
        assertEquals(listOf("CET", "/", "Europe/Oslo"), wordsOf("CET / Europe/Oslo"))
        assertEquals(listOf("59.9127 N,", "10.7461 E"), wordsOf("59.9127 N, 10.7461 E"))
        assertEquals(listOf("SUNRISE", "ANYWHERE"), wordsOf("SUNRISE\nANYWHERE"))
        assertTrue(wordsOf("").isEmpty())
    }

    @Test
    fun `a date keeps the year with its month while they fit and breaks between them when they do not (D90)`() {
        var day = LocalDate.of(2026, 1, 1)
        var joinedAt = 0
        var plainAt = 0
        while (day.year <= 2100) {
            val joined = displayBarDate(day)
            val plain = formatBarDate(day)
            val monthYear = joined.substringAfterLast(' ')
            // Wide: the joined form, exactly the date bar's.
            assertEquals(joined, fitDateText(day, 10_000f) { 1f * it.length })
            // The joined piece wider than the column but every plain word narrower: the plain form, so
            // the break falls between the month and the year, never inside the year.
            val column = (monthYear.length - 1).toFloat()
            val form = fitDateText(day, column) { it.length.toFloat() }
            assertEquals(day.toString(), plain, form)
            assertEquals(3, wordsOf(joined).size)
            assertEquals(4, wordsOf(plain).size)
            // The year is a word on its own only in the plain form, which is the last resort.
            assertTrue(wordsOf(plain).last().matches(Regex("""\d{4}""")))
            joinedAt++
            plainAt++
            day = day.plusDays(1)
        }
        assertTrue(joinedAt > 27_000 && plainAt == joinedAt)
    }

    /** The decision of the Sun card, from Inter and monospace widths in dp (R8, D91). */
    private fun sunCardStacked(screen: Double, scale: Double, coordinates: String, zone: String, date: String): Boolean {
        val row = (screen - 72.0) * 1.0
        // labelMedium at 11 sp with 0.5 sp tracking; bodyMedium at 14 sp, 0.25 sp tracking, monospace values.
        fun label(t: String) = widest(t) { inter(it.uppercase().let { _ -> it }, 11.0, 0.5, scale) }
        fun value(t: String) = widest(t) { inter(it, 14.0, 0.25, scale) }
        fun monoValue(t: String) = widest(t) { mono(it, 14.0, scale) }
        val pairs = listOf(
            label("Coordinates") to monoValue(coordinates),
            label("Date") to value(date),
            label("Timezone") to monoValue(zone),
        )
        return !pairs.all { (l, v) -> rowFitsBeside(l.toFloat(), v.toFloat(), row.toFloat(), 0.42f, 12f) }
    }

    @Test
    fun `the Sun card puts label over value exactly when a word does not fit its column, and then every word fits (D91)`() {
        val coordinates = "59.9127 N, 10.7461 E"
        val zone = "CET / Europe/Oslo"
        val date = "Wednesday 30 September 2026"
        // Side by side where it fits, as the apps have always been laid out.
        assertFalse(sunCardStacked(411.0, 1.0, coordinates, zone, date))
        assertFalse(sunCardStacked(360.0, 1.0, coordinates, zone, date))
        // The cases of the report: "Coordinates" in 121 dp at font scale 2.0 on 360 dp.
        assertTrue(sunCardStacked(360.0, 2.0, coordinates, zone, date))
        assertTrue(sunCardStacked(411.0, 2.0, coordinates, zone, date))
        // Stacked, a row has the whole width: every word of every label and value is narrower.
        for (screen in listOf(411.0, 360.0, 320.0)) {
            val row = screen - 72.0
            for (word in listOf("Coordinates", "Date", "Timezone")) assertTrue("$word", inter(word, 11.0, 0.5, 2.0) <= row)
            assertTrue(mono("59.9127 N,", 14.0, 2.0) <= row)
            assertTrue(mono("Europe/Oslo", 14.0, 2.0) <= row)
            assertTrue(inter("September 2044", 14.0, 0.25, 2.0) <= row + 40.0 || screen < 360.0)
        }
    }

    /** The decision of the Moon card: label 0.45 less 12 dp, value 0.55 (R8, D91). */
    private fun moonCardStacked(screen: Double, scale: Double): Boolean {
        val row = screen - 72.0
        fun w(t: String) = widest(t) { inter(it, 14.0, 0.25, scale) }
        val labels = listOf("Illumination", "Distance", "New moon night", "Full moon night")
        val values = listOf("52%", "384,400 km", "Wednesday 30 September 2026", "Wednesday 30 September 2026")
        return !labels.indices.all { rowFitsBeside(w(labels[it]).toFloat(), w(values[it]).toFloat(), row.toFloat(), 0.45f, 12f) }
    }

    @Test
    fun `the Moon card stacks at font scale 2 and every date then fits one column whole (D90, D91)`() {
        assertFalse(moonCardStacked(411.0, 1.0))
        assertFalse(moonCardStacked(360.0, 1.0))
        assertFalse("labels wrap between words only at 1.3", moonCardStacked(411.0, 1.3))
        assertTrue(moonCardStacked(360.0, 2.0))
        assertTrue(moonCardStacked(411.0, 2.0))
        // Stacked at 2.0 the value column is the whole row: the widest "month year" of 2026 to 2100 fits,
        // joined, on 360 dp, and the weekday and the day fit as words.
        val row = 360.0 - 72.0
        var day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2100) {
            val shown = fitDateText(day, row.toFloat()) { inter(it, 14.0, 0.25, 2.0).toFloat() }
            assertEquals(day.toString(), displayBarDate(day), shown)
            day = day.plusDays(1)
        }
        // A column narrower than "September 2044" (a phone narrower than 360 dp, or a larger scale than 1.3)
        // gets the plain form for the months that do not fit it, and each of its words fits.
        val column = 140f
        var plainSeen = false
        var joinedSeen = false
        day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2100) {
            val shown = fitDateText(day, column) { inter(it, 14.0, 0.25, 1.3).toFloat() }
            for (word in wordsOf(shown)) assertTrue("$word of $shown", inter(word, 14.0, 0.25, 1.3) <= column)
            if (shown == formatBarDate(day)) plainSeen = true else joinedSeen = true
            day = day.plusDays(1)
        }
        assertTrue("the long months are released", plainSeen)
        assertTrue("the short months stay joined", joinedSeen)
    }

    @Test
    fun `the date bar line fits the widest month and year at font scale 2 on 360 dp, so its form stays joined`() {
        // The bar's line is 324 - 88 = 236 dp on the 360 dp phone.
        var day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2100) {
            val shown = fitDateText(day, 236f) { inter(it, 14.0, 0.25, 2.0).toFloat() }
            assertEquals(day.toString(), displayBarDate(day), shown)
            day = day.plusDays(1)
        }
    }

    @Test
    fun `three Sun tiles stand side by side only while every label word fits, and the clock at 10 sp (D92)`() {
        // The app's own decision (sunTilesSideBySide, tileTextWidthPx, TILE_CLOCK_FLOOR), with Inter and
        // monospace widths in dp at the font scale. No rule is copied here: a change to the decision changes
        // these answers.
        val floorSp = TILE_CLOCK_FLOOR.value.toDouble()
        fun sideBySide(screen: Double, scale: Double, clock: String = "08:39:06", sunrise: String = "Sunrise", polar: String? = null): Boolean {
            val tile = tileTextWidthPx(screen.toFloat() - 72f, TILE_GAP.value, TILE_PADDING.value)
            val tiles = listOf(
                SunFieldValue(polar ?: clock, Color.Unspecified, polar == null) to sunrise,
                SunFieldValue(clock, Color.Unspecified, true) to "Solar noon",
                SunFieldValue(polar ?: clock, Color.Unspecified, polar == null) to "Sunset",
            )
            return sunTilesSideBySide(
                tileTextPx = tile,
                tiles = tiles,
                labelWordPx = { widest(it) { w -> inter(w, 11.0, 0.5, scale) }.toFloat() },
                valuePx = { v ->
                    if (v.monospace) (v.text.length * (floorSp * 0.6 + 0.1) * scale).toFloat()
                    else widest(v.text) { w -> inter(w, 14.0, 0.25, scale) }.toFloat()
                },
            )
        }
        assertEquals(73.33f, tileTextWidthPx(360f - 72f, TILE_GAP.value, TILE_PADDING.value), 0.01f)
        assertEquals(90.33f, tileTextWidthPx(411f - 72f, TILE_GAP.value, TILE_PADDING.value), 0.01f)
        assertEquals(10f, TILE_CLOCK_FLOOR.value, 0f)
        assertTrue(sideBySide(411.0, 1.0))
        assertTrue(sideBySide(360.0, 1.0))
        assertTrue(sideBySide(411.0, 1.3))
        assertTrue(sideBySide(360.0, 1.3))
        // 360 dp at 2.0: "Sunrise" is wider than the tile. 411 dp at 2.0: the clock would be below 10 sp.
        assertFalse(sideBySide(360.0, 2.0))
        assertFalse(sideBySide(411.0, 2.0))
        // A label word alone decides it: a clock that fits at 360 dp 1.3 does not save a label that does not.
        assertFalse("a label wider than the tile", sideBySide(360.0, 1.3, sunrise = "Sunrisexxxxxxxxxxxxxxx"))
        // A clock alone decides it: a long reading at the floor size does not fit 360 dp.
        assertFalse("a clock wider than the tile", sideBySide(360.0, 1.0, clock = "08:39:06:00:00"))
        // A polar word is held to the tile by its widest word.
        assertTrue(sideBySide(411.0, 1.0, polar = "Polar Night"))
        assertFalse(sideBySide(360.0, 2.0, polar = "Midnight Sun"))
        // Stacked, a tile has the whole row: the clock is at its normal size, never smaller than before.
        assertTrue(8 * (14.0 * 0.6 + 0.1) * 2.0 <= 360.0 - 72.0 - 16.0)
    }
}
