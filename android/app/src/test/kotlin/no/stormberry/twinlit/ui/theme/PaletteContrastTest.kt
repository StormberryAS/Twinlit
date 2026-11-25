package no.stormberry.twinlit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import no.stormberry.twinlit.theme.Theme
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The palettes (SPEC 4) against WCAG 2.x contrast (SPEC 14.3). The expected ratios are SPEC 4.3's
 * own tables, held in [PaletteExpected]; this test computes the ratios itself from the Kotlin
 * palettes with the WCAG relative-luminance formula (sRGB, the 0.03928 threshold) and holds every
 * one to the printed value to two decimals, so a palette change that fails AA, or merely drifts
 * from the specification, fails the build.
 *
 * It reads the plain value objects (`SunNight` and the others), never the `@Composable` facades
 * (SPEC 4.2): the facades cannot be read outside a composition, and the ring screen and the
 * widgets do not use them at all.
 *
 * Two ways of compositing a translucent colour over a ground are used, because the SPEC uses two:
 * the main table uses 8-bit steps (the `on Bg #0C1329` values printed beside each Card), the
 * "Further pairs" use the exact float arithmetic.
 */
class PaletteContrastTest {

    // ------------------------------------------------------------------ the WCAG arithmetic

    private fun luminance(r: Double, g: Double, b: Double): Double {
        // Takes channels already in 0..255, possibly fractional (the float composite).
        fun lin(v: Double): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    /** A colour as channels 0..255, possibly fractional after a float composite. Always opaque. */
    private data class Rgb(val r: Double, val g: Double, val b: Double)

    private fun rgb(c: Color): Rgb {
        val a = c.toArgb()
        return Rgb(((a shr 16) and 0xFF).toDouble(), ((a shr 8) and 0xFF).toDouble(), (a and 0xFF).toDouble())
    }

    private fun alpha(c: Color): Int = (c.toArgb() ushr 24) and 0xFF

    /** [fg] over [bg] in exact float arithmetic, at [a] (0..1). */
    private fun overF(fg: Rgb, a: Double, bg: Rgb) =
        Rgb(fg.r * a + bg.r * (1 - a), fg.g * a + bg.g * (1 - a), fg.b * a + bg.b * (1 - a))

    /** [fg] over [bg] rounded to 8-bit channels (round half to even, as the SPEC's script did). */
    private fun over8(fg: Color, bg: Rgb): Rgb {
        val a = alpha(fg) / 255.0
        val f = rgb(fg)
        fun mix(x: Double, y: Double) = Math.rint(x * a + y * (1 - a))
        return Rgb(mix(f.r, bg.r), mix(f.g, bg.g), mix(f.b, bg.b))
    }

    private fun ratio(a: Rgb, b: Rgb): Double {
        val la = luminance(a.r, a.g, a.b)
        val lb = luminance(b.r, b.g, b.b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun two(x: Double) = Math.round(x * 100.0) / 100.0

    // ------------------------------------------------------------------ the palettes by name

    private class P(
        val name: String,
        val background: Color, val surface: Color, val card: Color, val cardHover: Color,
        val isLight: Boolean,
        val onAccent: Color, val accent: Color,
        val textTokens: Map<String, Color>,
        val textSecondary: Color,
        /** The accents the Notice is drawn in (SunApp: gold, orange, rose, blue; MoonApp: its six). */
        val noticeAccents: List<Color>,
        val glows: List<Glow>,
    )

    private fun sun(name: String, t: SunTokens) = P(
        name, t.Background, t.Surface, t.Card, t.CardHover, t.isLight, t.OnAccent, t.Gold,
        mapOf(
            "TextPrimary" to t.TextPrimary, "TextSecondary" to t.TextSecondary, "TextMuted" to t.TextMuted,
            "Gold" to t.Gold, "Orange" to t.Orange, "Rose" to t.Rose, "Blue" to t.Blue, "Noon" to t.Noon,
        ),
        t.TextSecondary,
        listOf(t.Gold, t.Orange, t.Rose, t.Blue),
        listOf(t.GlowWarm, t.GlowGold, t.GlowCool),
    )

    private fun moon(name: String, t: MoonTokens) = P(
        name, t.Background, t.Surface, t.Card, t.CardHover, t.isLight, t.OnAccent, t.Silver,
        // TextMuted is "not for text" and Indigo "not a text container": neither is listed.
        mapOf(
            "TextPrimary" to t.TextPrimary, "TextSecondary" to t.TextSecondary, "TextHint" to t.TextHint,
            "TextSoft" to t.TextSoft, "TextMutedReadable" to t.TextMutedReadable, "Silver" to t.Silver,
            "Blue" to t.Blue, "Violet" to t.Violet, "Teal" to t.Teal, "NewMoon" to t.NewMoon, "Error" to t.Error,
        ),
        t.TextSecondary,
        listOf(t.Silver, t.Blue, t.Violet, t.Teal, t.Error, t.NewMoon),
        listOf(t.GlowTop, t.GlowLeft, t.GlowBottom),
    )

    private val all: List<P> = listOf(
        sun("SunNight", SunNight), sun("SunDusk", SunDusk), sun("SunLight", SunLight),
        moon("MoonNight", MoonNight), moon("MoonDusk", MoonDusk), moon("MoonLight", MoonLight),
        sun("TwilightDark", TwilightDark), sun("TwilightDusk", TwilightDusk), sun("TwilightLight", TwilightLight),
    )

    /** Background, Surface, Card over Background, CardHover over Background: the four grounds of the table. */
    private fun grounds(p: P): List<Rgb> {
        val bg = rgb(p.background)
        return listOf(bg, rgb(p.surface), over8(p.card, bg), over8(p.cardHover, bg))
    }

    // ------------------------------------------------------------------ the tables

    @Test
    fun `every ratio of SPEC 4_3 is reproduced to two decimals, and every text token passes AA`() {
        var checked = 0
        for (p in all) {
            val expected = PaletteExpected.ratios.getValue(p.name)
            assertEquals(p.name, expected.keys, p.textTokens.keys.filter { it in expected.keys }.toSet())
            val grounds = grounds(p)
            for ((token, want) in expected) {
                val colour = p.textTokens.getValue(token)
                val got = grounds.map { two(ratio(rgb(colour), it)) }
                assertEquals("${p.name} $token", want, got)
                for (g in got) assertTrue("${p.name} $token is $g, below 4.5", g >= 4.5)
                checked += 4
            }
        }
        // 81 text tokens in the nine tables, four grounds each.
        assertEquals(324, checked)
    }

    @Test
    fun `every text token the app uses is in the table, so none escapes the check`() {
        for (p in all) {
            val expected = PaletteExpected.ratios.getValue(p.name).keys
            assertEquals(p.name, p.textTokens.keys, expected)
        }
    }

    @Test
    fun `OnAccent on the accent is the printed ratio and passes`() {
        for (p in all) {
            val got = two(ratio(rgb(p.onAccent), rgb(p.accent)))
            assertEquals(p.name, PaletteExpected.onAccent.getValue(p.name), got, 0.0)
            assertTrue(p.name, got >= 4.5)
        }
    }

    @Test
    fun `every text token on Card over Surface passes, at the printed lowest value`() {
        for (p in all) {
            val surface = rgb(p.surface)
            val card = overF(rgb(p.card), alpha(p.card) / 255.0, surface)
            val lowest = p.textTokens.values.minOf { ratio(rgb(it), card) }
            assertEquals(p.name, PaletteExpected.cardOverSurface.getValue(p.name), two(lowest), 0.0)
            assertTrue("${p.name} $lowest", lowest >= 4.5)
        }
    }

    @Test
    fun `the Notice fills pass over the opaque Surface base, at the printed lowest value`() {
        for (p in all) {
            // R4: the Notice draws an opaque Surface base first, so the fill never depends on the sky.
            val surface = rgb(p.surface)
            var lowest = Double.MAX_VALUE
            for (accent in p.noticeAccents) {
                val fill = overF(rgb(accent), 0.10, surface)
                lowest = minOf(lowest, ratio(rgb(accent), fill), ratio(rgb(p.textSecondary), fill))
            }
            assertEquals(p.name, PaletteExpected.notice.getValue(p.name), two(lowest), 0.0)
            assertTrue("${p.name} $lowest", lowest >= 4.5)
        }
    }

    // ------------------------------------------------------------------ the composite sky grid (R4)

    /** The box sizes of the grid, in dp: portrait phones, a landscape phone and a small tablet. */
    private val boxes = listOf(360 to 560, 360 to 640, 411 to 640, 411 to 760, 800 to 280, 600 to 900)

    private fun washes(p: P) = listOf(
        SkyGlowGeometry.top to p.glows[0],
        SkyGlowGeometry.left to p.glows[1],
        SkyGlowGeometry.bottom to p.glows[2],
    )

    /**
     * The sky at (x, y) of a W x H box: the Background, then the three washes src-over in drawing
     * order. A wash at distance d from its centre with radius r has t = d / r and alpha
     * `glow.alpha * (1 - t)`. [premultiplied] interpolates the colour towards the same colour (the
     * brighter case); otherwise towards transparent black, which is what Android's gradient does
     * and the darker case that matters for the light palettes. Exact float arithmetic.
     */
    private fun skyAt(p: P, w: Int, h: Int, x: Double, y: Double, premultiplied: Boolean): Rgb {
        var c = rgb(p.background)
        val m = minOf(w, h).toDouble()
        for ((wash, glow) in washes(p)) {
            val r = wash.radius * m
            val d = Math.hypot(x - wash.cx * w, y - wash.cy * h)
            val t = d / r
            if (t >= 1.0) continue
            val a = glow.alpha * (1.0 - t)
            val col = rgb(glow.colour)
            val src = if (premultiplied) col else Rgb(col.r * (1 - t), col.g * (1 - t), col.b * (1 - t))
            c = overF(src, a, c)
        }
        return c
    }

    private class Worst(var ratio: Double = Double.MAX_VALUE, var where: String = "")

    /** The lowest ratio of [tokens] over every grid point and both models, optionally on [card] over the sky. */
    private fun gridMin(p: P, tokens: Map<String, Color>, card: Color?): Pair<Double, String> {
        val worst = Worst()
        for ((w, h) in boxes) for (i in 0..36) for (j in 0..36) for (premul in listOf(true, false)) {
            val x = w * i / 36.0
            val y = h * j / 36.0
            var ground = skyAt(p, w, h, x, y, premul)
            if (card != null) ground = overF(rgb(card), alpha(card) / 255.0, ground)
            for ((name, colour) in tokens) {
                val r = ratio(rgb(colour), ground)
                if (r < worst.ratio) { worst.ratio = r; worst.where = "$name ${if (premul) "premultiplied" else "unpremultiplied"} ${w}x$h at ${Math.round(x)},${Math.round(y)}" }
            }
        }
        return worst.ratio to worst.where
    }

    /** The tokens drawn straight on the sky: the hero, the results place name, the Labs heading, the Alarms header, the countdown. */
    private fun bareTokens(p: P): Map<String, Color> =
        if (p.name.startsWith("Twilight")) p.textTokens.filterKeys { it in setOf("TextPrimary", "TextSecondary", "TextMuted", "Gold") }
        else p.textTokens.filterKeys { it == "TextPrimary" || it == "TextSecondary" }

    @Test
    fun `text drawn straight on the summed sky glow passes AA at every grid point, at the printed lowest value`() {
        // R4: the old test checked one glow at a time, so where two washes overlap (upper left) a
        // bare-sky text still failed (the hero tagline and the Labs heading at 3.5 to 1 in SunNight).
        for (p in all) {
            val (lowest, where) = gridMin(p, bareTokens(p), card = null)
            assertEquals("${p.name} ($where)", PaletteExpected.skyBare.getValue(p.name), lowest, 0.01)
            assertTrue("${p.name} $lowest at $where", lowest >= 4.5)
        }
    }

    @Test
    fun `every text token on its Card over the summed sky glow passes AA at every grid point`() {
        for (p in all) {
            val (lowest, where) = gridMin(p, p.textTokens, card = p.card)
            assertEquals("${p.name} ($where)", PaletteExpected.skyCard.getValue(p.name), lowest, 0.01)
            assertTrue("${p.name} $lowest at $where", lowest >= 4.5)
        }
    }

    @Test
    fun `the glow geometry lives in one place and drawSkyGlow reads it`() {
        val chrome = TestFiles.require("${TestFiles.KOTLIN}/ui/components/Chrome.kt").readText()
        val draw = chrome.substringAfter("fun DrawScope.drawSkyGlow").substringBefore("\n}\n")
        assertTrue(draw.contains("SkyGlowGeometry.top"))
        assertTrue(draw.contains("SkyGlowGeometry.left"))
        assertTrue(draw.contains("SkyGlowGeometry.bottom"))
        for (literal in listOf("0.18f", "1.05f", "0.85f")) assertFalse("drawSkyGlow holds the literal $literal", draw.contains(literal))
        assertEquals(Wash(0.5f, 0f, 1.05f), SkyGlowGeometry.top)
        assertEquals(Wash(0.18f, 0.04f, 0.55f), SkyGlowGeometry.left)
        assertEquals(Wash(0.95f, 0.85f, 0.9f), SkyGlowGeometry.bottom)
    }

    @Test
    fun `the Notice draws an opaque Surface base before its tint`() {
        val chrome = TestFiles.require("${TestFiles.KOTLIN}/ui/components/Chrome.kt").readText()
        val notice = chrome.substringAfter("fun Notice(").substringBefore("fun DrawScope.drawSkyGlow")
        val base = notice.indexOf(".background(base, shape)")
        val tint = notice.indexOf(".background(accent.copy(alpha = 0.10f), shape)")
        assertTrue(base in 0 until tint)
        assertTrue(notice.contains("LocalSunTokens.current.Surface"))
    }

    // ------------------------------------------------------------------ what each theme and mode can show

    @Test
    fun `every palette either tab mode can put on a tab is one of the nine checked above`() {
        val checked = setOf(SunNight, SunDusk, SunLight, MoonNight, MoonDusk, MoonLight, TwilightDark, TwilightDusk, TwilightLight)
        for (mode in TabPaletteMode.entries) for (theme in Theme.entries) for (tab in Tab.entries) {
            val variant = Palettes.variantFor(theme, tab, mode)
            val used: Any = when (tab) {
                Tab.SUN -> Palettes.sun(variant)
                Tab.MOON -> Palettes.moon(variant)
                Tab.ALARMS -> Palettes.twilight(variant)
            }
            assertTrue("$mode $theme $tab", used in checked)
        }
    }

    @Test
    fun `THEMED gives Light, Dusk and Night to every tab, FIXED fixes the Moon and Alarms tabs on Night`() {
        for (tab in Tab.entries) {
            assertEquals(PaletteVariant.LIGHT, Palettes.variantFor(Theme.SUN, tab, TabPaletteMode.THEMED))
            assertEquals(PaletteVariant.DUSK, Palettes.variantFor(Theme.TWILIGHT, tab, TabPaletteMode.THEMED))
            assertEquals(PaletteVariant.NIGHT, Palettes.variantFor(Theme.MOON, tab, TabPaletteMode.THEMED))
        }
        assertEquals(PaletteVariant.LIGHT, Palettes.variantFor(Theme.SUN, Tab.SUN, TabPaletteMode.FIXED))
        assertEquals(PaletteVariant.DUSK, Palettes.variantFor(Theme.TWILIGHT, Tab.SUN, TabPaletteMode.FIXED))
        for (theme in Theme.entries) for (tab in listOf(Tab.MOON, Tab.ALARMS)) {
            assertEquals(PaletteVariant.NIGHT, Palettes.variantFor(theme, tab, TabPaletteMode.FIXED))
        }
        // The Alarms tab's Twilight palette: Light in the Sun theme, Dusk (golden hour) in the Twilight theme, Dark in the Moon theme.
        assertEquals(TwilightLight, Palettes.twilight(PaletteVariant.LIGHT))
        assertEquals(TwilightDusk, Palettes.twilight(PaletteVariant.DUSK))
        assertEquals(TwilightDark, Palettes.twilight(PaletteVariant.NIGHT))
    }

    @Test
    fun `the built mode is THEMED`() {
        assertEquals(TabPaletteMode.THEMED, TAB_PALETTE_MODE)
    }

    @Test
    fun `the chrome pairs pass in every theme on every tab, in both tab modes`() {
        for (mode in TabPaletteMode.entries) for (theme in Theme.entries) for (tab in Tab.entries) {
            val c = Palettes.chrome(theme, tab, mode)
            val bg = rgb(c.Background)
            val surface = rgb(c.Surface)
            for ((name, colour) in listOf("TextPrimary" to c.TextPrimary, "TextSecondary" to c.TextSecondary, "Accent" to c.Accent)) {
                for (ground in listOf(bg, surface)) {
                    val r = ratio(rgb(colour), ground)
                    assertTrue("$mode $theme $tab chrome $name is $r", r >= 4.5)
                }
            }
            assertTrue("$mode $theme $tab OnAccent", ratio(rgb(c.OnAccent), rgb(c.Accent)) >= 4.5)
        }
    }

    @Test
    fun `in FIXED mode the chrome comes from the palette of the tab body, so a fixed tab is dark all through`() {
        for (theme in Theme.entries) {
            assertEquals(Palettes.chromeOf(MoonNight), Palettes.chrome(theme, Tab.MOON, TabPaletteMode.FIXED))
            assertEquals(Palettes.chromeOf(TwilightDark), Palettes.chrome(theme, Tab.ALARMS, TabPaletteMode.FIXED))
            // The Sun tab still follows the theme.
            assertEquals(Palettes.chrome(theme, Tab.SUN, TabPaletteMode.THEMED), Palettes.chrome(theme, Tab.SUN, TabPaletteMode.FIXED))
        }
        for (theme in Theme.entries) for (tab in Tab.entries) {
            val body = when (tab) {
                Tab.SUN -> Palettes.sun(Palettes.variantFor(theme, tab, TabPaletteMode.FIXED)).Background
                Tab.MOON -> Palettes.moon(Palettes.variantFor(theme, tab, TabPaletteMode.FIXED)).Background
                Tab.ALARMS -> Palettes.twilight(Palettes.variantFor(theme, tab, TabPaletteMode.FIXED)).Background
            }
            if (tab != Tab.SUN || theme != Theme.TWILIGHT) {
                assertEquals("$theme $tab", body, Palettes.chrome(theme, tab, TabPaletteMode.FIXED).Background)
            }
        }
    }

    @Test
    fun `every theme gives the chrome of the tab's own palette (R5)`() {
        assertEquals(Palettes.chromeOf(SunDusk), Palettes.chrome(Theme.TWILIGHT, Tab.SUN))
        assertEquals(Palettes.chromeOf(MoonDusk), Palettes.chrome(Theme.TWILIGHT, Tab.MOON))
        assertEquals(Palettes.chromeOf(TwilightDusk), Palettes.chrome(Theme.TWILIGHT, Tab.ALARMS))
        assertEquals(Palettes.chromeOf(SunLight), Palettes.chrome(Theme.SUN, Tab.SUN))
        assertEquals(Palettes.chromeOf(MoonLight), Palettes.chrome(Theme.SUN, Tab.MOON))
        assertEquals(Palettes.chromeOf(TwilightLight), Palettes.chrome(Theme.SUN, Tab.ALARMS))
        assertEquals(Palettes.chromeOf(SunNight), Palettes.chrome(Theme.MOON, Tab.SUN))
        assertEquals(Palettes.chromeOf(MoonNight), Palettes.chrome(Theme.MOON, Tab.MOON))
        assertEquals(Palettes.chromeOf(TwilightDark), Palettes.chrome(Theme.MOON, Tab.ALARMS))
        assertTrue(Palettes.chrome(Theme.SUN, Tab.SUN).isLight)
        assertFalse(Palettes.chrome(Theme.MOON, Tab.SUN).isLight)
        assertFalse(Palettes.chrome(Theme.TWILIGHT, Tab.MOON).isLight)
    }

    // ------------------------------------------------------------------ "keeping the colours"

    @Test
    fun `Night is SunApp's palette (2a26f98, ui Theme_kt) except two lightened tokens`() {
        assertEquals(Color(0xFF080C18), SunNight.Background)
        assertEquals(Color(0xFF0E1628), SunNight.Surface)
        assertEquals(Color(0xB80E1630), SunNight.Card)
        assertEquals(Color(0xD9162246), SunNight.CardHover)
        assertEquals(Color(0x14FFFFFF), SunNight.Border)
        assertEquals(Color(0x59FFD700), SunNight.BorderActive)
        assertEquals(Color(0xFFF0F4FF), SunNight.TextPrimary)
        // SunApp has #8A9BCA; lightened by 2 per channel in R4 for the summed sky glow (D67, invisible: CIEDE2000 0.62).
        assertEquals(Color(0xFF8C9DCC), SunNight.TextSecondary)
        // SunApp has #7C8BBC; lightened for the glow peak (fix round 1, Palettes.kt): the other token that differs.
        assertEquals(Color(0xFF8190BF), SunNight.TextMuted)
        assertEquals(Color(0xFFFFD700), SunNight.Gold)
        assertEquals(Color(0xFFFF8C42), SunNight.Orange)
        assertEquals(Color(0xFFFF6B6B), SunNight.Rose)
        assertEquals(Color(0xFF4FC3F7), SunNight.Blue)
        assertEquals(Color(0xFFFFF176), SunNight.Noon)
        // SunApp's sky glow: orange 18 %, gold 12 %, blue 10 % (Chrome.kt drawSkyGlow).
        assertEquals(Glow(Color(0xFFFF8C42), 0.18f), SunNight.GlowWarm)
        assertEquals(Glow(Color(0xFFFFD700), 0.12f), SunNight.GlowGold)
        assertEquals(Glow(Color(0xFF4FC3F7), 0.10f), SunNight.GlowCool)
        assertFalse(SunNight.isLight)
        // SunApp put its Background on a Gold button.
        assertEquals(SunNight.Background, SunNight.OnAccent)
    }

    @Test
    fun `Night is MoonApp's palette (68c7596, ui Theme_kt) except two lightened tokens`() {
        assertEquals(Color(0xFF050810), MoonNight.Background)
        assertEquals(Color(0xFF090D1E), MoonNight.Surface)
        assertEquals(Color(0xBF090D20), MoonNight.Card)
        assertEquals(Color(0xE00E1430), MoonNight.CardHover)
        assertEquals(Color(0x12FFFFFF), MoonNight.Border)
        assertEquals(Color(0x4DC8D8F0), MoonNight.BorderActive)
        assertEquals(Color(0x0DFFFFFF), MoonNight.BorderSoft)
        assertEquals(Color(0xFFE8EEFF), MoonNight.TextPrimary)
        // MoonApp has #7B8FC4; lightened by 4 per channel in R4 for the summed sky glow (D67, CIEDE2000 1.33).
        assertEquals(Color(0xFF7F93C8), MoonNight.TextSecondary)
        assertEquals(Color(0xFF3A4570), MoonNight.TextMuted)
        // MoonApp has #6F7DB4; lightened for the glow peak (fix round 1, Palettes.kt): the other token that differs.
        assertEquals(Color(0xFF7A87BA), MoonNight.TextHint)
        assertEquals(Color(0xFFA9B7DA), MoonNight.TextSoft)
        assertEquals(Color(0xFF7B8FC4), MoonNight.TextMutedReadable)
        assertEquals(Color(0xFFC8D8F0), MoonNight.Silver)
        assertEquals(Color(0xFF7B9FD4), MoonNight.Blue)
        assertEquals(Color(0xFF5B72B8), MoonNight.Indigo)
        assertEquals(Color(0xFF9B7FD4), MoonNight.Violet)
        assertEquals(Color(0xFF4FC3F7), MoonNight.Teal)
        assertEquals(Color(0xFFB0B8D4), MoonNight.NewMoon)
        assertEquals(Color(0xFFFF6B6B), MoonNight.Error)
        // MoonApp's sky glow: blue 16 %, silver 10 %, violet 9 % (Chrome.kt drawSkyGlow).
        assertEquals(Glow(Color(0xFF7B9FD4), 0.16f), MoonNight.GlowTop)
        assertEquals(Glow(Color(0xFFC8D8F0), 0.10f), MoonNight.GlowLeft)
        assertEquals(Glow(Color(0xFF9B7FD4), 0.09f), MoonNight.GlowBottom)
        assertFalse(MoonNight.isLight)
        assertEquals(MoonNight.Background, MoonNight.OnAccent)
    }

    @Test
    fun `Twilight keeps each tab's identity`() {
        assertEquals(SunNight.Gold, SunDusk.Gold)
        assertEquals(SunNight.Noon, SunDusk.Noon)
        assertEquals(SunNight.BorderActive, SunDusk.BorderActive)
        assertEquals(MoonNight.Silver, MoonDusk.Silver)
        assertEquals(MoonNight.Teal, MoonDusk.Teal)
        assertEquals(MoonNight.BorderActive, MoonDusk.BorderActive)
        assertEquals(TwilightDark.Gold, TwilightDusk.Gold)
    }

    @Test
    fun `the Sun theme is unchanged in R4`() {
        // The values at HEAD a4b8c4f, copied here: Marcos said the Sun theme "looks great".
        assertEquals(
            SunTokens(
                Background = Color(0xFFFFF8EA), Surface = Color(0xFFFFFFFF), Card = Color(0xD9FFFFFF), CardHover = Color(0xE6FFEDBF),
                Border = Color(0x1F000000), BorderActive = Color(0x8C7A5C00), TextPrimary = Color(0xFF141A30),
                TextSecondary = Color(0xFF465276), TextMuted = Color(0xFF566184), Gold = Color(0xFF7A5C00),
                Orange = Color(0xFFA84300), Rose = Color(0xFFB3261E), Blue = Color(0xFF00658F), Noon = Color(0xFF6E5A00),
                OnAccent = Color(0xFFFFFFFF), GlowWarm = Glow(Color(0xFFFF8C42), 0.18f), GlowGold = Glow(Color(0xFFFFD700), 0.12f),
                GlowCool = Glow(Color(0xFF4FC3F7), 0.1f), isLight = true,
            ),
            SunLight,
        )
        assertEquals(
            MoonTokens(
                Background = Color(0xFFF2F5FB), Surface = Color(0xFFFFFFFF), Card = Color(0xD9FFFFFF), CardHover = Color(0xE6E1E8F6),
                Border = Color(0x1F0A1430), BorderActive = Color(0x803A4D73), BorderSoft = Color(0x14000000),
                TextPrimary = Color(0xFF121931), TextSecondary = Color(0xFF46537E), TextMuted = Color(0xFF9AA6C8),
                TextHint = Color(0xFF525E88), TextSoft = Color(0xFF2E3A63), TextMutedReadable = Color(0xFF46537E),
                Silver = Color(0xFF3A4D73), Blue = Color(0xFF2D5C9E), Indigo = Color(0xFF3D4F96), Violet = Color(0xFF6544AD),
                Teal = Color(0xFF006A93), NewMoon = Color(0xFF4A5371), Error = Color(0xFFB3261E), OnAccent = Color(0xFFFFFFFF),
                GlowTop = Glow(Color(0xFF7B9FD4), 0.16f), GlowLeft = Glow(Color(0xFFC8D8F0), 0.1f),
                GlowBottom = Glow(Color(0xFF9B7FD4), 0.09f), isLight = true,
            ),
            MoonLight,
        )
        assertEquals(
            SunTokens(
                Background = Color(0xFFFBF4F1), Surface = Color(0xFFFFFFFF), Card = Color(0xD9FFFFFF), CardHover = Color(0xE6F4E3EE),
                Border = Color(0x1F000000), BorderActive = Color(0x8C8F4F00), TextPrimary = Color(0xFF22172E),
                TextSecondary = Color(0xFF58496D), TextMuted = Color(0xFF66587B), Gold = Color(0xFF8F4F00),
                Orange = Color(0xFFA33F17), Rose = Color(0xFFB02A3E), Blue = Color(0xFF3A4FB0), Noon = Color(0xFF755800),
                OnAccent = Color(0xFFFFFFFF), GlowWarm = Glow(Color(0xFFFFB347), 0.14f), GlowGold = Glow(Color(0xFFFF8A5C), 0.12f),
                GlowCool = Glow(Color(0xFF93B6FF), 0.08f), isLight = true,
            ),
            TwilightLight,
        )
    }

    @Test
    fun `a fixed ring screen colour pair for a moon alarm passes (SPEC 4_3, fixed colours)`() {
        val bg = rgb(Color(0xFF0F172A))
        val expected = listOf(
            Color(0xFFFFFFFF) to 17.85, // the label
            Color(0xFF94A3B8) to 6.96, // the detail line
            Color(0xFF38BDF8) to 8.33, // the time, Snooze text and border
        )
        for ((colour, want) in expected) assertEquals(want, two(ratio(rgb(colour), bg)), 0.0)
        // Dismiss: #0F172A text on a #38BDF8 button.
        assertEquals(8.33, two(ratio(bg, rgb(Color(0xFF38BDF8)))), 0.0)
    }

    @Test
    fun `the shipped composition locals default to the Night palettes, so a bare composable stays dark`() {
        val source = TestFiles.require("${TestFiles.KOTLIN}/ui/theme/TwinlitTheme.kt").readText()
        assertTrue(source.contains("val LocalSunTokens = staticCompositionLocalOf { SunNight }"))
        assertTrue(source.contains("val LocalMoonTokens = staticCompositionLocalOf { MoonNight }"))
    }

    @Test
    fun `the Moon palette never uses Indigo as a container for text`() {
        // MoonApp put TextPrimary on Indigo at 3.98:1 (fails AA); Twinlit's shared picker uses the
        // accent button (Silver with OnAccent) instead. No main source puts text on Moon.Indigo.
        val offenders = TestFiles.require(TestFiles.KOTLIN).walk()
            .filter { it.isFile && it.extension == "kt" && it.name != "Palettes.kt" && it.name != "TwinlitTheme.kt" }
            .filter { Regex("""Moon\.Indigo|\.Indigo\b""").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertTrue(offenders.toString(), offenders.isEmpty())
        assertTrue(3.98 < 4.5 && ratio(rgb(MoonNight.TextPrimary), rgb(MoonNight.Indigo)) < 4.5)
    }
}
