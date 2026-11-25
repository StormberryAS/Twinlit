package no.stormberry.twinlit.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Word-safe layout (R8, round 9). At 360 dp and font scale 2.0 a short label or number in a narrow
 * column used to break inside the word ("Sunri" / "se"). A column cannot know that without measuring,
 * so the pieces here measure with the real text engine and let the layout choose a form where every
 * word stays whole: label over value instead of side by side, a plain space between month and year,
 * one tile per line, stacked fields. The decisions are pure functions of measured widths, so a unit
 * test can pin them with any measure ([WordFitTest]); the composables pass the real [TextMeasurer].
 *
 * A "word" is a run of characters with no plain space or line feed in it. The no-break space joins
 * words, "Europe/Oslo" is one word, and a number is one word.
 */

/** The words of [text], in the order they stand: split at a plain space or a line feed only. */
internal fun wordsOf(text: String): List<String> = text.split(' ', '\n').filter { it.isNotEmpty() }

/** The width in pixels of the widest word of [text] in [style], each word set on one line. */
internal fun TextMeasurer.widestWord(text: String, style: TextStyle): Float =
    wordsOf(text).maxOfOrNull { word ->
        measure(word, style, softWrap = false, maxLines = 1, constraints = Constraints()).size.width.toFloat()
    } ?: 0f

/** The width in pixels of [text] set on one line in [style]. */
internal fun TextMeasurer.lineWidth(text: String, style: TextStyle): Float =
    measure(text, style, softWrap = false, maxLines = 1, constraints = Constraints()).size.width.toFloat()

/**
 * One row of a card: a label and a value and the styles they are set in. [valueWords] is the text
 * whose words have to stay whole (for a date, the form with a plain space, which is the last resort).
 */
internal class RowSpec(
    val label: String,
    val labelStyle: TextStyle,
    val valueWords: String,
    val valueStyle: TextStyle,
)

/**
 * Whether the rows must put the label over the value (R8, D91): true when, side by side, the widest
 * word of some label does not fit its column ([labelShare] of [rowPx], less [gapPx]) or the widest
 * word of some value does not fit the rest. One answer for every row of a card, so the card never
 * mixes the two forms.
 */
internal fun TextMeasurer.rowsNeedStacking(rowPx: Float, labelShare: Float, gapPx: Float, rows: List<RowSpec>): Boolean =
    rows.any { row ->
        !rowFitsBeside(
            labelWordPx = widestWord(row.label, row.labelStyle),
            valueWordPx = widestWord(row.valueWords, row.valueStyle),
            rowPx = rowPx, labelShare = labelShare, gapPx = gapPx,
        )
    }

/**
 * Whether a label and a value fit side by side with every word whole: the label's widest word in its
 * column ([labelShare] of [rowPx], less [gapPx]) and the value's widest word in the rest.
 */
internal fun rowFitsBeside(labelWordPx: Float, valueWordPx: Float, rowPx: Float, labelShare: Float, gapPx: Float): Boolean =
    labelWordPx <= rowPx * labelShare - gapPx && valueWordPx <= rowPx * (1f - labelShare)

/**
 * The date as the date bar and the date rows say it: "Sunday 22 November 2026", with the space
 * before the year a no-break space while "November 2026" fits [columnPx] as one piece. When even that
 * piece is wider than the column, the plain space is used, so the line breaks between month and year
 * and never inside the year (R8, D90). [widthOf] measures a piece in pixels. A date is never split
 * into a year alone: the year is only released from the month when the joined piece cannot fit.
 */
internal fun fitDateText(date: LocalDate, columnPx: Float, widthOf: (String) -> Float): String {
    val joined = displayBarDate(date)
    val widest = wordsOf(joined).maxOfOrNull(widthOf) ?: 0f
    return if (widest <= columnPx) joined else formatBarDate(date)
}

/**
 * [style] as the Text composable will set it: merged over the surrounding text style, so a measurement
 * with the real text engine gives the width the screen will show.
 */
@Composable
internal fun fitStyle(style: TextStyle): TextStyle = LocalTextStyle.current.merge(style)

/**
 * A height that a larger font can outgrow (R8, D97): [min] is a minimum, never a fixed height, so a button or
 * a bar whose text wraps at font scale 2.0 grows instead of cutting its text. Every button and bar that
 * carries words uses this and not `height(...)`; `GrowsWithTextTest` applies it and reads the modifier back.
 */
internal fun Modifier.growsWithText(min: Dp): Modifier = heightIn(min = min)

/** The minimum heights of D97 and D94: the editor's "Create alarm", the empty list's button, the top bar and "Use these coordinates". */
internal val CREATE_ALARM_MIN_HEIGHT = 52.dp
internal val FIRST_ALARM_MIN_HEIGHT = 50.dp
internal val TOP_BAR_MIN_HEIGHT = 48.dp
internal val APPLY_COORDINATES_MIN_HEIGHT = 48.dp
