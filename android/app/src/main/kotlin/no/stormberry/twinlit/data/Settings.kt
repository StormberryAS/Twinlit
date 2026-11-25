package no.stormberry.twinlit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import no.stormberry.twinlit.theme.Appearance

/**
 * How the user told Twinlit where they are.
 *
 * There is deliberately no third value for device location. The web app has one
 * and this app cannot: reading the device position needs ACCESS_COARSE_LOCATION,
 * and no build of this app has ever asked for a location permission. See the notice composable in
 * `ui/components/Chrome.kt`, which says the same thing to the user rather than
 * leaving them to wonder why a button they remember is missing.
 */
enum class InputMode { CITY, COORDINATES }

/**
 * A resolved place: everything the sun-times screen needs, and nothing that
 * would make it go looking for more.
 *
 * The timezone is carried as a plain IANA id rather than a `ZoneId` because this
 * type is written to SharedPreferences, and because an id that no longer resolves
 * (a tzdb link retired between releases) must not throw on the way out of the
 * store. Resolution happens once, at the point of use, where it can fall back.
 *
 * @property label what the user sees: "Oslo, Norway" for a catalogue city, or
 *   the formatted coordinates for a typed pair.
 * @property fromCatalogue true when [zoneId] came with the city, false when it
 *   was inferred from the nearest catalogue city. Decides two things: whether the
 *   coordinate fields are prefilled on the next launch, and whether the home screen
 *   widgets may show and speak [label]. When it is false the widgets name the place
 *   with a neutral text, because the label of a typed pair is the coordinates
 *   themselves. So true is NOT a safe default for a place of unknown origin: set it
 *   only for a place that really came from the catalogue.
 * @property name the name the user gave to a place they typed as coordinates ("Home", "Bunker"),
 *   or empty for none (2.0.0). A field of its own, never part of [label], so that a typed place is
 *   still told by its label and a comma in a name is never read as the end of a city. At most
 *   [PlaceName.MAX_LENGTH] characters (see [PlaceName]). Ignored for a catalogue place. Places
 *   stored before 2.0.0 have none and behave as they always did. Shown through
 *   [PlaceDisplay.name] only.
 */
data class Place(
    val label: String,
    val latDeg: Double,
    val lonDeg: Double,
    val zoneId: String,
    val fromCatalogue: Boolean,
    val name: String = "",
)

/**
 * How a [Place] is written to and read from the preference file, apart from the file itself so
 * that the tests can check the round trip and the old shapes without a device (2.0.0).
 *
 * Coordinates are stored as strings, not floats: see [Settings]. The name has its own key,
 * [KEY_NAME], written only when there is a name and sanitised again on the way out, so a place
 * stored before 2.0.0 (no such key) reads with no name and a hand-edited value cannot exceed the
 * rules in [PlaceName].
 */
object PlaceCodec {
    const val KEY_LABEL = "place_label"
    const val KEY_LAT = "place_lat"
    const val KEY_LON = "place_lon"
    const val KEY_ZONE = "place_zone"
    const val KEY_FROM_CATALOGUE = "place_from_catalogue"
    const val KEY_NAME = "place_name"

    /** Every key a place uses, so that clearing the place removes all of them. */
    val KEYS: List<String> = listOf(KEY_LABEL, KEY_LAT, KEY_LON, KEY_ZONE, KEY_FROM_CATALOGUE, KEY_NAME)

    /**
     * The place held by [values] (the stored preference values by key), or null when it is absent
     * or incomplete: a partially written entry (label present, latitude unparseable) reads back as
     * null rather than as a place at 0,0 in the Gulf of Guinea.
     */
    fun decode(values: Map<String, *>): Place? {
        val label = values[KEY_LABEL] as? String ?: return null
        val lat = (values[KEY_LAT] as? String)?.toDoubleOrNull() ?: return null
        val lon = (values[KEY_LON] as? String)?.toDoubleOrNull() ?: return null
        val zone = values[KEY_ZONE] as? String ?: return null
        return Place(
            label = label,
            latDeg = lat,
            lonDeg = lon,
            zoneId = zone,
            // A missing key reads as true (a catalogue city), as it always has; changing that
            // would change what the app prefills. The key has been written together with the
            // place since the first release, so it is not missing in practice. True also lets
            // the widgets show the label; they still refuse a label equal to the place's own
            // formatted coordinates.
            fromCatalogue = values[KEY_FROM_CATALOGUE] as? Boolean ?: true,
            // A missing key (every place stored before 2.0.0) or a value of another type is no name.
            name = PlaceName.sanitise(values[KEY_NAME] as? String),
        )
    }

