package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.lunar.LunarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * **A full or new moon night is filed under its night, whatever the zone (SPEC 7.4; R5, Marcos's D7).**
 *
 * The night is the local date of the exact moment minus 12 hours; the ring is 22:00 local mean solar
 * time in it, and it can render on the next local date (Madrid in summer and Urumqi on Asia/Shanghai,
 * about 00:10; in Pacific/Kiritimati it renders on the night's own date, 22:29, which at HEAD `e1b7ed1`
 * was the next date, the D17 case). The occurrence is filed under the night: `anchorDate` is the night.
 * Per night N: `occurrenceFor(rule, N)` is not null with the oracle instant, `occurrenceFor` on N - 2,
 * N - 1, N + 1 and N + 2 is null, and `nextOccurrence` from the start of N - 1 returns it.
 *
 * ### Where the expectation comes from
 *
 * [MoonNightOracle] (changed in R5 by the decided rule): the moments from the website's algorithm, the
 * night as the date of the moment minus 12 hours and the instant written out, not read from
 * [MoonAnchors].
 */
class MoonNightFilingTest {

    private class Place(val name: String, val lat: Double, val lon: Double, val zone: String)

    private val kiritimati = Place("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati")
    private val madrid = Place("Madrid", 40.4168, -3.7038, "Europe/Madrid")
    private val urumqi = Place("Urumqi", 43.8256, 87.6168, "Asia/Shanghai")

    /** One night: the event, the night's date and the ring's instant. */
    private class Night(val event: LunarEvent, val night: LocalDate, val instant: Instant)

    /** Every full and new moon night in `from` to `until` (exclusive), by the oracle. */
    private fun nights(place: Place, from: LocalDate, until: LocalDate): List<Night> {
        val zone = ZoneId.of(place.zone)
        return listOf(LunarEvent.FULL_MOON, LunarEvent.NEW_MOON).flatMap { event ->
            MoonNightOracle.nights(event, zone, from, until.minusDays(1)).map {
                Night(event, it, MoonNightOracle.instant(it, place.lon, zone))
            }
        }.sortedBy { it.instant }
    }

    private fun ruleFor(place: Place, event: LunarEvent): AlarmRule =
        testMoonRule(event = event, latDeg = place.lat, lonDeg = place.lon, zoneId = place.zone, placeName = place.name)

    /** Each night in the window gives exactly one occurrence, filed under the night, and none on the days around it. */
    private fun assertEveryNightFiledOnce(place: Place, from: LocalDate, until: LocalDate, ringDayOffset: Long) {
        val zone = ZoneId.of(place.zone)
        val all = nights(place, from, until)
        assertTrue("${place.name}: the window holds both kinds", all.any { it.event == LunarEvent.FULL_MOON } && all.any { it.event == LunarEvent.NEW_MOON })
        for (night in all) {
            val rule = ruleFor(place, night.event)
            val where = "${place.name} ${night.event} of the night ${night.night}"
            // The premise: where the ring renders, relative to the night's own date (R5).
            assertEquals("$where: the ring's local date", night.night.plusDays(ringDayOffset), night.instant.atZone(zone).toLocalDate())

            val occurrence = OccurrenceEngine.occurrenceFor(rule, night.night)
            assertNotNull("$where: nothing filed under the night", occurrence)
            assertEquals(where, night.instant, occurrence!!.anchorAt)
            assertEquals(where, night.night, occurrence.anchorDate)

            for (shift in listOf(-2L, -1L, 1L, 2L)) {
                assertNull("$where: also found on ${night.night.plusDays(shift)}", OccurrenceEngine.occurrenceFor(rule, night.night.plusDays(shift)))
            }

            val dayBefore = night.night.minusDays(1).atStartOfDay(zone).toInstant()
            val next = OccurrenceEngine.nextOccurrence(rule, dayBefore)
            assertNotNull("$where: nextOccurrence from the day before finds nothing", next)
            assertEquals(where, night.instant, next!!.fireAt)
            assertEquals(where, night.night, next.anchorDate)
        }
    }

    @Test
    fun `Kiritimati full and new moon nights each give exactly one occurrence on the right local date`() =
        assertEveryNightFiledOnce(kiritimati, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 6, 1), ringDayOffset = 0)

    @Test
    fun `Madrid in July 2027 full and new moon nights each give exactly one occurrence on the right local date`() =
        assertEveryNightFiledOnce(madrid, LocalDate.of(2027, 6, 25), LocalDate.of(2027, 8, 1), ringDayOffset = 1)

    @Test
    fun `Urumqi on Asia-Shanghai full and new moon nights each give exactly one occurrence on the right local date`() =
        assertEveryNightFiledOnce(urumqi, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 6, 1), ringDayOffset = 1)
}
