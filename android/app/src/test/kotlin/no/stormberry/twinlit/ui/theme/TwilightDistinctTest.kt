package no.stormberry.twinlit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import no.stormberry.twinlit.theme.Theme
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
 * Marcos, 2026-11-20: "moon and twilight are way too similar" and "I imagine the twilight theme
 * more like a golden hour colourwise". The Twilight theme must be clearly different from the Moon
 * theme on every tab (CIEDE2000 of at least 12 for the grounds, the cards and the bars, and for
 * the sky behind the hero), and it must stay dark and warm: "dark, with warm accents" (A-3).
 * The Night grounds are cool and dark; the Dusk grounds warm.
 */
class TwilightDistinctTest {

    private data class Rgb(val r: Double, val g: Double, val b: Double)

    private fun rgb(c: Color): Rgb {
        val a = c.toArgb()
        return Rgb(((a shr 16) and 0xFF).toDouble(), ((a shr 8) and 0xFF).toDouble(), (a and 0xFF).toDouble())
    }

    private fun alpha(c: Color) = ((c.toArgb() ushr 24) and 0xFF) / 255.0

    private fun over(fg: Rgb, a: Double, bg: Rgb) =
        Rgb(fg.r * a + bg.r * (1 - a), fg.g * a + bg.g * (1 - a), fg.b * a + bg.b * (1 - a))

    /** The exact float composite of a translucent token over a ground. */
    private fun over(fg: Color, bg: Rgb) = over(rgb(fg), alpha(fg), bg)

    // ---- sRGB (D65) to CIELAB and the CIEDE2000 formula, kL = kC = kH = 1

