package no.stormberry.twinlit.ui.theme

/**
 * The ratios printed in SPEC 4.3 ("Token tables with contrast"), copied from that table, never
 * computed from the Kotlin palettes: PaletteContrastTest holds the palettes to them to two decimals.
 * Each text token lists its ratio on Background, Surface, Card over Background and CardHover over
 * Background; the last number of a palette's `onAccent` is the ratio of OnAccent on its accent.
 * Written once from the SPEC table by a script; regenerate it from the SPEC, not from the app.
 *
 * Round R4 (CHANGES-R4 section 6 to 8, appendix): the golden-hour Dusk sets, the two lightened
 * Night TextSecondary tokens, the opaque Notice base and the composite sky grid (`skyBare` and
 * `skyCard`, which replace the old single-glow `skyGlow`) are regenerated from the appendix tables.
 */
internal object PaletteExpected {
    /** palette -> token -> [Background, Surface, Card, CardHover]. */
    val ratios: Map<String, Map<String, List<Double>>> = mapOf(
        "SunNight" to mapOf(
            "TextPrimary" to listOf(17.74, 16.4, 16.73, 14.71),
            "TextSecondary" to listOf(7.26, 6.71, 6.85, 6.02),
            "TextMuted" to listOf(6.2, 5.73, 5.85, 5.14),
            "Gold" to listOf(13.91, 12.86, 13.12, 11.54),
            "Orange" to listOf(8.44, 7.8, 7.96, 7.0),
            "Rose" to listOf(7.03, 6.5, 6.63, 5.83),
            "Blue" to listOf(9.74, 9.0, 9.19, 8.08),
            "Noon" to listOf(16.82, 15.55, 15.86, 13.95),
        ),
        "SunDusk" to mapOf(
            "TextPrimary" to listOf(15.56, 14.04, 12.67, 10.5),
            "TextSecondary" to listOf(12.45, 11.23, 10.14, 8.4),
            "TextMuted" to listOf(9.16, 8.26, 7.46, 6.18),
            "Gold" to listOf(11.96, 10.79, 9.74, 8.07),
            "Orange" to listOf(9.2, 8.3, 7.49, 6.21),
            "Rose" to listOf(8.58, 7.74, 6.99, 5.79),
            "Blue" to listOf(10.93, 9.86, 8.9, 7.38),
            "Noon" to listOf(14.46, 13.05, 11.77, 9.76),
        ),
        "SunLight" to mapOf(
            "TextPrimary" to listOf(16.28, 17.21, 17.07, 14.97),
            "TextSecondary" to listOf(7.28, 7.7, 7.63, 6.69),
            "TextMuted" to listOf(5.78, 6.11, 6.06, 5.31),
            "Gold" to listOf(5.92, 6.25, 6.2, 5.44),
            "Orange" to listOf(5.73, 6.06, 6.01, 5.27),
            "Rose" to listOf(6.18, 6.54, 6.48, 5.69),
            "Blue" to listOf(6.1, 6.45, 6.39, 5.61),
            "Noon" to listOf(6.36, 6.72, 6.67, 5.85),
        ),
        "MoonNight" to mapOf(
            "TextPrimary" to listOf(17.26, 16.64, 16.77, 15.79),
            "TextSecondary" to listOf(6.59, 6.35, 6.4, 6.03),
            "TextHint" to listOf(5.72, 5.52, 5.56, 5.23),
            "TextSoft" to listOf(9.99, 9.63, 9.7, 9.13),
            "TextMutedReadable" to listOf(6.26, 6.04, 6.08, 5.73),
            "Silver" to listOf(13.86, 13.36, 13.46, 12.68),
            "Blue" to listOf(7.39, 7.13, 7.18, 6.76),
            "Violet" to listOf(6.08, 5.86, 5.91, 5.56),
            "Teal" to listOf(10.0, 9.63, 9.71, 9.14),
            "NewMoon" to listOf(10.16, 9.79, 9.87, 9.29),
            "Error" to listOf(7.22, 6.96, 7.01, 6.6),
        ),
        "MoonDusk" to mapOf(
            "TextPrimary" to listOf(14.32, 12.63, 11.34, 9.26),
            "TextSecondary" to listOf(9.69, 8.55, 7.67, 6.27),
            "TextHint" to listOf(8.74, 7.7, 6.92, 5.65),
            "TextSoft" to listOf(11.55, 10.19, 9.14, 7.47),
            "TextMutedReadable" to listOf(9.69, 8.55, 7.67, 6.27),
            "Silver" to listOf(10.97, 9.68, 8.69, 7.1),
            "Blue" to listOf(8.99, 7.93, 7.12, 5.82),
            "Violet" to listOf(8.9, 7.85, 7.04, 5.76),
            "Teal" to listOf(7.91, 6.98, 6.26, 5.12),
            "NewMoon" to listOf(9.7, 8.55, 7.68, 6.27),
            "Error" to listOf(8.28, 7.3, 6.56, 5.36),
        ),
        "MoonLight" to mapOf(
            "TextPrimary" to listOf(15.9, 17.37, 17.19, 14.27),
            "TextSecondary" to listOf(6.87, 7.5, 7.43, 6.16),
            "TextHint" to listOf(5.8, 6.33, 6.27, 5.2),
            "TextSoft" to listOf(10.11, 11.04, 10.93, 9.07),
            "TextMutedReadable" to listOf(6.87, 7.5, 7.43, 6.16),
            "Silver" to listOf(7.73, 8.44, 8.35, 6.93),
            "Blue" to listOf(6.13, 6.7, 6.63, 5.5),
            "Violet" to listOf(6.45, 7.04, 6.97, 5.78),
            "Teal" to listOf(5.52, 6.03, 5.97, 4.95),
            "NewMoon" to listOf(6.95, 7.59, 7.51, 6.24),
            "Error" to listOf(5.99, 6.54, 6.47, 5.37),
        ),
        "TwilightDark" to mapOf(
            "TextPrimary" to listOf(17.07, 15.93, 15.56, 13.6),
            "TextSecondary" to listOf(8.68, 8.1, 7.91, 6.92),
            "TextMuted" to listOf(6.63, 6.18, 6.04, 5.28),
            "Gold" to listOf(10.85, 10.12, 9.89, 8.64),
            "Orange" to listOf(8.32, 7.76, 7.58, 6.63),
            "Rose" to listOf(7.7, 7.19, 7.02, 6.14),
            "Blue" to listOf(9.55, 8.91, 8.7, 7.61),
            "Noon" to listOf(14.3, 13.34, 13.03, 11.39),
        ),
        "TwilightDusk" to mapOf(
            "TextPrimary" to listOf(14.91, 13.19, 11.46, 9.39),
            "TextSecondary" to listOf(10.75, 9.51, 8.26, 6.77),
            "TextMuted" to listOf(9.0, 7.96, 6.92, 5.67),
            "Gold" to listOf(9.02, 7.98, 6.94, 5.68),
            "Orange" to listOf(8.87, 7.85, 6.82, 5.58),
            "Rose" to listOf(8.43, 7.46, 6.48, 5.31),
            "Blue" to listOf(9.78, 8.66, 7.52, 6.16),
            "Noon" to listOf(11.9, 10.53, 9.14, 7.49),
        ),
        "TwilightLight" to mapOf(
            "TextPrimary" to listOf(15.7, 17.07, 16.81, 14.08),
            "TextSecondary" to listOf(7.46, 8.11, 7.99, 6.69),
            "TextMuted" to listOf(5.95, 6.47, 6.37, 5.34),
            "Gold" to listOf(5.88, 6.39, 6.29, 5.27),
            "Orange" to listOf(5.89, 6.4, 6.3, 5.28),
            "Rose" to listOf(5.95, 6.47, 6.37, 5.34),
            "Blue" to listOf(6.6, 7.18, 7.07, 5.92),
            "Noon" to listOf(6.13, 6.66, 6.56, 5.5),
        ),
    )

