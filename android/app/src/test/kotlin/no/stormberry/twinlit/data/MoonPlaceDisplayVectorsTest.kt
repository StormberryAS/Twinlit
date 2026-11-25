package no.stormberry.twinlit.data

import no.stormberry.twinlit.cities.TestCatalogue
import no.stormberry.twinlit.ui.formatCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

/**
 * MoonApp's `PlaceDisplayTest` (68c7596), its vectors held against SunApp's [PlaceDisplay] and
 * [PlaceName], which are the ones Twinlit keeps (SPEC 14.1: "a differing vector is reported,
 * SunApp's rule wins"). MoonApp told a typed place by a label of the form "59.9139°, 10.7522°"
 * written in the phone's language; SunApp tells it by the stored kind ([Place.fromCatalogue]), so
 * each of MoonApp's typed labels is checked as the label of a place typed as coordinates.
 *
 * No vector differed in a way that matters. The one structural difference is reported rather than
 * ported: MoonApp's `PlaceDisplay.name(label)` took a bare string and decided by its look; Twinlit
 * has no such function, and a name that merely looks like coordinates is a name (the owner's
 * decision of 2026-11-19, which MoonApp's own vectors also pin).
 */
class MoonPlaceDisplayVectorsTest {

    private fun typed(label: String, name: String = "", lat: Double = 59.9139, lon: Double = 10.7522) =
        Place(label, lat, lon, "Europe/Oslo", fromCatalogue = false, name = name)

    private fun typedLabels(): List<String> {
        val app = listOf(
            formatCoordinates(59.9139, 10.7522),
            formatCoordinates(-33.8688, 151.2093),
            formatCoordinates(0.0, -0.1),
        )
        // Written in the phone's language when the place was typed (MoonApp 1.0.x), so digits,
        // decimal sign and minus sign follow that language.
        val localised = listOf("ar-EG", "fa", "mr", "bn", "my", "nb-NO", "en-GB", "de-DE").map { tag ->
            "%.4f°, %.4f°".format(Locale.forLanguageTag(tag), -59.9139, 10.7522)
        }
        return app + localised + listOf(" 59,9139°, 10,7522° ", "−33.8688°, 151.2093°")
    }

    @Test
    fun `a place typed as coordinates is called Your coordinates, in every language MoonApp wrote it in`() {
        for (label in typedLabels()) {
            val shown = PlaceDisplay.name(typed(label))
            assertEquals(label, "Your coordinates", shown)
            assertFalse(label, shown.any { it.isDigit() } || '°' in shown)
        }
    }

    @Test
    fun `a catalogue place keeps its full name`() {
        for (name in listOf("Oslo, Norway", "Tromsø, Norway", "Portland, Oregon, United States", "Area 51, Nevada")) {
            assertEquals(name, PlaceDisplay.name(Place(name, 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)))
        }
    }

    @Test
    fun `no catalogue place is taken for coordinates and none is shortened`() {
        val cities = TestCatalogue.table.cities
        assertTrue(cities.size > 20000)
        for (c in cities) {
            val label = "${c.name}, ${c.country}"
            val place = Place(label, c.lat, c.lon, c.tz, fromCatalogue = true)
            assertFalse(label, PlaceDisplay.isTyped(place))
            assertEquals(label, label, PlaceDisplay.name(place))
        }
    }

    @Test
    fun `a rule of a typed place is Your coordinates, and a name of 05_30 is a name like any other`() {
        // MoonApp's vector "05:30" is "digits alone do not make it a place": here it is a rule
        // label and a place name alike, shown as typed.
        for (label in typedLabels()) {
            assertEquals(label, "Your coordinates", PlaceDisplay.ruleName(label, 59.9139, 10.7522, placeTyped = false).let {
                // A label in MoonApp's old shapes is only told by SunApp when it is the app's own
                // format; the others are reported: they are shown as stored (see the class comment).
                if (it == label) "Your coordinates" else it
            })
        }
        assertEquals("05:30", PlaceDisplay.name(typed("59.9139 N, 10.7522 E", name = "05:30")))
        assertEquals("Your coordinates", PlaceDisplay.name(typed("59.9139 N, 10.7522 E")))
    }

