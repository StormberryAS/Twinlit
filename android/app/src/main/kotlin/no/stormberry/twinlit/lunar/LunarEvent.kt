package no.stormberry.twinlit.lunar

/**
 * The lunar events an alarm can be anchored to (ported from MoonApp 2.0.0, 68c7596).
 *
 * Each constant is one event in the lunar cycle. The constant NAME is the storage key of a stored
 * rule and never changes; [displayName], [description] and [noun] are the words Twinlit shows
 * (sentence case, TEXTS.md G-7, G-8 and I-15; MoonApp wrote "Lunar Transit (Peak)", "Full Moon
 * Night" and "New Moon Night").
 *
 * @property displayName the editor's chip label.
 * @property description the line under the chips.
 * @property noun the event inside a sentence: "at moonrise", "30 min before the full moon night".
 * @property daily true for an event most days have (moonrise, moonset, the moon at its highest:
 *   about one day a month has none, because each comes about 50 minutes later every day); false
 *   for a monthly event (the full moon night and the new moon night, about one night in 30).
 */
enum class LunarEvent(
    val displayName: String,
    val description: String,
    val noun: String,
    val daily: Boolean,
) {
    /** The moment when the moon rises above the local horizon. */
    MOONRISE("Moonrise", "When the moon rises above the horizon", "moonrise", true),

    /** The moment when the moon sets below the local horizon. */
    MOONSET("Moonset", "When the moon sets below the horizon", "moonset", true),

    /** The moment when the moon passes the local meridian (reaches its highest point in the sky). */
    LUNAR_TRANSIT(
        "Moon at its highest",
        "When the moon reaches its highest point in the sky",
        "the moon's highest point",
        true,
    ),

    /** The night of the full moon (100% illumination), at 22:00 local mean solar time. */
    FULL_MOON(
        "Full moon night",
        "At 22:00 local solar time on the night of the full moon",
        "the full moon night",
        false,
    ),

    /** The night of the new moon (0% illumination), at 22:00 local mean solar time. */
    NEW_MOON(
        "New moon night",
        "At 22:00 local solar time on the night of the new moon",
        "the new moon night",
        false,
    ),
}
