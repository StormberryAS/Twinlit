package no.stormberry.twinlit.ui.sun

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import no.stormberry.twinlit.ui.components.RowSpec
import no.stormberry.twinlit.ui.components.SunCard
import no.stormberry.twinlit.ui.components.fitDateText
import no.stormberry.twinlit.ui.components.fitStyle
import no.stormberry.twinlit.ui.components.formatBarDate
import no.stormberry.twinlit.ui.components.lineWidth
import no.stormberry.twinlit.ui.components.rowsNeedStacking
import no.stormberry.twinlit.ui.components.widestWord
import no.stormberry.twinlit.ui.theme.Brand
import no.stormberry.twinlit.ui.theme.Sun

/**
 * The results half of the screen: the meta rows, the three sun-time cards and
 * the day-length bar, in the same order the web app puts them.
 *
 * Everything here takes strings that are already formatted. Timezone conversion
 * and polar labelling are decisions, and decisions belong in the pure functions
 * in `ui/sun/SunTimesScreen.kt` where they can be reasoned about and tested, not in
 * a composable where they can only be looked at.
 */

/** One value in a sun-time card: the text, its colour, and whether it is a clock. */
data class SunFieldValue(
    val text: String,
    val colour: Color,
    /** Clock readings are monospaced so the three cards line up digit for digit. */
    val monospace: Boolean,
)

/**
 * The web app's three meta pills, stacked as label/value rows.
 *
 * Stacked rather than laid out side by side because "Europe/Oslo" and a pair of
 * four-decimal coordinates do not fit across a phone at a readable size, and the
 * timezone is precisely the field a user checks when a time looks wrong.
 *
 * Word-safe (R8, D91): the label takes 0.42 of the row and the value 0.58, and when a word of a
 * label or of a value does not fit its column at the font size in use (360 dp and font scale 2.0:
 * "Coordinates" in 121 dp), the three rows put the label over the value instead, so no word breaks.
 * The date is given as a date, not as text, because its form depends on the width it gets: the
 * no-break space before the year while "November 2026" fits, a plain space when it does not (D90).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetaCard(
    coordinates: String,
    date: LocalDate,
    timezone: ZoneParts,
    modifier: Modifier = Modifier,
) {
    SunCard(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = fitStyle(MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp))
            val valueStyle = fitStyle(MaterialTheme.typography.bodyMedium)
            val monoStyle = valueStyle.copy(fontFamily = FontFamily.Monospace)
            val rowPx = constraints.maxWidth.toFloat()
            val gapPx = with(density) { META_LABEL_GAP.toPx() }
            val stacked = measurer.rowsNeedStacking(
                rowPx, META_LABEL_SHARE, gapPx,
                listOf(
                    RowSpec("Coordinates", labelStyle, coordinates, monoStyle),
                    RowSpec("Date", labelStyle, formatBarDate(date), valueStyle),
                    RowSpec("Timezone", labelStyle, timezone.text, monoStyle),
                ),
            )
            val valuePx = if (stacked) rowPx else rowPx * (1f - META_LABEL_SHARE)
            val dateText = fitDateText(date, valuePx) { measurer.lineWidth(it, valueStyle) }
            Column {
                MetaRow("Coordinates", stacked) { align, mod ->
                    Text(
                        text = coordinates,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sun.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        textAlign = align,
                        modifier = mod,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MetaRow("Date", stacked) { align, mod ->
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sun.TextPrimary,
                        textAlign = align,
                        modifier = mod,
                    )
                }
                Spacer(Modifier.height(10.dp))
                MetaRow("Timezone", stacked) { align, mod ->
                    // The zone breaks only between "CET /" and the id, never inside the id (R7, D87).
                    FlowRow(
                        modifier = mod.semantics(mergeDescendants = true) { },
                        horizontalArrangement = Arrangement.spacedBy(8.dp, if (align == TextAlign.End) Alignment.End else Alignment.Start),
                    ) {
                        val style = MaterialTheme.typography.bodyMedium
                        timezone.abbreviation?.let {
                            Text(text = it, style = style, color = Sun.TextPrimary, fontFamily = FontFamily.Monospace, textAlign = align)
                        }
                        Text(text = timezone.id, style = style, color = Sun.TextPrimary, fontFamily = FontFamily.Monospace, textAlign = align)
                    }
                }
            }
        }
    }
}

/** The label's share of a meta row and the gap after it, side by side (R7: 0.42 and 0.58). */
internal const val META_LABEL_SHARE = 0.42f
internal val META_LABEL_GAP = 12.dp

