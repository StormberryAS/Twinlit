package no.stormberry.twinlit.ui.sun

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R7 (D88): the clock in a sun-time tile never breaks. Each reading is one line (softWrap false,
 * maxLines 1) and steps down in size until it fits the tile, so "08:39:06" is never cut into
 * "08:39:" and "06" at 360 dp and font scale 1.3, and stays whole and readable at 2.0.
 *
 * The width budget is pure: the tile's text is 8 characters of titleSmall (14 sp, 0.1 sp tracking)
 * in the platform monospace face (advance 0.6 em); the sp is dp times the font scale. The tile is a
 * third of the card row, less two 10 dp gaps, less its 8 dp padding on each side; the card sits in
 * the 18 dp gutter and has 18 dp padding.
 */
class SunTimeTileTest {

    private fun code() = TestFiles.require("${TestFiles.KOTLIN}/ui/sun/Results.kt").readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    private fun tile(screenDp: Double) = (screenDp - 72.0 - 20.0) / 3.0 - 16.0

    /** The largest size in sp at which [chars] monospace characters fit [widthDp] at [scale]. */
    private fun fits(widthDp: Double, scale: Double, chars: Int = 8) = (widthDp / (chars * scale) - 0.1) / 0.6

    @Test
    fun `a clock reading is one line that steps down in size, a polar word may wrap`() {
        val code = code()
        val tileCode = code.substringAfter("private fun SunTimeCard(").substringBefore("Day length, as a figure")
        val clock = tileCode.substringAfter("if (value.monospace) {").substringBefore("} else {")
        assertTrue(clock.contains("softWrap = false"))
        assertTrue(clock.contains("maxLines = 1"))
        assertTrue(clock.contains("TextAutoSize.StepBased("))
        assertTrue(clock.contains("minFontSize = CLOCK_MIN_SIZE"))
        assertTrue("never above the tile's normal size", clock.contains("maxFontSize = style.fontSize"))
        val word = tileCode.substringAfter("} else {")
        assertFalse("the polar words keep their wrap", word.contains("softWrap = false") || word.contains("maxLines"))
        assertTrue(CLOCK_MIN_SIZE.value == 5f)
    }

    @Test
    fun `the clock fits its tile at font scale 1, 1 point 3 and 2 on 411, 360 and 320 dp, above the floor and readable`() {
        for (screen in listOf(411.0, 360.0, 320.0)) for (scale in listOf(1.0, 1.3, 2.0)) {
            val size = minOf(14.0, fits(tile(screen), scale))
            val shownDp = size * scale
            println("R7 tile: $screen dp, scale $scale: tile text width %.1f dp, the clock fits at %.1f sp (%.1f dp on screen)".format(tile(screen), fits(tile(screen), scale), shownDp))
            assertTrue("$screen dp, scale $scale needs %.2f sp, the floor is 5 sp".format(fits(tile(screen), scale)), fits(tile(screen), scale) >= CLOCK_MIN_SIZE.value)
            // Readable: never smaller on screen than the unscaled 14 dp text of a normal phone, down to 12 dp on the smallest.
            assertTrue("$screen dp, scale $scale: %.1f dp".format(shownDp), shownDp >= if (screen >= 360.0) 14.0 else 12.0)
        }
        // The case of the report: 360 dp, scale 1.3, was 87 dp of text in a 73 dp tile.
        assertTrue(8 * (0.6 * 14.0 + 0.1) * 1.3 > tile(360.0))
        assertTrue("unscaled it already fits", 8 * (0.6 * 14.0 + 0.1) <= tile(360.0))
    }

    @Test
    fun `the Moon tab's times are rows, not tiles, and each time is one line`() {
        val moon = TestFiles.require("${TestFiles.KOTLIN}/ui/moon/MoonTimesScreen.kt").readText()
        val row = moon.substringAfter("private fun TimeRow").substringBefore("private fun PolarNotice")
        assertTrue(row.contains("softWrap = false, maxLines = 1"))
        // "HH:mm" in a row of the card's full width: 5 characters, 2.0 times 14 sp is 28 dp per em, narrower than any phone's column.
        assertTrue(5 * 0.6 * 14.0 * 2.0 + 120.0 < 320.0 - 72.0)
    }
}
