package no.stormberry.twinlit.data

import no.stormberry.twinlit.alarm.defaultLabel
import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.ui.formatCoordinates
import kotlin.math.abs

/**
 * How a place is named wherever it can be seen by someone else than its owner (2.0.0, A6): on the
 * home screen widgets (1.1.9) and in the alarm rule list, the alarm notification, the backup ring
 * notification and the ring screen, which show on the lock screen and in screenshots.
 *
 * A place the user typed as coordinates has the coordinates themselves as its label ("59.9139 N,
 * 10.7522 E", perhaps a home position). Those are never printed or spoken there, not even
 * rounded: the place is called [YOUR_COORDINATES] instead, unless the user gave it a name of
 * their own ([Place.name], [PlaceName]): then it is called that, exactly as stored, whole, wherever
 * the place name is shown, with no cut at a comma. A catalogue city keeps its name.
 *
 * A name is free text (owner's decision, 2026-11-19: "i dont have a problem if the user wants to
 * name the location exactly as the coordinates, it's up to the user."). It is never refused and
 * never hidden because it looks like coordinates or holds digits. A place is "typed" because of
 * its stored kind ([Place.fromCatalogue], `AlarmRule.placeTyped`), never because its name looks like
 * a pair of numbers. Only a stored LABEL can be told to be coordinates, and only to read the old
 * shapes: the label of a place or of a rule saved by 1.x, which could be the place's own
 * coordinates.
 *
 * Pure, so the tests can check it without a device.
 */
object PlaceDisplay {

    /** The name given to a place typed as coordinates. */
    const val YOUR_COORDINATES = "Your coordinates"

    /**
     * Whether [label] is the place's own coordinates as the app writes them. Only the place's own
     * pair counts: another pair is a label like any other (as the widgets pinned in 1.1.9), and a
     * rule's place name is always its own pair when typed, because the editor saves the rule's
     * coordinates and name from the same place.
     */
    fun isCoordinates(label: String, latDeg: Double, lonDeg: Double): Boolean =
        label.trim() == formatCoordinates(latDeg, lonDeg)

    /**
     * Whether [place] is one the user typed as coordinates: it is not from the catalogue, or its
     * label is its own coordinates even though it is marked as a catalogue place (a place stored by
     * mistake, or a missing key, which reads as true).
     */
    fun isTyped(place: Place): Boolean =
        !place.fromCatalogue || isCoordinates(place.label, place.latDeg, place.lonDeg)

    /**
     * The name the user gave to a typed place, sanitised ([PlaceName.sanitise]), or empty for none.
     * Exactly what the user typed otherwise: "24", "59.9139, 10.7522" and "Cabin, north" are names
     * like any other.
     */
    fun givenName(place: Place): String = PlaceName.sanitise(place.name)

    /**
     * The name to show for [place]: for a typed place its own name, whole, or [YOUR_COORDINATES]
     * when it has none; otherwise its label.
     */
    fun name(place: Place): String =
        if (isTyped(place)) givenName(place).ifEmpty { YOUR_COORDINATES } else place.label

    /**
     * The place name of a stored rule, for display. A rule keeps `placeTyped` for a place typed as
     * coordinates, and a rule saved before 2.0.0 with a typed place is told by its place name being
     * its own coordinates.
     *
     * A typed place's name is looked up when it is shown (2.0.0): when [current], the place the user
     * last looked at, is a typed place at the rule's own coordinates, the rule shows that place's
     * name now, so renaming the place renames the rule's place in the list, the notification and
     * the ring screen. Any other typed rule shows the name it was saved with ([placeName]: the
     * name, or [YOUR_COORDINATES]), because only one typed place and its name are remembered.
     * A rule of a catalogue place shows its stored place name, as always.
     */
    fun ruleName(
        placeName: String,
        latDeg: Double,
        lonDeg: Double,
        placeTyped: Boolean = false,
        current: Place? = null,
    ): String {
        val legacy = isLegacyTyped(placeTyped, placeName, latDeg, lonDeg)
        if (!placeTyped && !legacy) return placeName
        if (current != null && isTyped(current) && sameSpot(current.latDeg, current.lonDeg, latDeg, lonDeg)) return name(current)
        // A legacy rule's place name is the coordinates, never shown. A rule of 2.0.0 stored the
        // name, or "Your coordinates", itself: shown exactly as stored, whatever it looks like.
        if (legacy) return YOUR_COORDINATES
        return PlaceName.sanitise(placeName).ifEmpty { YOUR_COORDINATES }
    }

    /**
     * A rule saved before 2.0.0 for a place typed as coordinates: it has no `placeTyped` marker and
     * its stored place name is the place's own coordinates. Told by the stored place name, which
     * the 1.x editor made from the coordinates; a rule that carries the marker is never judged by
     * the look of its name.
     */
    private fun isLegacyTyped(placeTyped: Boolean, placeName: String, latDeg: Double, lonDeg: Double): Boolean =
        !placeTyped && isCoordinates(placeName, latDeg, lonDeg)

