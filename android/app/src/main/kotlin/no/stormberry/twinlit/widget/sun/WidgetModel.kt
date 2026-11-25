package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.solar.DayKind
import no.stormberry.twinlit.solar.PlaceDay
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.solar.SunCalc
import no.stormberry.twinlit.solar.SunCrossings
import no.stormberry.twinlit.solar.computeSunTimes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The countdown line (1.1.9): what the medium and large widgets show under the times.
 * Pure data; [WidgetText] turns it into words.
 */
sealed interface CountdownLine {
    /** No target (only within a fraction of a degree of a pole): the line is hidden. */
    data object Hidden : CountdownLine

    /** More than 24 hours away: "Next sunrise Tue 16 Feb". */
    data class DateForm(val up: Boolean, val target: Instant) : CountdownLine

    /** The Chronometer, counting down to [target]: "Daylight left 2:48:50", "Sunrise in 8:49:58". */
    data class Ticking(val up: Boolean, val target: Instant) : CountdownLine

    /** Static, the target is on the city's today: "Sunset at 15:48". */
    data class AtToday(val up: Boolean, val target: Instant) : CountdownLine

    /** Static, the target is on the city's tomorrow: "Sunrise tomorrow at 08:19". */
    data class AtTomorrow(val up: Boolean, val target: Instant) : CountdownLine
}

/**
 * One twilight or golden hour row of the large widget (1.1.9). These are COMPUTED values that the
 * app itself does not show; their state depends on the noon and nadir altitudes alone, never on
 * the [DayKind], so a midnight sun day and a white night with the same sky get the same words.
 */
sealed interface Band {
    /** At least one crossing: the morning and evening instants, either may be missing. */
    data class Times(val morning: Instant?, val evening: Instant?) : Band

    /** Twilight: never darker than its angle, and the sun rises or never sets. "All night". */
    data object AllNight : Band

    /**
     * Twilight: polar night with the whole day inside this twilight band. Golden hour: the sun
     * never sets and never climbs above 6 degrees. "All day and night".
     */
    data object AllDayAndNight : Band

    /** Golden hour: the sun is up but never above 6 degrees. "All day". */
    data object AllDay : Band

    /** No crossing and none of the above: two en dashes. */
    data object None : Band
}

/**
 * Everything the three widget sizes show, computed once per refresh (1.1.9). Pure.
 *
 * @property today the date in the place's zone; the times rows are the times card for it.
 * @property noonDate the local date of today's solar noon. A twilight or golden hour time on
 *   another local date than this one gets "+1" or a minus one marker. Using the noon's date, not
 *   [today], keeps places far east of their zone's meridian (Apia, Kiritimati) free of markers on
 *   every day, consistent with their unmarked sunrise and sunset.
 * @property up whether the sun is up now.
 * @property target the next sunset when up, the next sunrise when down; null near a pole only.
 * @property zoneDiffers whether the place's clock differs from the phone's (offset now, or next
 *   transition), the condition for "Times shown in {zone}".
 */
data class WidgetState(
    val now: Instant,
    val placeLabel: String,
    val zone: ZoneId,
    val today: LocalDate,
    val kind: DayKind,
    val sunrise: Instant?,
    val solarNoon: Instant?,
    val sunset: Instant?,
    val up: Boolean,
    val target: Instant?,
    val line: CountdownLine,
    val noonDate: LocalDate,
    val golden: Band,
    val civil: Band,
    val nautical: Band,
    val astronomical: Band,
    val zoneDiffers: Boolean,
)

object WidgetModel {

    /** The sunrise and sunset altitude, in degrees (SolarEvent.SUNRISE). */
    private const val HORIZON_DEG = -0.833

    /** The golden hour altitude, in degrees (SolarEvent.GOLDEN_HOUR). */
    private const val GOLDEN_DEG = 6.0

