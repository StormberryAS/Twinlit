package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.WidgetTestPlaces
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.widget.WidgetTestPlaces.LONGYEARBYEN
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.SYDNEY
import no.stormberry.twinlit.widget.WidgetTestPlaces.TROMSO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Every widget string (1.1.9), checked exactly, with worked examples for Oslo, Longyearbyen,
 * Tromsø and Sydney.
 */
class WidgetTextTest {

    private val osloPhone = ZoneId.of("Europe/Oslo")

    private fun state(p: Place, iso: String, ticking: Boolean = true): WidgetState =
        WidgetModel.build(Instant.parse(iso), p, osloPhone, ticking)!!

    private fun small(p: Place, iso: String) = WidgetText.small(state(p, iso)).let { it.label + " / " + it.value }

    private fun line(p: Place, iso: String, ticking: Boolean = true, withTime: Boolean = false): String =
        when (val l = WidgetText.line(state(p, iso, ticking), withTime)) {
            WidgetText.LineText.Hidden -> "(hidden)"
            is WidgetText.LineText.Static -> l.text
            is WidgetText.LineText.Ticking -> l.format + " (ticking)"
        }

    @Test
    fun `Oslo, Monday 16 November, through the day`() {
        // 06:00 local
        assertEquals("Sunrise / 08:17", small(OSLO, "2026-11-16T05:00:00Z"))
        assertEquals("Sunrise in %s (ticking)", line(OSLO, "2026-11-16T05:00:00Z"))
        assertEquals("Sunrise at 08:17", line(OSLO, "2026-11-16T05:00:00Z", ticking = false))
        // 07:45, the last stretch
        assertEquals("Sunrise at 08:17", line(OSLO, "2026-11-16T06:45:00Z"))
        // 08:30 and 13:00
        assertEquals("Sunset / 15:48", small(OSLO, "2026-11-16T07:30:00Z"))
        assertEquals("Daylight left %s (ticking)", line(OSLO, "2026-11-16T12:00:00Z"))
        assertEquals("Sunset at 15:48", line(OSLO, "2026-11-16T12:00:00Z", ticking = false))
        // 15:30
        assertEquals("Sunset at 15:48", line(OSLO, "2026-11-16T14:30:00Z"))
        // 16:30 and 23:30
        assertEquals("Sunrise tomorrow / 08:19", small(OSLO, "2026-11-16T15:30:00Z"))
        assertEquals("Sunrise in %s (ticking)", line(OSLO, "2026-11-16T22:30:00Z"))
        assertEquals("Sunrise tomorrow at 08:19", line(OSLO, "2026-11-16T22:30:00Z", ticking = false))
        // The times line all day
        assertEquals(WidgetText.TimesLine.Normal("08:17", "15:48"), WidgetText.timesLine(state(OSLO, "2026-11-16T12:00:00Z")))
    }

