package no.stormberry.twinlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.stormberry.twinlit.ui.theme.Moon
import no.stormberry.twinlit.ui.theme.Sun
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The colours of a [DateBar]. A bar is drawn in the palette of the tab it sits on: the Sun tab's
 * ([sunDateBarColours]) or the Moon tab's ([moonDateBarColours]).
 */
data class DateBarColours(
    /** The box. */
    val fill: Color,
    /** A 1 dp edge, the tab's Border (R5, D14). */
    val border: Color,
    /** The date line. */
    val date: Color,
    /** "Today". */
    val today: Color,
    /** "Back to today". */
    val link: Color,
    /** The two glyphs. */
    val arrow: Color,
    val dialogContainer: Color,
    val dialogConfirm: Color,
    val dialogDismiss: Color,
)

/** The Sun tab's bar: its card and border, gold link and confirm button. */
@Composable
fun sunDateBarColours() = DateBarColours(
    fill = Sun.Card,
    border = Sun.Border,
    date = Sun.TextPrimary,
    today = Sun.TextMuted,
    link = Sun.Gold,
    arrow = Sun.TextSecondary,
    dialogContainer = Sun.Surface,
    dialogConfirm = Sun.Gold,
    dialogDismiss = Sun.TextSecondary,
)

/** The Moon tab's bar: MoonApp's card colour with the same 1 dp edge as the Sun tab's bar (R5, D14), silver. */
@Composable
fun moonDateBarColours() = DateBarColours(
    fill = Moon.Card,
    border = Moon.Border,
    date = Moon.TextPrimary,
    today = Moon.TextHint,
    link = Moon.Silver,
    arrow = Moon.Silver,
    dialogContainer = Moon.Surface,
    dialogConfirm = Moon.Silver,
    dialogDismiss = Moon.Silver,
)

/** The space either side of the date line, which the 48 dp arrows sit in. */
private val DATE_SIDE_PADDING = 44.dp

/**
 * The one date bar of the Sun and Moon tabs (round R4, Marcos, 2026-11-20: "make the date bar look
 * like moon date bar, where it is a discreet arrow to each side and day of the week day month year
 * in its own box"). MoonApp's look: one rounded box, the date centred in it, a discreet arrow at
 * each side with no circle around it.
 *
 * The date line may wrap on a large font but never clips; the second line is always there ("Today",
 * or "Back to today" as a link), so the bar keeps its height and the arrows never move. Tapping the
 * date opens the date picker. The two arrows are 48 dp targets, declared after the centre column so
 * they win the touch where a wrapped date line reaches into them.
 *
 * @param isToday whether [date] is the place's today
 * @param onShift -1 for the previous day, +1 for the next
 */
@Composable
fun DateBar(
    date: LocalDate,
    isToday: Boolean,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
    onPick: (LocalDate) -> Unit,
    colours: DateBarColours,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colours.fill, shape)
            .border(1.dp, colours.border, shape),
    ) {
        // The date keeps the no-break space before the year while "November 2026" fits the line, and
        // breaks between month and year when it does not, never inside the year (R8, D90).
        val measurer = rememberTextMeasurer()
        val style = fitStyle(MaterialTheme.typography.bodyMedium)
        val sidesPx = with(LocalDensity.current) { DATE_SIDE_PADDING.toPx() } * 2f
        val linePx = (constraints.maxWidth.toFloat() - sidesPx).coerceAtLeast(0f)
        val dateText = fitDateText(date, linePx) { measurer.lineWidth(it, style) }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = DATE_SIDE_PADDING, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = colours.date,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = CHOOSE_A_DATE) { showPicker = true },
            )
            if (isToday) {
                Text(text = TODAY, style = MaterialTheme.typography.bodySmall, color = colours.today)
            } else {
                Text(
                    text = BACK_TO_TODAY,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colours.link,
                    modifier = Modifier.clickable(role = Role.Button) { onToday() },
                )
            }
        }
        DateArrow("‹", PREVIOUS_DAY, colours.arrow, Modifier.align(Alignment.CenterStart)) { onShift(-1L) }
        DateArrow("›", NEXT_DAY, colours.arrow, Modifier.align(Alignment.CenterEnd)) { onShift(1L) }
    }

    if (showPicker) {
        DateDialog(
            initial = date,
            colours = colours,
            onDismiss = { showPicker = false },
            onPick = onPick,
        )
    }
}

/**
 * One arrow: only the glyph shows, in a 48 dp target flush with the edge of the box, named by its
 * label (TalkBack read the bare glyph, "single left-pointing angle quotation mark").
 */
@Composable
private fun DateArrow(glyph: String, label: String, colour: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = MaterialTheme.typography.labelLarge, color = colour)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initial: LocalDate,
    colours: DateBarColours,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    // The Material date picker speaks epoch milliseconds at UTC midnight, which is exactly a
    // LocalDate with no zone attached. Converting through UTC in both directions keeps it that way;
    // going through the device zone would move the date by one either side of midnight.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val pickerColours = DatePickerDefaults.colors(containerColor = colours.dialogContainer)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = pickerColours,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) {
                Text("Select", color = colours.dialogConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colours.dialogDismiss) }
        },
    ) {
        DatePicker(state = state, colors = pickerColours)
    }
}

/**
 * "Friday 20 November 2026" (TEXTS.md D-30): British long form, the same as the Moon tab's phase card,
 * whatever the phone's language.
 */
internal fun formatBarDate(date: LocalDate): String = BAR_DATE_FORMAT.format(date)

private val BAR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)

/**
 * What the bar shows (R5, D13): [formatBarDate] with the space before the year made a no-break space,
 * so a date that does not fit one line breaks after the day ("Wednesday 25" / "November 2026") and
 * never leaves the year alone. Spoken the same as the plain text.
 */
internal fun displayBarDate(date: LocalDate): String {
    val text = formatBarDate(date)
    val i = text.lastIndexOf(' ')
    return text.substring(0, i) + '\u00A0' + text.substring(i + 1)
}

/** TEXTS.md D-6 to D-9 and E-3: what the bar says and what a screen reader says for the arrows. */
internal const val PREVIOUS_DAY = "Previous day"
internal const val NEXT_DAY = "Next day"
internal const val CHOOSE_A_DATE = "Choose a date"
internal const val TODAY = "Today"
internal const val BACK_TO_TODAY = "Back to today"
