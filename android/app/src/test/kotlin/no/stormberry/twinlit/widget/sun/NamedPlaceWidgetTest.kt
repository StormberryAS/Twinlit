package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.TROMSO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * A place typed as coordinates can have a name of its own (2.0.0, owner's decision W5 of
 * 2026-11-19), and the widgets show it: whole, not cut at a comma, never as numbers. A place with
 * no name is still "Your coordinates", and a catalogue place is still the city only.
 */
class NamedPlaceWidgetTest {

    private val osloPhone = ZoneId.of("Europe/Oslo")
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private val typedOslo = Place(
        label = formatCoordinates(OSLO.latDeg, OSLO.lonDeg),
        latDeg = OSLO.latDeg,
        lonDeg = OSLO.lonDeg,
        zoneId = OSLO.zoneId,
        fromCatalogue = false,
    )

    private fun state(place: Place) = WidgetModel.build(now, place, osloPhone, true)!!

    @Test
    fun `the header and the descriptions show the name`() {
        for (name in listOf("Bunker", "Home", "Cabin")) {
            val s = state(typedOslo.copy(name = name))
            assertEquals(name, s.placeLabel)
            assertEquals(name, WidgetText.placeName(typedOslo.copy(name = name)))
            for (d in listOf(WidgetText.describeSmall(s), WidgetText.describeMedium(s), WidgetText.describeLarge(s))) {
                assertTrue(d, d.startsWith("$name: "))
                assertFalse(d, d.contains("Your coordinates"))
            }
        }
    }

    @Test
    fun `a comma in the name is kept, the widget does not cut it to a city`() {
        val place = typedOslo.copy(name = "Home, sweet home")
        assertEquals("Home, sweet home", WidgetText.placeName(place))
        val s = state(place)
        assertEquals("Home, sweet home", s.placeLabel)
        assertTrue(WidgetText.describeLarge(s).startsWith("Home, sweet home: "))
        // A name that looks like "city, country" is still the name.
        assertEquals("Skien, Telemark", WidgetText.placeName(typedOslo.copy(name = "Skien, Telemark")))
    }

    @Test
    fun `a typed place without a name is Your coordinates`() {
        assertEquals("Your coordinates", WidgetText.placeName(typedOslo))
        assertEquals("Your coordinates", WidgetText.placeName(typedOslo.copy(name = "")))
        assertEquals("Your coordinates", WidgetText.placeName(typedOslo.copy(name = "   ")))
        assertEquals("Your coordinates", state(typedOslo).placeLabel)
    }

    @Test
    fun `a catalogue place is the city only and ignores a name`() {
        assertEquals("Oslo", WidgetText.placeName(OSLO))
        assertEquals("Tromsø", WidgetText.placeName(TROMSO))
        assertEquals("Oslo", WidgetText.placeName(OSLO.copy(name = "Home")))
        assertEquals("Oslo", state(OSLO.copy(name = "Home")).placeLabel)
    }

    @Test
    fun `no widget text or description holds the coordinates, named or not`() {
        val decimal = Regex("""\d[.,]\d""")
        val fractions = Regex("""\d+[.,](\d+)""").findAll(typedOslo.label).map { it.groupValues[1] }.toList()
        for (name in listOf("", "Bunker", "Home, sweet home")) {
            val s = state(typedOslo.copy(name = name))
            val texts = listOf(s.placeLabel, WidgetText.describeSmall(s), WidgetText.describeMedium(s), WidgetText.describeLarge(s))
            for (t in texts) {
                assertFalse(t, t.contains(typedOslo.label))
                assertFalse(t, decimal.containsMatchIn(t))
                for (f in fractions) assertFalse("$f in $t", t.contains(f))
            }
        }
    }

    @Test
    fun `a name that looks like numbers or coordinates is shown as typed, header and spoken text`() {
        // Owner's decision, 2026-11-19: the name is free text, it is up to the user.
        // The pair is the test place's own, written out, so that no other coordinate is in this file.
        val pair = "${OSLO.latDeg}, ${OSLO.lonDeg}"
        for (name in listOf("24", pair, "Hut ${OSLO.latDeg}", "Cabin, north", typedOslo.label)) {
            val place = typedOslo.copy(name = name)
            assertEquals(name, name, WidgetText.placeName(place))
            val s = state(place)
            assertEquals(name, name, s.placeLabel)
            for (d in listOf(WidgetText.describeSmall(s), WidgetText.describeMedium(s), WidgetText.describeLarge(s))) {
                assertTrue(d, d.startsWith("$name: "))
            }
        }
        // The same name on a catalogue place is ignored, as always.
        assertEquals("Oslo", WidgetText.placeName(OSLO.copy(name = "${OSLO.latDeg}, ${OSLO.lonDeg}")))
    }
}
