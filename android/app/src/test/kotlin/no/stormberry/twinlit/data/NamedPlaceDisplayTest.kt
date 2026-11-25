package no.stormberry.twinlit.data

import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.SYDNEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The display rule for the name of a typed place (2.0.0, owner's decision W5 of 2026-11-19): a
 * typed place with a name shows the name, whole; without a name "Your coordinates"; a catalogue
 * place is unchanged. Never the coordinates as numbers.
 */
class NamedPlaceDisplayTest {

    private val typedOslo = Place(
        label = formatCoordinates(OSLO.latDeg, OSLO.lonDeg),
        latDeg = OSLO.latDeg,
        lonDeg = OSLO.lonDeg,
        zoneId = OSLO.zoneId,
        fromCatalogue = false,
    )

    private val digit = Regex("""\d""")

    @Test
    fun `a typed place with a name shows the name, without one Your coordinates`() {
        assertEquals("Bunker", PlaceDisplay.name(typedOslo.copy(name = "Bunker")))
        assertEquals("Home", PlaceDisplay.name(typedOslo.copy(name = "Home")))
        assertEquals("Your coordinates", PlaceDisplay.name(typedOslo))
        assertEquals("Your coordinates", PlaceDisplay.name(typedOslo.copy(name = "   ")))
    }

    @Test
    fun `a comma in the name is not the end of a city`() {
        assertEquals("Home, sweet home", PlaceDisplay.name(typedOslo.copy(name = "Home, sweet home")))
    }

    @Test
    fun `a name is shown sanitised even when it was stored badly`() {
        assertEquals("My cabin", PlaceDisplay.name(typedOslo.copy(name = "  My \n cabin  ")))
        assertEquals("A".repeat(20), PlaceDisplay.name(typedOslo.copy(name = "A".repeat(50))))
    }

    @Test
    fun `a catalogue place is unchanged, and a name on it is ignored`() {
        assertEquals("Oslo, Norway", PlaceDisplay.name(OSLO))
        assertEquals("Oslo, Norway", PlaceDisplay.name(OSLO.copy(name = "Home")))
        assertEquals("Sydney, Australia", PlaceDisplay.name(SYDNEY))
    }

    @Test
    fun `a label that is the place's coordinates is typed even when marked as catalogue, and takes the name`() {
        val mislabelled = typedOslo.copy(fromCatalogue = true, name = "Cabin")
        assertTrue(PlaceDisplay.isTyped(mislabelled))
        assertEquals("Cabin", PlaceDisplay.name(mislabelled))
        assertEquals("Your coordinates", PlaceDisplay.name(mislabelled.copy(name = "")))
        assertFalse(PlaceDisplay.isTyped(OSLO))
    }

    @Test
    fun `a name is free text, even one that looks like coordinates or a number`() {
        // Owner's decision, 2026-11-19: it is up to the user. Shown exactly as stored.
        for (name in listOf("24", "7", "59.9139, 10.7522", "59.9139 N", "59.9139 N, 10.7522 E", "Hut 59.9139", "Cabin, north", "10.7461 E", "59,9127 10,7461", "Camp 7.5", "1,500 m", "-3")) {
            val place = typedOslo.copy(name = name)
            assertEquals(name, name, PlaceDisplay.name(place))
            assertEquals(name, name, PlaceDisplay.givenName(place))
            assertEquals(name, name, PlaceDisplay.ruleName(name, OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
            assertEquals(name, name, PlaceDisplay.ruleName("Your coordinates", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = place))
        }
        // The name that is the place's own coordinates, as the app writes them, is the name as well.
        assertEquals(typedOslo.label, PlaceDisplay.name(typedOslo.copy(name = typedOslo.label)))
        assertEquals(typedOslo.label, PlaceDisplay.ruleName(typedOslo.label, OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
    }

    @Test
    fun `no name is a name to refuse, and the name has no rule left but the limits`() {
        // The refusals of the earlier drafts are gone from the model, the strings and the field.
        val source = no.stormberry.twinlit.widget.TestFiles.require("${no.stormberry.twinlit.widget.TestFiles.KOTLIN}/data/PlaceDisplay.kt").readText()
        assertFalse(source.contains("Add a letter"))
        assertFalse(source.contains("Leave coordinates out"))
        assertFalse(source.contains("nameProblem"))
        // Still: trimmed, collapsed, 20 characters, blank is no name.
        assertEquals("Your coordinates", PlaceDisplay.name(typedOslo.copy(name = "\u200B\u200B")))
        assertEquals("4 2", PlaceDisplay.name(typedOslo.copy(name = "  4\n 2  ")))
    }

    @Test
    fun `a rule of a typed place shows the name it was saved with`() {
        assertEquals("Bunker", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
        assertEquals("Home, sweet home", PlaceDisplay.ruleName("Home, sweet home", OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
        assertEquals("Your coordinates", PlaceDisplay.ruleName("Your coordinates", OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
        assertEquals("Your coordinates", PlaceDisplay.ruleName("", OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
        // Saved by 1.x with the coordinates as the place name, no marker.
        assertEquals("Your coordinates", PlaceDisplay.ruleName(typedOslo.label, OSLO.latDeg, OSLO.lonDeg))
        // A rule that carries the typed marker has a name of the user's: shown as stored, even when it
        // is the coordinates (owner's decision, 2026-11-19). Only a rule without the marker is told
        // by its stored place name being its own coordinates (1.x).
        assertEquals(typedOslo.label, PlaceDisplay.ruleName(typedOslo.label, OSLO.latDeg, OSLO.lonDeg, placeTyped = true))
    }

    @Test
    fun `a rule of a catalogue place is unchanged`() {
        assertEquals("Oslo, Norway", PlaceDisplay.ruleName("Oslo, Norway", OSLO.latDeg, OSLO.lonDeg))
        assertEquals("Oslo, Norway", PlaceDisplay.ruleName("Oslo, Norway", OSLO.latDeg, OSLO.lonDeg, current = typedOslo.copy(name = "Home")))
        assertEquals("", PlaceDisplay.ruleName("", OSLO.latDeg, OSLO.lonDeg))
    }

    @Test
    fun `renaming the current typed place renames the rule of the same place`() {
        val saved = PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true)
        assertEquals("Bunker", saved)
        val renamed = typedOslo.copy(name = "Shelter")
        assertEquals("Shelter", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = renamed))
        // The name taken away: "Your coordinates".
        assertEquals("Your coordinates", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = typedOslo))
        // A rule saved without a name takes the name given later.
        assertEquals("Home", PlaceDisplay.ruleName("Your coordinates", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = typedOslo.copy(name = "Home")))
    }

    @Test
    fun `a rule of another place keeps its own name when the current place is renamed`() {
        // The user typed a second place after the first: only one typed place is remembered.
        val other = typedOslo.copy(latDeg = SYDNEY.latDeg, lonDeg = SYDNEY.lonDeg, name = "Cabin")
        assertEquals("Bunker", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = other))
        // A current place from the catalogue never names a typed rule.
        assertEquals("Bunker", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = OSLO))
        // No current place readable: the saved name.
        assertEquals("Bunker", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = null))
    }

    @Test
    fun `the same spot is told apart from a different one to a centimetre`() {
        val almost = typedOslo.copy(latDeg = OSLO.latDeg + 0.00000001, name = "Shelter")
        assertEquals("Shelter", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = almost))
        val moved = typedOslo.copy(latDeg = OSLO.latDeg + 0.001, name = "Shelter")
        assertEquals("Bunker", PlaceDisplay.ruleName("Bunker", OSLO.latDeg, OSLO.lonDeg, placeTyped = true, current = moved))
    }
}