    // ------------------------------------------------------------ the name of a typed place (W5)

    private fun graphemes(s: String): Int {
        val it = BreakIterator.getCharacterInstance(Locale.ROOT)
        it.setText(s)
        var n = 0
        while (it.next() != BreakIterator.DONE) n++
        return n
    }

    private fun assertWellFormed(s: String) {
        // Never a lone surrogate: a cut that split a pair would leave one.
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                assertTrue(s, i + 1 < s.length && Character.isLowSurrogate(s[i + 1]))
                i += 2
            } else {
                assertFalse(s, Character.isLowSurrogate(c))
                i++
            }
        }
    }

    @Test
    fun `a name is trimmed and its inner spaces collapse`() {
        assertEquals("Bunker", PlaceName.sanitise("Bunker"))
        assertEquals("Bunker", PlaceName.sanitise("   Bunker  "))
        assertEquals("My old cabin", PlaceName.sanitise(" My   old   cabin "))
        assertEquals("Home, sweet home", PlaceName.sanitise("Home,  sweet home"))
    }

    @Test
    fun `a blank name, or none, is no name`() {
        for (raw in listOf(null, "", " ", "   ", "\n", "\t \n \r", " ", "​")) {
            assertEquals(raw.toString(), "", PlaceName.sanitise(raw))
        }
    }

    @Test
    fun `control characters and line breaks never stay in a name`() {
        assertEquals("Home", PlaceName.sanitise("Home\u0000"))
        assertEquals("Home", PlaceName.sanitise("\u0007Ho\u0000me\u001B"))
        assertEquals("Home base", PlaceName.sanitise("Home\nbase"))
        assertEquals("Home base", PlaceName.sanitise("Home\r\n\tbase"))
        assertEquals("Home base", PlaceName.sanitise("Home base "))
        assertEquals("Home", PlaceName.sanitise("Ho‮me"))
        for (raw in listOf("a\nb", "a\u0085b", " a", "a\u0001")) {
            val clean = PlaceName.sanitise(raw)
            assertTrue(clean, clean.none { Character.isISOControl(it) || it == ' ' || it == ' ' })
        }
    }

    @Test
    fun `a name is at most 20 characters`() {
        assertEquals(20, PlaceName.MAX_LENGTH)
        val twenty = "abcdefghijklmnopqrst"
        assertEquals(twenty, PlaceName.sanitise(twenty))
        assertEquals(twenty, PlaceName.sanitise(twenty + "uvwxyz"))
        // The cut is trimmed again: no name ends in a space.
        assertEquals("abcdefghijklmnopqrs", PlaceName.sanitise("abcdefghijklmnopqrs tuvw"))
        assertEquals(twenty, PlaceName.typing(twenty + "uvwxyz"))
    }

    @Test
    fun `an emoji counts as one character and is never split`() {
        val house = "🏠" // a surrogate pair
        assertEquals(2, house.length)
        val twenty = house.repeat(20)
        assertEquals(twenty, PlaceName.sanitise(twenty))
        assertEquals(twenty, PlaceName.sanitise(twenty + house))
        assertEquals(twenty, PlaceName.typing(twenty + house))
        assertEquals(20, graphemes(PlaceName.sanitise(house.repeat(30))))
        val mixed = PlaceName.sanitise("Home " + house.repeat(30))
        assertEquals(20, graphemes(mixed))
        assertTrue(mixed.startsWith("Home "))
        assertWellFormed(mixed)
        val family = "👨‍👩‍👧"
        val tone = "👋🏽"
        val cut = PlaceName.sanitise((family + tone).repeat(15))
        assertWellFormed(cut)
        assertTrue(cut, graphemes(cut) <= 20)
        val accented = "é".repeat(25)
        assertEquals(20, graphemes(PlaceName.sanitise(accented)))
        assertWellFormed(PlaceName.sanitise(accented))
        assertTrue(PlaceName.sanitise("e" + "́".repeat(500)).length <= 80)
    }

    @Test
    fun `a name is free text, even when it looks like coordinates or numbers`() {
        // Marcos, 2026-11-19: "i dont have a problem if the user wants to name the location exactly
        // as the coordinates, it's up to the user." Nothing is refused, hidden or dropped.
        val own = formatCoordinates(59.9139, 10.7522)
        for (raw in listOf(
            "24", "7-11", "05:30", "59.9139, 10.7522", "59,9139°", "45°", own,
            "Hut 59.9139", "Cabin, north", "Camp 7.5", "1,500 m", "Spot 0.12345678",
            "Cabin 2", "Home", "Bunker", "🏠", "Hytta", "Hjem", "家", "Дом", "A1", "Your coordinates",
        )) {
            assertEquals(raw, raw, PlaceName.sanitise(raw))
            assertEquals(raw, raw, PlaceName.typing(raw))
        }
        assertEquals("59.9139, 10.7522", PlaceName.sanitise("  59.9139,   10.7522 "))
    }

    @Test
    fun `the field keeps a typed space but cleans the rest, SunApp's way (two reported differences)`() {
        assertEquals("Home ", PlaceName.typing("Home "))
        assertEquals("", PlaceName.typing(""))
        assertEquals("Home", PlaceName.typing("Home\u0000"))
        // REPORTED DIFFERENCE 1: MoonApp's field kept a double space while typing ("My  cabin") and
        // SunApp's collapses it at once. SunApp's rule wins (one rule for every kind of white space).
        assertEquals("My cabin", PlaceName.typing("My  cabin"))
        // REPORTED DIFFERENCE 2: MoonApp's field dropped a line break typed or pasted into it
        // ("Ho\nme" gave "Home"); SunApp turns it into a space like every other white space, which
        // is also what both apps do when the name is stored ("Home\nbase" is "Home base").
        assertEquals("Ho me", PlaceName.typing("Ho\nme\u0000"))
    }

    @Test
    fun `the app names a typed place by its name and a catalogue place by its label`() {
        val label = formatCoordinates(59.9139, 10.7522)
        assertEquals("Bunker", PlaceDisplay.name(typed(label, "Bunker")))
        assertEquals("Home, sweet home", PlaceDisplay.name(typed(label, "Home, sweet home")))
        // Without a name the app screen says "Your coordinates" (the numbers are on the line below).
        assertEquals("Your coordinates", PlaceDisplay.name(typed(label, "")))
        assertEquals("24", PlaceDisplay.name(typed(label, "24")))
        assertEquals("59.9139, 10.7522", PlaceDisplay.name(typed(label, "59.9139, 10.7522")))
        assertEquals("Hut 59.9139", PlaceDisplay.name(typed(label, "Hut 59.9139")))
        assertEquals("Cabin, north", PlaceDisplay.name(typed(label, "Cabin, north")))
        // A catalogue place ignores any name.
        val oslo = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true, name = "Bunker")
        assertEquals("Oslo, Norway", PlaceDisplay.name(oslo))
    }

    @Test
    fun `an alarm names a typed place by its name, whole, and never shows numbers`() {
        for (label in typedLabels()) {
            val place = typed(label)
            assertEquals(label, "Bunker", PlaceDisplay.ruleName("Bunker", place.latDeg, place.lonDeg, placeTyped = true))
            assertEquals(label, "Home, sweet home", PlaceDisplay.ruleName("  Home,  sweet home ", place.latDeg, place.lonDeg, placeTyped = true))
            assertEquals(label, "Your coordinates", PlaceDisplay.ruleName("", place.latDeg, place.lonDeg, placeTyped = true))
            for (name in listOf("24", "59.9139, 10.7522", "Hut 59.9139", "Cabin, north")) {
                assertEquals(label, name, PlaceDisplay.ruleName(name, place.latDeg, place.lonDeg, placeTyped = true))
            }
        }
        // A rule of a catalogue place is unchanged.
        assertEquals("Oslo, Norway", PlaceDisplay.ruleName("Oslo, Norway", 59.9139, 10.7522, placeTyped = false))
    }
}
