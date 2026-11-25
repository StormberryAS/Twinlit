package no.stormberry.twinlit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import no.stormberry.twinlit.ui.theme.Glow
import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.MoonLight
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.SunLight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.ui.theme.TwilightDark
import no.stormberry.twinlit.ui.theme.TwilightDusk
import no.stormberry.twinlit.ui.theme.TwilightLight
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * R5 (D13, D14): the selected place mode chip has a 2 dp ring in the picker's accent and a semibold
 * label, the unselected one a 1 dp outline in its label colour, in every palette (CHANGES-R5 section
 * 7.3). The cue is 3 to 1 or more against the card, flat and over the three sky glows stacked at their
 * peak alpha (the conservative bound); the fills alone differ by 1.06 to 1.47 to 1.
 *
 * R6: the outline is blended over the chip's own fill, `colours.field`, because that is the layering the
 * screen draws (the stroke lies on the chip's container, which is opaque), and it is measured against
 * what lies on either side of it: the card outside and the chip's fill inside. R5 blended it over the
 * card, which hid that the 70 per cent outline was 2.97 to 1 over the glow in SunNight and MoonNight.
 * The dark palettes now use 80 per cent (3.55 to 1 or more); the light palettes keep 70 per cent
 * (3.05 to 1 or more).
 */
class ModeChipCueTest {

    private class Row(
        val name: String,
        val colours: PickerColours,
        val background: Color,
        val card: Color,
        val glows: List<Glow>,
        val ringCard: Double, val ringCardGlow: Double,
        val ringFill: Double, val ringFillGlow: Double,
        /** The outline against the card outside it, flat and over the glows; then against the chip's fill inside it. */
        val outlineOut: Double, val outlineOutGlow: Double,
        val outlineIn: Double, val outlineInGlow: Double,
    )

    private val rows = listOf(
        row("SunLight", SunLight.let { PickerColours.of(it) }, SunLight.Background, SunLight.Card, listOf(SunLight.GlowWarm, SunLight.GlowGold, SunLight.GlowCool), 6.20, 6.01, 5.47, 5.45, 3.15, 3.05, 3.17, 3.17),
        row("SunDusk", PickerColours.of(SunDusk), SunDusk.Background, SunDusk.Card, listOf(SunDusk.GlowWarm, SunDusk.GlowGold, SunDusk.GlowCool), 9.70, 7.06, 7.81, 7.43, 5.24, 3.81, 5.83, 5.83),
        row("SunNight", PickerColours.of(SunNight), SunNight.Background, SunNight.Card, listOf(SunNight.GlowWarm, SunNight.GlowGold, SunNight.GlowCool), 13.10, 11.09, 11.40, 11.10, 4.19, 3.55, 4.12, 4.12),
        row("TwilightLight", PickerColours.of(TwilightLight), TwilightLight.Background, TwilightLight.Card, listOf(TwilightLight.GlowWarm, TwilightLight.GlowGold, TwilightLight.GlowCool), 6.31, 6.14, 5.30, 5.28, 3.24, 3.16, 3.28, 3.28),
        row("TwilightDusk", PickerColours.of(TwilightDusk), TwilightDusk.Background, TwilightDusk.Card, listOf(TwilightDusk.GlowWarm, TwilightDusk.GlowGold, TwilightDusk.GlowCool), 6.97, 5.62, 5.43, 5.26, 4.95, 3.99, 5.67, 5.67),
        row("TwilightDark", PickerColours.of(TwilightDark), TwilightDark.Background, TwilightDark.Card, listOf(TwilightDark.GlowWarm, TwilightDark.GlowGold, TwilightDark.GlowCool), 9.87, 8.46, 8.49, 8.28, 4.29, 3.68, 4.40, 4.40),
        row("MoonLight", PickerColours.of(MoonLight), MoonLight.Background, MoonLight.Card, listOf(MoonLight.GlowTop, MoonLight.GlowLeft, MoonLight.GlowBottom), 8.33, 8.08, 7.00, 6.97, 3.21, 3.11, 3.25, 3.25),
        row("MoonDusk", PickerColours.of(MoonDusk), MoonDusk.Background, MoonDusk.Card, listOf(MoonDusk.GlowTop, MoonDusk.GlowLeft, MoonDusk.GlowBottom), 8.65, 7.35, 6.86, 6.73, 4.92, 4.18, 5.50, 5.50),
        row("MoonNight", PickerColours.of(MoonNight), MoonNight.Background, MoonNight.Card, listOf(MoonNight.GlowTop, MoonNight.GlowLeft, MoonNight.GlowBottom), 13.48, 12.14, 12.65, 12.47, 3.95, 3.56, 3.92, 3.92),
    )

    private fun row(
        name: String, c: PickerColours, bg: Color, card: Color, glows: List<Glow>,
        a: Double, b: Double, d: Double, e: Double, f: Double, g: Double, h: Double, i: Double,
    ) = Row(name, c, bg, card, glows, a, b, d, e, f, g, h, i)

    private fun ch(c: Color): DoubleArray {
        val a = c.toArgb()
        return doubleArrayOf(((a shr 16) and 0xFF).toDouble(), ((a shr 8) and 0xFF).toDouble(), (a and 0xFF).toDouble())
    }

    private fun alphaOf(c: Color): Double = ((c.toArgb() ushr 24) and 0xFF) / 255.0

    private fun over(top: Color, ground: DoubleArray): DoubleArray {
        val a = alphaOf(top); val t = ch(top)
        return DoubleArray(3) { t[it] * a + ground[it] * (1 - a) }
    }

    private fun lum(c: DoubleArray): Double {
        fun lin(v: Double): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2])
    }

    private fun ratio(a: DoubleArray, b: DoubleArray): Double {
        val l1 = lum(a); val l2 = lum(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** The ground under the card: the background, with the three glows stacked at their peak alpha when [glow]. */
    private fun ground(r: Row, glow: Boolean): DoubleArray {
        var g = ch(r.background)
        if (glow) for (gl in r.glows) {
            val c = ch(gl.colour)
            g = DoubleArray(3) { c[it] * gl.alpha + g[it] * (1 - gl.alpha) }
        }
        return g
    }

    @Test
    fun `the ring and the outline keep 3 to 1 on the card, flat and over the stacked glows, in all nine palettes`() {
        for (r in rows) for (glow in listOf(false, true)) {
            val card = over(r.card, ground(r, glow))
            val selected = over(r.colours.selectedFill, card)
            val tag = "${r.name}${if (glow) " (glow)" else ""}"
            val ringCard = ratio(ch(r.colours.accent), card)
            val ringFill = ratio(ch(r.colours.accent), selected)
            // The real layering: the chip's fill over the card, the outline over the chip's fill.
            val field = over(r.colours.field, card)
            val stroke = over(r.colours.modeOutline, field)
            val outlineOut = ratio(stroke, card)
            val outlineIn = ratio(stroke, field)
            assertTrue("$tag ring on the card %.2f".format(ringCard), ringCard >= 3.0)
            assertTrue("$tag ring on the selected fill %.2f".format(ringFill), ringFill >= 3.0)
            assertTrue("$tag outline on the card %.2f".format(outlineOut), outlineOut >= 3.0)
            assertTrue("$tag outline on the chip's fill %.2f".format(outlineIn), outlineIn >= 3.0)
            assertEquals("$tag ring:card", if (glow) r.ringCardGlow else r.ringCard, ringCard, 0.006)
            assertEquals("$tag ring:fill", if (glow) r.ringFillGlow else r.ringFill, ringFill, 0.006)
            assertEquals("$tag outline:card", if (glow) r.outlineOutGlow else r.outlineOut, outlineOut, 0.006)
            assertEquals("$tag outline:fill", if (glow) r.outlineInGlow else r.outlineIn, outlineIn, 0.006)
        }
    }

    @Test
    fun `the old 70 per cent outline fails 3 to 1 over the glow in SunNight and MoonNight under the real layering`() {
        for (r in rows.filter { it.name == "SunNight" || it.name == "MoonNight" }) {
            val card = over(r.card, ground(r, true))
            val field = over(r.colours.field, card)
            val old = over(r.colours.modeOutline.copy(alpha = 0.7f), field)
            val got = ratio(old, card)
            assertTrue("${r.name} at 70 per cent: %.2f".format(got), got < 3.0)
            // The R5 test blended over the card and read 3.12 and 3.17 here: it could not see this.
            val blindSpot = ratio(over(r.colours.modeOutline.copy(alpha = 0.7f), card), card)
            assertTrue("${r.name} over the card: %.2f".format(blindSpot), blindSpot >= 3.0)
        }
    }

    @Test
    fun `the dark palettes outline at 80 per cent and the light palettes at 70 per cent`() {
        for (r in rows) {
            val dark = r.name.endsWith("Night") || r.name.endsWith("Dusk") || r.name == "TwilightDark"
            val expected = if (dark) 0.8 else 0.7
            assertEquals(r.name, expected, alphaOf(r.colours.modeOutline), 0.003)
        }
    }

    @Test
    fun `the chip draws a ring when selected, an outline when not, and a semibold selected label`() {
        val chrome = TestFiles.require("${TestFiles.KOTLIN}/ui/components/Chrome.kt").readText()
        val chip = chrome.substringAfter("private fun ModeChip(").substringBefore("\n}\n")
        assertTrue(chip.contains("border = modeChipBorder(selected)"))
        assertTrue(chip.contains("fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal"))
        assertTrue(chrome.contains("BorderStroke(2.dp, c.accent)"))
        assertTrue(chrome.contains("BorderStroke(1.dp, c.modeOutline)"))
        // The alarm editor's own chips keep their light-palette outline.
        val control = chrome.substringAfter("internal fun controlBorder(").substringBefore("\n}\n")
        assertTrue(control.contains("pickerColours().chipOutline"))
    }
}
