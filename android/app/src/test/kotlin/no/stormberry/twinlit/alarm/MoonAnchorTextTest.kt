package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.displayLabel
import no.stormberry.twinlit.data.hasGeneratedLabel
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words of an alarm for the five moon events (TEXTS.md G-7, G-8, I-15 and the generated names
 * under G; SPEC 7.2 and 7.8) and, since M5, the notification sentences (`describeAnchor` for the five
 * moon events, `summaryFor`'s sun-only fallback line; TEXTS.md I-13, I-15).
 */
class MoonAnchorTextTest {

    private fun moon(event: LunarEvent) = Anchor.Moon(event)

    @Test
    fun `the chip labels are the five moon events in sentence case`() {
        assertEquals(
            listOf("Moonrise", "Moonset", "Moon at its highest", "Full moon night", "New moon night"),
            LunarEvent.entries.map { it.displayName },
        )
        assertEquals(listOf("Moonrise", "Moonset", "Moon at its highest", "Full moon night", "New moon night"), LunarEvent.entries.map { anchorLabel(moon(it)) })
    }

    @Test
    fun `the descriptions say what the event is and the monthly ones say the time`() {
        assertEquals(
            listOf(
                "When the moon rises above the horizon",
                "When the moon sets below the horizon",
                "When the moon reaches its highest point in the sky",
                "At 22:00 local solar time on the night of the full moon",
                "At 22:00 local solar time on the night of the new moon",
            ),
            LunarEvent.entries.map { it.description },
        )
    }

    @Test
    fun `the nouns read inside a sentence`() {
        assertEquals(
            listOf("moonrise", "moonset", "the moon's highest point", "the full moon night", "the new moon night"),
            LunarEvent.entries.map { anchorNoun(moon(it)) },
        )
        assertEquals("sunrise", anchorNoun(Anchor.Sun(SolarEvent.SUNRISE)))
        assertEquals("solar midnight", anchorNoun(Anchor.Sun(SolarEvent.NADIR)))
    }

    @Test
    fun `daily events read At, before and after`() {
        assertEquals("At moonrise", describeTiming(moon(LunarEvent.MOONRISE), Direction.AT, 0))
        assertEquals("At moonset", describeTiming(moon(LunarEvent.MOONSET), Direction.AT, 45))
        assertEquals("At the moon's highest point", describeTiming(moon(LunarEvent.LUNAR_TRANSIT), Direction.AT, 0))
        assertEquals("30 min before moonset", describeTiming(moon(LunarEvent.MOONSET), Direction.BEFORE, 30))
        assertEquals("1 h after the moon's highest point", describeTiming(moon(LunarEvent.LUNAR_TRANSIT), Direction.AFTER, 60))
        assertEquals("1 h 30 min before moonrise", describeTiming(moon(LunarEvent.MOONRISE), Direction.BEFORE, 90))
        // A zero offset on a side is At, as for the sun.
        assertEquals("At moonrise", describeTiming(moon(LunarEvent.MOONRISE), Direction.AFTER, 0))
    }

    @Test
    fun `monthly events read On the night, and with an offset take the time of that night`() {
        assertEquals("On the full moon night", describeTiming(moon(LunarEvent.FULL_MOON), Direction.AT, 0))
        assertEquals("On the new moon night", describeTiming(moon(LunarEvent.NEW_MOON), Direction.AT, 0))
        assertEquals("30 min before the full moon night time", describeTiming(moon(LunarEvent.FULL_MOON), Direction.BEFORE, 30))
        assertEquals("2 h after the new moon night time", describeTiming(moon(LunarEvent.NEW_MOON), Direction.AFTER, 120))
    }

    @Test
    fun `the sun's timing sentences are SunApp's`() {
        assertEquals("At sunrise", describeTiming(Anchor.Sun(SolarEvent.SUNRISE), Direction.AT, 0))
        assertEquals("30 min before sunset", describeTiming(Anchor.Sun(SolarEvent.SUNSET), Direction.BEFORE, 30))
        assertEquals("1 h 30 min after solar noon", describeTiming(Anchor.Sun(SolarEvent.SOLAR_NOON), Direction.AFTER, 90))
    }

    @Test
    fun `generated names are the timing, a comma and the place, for both kinds`() {
        assertEquals("At moonrise, Tokyo", defaultLabel(moon(LunarEvent.MOONRISE), Direction.AT, 0, "Tokyo"))
        assertEquals("1 h after moonset, Oslo", defaultLabel(moon(LunarEvent.MOONSET), Direction.AFTER, 60, "Oslo, Norway"))
        assertEquals("At the moon's highest point, Oslo", defaultLabel(moon(LunarEvent.LUNAR_TRANSIT), Direction.AT, 0, "Oslo, Norway"))
        assertEquals("On the full moon night, Oslo", defaultLabel(moon(LunarEvent.FULL_MOON), Direction.AT, 0, "Oslo, Norway"))
        assertEquals("30 min before the full moon night time, Oslo", defaultLabel(moon(LunarEvent.FULL_MOON), Direction.BEFORE, 30, "Oslo, Norway"))
        assertEquals("On the new moon night, Oslo", defaultLabel(moon(LunarEvent.NEW_MOON), Direction.AT, 0, "Oslo, Norway"))
        assertEquals("At sunrise, Oslo", defaultLabel(Anchor.Sun(SolarEvent.SUNRISE), Direction.AT, 0, "Oslo, Norway"))
        // A typed place's name is whole; no place gives the timing alone.
        assertEquals("At moonrise, Home, sweet home", defaultLabel(moon(LunarEvent.MOONRISE), Direction.AT, 0, "Home, sweet home", cutAtComma = false))
        assertEquals("At moonrise", defaultLabel(moon(LunarEvent.MOONRISE), Direction.AT, 0, ""))
    }

    @Test
    fun `MoonApp's own alarm names are not generated names`() {
        for (old in listOf("Moonrise alarm", "Moonset alarm", "Lunar transit alarm", "Full moon alarm", "New moon alarm")) {
            val rule = testMoonRule(label = old)
            assertFalse(old, rule.hasGeneratedLabel())
            assertEquals(old, rule.displayLabel())
        }
    }

    @Test
    fun `a moon rule's generated name follows its place and its timing, a typed name never changes`() {
        val rule = testMoonRule(
            event = LunarEvent.MOONSET,
            direction = Direction.BEFORE,
            offsetMinutes = 30,
            label = defaultLabel(moon(LunarEvent.MOONSET), Direction.BEFORE, 30, "Oslo"),
        )
        assertTrue(rule.hasGeneratedLabel())
        assertEquals("30 min before moonset, Oslo", rule.displayLabel())

        val own = testMoonRule(event = LunarEvent.MOONSET, label = "Night shift")
        assertFalse(own.hasGeneratedLabel())
        assertEquals("Night shift", own.displayLabel())

        // A typed place gets the rule its own name, and the generated label follows a rename.
        val typed = Place("59.9139 N, 10.7522 E", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = false, name = "Cabin")
        val onTyped = testMoonRule(
            event = LunarEvent.FULL_MOON,
            placeName = "Cabin",
            label = "On the full moon night, Cabin",
        ).copy(placeTyped = true)
        assertTrue(onTyped.hasGeneratedLabel())
        assertEquals("On the full moon night, Hut", onTyped.displayLabel(typed.copy(name = "Hut")))
        assertEquals("On the full moon night, Your coordinates", onTyped.displayLabel(typed.copy(name = "")))
    }

    @Test
    fun `the notification line names the moon event, with the offset in the same units as the name over it`() {
        fun line(event: LunarEvent, direction: Direction, minutes: Int) =
            AlarmService.describeAnchor(testMoonRule(event = event, direction = direction, offsetMinutes = minutes))
        assertEquals("at moonrise", line(LunarEvent.MOONRISE, Direction.AT, 0))
        assertEquals("30 min before moonset", line(LunarEvent.MOONSET, Direction.BEFORE, 30))
        assertEquals("1 h 30 min after moonrise", line(LunarEvent.MOONRISE, Direction.AFTER, 90))
        assertEquals("at the moon's highest point", line(LunarEvent.LUNAR_TRANSIT, Direction.AT, 0))
        assertEquals("20 min before the moon's highest point", line(LunarEvent.LUNAR_TRANSIT, Direction.BEFORE, 20))
        // A zero offset on a side is At, as for the sun.
        assertEquals("at moonset", line(LunarEvent.MOONSET, Direction.AFTER, 0))
        // A monthly event is a night: "on the full moon night", and with an offset the time of that night.
        assertEquals("on the full moon night", line(LunarEvent.FULL_MOON, Direction.AT, 0))
        assertEquals("on the new moon night", line(LunarEvent.NEW_MOON, Direction.AT, 0))
        assertEquals("30 min before the full moon night time", line(LunarEvent.FULL_MOON, Direction.BEFORE, 30))
        assertEquals("2 h after the new moon night time", line(LunarEvent.NEW_MOON, Direction.AFTER, 120))
    }

    @Test
    fun `the offset is told once, in the units of the generated name (4 h 31 min, not 271 min)`() {
        val rule = testRule(anchor = Anchor.Sun(SolarEvent.SUNRISE), direction = Direction.BEFORE, offsetMinutes = 271)
        assertEquals("4 h 31 min before sunrise", AlarmService.describeAnchor(rule))
        // The ring screen's two lines no longer give one offset in two units.
        assertEquals("4 h 31 min before sunrise, Oslo", defaultLabel(rule.anchor, rule.direction, 271, "Oslo, Norway"))
        assertEquals("45 min before sunrise", AlarmService.describeAnchor(rule.copy(offsetMinutes = 45)))
        assertEquals("2 h before sunrise", AlarmService.describeAnchor(rule.copy(offsetMinutes = 120)))
    }

    @Test
    fun `the sun's notification line is SunApp's for all fourteen events`() {
        val expected = mapOf(
            SolarEvent.SUNRISE to "sunrise", SolarEvent.SUNSET to "sunset", SolarEvent.SOLAR_NOON to "solar noon",
            SolarEvent.NADIR to "solar midnight", SolarEvent.SUNRISE_END to "the end of sunrise",
            SolarEvent.SUNSET_START to "the start of sunset", SolarEvent.DAWN to "dawn", SolarEvent.DUSK to "dusk",
            SolarEvent.NAUTICAL_DAWN to "nautical dawn", SolarEvent.NAUTICAL_DUSK to "nautical dusk",
            SolarEvent.NIGHT_END to "the end of night", SolarEvent.NIGHT to "nightfall",
            SolarEvent.GOLDEN_HOUR_END to "the end of the golden hour", SolarEvent.GOLDEN_HOUR to "the golden hour",
        )
        assertEquals(SolarEvent.entries.toSet(), expected.keys)
        for ((event, noun) in expected) {
            val rule = testRule(anchor = Anchor.Sun(event), direction = Direction.BEFORE, offsetMinutes = 30)
            assertEquals("30 min before $noun", AlarmService.describeAnchor(rule))
            assertEquals("at $noun", AlarmService.describeAnchor(rule.copy(direction = Direction.AT)))
        }
    }

    @Test
    fun `the ring of a moon rule carries the moon's words and no fallback line`() {
        val rule = testMoonRule(event = LunarEvent.MOONRISE, direction = Direction.BEFORE, offsetMinutes = 30)
        val ring = AlarmService.alarmFor("a", rule, 1_000L, usedFallback = false, isSnooze = false, startedAtMillis = 1_000L)
        assertEquals(AnchorKind.MOON, ring.kind)
        assertEquals("30 min before moonrise", ring.anchorText)
        assertEquals("Oslo", ring.placeName)
        assertEquals("Moon test alarm", ring.label)
        // The fallback sentence is added only for sun rules (SPEC 7.5).
        val service = TestFiles.require("${TestFiles.KOTLIN}/alarm/AlarmService.kt").readText()
        assertTrue(service.contains("sunFallback = alarm.usedFallback && alarm.kind == AnchorKind.SUN"))
    }
}
