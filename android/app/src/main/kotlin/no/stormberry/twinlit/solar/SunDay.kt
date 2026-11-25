package no.stormberry.twinlit.solar

import no.stormberry.twinlit.data.Place
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToLong

/*
 * The sun times card's model: one place on one date, as the Sun times screen shows it.
 *
 * Moved unchanged out of `ui/SunTimesScreen.kt` in 1.1.9 so that the home screen widgets
 * (`widget/`) can compute exactly what the card shows without the solar layer depending on `ui`.
 * Pure: no Compose, no Android, no state.
 */

/** One place on one date, resolved into what the screen renders. */
internal data class SunTimesResult(
    val date: LocalDate,
    val latDeg: Double,
    val lonDeg: Double,
    val zone: ZoneId,
    val kind: DayKind,
    val sunrise: Instant?,
    val solarNoon: Instant?,
    val sunset: Instant?,
    /** Sunset minus sunrise in whole minutes, or null when either is absent. */
    val dayLengthMinutes: Long?,
)

internal fun computeSunTimes(place: Place, date: LocalDate): SunTimesResult {
    val times = SunCalc.times(date, place.latDeg, place.lonDeg)
    val sunrise = times[SolarEvent.SUNRISE]
    val sunset = times[SolarEvent.SUNSET]

    // DayKindCalculator recomputes the same event table internally. That is a few
    // microseconds of duplicated arithmetic, and it buys the guarantee that the
    // classifier the web app shares stays the single authority on what a polar day
    // is. Reimplementing the test here against the map above would be the exact
    // drift its KDoc warns about.
    val kind = DayKindCalculator.of(date, place.latDeg, place.lonDeg)

    val dayLengthMinutes = if (sunrise != null && sunset != null) {
        // app.js section 9.6: Math.round(ms / 60000), then split into hours and
        // minutes. Rounding rather than truncating, so a 12h 29m 40s day reads as
        // 12h 30m on both surfaces.
        ((sunset.toEpochMilli() - sunrise.toEpochMilli()) / 60_000.0).roundToLong()
    } else {
        null
    }

    return SunTimesResult(
        date = date,
        latDeg = place.latDeg,
        lonDeg = place.lonDeg,
        zone = resolveZone(place.zoneId),
        kind = kind,
        sunrise = sunrise,
        solarNoon = times[SolarEvent.SOLAR_NOON],
        sunset = sunset,
        dayLengthMinutes = dayLengthMinutes,
    )
}

/**
 * Turns a stored IANA id into a zone, falling back to UTC.
 *
 * The fallback is not decoration. Zone ids are persisted across app versions and
 * the tzdb underneath them is not frozen: a link retired between releases would
 * otherwise throw on the way out of SharedPreferences and take the screen with
 * it. UTC is wrong but visible, and the timezone row says so.
 */
internal fun resolveZone(id: String): ZoneId =
    try {
        ZoneId.of(id)
    } catch (_: DateTimeException) {
        ZoneOffset.UTC
    }
