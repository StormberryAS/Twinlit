package no.stormberry.twinlit.widget.moon

import no.stormberry.twinlit.lunar.LunarDayKind
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonCalc
import no.stormberry.twinlit.lunar.MoonPhase
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.solar.PlaceDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the three home screen widgets show, worked out once per refresh and shared by all of
 * them (1.0.8). Pure: no Android import, so WidgetModelTest runs it on the JVM.
 *
 * Every figure is the app's own, from the functions the times card calls, for TODAY IN THE
 * PLACE'S ZONE (decision D3): illumination and phase at 12:00 UTC of that date as on the card
 * (D1, [MoonPhase.noonUtc]), so the widget and the card for today always print the same
 * percentage; rise, transit and set from [MoonCalc.times] in the place's zone, never UTC (the
 * lesson of the web day-boundary bug); the polar case from [MoonCalc.dayKind]; and the four
 * principal phases from [MoonPhase.nextPhases].
 */
data class WidgetState(
    /**
     * The city only, from [WidgetText.placeName]; never typed coordinates: a place typed as
     * coordinates is the user's name for it, whole, or "Your coordinates".
     */
    val placeLabel: String,
    val zone: ZoneId,
    /** The place's zone is not the phone's, so the times are not on the phone's clock. */
    val zoneDiffers: Boolean,
    val today: LocalDate,
    val fraction: Double,
    val phase: Double,
    val percent: Int,
    val phaseName: String,
    /** Which side of the disc is lit as the place sees it: waxing is on the right in the north. */
    val litOnRight: Boolean,
    val dayKind: LunarDayKind,
    val rise: Instant?,
    /** Null on an always-down day, where the card hides it too, and on a day with no culmination. */
    val transit: Instant?,
    val set: Instant?,
    val phases: List<MoonPhase.PhaseEvent>,
)

object WidgetModel {

    /**
     * The state at [now] for [place], the one place of Twinlit (SPEC 8.3). The zone is the place's
     * own ([PlaceDay.zoneOf]: UTC for an id this device does not know, as the Moon tab and the Sun
     * widgets do), never the phone's; [deviceZone] only tells whether the times are on the phone's
     * clock ([WidgetState.zoneDiffers]). The caller handles "no place yet" (placeholder) before
     * it gets here; MoonApp fell back to Oslo, Twinlit does not.
     */
    fun build(now: Instant, place: Place, deviceZone: ZoneId): WidgetState {
        val latDeg = place.latDeg
        val lonDeg = place.lonDeg
        val zone = PlaceDay.zoneOf(place.zoneId, deviceZone)
        // The same "today" the Moon tab opens on (PlaceDay), so the app and the widget agree.
        val today = PlaceDay.today(zone, now)
        val illum = MoonCalc.illumination(MoonPhase.noonUtc(today))
        val times = MoonCalc.times(today, latDeg, lonDeg, zone)
        val kind = MoonCalc.dayKind(today, latDeg, lonDeg, zone)
        return WidgetState(
            placeLabel = WidgetText.placeName(place),
            zone = zone,
            zoneDiffers = zone.id != deviceZone.id,
            today = today,
            fraction = illum.fraction,
            phase = illum.phase,
            // Math.round, as the card (app.js:357).
            percent = Math.round(illum.fraction * 100).toInt(),
            phaseName = MoonPhase.name(illum.phase),
            litOnRight = MoonDisc.litOnRight(illum.phase, latDeg),
            dayKind = kind,
            rise = times[LunarEvent.MOONRISE],
            transit = if (kind == LunarDayKind.ALWAYS_DOWN) null else times[LunarEvent.LUNAR_TRANSIT],
            set = times[LunarEvent.MOONSET],
            phases = MoonPhase.nextPhases(today, zone),
        )
    }
}
