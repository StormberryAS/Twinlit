package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonPhase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The full and new moon nights and the instant of the ring, written out in the test tree so that the
 * filing logic of `MoonAnchors` and the Moon tab's card are checked against something they do not call:
 * no `MoonAnchors`, no `MoonPhase.lunationsBracketed`, no `MoonPhase.nightOf`.
 *
 * **The oracle changed in R5 by the decided rule (Marcos's D7, DECISIONS question 8).** Until R5 it
 * was MoonApp's fraction rule (the UTC date whose noon illumination peaks, `utcDates`, and
 * `instant(utcDate, lon)`). Now:
 *  - [moments]: the exact moments, the chain of `MoonPhase.nextLunations(date, UTC)` (the website's
 *    algorithm);
 *  - [night]: the local date of (moment minus 12 hours) in the zone;
 *  - [instant]: 22:00 UTC minus `lon / 15` hours on the UTC date of the night, stepped by whole days
 *    (a loop, back or forward) to the first instant at or after 12 hours past the start of the night in the zone.
 */
internal object MoonNightOracle {

    /** The exact moments of [event] (FULL_MOON or NEW_MOON) whose UTC date lies in `from` to `until` (exclusive). */
    fun moments(event: LunarEvent, from: LocalDate, until: LocalDate): List<Instant> {
        require(event == LunarEvent.FULL_MOON || event == LunarEvent.NEW_MOON)
        val out = mutableListOf<Instant>()
        var date = from
        while (date.isBefore(until)) {
            val l = MoonPhase.nextLunations(date, ZoneOffset.UTC)
            val at = (if (event == LunarEvent.FULL_MOON) l.fullAt else l.newAt) ?: break
            val utc = at.atZone(ZoneOffset.UTC).toLocalDate()
            if (!utc.isBefore(until)) break
            out += at
            date = utc.plusDays(1)
        }
        return out.distinct()
    }

    /** The night a moment at [at] belongs to in [zone]: the local date of the moment minus 12 hours. */
    fun night(at: Instant, zone: ZoneId): LocalDate = at.minusSeconds(12 * 3600).atZone(zone).toLocalDate()

    /** The nights of [event] in [zone] that lie in `from` to `to` (inclusive), in order. */
    fun nights(event: LunarEvent, zone: ZoneId, from: LocalDate, to: LocalDate): List<LocalDate> =
        moments(event, from.minusDays(5), to.plusDays(5)).map { night(it, zone) }.filter { it in from..to }.distinct()

    /**
     * The ring of [night] at longitude [lonDeg]: 22:00 local mean solar time, the first one at or after
     * 12 hours past the start of the night in [zone].
     */
    fun instant(night: LocalDate, lonDeg: Double, zone: ZoneId): Instant {
        val start = night.atStartOfDay(zone).toInstant().plusSeconds(12 * 3600)
        var at = night.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plusSeconds(22L * 3600L + (-lonDeg / 15.0 * 3600.0).toLong())
        while (!at.minusSeconds(86_400L).isBefore(start)) at = at.minusSeconds(86_400L)
        while (at.isBefore(start)) at = at.plusSeconds(86_400L)
        return at
    }
}
