package no.stormberry.twinlit.ui.moon

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonCalc
import no.stormberry.twinlit.lunar.MoonPhase
import no.stormberry.twinlit.solar.PlaceDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The pure parts of the Moon tab: the formats of its cards, the rules of its place, and the
 * acceptance of milestone M4 (Oslo and Tokyo on one date show the same phase name and the same
 * illumination, sampled at 12:00 UTC, and different rise and set times). No Compose UI test runs
 * in this project.
 */
class MoonTimesScreenTest {

    private val oslo = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)
    private val tokyo = Place("Tokyo, Japan", 35.6895, 139.6917, "Asia/Tokyo", fromCatalogue = true)
    private val typed = Place("59.9139 N, 10.7522 E", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = false)

    private val date = LocalDate.of(2026, 11, 20)

    @Test
    fun `a time is written in the place's zone as HH mm, or an en dash`() {
        val at = Instant.parse("2026-11-20T17:42:00Z")
        assertEquals("18:42", cardTime(at, ZoneId.of("Europe/Oslo")))
        assertEquals("02:42", cardTime(at, ZoneId.of("Asia/Tokyo")))
        assertEquals("\u2013", cardTime(null, ZoneId.of("Europe/Oslo")))
        // U+2013, never an em dash (house style).
        assertEquals(0x2013, NO_TIME.codePointAt(0))
        assertFalse(NO_TIME.contains("\u2014"))
    }

    @Test
    fun `the illumination is rounded as the web rounds it, not truncated`() {
        assertEquals("79%", percentText(0.789))
        assertEquals("78%", percentText(0.784))
        assertEquals("0%", percentText(0.004))
        assertEquals("100%", percentText(0.996))
    }

    @Test
    fun `the distance has thousands separators and no decimals`() {
        assertEquals("384,400 km", distanceText(384_400.4))
        assertEquals("356,500 km", distanceText(356_499.6))
        assertEquals("999 km", distanceText(999.0))
    }

    @Test
    fun `the long date is the day, the date and the year in British English`() {
        // The space before the year is a no-break space (R7, D85).
        assertEquals("Friday 20 November\u00A02026", formatMoonDate(date))
        assertEquals("Monday 21 December\u00A02026", formatMoonDate(LocalDate.of(2026, 12, 21)))
    }

    @Test
    fun `the skipped-date notice names the zone`() {
        assertEquals(
            "The clocks in Pacific/Apia skipped it. Please select another date.",
            skippedBody(ZoneId.of("Pacific/Apia")),
        )
    }

    @Test
    fun `a typed place is shown by its name or Your coordinates, a catalogue place by its label`() {
        assertEquals("Oslo, Norway", MoonPlace.of(oslo).title)
        assertEquals("Your coordinates", MoonPlace.of(typed).title)
        assertEquals("Studio", MoonPlace.of(typed.copy(name = "Studio")).title)
        assertTrue(MoonPlace.of(typed).isTyped)
        assertFalse(MoonPlace.of(oslo).isTyped)
        // A catalogue place never takes a name, even if one is stored by mistake.
        assertEquals("Oslo, Norway", MoonPlace.of(oslo.copy(name = "Home")).title)
    }

    @Test
    fun `the coordinates line is MoonApp's, degree signs and no hemisphere letters`() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.UK)
            assertEquals("59.9139\u00B0, 10.7522\u00B0", MoonPlace.of(oslo).coordinateLine)
            assertEquals("-33.8688\u00B0, 151.2093\u00B0", MoonPlace.of(oslo.copy(latDeg = -33.8688, lonDeg = 151.2093)).coordinateLine)
        } finally {
            java.util.Locale.setDefault(before)
        }
        assertFalse(MoonPlace.of(oslo).coordinateLine.contains("N"))
    }

    @Test
    fun `the tab's zone is the place's own, and UTC when the id is unknown to the device`() {
        assertEquals(ZoneId.of("Asia/Tokyo"), MoonPlace.of(tokyo).zone)
        // PlaceDay's rule (M1): an unknown id is UTC, not the phone's zone, which MoonApp's old fallback was.
        assertEquals(java.time.ZoneOffset.UTC, MoonPlace.of(oslo.copy(zoneId = "Not/AZone")).zone)
    }

    @Test
    fun `Oslo and Tokyo on one date show the same phase and illumination and different times`() {
        // The phase card samples 12:00 UTC of the date, whatever the place (MoonApp, app.js:315).
        val noon = MoonPhase.noonUtc(date)
        val illum = MoonCalc.illumination(noon)
        assertEquals(MoonPhase.name(illum.phase), MoonPhase.name(MoonCalc.illumination(MoonPhase.noonUtc(date)).phase))
        assertEquals(percentText(illum.fraction), percentText(MoonCalc.illumination(noon).fraction))

        val osloZone = MoonPlace.of(oslo).zone
        val tokyoZone = MoonPlace.of(tokyo).zone
        val osloTimes = MoonCalc.times(date, oslo.latDeg, oslo.lonDeg, osloZone)
        val tokyoTimes = MoonCalc.times(date, tokyo.latDeg, tokyo.lonDeg, tokyoZone)
        val osloRise = cardTime(osloTimes[LunarEvent.MOONRISE], osloZone)
        val tokyoRise = cardTime(tokyoTimes[LunarEvent.MOONRISE], tokyoZone)
        val osloSet = cardTime(osloTimes[LunarEvent.MOONSET], osloZone)
        val tokyoSet = cardTime(tokyoTimes[LunarEvent.MOONSET], tokyoZone)
        assertNotEquals("the rise differs", osloRise, tokyoRise)
        assertNotEquals("the set differs", osloSet, tokyoSet)
        assertNotNull(osloTimes[LunarEvent.LUNAR_TRANSIT])
    }

    @Test
    fun `the date moves to the new place's today when it showed today, and stays when picked by hand`() {
        val now = Instant.parse("2026-11-20T23:30:00Z") // 00:30 on the 21st in Oslo, 08:30 on the 21st in Tokyo
        val osloZone = PlaceDay.zoneOf(oslo.zoneId)
        val sydney = ZoneId.of("Australia/Sydney")
        val todayOslo = PlaceDay.today(osloZone, now)
        assertEquals(LocalDate.of(2026, 11, 21), todayOslo)
        assertEquals(LocalDate.of(2026, 11, 21), PlaceDay.afterPlaceChange(todayOslo, osloZone, sydney, now))
        val west = ZoneId.of("America/Los_Angeles")
        assertEquals(LocalDate.of(2026, 11, 20), PlaceDay.afterPlaceChange(todayOslo, osloZone, west, now))
        val picked = LocalDate.of(2026, 12, 25)
        assertEquals(picked, PlaceDay.afterPlaceChange(picked, osloZone, west, now))
    }
}