    /** Two points are the same place: equal to a centimetre, so that a stored number's round trip cannot matter. */
    private fun sameSpot(latA: Double, lonA: Double, latB: Double, lonB: Double): Boolean =
        abs(latA - latB) < 1e-7 && abs(lonA - lonB) < 1e-7

    /**
     * The coordinates as the app writes them ("59.9139 N" or "59.9139 N, 10.7522 E"), wherever
     * they stand in a text. A rule saved by 1.x for a typed place has them in its default label,
     * and the 1.x editor kept that label when the rule was later moved to a catalogue city.
     */
    private val COORDINATES_IN_TEXT = Regex("""\b\d{1,2}\.\d{4} [NS]\b(, \d{1,3}\.\d{4} [EW]\b)?""")

    /**
     * The label of a stored rule, for display. A rule saved before 2.0.0 for a typed place has a
     * default label made of the timing and the first part of the coordinates ("At sunrise,
     * 59.9139 N"); the coordinates in it are replaced by [YOUR_COORDINATES]. This holds whatever
     * the rule's place is now: a rule moved to a catalogue city in the 1.x editor kept that
     * label. A label the user typed is left alone unless it holds coordinates in the app's own
     * format or the rule's own coordinates.
     *
     * A rule of 2.0.0 ([placeTyped]) is left as stored: its label is the default made from the
     * name the user gave the place, or text they typed, and none of it is a stored 1.x label.
     */
    fun ruleLabel(label: String, placeName: String, latDeg: Double, lonDeg: Double, placeTyped: Boolean = false): String {
        if (placeTyped) return label
        var out = label
        if (isCoordinates(placeName, latDeg, lonDeg)) {
            val full = formatCoordinates(latDeg, lonDeg)
            // The whole pair first, then its first part, which is what the default label held.
            for (fragment in listOf(full, full.substringBefore(','))) out = out.replace(fragment, YOUR_COORDINATES)
        }
        return COORDINATES_IN_TEXT.replace(out, YOUR_COORDINATES)
    }
}

/**
 * The rule's place as shown to anyone: see [PlaceDisplay.ruleName]. [current] is the place the user
 * last looked at (`Settings.place`), or null where it cannot be read: the rule then shows the name
 * it was saved with.
 */
fun AlarmRule.displayPlaceName(current: Place? = null): String =
    PlaceDisplay.ruleName(placeName, latDeg, lonDeg, placeTyped, current)

/**
 * The rule's name as shown to anyone: see [PlaceDisplay.ruleLabel]. A name that is still the one
 * the app made when the rule was created ("At sunrise, Bunker": the timing, a comma and the place)
 * follows the place name as it is shown now ([current] as in [displayPlaceName]), so renaming the
 * place to "Cabin" gives "At sunrise, Cabin". A name the user edited is another text and never
 * changes; so does a name typed as exactly the default, which cannot be told from it.
 */
fun AlarmRule.displayLabel(current: Place? = null): String {
    val shown = PlaceDisplay.ruleLabel(label, placeName, latDeg, lonDeg, placeTyped)
    if (!hasGeneratedLabel()) return shown
    return defaultLabel(anchor, direction, offsetMinutes, displayPlaceName(current), cutAtComma = !isTypedForLabel())
}

/**
 * Whether the stored name of this rule is still the one the app made from its event, direction,
 * offset and place ("At sunrise, Bunker"), and not one the user typed. The one test behind
 * [displayLabel] (the name follows a renamed place, S6) and the editor (the name follows the
 * event, S11). A name typed as exactly the generated one cannot be told from it, and a rule that
 * 1.1.8 saved with the generated name counts as generated too: nothing is stored to tell them apart.
 */
fun AlarmRule.hasGeneratedLabel(): Boolean {
    val typed = isTypedForLabel()
    // Told by the stored marker; only a rule without it is judged by its stored place name (1.x).
    val own = !placeTyped && PlaceDisplay.isCoordinates(placeName, latDeg, lonDeg)
    // The app cut a catalogue label at its first comma and a typed place's name never (a rule
    // saved before 2.0.0 for a typed place had its coordinates, cut at the comma, in the default).
    val cuts = if (!typed) listOf(true) else if (own) listOf(false, true) else listOf(false)
    return cuts.any { cut -> label == defaultLabel(anchor, direction, offsetMinutes, placeName, cutAtComma = cut) }
}

/** A rule of a place typed as coordinates: told by the stored marker, or (a 1.x rule) by its place name. */
private fun AlarmRule.isTypedForLabel(): Boolean =
    placeTyped || PlaceDisplay.isCoordinates(placeName, latDeg, lonDeg)
