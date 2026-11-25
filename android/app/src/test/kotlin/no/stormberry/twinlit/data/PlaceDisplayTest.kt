package no.stormberry.twinlit.data

import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.SYDNEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one name for a place that other people can see (2.0.0, A6, owner's decision D1 of
 * 2026-11-18, the same wording as the widgets' P16): "Your coordinates" for a place typed as
 * coordinates, the catalogue name for a catalogue city. Examples use catalogue places only.
 */
class PlaceDisplayTest {

    private val typedOslo = Place(
        label = formatCoordinates(OSLO.latDeg, OSLO.lonDeg),
        latDeg = OSLO.latDeg,
        lonDeg = OSLO.lonDeg,
        zoneId = OSLO.zoneId,
        fromCatalogue = false,
    )

    private val digit = Regex("""\d""")

    @Test
    fun `the wording is the same everywhere a place is shown`() {
        // SunApp also pinned the widgets' own constant equal to this one; that line returns with
        // the widgets in milestone M6.
        assertEquals("Your coordinates", PlaceDisplay.YOUR_COORDINATES)
        assertFalse(digit.containsMatchIn(PlaceDisplay.YOUR_COORDINATES))
    }

    @Test
    fun `a typed place has no digit in its name, a catalogue city keeps its name`() {
        assertEquals("Your coordinates", PlaceDisplay.name(typedOslo))
        assertEquals("Oslo, Norway", PlaceDisplay.name(OSLO))
        assertEquals("Sydney, Australia", PlaceDisplay.name(SYDNEY))
        // The widgets cut the label at the first comma (W1) and the app does not: that pair of
        // assertions returns with the widgets in milestone M6.
    }

    @Test
    fun `a label that is the place's coordinates is hidden even when marked as catalogue`() {
        assertEquals("Your coordinates", PlaceDisplay.name(typedOslo.copy(fromCatalogue = true)))
        assertEquals("Your coordinates", PlaceDisplay.name(typedOslo.copy(fromCatalogue = true, label = "  ${typedOslo.label} ")))
    }

    @Test
    fun `a rule's place name - coordinates become the neutral name, a city stays`() {
        assertEquals("Your coordinates", PlaceDisplay.ruleName(typedOslo.label, OSLO.latDeg, OSLO.lonDeg))
        assertEquals("Oslo, Norway", PlaceDisplay.ruleName("Oslo, Norway", OSLO.latDeg, OSLO.lonDeg))
        assertEquals("", PlaceDisplay.ruleName("", OSLO.latDeg, OSLO.lonDeg))
    }

    @Test
    fun `a rule's label from before 2_0_0 loses the coordinates in its default text`() {
        val first = typedOslo.label.substringBefore(',')
        val old = "At sunrise, $first"
        assertEquals("At sunrise, Your coordinates", PlaceDisplay.ruleLabel(old, typedOslo.label, OSLO.latDeg, OSLO.lonDeg))
        // The whole pair in a label the user typed.
        assertEquals(
            "Wake at Your coordinates",
            PlaceDisplay.ruleLabel("Wake at ${typedOslo.label}", typedOslo.label, OSLO.latDeg, OSLO.lonDeg),
        )
        // A label the user typed without the coordinates is left alone.
        assertEquals("Wake", PlaceDisplay.ruleLabel("Wake", typedOslo.label, OSLO.latDeg, OSLO.lonDeg))
        // A catalogue city's label is left alone.
        assertEquals("At sunrise, Oslo", PlaceDisplay.ruleLabel("At sunrise, Oslo", "Oslo, Norway", OSLO.latDeg, OSLO.lonDeg))
    }

    @Test
    fun `a 1_x label with coordinates is cleaned although the rule moved to a catalogue city`() {
        // A rule made for a typed place, later moved to a catalogue city in the 1.x editor, kept its label.
        val old = "At sunrise, ${typedOslo.label.substringBefore(',')}"
        assertEquals("At sunrise, Your coordinates", PlaceDisplay.ruleLabel(old, "Sydney, Australia", -33.8688, 151.2093))
        assertEquals(
            "Wake at Your coordinates",
            PlaceDisplay.ruleLabel("Wake at ${typedOslo.label}", "Sydney, Australia", -33.8688, 151.2093),
        )
        assertEquals("Your coordinates", PlaceDisplay.ruleLabel("59.9139 N", "Oslo, Norway", OSLO.latDeg, OSLO.lonDeg))
        // Text that is not in the app's coordinate format is left alone.
        assertEquals("At sunrise, Oslo 2025.5 N", PlaceDisplay.ruleLabel("At sunrise, Oslo 2025.5 N", "Oslo, Norway", OSLO.latDeg, OSLO.lonDeg))
        assertEquals("Wake 7.30", PlaceDisplay.ruleLabel("Wake 7.30", "Oslo, Norway", OSLO.latDeg, OSLO.lonDeg))
    }
}
