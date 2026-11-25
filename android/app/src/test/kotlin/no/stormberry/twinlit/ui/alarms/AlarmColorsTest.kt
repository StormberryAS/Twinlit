package no.stormberry.twinlit.ui.alarms

import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.SunLight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.ui.theme.SunTokens
import no.stormberry.twinlit.ui.theme.TwilightDark
import no.stormberry.twinlit.ui.theme.TwilightDusk
import no.stormberry.twinlit.ui.theme.TwilightLight
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The text colours of the Alarms tab and the rule editor (SunApp 2.0.0, here for every palette they can be drawn in; they read the plain value objects, never the themed facade): every colour the screens draw
 * text in is at least 4.5 to 1 (WCAG AA, normal text) against every surface they draw it on. The
 * place line of an alarm, which now holds the user's own name for the place, was the faintest
 * text on the card at about 2.5 to 1.
 */
class AlarmColorsTest {

    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { x -> luminance(b).let { y -> if (x >= y) x to y else y to x } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun over(top: Color, bottom: Color): Color {
        val a = top.alpha
        return Color(top.red * a + bottom.red * (1 - a), top.green * a + bottom.green * (1 - a), top.blue * a + bottom.blue * (1 - a))
    }

    /** The palettes the alarm screens can be drawn in: the Sun palette in its three variants and the Twilight palette in its three. */
    private val palettes = mapOf(
        "SunNight" to SunNight,
        "SunDusk" to SunDusk,
        "SunLight" to SunLight,
        "TwilightDark" to TwilightDark,
        "TwilightDusk" to TwilightDusk,
        "TwilightLight" to TwilightLight,
    )

    /** The surfaces text sits on: the page, a field or sheet, a card, and a selected chip or the editor's raised card. */
    private fun surfaces(t: SunTokens) = mapOf(
        "Background" to t.Background,
        "Surface" to t.Surface,
        "Card over Background" to over(t.Card, t.Background),
        "CardHover over Background" to over(t.CardHover, t.Background),
        "Card over Surface" to over(t.Card, t.Surface),
    )

    /** Every colour the alarm screens, the editor, the permission sheet and the shared chrome draw text in. */
    private fun texts(t: SunTokens) = mapOf(
        "TextPrimary" to t.TextPrimary,
        "TextSecondary" to t.TextSecondary,
        "TextMuted" to t.TextMuted,
        "Gold" to t.Gold,
        "Orange" to t.Orange,
        "Rose" to t.Rose,
        "Blue" to t.Blue,
        "Noon" to t.Noon,
    )

    @Test
    fun `every text colour is at least 4_5 to 1 on every surface of the alarm screens, in every palette`() {
        val low = mutableListOf<String>()
        for ((pn, p) in palettes) for ((tn, t) in texts(p)) for ((sn, s) in surfaces(p)) {
            val r = ratio(t, s)
            if (r < 4.5) low += "$pn $tn on $sn: ${"%.2f".format(r)}"
        }
        assertTrue(low.toString(), low.isEmpty())
    }

    @Test
    fun `the button text of the editor and the list passes on its accent, in every palette`() {
        for ((pn, p) in palettes) {
            assertTrue("$pn OnAccent on Gold", ratio(p.OnAccent, p.Gold) >= 4.5)
        }
    }

    @Test
    fun `the place line of an alarm is no longer the faintest text under the rule`() {
        // Measured 2.54 to 1 before (#4A5580 on the card).
        for ((pn, p) in palettes) {
            val card = over(p.Card, p.Background)
            assertTrue(pn, ratio(p.TextMuted, card) >= 4.5)
            // Light palettes darken their muted text, so it can be the stronger of the two there; the
            // dark palettes keep SunApp's order.
            if (!p.isLight) assertTrue(pn, ratio(p.TextMuted, card) < ratio(p.TextSecondary, card))
        }
    }

    @Test
    fun `the muted colour keeps its hue family`() {
        fun hue(c: Color): Double {
            val mx = maxOf(c.red, c.green, c.blue)
            val mn = minOf(c.red, c.green, c.blue)
            val d = (mx - mn).toDouble()
            return when (mx) {
                c.red -> 60 * (((c.green - c.blue) / d) % 6)
                c.green -> 60 * (((c.blue - c.red) / d) + 2)
                else -> 60 * (((c.red - c.green) / d) + 4)
            }.let { if (it < 0) it + 360 else it }
        }
        assertEquals(227.0, hue(SunNight.TextMuted), 3.0)
        assertEquals(hue(Color(0xFF4A5580)), hue(SunNight.TextMuted), 3.0)
    }
}
