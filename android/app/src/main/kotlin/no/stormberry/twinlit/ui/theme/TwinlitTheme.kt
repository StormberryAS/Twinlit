package no.stormberry.twinlit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import no.stormberry.twinlit.R

/**
 * The token sets in effect for the code below the nearest [TwinlitTheme]. **Their defaults are the
 * Night palettes** (D39), so a composable outside any provider (a preview, a dialog window that
 * loses the locals) stays dark and looks like the original app. The ring screen and the widgets
 * never read these at all: they use [SunNight], [MoonNight] and their own fixed colours.
 */
val LocalSunTokens = staticCompositionLocalOf { SunNight }
val LocalMoonTokens = staticCompositionLocalOf { MoonNight }

/**
 * SunApp's token facade: `Sun.Gold` in a ported composable still compiles and now reads the
 * palette of the nearest [TwinlitTheme] (the Sun palette on the Sun tab, the Twilight palette on
 * the Alarms tab). A getter is `@Composable`, so it cannot be called from a `DrawScope` lambda or
 * a plain function: there the code captures `LocalSunTokens.current` first (SPEC 4.2).
 */
object Sun {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Surface
    val Card: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Card
    val CardHover: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.CardHover
    val Border: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Border
    val BorderActive: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.BorderActive
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.TextPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.TextSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.TextMuted
    val Gold: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Gold
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Orange
    val Rose: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Rose
    val Blue: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Blue
    val Noon: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.Noon
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalSunTokens.current.OnAccent
}

/** MoonApp's token facade, with the same rules as [Sun]. */
object Moon {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Surface
    val Card: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Card
    val CardHover: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.CardHover
    val Border: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Border
    val BorderActive: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.BorderActive
    val BorderSoft: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.BorderSoft
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.TextPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.TextSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.TextMuted
    val TextHint: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.TextHint
    val TextSoft: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.TextSoft
    val TextMutedReadable: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.TextMutedReadable
    val Silver: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Silver
    val Blue: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Blue
    val Indigo: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Indigo
    val Violet: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Violet
    val Teal: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Teal
    val NewMoon: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.NewMoon
    val Error: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.Error
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMoonTokens.current.OnAccent
}

/*
 * Every scheme below also sets the container roles (primaryContainer, secondaryContainer and the
 * surfaceContainer family). Left at Material's baseline they showed purple in the alarm editor's
 * time picker (the hour and minute selectors and the dial), off every Twinlit palette (fix round 1).
 * The pickers, dialogs and sheets now take the palette's Surface, Card and CardHover instead.
 */

/**
 * The Material 3 scheme of a Sun-style palette (SunApp's, S-map 10): primary Gold, secondary
 * Blue, tertiary Orange, error Rose. For the Night palettes this equals SunApp's own scheme.
 */
internal fun sunColorScheme(t: SunTokens): ColorScheme =
    if (t.isLight) {
        lightColorScheme(
            primary = t.Gold, onPrimary = t.OnAccent, secondary = t.Blue, onSecondary = t.OnAccent,
            tertiary = t.Orange, background = t.Background, onBackground = t.TextPrimary,
            surface = t.Surface, onSurface = t.TextPrimary, surfaceVariant = t.Card,
            onSurfaceVariant = t.TextSecondary, outline = t.Border, error = t.Rose,
            primaryContainer = t.CardHover, onPrimaryContainer = t.Gold,
            secondaryContainer = t.CardHover, onSecondaryContainer = t.TextPrimary,
            surfaceContainerLowest = t.Surface, surfaceContainerLow = t.Surface, surfaceContainer = t.Surface,
            surfaceContainerHigh = t.Surface, surfaceContainerHighest = t.Card,
        )
    } else {
        darkColorScheme(
            primary = t.Gold, onPrimary = t.OnAccent, secondary = t.Blue, onSecondary = t.OnAccent,
            tertiary = t.Orange, background = t.Background, onBackground = t.TextPrimary,
            surface = t.Surface, onSurface = t.TextPrimary, surfaceVariant = t.Card,
            onSurfaceVariant = t.TextSecondary, outline = t.Border, error = t.Rose,
            primaryContainer = t.CardHover, onPrimaryContainer = t.Gold,
            secondaryContainer = t.CardHover, onSecondaryContainer = t.TextPrimary,
            surfaceContainerLowest = t.Surface, surfaceContainerLow = t.Surface, surfaceContainer = t.Surface,
            surfaceContainerHigh = t.Surface, surfaceContainerHighest = t.Card,
        )
    }