    @Test
    fun `Longyearbyen and Tromso, the polar forms`() {
        assertEquals("Always Up / Sunset 26 Aug", small(LONGYEARBYEN, "2026-06-21T10:00:00Z"))
        assertEquals("Next sunset Wed 26 Aug", line(LONGYEARBYEN, "2026-06-21T10:00:00Z"))
        assertEquals("Next sunset Wed 26 Aug, 00:28", line(LONGYEARBYEN, "2026-06-21T10:00:00Z", withTime = true))
        assertEquals(WidgetText.TimesLine.Polar("Always Up", WidgetText.Tint.GOLD), WidgetText.timesLine(state(LONGYEARBYEN, "2026-06-21T10:00:00Z")))

        assertEquals("Always Down / Sunrise 16 Feb", small(LONGYEARBYEN, "2026-12-21T11:00:00Z"))
        assertEquals("Next sunrise Tue 16 Feb", line(LONGYEARBYEN, "2026-12-21T11:00:00Z"))
        assertEquals("Next sunrise Tue 16 Feb, 11:39", line(LONGYEARBYEN, "2026-12-21T11:00:00Z", withTime = true))
        assertEquals(WidgetText.TimesLine.Polar("Always Down", WidgetText.Tint.BLUE), WidgetText.timesLine(state(LONGYEARBYEN, "2026-12-21T11:00:00Z")))

        // Polar day, less than 24 hours before the sunrise: the line ticks.
        assertEquals("Sunrise in %s (ticking)", line(LONGYEARBYEN, "2027-02-15T12:00:00Z"))

        // The end of the midnight sun.
        assertEquals("Sunset tomorrow / 00:28", small(LONGYEARBYEN, "2026-08-24T22:30:00Z"))
        assertEquals("Daylight left %s (ticking)", line(LONGYEARBYEN, "2026-08-24T22:30:00Z"))
        assertEquals("Sunset tomorrow at 00:28", line(LONGYEARBYEN, "2026-08-24T22:30:00Z", ticking = false))
        assertEquals("Sunrise / 02:05", small(LONGYEARBYEN, "2026-08-25T22:40:00Z"))

        assertEquals("Always Down / Sunrise 16 Jan", small(TROMSO, "2026-12-21T11:00:00Z"))
        assertEquals("Next sunrise Sat 16 Jan", line(TROMSO, "2026-12-21T11:00:00Z"))
        assertEquals("Always Up / Sunset 27 Jul", small(TROMSO, "2026-06-21T11:00:00Z"))
        assertEquals("Next sunset Mon 27 Jul", line(TROMSO, "2026-06-21T11:00:00Z"))
    }

    @Test
    fun `the place name is the city only (W1)`() {
        assertEquals("Oslo", WidgetText.placeName(OSLO))
        assertEquals("Tromsø", WidgetText.placeName(TROMSO))
        assertEquals("Sydney", WidgetText.placeName(SYDNEY))
        assertEquals("Oslo", WidgetText.cityOnly("Oslo, Norway"))
        assertEquals("Tromsø", WidgetText.cityOnly("Tromsø, Norway"))
        assertEquals("Portland", WidgetText.cityOnly("Portland, Oregon, United States"))
        assertEquals("Oslo", WidgetText.cityOnly("  Oslo ,  Norway "))
        // A label with no comma is unchanged (apart from the trim).
        assertEquals("Singapore", WidgetText.cityOnly("Singapore"))
        assertEquals("Singapore", WidgetText.cityOnly(" Singapore "))
        // A place typed as coordinates stays neutral, and the model and the descriptions follow.
        val typed = OSLO.copy(label = "59.9127 N, 10.7461 E", fromCatalogue = false)
        assertEquals("Your coordinates", WidgetText.placeName(typed))
        assertEquals("59.9127 N, 10.7461 E", WidgetText.cityOnly("59.9127 N, 10.7461 E"))
        assertEquals("Oslo", state(OSLO, "2026-11-16T12:00:00Z").placeLabel)
        assertTrue(WidgetText.describeSmall(state(TROMSO, "2026-11-16T12:00:00Z")).startsWith("Tromsø: "))
    }

