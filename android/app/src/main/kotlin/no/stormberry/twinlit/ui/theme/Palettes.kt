package no.stormberry.twinlit.ui.theme

import androidx.compose.ui.graphics.Color
import no.stormberry.twinlit.theme.Theme

/*
 * Twinlit's palettes (SPEC 4). Plain value objects, readable anywhere: in composables through the
 * `Sun` and `Moon` facades of TwinlitTheme.kt, in drawing code through a captured token set, and
 * in tests, widgets and the ring screen directly (never through the facade: the ring screen is
 * always dark, D39). The values are SPEC 4.3; PaletteContrastTest reproduces every ratio printed
 * there from these constants.
 *
 * Night is the two source apps' own colours (SunApp 2a26f98 ui/Theme.kt, MoonApp 68c7596
 * ui/Theme.kt), so the Moon theme shows each tab as its app looks today, with ONE exception per
 * family (fix round 1, WCAG AA 4.5 to 1 in every theme, Marcos's decided rule): SunApp's TextMuted
 * (#7C8BBC) and MoonApp's TextHint (#6F7DB4) dropped to 4.45 and 4.09 to 1 where they lie on the
 * brightest spot of the sky glow. They are lightened to #8190BF and #7A87BA (a few levels per
 * channel, no visible change) and are 4.5 or more everywhere. R4 lightened two more
 * (SunNight and MoonNight TextSecondary, #8C9DCC and #7F93C8, on the sky glows' summed peak). Dusk is
 * golden hour (Marcos, 2026-11-20): a warm dark ground, warm glows, the tab's own accents. Light keeps each palette's hues in darker tones
 * on light grounds, so every text passes 4.5 to 1.
 */

/** One soft radial wash of the sky glow: its colour and the alpha it has at its centre. */
data class Glow(val colour: Color, val alpha: Float)

/**
 * SunApp's token names, plus [OnAccent] (text on a [Gold] button; SunApp used its Background), the
 * three sky-glow washes and [isLight]. The Twilight palette is a [SunTokens] as well: the ported
 * alarm screens use the same names, and `Gold` is amber there.
 */
data class SunTokens(
    val Background: Color,
    val Surface: Color,
    val Card: Color,
    val CardHover: Color,
    val Border: Color,
    val BorderActive: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val Gold: Color,
    val Orange: Color,
    val Rose: Color,
    val Blue: Color,
    val Noon: Color,
    val OnAccent: Color,
    val GlowWarm: Glow,
    val GlowGold: Glow,
    val GlowCool: Glow,
    val isLight: Boolean,
)

/**
 * MoonApp's token names, plus [OnAccent] (text on a [Silver] button), the three sky-glow washes
 * and [isLight]. [TextMuted] is never used for text (2.1 to 1 on a card); [Indigo] is never a
 * container for text (MoonApp put TextPrimary on it at 3.98 to 1, which fails WCAG AA, D32).
 */
data class MoonTokens(
    val Background: Color,
    val Surface: Color,
    val Card: Color,
    val CardHover: Color,
    val Border: Color,
    val BorderActive: Color,
    val BorderSoft: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val TextHint: Color,
    val TextSoft: Color,
    val TextMutedReadable: Color,
    val Silver: Color,
    val Blue: Color,
    val Indigo: Color,
    val Violet: Color,
    val Teal: Color,
    val NewMoon: Color,
    val Error: Color,
    val OnAccent: Color,
    val GlowTop: Glow,
    val GlowLeft: Glow,
    val GlowBottom: Glow,
    val isLight: Boolean,
)

/**
 * The apps' own brand colours for DECORATION only: the hero halos and the day-length bar (fix round
 * 1). The light palettes tune their Gold, Orange and Blue for text on a light ground (#7A5C00,
 * #A84300, #2D5C9E), which made a khaki smudge of the Sun halo and a brown day-length bar. A glow or a bar
 * carries no text, so it needs no text contrast and keeps the colours of SunApp and MoonApp in every theme.
 * Never used for text.
 */
