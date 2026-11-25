package no.stormberry.twinlit.widget.moon

import no.stormberry.twinlit.cities.CityTable
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.lunar.LunarDayKind
import no.stormberry.twinlit.lunar.MoonPhase
import no.stormberry.twinlit.lunar.MoonPhase.PrincipalPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The widgets' words and formats (1.0.8), exactly, and the house style they keep. */
class WidgetTextTest {

    private var saved: Locale = Locale.getDefault()

    /** The phone's language must not reach the widget's dates (D4): run as a Norwegian phone. */
    @Before fun norwegianPhone() { saved = Locale.getDefault(); Locale.setDefault(Locale.forLanguageTag("nb-NO")) }
    @After fun restore() { Locale.setDefault(saved) }

    private val oslo = ZoneId.of("Europe/Oslo")
    private val today = LocalDate.parse("2026-10-30")

    private fun state(
        kind: LunarDayKind = LunarDayKind.NORMAL,
        rise: Instant? = Instant.parse("2026-10-30T17:42:00Z"),
        transit: Instant? = Instant.parse("2026-10-31T00:10:00Z"),
        set: Instant? = Instant.parse("2026-10-31T07:55:00Z"),
        zoneDiffers: Boolean = false,
    ) = WidgetState(
        placeLabel = "Oslo", zone = oslo, zoneDiffers = zoneDiffers, today = today,
        fraction = 0.78, phase = 0.36, percent = 78, phaseName = "Waxing Gibbous", litOnRight = true,
        dayKind = kind, rise = rise, transit = transit, set = set,
        phases = listOf(
            MoonPhase.PhaseEvent(PrincipalPhase.FULL, LocalDate.parse("2026-11-05"), Instant.parse("2026-11-05T13:00:00Z")),
            MoonPhase.PhaseEvent(PrincipalPhase.LAST_QUARTER, LocalDate.parse("2026-11-12"), Instant.parse("2026-11-12T13:00:00Z")),
            MoonPhase.PhaseEvent(PrincipalPhase.NEW, LocalDate.parse("2026-11-20"), Instant.parse("2026-11-20T13:00:00Z")),
            MoonPhase.PhaseEvent(PrincipalPhase.FIRST_QUARTER, LocalDate.parse("2026-11-27"), Instant.parse("2026-11-27T13:00:00Z")),
        ),
    )

    /** A catalogue place (what the city picker stores), or a typed one. */
    private fun city(label: String, lat: Double = 59.9139, lon: Double = 10.7522, zone: String = "Europe/Oslo") =
        Place(label, lat, lon, zone, fromCatalogue = true)

    private fun typed(name: String = "", lat: Double = 59.9139, lon: Double = 10.7522) =
        Place("%.4f N, %.4f E".format(Locale.ROOT, lat, lon), lat, lon, "Europe/Oslo", fromCatalogue = false, name = name)

    @Test fun `the widgets name a catalogue place by its city only`() {
        assertEquals("Oslo", WidgetText.placeName(city("Oslo, Norway")))
        assertEquals("Tromsø", WidgetText.placeName(city("Tromsø, Norway", 69.6489, 18.9551)))
        assertEquals("Portland", WidgetText.placeName(city("Portland, Oregon, United States")))
        assertEquals("Oslo", WidgetText.placeName(city("Oslo")))
        assertEquals("Oslo", WidgetText.placeName(city("  Oslo ,  Norway  ")))
        assertEquals("Oslo", WidgetText.placeName(city("  Oslo  ")))
        // Nothing before the comma: the label is kept rather than shown empty.
        assertEquals(", Norway", WidgetText.placeName(city(", Norway")))
    }

