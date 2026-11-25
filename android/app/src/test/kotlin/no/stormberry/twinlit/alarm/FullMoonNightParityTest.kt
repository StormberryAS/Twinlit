package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * D-M3 (SPEC 7.4, R5): the full and new moon nights of 2027 in eight zones, the Alarms tab against the
 * Moon tab. Both parts are hard (R5, Marcos's D7: "FullMoonNightParityTest and MoonNightFilingTest stay
 * hard tests"). The oracle is [MoonNightOracle], changed in R5 by the decided rule (the night is the
 * local date of the exact moment minus 12 hours; the ring is 22:00 local mean solar time in it).
 *
 * **Hard part 1.** In every zone, `occurrenceFor` over every date of 2027 is non-null on exactly the
 * oracle's nights in 2027, and the `nextOccurrence` chain from 2027-01-01 visits each once, in order.
 *
 * **Hard part 2 (report-only before R5).** For every such night, the occurrence's `anchorAt` is the
 * oracle's instant, and the Moon tab's card agrees: for every date D of 2027,
 * `MoonPhase.nextNights(D, zone)` names the first oracle night on or after D (nights taken up to
 * 2028-02-29, so the last dates of the year compare against January 2028).
 */
class FullMoonNightParityTest {

    private class Zone(val name: String, val lat: Double, val lon: Double, val zone: String)

    private val zones = listOf(
        Zone("Oslo", 59.9139, 10.7522, "Europe/Oslo"),
        Zone("Tokyo", 35.6762, 139.6503, "Asia/Tokyo"),
        Zone("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
        Zone("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu"),
        Zone("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati"),
        Zone("Auckland", -36.8485, 174.7633, "Pacific/Auckland"),
        Zone("Reykjavik", 64.1466, -21.9426, "Atlantic/Reykjavik"),
        Zone("Los Angeles", 34.0522, -118.2437, "America/Los_Angeles"),
    )

    private val year = 2027
    private val first = LocalDate.of(year, 1, 1)
    private val last = LocalDate.of(year, 12, 31)
    private val oracleEnd = LocalDate.of(2028, 2, 29)

    private fun ruleFor(z: Zone, event: LunarEvent): AlarmRule =
        testMoonRule(event = event, latDeg = z.lat, lonDeg = z.lon, zoneId = z.zone, placeName = z.name)

    @Test
    fun `hard parts - every full and new moon night of 2027 gives one occurrence at the oracle instant, and the Moon tab names the same night`() {
        val failures = mutableListOf<String>()
        var nights = 0
        var differ = 0
        for (z in zones) {
            val zone = ZoneId.of(z.zone)
            for (event in listOf(LunarEvent.FULL_MOON, LunarEvent.NEW_MOON)) {
                val rule = ruleFor(z, event)
                val oracleAll = MoonNightOracle.nights(event, zone, first, oracleEnd)
                val oracle = oracleAll.filter { it <= last }

                // Hard part 1, count: every date of the year, one occurrence on exactly the oracle's nights.
                val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
                val filed = dates.filter { OccurrenceEngine.occurrenceFor(rule, it) != null }
                nights += filed.size
                if (filed != oracle) failures += "${z.name} $event: filed on $filed, the nights are on $oracle"

                // Hard part 1, chain: nextOccurrence from the start of the year visits each night once, in order.
                val visited = mutableListOf<LocalDate>()
                var cursor = first.atStartOfDay(zone).toInstant()
                while (true) {
                    val next = OccurrenceEngine.nextOccurrence(rule, cursor) ?: break
                    if (next.anchorDate.isAfter(last)) break
                    visited += next.anchorDate
                    cursor = next.fireAt
                }
                if (visited != oracle) failures += "${z.name} $event: the chain visits $visited, the nights are $oracle"
                if (visited.toSet().size != visited.size) failures += "${z.name} $event: the chain visits a night twice: $visited"

                // Hard part 2: the instant, and the card on the night itself.
                for (night in oracle) {
                    val occurrence = OccurrenceEngine.occurrenceFor(rule, night)
                    val want = MoonNightOracle.instant(night, z.lon, zone)
                    if (occurrence?.anchorAt != want) failures += "${z.name} $event $night: rings ${occurrence?.anchorAt}, the oracle says $want"
                    val card = MoonPhase.nextNights(night, zone).let { if (event == LunarEvent.FULL_MOON) it.fullNight else it.newNight }
                    if (card != night) {
                        differ++
                        failures += "${z.name} $event: the card on $night names $card"
                    }
                }

                // Hard part 2: the card of every date names the first oracle night on or after it.
                for (d in dates) {
                    val want = oracleAll.firstOrNull { !it.isBefore(d) }
                    val got = MoonPhase.nextNights(d, zone).let { if (event == LunarEvent.FULL_MOON) it.fullNight else it.newNight }
                    if (got != want) failures += "${z.name} $event: on $d the card names $got, the oracle's first night is $want"
                }
            }
        }
        println("FullMoonNightParityTest: card and alarm differ on $differ of $nights nights (HEAD e1b7ed1: 67 of 200)")
        assertTrue(failures.take(40).joinToString("\n"), failures.isEmpty())
        assertTrue("2027 has 12 or 13 of each kind in every zone", nights in (8 * 2 * 12)..(8 * 2 * 13))
        assertEquals("sanity: the zones list", 8, zones.size)
    }

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    /** Pins from CHANGES-R5 Appendix A: the night's date and the ring on the zone's clock. */
    @Test
    fun `pins - six nights and their rings on the local clock`() {
        data class Pin(val zone: String, val event: LunarEvent, val night: LocalDate, val clock: String)
        val pins = listOf(
            Pin("Oslo", LunarEvent.FULL_MOON, LocalDate.of(2027, 1, 21), "22:17"),
            Pin("Honolulu", LunarEvent.FULL_MOON, LocalDate.of(2027, 3, 21), "22:31"),
            Pin("Kiritimati", LunarEvent.FULL_MOON, LocalDate.of(2027, 3, 22), "22:29"),
            Pin("Tokyo", LunarEvent.FULL_MOON, LocalDate.of(2027, 9, 16), "21:41"),
            Pin("Sydney", LunarEvent.NEW_MOON, LocalDate.of(2027, 3, 8), "22:55"),
            Pin("Los Angeles", LunarEvent.NEW_MOON, LocalDate.of(2027, 11, 27), "21:52"),
        )
        for (p in pins) {
            val z = zones.first { it.name == p.zone }
            val occurrence = OccurrenceEngine.occurrenceFor(ruleFor(z, p.event), p.night)
            assertTrue("${p.zone} ${p.night}: no occurrence", occurrence != null)
            assertEquals("${p.zone} ${p.night}", p.night, occurrence!!.anchorDate)
            assertEquals("${p.zone} ${p.night}", p.clock, occurrence.anchorAt.atZone(ZoneId.of(z.zone)).format(hhmm))
        }
    }
}
