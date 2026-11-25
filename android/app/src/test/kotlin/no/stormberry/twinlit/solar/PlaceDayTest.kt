package no.stormberry.twinlit.solar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PlaceDayTest {

    private val oslo = ZoneId.of("Europe/Oslo")
    private val sydney = ZoneId.of("Australia/Sydney")

    @Test
    fun `today is the date in the place's zone, not the phone's`() {
        // 23:30 in Oslo on Monday 16 November 2026 is already Tuesday morning in Sydney.
        val now = Instant.parse("2026-11-16T22:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 16), PlaceDay.today(oslo, now))
        assertEquals(LocalDate.of(2026, 11, 17), PlaceDay.today(sydney, now))
    }

    @Test
    fun `an unknown zone id falls back to UTC, and no place means the phone's zone`() {
        assertEquals(ZoneOffset.UTC, PlaceDay.zoneOf("Mars/Olympus", oslo))
        assertEquals(oslo, PlaceDay.zoneOf(null, oslo))
        assertEquals(sydney, PlaceDay.zoneOf("Australia/Sydney", oslo))
    }

    @Test
    fun `a screen on today follows the new place's today, a picked date stays`() {
        val now = Instant.parse("2026-11-16T22:30:00Z")
        val osloToday = LocalDate.of(2026, 11, 16)
        assertEquals(LocalDate.of(2026, 11, 17), PlaceDay.afterPlaceChange(osloToday, oslo, sydney, now))
        val picked = LocalDate.of(2026, 12, 24)
        assertEquals(picked, PlaceDay.afterPlaceChange(picked, oslo, sydney, now))
    }

    /**
     * MoonApp's PlaceDayTest said an unknown or empty zone id falls back to the PHONE's zone;
     * SunApp's `resolveZone` (the one Twinlit keeps, SPEC 6.2) falls back to UTC. SunApp's rule
     * wins: the engine, the widgets and the alarms already agree on it. The difference is
     * reported here instead of ported (DECISIONS: MoonApp case reported, SunApp's rule kept), and
     * every id MoonApp's test used is pinned to UTC.
     */
    @Test
    fun `every unknown or empty zone id MoonApp's test used gives UTC, not the phone's zone`() {
        for (id in listOf("", "Not/AZone", "Mars/Olympus")) {
            assertEquals(id, ZoneOffset.UTC, PlaceDay.zoneOf(id, oslo))
        }
    }

    @Test
    fun `the start date follows the place's zone, never the phone's, for a Tokyo place on an Oslo phone`() {
        // 22:30 UTC on 2026-10-30 is 23:30 in Oslo (CET) and 07:30 on 31 October in Tokyo.
        val now = Instant.parse("2026-10-30T22:30:00Z")
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(LocalDate.parse("2026-10-31"), PlaceDay.today(PlaceDay.zoneOf("Asia/Tokyo", oslo), now))
        assertEquals(LocalDate.parse("2026-10-30"), PlaceDay.today(oslo, now))
        assertEquals(tokyo, PlaceDay.zoneOf("Asia/Tokyo", oslo))
    }
}