    private fun lab(c: Rgb): Triple<Double, Double, Double> {
        fun lin(v: Double): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(c.r); val g = lin(c.g); val b = lin(c.b)
        // The sRGB to XYZ matrix (D65) in units of 1e-7, and the white point in units of 1e-5.
        val x = (4124564 * r + 3575761 * g + 1804375 * b) / 1e7 / (95047 / 1e5)
        val y = (2126729 * r + 7151522 * g + 721750 * b) / 1e7
        val z = (193339 * r + 1191920 * g + 9503041 * b) / 1e7 / (108883 / 1e5)
        fun f(t: Double) = if (t > 216.0 / 24389.0) Math.cbrt(t) else (24389.0 / 27.0 * t + 16) / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    private fun de2000(c1: Rgb, c2: Rgb): Double {
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

    // ---- the pairs: the Twilight theme's set against the Moon theme's set of the same tab

    private class Set(
        val name: String, val background: Color, val surface: Color, val card: Color, val cardHover: Color,
        val glows: List<Glow>,
    )

    private fun of(name: String, t: SunTokens) = Set(name, t.Background, t.Surface, t.Card, t.CardHover, listOf(t.GlowWarm, t.GlowGold, t.GlowCool))
    private fun of(name: String, t: MoonTokens) = Set(name, t.Background, t.Surface, t.Card, t.CardHover, listOf(t.GlowTop, t.GlowLeft, t.GlowBottom))

    /** tab -> (Moon theme set, Twilight theme set) as the palettes mapping gives them. */
    private val pairs: Map<Tab, Pair<Set, Set>> = mapOf(
        Tab.SUN to (of("SunNight", Palettes.sun(Palettes.variantFor(Theme.MOON, Tab.SUN))) to of("SunDusk", Palettes.sun(Palettes.variantFor(Theme.TWILIGHT, Tab.SUN)))),
        Tab.MOON to (of("MoonNight", Palettes.moon(Palettes.variantFor(Theme.MOON, Tab.MOON))) to of("MoonDusk", Palettes.moon(Palettes.variantFor(Theme.TWILIGHT, Tab.MOON)))),
        Tab.ALARMS to (of("TwilightDark", Palettes.twilight(Palettes.variantFor(Theme.MOON, Tab.ALARMS))) to of("TwilightDusk", Palettes.twilight(Palettes.variantFor(Theme.TWILIGHT, Tab.ALARMS)))),
    )

    private class Grounds(val background: Rgb, val surface: Rgb, val card: Rgb, val cardHover: Rgb)

    private fun grounds(s: Set): Grounds {
        val bg = rgb(s.background)
        return Grounds(bg, rgb(s.surface), over(s.card, bg), over(s.cardHover, bg))
    }

    private fun one(x: Double) = Math.round(x * 10.0) / 10.0

    @Test
    fun `the palettes mapping gives each tab its Moon theme set and its Twilight theme set`() {
        assertEquals(SunNight, Palettes.sun(Palettes.variantFor(Theme.MOON, Tab.SUN)))
        assertEquals(MoonNight, Palettes.moon(Palettes.variantFor(Theme.MOON, Tab.MOON)))
        assertEquals(TwilightDark, Palettes.twilight(Palettes.variantFor(Theme.MOON, Tab.ALARMS)))
        assertEquals(SunDusk, Palettes.sun(Palettes.variantFor(Theme.TWILIGHT, Tab.SUN)))
        assertEquals(MoonDusk, Palettes.moon(Palettes.variantFor(Theme.TWILIGHT, Tab.MOON)))
        assertEquals(TwilightDusk, Palettes.twilight(Palettes.variantFor(Theme.TWILIGHT, Tab.ALARMS)))
    }

    @Test
    fun `Twilight is clearly different from the Moon theme on every tab, grounds and cards`() {
        for ((tab, pair) in pairs) {
            val (night, dusk) = pair.first to pair.second
            val a = grounds(night); val b = grounds(dusk)
            for ((what, x, y) in listOf(
                Triple("Background", a.background, b.background),
                Triple("Surface", a.surface, b.surface),
                Triple("Card over Background", a.card, b.card),
                Triple("CardHover over Background", a.cardHover, b.cardHover),
            )) {
                val d = de2000(x, y)
                assertTrue("$tab ${night.name} vs ${dusk.name} $what is $d, below 12", d >= 12.0)
            }
        }
    }

    @Test
    fun `the known values of CHANGES-R4 section 6 hold to one decimal`() {
        // In tenths of a unit of CIEDE2000.
        val want = mapOf(
            Tab.SUN to listOf(164, 226, 263, 309),
            Tab.MOON to listOf(137, 191, 200, 251),
            Tab.ALARMS to listOf(201, 268, 290, 344),
        )
        for ((tab, pair) in pairs) {
            val a = grounds(pair.first); val b = grounds(pair.second)
            val got = listOf(de2000(a.background, b.background), de2000(a.surface, b.surface), de2000(a.card, b.card), de2000(a.cardHover, b.cardHover)).map(::one)
            for (i in got.indices) assertEquals("$tab ${listOf("Background", "Surface", "Card", "CardHover")[i]}", want.getValue(tab)[i] / 10.0, got[i], 0.1)
        }
    }

    @Test
    fun `the bars in the Twilight theme differ from the bars in the Moon theme on every tab`() {
        val wantBackground = mapOf(Tab.SUN to 164, Tab.MOON to 137, Tab.ALARMS to 201)
        val wantSurface = mapOf(Tab.SUN to 226, Tab.MOON to 191, Tab.ALARMS to 268)
        for (tab in Tab.entries) {
            val moon = Palettes.chrome(Theme.MOON, tab)
            val twilight = Palettes.chrome(Theme.TWILIGHT, tab)
            val bg = de2000(rgb(moon.Background), rgb(twilight.Background))
            val surface = de2000(rgb(moon.Surface), rgb(twilight.Surface))
            assertTrue("$tab bar Background $bg", bg >= 12.0)
            assertTrue("$tab bar Surface $surface", surface >= 12.0)
            assertEquals("$tab bar Background", wantBackground.getValue(tab) / 10.0, one(bg), 0.1)
            assertEquals("$tab bar Surface", wantSurface.getValue(tab) / 10.0, one(surface), 0.1)
        }
    }

    @Test
    fun `golden hour is dark and warm, and Night is dark and cool`() {
        for (c in listOf(SunDusk.Background, MoonDusk.Background, TwilightDusk.Background)) {
            val (l, a, b) = lab(rgb(c))
            assertTrue("L* $l", l in 8.0..16.0)
            assertTrue("a* $a", a > 2.0)
            assertTrue("b* $b", b > 5.0)
        }
        for (c in listOf(SunNight.Background, MoonNight.Background, TwilightDark.Background)) {
            assertTrue("b* of $c", lab(rgb(c)).third < 0.0)
        }
        // The CIELAB values quoted in CHANGES-R4 section 6.
        val known = mapOf(
            SunDusk.Background to listOf(11.0, 6.0, 11.5), MoonDusk.Background to listOf(13.4, 6.0, 8.3),
            TwilightDusk.Background to listOf(12.8, 7.2, 14.2), SunNight.Background to listOf(3.4, 1.4, -7.2),
            MoonNight.Background to listOf(2.2, 0.5, -3.7), TwilightDark.Background to listOf(3.9, 5.6, -11.2),
        )
        for ((c, want) in known) {
            val (l, a, b) = lab(rgb(c))
            assertEquals("L* of $c", want[0], l, 0.1)
            assertEquals("a* of $c", want[1], a, 0.1)
            assertEquals("b* of $c", want[2], b, 0.1)
        }
    }

    @Test
    fun `the sky behind the hero differs as well, at four points of a 411 by 800 box and in both gradient models`() {
        val w = 411; val h = 800
        val points = listOf(
            "top centre" to (SkyGlowGeometry.top.cx * w.toDouble() to 0.0),
            "upper left" to (SkyGlowGeometry.left.cx * w.toDouble() to SkyGlowGeometry.left.cy * h.toDouble()),
            "lower right" to (SkyGlowGeometry.bottom.cx * w.toDouble() to SkyGlowGeometry.bottom.cy * h.toDouble()),
            "middle" to (w / 2.0 to h / 2.0),
        )
        fun sky(s: Set, x: Double, y: Double, premultiplied: Boolean): Rgb {
            var c = rgb(s.background)
            val m = minOf(w, h).toDouble()
            for ((wash, glow) in listOf(SkyGlowGeometry.top, SkyGlowGeometry.left, SkyGlowGeometry.bottom).zip(s.glows)) {
                val t = hypot(x - wash.cx * w, y - wash.cy * h) / (wash.radius * m)
                if (t >= 1.0) continue
                val col = rgb(glow.colour)
                val src = if (premultiplied) col else Rgb(col.r * (1 - t), col.g * (1 - t), col.b * (1 - t))
                c = over(src, glow.alpha * (1 - t), c)
            }
            return c
        }
        for ((tab, pair) in pairs) for ((where, xy) in points) {
            // The lower value of the two gradient models (one model draws both skies).
            val lowest = listOf(true, false).minOf { pm ->
                de2000(sky(pair.first, xy.first, xy.second, pm), sky(pair.second, xy.first, xy.second, pm))
            }
            assertTrue("$tab $where sky is $lowest, below 12", lowest >= 12.0)
        }
    }
}
