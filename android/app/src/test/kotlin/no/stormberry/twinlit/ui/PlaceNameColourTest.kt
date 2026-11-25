package no.stormberry.twinlit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import no.stormberry.twinlit.ui.theme.Glow
import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.MoonLight
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.ui.theme.MoonTokens
import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.SunLight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.ui.theme.SunTokens
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * R7 (D89, which replaces R6's D82 colour): on the Moon tab the selected place name is in lunar cyan, the
 * Moon palette's `Teal`, so it is clearly coloured as the Sun tab's gold is (silver read almost like
 * the white main text). The Sun tab and the alarm editor keep their gold.
 *
 * Teal is #4FC3F7 in the Night and Dusk palettes, which passes WCAG AA text contrast (4.5 to 1) over the
 * real layering: the selected row's fill over the card over the background, with the three sky glows
 * stacked at their peak alpha (the conservative bound), and flat. In the Light palette #4FC3F7 would be
 * 1.66 to 1 on the white card, so the palette's own darker cyan token #006A93 (the same hue, 5.00 to 1)
 * is used; no new token was needed. The tabs stay apart: CIEDE2000 between the Sun tab's gold and the
 * Moon tab's cyan in the same theme is 56.6 (Night, Dusk) and 46.4 (Light), against 45.4 and 41.7 for the
 * silver it replaces.
 */
class PlaceNameColourTest {

    private class Row(val name: String, val t: MoonTokens, val sun: SunTokens, val flat: Double, val glow: Double, val de: Double)

    private val rows = listOf(
        Row("MoonNight", MoonNight, SunNight, 9.12, 8.99, 56.63),
        Row("MoonDusk", MoonDusk, SunDusk, 4.95, 4.85, 56.63),
        Row("MoonLight", MoonLight, SunLight, 5.00, 4.98, 46.41),
    )

    private fun ch(c: Color): DoubleArray {
        val a = c.toArgb()
        return doubleArrayOf(((a shr 16) and 0xFF).toDouble(), ((a shr 8) and 0xFF).toDouble(), (a and 0xFF).toDouble())
    }

    private fun over(top: Color, ground: DoubleArray): DoubleArray {
        val a = ((top.toArgb() ushr 24) and 0xFF) / 255.0
        val t = ch(top)
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

    private fun ground(t: MoonTokens, glow: Boolean): DoubleArray {
        var g = ch(t.Background)
        if (glow) for (gl: Glow in listOf(t.GlowTop, t.GlowLeft, t.GlowBottom)) {
            val c = ch(gl.colour)
            g = DoubleArray(3) { c[it] * gl.alpha + g[it] * (1 - gl.alpha) }
        }
        return g
    }

    // ---- sRGB (D65) to CIELAB and the CIEDE2000 formula, kL = kC = kH = 1 (as TwilightDistinctTest)

    private fun lab(c: DoubleArray): Triple<Double, Double, Double> {
        fun lin(v: Double): Double { val s = v / 255.0; return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        val r = lin(c[0]); val g = lin(c[1]); val b = lin(c[2])
        val x = (4124564 * r + 3575761 * g + 1804375 * b) / 1e7 / (95047 / 1e5)
        val y = (2126729 * r + 7151522 * g + 721750 * b) / 1e7
        val z = (193339 * r + 1191920 * g + 9503041 * b) / 1e7 / (108883 / 1e5)
        fun f(t: Double) = if (t > 216.0 / 24389.0) Math.cbrt(t) else (24389.0 / 27.0 * t + 16) / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    private fun de2000(c1: DoubleArray, c2: DoubleArray): Double {
        val (l1, a1, b1) = lab(c1)
        val (l2, a2, b2) = lab(c2)
        val c1s = hypot(a1, b1); val c2s = hypot(a2, b2)
        val cb = (c1s + c2s) / 2
        val g = 0.5 * (1 - sqrt(cb.pow(7) / (cb.pow(7) + 25.0.pow(7))))
        val a1p = (1 + g) * a1; val a2p = (1 + g) * a2
        val c1p = hypot(a1p, b1); val c2p = hypot(a2p, b2)
        val h1p = (deg(atan2(b1, a1p)) + 360) % 360
        val h2p = (deg(atan2(b2, a2p)) + 360) % 360
        val dLp = l2 - l1
        val dCp = c2p - c1p
        var dhp = 0.0
        if (c1p * c2p != 0.0) {
            dhp = h2p - h1p
            if (dhp > 180) dhp -= 360 else if (dhp < -180) dhp += 360
        }
        val dHp = 2 * sqrt(c1p * c2p) * sin(rad(dhp / 2))
        val lbp = (l1 + l2) / 2
        val cbp = (c1p + c2p) / 2
        val hbp = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
        val t = 1 - 0.17 * cos(rad(hbp - 30)) + 0.24 * cos(rad(2 * hbp)) + 0.32 * cos(rad(3 * hbp + 6)) - 0.20 * cos(rad(4 * hbp - 63))
        val dth = 30 * exp(-((hbp - 275) / 25).pow(2))
        val rc = 2 * sqrt(cbp.pow(7) / (cbp.pow(7) + 25.0.pow(7)))
        val sl = 1 + 0.015 * (lbp - 50).pow(2) / sqrt(20 + (lbp - 50).pow(2))
        val sc = 1 + 0.045 * cbp
        val sh = 1 + 0.015 * cbp * t
        val rt = -sin(rad(2 * dth)) * rc
        return sqrt((dLp / sl).pow(2) + (dCp / sc).pow(2) + (dHp / sh).pow(2) + rt * (dCp / sc) * (dHp / sh))
    }

    @Test
    fun `the Moon tab's selected place name is lunar cyan in every theme, and the Sun tab's stays gold`() {
        for (r in rows) {
            assertEquals(r.name, r.t.Teal, PickerColours.of(r.t).placeName)
            assertEquals(r.name, r.sun.Gold, PickerColours.of(r.sun).placeName)
        }
        assertEquals(Color(0xFF4FC3F7), MoonNight.Teal)
        assertEquals(Color(0xFF4FC3F7), MoonDusk.Teal)
        assertEquals("the light palette's own darker cyan token, same hue", Color(0xFF006A93), MoonLight.Teal)
        // The accent (chips, button, cursor) is untouched: still silver.
        for (r in rows) assertEquals(r.name, r.t.Silver, PickerColours.of(r.t).accent)
    }

    @Test
    fun `the selected place name keeps 4 point 5 to 1 over the selected fill, the card and the stacked glows`() {
        for (r in rows) for (glow in listOf(false, true)) {
            val card = over(r.t.Card, ground(r.t, glow))
            val selected = over(PickerColours.of(r.t).selectedFill, card)
            val got = ratio(ch(PickerColours.of(r.t).placeName), selected)
            val tag = "${r.name}${if (glow) " (glow)" else ""}"
            assertTrue("$tag %.2f".format(got), got >= 4.5)
            assertEquals(tag, if (glow) r.glow else r.flat, got, 0.006)
        }
    }

    @Test
    fun `where the light cyan fails AA the palette's darker cyan token is used, the same hue`() {
        // #4FC3F7 over the Light palette's selected row is 1.66 to 1; the token is 5.00.
        val light = rows.first { it.name == "MoonLight" }
        val card = over(light.t.Card, ground(light.t, false))
        val selected = over(light.t.CardHover, card)
        assertEquals(1.66, ratio(ch(Color(0xFF4FC3F7)), selected), 0.006)
        assertTrue(ratio(ch(Color(0xFF4FC3F7)), selected) < 4.5)
        // Hue: the HSV hue of the two cyans is the same to within a few degrees (199 and 197).
        fun hue(c: Color): Double {
            val v = ch(c).map { it / 255.0 }
            val mx = v.max(); val mn = v.min(); val d = mx - mn
            val h = when (mx) { v[0] -> ((v[1] - v[2]) / d) % 6; v[1] -> (v[2] - v[0]) / d + 2; else -> (v[0] - v[1]) / d + 4 }
            return (h * 60 + 360) % 360
        }
        assertEquals(hue(Color(0xFF4FC3F7)), hue(MoonLight.Teal), 6.0)
    }

    @Test
    fun `the Moon tab's name colour stays far from the Sun tab's gold in the same theme (CIEDE2000)`() {
        for (r in rows) {
            val got = de2000(ch(r.sun.Gold), ch(r.t.Teal))
            assertEquals(r.name, r.de, got, 0.05)
            assertTrue("${r.name} %.1f".format(got), got >= 40.0)
            // Stronger than the silver it replaces.
            assertTrue(r.name, got > de2000(ch(r.sun.Gold), ch(r.t.Silver)))
        }
    }

    @Test
    fun `the selected row of the picker draws the name in the picker's place name colour, and the Moon tab hands in the Moon colours`() {
        val picker = TestFiles.require("${TestFiles.KOTLIN}/ui/CityPicker.kt").readText()
        val row = picker.substringAfter("private fun SelectedRow(").substringBefore("\n}\n")
        assertTrue(row.contains("text = label"))
        assertTrue(row.contains("color = c.placeName"))
        val moon = TestFiles.require("${TestFiles.KOTLIN}/ui/moon/MoonTimesScreen.kt").readText()
        assertTrue(moon.contains("colours = PickerColours.of(LocalMoonTokens.current)"))
    }
}