    @Test
    fun `spoken descriptions`() {
        val s = state(OSLO, "2026-11-16T12:00:00Z")
        assertEquals("Oslo: sunset at 15:48.", WidgetText.describeSmall(s))
        assertEquals("Oslo: sunrise 08:17, sunset 15:48. Daylight until sunset at 15:48.", WidgetText.describeMedium(s))
        assertEquals(
            "Oslo: sunrise 08:17, sunset 15:48. Daylight until sunset at 15:48. Solar noon 12:03. " +
                "Golden hour until 09:37 and from 14:29. Civil twilight 07:28 to 16:37. " +
                "Nautical twilight 06:36 to 17:29. Astronomical twilight 05:47 to 18:18.",
            WidgetText.describeLarge(s),
        )
        val evening = state(OSLO, "2026-11-16T15:30:00Z")
        assertEquals("Oslo: sunrise tomorrow at 08:19.", WidgetText.describeSmall(evening))
        assertTrue(WidgetText.describeMedium(evening).endsWith("Next sunrise tomorrow at 08:19."))

        val dec = state(LONGYEARBYEN, "2026-12-21T11:00:00Z")
        val polar = "Longyearbyen: the sun stays below the horizon all day. " +
            "Next sunrise Tuesday 16 February at 11:39."
        assertEquals(polar, WidgetText.describeSmall(dec))
        assertEquals(polar, WidgetText.describeMedium(dec))
        assertEquals(
            "$polar Solar noon 11:56. Nautical twilight 10:59 to 12:53. Astronomical twilight 07:38 to 16:15.",
            WidgetText.describeLarge(dec),
        )

        val june = WidgetText.describeLarge(state(OSLO, "2026-06-21T11:00:00Z"))
        assertTrue(june, june.contains("Civil twilight 02:10 to 00:29 the next day."))
        assertTrue(june, june.contains("Never darker than nautical twilight all night."))

        val lybJune = WidgetText.describeLarge(state(LONGYEARBYEN, "2026-06-21T10:00:00Z"))
        assertTrue(lybJune, lybJune.contains("Never darker than civil twilight all night."))
        assertFalse(lybJune, lybJune.contains("Civil twilight all night"))

        val pole = WidgetText.describeLarge(state(WidgetTestPlaces.NEAR_NORTH_POLE, "2026-03-12T12:00:00Z"))
        assertTrue(pole, pole.contains("Never darker than civil twilight all day and night."))

        assertTrue(WidgetText.describeLarge(state(SYDNEY, "2026-11-16T12:00:00Z")).endsWith("Times shown in Australia/Sydney."))
        assertFalse(WidgetText.describeLarge(state(LONGYEARBYEN, "2026-11-16T12:00:00Z")).contains("Times shown"))
        // A description never contains a ticking value.
        assertFalse(WidgetText.describeMedium(state(OSLO, "2026-11-16T05:00:00Z")).contains("%s"))
    }

    @Test
    fun `header date, with the zone when the clocks differ`() {
        assertEquals("Mon 16 Nov", WidgetText.headerDate(state(OSLO, "2026-11-16T12:00:00Z")))
        assertEquals("Mon 16 Nov · Australia/Sydney", WidgetText.headerDate(state(SYDNEY, "2026-11-16T01:00:00Z")))
    }

    @Test
    fun `markers, including the synthetic previous-day case`() {
        val zone = ZoneId.of("Europe/Oslo")
        val ref = LocalDate.of(2026, 6, 21)
        assertEquals("00:29 +1", WidgetText.marked(Instant.parse("2026-06-21T22:29:13Z"), zone, ref))
        assertEquals("23:58 −1", WidgetText.marked(Instant.parse("2026-06-20T21:58:00Z"), zone, ref))
        assertEquals("02:10", WidgetText.marked(Instant.parse("2026-06-21T00:10:46Z"), zone, ref))
        assertEquals("–", WidgetText.marked(null, zone, ref))
        assertEquals("–", WidgetText.time(null, zone))
    }

    @Test
    fun `dates are British under any default locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("nb-NO"))
            assertEquals("Mon 16 Nov", WidgetText.shortDate(LocalDate.of(2026, 11, 16)))
            assertEquals("Next sunrise Tue 16 Feb, 11:39", line(LONGYEARBYEN, "2026-12-21T11:00:00Z", withTime = true))
            assertTrue(WidgetText.describeSmall(state(LONGYEARBYEN, "2026-12-21T11:00:00Z")).contains("Tuesday 16 February"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `house style - en dash, U+2212 minus, no em dash, no double hyphen, one placeholder per format`() {
        assertEquals("–", WidgetText.MISSING)
        assertEquals(" −1", WidgetText.PREVIOUS_DAY_MARK)
        for (s in WidgetText.ALL_FIXED) {
            assertFalse(s, s.contains('—'))
            assertFalse(s, s.contains("--"))
        }
        for (f in listOf(WidgetText.DAYLIGHT_LEFT, WidgetText.SUNRISE_IN, WidgetText.ZONE_NOTE)) {
            assertEquals(f, 1, Regex("%s").findAll(f).count())
            assertEquals(f, 1, f.count { it == '%' })
        }
        assertEquals("Daylight left 00:00:00", WidgetText.widestTicking(WidgetText.DAYLIGHT_LEFT))
        assertEquals("Choose a place in Twinlit to start the widget.", WidgetText.CHOOSE_PLACE)
    }
}
