package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.solar.SolarEvent
import java.util.Locale

/*
 * The words of an alarm's timing and its generated name, for both kinds (SPEC 7.8, TEXTS.md G-14
 * and the generated names under G).
 *
 * SunApp kept these as `internal` functions of its rule editor screen, so that the name made by the
 * app could be told from a name the user typed (`AlarmRule.hasGeneratedLabel`) by the data layer.
 * They live here, pure and on the JVM, so that this milestone can test them; the screens of M5
 * call them. MoonApp's own names ("Moonrise alarm" and its 1.0.8 repair) are not carried over: its
 * "follow the event while the name is generated" rule is SunApp's S11, which is this.
 */

/** "Sunrise", "Solar noon", "Sunset", and a readable name for the eleven the picker hides; the moon events as the chip labels. */
internal fun anchorLabel(anchor: Anchor): String = when (anchor) {
    is Anchor.Sun -> anchorLabel(anchor.event)
    is Anchor.Moon -> anchor.event.displayName
}

/** The solar events' names (SunApp's `anchorLabel`). */
internal fun anchorLabel(anchor: SolarEvent): String = when (anchor) {
    SolarEvent.SUNRISE -> "Sunrise"
    SolarEvent.SOLAR_NOON -> "Solar noon"
    SolarEvent.SUNSET -> "Sunset"
    SolarEvent.SUNRISE_END -> "Sunrise ends"
    SolarEvent.SUNSET_START -> "Sunset starts"
    SolarEvent.DAWN -> "Civil dawn"
    SolarEvent.DUSK -> "Civil dusk"
    SolarEvent.NAUTICAL_DAWN -> "Nautical dawn"
    SolarEvent.NAUTICAL_DUSK -> "Nautical dusk"
    SolarEvent.NIGHT_END -> "Astronomical dawn"
    SolarEvent.NIGHT -> "Astronomical dusk"
    SolarEvent.GOLDEN_HOUR_END -> "Golden hour ends"
    SolarEvent.GOLDEN_HOUR -> "Golden hour"
    SolarEvent.NADIR -> "Solar midnight"
}

/** "1 h 30 min", "45 min", "0 min". Spaced units, because "1h30" is not English. */
internal fun formatOffset(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/**
 * The event as it reads inside a sentence: "sunrise", "solar noon", "moonrise", "the moon at its
 * highest", "the full moon night" (TEXTS.md I-14 and I-15).
 */
internal fun anchorNoun(anchor: Anchor): String = when (anchor) {
    is Anchor.Sun -> anchorLabel(anchor.event).lowercase(Locale.UK)
    is Anchor.Moon -> anchor.event.noun
}

/**
 * "30 min before sunrise", "At sunset", "At moonrise", "1 h after the moon at its highest", "On the
 * full moon night", "30 min before the full moon night time". The sentence the list row and the
 * editor both show.
 *
 * A monthly moon event is a night, not an instant on the clock the user thinks of, so with no
 * offset it reads "On the full moon night" and with an offset "30 min before the full moon night
 * time": the offset is taken from 22:00 local solar time on that night.
 */
internal fun describeTiming(anchor: Anchor, direction: Direction, offsetMinutes: Int): String {
    val event = anchorNoun(anchor)
    val atEvent = direction == Direction.AT || offsetMinutes == 0
    val monthly = anchor is Anchor.Moon && !anchor.event.daily
    return when {
        atEvent && monthly -> "On $event"
        atEvent -> "At $event"
        else -> {
            val subject = if (monthly) "$event time" else event
            val side = if (direction == Direction.BEFORE) "before" else "after"
            "${formatOffset(offsetMinutes)} $side $subject"
        }
    }
}

/**
 * The name a rule gets when the user does not type one: "<timing>, <place>". A catalogue place is
 * cut at its first comma ("Oslo, Norway" is "Oslo"); the name of a typed place is whole
 * ([cutAtComma] false), because a comma in a name the user chose is not the end of a city.
 */
internal fun defaultLabel(
    anchor: Anchor,
    direction: Direction,
    offsetMinutes: Int,
    placeName: String,
    cutAtComma: Boolean = true,
): String {
    val timing = describeTiming(anchor, direction, offsetMinutes)
    val place = if (cutAtComma) placeName.substringBefore(',') else placeName
    return if (placeName.isBlank()) timing else "$timing, $place"
}
