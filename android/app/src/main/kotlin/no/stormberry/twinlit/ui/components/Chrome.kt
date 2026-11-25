package no.stormberry.twinlit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import no.stormberry.twinlit.R
import no.stormberry.twinlit.data.InputMode
import no.stormberry.twinlit.ui.pickerColours
import no.stormberry.twinlit.ui.theme.Brand
import no.stormberry.twinlit.ui.theme.Glow
import no.stormberry.twinlit.ui.theme.LocalMoonTokens
import no.stormberry.twinlit.ui.theme.LocalSunTokens
import no.stormberry.twinlit.ui.theme.Moon
import no.stormberry.twinlit.ui.theme.SkyGlowGeometry
import no.stormberry.twinlit.ui.theme.Sun
import no.stormberry.twinlit.ui.theme.Wash
import java.util.Locale

/**
 * Shared chrome of the Sun tab and the place picker, ported from SunApp's `Chrome.kt` (milestone
 * M1 brought the pieces the shared picker needs; M3 adds the hero and the notice box; M4 adds the Moon tab's hero, [MoonHero], which is MoonApp's `AppHero`).
 *
 * Two changes from SunApp, both forced by the palette now being a token set that changes with the
 * theme: the colours come from the `Sun` facade (composable getters), so the drawing functions
 * that run inside a `DrawScope` lambda cannot read them and take the colours as parameters
 * instead (SPEC 4.2, the DrawScope rule).
 */

/** The small upper-case captions the web app puts above each field group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = pickerColours().label,
        fontSize = 11.sp,
        modifier = modifier,
    )
}

/**
 * A translucent card, the Compose equivalent of the web app's `.card` rule.
 *
 * Written as a plain Box rather than a Material3 [androidx.compose.material3.Card]
 * so the fill can be the exact `--bg-card` alpha from style.css. Card would tint
 * it with its own elevation overlay and the two surfaces would drift apart.
 */
@Composable
fun SunCard(
    modifier: Modifier = Modifier,
    borderColour: Color = Sun.Border,
    content: @Composable () -> Unit,
) = InputCard(fill = Sun.Card, border = borderColour, modifier = modifier, content = content)

/**
 * The input card of the Sun and Moon tabs (R5, D14), SunApp's card: 20 dp corners, a 1 dp edge and
 * 18 dp padding, in the colours of the tab it sits on. [SunCard] is this card in the Sun tokens.
 */
@Composable
fun InputCard(fill: Color, border: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill, shape)
            .border(1.dp, border, shape)
            .padding(18.dp),
    ) {
        content()
    }
}

/**
 * The location-mode selector: two options, where the web app has three.
 *
 * "My Device" is left out because Twinlit asks for no location permission, and a disabled control
 * would invite the user to look for a setting that does not exist.
 *
 * Word-safe (R8, D93): the two labels share one row at 14 sp; when "Coordinates" does not fit its half at
 * that size (font scale 1.3 and more on a phone) both labels step down together, one 0.5 sp step at a time,
 * to [MODE_CHIP_MIN_SIZE], 12 sp; when it does not fit even then (font scale 2.0), the chips stand one over
 * the other at full width and 14 sp. A label is never on two lines and never broken.
 */
