package no.stormberry.twinlit.alarm.model

import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent

/**
 * Which body an alarm follows. Stored as [key] (`"sun"` or `"moon"`), carried by every ring
 * (D38) so that the look, the small icon and the words of a ring, a snooze and a Missed notice
 * never depend on looking the rule up by id.
 */
enum class AnchorKind(val key: String) {
    SUN("sun"),
    MOON("moon");

    companion object {
        /** The kind stored as [key]; a missing or unknown key reads as [SUN] (the forward-compatibility policy). */
        fun fromStored(key: String?): AnchorKind = entries.firstOrNull { it.key == key } ?: SUN

        /** The kind stored as [key], or null when [key] is missing or names no kind. */
        fun parse(key: String?): AnchorKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What an alarm is anchored to: an event of the sun or an event of the moon (SPEC 7.1).
 *
 * The editor offers [SolarEvent.SUNRISE], [SolarEvent.SOLAR_NOON] and [SolarEvent.SUNSET] and the
 * five [LunarEvent]s, but a stored sun rule may hold any of the fourteen solar events, and keeps
 * it exactly as stored.
 */
sealed interface Anchor {

    /** The body this anchor follows. */
    val kind: AnchorKind

    /** The enum name of the event, which is what the store writes under `"anchor"`. */
    val eventName: String

    /** An event of the sun. */
    data class Sun(val event: SolarEvent) : Anchor {
        override val kind: AnchorKind get() = AnchorKind.SUN
        override val eventName: String get() = event.name
    }

    /** An event of the moon. */
    data class Moon(val event: LunarEvent) : Anchor {
        override val kind: AnchorKind get() = AnchorKind.MOON
        override val eventName: String get() = event.name
    }

    companion object {
        /** The first anchor of [kind]: sunrise, or moonrise. */
        fun first(kind: AnchorKind): Anchor = when (kind) {
            AnchorKind.SUN -> Sun(SolarEvent.SUNRISE)
            AnchorKind.MOON -> Moon(LunarEvent.MOONRISE)
        }

        /**
         * The anchor of [kind] whose event is named [eventName], or null when that kind has no
         * event of that name (a moon event name under kind sun, an unknown name).
         */
        fun of(kind: AnchorKind, eventName: String?): Anchor? = when (kind) {
            AnchorKind.SUN -> SolarEvent.entries.firstOrNull { it.name == eventName }?.let(::Sun)
            AnchorKind.MOON -> LunarEvent.entries.firstOrNull { it.name == eventName }?.let(::Moon)
        }
    }
}
