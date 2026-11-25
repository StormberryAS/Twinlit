package no.stormberry.twinlit.data

import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a place is stored (2.0.0): the name has a key of its own, a place stored before 2.0.0 has no
 * such key and reads with no name, and nothing a hand-edited file holds can break the rules.
 */
class PlaceCodecTest {

    private val typed = Place(
        label = formatCoordinates(OSLO.latDeg, OSLO.lonDeg),
        latDeg = OSLO.latDeg,
        lonDeg = OSLO.lonDeg,
        zoneId = OSLO.zoneId,
        fromCatalogue = false,
    )

    /** The preference file of SunApp before 2.0.0 (the shape the keys keep) for [place]: exactly the five keys it wrote. */
    private fun before200(place: Place): Map<String, Any> = mapOf(
        "place_label" to place.label,
        "place_lat" to place.latDeg.toString(),
        "place_lon" to place.lonDeg.toString(),
        "place_zone" to place.zoneId,
        "place_from_catalogue" to place.fromCatalogue,
    )

    @Test
    fun `a named typed place survives a round trip, with its name whole`() {
        for (name in listOf("Home", "Bunker", "Cabin", "Home, sweet home", "🏠 Base")) {
            val place = typed.copy(name = name)
            val stored = PlaceCodec.encode(place)
            assertEquals(name, stored["place_name"])
            assertEquals(place, PlaceCodec.decode(stored))
        }
    }

    @Test
    fun `the label is not touched by the name`() {
        val stored = PlaceCodec.encode(typed.copy(name = "Home, sweet home"))
        assertEquals(typed.label, stored["place_label"])
        assertEquals(typed.label, PlaceCodec.decode(stored)!!.label)
        assertTrue(PlaceDisplay.isCoordinates(PlaceCodec.decode(stored)!!.label, typed.latDeg, typed.lonDeg))
    }

    @Test
    fun `a place stored before 2_0_0 has no name key and reads with no name`() {
        val old = before200(typed)
        assertFalse(old.containsKey("place_name"))
        val read = PlaceCodec.decode(old)!!
        assertEquals("", read.name)
        assertEquals(typed, read)
        assertEquals("Your coordinates", PlaceDisplay.name(read))
        // A catalogue place likewise.
        assertEquals(OSLO, PlaceCodec.decode(before200(OSLO)))
        assertEquals("Oslo, Norway", PlaceDisplay.name(PlaceCodec.decode(before200(OSLO))!!))
    }

    @Test
    fun `no name is written for a place without one, and a blank name writes nothing`() {
        assertFalse(PlaceCodec.encode(typed).containsKey("place_name"))
        assertFalse(PlaceCodec.encode(typed.copy(name = "   ")).containsKey("place_name"))
        assertEquals(before200(typed), PlaceCodec.encode(typed))
    }

    @Test
    fun `a name is sanitised on the way in and on the way out`() {
        val stored = PlaceCodec.encode(typed.copy(name = "  My   cabin and a very long name  "))
        assertEquals("My cabin and a very", stored["place_name"])
        val hand = before200(typed) + mapOf("place_name" to "  Ho\u0000me" + "x".repeat(40))
        assertEquals(PlaceName.MAX_LENGTH, PlaceCodec.decode(hand)!!.name.length)
        assertEquals("Home" + "x".repeat(16), PlaceCodec.decode(hand)!!.name)
        // Another type under the key is no name.
        assertEquals("", PlaceCodec.decode(before200(typed) + mapOf("place_name" to 7))!!.name)
    }

    @Test
    fun `an incomplete place reads as none, as before`() {
        assertNull(PlaceCodec.decode(emptyMap<String, Any>()))
        assertNull(PlaceCodec.decode(before200(typed) + mapOf("place_lat" to "north")))
        assertNull(PlaceCodec.decode(before200(typed) - "place_zone"))
        // A name alone is not a place.
        assertNull(PlaceCodec.decode(mapOf("place_name" to "Home")))
    }

    @Test
    fun `a missing catalogue flag still reads as a catalogue place, and a name on it is not shown`() {
        val read = PlaceCodec.decode(before200(OSLO) - "place_from_catalogue" + mapOf("place_name" to "Home"))!!
        assertTrue(read.fromCatalogue)
        assertEquals("Oslo, Norway", PlaceDisplay.name(read))
    }

    @Test
    fun `every key the codec writes is in KEYS, so that clearing leaves nothing`() {
        val written = PlaceCodec.encode(typed.copy(name = "Home")).keys
        assertTrue(PlaceCodec.KEYS.containsAll(written))
        assertEquals(6, PlaceCodec.KEYS.size)
    }
}
