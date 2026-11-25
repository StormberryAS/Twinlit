package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonCalc
import no.stormberry.twinlit.lunar.MoonPhase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The instant of a moon event on a local date: the moon half of the occurrence engine's step 1
 * (SPEC 7.4, D16, D17). Pure; no Android, no clock.
 *
 * A moon event, unlike a solar one, can be absent on a date, and there is no lunar equivalent of
 * solar noon to stand in for it. So [instantOn] returns null for such a date and the engine skips
 * the day: no fallback, no one-step "anchor day correction" (that correction relies on a sun event
 * never being null, and on a moon event it can lose a day).
 *
 * ### Daily events: moonrise, moonset, the moon at its highest
 *
 * A moonrise or a moonset is read with `MoonCalc.crossingsIn` over the place's local day, which is
 * the Moon tab's window and keeps EVERY crossing, so a day with two moonrises (Tromso, June) gives
 * both. The transit comes from `MoonCalc.times`, whose half-open window gives at most one per day.
 * Each result is checked once against the zone (defensive: it should always pass); an instant that
 * renders on another local date is dropped rather than filed under the wrong day.
 *
 * ### Monthly events: the full moon night and the new moon night (R5, Marcos's D7, DECISIONS question 8)
 *
 * 1. The moment is the exact New Moon or Full Moon of the website's algorithm (the 12:00 UTC sample
 *    grid, the bracket tests and `MoonPhase.crossing`, as `MoonPhase.nextLunations`), found by
 *    `MoonPhase.lunationsBracketed`.
 * 2. The night is the local date of (moment minus 12 hours) in the rule's zone (`MoonPhase.nightOf`).
 * 3. The ring is 22:00 local mean solar time in that night: MoonApp's hour (22:00 UTC minus `lon / 15`
 *    hours, whole seconds), on the first day whose 22:00 lies at or after 12 hours past the start of
 *    the night ([nightInstant]). It can render just after midnight (Madrid in summer, Urumqi on
 *    Asia/Shanghai).
 * 4. The occurrence is filed under the night, so the engine's `anchorDate` is the night also when the
 *    ring renders on the next date. `MoonNightFilingTest` and `FullMoonNightParityTest` pin this, and
 *    the Moon tab's phase card names the same night (`MoonPhase.nextNights`).
 *
 * A monthly date never has two occurrences (lunations of one kind are 29.5 days apart).
 */
internal object MoonAnchors {

    /**
     * The first instant of [event] that belongs to [localDate] in [zone] at ([latDeg], [lonDeg]), or
     * null when the event does not occur on that date there. See [instantsOn] for a date with two.
     */
    fun instantOn(event: LunarEvent, localDate: LocalDate, latDeg: Double, lonDeg: Double, zone: ZoneId): Instant? =
        instantsOn(event, localDate, latDeg, lonDeg, zone).firstOrNull()

    /**
     * Every instant of [event] that belongs to [localDate] in [zone] at ([latDeg], [lonDeg]), in time
     * order; empty when the event does not occur on that date there.
     *
     * Two or more only for a moonrise or a moonset at high latitude (fix round 2): a local day can
     * hold two rises or two sets (Tromso, 2026-06-11: moonrises at 00:11 and 23:15), and an alarm
     * that kept one lost the other and could stay silent for days. The scan is
     * [MoonCalc.crossingsIn] over the local day, the Moon tab's own window.
     */
    fun instantsOn(event: LunarEvent, localDate: LocalDate, latDeg: Double, lonDeg: Double, zone: ZoneId): List<Instant> =
        when (event) {
            LunarEvent.MOONRISE, LunarEvent.MOONSET ->
                MoonCalc.crossingsIn(
                    start = localDate.atStartOfDay(zone).toInstant(),
                    hours = MoonCalc.dayHours(localDate, zone),
                    latDeg = latDeg,
                    lonDeg = lonDeg,
                )
                    .filter { it.first == event }
                    .map { it.second }
                    .filter { it.atZone(zone).toLocalDate() == localDate }

            LunarEvent.LUNAR_TRANSIT ->
                listOfNotNull(
                    MoonCalc.times(localDate, latDeg, lonDeg, zone)[event]
                        ?.takeIf { it.atZone(zone).toLocalDate() == localDate },
                )

            // R5 (D7): the night is the local date of (moment minus 12 h), and the occurrence is filed
            // under it. A night N holds moments from 12 h after the start of N to 12 h after the start of
            // N + 1: in UTC from N - 1 at 22:00 (UTC+14) to N + 2 at 00:00 (UTC-12), so the brackets that
            // end at 12:00 UTC on N, N + 1 and N + 2 hold every candidate.
            LunarEvent.FULL_MOON, LunarEvent.NEW_MOON ->
                if (MoonPhase.lunationsBracketed(localDate, localDate.plusDays(2))
                        .any { it.event == event && MoonPhase.nightOf(it.at, zone) == localDate }
                ) listOf(nightInstant(localDate, lonDeg, zone)) else emptyList()
        }

    /**
     * When a full or new moon night alarm rings for [night] (R5, D7): MoonApp's hour, 22:00 local mean
     * solar time (22:00 UTC minus `lon / 15` hours, truncated to whole seconds as MoonApp's
     * `eveningLocal`), on the first day whose 22:00 lies at or after 12 hours past the start of [night]
     * in [zone]. So the ring lies in the night the Moon tab's card names: 22:29 on the night's own date
     * in Kiritimati, about 00:10 after midnight in Madrid in summer and in Urumqi on Asia/Shanghai.
     */
    internal fun nightInstant(night: LocalDate, lonDeg: Double, zone: ZoneId): Instant {
        val start = night.atStartOfDay(zone).toInstant().plusSeconds(43_200L)
        val base = night.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plusSeconds(79_200L + (-lonDeg / 15.0 * 3600.0).toLong())
        val days = Math.floorDiv(Duration.between(base, start).seconds + 86_399L, 86_400L)
        return base.plusSeconds(days * 86_400L)
    }
}
