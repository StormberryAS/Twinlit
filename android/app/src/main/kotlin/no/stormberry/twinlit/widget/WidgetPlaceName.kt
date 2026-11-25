package no.stormberry.twinlit.widget

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.PlaceDisplay

/**
 * The place as a widget of either family names it (SPEC 8.3, "a typed place shows its name or
 * 'Your coordinates', never numbers"): a catalogue place is its city only, the text before the first
 * comma of its label ("Oslo, Norway" is "Oslo"); a place typed as coordinates is the name the user
 * gave it, whole (a comma in it is not the end of a city), or "Your coordinates" when it has none.
 * Never numbers. SunApp's rule, [PlaceDisplay.name] first, then the cut for a catalogue label; MoonApp
 * decided the same thing from the label's look, which Twinlit does not do (a place is "typed"
 * because of its stored kind, [PlaceDisplay.isTyped]).
 *
 * Widget only: the app screens and the alarms keep the whole label of a catalogue place through
 * [PlaceDisplay.name]. Pure, so the tests of both families can pin it.
 */
object WidgetPlaceName {

    fun of(place: Place): String =
        if (PlaceDisplay.isTyped(place)) PlaceDisplay.name(place) else cityOnly(PlaceDisplay.name(place))

    /**
     * [name] up to its first comma, trimmed ("Oslo, Norway" is "Oslo", "Tromsø, Norway" is
     * "Tromsø"). A name with no comma is returned trimmed and otherwise unchanged. A name that is
     * a pair of coordinates is also kept whole, so that it is never cut into half a pair, and a
     * name with nothing before its comma is kept whole rather than shown empty.
     */
    fun cityOnly(name: String): String {
        val trimmed = name.trim()
        if (COORDINATE_PAIR.matches(trimmed)) return trimmed
        // MoonApp's rule too: nothing before the comma keeps the label rather than showing nothing.
        return trimmed.substringBefore(',').trim().ifEmpty { trimmed }
    }

    private val COORDINATE_PAIR = Regex("""\d+(\.\d+)? [NS], \d+(\.\d+)? [EW]""")
}
