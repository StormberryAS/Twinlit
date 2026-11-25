package no.stormberry.twinlit.ui.moon

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.PlaceDisplay
import no.stormberry.twinlit.lunar.MoonPhase
import no.stormberry.twinlit.solar.PlaceDay
import java.time.ZoneId

/**
 * What the Moon tab reads from the shared place (SPEC 6.2): the five fields MoonApp's screen read
 * from its own `ChosenPlace` (`label`, `latDeg`, `lonDeg`, `zoneId`, `name`), taken from the one
 * [Place] of the app. There is no default place here: MoonApp fell back to Oslo when it had
 * nothing stored, Twinlit shows an empty state instead (D12), so a [MoonPlace] only exists for a
 * place that was chosen.
 *
 * Pure, so MoonTabSourceTest can pin the rules without a device.
 */
internal data class MoonPlace(
    val label: String,
    val latDeg: Double,
    val lonDeg: Double,
    val zoneId: String,
    val name: String,
    /** Whether the user typed the place as coordinates (so it shows its own name, W5; it is named in the picker). */
    val isTyped: Boolean,
) {
    /** The zone every day boundary and "today" of this tab uses: the place's own (UTC for an id the device does not know, as `PlaceDay`), never the phone's. */
    val zone: ZoneId get() = PlaceDay.zoneOf(zoneId)

    /**
     * The name on the phase card (W5): a typed place shows its own name, whole, or "Your
     * coordinates" when it has none; a catalogue place shows its label.
     */
    val title: String
        get() = if (isTyped) name.ifEmpty { PlaceDisplay.YOUR_COORDINATES } else label

    /** The line under the name: MoonApp's "59.9139°, 10.7522°" (degree signs, no hemisphere letters). */
    val coordinateLine: String get() = MoonPhase.formatCoordinates(latDeg, lonDeg)

    companion object {
        fun of(place: Place): MoonPlace = MoonPlace(
            label = place.label,
            latDeg = place.latDeg,
            lonDeg = place.lonDeg,
            zoneId = place.zoneId,
            name = PlaceDisplay.givenName(place),
            isTyped = PlaceDisplay.isTyped(place),
        )
    }
}
