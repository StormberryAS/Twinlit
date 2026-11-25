package no.stormberry.twinlit.theme

import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.solar.SunCalc
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Auto schedule (approved by Marcos, 2026-11-17; SPEC 4.4). It follows SOLAR NOON at the chosen
 * place, not sunrise or sunset, so it exists at every latitude, in midnight sun and in polar night.
 *
 *  - Sun: within 5 h of solar noon (noon minus 5 h up to, not including, noon plus 5 h);
 *  - Twilight: from 5 h up to, not including, 7 h from solar noon, on each side;
 *  - Moon: 7 h or more from solar noon, the rest of the day.
 *
 * Exactly 5 h 00 min 00 s from noon is already Twilight; exactly 7 h 00 min 00 s is already Moon.
 * Computed offline with the same SunCalc as the Sun tab; no GPS, no zone needed. Pure and with no
 * Compose, so AutoScheduleTest runs on the JVM.
 */
object AutoSchedule {

    /** The Sun phase lasts to this distance from solar noon (exclusive). */
    val SUN_HALF_WIDTH: Duration = Duration.ofHours(5)

    /** The Twilight phase lasts to this distance from solar noon (exclusive). */
    val TWILIGHT_HALF_WIDTH: Duration = Duration.ofHours(7)

    /**
     * The solar noons (UTC date d anchored at 12:00 UTC, as the Sun tab does) of the dates from
     * [first] to [last] days around [now]'s UTC date. SunCalc never returns a null solar noon.
     */
    private fun noons(now: Instant, latDeg: Double, lonDeg: Double, first: Long, last: Long): List<Instant> {
        val d0 = now.atZone(ZoneOffset.UTC).toLocalDate()
        return (first..last).mapNotNull { offset ->
            SunCalc.times(d0.plusDays(offset), latDeg, lonDeg)[SolarEvent.SOLAR_NOON]
        }
    }

    /** The theme in effect at [now] at the place ([latDeg], [lonDeg]). */
    fun phaseAt(now: Instant, latDeg: Double, lonDeg: Double): Theme {
        val noons = noons(now, latDeg, lonDeg, -1, 1)
        // The nearest solar noon; on a tie the earlier one (minByOrNull keeps the first minimum).
        val nearest = noons.minByOrNull { Duration.between(it, now).abs() } ?: return Theme.MOON
        val delta = Duration.between(nearest, now).abs()
        return when {
            delta < SUN_HALF_WIDTH -> Theme.SUN
            delta < TWILIGHT_HALF_WIDTH -> Theme.TWILIGHT
            else -> Theme.MOON
        }
    }

    /**
     * The first instant after [now] at which [phaseAt] gives another answer: the first instant of
     * the next phase. The rule gives the boundary itself to the later phase on the afternoon side
     * (5 h after noon is Twilight, 7 h after is Moon) and to the earlier phase on the morning
     * side (5 h before noon is Twilight, 7 h before is Moon), because it measures the distance to
     * the nearest noon. So the morning boundaries are one millisecond later than the exact
     * "n minus 7 h" and "n minus 5 h" of the specification (SPEC 4.4 names the instants, not the
     * side that owns them); the afternoon ones are exact.
     */
    fun nextChange(now: Instant, latDeg: Double, lonDeg: Double): Instant {
        val oneMs = Duration.ofMillis(1)
        val boundaries = noons(now, latDeg, lonDeg, -1, 2).flatMap { n ->
            listOf(
                n.minus(TWILIGHT_HALF_WIDTH).plus(oneMs), n.minus(SUN_HALF_WIDTH).plus(oneMs),
                n.plus(SUN_HALF_WIDTH), n.plus(TWILIGHT_HALF_WIDTH),
            )
        }
        // A boundary exists in every 24 hours, so this is never empty; the fallback keeps the
        // function total for a degenerate catalogue.
        return boundaries.filter { it > now }.minOrNull() ?: now.plus(Duration.ofMinutes(1))
    }
}
