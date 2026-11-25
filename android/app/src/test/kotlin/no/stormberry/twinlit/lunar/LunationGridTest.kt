package no.stormberry.twinlit.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * R5 (D7): the bracket scan of `MoonPhase.lunationsBracketed` finds exactly the moments the website's
 * algorithm (`MoonPhase.nextLunations`) finds, and the night is the local date of the moment minus
 * 12 hours.
 */
class LunationGridTest {

    private val from = LocalDate.of(2025, 12, 1)
    private val to = LocalDate.of(2028, 2, 29)

    /** The chain of `nextLunations(date, UTC)` moments of one kind with a UTC date in [from] to [to]. */
    private fun chain(full: Boolean): List<Instant> {
        val out = mutableListOf<Instant>()
        var date = from
        while (!date.isAfter(to)) {
            val l = MoonPhase.nextLunations(date, ZoneOffset.UTC)
            val at = (if (full) l.fullAt else l.newAt) ?: break
            val utc = at.atZone(ZoneOffset.UTC).toLocalDate()
            if (utc.isAfter(to)) break
            out += at
            date = utc.plusDays(1)
        }
        return out.distinct()
    }

    private val bracketed by lazy { MoonPhase.lunationsBracketed(from, to) }

    @Test
    fun `the bracket scan finds exactly the website's moments`() {
        val full = chain(full = true)
        val new = chain(full = false)
        assertTrue("some moments", full.size > 25 && new.size > 25)
        assertEquals(full, bracketed.filter { it.event == LunarEvent.FULL_MOON }.map { it.at })
        assertEquals(new, bracketed.filter { it.event == LunarEvent.NEW_MOON }.map { it.at })
    }

    private val zones = listOf(
        "Europe/Oslo", "Asia/Tokyo", "Australia/Sydney", "Pacific/Honolulu",
        "Pacific/Kiritimati", "Pacific/Auckland", "Atlantic/Reykjavik", "America/Los_Angeles",
    )

    @Test
    fun `every nextLunations moment of 2026 and 2027 in eight zones is on the bracket list`() {
        val onList = bracketed.map { it.at }.toSet()
        val misses = mutableListOf<String>()
        for (zoneId in zones) {
            val zone = ZoneId.of(zoneId)
            var date = LocalDate.of(2026, 1, 1)
            while (!date.isAfter(LocalDate.of(2027, 12, 31))) {
                val l = MoonPhase.nextLunations(date, zone)
                for ((kind, at) in listOf("new" to l.newAt, "full" to l.fullAt)) {
                    if (at != null && at !in onList) misses += "$zoneId $date $kind $at"
                }
                date = date.plusDays(1)
            }
        }
        assertTrue("moments missing from the bracket list: ${misses.take(10)}", misses.isEmpty())
    }

    @Test
    fun `the night is the date of the moment minus 12 hours`() {
        val oslo = ZoneId.of("Europe/Oslo")
        fun local(z: ZoneId, y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
            LocalDate.of(y, m, d).atTime(h, mi, s).atZone(z).toInstant()
        assertEquals(LocalDate.of(2027, 1, 21), MoonPhase.nightOf(local(oslo, 2027, 1, 22, 11, 5), oslo))
        assertEquals(LocalDate.of(2027, 1, 22), MoonPhase.nightOf(local(oslo, 2027, 1, 22, 12, 0), oslo))
        assertEquals(LocalDate.of(2027, 1, 21), MoonPhase.nightOf(local(oslo, 2027, 1, 22, 11, 59, 59), oslo))
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        assertEquals(LocalDate.of(2027, 3, 22), MoonPhase.nightOf(local(kiritimati, 2027, 3, 22, 23, 34), kiritimati))
    }

    @Test
    fun `nextNights names the first night on or after the date`() {
        val oslo = ZoneId.of("Europe/Oslo")
        assertEquals(LocalDate.of(2027, 1, 21), MoonPhase.nextNights(LocalDate.of(2027, 1, 21), oslo).fullNight)
        assertEquals(LocalDate.of(2027, 2, 20), MoonPhase.nextNights(LocalDate.of(2027, 1, 22), oslo).fullNight)
    }
}