object Brand {
    val SunGold: Color = SunNight.Gold
    val SunOrange: Color = SunNight.Orange
    val MoonBlue: Color = MoonNight.Blue
}

/** The colour set a palette takes in a theme: Sun gives Light, Twilight gives Dusk, Moon gives Night. */
enum class PaletteVariant { LIGHT, DUSK, NIGHT }

/** The three tabs of the bottom bar. */
enum class Tab { SUN, MOON, ALARMS }

/**
 * How the tabs take their palette from the theme (D37, for Marcos's review).
 *
 *  - THEMED (the built default): each tab shows its own palette in the theme's variant: Light in
 *    the Sun theme, Dusk in the Twilight theme, Night in the Moon theme (the Alarms tab: Twilight
 *    Light, Twilight Dusk, Twilight Dark).
 *  - FIXED (alternative B): the Moon tab is always Moon Night and the Alarms tab always Twilight
 *    Dark; the theme changes only the Sun tab and the chrome.
 */
enum class TabPaletteMode { THEMED, FIXED }

/** The mode this build uses. A constant, not a setting, and not an edition difference. */
val TAB_PALETTE_MODE: TabPaletteMode = TabPaletteMode.THEMED

/* ---- SPEC 4.3 values ---- */

val SunNight = SunTokens(
    Background = Color(0xFF080C18),
    Surface = Color(0xFF0E1628),
    Card = Color(0xB80E1630),
    CardHover = Color(0xD9162246),
    Border = Color(0x14FFFFFF),
    BorderActive = Color(0x59FFD700),
    TextPrimary = Color(0xFFF0F4FF),
    TextSecondary = Color(0xFF8C9DCC),
    TextMuted = Color(0xFF8190BF),
    Gold = Color(0xFFFFD700),
    Orange = Color(0xFFFF8C42),
    Rose = Color(0xFFFF6B6B),
    Blue = Color(0xFF4FC3F7),
    Noon = Color(0xFFFFF176),
    OnAccent = Color(0xFF080C18),
    GlowWarm = Glow(Color(0xFFFF8C42), 0.18f),
    GlowGold = Glow(Color(0xFFFFD700), 0.12f),
    GlowCool = Glow(Color(0xFF4FC3F7), 0.1f),
    isLight = false,
)

val SunDusk = SunTokens(
    Background = Color(0xFF2A1A0C),
    Surface = Color(0xFF35220F),
    Card = Color(0xB84A2E14),
    CardHover = Color(0xD958381A),
    Border = Color(0x1FFFD8A8),
    BorderActive = Color(0x59FFD700),
    TextPrimary = Color(0xFFFFF5E8),
    TextSecondary = Color(0xFFF0DBBE),
    TextMuted = Color(0xFFD8BB95),
    Gold = Color(0xFFFFD700),
    Orange = Color(0xFFFFAE6B),
    Rose = Color(0xFFFFA099),
    Blue = Color(0xFF8FDBFA),
    Noon = Color(0xFFFFF176),
    OnAccent = Color(0xFF2A1A0C),
    GlowWarm = Glow(Color(0xFFFFB347), 0.24f),
    GlowGold = Glow(Color(0xFFFFD27A), 0.20f),
    GlowCool = Glow(Color(0xFFFF8A80), 0.10f),
    isLight = false,
)

val SunLight = SunTokens(
    Background = Color(0xFFFFF8EA),
    Surface = Color(0xFFFFFFFF),
    Card = Color(0xD9FFFFFF),
    CardHover = Color(0xE6FFEDBF),
    Border = Color(0x1F000000),
    BorderActive = Color(0x8C7A5C00),
    TextPrimary = Color(0xFF141A30),
    TextSecondary = Color(0xFF465276),
    TextMuted = Color(0xFF566184),
    Gold = Color(0xFF7A5C00),
    Orange = Color(0xFFA84300),
    Rose = Color(0xFFB3261E),
    Blue = Color(0xFF00658F),
    Noon = Color(0xFF6E5A00),
    OnAccent = Color(0xFFFFFFFF),
    GlowWarm = Glow(Color(0xFFFF8C42), 0.18f),
    GlowGold = Glow(Color(0xFFFFD700), 0.12f),
    GlowCool = Glow(Color(0xFF4FC3F7), 0.1f),
    isLight = true,
)