    /** OnAccent on the palette's accent (Gold for the Sun and Twilight palettes, Silver for the Moon ones). */
    val onAccent: Map<String, Double> = mapOf(
        "SunNight" to 13.91,
        "SunDusk" to 11.96,
        "SunLight" to 6.25,
        "MoonNight" to 13.86,
        "MoonDusk" to 10.97,
        "MoonLight" to 8.44,
        "TwilightDark" to 10.85,
        "TwilightDusk" to 9.02,
        "TwilightLight" to 6.39,
    )

    /** Lowest ratio of every text token on Card composited over Surface (SPEC 4.3, "Further pairs", row 1). */
    val cardOverSurface: Map<String, Double> = mapOf(
        "SunNight" to 5.69,
        "SunDusk" to 6.73,
        "SunLight" to 6.06,
        "MoonNight" to 5.51,
        "MoonDusk" to 6.02,
        "MoonLight" to 6.03,
        "TwilightDark" to 5.88,
        "TwilightDusk" to 6.26,
        "TwilightLight" to 6.39,
    )

    /** Lowest ratio in the Notice: the accent title and the TextSecondary body on the accent at 10 per cent over an opaque Surface base (R4; the Notice draws its own Surface first). */
    val notice: Map<String, Double> = mapOf(
        "SunNight" to 5.46,
        "SunDusk" to 6.31,
        "SunLight" to 5.22,
        "MoonNight" to 5.24,
        "MoonDusk" to 5.83,
        "MoonLight" to 5.21,
        "TwilightDark" to 6.22,
        "TwilightDusk" to 6.05,
        "TwilightLight" to 5.5,
    )

    /** The footer's closing notes: TextSecondary at 85 per cent over Background (CHANGES-R4 section 8, FooterTest). */
    val footer85: Map<String, Double> = mapOf(
        "SunNight" to 5.5,
        "SunDusk" to 9.31,
        "SunLight" to 5.01,
        "MoonNight" to 4.99,
        "MoonDusk" to 7.38,
        "MoonLight" to 4.79,
        "TwilightDark" to 6.51,
        "TwilightDusk" to 8.13,
        "TwilightLight" to 5.11,
    )

    /** Lowest ratio of the bare-sky text tokens over the composite sky grid (SPEC 4.3, R4): 37 x 37 points on six box sizes, both gradient models, the lower value. */
    val skyBare: Map<String, Double> = mapOf(
        "SunNight" to 4.63,
        "SunDusk" to 5.31,
        "SunLight" to 5.81,
        "MoonNight" to 4.65,
        "MoonDusk" to 6.46,
        "MoonLight" to 5.64,
        "TwilightDark" to 4.63,
        "TwilightDusk" to 5.45,
        "TwilightLight" to 4.84,
    )

    /** Lowest ratio of every text token on its Card over the composite sky grid (SPEC 4.3, R4). */
    val skyCard: Map<String, Double> = mapOf(
        "SunNight" to 5.27,
        "SunDusk" to 5.47,
        "SunLight" to 5.82,
        "MoonNight" to 5.2,
        "MoonDusk" to 5.6,
        "MoonLight" to 5.79,
        "TwilightDark" to 5.48,
        "TwilightDusk" to 5.62,
        "TwilightLight" to 6.14,
    )
}