    @Test fun `a place typed as coordinates is never shown as numbers`() {
        // The label of a typed place is its own coordinates, in the phone's language when typed:
        // none of the digits, decimal signs or minus signs may reach the widget.
        for (tag in listOf("ar-EG", "fa", "mr", "bn", "my", "nb-NO", "en-GB")) {
            val p = Place("%.4f°, %.4f°".format(Locale.forLanguageTag(tag), -59.9139, 10.7522), -59.9139, 10.7522, "Europe/Oslo", fromCatalogue = false)
            assertEquals(tag, "Your coordinates", WidgetText.placeName(p))
        }
        assertTrue(
            "the Arabic label must exercise non-ASCII digits",
            "%.4f°".format(Locale.forLanguageTag("ar-EG"), 59.9139).none { it in '0'..'9' },
        )
        assertEquals("Your coordinates", WidgetText.placeName(typed()))
        // A city name with digits is a city, not coordinates.
        assertEquals("Area 51", WidgetText.placeName(city("Area 51, Nevada")))
    }

    /**
     * MoonApp 1.0.0 stored a typed place as "%.4f, %.4f" and the place could still be marked as a
     * catalogue place (a missing key reads as true). The widget uses the shared test
     * (`PlaceDisplay.isTyped`: the place's own coordinates as its label) and must not show the numbers.
     */
    @Test fun `a place whose label is its own coordinates is shown as your coordinates even when marked as a catalogue place`() {
        val old = Place("59.9139 N, 10.7522 E", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)
        assertEquals("Your coordinates", WidgetText.placeName(old))
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val s = WidgetModel.build(now, old, oslo)
        assertEquals("Your coordinates", s.placeLabel)
        assertFalse(WidgetText.describeLarge(s).contains("59.9"))
        // Names with a letter are places, whatever digits they hold.
        assertEquals("Area 51", WidgetText.placeName(city("Area 51, Nevada")))
        assertEquals("Oslo", WidgetText.placeName(city("Oslo, Norway")))
    }