@Composable
fun ModeTabs(
    mode: InputMode,
    onSelect: (InputMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val base = fitStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
        val halfPx = with(density) { (constraints.maxWidth - MODE_CHIP_GAP.toPx()) / 2f - MODE_CHIP_CHROME.toPx() }
        val size = modeChipSize(MODE_CHIP_LABELS, base.fontSize.value, halfPx) { label, sp ->
            measurer.lineWidth(label, base.copy(fontSize = sp.sp))
        }
        val select = { chosen: InputMode -> onSelect(chosen) }
        if (size != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(MODE_CHIP_GAP), modifier = Modifier.fillMaxWidth()) {
                ModeChip("City search", mode == InputMode.CITY, size.sp, { select(InputMode.CITY) }, Modifier.weight(1f))
                ModeChip("Coordinates", mode == InputMode.COORDINATES, size.sp, { select(InputMode.COORDINATES) }, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(MODE_CHIP_GAP), modifier = Modifier.fillMaxWidth()) {
                ModeChip("City search", mode == InputMode.CITY, base.fontSize, { select(InputMode.CITY) }, Modifier.fillMaxWidth())
                ModeChip("Coordinates", mode == InputMode.COORDINATES, base.fontSize, { select(InputMode.COORDINATES) }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The labels of the mode chips, as they stand on both tabs. */
internal val MODE_CHIP_LABELS = listOf("City search", "Coordinates")

/** The smallest size the two mode labels step down to before the chips are stacked (R8, D93). */
internal const val MODE_CHIP_MIN_SIZE = 12f
internal val MODE_CHIP_GAP = 8.dp

/** A chip's own width beside its label: 32 dp (16 dp of padding on each side, measured on a device) and 4 dp of slack. */
internal val MODE_CHIP_CHROME = 36.dp

/**
 * The largest label size in sp, from [maxSp] down to [MODE_CHIP_MIN_SIZE] in 0.5 sp steps, at which every
 * one of [labels] fits [availablePx] on one line, or null when none does (the chips are then stacked).
 * [widthOf] measures a label at a size in sp.
 */
internal fun modeChipSize(labels: List<String>, maxSp: Float, availablePx: Float, widthOf: (String, Float) -> Float): Float? {
    var sp = maxSp
    while (sp >= MODE_CHIP_MIN_SIZE) {
        if (labels.all { widthOf(it, sp) <= availablePx }) return sp
        sp -= 0.5f
    }
    return null
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    fontSize: TextUnit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = pickerColours()
    FilterChip(
        selected = selected,
        onClick = { if (!selected) onSelect() },
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = c.field,
            labelColor = c.textSecondary,
            selectedContainerColor = c.selectedFill,
            selectedLabelColor = c.accent,
        ),
        border = modeChipBorder(selected),
        modifier = modifier,
    )
}

/**
 * The state cue of the place mode chips (R5, D13 and D14): the selected chip has a 2 dp ring in the
 * picker's accent, the unselected one a 1 dp outline in its label colour (70 per cent in the light
 * palettes, 80 per cent in the dark ones; R6), in every theme and on every tab. The ring is 5.3 to 1 or
 * more against the card and the chip's own fill, the outline 3.0 to 1 or more against the card outside
 * it and the chip's fill inside it, over the sky glows (ModeChipCueTest); the fills alone differ by 1.06
 * to 1.47 to 1.
 */
@Composable
internal fun modeChipBorder(selected: Boolean): BorderStroke {
    val c = pickerColours()
    return if (selected) BorderStroke(2.dp, c.accent) else BorderStroke(1.dp, c.modeOutline)
}

/**
 * The outline of an unselected chip in the LIGHT palettes (fix round 1). A white chip on a white or
 * cream card had no visible edge (1.0 to 1; WCAG 1.4.11 asks 3 to 1 for the boundary of a control).
 * [TextMuted] at 70 per cent over white is 3.3 to 1. A selected chip is told by its fill and its
 * accent label, and the dark palettes' chips stand out by their fill, so both keep no outline.
 */
@Composable
internal fun controlBorder(selected: Boolean): BorderStroke? {
    val outline = pickerColours().chipOutline
    return if (outline != null && !selected) BorderStroke(1.dp, outline) else null
}

/**
 * The Sun tab's mark (SunApp's `ic_app_logo`, byte-identical as `ic_sun_mark`) at 64 dp with the
 * gold halo the web app puts behind it. [SunHero] puts the title and the tagline under it.
 */
@Composable
fun SunHeroMark(modifier: Modifier = Modifier) {
    // SunApp's own gold (#FFD700) in every variant: the light palette's Gold (#7A5C00) is tuned for
    // text and made a khaki smudge of the halo (fix round 1). A decorative wash needs no text contrast.
    val gold = Brand.SunGold
    Image(
        painter = painterResource(R.drawable.ic_sun_mark),
        // Decorative: the title beside it is the accessible name.
        contentDescription = null,
        modifier = modifier
            .size(64.dp)
            .drawBehind { drawLogoGlow(gold) },
    )
}

/**
 * The Sun tab's hero (SunApp's `AppHero`): the mark centred with its glow, the title large
 * beneath it and a centred tagline under the title, the same order and proportions (64 dp mark,
 * 30 sp title, 16 dp between them). The title is "Sun times" (D-1, approved by Marcos 2026-11-22: SunApp showed its own
 * name there, and the top bar now carries "Twinlit"). The tagline is the website's, word for word,
 * set the way the website sets it: upper case, tracked out by 0.06 em, on two lines.
 */
@Composable
fun SunHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SunHeroMark()
        Text(
            text = SUN_HERO_TITLE,
            color = Sun.TextPrimary,
            fontSize = 30.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            // The web's .tagline rule is text-transform: uppercase; the source text keeps the
            // lower-case "anywhere on Earth" exactly as index.html has it.
            text = HERO_TAGLINE.uppercase(),
            color = Sun.TextSecondary,
            fontSize = 15.2.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.06.em,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 544.dp),
        )
    }
}

/**
 * The Moon tab's hero (MoonApp's `AppHero`, CheckLink's hero re-built natively): the moon mark
 * on its night chip (D36) with MoonApp's blue halo (alpha 0.34, SPEC 6.1), the title large and
 * centred, and the website's tagline under it in capitals. Same sizes as [SunHero]: 64 dp mark,
 * 30 sp title, 15.2 sp tagline, 16 dp between the three.
 */
@Composable
fun MoonHero(modifier: Modifier = Modifier) {
    // MoonApp's own halo colour and the bare mark on the dark palettes; the night chip only on a
    // light ground (MoonMark). The halo keeps MoonApp's blue in every variant: a decorative wash
    // needs no text contrast, and the light variant's text-tuned Blue (#2D5C9E) made it a dull smudge.
    val tokens = LocalMoonTokens.current
    val blue = Brand.MoonBlue
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MoonMark(
            size = 64.dp,
            chip = tokens.isLight,
            modifier = Modifier.drawBehind { drawLogoGlow(blue, alpha = 0.34f) },
        )
        Text(
            text = MOON_HERO_TITLE,
            color = Moon.TextPrimary,
            fontSize = 30.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            // The web hero sets this in capitals with CSS; the constant stays the page's own text.
            text = MOON_HERO_TAGLINE.uppercase(Locale.ROOT),
            color = Moon.TextSecondary,
            fontSize = 15.2.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.06.em,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 544.dp),
        )
    }
}

