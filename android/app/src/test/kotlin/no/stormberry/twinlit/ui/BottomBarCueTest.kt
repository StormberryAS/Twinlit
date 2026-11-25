package no.stormberry.twinlit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import no.stormberry.twinlit.theme.Theme
import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.Palettes
import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.Tab
import no.stormberry.twinlit.ui.theme.TabPaletteMode
import no.stormberry.twinlit.ui.theme.TwilightDusk
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * R5 (D10): the selected tab of the bottom bar is marked by a 48 by 3 dp bar in the tab's own accent,
 * 3 to 1 or more against the bar in every theme, tab and tab palette mode (CHANGES-R5 section 5.2:
 * before R5 the indicator pill read 1.06 to 1.44 to 1 and the accent label 1.04 to 2.10 to 1 against
 * the bar). Pure arithmetic plus a source check, because no Compose UI test runs here.
 */
class BottomBarCueTest {

    private fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }

    private fun lum(c: Color): Double {
        val a = c.toArgb()
        return 0.2126 * lin((a shr 16) and 0xFF) + 0.7152 * lin((a shr 8) and 0xFF) + 0.0722 * lin(a and 0xFF)
    }

    private fun ratio(fg: Color, bg: Color): Double {
        assertEquals("the bar is opaque", 1f, bg.alpha, 0f)
        assertEquals("the mark is opaque", 1f, fg.alpha, 0f)
        val l1 = lum(fg); val l2 = lum(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test
    fun `the selection mark keeps 3 to 1 on the bar in every theme, tab and mode`() {
        for (mode in TabPaletteMode.entries) for (theme in Theme.entries) for (tab in Tab.entries) {
            val chrome = Palettes.chrome(theme, tab, mode)
            val r = ratio(chrome.Accent, chrome.Surface)
            assertTrue("$mode $theme $tab: mark on the bar is %.2f".format(r), r >= 3.0)
        }
        // CHANGES-R5 5.2, THEMED, to two decimals.
        val want = mapOf(
            (Theme.SUN to Tab.SUN) to 6.25, (Theme.SUN to Tab.MOON) to 8.44, (Theme.SUN to Tab.ALARMS) to 6.39,
            (Theme.TWILIGHT to Tab.SUN) to 10.79, (Theme.TWILIGHT to Tab.MOON) to 9.68, (Theme.TWILIGHT to Tab.ALARMS) to 7.98,
            (Theme.MOON to Tab.SUN) to 12.86, (Theme.MOON to Tab.MOON) to 13.36, (Theme.MOON to Tab.ALARMS) to 10.12,
        )
        for ((key, w) in want) {
            val chrome = Palettes.chrome(key.first, key.second, TabPaletteMode.THEMED)
            assertEquals("${key.first} ${key.second}", w, ratio(chrome.Accent, chrome.Surface), 0.006)
        }
    }

    @Test
    fun `in the Twilight theme each tab is marked in its own accent`() {
        val sun = Palettes.chrome(Theme.TWILIGHT, Tab.SUN).Accent
        val moon = Palettes.chrome(Theme.TWILIGHT, Tab.MOON).Accent
        val alarms = Palettes.chrome(Theme.TWILIGHT, Tab.ALARMS).Accent
        assertEquals(SunDusk.Gold, sun)
        assertEquals(MoonDusk.Silver, moon)
        assertEquals(TwilightDusk.Gold, alarms)
        assertEquals(3, setOf(sun, moon, alarms).size)
    }

    @Test
    fun `the mark is drawn only on the selected item, in the chrome accent, at the top`() {
        val bar = TestFiles.require("${TestFiles.KOTLIN}/ui/BottomBar.kt").readText()
        assertTrue(bar.contains("if (isSelected) Modifier.drawBehind { drawSelectionMark(chrome.Accent) } else Modifier"))
        assertTrue(bar.contains("SELECTION_MARK_WIDTH = 48.dp"))
        assertTrue(bar.contains("SELECTION_MARK_HEIGHT = 3.dp"))
        assertTrue(bar.contains("topLeft = Offset((size.width - w) / 2f, 0f)"))
    }
}
