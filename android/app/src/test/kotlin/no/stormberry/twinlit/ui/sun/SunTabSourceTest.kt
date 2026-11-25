package no.stormberry.twinlit.ui.sun

import no.stormberry.twinlit.theme.Theme
import no.stormberry.twinlit.ui.theme.Glow
import no.stormberry.twinlit.ui.theme.Palettes
import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.SunLight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.ui.theme.Tab
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sun tab keeps SunApp's colours (SPEC 5, milestone M3 acceptance): in the Moon theme it uses
 * exactly SunApp's palette (`PaletteContrastTest` pins [SunNight] to SunApp `2a26f98`'s `Theme.kt`
 * values), in the Sun theme the light tokens, in the Twilight theme the dusk grounds. This test
 * pins the other half of that promise: that **every colour in the Sun tab's files comes from the
 * `Sun` facade** (no literal colours), so the theme reaches all of it, and that the tab is wired
 * into the root through the Sun palette of the theme in effect. It also pins the words that are
 * not in the shared picker: the hero and the notices.
 */
class SunTabSourceTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    /** The file without its comments, so a doc comment may name Settings or SunApp. */
    private fun code(path: String) = source(path)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    private val tabFiles = listOf("ui/sun/SunTimesScreen.kt", "ui/sun/Results.kt")

    @Test
    fun `no colour in the Sun tab is a literal, every one comes from the facade`() {
        for (path in tabFiles) {
            val text = code(path)
            assertFalse("$path: a literal Color(...)", Regex("""\bColor\(""").containsMatchIn(text))
            assertFalse("$path: a literal Color.Name", Regex("""\bColor\.(?!Transparent\b)[A-Z]""").containsMatchIn(text))
            assertFalse("$path: a hex literal", Regex("""0x[0-9A-Fa-f]{6,8}""").containsMatchIn(text))
            assertFalse("$path: reads a plain palette object", Regex("""\b(SunNight|SunDusk|SunLight|MoonNight|TwilightDark|TwilightDusk)\b""").containsMatchIn(text))
            assertFalse("$path: reads the Moon facade", Regex("""\bMoon\.""").containsMatchIn(text))
        }
        // The two Compose-only colour carriers the tab uses are read through the facade as well.
        assertTrue(source("ui/sun/Results.kt").contains("Sun.Noon"))
    }

    @Test
    fun `the root shows the Sun tab inside the Sun palette of the theme in effect`() {
        val app = source("ui/App.kt")
        assertTrue(app.contains("Tab.SUN -> TwinlitTheme(Palettes.sun(Palettes.variantFor(theme, Tab.SUN)))"))
        assertTrue(app.contains("SunTimesScreen("))
        assertFalse("the M1 placeholder is gone", app.contains("SunTabPlaceholder"))
        assertEquals(SunNight, Palettes.sun(Palettes.variantFor(Theme.MOON, Tab.SUN)))
        assertEquals(SunDusk, Palettes.sun(Palettes.variantFor(Theme.TWILIGHT, Tab.SUN)))
        assertEquals(SunLight, Palettes.sun(Palettes.variantFor(Theme.SUN, Tab.SUN)))
    }

    @Test
    fun `the Moon theme draws SunApp's sky glow, the same three washes`() {
        // SunApp's drawSkyGlow: Orange 0.18, Gold 0.12 and Blue 0.10 (ui/components/Chrome.kt).
        assertEquals(Glow(SunNight.Orange, 0.18f), SunNight.GlowWarm)
        assertEquals(Glow(SunNight.Gold, 0.12f), SunNight.GlowGold)
        assertEquals(Glow(SunNight.Blue, 0.10f), SunNight.GlowCool)
    }

    @Test
    fun `the date and the place are the root's, one place for the three tabs`() {
        val app = source("ui/App.kt")
        // The date moves with a place chosen on any tab (SPEC 3.3 step 2).
        assertTrue(app.contains("PlaceDay.afterPlaceChange("))
        assertTrue(app.contains("dateEpochDay = sunEpochDay"))
        val screen = code("ui/sun/SunTimesScreen.kt")
        assertFalse("the tab does not read or write the stored place itself", screen.contains("Settings"))
        assertFalse(screen.contains("CityAssets"))
        // The shared picker, not a second copy of its logic.
        assertTrue(screen.contains("PlacePicker("))
        assertFalse(screen.contains("fun parseCoordinate"))
        assertFalse(screen.contains("fun formatCoordinates"))
    }

    @Test
    fun `the hero and the empty results carry the words of TEXTS md`() {
        val chrome = source("ui/components/Chrome.kt")
        // D-1 (approved by Marcos 2026-11-22): the title; D-2 (SHIPPED): the tagline, slash as a line break.
        assertTrue(chrome.contains("const val SUN_HERO_TITLE = \"Sun times\""))
        assertTrue(chrome.contains("const val HERO_TAGLINE = \"SUNRISE \\u00B7 SOLAR NOON \\u00B7 SUNSET\\nanywhere on Earth\""))
        // D-3 and D-4 are REMOVED (Marcos, 2026-11-20): the box is gone, in every file of the tab.
        for (path in tabFiles + "ui/components/Chrome.kt") {
            val text = source(path)
            assertFalse("$path: NoLocationNotice", text.contains("NoLocationNotice"))
            assertFalse("$path: use my location", text.contains("use my location"))
            assertFalse("$path: the notice body", text.contains("Reading your position would need"))
        }
        // D-26 and D-27: the empty results.
        val results = source("ui/sun/Results.kt")
        assertTrue(results.contains("text = \"No place chosen yet\""))
        assertTrue(
            results.contains(
                "\"Search for a city, or switch to coordinates and type a latitude and \" +\n" +
                    "                \"longitude. Twinlit cannot guess where you are, because it never asks the \" +\n" +
                    "                \"device.\"",
            ),
        )
        // The word "SunApp" is in none of the tab's files (credits are the footer's, M7).
        for (path in tabFiles + "ui/components/Chrome.kt") {
            assertFalse("$path names SunApp in code", code(path).contains("SunApp"))
        }
    }

    @Test
    fun `the Sun tab has the picker card, the shared date bar and the results in this order`() {
        val screen = code("ui/sun/SunTimesScreen.kt")
        val order = listOf("SunHero()", "PlacePicker(", "DateBar(", "EmptyResults()", "SunResults(")
        val at = order.map { screen.indexOf(it) }
        assertTrue("every piece present: $at", at.all { it >= 0 })
        assertEquals("in order", at.sorted(), at)
        // The old date row is gone: no caption, no own field, no own step button, no own dialog.
        assertFalse(screen.contains("SectionLabel(\"Date\")"))
        assertFalse(screen.contains("StepButton"))
        assertFalse(screen.contains("DateField"))
        assertFalse(screen.contains("DateDialog"))
        assertTrue(screen.contains("colours = sunDateBarColours()"))
        // The bar is its own box under the card, not a part of it.
        val card = screen.substringAfter("SunCard {").substringBefore("DateBar(")
        assertEquals("the card is closed before the bar", 1, card.count { it == '}' } - card.count { it == '{' })
        // The tab bar of SunApp's three-chip footer is the app's bottom bar now; no second one.
        assertFalse(screen.contains("SunTab"))
    }

    @Test
    fun `SunCard delegates to the shared InputCard and keeps its look`() {
        val chrome = source("ui/components/Chrome.kt")
        val input = chrome.substringAfter("fun InputCard(").substringBefore("\n}\n")
        assertTrue(input.contains("RoundedCornerShape(20.dp)"))
        assertTrue(input.contains(".border(1.dp, border, shape)"))
        assertTrue(input.contains(".padding(18.dp)"))
        assertTrue(chrome.contains("InputCard(fill = Sun.Card"))
    }
}