    @Test fun `every catalogue place keeps a name and is never taken for coordinates`() {
        val tsv = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "data/cities.tsv") }
            .firstOrNull { it.isFile }
            ?: error("data/cities.tsv not found; the copyCityData task keeps it in sync")
        val cities = tsv.reader().use { CityTable.parse(it) }.cities
        assertTrue(cities.size > 20000)
        for (c in cities) {
            // The label the city picker stores for a catalogue place.
            val name = WidgetText.placeName(city("${c.name}, ${c.country}"))
            assertEquals(c.name, c.name.substringBefore(',').trim(), name)
            assertTrue(c.name, name.isNotEmpty() && name != WidgetText.YOUR_COORDINATES)
        }
    }

    @Test fun `the state and the spoken description carry the city only`() {
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val cityState = WidgetModel.build(now, city("Oslo, Norway"), oslo)
        assertEquals("Oslo", cityState.placeLabel)
        assertTrue(WidgetText.describeSmall(cityState).startsWith("Oslo: "))
        assertFalse(WidgetText.describeLarge(cityState).contains("Norway"))
        val unnamed = WidgetModel.build(now, typed(), oslo)
        assertEquals("Your coordinates", unnamed.placeLabel)
        assertTrue(WidgetText.describeMedium(unnamed).startsWith("Your coordinates: "))
        assertFalse(WidgetText.describeMedium(unnamed).contains("59"))
    }

    @Test fun `a place typed as coordinates with a name is shown by that name, whole`() {
        // The name, whole: no cut at a comma, not shortened to a city, nothing added.
        assertEquals("Bunker", WidgetText.placeName(typed("Bunker")))
        assertEquals("Home, sweet home", WidgetText.placeName(typed("Home, sweet home")))
        assertEquals("Cabin 2, north", WidgetText.placeName(typed("  Cabin   2, north ")))
        assertEquals("Oslo, Norway", WidgetText.placeName(typed("Oslo, Norway")))
        // No name or a blank name: "Your coordinates", never the numbers.
        for (name in listOf("", "   ", "\n\t")) {
            assertEquals(name, "Your coordinates", WidgetText.placeName(typed(name)))
        }
        // A name is free text (Marcos, 2026-11-19): one that looks like numbers or coordinates is
        // shown as it was typed.
        for (name in listOf("24", "59.9139, 10.7522", "Hut 59.9139", "Cabin, north", "59.9139\u00B0")) {
            assertEquals(name, name, WidgetText.placeName(typed(name)))
        }
        // A catalogue place has no name of its own: a name is ignored and the city is shown.
        assertEquals("Oslo", WidgetText.placeName(city("Oslo, Norway").copy(name = "Bunker")))
        assertEquals("Portland", WidgetText.placeName(city("Portland, Oregon, United States").copy(name = "Home")))
    }

    @Test fun `the state and the spoken description carry the name of a named place`() {
        val now = Instant.parse("2026-10-30T12:00:00Z")
        val named = WidgetModel.build(now, typed("Home, sweet home"), oslo)
        assertEquals("Home, sweet home", named.placeLabel)
        assertTrue(WidgetText.describeSmall(named).startsWith("Home, sweet home: "))
        assertTrue(WidgetText.describeMedium(named).startsWith("Home, sweet home: "))
        assertTrue(WidgetText.describeLarge(named).startsWith("Home, sweet home: "))
        for (text in listOf(WidgetText.describeLarge(named), named.placeLabel)) {
            assertFalse(text, '°' in text || text.contains("59.9") || text.contains("10.75"))
        }
        val unnamed = WidgetModel.build(now, typed(""), oslo)
        assertEquals("Your coordinates", unnamed.placeLabel)
        assertTrue(WidgetText.describeMedium(unnamed).startsWith("Your coordinates: "))
        // A name that looks like numbers is spoken and shown as typed, in every size.
        for (name in listOf("24", "59.9139, 10.7522", "Hut 59.9139", "Cabin, north")) {
            val s = WidgetModel.build(now, typed(name), oslo)
            assertEquals(name, s.placeLabel)
            assertTrue(WidgetText.describeSmall(s).startsWith("$name: "))
            assertTrue(WidgetText.describeMedium(s).startsWith("$name: "))
            assertTrue(WidgetText.describeLarge(s).startsWith("$name: "))
        }
        // A catalogue place is unchanged, and a leftover name does not reach it.
        val leftover = WidgetModel.build(now, city("Oslo, Norway").copy(name = "Bunker"), oslo)
        assertEquals("Oslo", leftover.placeLabel)
    }

    @Test fun `percent at both ends`() {
        assertEquals("0%", WidgetText.percent(0))
        assertEquals("78%", WidgetText.percent(78))
        assertEquals("100%", WidgetText.percent(100))
    }

    @Test fun `times in the place's zone, an en dash when there is none`() {
        assertEquals("18:42", WidgetText.time(Instant.parse("2026-10-30T17:42:00Z"), oslo))
        assertEquals("02:42", WidgetText.time(Instant.parse("2026-10-30T17:42:00Z"), ZoneId.of("Asia/Tokyo")))
        assertEquals("–", WidgetText.time(null, oslo))
    }

    @Test fun `dates in British English whatever the phone's language`() {
        assertEquals("Thu 12 Nov", WidgetText.shortDate(LocalDate.parse("2026-11-12")))
        assertEquals("Thursday 12 November", WidgetText.longDate(LocalDate.parse("2026-11-12")))
        assertEquals("Today", WidgetText.phaseDate(today, today))
        assertEquals("Fri 20 Nov", WidgetText.phaseDate(LocalDate.parse("2026-11-20"), today))
        assertEquals("Fri 30 Oct", WidgetText.wideHeaderDate(state()))
        assertEquals("Fri 30 Oct · Europe/Oslo", WidgetText.wideHeaderDate(state(zoneDiffers = true)))
        assertEquals("Times shown in Europe/Oslo.", WidgetText.zoneNote(oslo))
        // The compact arrangement: no weekday.
        assertEquals("12 Nov", WidgetText.compactPhaseDate(LocalDate.parse("2026-11-12"), today))
        assertEquals("1 Dec", WidgetText.compactPhaseDate(LocalDate.parse("2026-12-01"), today))
        assertEquals("Today", WidgetText.compactPhaseDate(today, today))
    }

    /** A day the moon stays below the horizon shows no time, so no zone either (emulator check). */
    @Test fun `no zone note on a day without times`() {
        val down = state(kind = LunarDayKind.ALWAYS_DOWN, rise = null, transit = null, set = null, zoneDiffers = true)
        assertFalse(WidgetText.showsZoneNote(down))
        assertEquals("Fri 30 Oct", WidgetText.wideHeaderDate(down))
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. The moon stays below the horizon all day. " +
                "Next full moon Thursday 5 November. Next last quarter Thursday 12 November. " +
                "Next new moon Friday 20 November. Next first quarter Friday 27 November.",
            WidgetText.describeLarge(down),
        )
        // Always up has a transit, so the zone stays.
        val up = state(kind = LunarDayKind.ALWAYS_UP, rise = null, set = null, zoneDiffers = true)
        assertTrue(WidgetText.showsZoneNote(up))
        assertTrue(WidgetText.describeLarge(up).contains("Times shown in Europe/Oslo."))
        assertEquals("Fri 30 Oct · Europe/Oslo", WidgetText.wideHeaderDate(up))
        // Same zone as the phone: no note on the widget (the description still names it).
        assertFalse(WidgetText.showsZoneNote(state()))
        assertTrue(WidgetText.showsZoneNote(state(zoneDiffers = true)))
    }

    @Test fun `phase labels`() {
        assertEquals(
            listOf("New moon", "First quarter", "Full moon", "Last quarter"),
            PrincipalPhase.entries.map { WidgetText.phaseLabel(it) },
        )
    }

    @Test fun `content descriptions, word for word`() {
        assertEquals("Oslo: Waxing Gibbous, 78% lit.", WidgetText.describeSmall(state()))
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. Moonrise 18:42. Transit 01:10. Moonset 08:55.",
            WidgetText.describeMedium(state()),
        )
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. No moonrise today. No transit today. No moonset today.",
            WidgetText.describeMedium(state(rise = null, transit = null, set = null)),
        )
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. The moon is above the horizon all day. Transit 01:10.",
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_UP, rise = null, set = null)),
        )
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. The moon is above the horizon all day. No transit today.",
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_UP, rise = null, transit = null, set = null)),
        )
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. The moon stays below the horizon all day.",
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_DOWN, rise = null, transit = null, set = null)),
        )
        assertEquals(
            "Oslo: Waxing Gibbous, 78% lit. Moonrise 18:42. Transit 01:10. Moonset 08:55. " +
                "Times shown in Europe/Oslo. Next full moon Thursday 5 November. Next last quarter Thursday 12 November. " +
                "Next new moon Friday 20 November. Next first quarter Friday 27 November.",
            WidgetText.describeLarge(state()),
        )
        assertEquals(
            "Next full moon today.",
            WidgetText.describePhase(MoonPhase.PhaseEvent(PrincipalPhase.FULL, today, Instant.parse("2026-10-30T12:00:00Z")), today),
        )
    }

    /** The resource file in this module, found from the working directory Gradle gives the test. */
    private fun res(rel: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "app/src/main/res/$rel"), File(it, "android/app/src/main/res/$rel"), File(it, "src/main/res/$rel")) }
            .firstOrNull { it.exists() } ?: error("$rel not found above the working directory")

    @Test fun `no widget string carries an em dash or a double hyphen`() {
        val texts = WidgetText.ALL_FIXED + listOf(
            WidgetText.describeLarge(state()),
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_UP, rise = null, set = null)),
            WidgetText.describeMedium(state(kind = LunarDayKind.ALWAYS_DOWN)),
            WidgetText.describeMedium(state(rise = null, transit = null, set = null)),
        )
        val strings = Regex("<string name=\"widget_moon_[^\"]*\">([^<]*)</string>").findAll(res("values/strings.xml").readText()).map { it.groupValues[1] }.toList()
        assertEquals("six widget picker strings and the placeholder hint", 7, strings.size)
        for (t in texts + strings) {
            assertFalse("em dash in: $t", t.contains('\u2014'))
            assertFalse("double hyphen in: $t", t.contains("--"))
        }
    }
}
