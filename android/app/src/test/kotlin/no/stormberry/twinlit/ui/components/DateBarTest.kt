package no.stormberry.twinlit.ui.components

import no.stormberry.twinlit.ui.sun.formatFullDate
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The shared date bar of the Sun and Moon tabs (round R4, CHANGES-R4.md section 3): its format,
 * its look as source properties (no Compose UI test runs in this project) and a pure width budget
 * measured with the advance widths of the bundled Inter, so a 360 dp phone at normal text size
 * shows every date from 2026 to 2100 on one line.
 */
class DateBarTest {

    private fun source() = TestFiles.require("${TestFiles.KOTLIN}/ui/components/DateBar.kt").readText()

    /** The file without its comments, so a doc comment may say "circle" or "background". */
    private fun code() = source()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    @Test
    fun `the date reads weekday day month year, without a comma, in British English whatever the phone's language`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale("nb", "NO"))
            assertEquals("Wednesday 25 November 2026", formatBarDate(LocalDate.of(2026, 11, 25)))
            assertEquals("Wednesday 30 September 2026", formatBarDate(LocalDate.of(2026, 9, 30)))
            assertEquals("Friday 1 January 2027", formatBarDate(LocalDate.of(2027, 1, 1)))
            assertEquals("Friday 20 November 2026", formatBarDate(LocalDate.of(2026, 11, 20)))
            // One form on the Moon tab (D12): the phase card's own date.
            var day = LocalDate.of(2026, 1, 1)
            while (day.year == 2026) {
                assertEquals(day.toString(), no.stormberry.twinlit.ui.moon.formatMoonDate(day).replace('\u00A0', ' '), formatBarDate(day))
                day = day.plusDays(1)
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `the arrows are a discreet glyph in an invisible 48 dp target, after the date so they win the touch`() {
        val code = code()
        assertFalse("no circle anywhere", code.contains("CircleShape"))
        val arrow = code.substringAfter("private fun DateArrow").substringBefore("@OptIn")
        assertFalse("no fill on an arrow", arrow.contains(".background("))
        assertFalse("no outline on an arrow", arrow.contains(".border("))
        assertTrue(arrow.contains(".size(48.dp)"))
        assertTrue(arrow.contains("clearAndSetSemantics"))
        assertTrue(arrow.contains("contentDescription = label"))
        assertTrue(arrow.contains("role = Role.Button"))
        assertTrue("the glyph is labelLarge, the size of MoonApp's text-button arrow", arrow.contains("MaterialTheme.typography.labelLarge"))
        // Declared after the centre column, so they are drawn on top and take the touch.
        val centre = code.indexOf("horizontal = DATE_SIDE_PADDING")
        assertTrue(centre > 0)
        assertTrue(code.indexOf("DateArrow(\"\u2039\"") > centre)
        assertTrue(code.indexOf("DateArrow(\"\u203A\"") > centre)
    }

    @Test
    fun `the date is centred, may wrap and never clips, and the box grows with it`() {
        val code = code()
        assertTrue(code.contains("textAlign = TextAlign.Center"))
        assertTrue("the date takes the form that fits (R8, D90)", code.contains("text = dateText") && code.contains("fitDateText(date, linePx)"))
        assertTrue(code.contains(".border(1.dp, colours.border, shape)"))
        assertFalse("no maxLines", code.contains("maxLines"))
        assertFalse("no ellipsis", code.contains("TextOverflow"))
        assertTrue(code.contains(".heightIn(min = 56.dp)"))
        assertFalse("no fixed height", Regex("""\.height\(\d+\.dp\)""").containsMatchIn(code))
        assertTrue(code.contains("padding(horizontal = DATE_SIDE_PADDING, vertical = 6.dp)"))
        assertTrue(code.contains("DATE_SIDE_PADDING = 44.dp"))
        assertTrue(code.contains("RoundedCornerShape(16.dp)"))
        // The second line is always there, so the bar keeps its height.
        assertTrue(code.contains("if (isToday) {"))
        assertTrue(code.contains("Text(text = TODAY"))
        assertTrue(code.contains("Text(\n                    text = BACK_TO_TODAY"))
        // Tapping the date opens the picker; "Back to today" returns.
        assertTrue(code.contains("onClickLabel = CHOOSE_A_DATE"))
        assertTrue(code.contains("onToday()"))
        assertTrue(code.contains("ZoneOffset.UTC"))
    }

    @Test
    fun `the words are D-6 to D-9 and E-3 verbatim`() {
        assertEquals("Previous day", PREVIOUS_DAY)
        assertEquals("Next day", NEXT_DAY)
        assertEquals("Choose a date", CHOOSE_A_DATE)
        assertEquals("Today", TODAY)
        assertEquals("Back to today", BACK_TO_TODAY)
        val code = code()
        assertTrue(code.contains("Text(\"Select\""))
        assertTrue(code.contains("Text(\"Cancel\""))
    }

    @Test
    fun `the Sun and Moon bars differ in palette only`() {
        val code = code()
        val sun = code.substringAfter("fun sunDateBarColours()").substringBefore("fun moonDateBarColours()")
        val moon = code.substringAfter("fun moonDateBarColours()").substringBefore("fun DateBar(")
        assertTrue(sun.contains("fill = Sun.Card") && sun.contains("border = Sun.Border") && sun.contains("link = Sun.Gold"))
        assertTrue(moon.contains("fill = Moon.Card") && moon.contains("border = Moon.Border") && moon.contains("arrow = Moon.Silver"))
        assertFalse(sun.contains("Moon."))
        assertFalse(moon.contains("Sun."))
    }

    @Test
    fun `the widest date of 2026 to 2100 fits one line on a 360 dp phone at normal text size`() {
        val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())
        // bodyMedium: 14 sp, 0.25 sp tracking per character. At font scale 1.0 sp equals dp.
        fun width(text: String) = text.sumOf { font.advance(it) * 14.0 / font.unitsPerEm + 0.25 }

        var widest = 0.0
        var widestText = ""
        var day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2100) {
            val text = formatBarDate(day)
            val w = width(text)
            if (w > widest) { widest = w; widestText = text }
            day = day.plusDays(1)
        }
        // The Sun tab's box is 360 - 2 * 18 = 324 dp; 44 dp padding each side leaves 236 dp; 4 dp to
        // spare on each side keeps the text clear of the 48 dp arrow targets.
        val budget = 324.0 - 88.0 - 8.0
        assertTrue("$widestText is $widest dp, budget $budget", widest <= budget)
        // The Moon tab's box is 324 dp as well since R5 (D13: one 18 dp margin on both tabs).
        assertTrue(widest <= 324.0 - 88.0 - 8.0)
        // Sanity: the measurement is real (a date is about 220 dp, not 0 or 2000). Widest: "Wednesday 24 September 2064", 222.6 dp.
        assertTrue("$widest", widest in 180.0..240.0)
    }

    @Test
    fun `the Date row of the Sun results card breaks after the day, never leaves the year alone, at font scale 1 and 1 point 3 on 411 and 360 dp (D21)`() {
        val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())
        // bodyMedium: 14 sp with 0.25 sp tracking per character; the sp is dp times the font scale.
        fun width(text: String, scale: Double) = text.sumOf { (font.advance(it) * 14.0 / font.unitsPerEm + 0.25) * scale }
        // The row sits in the 18 dp gutter and the card's 18 dp padding; the value column is 0.58 of the rest.
        fun column(screenDp: Double) = (screenDp - 72.0) * 0.58

        val source = TestFiles.require("${TestFiles.KOTLIN}/ui/sun/SunTimesScreen.kt").readText()
        assertTrue("the card takes the date and sets it in the form that fits (R8, D90)", source.contains("date = result.date,"))
        assertTrue(source.contains("internal fun formatFullDate(date: LocalDate): String = displayBarDate(date)"))

        for (screen in listOf(411.0, 360.0)) for (scale in listOf(1.0, 1.3)) {
            var worst = ""
            var worstWidth = 0.0
            var day = LocalDate.of(2026, 1, 1)
            while (day.year <= 2100) {
                val shown = formatFullDate(day)
                val units = shown.split(' ')
                // Three break units at most: the weekday, the day, and "month year" tied by the no-break space.
                assertEquals(shown, 3, units.size)
                assertTrue(shown, Regex("""[A-Z][a-z]+\u00A0\d{4}""").matches(units[2]))
                for (unit in units) {
                    val w = width(unit, scale)
                    if (w > worstWidth) { worstWidth = w; worst = unit }
                }
                day = day.plusDays(1)
            }
            // The widest unbreakable piece (the month and the year) fits the value column, so the
            // year is never alone and no word is split.
            assertTrue("$screen dp, scale $scale: '$worst' is %.1f dp, column %.1f dp".format(worstWidth, column(screen)), worstWidth <= column(screen))
        }
    }

    @Test
    fun `the Moon card rows New moon night and Full moon night use the date bar's form and never leave the year alone, at font scale 1 and 1 point 3 on 411 and 360 dp (R7, D85)`() {
        val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())
        // bodyMedium: 14 sp with 0.25 sp tracking per character; the sp is dp times the font scale.
        fun width(text: String, scale: Double) = text.sumOf { (font.advance(it) * 14.0 / font.unitsPerEm + 0.25) * scale }
        // The card sits in the 18 dp gutter and has 18 dp padding; the label takes 0.45 of the row (less its 12 dp gap) and the value 0.55.
        fun row(screenDp: Double) = screenDp - 72.0
        fun value(screenDp: Double) = row(screenDp) * 0.55
        fun label(screenDp: Double) = row(screenDp) * 0.45 - 12.0

        val source = TestFiles.require("${TestFiles.KOTLIN}/ui/moon/MoonTimesScreen.kt").readText()
        assertTrue(source.contains("internal fun formatMoonDate(date: LocalDate): String = displayBarDate(date)"))
        val row = source.substringAfter("private fun StatRow").substringBefore("private fun TimeRow")
        assertTrue("label column", row.contains("Modifier.weight(STAT_LABEL_SHARE).padding(end = STAT_LABEL_GAP)"))
        assertTrue("value column", row.contains("modifier = Modifier.weight(1f - STAT_LABEL_SHARE)"))
        assertTrue(source.contains("STAT_LABEL_SHARE = 0.45f") && source.contains("STAT_LABEL_GAP = 12.dp"))
        assertTrue("both rows take the one form, fitted to the column (R8, D90)", source.contains("dateText(newNight)") && source.contains("dateText(fullNight)") && source.contains("fitDateText(it, valuePx)"))

        for (screen in listOf(411.0, 360.0)) for (scale in listOf(1.0, 1.3)) {
            var worst = ""
            var worstWidth = 0.0
            var day = LocalDate.of(2026, 1, 1)
            while (day.year <= 2100) {
                val shown = no.stormberry.twinlit.ui.moon.formatMoonDate(day)
                val units = shown.split(' ')
                assertEquals(shown, 3, units.size)
                assertTrue(shown, Regex("""[A-Z][a-z]+\u00A0\d{4}""").matches(units[2]))
                for (unit in units) {
                    val w = width(unit, scale)
                    if (w > worstWidth) { worstWidth = w; worst = unit }
                }
                day = day.plusDays(1)
            }
            assertTrue("$screen dp, scale $scale: '$worst' is %.1f dp, value column %.1f dp".format(worstWidth, value(screen)), worstWidth <= value(screen))
            // The two labels: at font scale 1.0 each is one line; at 1.3 a word never splits.
            for (text in listOf("New moon night", "Full moon night", "Illumination", "Distance")) {
                if (scale == 1.0) assertTrue("$text at $screen dp is %.1f dp, column %.1f dp".format(width(text, scale), label(screen)), width(text, scale) <= label(screen))
                for (word in text.split(' ')) assertTrue("$word at $screen dp, scale $scale", width(word, scale) <= label(screen))
            }
            println("R7 moon value column: $screen dp, scale $scale: widest piece '$worst' %.1f dp of %.1f dp".format(worstWidth, value(screen)))
        }
    }

    @Test
    fun `the bar ties the year to its month`() {
        var day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2100) {
            val shown = displayBarDate(day)
            assertEquals(shown, formatBarDate(day), shown.replace('\u00A0', ' '))
            assertEquals(shown, 1, shown.count { it == '\u00A0' })
            val i = shown.indexOf('\u00A0')
            assertTrue(shown, Regex("""\d{4}""").matches(shown.substring(i + 1)))
            day = day.plusDays(1)
        }
    }

    @Test
    fun `a wrapped date breaks after the day, up to font scale 2`() {
        val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())
        fun width(text: String) = text.sumOf { font.advance(it) * 14.0 / font.unitsPerEm + 0.25 }
        var widestFirst = 0.0
        var widestSecond = 0.0
        var day = LocalDate.of(2026, 1, 1)
        while (day.year <= 2100) {
            val shown = displayBarDate(day)
            // The plain spaces are the only places a line may break: after the weekday and after the day.
            val afterDay = shown.indexOf(' ', shown.indexOf(' ') + 1)
            widestFirst = maxOf(widestFirst, width(shown.substring(0, afterDay)))
            widestSecond = maxOf(widestSecond, width(shown.substring(afterDay + 1)))
            day = day.plusDays(1)
        }
        // 324 - 88 = 236 dp of text at font scale 1.0; at 2.0 the widths double.
        assertTrue("weekday and day: $widestFirst", widestFirst * 2.0 <= 236.0)
        assertTrue("month and year: $widestSecond", widestSecond * 2.0 <= 236.0)
        assertTrue("$widestFirst", widestFirst in 80.0..110.0)
        assertTrue("$widestSecond", widestSecond in 100.0..120.0)
    }
}
