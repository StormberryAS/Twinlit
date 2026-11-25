package no.stormberry.twinlit.solar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI

/**
 * Is the sun up now, and when does it next rise or set (1.1.9, the home screen widgets).
 *
 * Pure. It shares no code with `alarm/OccurrenceEngine` on purpose, so the widgets stay
 * independent of the alarm runtime, and it has no solar-noon fallback: a missing crossing
 * is a polar day, and the caller shows a polar label.
 *
 * Every day is `SunCalc.times(date, ...)`, the call the times card makes, anchored at 12:00 UTC of
 * the date. In places far from their zone's meridian (Apia, Kiritimati) that call can return an
 * event on the adjacent local date, which is why [next] looks at the following day as well and
 * takes the earlier crossing.
 */
object SunCrossings {

    /**
     * Whether the sun is up at [now] for a place in [zone]: decided first by the city's [DayKind]
     * for today, then by the most recent sunrise or sunset at or before [now] (today's and
     * yesterday's). An instant equal to a sunrise counts as up, one equal to a sunset as down.
     *
     * Ordering by the latest event, not by "the next crossing of either kind", keeps the answer
     * right on a day where SunCalc reports a "sunrise" before the first real sunset (Longyearbyen,
     * 25 August 2026, the last day of the midnight sun): the sun has not set for months, so it is
     * up until that sunset.
     */
    fun isUp(now: Instant, latDeg: Double, lonDeg: Double, zone: ZoneId): Boolean {
        val today = PlaceDay.today(zone, now)
        return when (DayKindCalculator.of(today, latDeg, lonDeg)) {
            DayKind.MIDNIGHT_SUN -> true
            DayKind.POLAR_NIGHT -> false
            DayKind.NORMAL -> {
                var latest: Instant? = null
                var latestIsRise = false
                for (day in listOf(today.minusDays(1), today)) {
                    val times = SunCalc.times(day, latDeg, lonDeg)
                    for ((event, isRise) in listOf(SolarEvent.SUNRISE to true, SolarEvent.SUNSET to false)) {
                        val t = times[event] ?: continue
                        if (!t.isAfter(now) && (latest == null || t.isAfter(latest))) {
                            latest = t
                            latestIsRise = isRise
                        }
                    }
                }
                if (latest != null) {
                    latestIsRise
                } else {
                    DayKindCalculator.of(today.minusDays(1), latDeg, lonDeg) == DayKind.MIDNIGHT_SUN
                }
            }
        }
    }

    /**
     * The first [event] strictly after [after], searching from yesterday (the place's date) up to
     * [maxDays] ahead, or null when none is found (only within a fraction of a degree of a pole).
     * In polar night that walk can cover about 110 days (Longyearbyen, 21 December to 16 February).
     */
    fun next(
        event: SolarEvent,
        after: Instant,
        latDeg: Double,
        lonDeg: Double,
        zone: ZoneId,
        maxDays: Int = MAX_DAYS,
    ): Instant? {
        val today = PlaceDay.today(zone, after)
        for (i in -1L..maxDays.toLong()) {
            val day = today.plusDays(i)
            val t = SunCalc.times(day, latDeg, lonDeg)[event] ?: continue
            if (!t.isAfter(after)) continue
            // The adjacent-date guard: the next day's call can hold an earlier crossing.
            val t2 = SunCalc.times(day.plusDays(1), latDeg, lonDeg)[event]
            return if (t2 != null && t2.isAfter(after) && t2.isBefore(t)) t2 else t
        }
        return null
    }

    /** The sun's altitude, in degrees, at a day's solar noon and at its nadir. */
    data class Altitudes(val noonDeg: Double, val nadirDeg: Double)

    /**
     * The altitudes at [date]'s `SOLAR_NOON` and `NADIR`, the two samples [DayKindCalculator]
     * uses. The widget's twilight and golden hour states depend on these two numbers alone.
     */
    fun altitudes(date: LocalDate, latDeg: Double, lonDeg: Double): Altitudes {
        val times = SunCalc.times(date, latDeg, lonDeg)
        fun alt(event: SolarEvent): Double =
            times[event]?.let { SunCalc.position(it, latDeg, lonDeg).altitudeRad * 180.0 / PI } ?: Double.NaN
        return Altitudes(alt(SolarEvent.SOLAR_NOON), alt(SolarEvent.NADIR))
    }

    /** How far [next] looks ahead. A polar night or a midnight sun never lasts this long. */
    const val MAX_DAYS = 400
}