val MoonNight = MoonTokens(
    Background = Color(0xFF050810),
    Surface = Color(0xFF090D1E),
    Card = Color(0xBF090D20),
    CardHover = Color(0xE00E1430),
    Border = Color(0x12FFFFFF),
    BorderActive = Color(0x4DC8D8F0),
    BorderSoft = Color(0x0DFFFFFF),
    TextPrimary = Color(0xFFE8EEFF),
    TextSecondary = Color(0xFF7F93C8),
    TextMuted = Color(0xFF3A4570),
    TextHint = Color(0xFF7A87BA),
    TextSoft = Color(0xFFA9B7DA),
    TextMutedReadable = Color(0xFF7B8FC4),
    Silver = Color(0xFFC8D8F0),
    Blue = Color(0xFF7B9FD4),
    Indigo = Color(0xFF5B72B8),
    Violet = Color(0xFF9B7FD4),
    Teal = Color(0xFF4FC3F7),
    NewMoon = Color(0xFFB0B8D4),
    Error = Color(0xFFFF6B6B),
    OnAccent = Color(0xFF050810),
    GlowTop = Glow(Color(0xFF7B9FD4), 0.16f),
    GlowLeft = Glow(Color(0xFFC8D8F0), 0.1f),
    GlowBottom = Glow(Color(0xFF9B7FD4), 0.09f),
    isLight = false,
)

val MoonDusk = MoonTokens(
    Background = Color(0xFF2E1F17),
    Surface = Color(0xFF3A281E),
    Card = Color(0xBF4A352B),
    CardHover = Color(0xE0574034),
    Border = Color(0x1FFFE2C4),
    BorderActive = Color(0x4DC8D8F0),
    BorderSoft = Color(0x0DFFFFFF),
    TextPrimary = Color(0xFFF0F3FF),
    TextSecondary = Color(0xFFC4CADD),
    TextMuted = Color(0xFF6B5646),
    TextHint = Color(0xFFB9C0D6),
    TextSoft = Color(0xFFD5DCEE),
    TextMutedReadable = Color(0xFFC4CADD),
    Silver = Color(0xFFC8D8F0),
    Blue = Color(0xFFA8C5F0),
    Indigo = Color(0xFF5B72B8),
    Violet = Color(0xFFCBB9F4),
    Teal = Color(0xFF4FC3F7),
    NewMoon = Color(0xFFC3CAE0),
    Error = Color(0xFFFFA39C),
    OnAccent = Color(0xFF2E1F17),
    GlowTop = Glow(Color(0xFFFFB27A), 0.14f),
    GlowLeft = Glow(Color(0xFFFFD27A), 0.08f),
    GlowBottom = Glow(Color(0xFFE8899A), 0.08f),
    isLight = false,
)

val MoonLight = MoonTokens(
    Background = Color(0xFFF2F5FB),
    Surface = Color(0xFFFFFFFF),
    Card = Color(0xD9FFFFFF),
    CardHover = Color(0xE6E1E8F6),
    Border = Color(0x1F0A1430),
    BorderActive = Color(0x803A4D73),
    BorderSoft = Color(0x14000000),
    TextPrimary = Color(0xFF121931),
    TextSecondary = Color(0xFF46537E),
    TextMuted = Color(0xFF9AA6C8),
    TextHint = Color(0xFF525E88),
    TextSoft = Color(0xFF2E3A63),
    TextMutedReadable = Color(0xFF46537E),
    Silver = Color(0xFF3A4D73),
    Blue = Color(0xFF2D5C9E),
    Indigo = Color(0xFF3D4F96),
    Violet = Color(0xFF6544AD),
    Teal = Color(0xFF006A93),
    NewMoon = Color(0xFF4A5371),
    Error = Color(0xFFB3261E),
    OnAccent = Color(0xFFFFFFFF),
    GlowTop = Glow(Color(0xFF7B9FD4), 0.16f),
    GlowLeft = Glow(Color(0xFFC8D8F0), 0.1f),
    GlowBottom = Glow(Color(0xFF9B7FD4), 0.09f),
    isLight = true,
)