    /** The values to store for [place]: the name only when it is not blank, after [PlaceName.sanitise]. */
    fun encode(place: Place): Map<String, Any> = buildMap {
        put(KEY_LABEL, place.label)
        put(KEY_LAT, place.latDeg.toString())
        put(KEY_LON, place.lonDeg.toString())
        put(KEY_ZONE, place.zoneId)
        put(KEY_FROM_CATALOGUE, place.fromCatalogue)
        val name = PlaceName.sanitise(place.name)
        if (name.isNotEmpty()) put(KEY_NAME, name)
    }
}

/**
 * The one thing Twinlit remembers between launches: where you last looked.
 *
 * SharedPreferences lives in the app's private data directory, so this needs no
 * permission and never leaves the device. The date is deliberately NOT persisted;
 * opening the app tomorrow should show tomorrow, not the day you last happened to
 * be curious about.
 *
 * Coordinates are stored as strings rather than through `putFloat`, because a
 * float carries about seven significant digits and the catalogue's coordinates
 * have up to eight (a longitude such as -122.4194 plus its sign). Rounding a
 * stored place on every launch would move it by tens of metres for no reason.
 * `Double.toString` and `String.toDoubleOrNull` are both locale-independent, so
 * a Norwegian device does not write a value a British one cannot read back.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: InputMode
        get() {
            val stored = prefs.getString(KEY_MODE, null) ?: return InputMode.CITY
            // Read by name with a fallback rather than by ordinal: renaming or
            // reordering a constant in a later version must not crash, or worse
            // silently mean something else, on someone's existing install.
            return InputMode.entries.firstOrNull { it.name == stored } ?: InputMode.CITY
        }
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    /**
     * The last place the user looked at, or null on a fresh install.
     *
     * A partially written entry (label present, latitude unparseable) reads back
     * as null rather than as a place at 0,0 in the Gulf of Guinea, which is what
     * a defaulting `getFloat` would have produced.
     */
    var place: Place?
        get() = PlaceCodec.decode(prefs.all)
        set(value) = prefs.edit {
            val stored = if (value == null) emptyMap() else PlaceCodec.encode(value)
            // Every key the new place does not write is removed, so that clearing a name (or the
            // place) leaves nothing of the old one behind.
            for (key in PlaceCodec.KEYS) if (key !in stored) remove(key)
            for ((key, v) in stored) {
                when (v) {
                    is String -> putString(key, v)
                    is Boolean -> putBoolean(key, v)
                }
            }
        }

    /**
     * What the user chose in the Appearance sheet (D7: applied at once, kept here). Stored by
     * key, never by ordinal; a missing or unknown value reads as [Appearance.AUTO], the default
     * (see [Appearance.fromKey]).
     */
    var appearance: Appearance
        get() = Appearance.fromKey(prefs.getString(KEY_APPEARANCE, null))
        set(value) = prefs.edit { putString(KEY_APPEARANCE, value.key) }

    /**
     * Which revision of the first-run notice this install has already seen, 0 on a
     * fresh install.
     *
     * An integer rather than a boolean so the notice can be shown again if its text
     * ever changes materially; the comparison itself lives in
     * `ui/FirstRunNotice.kt`, next to the copy it is about, and the constant that
     * gets written here is that file's version rather than a literal.
     *
     * Stored in the same file as the place. That file is NOT backed up (see
     * [PREFS_NAME]), so a restored or new phone shows the notice once more and starts
     * with no place, which keeps the footer's "Your places ... never leave this device" true.
     */
    var firstRunNoticeSeenVersion: Int
        get() = prefs.getInt(KEY_NOTICE_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_NOTICE_VERSION, value) }

    companion object {
        /**
         * The place the user last looked at, or null when there is none or it cannot be read (a
         * device that is still locked after a restart keeps the file out of reach). For the alarm
         * service, which names a typed place by it and must ring whatever happens here.
         */
        fun placeOrNull(context: Context): Place? = try {
            Settings(context).place
        } catch (e: RuntimeException) {
            null
        }

        /**
         * The preference file name, which is also the filename inside the app's
         * `shared_prefs` directory. It is not backed up: `res/xml/backup_rules.xml`
         * and `res/xml/data_extraction_rules.xml` never include it.
         */
        const val PREFS_NAME = "twinlit"

        private const val KEY_MODE = "input_mode"
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_NOTICE_VERSION = "first_run_notice_seen_version"
    }
}
