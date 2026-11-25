package no.stormberry.twinlit.theme

/**
 * What the user picks in the Appearance sheet (SPEC 4.1: the "choice"). The declaration order is
 * the order the sheet lists them in, which is the order Marcos approved (2026-11-19).
 *
 * The texts are APPROVED verbatim (TEXTS.md A-0 to A-5): [TITLE] and each [description]. They are
 * plain strings in this pure file, not resources, so that AppearanceTextsTest can pin every
 * character. The word before the first colon is the option's name; the sheet may set it in
 * semibold, but no character changes ([name]).
 */
enum class Appearance(val key: String, val description: String) {
    SUN("sun", "Sun: light colours, easy to read in daylight."),
    MOON("moon", "Moon: dark colours, gentle on the eyes at night."),
    TWILIGHT("twilight", "Twilight: a mix of both: dark, with warm accents."),
    AUTO(
        "auto",
        "Auto: follows the sun's daily rhythm at your chosen place: Sun for the ten hours around " +
            "solar noon, Twilight for two hours on each side, Moon for the rest.",
    ),
    SYSTEM("system", "System: follows your phone's light or dark setting.");

    /** The option's name, the text before the first colon ("Sun", "Moon", "Twilight", "Auto", "System"). */
    val label: String get() = description.substringBefore(':')

    /** The rest of the text after "Name:", with the leading space removed. */
    val explanation: String get() = description.substringAfter(": ")

    companion object {
        /** TEXTS.md A-0: the menu title, also the spoken label of the top bar's button. */
        const val TITLE = "Appearance"

        /** TEXTS.md A-6 (approved by Marcos 2026-11-22): shown under the Auto row while no place is chosen. */
        const val AUTO_NOTE = "No place chosen yet, so Auto follows System for now."

        /** The default (Marcos, 2026-11-17): Auto, which falls back to System until a place is chosen. */
        val DEFAULT = AUTO

        /**
         * The choice stored under [key]. A missing or unknown value reads as [DEFAULT]: a later
         * version that renames a choice must never crash on an old install, or mean something else.
         */
        fun fromKey(key: String?): Appearance = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * What is in effect right now (SPEC 4.1: the "theme"). Sun gives each tab its light variant,
 * Twilight its dusk variant, Moon its night variant (the two apps' own colours).
 */
enum class Theme { SUN, MOON, TWILIGHT }