/** TEXTS.md E-1 (approved by Marcos 2026-11-22): MoonApp's own name is "MoonApp", and the top bar now carries "Twinlit". */
const val MOON_HERO_TITLE = "Moon times"

/** TEXTS.md E-2 (SHIPPED): `<p class="tagline">` of moon.stormberry.as, with its `<br>` as a newline. */
const val MOON_HERO_TAGLINE = "MOONRISE \u00B7 TRANSIT \u00B7 MOONSET\nanywhere on Earth"

/** TEXTS.md D-1 (approved by Marcos 2026-11-22). */
const val SUN_HERO_TITLE = "Sun times"

/**
 * The line under the title: the text of `<p class="tagline">` in SunApp's index.html, with its
 * `<br>` as a newline (TEXTS.md D-2, SHIPPED).
 */
const val HERO_TAGLINE = "SUNRISE \u00B7 SOLAR NOON \u00B7 SUNSET\nanywhere on Earth"

/** A titled note in the accent colour of whatever it is warning about. */
@Composable
fun Notice(
    title: String,
    body: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    // An opaque base first (R4): the 10 per cent tint alone let the sky glow shine through, and
    // the text then depended on where on the sky the notice sat.
    val base = LocalSunTokens.current.Surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(base, shape)
            .background(accent.copy(alpha = 0.10f), shape)
            .border(1.dp, accent.copy(alpha = 0.32f), shape)
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accent,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextSecondary,
        )
    }
}

/**
 * The web app's header glow and its cool lower wash, as three radial gradients. [top] is centred at
 * the top edge, [left] sits near the top left, [bottom] near the bottom right.
 *
 * Deliberately not `Modifier.blur`, which needs API 31 and would leave the gradient flat for every
 * device between minSdk 24 and there. A soft radial gradient reaches the same look with no API
 * floor.
 */
fun DrawScope.drawSkyGlow(top: Glow, left: Glow, bottom: Glow) {
    fun glow(g: Glow, wash: Wash) {
        val centre = Offset(size.width * wash.cx, size.height * wash.cy)
        val radius = size.minDimension * wash.radius
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(g.colour.copy(alpha = g.alpha), Color.Transparent),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
    // Sunrise glow behind the header, matching .header-glow in style.css.
    glow(top, SkyGlowGeometry.top)
    glow(left, SkyGlowGeometry.left)
    // Night sky towards the bottom, so a long scroll does not end in flat black.
    glow(bottom, SkyGlowGeometry.bottom)
}

/**
 * The halo the web app puts behind a hero mark with a CSS drop-shadow, in [colour]. SunApp's is
 * the default alpha of 0.38; MoonApp's blue halo uses 0.34.
 */
fun DrawScope.drawLogoGlow(colour: Color, alpha: Float = 0.38f) {
    val radius = size.minDimension * 0.95f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colour.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