/** The Material 3 scheme of a Moon palette (MoonApp's): primary Silver, secondary Blue, tertiary Violet. */
internal fun moonColorScheme(t: MoonTokens): ColorScheme =
    if (t.isLight) {
        lightColorScheme(
            primary = t.Silver, onPrimary = t.OnAccent, secondary = t.Blue, onSecondary = t.OnAccent,
            tertiary = t.Violet, background = t.Background, onBackground = t.TextPrimary,
            surface = t.Surface, onSurface = t.TextPrimary, surfaceVariant = t.Card,
            onSurfaceVariant = t.TextSecondary, outline = t.Border, error = t.Error,
            primaryContainer = t.CardHover, onPrimaryContainer = t.Silver,
            secondaryContainer = t.CardHover, onSecondaryContainer = t.TextPrimary,
            surfaceContainerLowest = t.Surface, surfaceContainerLow = t.Surface, surfaceContainer = t.Surface,
            surfaceContainerHigh = t.Surface, surfaceContainerHighest = t.Card,
        )
    } else {
        darkColorScheme(
            primary = t.Silver, onPrimary = t.OnAccent, secondary = t.Blue, onSecondary = t.OnAccent,
            tertiary = t.Violet, background = t.Background, onBackground = t.TextPrimary,
            surface = t.Surface, onSurface = t.TextPrimary, surfaceVariant = t.Card,
            onSurfaceVariant = t.TextSecondary, outline = t.Border, error = t.Error,
            primaryContainer = t.CardHover, onPrimaryContainer = t.Silver,
            secondaryContainer = t.CardHover, onSecondaryContainer = t.TextPrimary,
            surfaceContainerLowest = t.Surface, surfaceContainerLow = t.Surface, surfaceContainer = t.Surface,
            surfaceContainerHigh = t.Surface, surfaceContainerHighest = t.Card,
        )
    }

/** The chrome (bars, sheet, notice) as a Material scheme, for components that read MaterialTheme. */
internal fun chromeColorScheme(c: ChromeColors): ColorScheme =
    if (c.isLight) {
        lightColorScheme(
            primary = c.Accent, onPrimary = c.OnAccent, background = c.Background,
            onBackground = c.TextPrimary, surface = c.Surface, onSurface = c.TextPrimary,
            surfaceVariant = c.CardHover, onSurfaceVariant = c.TextSecondary, outline = c.Border,
            primaryContainer = c.CardHover, onPrimaryContainer = c.Accent,
            secondaryContainer = c.CardHover, onSecondaryContainer = c.TextPrimary,
            surfaceContainerLowest = c.Surface, surfaceContainerLow = c.Surface, surfaceContainer = c.Surface,
            surfaceContainerHigh = c.Surface, surfaceContainerHighest = c.CardHover,
        )
    } else {
        darkColorScheme(
            primary = c.Accent, onPrimary = c.OnAccent, background = c.Background,
            onBackground = c.TextPrimary, surface = c.Surface, onSurface = c.TextPrimary,
            surfaceVariant = c.CardHover, onSurfaceVariant = c.TextSecondary, outline = c.Border,
            primaryContainer = c.CardHover, onPrimaryContainer = c.Accent,
            secondaryContainer = c.CardHover, onSecondaryContainer = c.TextPrimary,
            surfaceContainerLowest = c.Surface, surfaceContainerLow = c.Surface, surfaceContainer = c.Surface,
            surfaceContainerHigh = c.Surface, surfaceContainerHighest = c.CardHover,
        )
    }

/**
 * Inter, the typeface the Stormberry sites self-host from fonts/inter/, so the APK and the sites
 * set text in the same face. Four static instances of that same InterVariable.woff2, one per
 * weight this app uses, live in res/font and are regenerated by android/tools/build-inter-fonts.py (milestone M7).
 * Bundled in the APK: no downloadable-fonts provider, no network. A weight not listed here
 * would be faked by synthesis, so add an instance before using one.
 *
 * Clock readings and coordinates stay FontFamily.Monospace where the screens ask for it, so
 * the digits line up; everything else is Inter.
 */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Every Material 3 text style, set in [family]. Text with no explicit style inherits bodyLarge. */
private fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

private val TwinlitTypography = Typography().withFontFamily(Inter).run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** The Sun palette, or the Twilight palette (also a [SunTokens]), for the content below. */
@Composable
fun TwinlitTheme(tokens: SunTokens, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSunTokens provides tokens) {
        MaterialTheme(colorScheme = sunColorScheme(tokens), typography = TwinlitTypography, content = content)
    }
}

/** The Moon palette for the content below. */
@Composable
fun TwinlitTheme(tokens: MoonTokens, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMoonTokens provides tokens) {
        MaterialTheme(colorScheme = moonColorScheme(tokens), typography = TwinlitTypography, content = content)
    }
}

/** The chrome palette for the bars and the sheet. */
@Composable
fun TwinlitChromeTheme(chrome: ChromeColors, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = chromeColorScheme(chrome), typography = TwinlitTypography, content = content)
}
