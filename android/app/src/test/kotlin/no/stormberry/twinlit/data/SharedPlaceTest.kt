package no.stormberry.twinlit.data

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ONE place for the whole app (SPEC 3.3, P1): stored once, in the `twinlit` file, and handed to
 * every tab by the root. There is no default place (D12): with none chosen Auto behaves as System
 * and the tabs show their empty states. The Moon tab reads the same place (M4).
 */
class SharedPlaceTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    @Test
    fun `an empty store has no place, and an incomplete one is none rather than a point in the Gulf of Guinea`() {
        assertNull(PlaceCodec.decode(emptyMap<String, Any>()))
        assertNull(PlaceCodec.decode(mapOf(PlaceCodec.KEY_LABEL to "Oslo, Norway")))
        assertNull(
            PlaceCodec.decode(
                mapOf(PlaceCodec.KEY_LABEL to "Oslo, Norway", PlaceCodec.KEY_LAT to "north", PlaceCodec.KEY_LON to "10.75", PlaceCodec.KEY_ZONE to "Europe/Oslo"),
            ),
        )
    }

    @Test
    fun `the place is stored once, in one preference file, as the same keys SunApp wrote`() {
        assertEquals("twinlit", Settings.PREFS_NAME)
        assertEquals(
            listOf("place_label", "place_lat", "place_lon", "place_zone", "place_from_catalogue", "place_name"),
            PlaceCodec.KEYS,
        )
        val place = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)
        assertEquals(place, PlaceCodec.decode(PlaceCodec.encode(place)))
    }

    @Test
    fun `the root holds the one place and hands the same value to every tab`() {
        val app = source("ui/App.kt")
        assertEquals("exactly one place state", 1, Regex("""var place by remember""").findAll(app).count())
        assertTrue(app.contains("var place by remember { mutableStateOf(settings.place) }"))
        assertTrue(app.contains("settings.place = next"))
        // The Sun and Moon tabs read it as `place = place`; the Alarms tab takes it as the starting
        // place of a new alarm, never imposed (each alarm keeps the place it was made for).
        assertEquals("two tabs read it", 2, Regex("""(?<![A-Za-z])place = place""").findAll(app).count())
        assertEquals("the Alarms tab is offered it", 1, Regex("""defaultPlace = place""").findAll(app).count())
        assertEquals("two pickers write it", 2, Regex("""onPlaceChange = ::changePlace""").findAll(app).count())
    }

    @Test
    fun `no tab keeps a place of its own or invents a default one`() {
        val tabFiles = listOf(
            "ui/sun/SunTimesScreen.kt", "ui/moon/MoonTimesScreen.kt",
            "ui/moon/MoonPlace.kt", "ui/alarms/AlarmsScreen.kt",
        )
        for (path in tabFiles) {
            val text = source(path)
            assertFalse("$path keeps a place", text.contains("mutableStateOf<Place"))
            // A call to a Place constructor; the pickers' own names and MoonApp's adapter do not count.
            val calls = text.replace("PlacePicker(", "").replace("MoonPlace(", "")
            assertFalse("$path invents a place", Regex("""(?<![A-Za-z])Place\(""").containsMatchIn(calls))
            assertFalse("$path writes the store", text.contains("Settings("))
        }
        // MoonApp's PlaceStore with its Oslo default is not brought (D12).
        assertFalse(source("data/Settings.kt").contains("DEFAULT_PLACE"))
    }

    @Test
    fun `changing the place re-evaluates the theme because Auto needs it`() {
        val app = source("ui/App.kt")
        assertTrue(app.contains("LaunchedEffect(appearance, place?.latDeg, place?.lonDeg, foreground)"))
        assertTrue(app.contains("ThemeResolver.resolve(appearance, place, now, systemDark)"))
    }

    @Test
    fun `the Moon tab reads the shared place, has no Oslo default and shows an empty state without one`() {
        val app = source("ui/App.kt")
        assertTrue(app.contains("MoonTimesScreen("))
        assertFalse("the M1 placeholder is gone", app.contains("MoonTabPlaceholder"))
        val moonCall = app.substringAfter("MoonTimesScreen(").substringBefore("dateEpochDay")
        assertTrue(moonCall.contains("place = place"))
        assertTrue(moonCall.contains("onPlaceChange = ::changePlace"))
        assertTrue(moonCall.contains("catalogue = catalogue"))

        val screen = source("ui/moon/MoonTimesScreen.kt")
        assertTrue("the screen takes a nullable place", screen.contains("place: Place?,"))
        // MoonApp's PlaceStore default is not brought (D12): no Oslo, no coordinates, in the tab.
        for (path in listOf("ui/moon/MoonTimesScreen.kt", "ui/moon/MoonPlace.kt")) {
            val text = source(path)
            assertFalse("$path names a default place", Regex("""\bOslo\b.*(\d\d\.\d{3,4})""").containsMatchIn(text))
            assertFalse("$path has a default", text.contains("DEFAULT_PLACE") || text.contains("PlaceStore"))
        }
        // The input comes first (R4): the picker, then the date bar, then the output. No place: the two
        // cards give way to the empty card (E-27, E-28); the picker and the bar stay.
        val body = screen.substring(screen.indexOf("verticalScroll("))
        assertTrue(body.indexOf("PlacePicker(") < body.indexOf("DateBar("))
        assertTrue(body.indexOf("DateBar(") < body.indexOf("if (moonPlace == null) {"))
        val items = body.substringAfter("if (moonPlace == null) {").substringBefore("LabsCarousel(")
        assertTrue(items.contains("EmptyCard(") && items.contains("PhaseCard(") && items.contains("TimesCard("))
        assertTrue(items.indexOf("EmptyCard(") < items.indexOf("PhaseCard("))
    }

    @Test
    fun `a place chosen on one tab moves the date of both tabs`() {
        val app = source("ui/App.kt")
        val change = app.substringAfter("fun changePlace(next: Place?) {").substringBefore("val tabState")
        assertTrue(change.contains("sunEpochDay = PlaceDay.afterPlaceChange("))
        assertTrue(change.contains("moonEpochDay = PlaceDay.afterPlaceChange("))
        assertTrue(change.contains("place = next"))
        assertTrue(change.contains("settings.place = next"))
        // The Moon tab's picker hands the place to the same callback the Sun tab's does.
        assertTrue(source("ui/moon/MoonTimesScreen.kt").contains("onPlaceChange = onPlaceChange"))
    }
}
