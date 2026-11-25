package no.stormberry.twinlit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.MoonLight
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.ui.theme.MoonTokens
import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.SunLight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.ui.theme.SunTokens
import no.stormberry.twinlit.ui.theme.TwilightDark
import no.stormberry.twinlit.ui.theme.TwilightDusk
import no.stormberry.twinlit.ui.theme.TwilightLight
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * One picker, two palettes (round R4, CHANGES-R4.md section 4): [PickerColours] gives every role of
 * the shared picker a token of the palette it is drawn in, and every text role reads at 4.5 to 1
 * or more (WCAG AA, Marcos's rule) on the three grounds a picker puts text on. Pure arithmetic on
 * the palette values; the source checks pin that the picker reads only [pickerColours].
 */
class PickerColoursTest {

    private val sunPalettes: Map<String, SunTokens> = mapOf(
        "SunNight" to SunNight, "SunDusk" to SunDusk, "SunLight" to SunLight,
        "TwilightDark" to TwilightDark, "TwilightDusk" to TwilightDusk, "TwilightLight" to TwilightLight,
    )
    private val moonPalettes: Map<String, MoonTokens> = mapOf(
        "MoonNight" to MoonNight, "MoonDusk" to MoonDusk, "MoonLight" to MoonLight,
    )

    private fun lum(r: Double, g: Double, b: Double): Double {
        fun lin(v: Double): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private fun ch(c: Color): DoubleArray {
        val a = c.toArgb()
        return doubleArrayOf(((a shr 16) and 0xFF).toDouble(), ((a shr 8) and 0xFF).toDouble(), (a and 0xFF).toDouble())
    }

    private fun alphaOf(c: Color): Double = ((c.toArgb() ushr 24) and 0xFF) / 255.0

    /** [top] over [ground], exact float arithmetic, opaque result. */
    private fun over(top: Color, ground: DoubleArray): DoubleArray {
        val a = alphaOf(top)
        val t = ch(top)
        return DoubleArray(3) { t[it] * a + ground[it] * (1 - a) }
    }

    private fun ratio(fg: Color, bg: DoubleArray): Double {
        val f = over(fg, bg)
        val l1 = lum(f[0], f[1], f[2])
        val l2 = lum(bg[0], bg[1], bg[2])
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun check(name: String, c: PickerColours, background: Color, card: Color) {
        val bg = ch(background)
        val grounds = mapOf(
            "field" to over(c.field, bg),
            "selectedFill over Background" to over(c.selectedFill, bg),
            "Card over Background" to over(card, bg),
        )
        val text = mapOf("label" to c.label, "text" to c.text, "textSecondary" to c.textSecondary, "accent" to c.accent, "placeName" to c.placeName, "error" to c.error)
        for ((ground, rgb) in grounds) for ((role, colour) in text) {
            val r = ratio(colour, rgb)
            assertTrue("$name: $role on $ground is %.2f".format(r), r >= 4.5)
        }
        val button = ratio(c.onAccent, ch(c.accent))
        assertTrue("$name: onAccent on accent is %.2f".format(button), button >= 4.5)
    }

    @Test
    fun `every text role of the picker reads at 4 point 5 to 1 in every palette`() {
        for ((name, t) in sunPalettes) check(name, PickerColours.of(t), t.Background, t.Card)
        for ((name, t) in moonPalettes) check(name, PickerColours.of(t), t.Background, t.Card)
    }

    @Test
    fun `the Sun tokens give the picker what it drew before round R4`() {
        for ((name, t) in sunPalettes) {
            val c = PickerColours.of(t)
            assertEquals(name, t.TextMuted, c.label)
            assertEquals(name, t.TextPrimary, c.text)
            assertEquals(name, t.Surface, c.field)
            assertEquals(name, t.CardHover, c.selectedFill)
            assertEquals(name, t.Gold, c.accent)
            assertEquals("the button text is the Background, as SunApp has it (Critic R4, item 3)", t.Background, c.onAccent)
            assertEquals(name, t.Rose, c.error)
            if (t.isLight) assertEquals(t.TextMuted.copy(alpha = 0.7f), c.chipOutline) else assertNull(c.chipOutline)
            assertEquals(name, t.TextMuted.copy(alpha = if (t.isLight) 0.7f else 0.8f), c.modeOutline)
        }
    }

    @Test
    fun `the Moon tokens make the picker silver on dark text, never an indigo container`() {
        for ((name, t) in moonPalettes) {
            val c = PickerColours.of(t)
            assertEquals(name, t.Silver, c.accent)
            assertEquals(name, t.OnAccent, c.onAccent)
            assertEquals(name, t.TextHint, c.label)
            assertEquals(name, t.Error, c.error)
            assertEquals(name, t.TextHint.copy(alpha = if (t.isLight) 0.7f else 0.8f), c.modeOutline)
            assertTrue(name, c.accent != t.Indigo)
        }
    }

    @Test
    fun `the picker reads only the picker colours, never a palette of its own`() {
        fun code(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)^\s*//.*$"""), "")
        for (path in listOf("ui/PlacePicker.kt", "ui/CityPicker.kt")) {
            val text = code(path)
            assertFalse("$path reads the Sun facade", Regex("""\bSun\.""").containsMatchIn(text))
            assertFalse("$path reads the Moon facade", Regex("""\bMoon\.""").containsMatchIn(text))
            assertFalse("$path reads LocalSunTokens", text.contains("LocalSunTokens"))
            assertTrue("$path reads pickerColours()", text.contains("pickerColours()"))
        }
        val chrome = code("ui/components/Chrome.kt")
        for (piece in listOf("fun SectionLabel(", "private fun ModeChip(", "internal fun controlBorder(", "internal fun modeChipBorder(")) {
            val body = chrome.substringAfter(piece).substringBefore("\n}\n")
            assertTrue("$piece reads pickerColours()", body.contains("pickerColours()"))
            assertFalse("$piece reads the Sun facade", Regex("""\bSun\.""").containsMatchIn(body))
        }
        // Outside a picker the fallback is the Sun-style tokens in effect, so the Alarms tab is unchanged.
        val colours = code("ui/PickerColours.kt")
        assertTrue(colours.contains("LocalPickerColours.current ?: PickerColours.of(LocalSunTokens.current)"))
    }
}