// Twilight Dark (the Moon theme's Alarms tab, D3): dusk violet with amber, coral and lavender-blue. Its sky
// glow is Gold 14 %, Orange 12 % and Blue 8 % of these hues (SPEC 4.3), the three wash slots in that order.
val TwilightDark = SunTokens(
    Background = Color(0xFF0F0B1E),
    Surface = Color(0xFF18122E),
    Card = Color(0xB81F183A),
    CardHover = Color(0xD92A2150),
    Border = Color(0x14FFFFFF),
    BorderActive = Color(0x59FFB347),
    TextPrimary = Color(0xFFF5EEFF),
    TextSecondary = Color(0xFFB4A7D6),
    TextMuted = Color(0xFF9D90C2),
    Gold = Color(0xFFFFB347),
    Orange = Color(0xFFFF8A5C),
    Rose = Color(0xFFFF7A85),
    Blue = Color(0xFF93B6FF),
    Noon = Color(0xFFFFD98A),
    OnAccent = Color(0xFF0F0B1E),
    GlowWarm = Glow(Color(0xFFFFB347), 0.14f),
    GlowGold = Glow(Color(0xFFFF8A5C), 0.12f),
    GlowCool = Glow(Color(0xFF93B6FF), 0.08f),
    isLight = false,
)

// Twilight at dusk (R4, golden hour, Marcos 2026-11-20): burnt amber brown with amber, apricot and rose
// glows. The Twilight theme's Alarms tab and its bars when Alarms is selected; the Moon theme keeps TwilightDark.
val TwilightDusk = SunTokens(
    Background = Color(0xFF301D0C),
    Surface = Color(0xFF3C2610),
    Card = Color(0xB8553417),
    CardHover = Color(0xD9643F1C),
    Border = Color(0x1FFFD8A8),
    BorderActive = Color(0x59FFB347),
    TextPrimary = Color(0xFFFFF5E8),
    TextSecondary = Color(0xFFEBCFAB),
    TextMuted = Color(0xFFDCBD96),
    Gold = Color(0xFFFFB347),
    Orange = Color(0xFFFFAE7A),
    Rose = Color(0xFFFFA3A3),
    Blue = Color(0xFFAECBFF),
    Noon = Color(0xFFFFD98A),
    OnAccent = Color(0xFF301D0C),
    GlowWarm = Glow(Color(0xFFFFB347), 0.18f),
    GlowGold = Glow(Color(0xFFFF9A66), 0.12f),
    GlowCool = Glow(Color(0xFFFF8A9A), 0.08f),
    isLight = false,
)

val TwilightLight = SunTokens(
    Background = Color(0xFFFBF4F1),
    Surface = Color(0xFFFFFFFF),
    Card = Color(0xD9FFFFFF),
    CardHover = Color(0xE6F4E3EE),
    Border = Color(0x1F000000),
    BorderActive = Color(0x8C8F4F00),
    TextPrimary = Color(0xFF22172E),
    TextSecondary = Color(0xFF58496D),
    TextMuted = Color(0xFF66587B),
    Gold = Color(0xFF8F4F00),
    Orange = Color(0xFFA33F17),
    Rose = Color(0xFFB02A3E),
    Blue = Color(0xFF3A4FB0),
    Noon = Color(0xFF755800),
    OnAccent = Color(0xFFFFFFFF),
    GlowWarm = Glow(Color(0xFFFFB347), 0.14f),
    GlowGold = Glow(Color(0xFFFF8A5C), 0.12f),
    GlowCool = Glow(Color(0xFF93B6FF), 0.08f),
    isLight = true,
)

/**
 * The colours of the two bars, the Appearance sheet and the first-run notice (SPEC 3.1 "chrome
 * palette"): the same tokens whichever palette they come from.
 */