/**
 * One meta row. Side by side the label takes [META_LABEL_SHARE] of the row, less the gap, and the value
 * the rest, flush right; stacked the label stands over the value, both flush left, each the full row.
 */
@Composable
private fun MetaRow(label: String, stacked: Boolean, value: @Composable (TextAlign, Modifier) -> Unit) {
    val labelText: @Composable (Modifier) -> Unit = { mod ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Sun.TextMuted,
            fontSize = 11.sp,
            modifier = mod,
        )
    }
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth()) {
            labelText(Modifier)
            Spacer(Modifier.height(2.dp))
            value(TextAlign.Start, Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            labelText(Modifier.weight(META_LABEL_SHARE).padding(end = META_LABEL_GAP))
            value(TextAlign.End, Modifier.weight(1f - META_LABEL_SHARE))
        }
    }
}

/**
 * Sunrise, solar noon and sunset side by side, as in the web app's grid.
 *
 * Solar noon sits in the middle and is never blank. It is the transit of the sun
 * rather than a horizon crossing, so it exists on every day at every latitude,
 * including the polar days where the other two cards read "Midnight Sun" or
 * "Polar Night" instead of a time.
 */
@Composable
fun SunTimesRow(
    sunrise: SunFieldValue,
    solarNoon: SunFieldValue,
    sunset: SunFieldValue,
    modifier: Modifier = Modifier,
) {
    // Three tiles side by side while every word of every label, and the clock at TILE_CLOCK_FLOOR, fits a
    // tile; one tile per line when not (R8, D92), so no label breaks and no clock gets small. At font scale
    // 2.0 that is every phone narrower than about 480 dp.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val labelStyle = fitStyle(MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp))
        val clockStyle = fitStyle(MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontSize = TILE_CLOCK_FLOOR))
        val wordStyle = fitStyle(MaterialTheme.typography.titleSmall)
        val tileText = tileTextWidthPx(constraints.maxWidth.toFloat(), with(density) { TILE_GAP.toPx() }, with(density) { TILE_PADDING.toPx() })
        val tiles = listOf(sunrise to "Sunrise", solarNoon to "Solar noon", sunset to "Sunset")
        val sideBySide = sunTilesSideBySide(
            tileTextPx = tileText,
            tiles = tiles,
            labelWordPx = { measurer.widestWord(it, labelStyle) },
            valuePx = { if (it.monospace) measurer.lineWidth(it.text, clockStyle) else measurer.widestWord(it.text, wordStyle) },
        )
        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
            ) {
                SunTimeCard("🌅", "Sunrise", sunrise, Sun.Gold, Modifier.weight(1f))
                SunTimeCard("☀️", "Solar noon", solarNoon, Sun.Noon, Modifier.weight(1f))
                SunTimeCard("🌇", "Sunset", sunset, Sun.Orange, Modifier.weight(1f))
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                SunTimeCard("🌅", "Sunrise", sunrise, Sun.Gold, Modifier.fillMaxWidth())
                SunTimeCard("☀️", "Solar noon", solarNoon, Sun.Noon, Modifier.fillMaxWidth())
                SunTimeCard("🌇", "Sunset", sunset, Sun.Orange, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The decision of D92, as the Sun tab takes it: the three tiles stand side by side only while every tile fits
 * [tileTextPx] with every word whole, that is while the widest word of its label ([labelWordPx]) and its value
 * ([valuePx]: a clock on one line at [TILE_CLOCK_FLOOR], a polar word by its widest word) are no wider than the
 * tile's text. One answer for the three tiles, so they never mix the two forms. `SunTilesTest` calls this
 * function with measured widths, so a unit test proves the code the screen runs.
 */
internal fun sunTilesSideBySide(
    tileTextPx: Float,
    tiles: List<Pair<SunFieldValue, String>>,
    labelWordPx: (String) -> Float,
    valuePx: (SunFieldValue) -> Float,
): Boolean = tiles.all { (value, label) -> labelWordPx(label) <= tileTextPx && valuePx(value) <= tileTextPx }

/** The gap between tiles and a tile's side padding (the row's 10 dp and the tile's 8 dp, R7). */
internal val TILE_GAP = 10.dp
internal val TILE_PADDING = 8.dp

/**
 * The smallest size a clock reading in a side-by-side tile may need (R8, D92): when it would have to go
 * below this to fit, the tiles are laid one per line instead. At the tile's normal 14 sp, a clock is
 * 8 monospace characters.
 */
internal val TILE_CLOCK_FLOOR = 10.sp

/**
 * The width in pixels a tile's text can use when three tiles share [rowPx]: a third of the row less the two
 * gaps, less the tile's padding on each side.
 */
internal fun tileTextWidthPx(rowPx: Float, gapPx: Float, paddingPx: Float): Float = (rowPx - 2 * gapPx) / 3f - 2 * paddingPx

/**
 * The smallest size a clock reading in a sun-time tile steps down to, and the step. The tile's text
 * is 8 characters in a monospace face; at font scale 2.0 on a 360 dp phone it needs 7.4 sp
 * (`SunTimeTileTest`), so 5 sp leaves room down to a 320 dp phone and never clips.
 */
internal val CLOCK_MIN_SIZE = 5.sp
internal val CLOCK_STEP = 0.5.sp

@Composable
private fun SunTimeCard(
    icon: String,
    label: String,
    value: SunFieldValue,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Sun.CardHover, shape)
            .border(1.dp, accent.copy(alpha = 0.22f), shape)
            .padding(horizontal = TILE_PADDING, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = icon,
            fontSize = 22.sp,
            // Decorative twice over: the label under it names the event, and an
            // emoji read aloud as "sunrise over mountains" adds nothing but noise.
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Sun.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        val style = MaterialTheme.typography.titleSmall
        if (value.monospace) {
            // A clock reading never breaks (R7, D88): one line, stepped down in size until it fits the tile.
            Text(
                text = value.text,
                style = style,
                color = value.colour,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                softWrap = false,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = CLOCK_MIN_SIZE,
                    maxFontSize = style.fontSize,
                    stepSize = CLOCK_STEP,
                ),
            )
        } else {
            // "Midnight Sun" and "Polar Night" are words and may wrap between them.
            Text(
                text = value.text,
                style = style,
                color = value.colour,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Day length, as a figure and as a bar filled to its share of twenty-four hours.
 *
 * The denominator is the full day rather than the longest day of the year at this
 * latitude, which is what makes the bar comparable between places: a full bar
 * always means midnight sun and an empty one always means polar night, wherever
 * you are.
 */
@Composable
fun DayLengthBar(
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "dayLengthFill",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Day length",
                style = MaterialTheme.typography.labelMedium,
                color = Sun.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Sun.Gold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Sun.Surface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .background(
                        // SunApp's own gold to orange in every variant; the light palette's text-tuned pair read
                        // brown (fix round 1). A bar needs no text contrast.
                        Brush.horizontalGradient(listOf(Brand.SunGold, Brand.SunOrange)),
                    ),
            )
        }
    }
}

/** What the results area shows before a place has been chosen. */
@Composable
fun EmptyResults(modifier: Modifier = Modifier) {
    SunCard(modifier = modifier) {
        Text(
            text = "No place chosen yet",
            style = MaterialTheme.typography.titleMedium,
            color = Sun.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Search for a city, or switch to coordinates and type a latitude and " +
                "longitude. Twinlit cannot guess where you are, because it never asks the " +
                "device.",
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextSecondary,
        )
    }
}