    /**
     * The state at [now] for [place] (null: no place chosen yet, which the widgets show as
     * "Choose a place in Twinlit to start the widget."). [tickingAllowed] is false while the app is
     * background restricted, always on Android 9 to 11 (API 28 to 30, where Battery Saver can hold
     * the handover alarm), or when the phone would not print `1:02:03`; the line is then static.
     */
    fun build(now: Instant, place: Place?, phoneZone: ZoneId, tickingAllowed: Boolean): WidgetState? {
        if (place == null) return null
        val zone = PlaceDay.zoneOf(place.zoneId, phoneZone)
        val today = PlaceDay.today(zone, now)
        // Card parity: the times rows are exactly what the Sun times card computes.
        val card = computeSunTimes(place, today)
        val lat = place.latDeg
        val lon = place.lonDeg
        val up = SunCrossings.isUp(now, lat, lon, zone)
        val target = SunCrossings.next(if (up) SolarEvent.SUNSET else SolarEvent.SUNRISE, now, lat, lon, zone)
        val times = SunCalc.times(today, lat, lon)
        val alt = SunCrossings.altitudes(today, lat, lon)
        return WidgetState(
            now = now,
            placeLabel = WidgetText.placeName(place),
            zone = zone,
            today = today,
            kind = card.kind,
            sunrise = card.sunrise,
            solarNoon = card.solarNoon,
            sunset = card.sunset,
            up = up,
            target = target,
            line = countdownLine(now, today, zone, up, target, tickingAllowed),
            noonDate = card.solarNoon?.atZone(zone)?.toLocalDate() ?: today,
            golden = golden(times[SolarEvent.GOLDEN_HOUR_END], times[SolarEvent.GOLDEN_HOUR], alt),
            civil = twilight(times[SolarEvent.DAWN], times[SolarEvent.DUSK], -6.0, alt),
            nautical = twilight(times[SolarEvent.NAUTICAL_DAWN], times[SolarEvent.NAUTICAL_DUSK], -12.0, alt),
            astronomical = twilight(times[SolarEvent.NIGHT_END], times[SolarEvent.NIGHT], -18.0, alt),
            zoneDiffers = zoneDiffers(zone, phoneZone, now),
        )
    }

    /**
     * The countdown line: hidden with no target; a date beyond 24 hours; the Chronometer
     * while more than [WidgetSchedule.TICK_MIN] is left and ticking is allowed; otherwise the
     * static line, which names a fixed instant and so is never false. Durations are instant
     * differences, so a daylight saving change cannot skew them.
     */
    fun countdownLine(
        now: Instant,
        today: LocalDate,
        zone: ZoneId,
        up: Boolean,
        target: Instant?,
        tickingAllowed: Boolean,
    ): CountdownLine {
        if (target == null) return CountdownLine.Hidden
        val left = Duration.between(now, target)
        return when {
            left > WidgetSchedule.DATE_FORM_AFTER -> CountdownLine.DateForm(up, target)
            tickingAllowed && left > WidgetSchedule.TICK_MIN -> CountdownLine.Ticking(up, target)
            target.atZone(zone).toLocalDate() == today -> CountdownLine.AtToday(up, target)
            else -> CountdownLine.AtTomorrow(up, target)
        }
    }

    /** A twilight row at [angleDeg]. */
    fun twilight(morning: Instant?, evening: Instant?, angleDeg: Double, alt: SunCrossings.Altitudes): Band = when {
        morning != null || evening != null -> Band.Times(morning, evening)
        alt.nadirDeg > angleDeg && alt.noonDeg > HORIZON_DEG -> Band.AllNight
        alt.nadirDeg > angleDeg -> Band.AllDayAndNight
        else -> Band.None
    }

    /**
     * The golden hour row: "until" the morning end, "from" the evening start. With no
     * crossing and the sun up but never above 6 degrees: "All day", or "All day and night" when
     * the sun never sets either (89.99 N in late March).
     */
    fun golden(morningEnd: Instant?, eveningStart: Instant?, alt: SunCrossings.Altitudes): Band = when {
        morningEnd != null || eveningStart != null -> Band.Times(morningEnd, eveningStart)
        alt.noonDeg > HORIZON_DEG && alt.noonDeg < GOLDEN_DEG ->
            if (alt.nadirDeg > HORIZON_DEG) Band.AllDayAndNight else Band.AllDay
        else -> Band.None
    }

    /**
     * Whether the place's clock differs from the phone's: a different offset now, or a different
     * next transition. Not `ZoneRules.equals`, which compares whole transition histories and so
     * depends on the tzdb build; `Arctic/Longyearbyen` on a `Europe/Oslo` phone is the same clock.
     */
    fun zoneDiffers(zone: ZoneId, phone: ZoneId, now: Instant): Boolean {
        val a = zone.rules
        val b = phone.rules
        if (a.getOffset(now) != b.getOffset(now)) return true
        val ta = a.nextTransition(now)
        val tb = b.nextTransition(now)
        return ta?.instant != tb?.instant || ta?.offsetAfter != tb?.offsetAfter
    }
}