data class ChromeColors(
    val Background: Color,
    val Surface: Color,
    val CardHover: Color,
    val Border: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    /** The selected tab's label colour, and the button colour: Gold, Silver or the Twilight amber. */
    val Accent: Color,
    val OnAccent: Color,
    val isLight: Boolean,
)

/** Which palette variant belongs to which theme, tab and mode (pure; SPEC 4.1). */
object Palettes {

    private fun themed(theme: Theme): PaletteVariant = when (theme) {
        Theme.SUN -> PaletteVariant.LIGHT
        Theme.TWILIGHT -> PaletteVariant.DUSK
        Theme.MOON -> PaletteVariant.NIGHT
    }

    /** The variant the body of [tab] shows in [theme]. */
    fun variantFor(theme: Theme, tab: Tab, mode: TabPaletteMode = TAB_PALETTE_MODE): PaletteVariant =
        when (mode) {
            TabPaletteMode.THEMED -> themed(theme)
            TabPaletteMode.FIXED -> if (tab == Tab.SUN) themed(theme) else PaletteVariant.NIGHT
        }

    fun sun(variant: PaletteVariant): SunTokens = when (variant) {
        PaletteVariant.LIGHT -> SunLight
        PaletteVariant.DUSK -> SunDusk
        PaletteVariant.NIGHT -> SunNight
    }

    fun moon(variant: PaletteVariant): MoonTokens = when (variant) {
        PaletteVariant.LIGHT -> MoonLight
        PaletteVariant.DUSK -> MoonDusk
        PaletteVariant.NIGHT -> MoonNight
    }

    /** Twilight has three sets: Light for the Sun theme, Dusk (golden hour) for the Twilight theme, Dark for the Moon theme. */
    fun twilight(variant: PaletteVariant): SunTokens = when (variant) {
        PaletteVariant.LIGHT -> TwilightLight
        PaletteVariant.DUSK -> TwilightDusk
        PaletteVariant.NIGHT -> TwilightDark
    }

    /**
     * The chrome in [theme] with [tab] selected: the selected tab's palette in the theme's variant, in
     * every theme, so the bars, the sheet and the bottom bar's mark carry that tab's accent: gold, silver
     * or amber (R5, Marcos's D10; until R5 the Twilight theme used Twilight Dusk on every tab, D2). In
     * [TabPaletteMode.FIXED] the Moon and Alarms tabs take their fixed Night palette for the chrome as well.
     */
    fun chrome(theme: Theme, tab: Tab, mode: TabPaletteMode = TAB_PALETTE_MODE): ChromeColors {
        // FIXED (alternative B): the bars and the root ground come from the palette the tab's body is
        // drawn in, so a fixed Moon or Alarms tab is dark all through and its text sits on its own
        // ground (fix round 2; before it, the bars and the root were the Sun theme's light ones around
        // a dark body, and the Alarms title was drawn light on the light root).
        if (mode == TabPaletteMode.FIXED) {
            when (tab) {
                Tab.MOON -> return chromeOf(moon(variantFor(theme, tab, mode)))
                Tab.ALARMS -> return chromeOf(twilight(variantFor(theme, tab, mode)))
                Tab.SUN -> Unit
            }
        }
        val variant = themed(theme)
        return when (tab) {
            Tab.SUN -> chromeOf(sun(variant))
            Tab.MOON -> chromeOf(moon(variant))
            Tab.ALARMS -> chromeOf(twilight(variant))
        }
    }

    fun chromeOf(t: SunTokens) = ChromeColors(
        Background = t.Background, Surface = t.Surface, CardHover = t.CardHover, Border = t.Border,
        TextPrimary = t.TextPrimary, TextSecondary = t.TextSecondary, Accent = t.Gold,
        OnAccent = t.OnAccent, isLight = t.isLight,
    )

    fun chromeOf(t: MoonTokens) = ChromeColors(
        Background = t.Background, Surface = t.Surface, CardHover = t.CardHover, Border = t.Border,
        TextPrimary = t.TextPrimary, TextSecondary = t.TextSecondary, Accent = t.Silver,
        OnAccent = t.OnAccent, isLight = t.isLight,
    )
}
