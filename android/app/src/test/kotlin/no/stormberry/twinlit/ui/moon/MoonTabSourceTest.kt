package no.stormberry.twinlit.ui.moon

import no.stormberry.twinlit.theme.Theme
import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.MoonLight
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.ui.theme.Palettes
import no.stormberry.twinlit.ui.theme.Tab
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Moon tab keeps MoonApp's colours and words (SPEC 6, milestone M4 acceptance): in the Moon
 * theme it uses exactly MoonApp's palette (`PaletteContrastTest` pins [MoonNight] to MoonApp
 * `68c7596`'s `Theme.kt` values), in the Sun theme the light tokens, in the Twilight theme the
 * dusk grounds. This test pins the other half of the promise: that **every colour in the Moon
 * tab's files comes from the `Moon` facade** (no literal colours, no Sun colours), so the theme
 * reaches all of it, that the tab is wired into the root through the Moon palette of the theme in
 * effect, and the words of the hero, the picker and the cards, verbatim to TEXTS.md section E.
 */
class MoonTabSourceTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    /** The file without its comments, so a doc comment may name MoonApp, Indigo or a colour. */
    private fun code(path: String) = source(path)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    private val tabFiles = listOf("ui/moon/MoonTimesScreen.kt", "ui/moon/MoonPlace.kt")

    @Test
    fun `no colour in the Moon tab is a literal, every one comes from the facade`() {
        for (path in tabFiles) {
            val text = code(path)
            assertFalse("$path: a literal Color(...)", Regex("""\bColor\(""").containsMatchIn(text))
            assertFalse("$path: a literal Color.Name", Regex("""\bColor\.(?!Transparent\b)[A-Z]""").containsMatchIn(text))
            assertFalse("$path: a hex literal", Regex("""0x[0-9A-Fa-f]{6,8}""").containsMatchIn(text))
            assertFalse("$path: reads a plain palette object", Regex("""\b(MoonNight|MoonDusk|MoonLight|SunNight|SunDusk|SunLight|TwilightDark|TwilightDusk|TwilightLight)\b""").containsMatchIn(text))
            assertFalse("$path: reads the Sun facade", Regex("""\bSun\.""").containsMatchIn(text))
            assertFalse("$path: reads MaterialTheme colours", Regex("""MaterialTheme\.colorScheme""").containsMatchIn(text))
        }
        assertTrue(source("ui/moon/MoonTimesScreen.kt").contains("Moon.Silver"))
    }

    @Test
    fun `the root shows the Moon tab inside the Moon palette of the theme in effect`() {
        val app = source("ui/App.kt")
        assertTrue(app.contains("Tab.MOON -> {"))
        assertTrue(app.contains("TwinlitTheme(Palettes.moon(Palettes.variantFor(theme, Tab.MOON)))"))
        assertTrue(app.contains("MoonTimesScreen("))
        assertFalse("the M1 placeholder is gone", app.contains("MoonTabPlaceholder"))
        assertEquals(MoonNight, Palettes.moon(Palettes.variantFor(Theme.MOON, Tab.MOON)))
        assertEquals(MoonDusk, Palettes.moon(Palettes.variantFor(Theme.TWILIGHT, Tab.MOON)))
        assertEquals(MoonLight, Palettes.moon(Palettes.variantFor(Theme.SUN, Tab.MOON)))
    }

    @Test
    fun `the input comes first, then the shared date bar, then the output (R4)`() {
        val screen = source("ui/moon/MoonTimesScreen.kt")
        val body = screen.substring(screen.indexOf("verticalScroll("))
        val order = listOf("MoonHero(", "PlacePicker(", "DateBar(", "EmptyCard(", "PhaseCard(", "TimesCard(", "LabsCarousel(")
            .map { body.indexOf(it) }
        assertTrue(order.toString(), order.all { it >= 0 })
        assertEquals("in this order", order.sorted(), order)
        // A scrolling Column, no longer a LazyColumn.
        assertTrue(screen.contains(".verticalScroll(rememberScrollState())"))
        assertFalse(code("ui/moon/MoonTimesScreen.kt").contains("LazyColumn"))
        // MoonApp's own picker card and date bar are gone.
        assertFalse(code("ui/moon/MoonTimesScreen.kt").contains("MoonPlacePicker"))
        assertFalse(code("ui/moon/MoonTimesScreen.kt").contains("private fun DateBar("))
        assertFalse(code("ui/moon/MoonTimesScreen.kt").contains("ArrowButton"))
        // The date bar is the shared one, in the Moon colours, before a place is chosen as well.
        assertTrue(code("ui/moon/MoonTimesScreen.kt").contains("colours = moonDateBarColours()"))
        assertTrue("the bar is before the place branch, so it shows without a place", body.indexOf("DateBar(") < body.indexOf("if (moonPlace == null)"))
    }

    @Test
    fun `the Moon tab calls the shared picker in the Moon palette with moon suggestions`() {
        val screen = source("ui/moon/MoonTimesScreen.kt")
        assertTrue(screen.contains("PlacePicker("))
        assertTrue(screen.contains("colours = PickerColours.of(LocalMoonTokens.current)"))
        assertTrue(screen.contains("suggestionLabel = { \"Show moon times for \${it.name}\" }"))
        assertTrue(screen.contains("mode = mode"))
        assertTrue(screen.contains("onModeChange = onModeChange"))
        // R5 (D14): the input part is the Sun tab's, the same card (20 dp, 1 dp edge, 18 dp padding), 12 dp
        // to the date bar, in the Moon palette.
        val input = screen.substringAfter("// The input first")
        assertTrue(input.contains("InputCard(fill = Moon.Card, border = Moon.Border)"))
        assertTrue(input.contains("verticalArrangement = Arrangement.spacedBy(12.dp)"))
        assertTrue(screen.contains("private val gutter = Modifier.padding(horizontal = 18.dp)"))
        val card = input.substringBefore("PlacePicker(")
        assertFalse(card.contains("RoundedCornerShape(16.dp)"))
    }

    @Test
    fun `the hero is the moon mark on its night chip with the blue halo, the title and the website tagline`() {
        val chrome = source("ui/components/Chrome.kt")
        val hero = chrome.substringAfter("fun MoonHero(").substringBefore("/** TEXTS.md E-1")
        assertTrue(hero.contains("MoonMark(\n            size = 64.dp,\n            chip = tokens.isLight,"))
        assertTrue(hero.contains("drawLogoGlow(blue, alpha = 0.34f)"))
        assertTrue(hero.contains("val blue = Brand.MoonBlue"))
        assertTrue(hero.contains("fontSize = 30.sp"))
        assertTrue(chrome.contains("const val MOON_HERO_TITLE = \"Moon times\""))
        assertTrue(chrome.contains("const val MOON_HERO_TAGLINE = \"MOONRISE \\u00B7 TRANSIT \\u00B7 MOONSET\\nanywhere on Earth\""))
        // The mark is only ever drawn through MoonMark (NavigationSourceTest), never as a bare image here.
        assertFalse(code("ui/moon/MoonTimesScreen.kt").contains("ic_moon_mark"))
    }

    @Test
    fun `the Moon tab words are TEXTS md section E verbatim`() {
        val screen = source("ui/moon/MoonTimesScreen.kt")
        for (text in listOf(
            "\"Illumination\"", "\"Distance\"", "\"New moon night\"", "\"Full moon night\"", "\"Not found\"",
            "\"Moonrise\"", "\"Transit\"", "\"Moonset\"", "ZoneCaption(MOON_ZONE_LEAD, zone.id,",
        )) assertTrue("missing $text", screen.contains(text))
        // R5 (D7): the card names the nights, not the dates of the moments.
        assertTrue(screen.contains("MoonPhase.nextNights(date, zone)"))
        assertFalse(screen.contains("nextLunations"))
        // R5 (D14): nothing the Sun tab does not have, no rename link and no dialog.
        for (gone in listOf("RenameDialog", "\"Rename\"", "\"Add a name\"", "AlertDialog"))
            assertFalse("$gone", screen.contains(gone))
        assertEquals("No place chosen yet", EMPTY_TITLE)
        assertEquals(
            "Choose a city above, or type coordinates. Twinlit then shows the moon's phase, moonrise, " +
                "transit and moonset for that place, computed on this device.",
            EMPTY_BODY,
        )
        assertEquals("This date did not happen here", SKIPPED_TITLE)
        assertEquals("The moon is above the horizon all day", ALWAYS_UP_TITLE)
        assertEquals("The moon stays below the horizon all day", ALWAYS_DOWN_TITLE)
        assertEquals("It neither rises nor sets on this date at this latitude.", NO_RISE_SET_BODY)
    }

    @Test
    fun `the Moon tab keeps no picker or rename state of its own`() {
        val screen = code("ui/moon/MoonTimesScreen.kt")
        for (field in listOf("renaming", "renameText"))
            assertFalse("$field", Regex("""var $field\b""").containsMatchIn(screen))
        // The six hoisted fields of MoonApp's picker are gone: the shared picker keeps its own text.
        for (field in listOf("query", "latText", "lonText", "nameText"))
            assertFalse("$field", Regex("""var $field by""").containsMatchIn(screen))
    }

    @Test
    fun `the output cards pad 18 dp like the Sun tab's cards (R5)`() {
        val screen = source("ui/moon/MoonTimesScreen.kt")
        for (card in listOf("private fun EmptyCard(", "private fun PhaseCard(", "private fun TimesCard(")) {
            val body = screen.substringAfter(card).substringBefore("\n}\n")
            assertTrue(card, body.contains("Column(Modifier.padding(18.dp))"))
        }
        assertTrue(screen.contains("MoonHero(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))"))
    }

    @Test
    fun `the date is the root's and nothing in the tab reads the clock for its own default`() {
        val screen = code("ui/moon/MoonTimesScreen.kt")
        assertTrue(screen.contains("dateEpochDay: Long"))
        assertTrue(screen.contains("onDateChange: (Long) -> Unit"))
        assertFalse(screen.contains("rememberSaveable { mutableStateOf(PlaceDay.today"))
        // "today" is the place's today (MoonApp 1.0.8), in both places it is needed.
        assertEquals(1, Regex("""PlaceDay\.today\(zone\)""").findAll(screen).count())
    }

    @Test
    fun `MoonApp's rewrite of every alarm on a place change is not brought`() {
        for (path in tabFiles + "ui/App.kt") {
            val text = code(path)
            assertFalse("$path rewrites alarms", text.contains("loadRules") || text.contains("saveRule") || text.contains("AlarmStore"))
        }
    }

    @Test
    fun `the Moon tab files hold no coordinate and no place name, the place is always the shared one`() {
        for (path in tabFiles) {
            val text = code(path)
            assertFalse("$path: a coordinate pair", Regex("""-?\d{1,3}\.\d{2,}\s*,\s*-?\d{1,3}\.\d{2,}""").containsMatchIn(text))
            assertFalse("$path: a city by name", Regex("""\b(Oslo|Tokyo|Sydney|Reykjavik)\b""").containsMatchIn(text))
        }
    }
}
