package no.stormberry.twinlit.ui.alarms

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import no.stormberry.twinlit.alarm.OccurrenceEngine
import no.stormberry.twinlit.alarm.PreviewDay
import no.stormberry.twinlit.alarm.PendingSnooze
import no.stormberry.twinlit.alarm.anchorLabel
import no.stormberry.twinlit.alarm.anchorNoun
import no.stormberry.twinlit.alarm.defaultLabel
import no.stormberry.twinlit.alarm.describeTiming
import no.stormberry.twinlit.alarm.formatOffset
import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.alarm.model.Clamp
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.alarm.model.Occurrence
import no.stormberry.twinlit.cities.City
import no.stormberry.twinlit.cities.CitySearch
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.PlaceDisplay
import no.stormberry.twinlit.data.displayLabel
import no.stormberry.twinlit.data.displayPlaceName
import no.stormberry.twinlit.data.hasGeneratedLabel
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.solar.resolveZone
import no.stormberry.twinlit.ui.CatalogueState
import no.stormberry.twinlit.ui.CityPicker
import no.stormberry.twinlit.ui.applyChosenPlace
import no.stormberry.twinlit.ui.components.EDITOR_ZONE_LEAD
import no.stormberry.twinlit.ui.components.Notice
import no.stormberry.twinlit.ui.components.SectionLabel
import no.stormberry.twinlit.ui.components.SunCard
import no.stormberry.twinlit.ui.components.ZoneCaption
import no.stormberry.twinlit.ui.components.controlBorder
import no.stormberry.twinlit.ui.theme.LocalSunTokens
import no.stormberry.twinlit.ui.components.fitStyle
import no.stormberry.twinlit.ui.components.widestWord
import no.stormberry.twinlit.ui.theme.Sun
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import no.stormberry.twinlit.ui.components.CREATE_ALARM_MIN_HEIGHT
import no.stormberry.twinlit.ui.components.growsWithText

/**
 * Create or edit one alarm rule, for the sun or for the moon.
 *
 * ### The anchors the editor offers, and the eleven it does not
 *
 * [OccurrenceEngine] and [SolarEvent] support all fourteen solar events, from astronomical
 * dawn to nadir. For the sun this screen offers exactly three: sunrise, solar noon, sunset (owner's
 * confirmed decision 5). That is not a limitation waiting to be lifted, it is the product:
 * the three events every person already has a word for. The engine keeps the other eleven because
 * a rule can arrive from a hand-edited store or from a later version, and [EDITOR_ANCHORS] is the
 * only place that decides what the sun's picker shows. For the moon it offers the five events of
 * [LunarEvent] ([EDITOR_MOON_ANCHORS]), and the preview differs by event group (SPEC 7.7, D47).
 *
 * ### The preview is the whole safety story
 *
 * There is no forced clamp in Twinlit (owner's confirmed decision 2), which means a rule
 * genuinely can walk from before 04:00 in June to after 08:00 in December in Oslo, and further north it
 * is worse. The mitigation is not a hidden bound, it is that the user sees thirty days of
 * real fire times before they press Save, with the earliest, the latest and the spread called
 * out above them. The rows come from [OccurrenceEngine.preview] and from nothing else: a
 * second implementation of that arithmetic here would be a preview that agrees with the
 * scheduler right up until the day it does not, which is worse than no preview at all.
 *
 * ### State is hoisted
 *
 * The editor holds no draft of its own. [RuleDraft] lives in the caller, which is what lets
 * the alarms list keep an in-progress rule across a permission sheet, a system settings trip
 * or a configuration change without this screen knowing that any of those exist.
 */

/** The sun anchors the picker offers, in the order the sun reaches them. */
internal val EDITOR_ANCHORS: List<SolarEvent> =
    listOf(SolarEvent.SUNRISE, SolarEvent.SOLAR_NOON, SolarEvent.SUNSET)

/** The moon anchors the picker offers: the five events of MoonApp, daily ones first. */
internal val EDITOR_MOON_ANCHORS: List<LunarEvent> = LunarEvent.entries.toList()

/**
 * An alarm rule part-way through being written.
 *
 * The offset is held as two strings rather than as an `Int` because a text field whose value
 * is derived from a number cannot represent "the user has cleared the box and not typed the
 * new digit yet". Parsing happens once, in [parseOffsetMinutes], at the point the draft is
 * turned into a rule.
 *
 * @property requestedTime a wall time asked for by an `AlarmClock.ACTION_SET_ALARM` intent.
 *   Twinlit cannot honour a fixed time (its alarms track the sun by definition), so this is
 *   carried purely so the editor can offer to match it on today's date and then show the
 *   drift that follows. See `AlarmIntentActivity`.
 */
data class RuleDraft(
    val id: String,
    val label: String = "",
    val anchor: Anchor = Anchor.Sun(SolarEvent.SUNRISE),
    val direction: Direction = Direction.AT,
    val offsetHours: String = "0",
    val offsetMinutes: String = "0",
    val place: Place? = null,
    val clampOpen: Boolean = false,
    val earliest: LocalTime? = null,
    val latest: LocalTime? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val isNew: Boolean = true,
    val requestedTime: LocalTime? = null,
) {
    /** The body this draft follows. */
    val kind: AnchorKind get() = anchor.kind
}

/**
 * Open an existing rule for editing, losing nothing that the editor cannot express. [current] is
 * the place the user last looked at, which names the rule's place when it is the same typed place
 * (see [PlaceDisplay.ruleName]).
 */
fun AlarmRule.toDraft(current: Place? = null): RuleDraft = RuleDraft(
    id = id,
    // A name the app made (S11) starts empty, like a new rule: the field then shows the generated
    // name of the CURRENT event, direction, offset and place as its preview, follows every change
    // and is saved as the generated name. A name the user typed is shown as it is, and never
    // changes. A rule 1.1.8 saved with the generated name is treated the same.
    // Never the coordinates of a typed place, not even in a rule saved before 2.0.0.
    label = if (hasGeneratedLabel()) "" else displayLabel(current),
    anchor = anchor,
    direction = direction,
    offsetHours = (offsetMinutes / 60).toString(),
    offsetMinutes = (offsetMinutes % 60).toString(),
    place = draftPlace(current),
    // Opened because it is set. A user who gave a rule a clamp should see it without hunting.
    clampOpen = clamp != null,
    earliest = clamp?.earliest,
    latest = clamp?.latest,
    vibrate = vibrate,
    enabled = enabled,
    isNew = false,
)

/**
 * The place of an existing rule as the editor holds it. A place typed as coordinates keeps its
 * kind (`fromCatalogue` false) and its name; its label is the name it is shown by, never its
 * coordinates. A catalogue place has its saved name as label.
 */
private fun AlarmRule.draftPlace(current: Place?): Place {
    val shown = displayPlaceName(current)
    if (placeTyped || PlaceDisplay.isCoordinates(placeName, latDeg, lonDeg)) {
        return Place(
            label = shown,
            latDeg = latDeg,
            lonDeg = lonDeg,
            zoneId = zoneId,
            // Typed, so the place is told by this flag (the label is its display name, never its
            // coordinates). In the editor it decides nothing else: only Settings.place feeds the
            // sun-times boxes.
            fromCatalogue = false,
            name = if (shown == PlaceDisplay.YOUR_COORDINATES) "" else shown,
        )
    }
    return Place(
        label = shown,
        latDeg = latDeg,
        lonDeg = lonDeg,
        zoneId = zoneId,
        // Unknowable after the fact for a place of unknown kind. For this editor it only decides
        // whether the sun-times coordinate boxes are prefilled, and true prefills nothing. It is
        // not a safe answer in general: on Settings.place, true also lets the home screen widgets
        // show the label. A rule's place never becomes Settings.place.
        fromCatalogue = true,
    )
}

/**
 * The name of an alarm is at most [PendingSnooze.MAX_LABEL] characters, the one label limit the app has (the snooze
 * record and the notices keep no more). The name field holds back what is typed or pasted past it, and a draft that
 * already holds more (an intent's text, a name saved before the limit) is cut when it becomes a rule (R13, D139).
 * Never cuts a surrogate pair in two.
 */
internal fun clampLabel(text: String): String {
    if (text.length <= PendingSnooze.MAX_LABEL) return text
    val cut = text.take(PendingSnooze.MAX_LABEL)
    return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}

/**
 * Turn a draft into a rule, or null when it is not yet complete.
 *
 * Null is not an error state to report; it is what disables the Save button. The reason a
 * draft is incomplete is shown next to the field that is incomplete, which is why this
 * returns a nullable rule rather than a result type carrying a message.
 */
fun RuleDraft.toRule(): AlarmRule? {
    val place = place ?: return null
    val offset = parseOffsetMinutes(offsetHours, offsetMinutes) ?: return null
    if (earliest != null && latest != null && !earliest.isBefore(latest)) return null

    val clamp = if (earliest == null && latest == null) null else Clamp(earliest, latest)
    return AlarmRule(
        id = id,
        label = clampLabel(label.trim()).trim().ifEmpty { defaultLabel(anchor, direction, offset, PlaceDisplay.name(place), cutAtComma = !PlaceDisplay.isTyped(place)) },
        anchor = anchor,
        direction = direction,
        offsetMinutes = offset,
        latDeg = place.latDeg,
        lonDeg = place.lonDeg,
        zoneId = place.zoneId,
        // Typed coordinates are never stored as the rule's place name (2.0.0, D1).
        placeName = PlaceDisplay.name(place),
        // A place typed as coordinates is looked up again, by its coordinates, when it is shown.
        placeTyped = PlaceDisplay.isTyped(place),
        // A clamp the user collapsed without setting a bound is not a clamp. Storing an empty
        // one would be indistinguishable in behaviour and would make the rule look clamped in
        // the list, so it is dropped here rather than carried.
        clamp = clamp,
        enabled = enabled,
        ringtoneUri = null,
        vibrate = vibrate,
    )
}

/**
 * Read the two offset boxes.
 *
 * Blank reads as zero, because clearing a box to type into it should not make the whole draft
 * invalid mid-keystroke. Anything else that is not a plain non-negative number is rejected:
 * the sign belongs to [Direction] and nowhere else, and a negative typed here would silently
 * mean the opposite of the button the user pressed.
 */
internal fun parseOffsetMinutes(hours: String, minutes: String): Int? {
    val h = hours.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
    val m = minutes.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
    if (h < 0 || m < 0) return null
    // A day and a half of offset is already past the point where "before sunrise" describes
    // anything, and beyond it the day walk in nextOccurrence starts paying for lookback it
    // will never use.
    if (h > 36 || m > 59) return null
    return h * 60 + m
}

/** G-54: the timing summary while the offset boxes hold something that is not an offset. */
internal const val TIMING_INVALID_OFFSET = "Fix the offset to see the timing."

/**
 * G-55: what the preview card says while it has no rows. [offsetInvalid] is the offset boxes holding
 * something that is not an offset; otherwise the rule is waiting for a place (or for a clamp that
 * makes sense, which its own red line explains).
 */
internal fun previewPlaceholder(offsetInvalid: Boolean): String =
    if (offsetInvalid) {
        "Fix the offset above and the next $PREVIEW_DAYS fire times appear here, before you commit to anything."
    } else {
        "Choose a city and the next $PREVIEW_DAYS fire times appear here, before you commit to anything."
    }

/** The line above the place picker while a sun rule has no place or a city from the catalogue (G-17). */
internal const val PLACE_INTRO_CATALOGUE =
    "An alarm needs a place before its sun time can be worked out. This is a city from the bundled " +
        "catalogue, never your phone's position: Twinlit has no location permission to ask for."

/** The same line for a moon rule (G-18). */
internal const val PLACE_INTRO_CATALOGUE_MOON =
    "An alarm needs a place before its moon time can be worked out. This is a city from the bundled " +
        "catalogue, never your phone's position: Twinlit has no location permission to ask for."

/** The same line for a place the user typed as coordinates: it is not a city, and it is true of that place. */
internal const val PLACE_INTRO_TYPED =
    "These are coordinates you typed, never your phone's position: Twinlit has no location " +
        "permission to ask for."

/**
 * The line above the place picker (2.0.0). A place typed as coordinates is not "a city from the
 * bundled catalogue", so it gets its own true sentence; no place yet, and a catalogue city, keep
 * the catalogue one. A place of unknown kind (a rule saved before the kind was kept) is told as
 * everywhere else, by [PlaceDisplay.isTyped].
 */
internal fun placeIntro(place: Place?, kind: AnchorKind = AnchorKind.SUN): String = when {
    place != null && PlaceDisplay.isTyped(place) -> PLACE_INTRO_TYPED
    kind == AnchorKind.MOON -> PLACE_INTRO_CATALOGUE_MOON
    else -> PLACE_INTRO_CATALOGUE
}

/**
 * What the thirty rows add up to.
 *
 * @property spreadMinutes the width of the window between the earliest and the latest fire
 *   time. This is the number the whole preview exists to put in front of the user: at 60
 *   degrees north a sunrise rule moves by roughly an hour and a half over a month either side
 *   of the equinox, and nobody expects that until they see it.
 */
internal data class PreviewSummary(
    val rows: List<Occurrence>,
    val earliest: Occurrence?,
    val latest: Occurrence?,
    val spreadMinutes: Int,
    val fallbackDays: Int,
    val clampedDays: Int,
)

/**
 * Reduce a preview to its extremes.
 *
 * Times of day are compared relative to the first row rather than as absolute minutes past
 * midnight, and the differences are folded into plus or minus twelve hours. Without that, a
 * rule that rings at 23:55 on one day and 00:05 on the next reads as a spread of 23 hours 50
 * minutes instead of the ten minutes it actually is, and the headline number of the whole
 * screen would be nonsense for exactly the rules (sunset in high summer, anything with a
 * large offset) that most need it.
 */
internal fun summarisePreview(rows: List<Occurrence>, zone: ZoneId): PreviewSummary {
    val first = rows.firstOrNull()
        ?: return PreviewSummary(rows, null, null, 0, 0, 0)

    val base = minuteOfDay(first, zone)
    var minRelative = 0
    var maxRelative = 0
    var earliest = first
    var latest = first
    for (row in rows) {
        val relative = wrapHalfDay(minuteOfDay(row, zone) - base)
        if (relative < minRelative) {
            minRelative = relative
            earliest = row
        }
        if (relative > maxRelative) {
            maxRelative = relative
            latest = row
        }
    }
    return PreviewSummary(
        rows = rows,
        earliest = earliest,
        latest = latest,
        spreadMinutes = maxRelative - minRelative,
        fallbackDays = rows.count { it.usedFallback },
        clampedDays = rows.count { it.clamped },
    )
}

/** Fold a minute difference into [-720, 720), so a window straddling midnight measures small. */
internal fun wrapHalfDay(delta: Int): Int {
    val positive = ((delta % 1440) + 1440) % 1440
    return if (positive >= 720) positive - 1440 else positive
}

private fun minuteOfDay(occurrence: Occurrence, zone: ZoneId): Int {
    val time = occurrence.fireAt.atZone(zone).toLocalTime()
    return time.hour * 60 + time.minute
}

/**
 * The three preview shapes of the editor (SPEC 7.7, D47): the sun's thirty days with the Earliest,
 * Latest and Spread lines; a daily moon event's thirty days with the days without the event marked
 * and no spread (the time walks round the clock); a monthly moon event's next three nights.
 */
internal enum class PreviewGroup { SUN, DAILY_MOON, MONTHLY_MOON }

/** The preview group of [anchor]. */
internal fun previewGroup(anchor: Anchor): PreviewGroup = when {
    anchor is Anchor.Sun -> PreviewGroup.SUN
    anchor is Anchor.Moon && anchor.event.daily -> PreviewGroup.DAILY_MOON
    else -> PreviewGroup.MONTHLY_MOON
}

/**
 * What the preview card shows for a rule: the [group], one [PreviewDay] per row (a null occurrence
 * is a day without the event, daily moon rules only), and the [summary] of the rows that have an
 * occurrence. Every row is an [OccurrenceEngine.occurrenceFor] result and nothing else.
 */
internal data class EditorPreview(
    val group: PreviewGroup,
    val rows: List<PreviewDay>,
    val summary: PreviewSummary,
    /** Days of a daily moon preview that have no event. */
    val missingDays: Int,
)

/**
 * The preview of [rule] from [today] (the place's today), for the rule's group.
 *
 * With [now] the rows are the NEXT fire times: a row whose time has already passed is left out, so
 * the preview starts where the alarm list does (fix round 2: at 09:00 it used to open with today's
 * 08:27 and use it as Earliest while the list said 08:29 tomorrow). The walk starts a few days back,
 * as [OccurrenceEngine.nextOccurrence] does, because an offset can carry a fire time onto a later day
 * than its anchor, and the rows are still [OccurrenceEngine] results and nothing else. Without [now]
 * the rows are the calendar from [today] (tests).
 */
internal fun editorPreview(rule: AlarmRule, today: LocalDate, zone: ZoneId, now: Instant? = null): EditorPreview {
    val group = previewGroup(rule.anchor)
    val back = if (now == null) 0L else kotlin.math.abs(rule.signedOffsetMinutes).toLong() / (24L * 60L) + 2L
    val from = today.minusDays(back)
    val span = PREVIEW_DAYS + back.toInt() + 1
    val rows = when (group) {
        PreviewGroup.SUN ->
            OccurrenceEngine.preview(rule, from, if (now == null) PREVIEW_DAYS else span)
                .filter { now == null || it.fireAt.isAfter(now) }
                .take(PREVIEW_DAYS)
                .map { PreviewDay(it.anchorDate, it) }
        PreviewGroup.DAILY_MOON ->
            OccurrenceEngine.previewDays(rule, from, if (now == null) PREVIEW_DAYS else span)
                .mapNotNull { day ->
                    if (now == null) return@mapNotNull day
                    val live = day.occurrences.filter { it.fireAt.isAfter(now) }
                    when {
                        live.isNotEmpty() -> PreviewDay(day.date, live)
                        day.occurrences.isEmpty() && !day.date.isBefore(today) -> day
                        else -> null
                    }
                }
                .take(PREVIEW_DAYS)
        PreviewGroup.MONTHLY_MOON ->
            OccurrenceEngine.nextNights(rule, from, PREVIEW_NIGHTS, now).map { PreviewDay(it.anchorDate, it) }
    }
    return EditorPreview(
        group = group,
        rows = rows,
        summary = summarisePreview(rows.flatMap { it.occurrences }, zone),
        missingDays = rows.count { it.occurrences.isEmpty() },
    )
}

/** The moon event as a word inside the preview's sentences: "moonrise", "moonset", "highest point of the moon" (G-29, G-30). */
internal fun moonEventWord(event: LunarEvent): String = when (event) {
    LunarEvent.MOONRISE -> "moonrise"
    LunarEvent.MOONSET -> "moonset"
    LunarEvent.LUNAR_TRANSIT -> "highest point of the moon"
    LunarEvent.FULL_MOON -> "full moon night"
    LunarEvent.NEW_MOON -> "new moon night"
}

/** G-51: the line that stands where Earliest, Latest and Spread would be, for a daily moon rule. */
internal fun moonDriftLine(event: LunarEvent): String {
    val subject = when (event) {
        LunarEvent.MOONRISE -> "Moonrise"
        LunarEvent.MOONSET -> "Moonset"
        else -> "The moon's highest point"
    }
    return "$subject comes later each day, by about 50 minutes on average. The daily step changes with " +
        "the place and the season, so read the rows below."
}

/** G-29: the card for the days of a daily moon preview that have no event. */
internal fun moonMissingBody(event: LunarEvent, days: Int, placeName: String): String =
    "There is no ${moonEventWord(event)} on $days of these days at $placeName. That is normal: " +
        "the moon's times come later each day, so now and then a calendar day has none, because one " +
        "falls just before midnight and the next just after it. On those days this alarm does " +
        "not ring. The rows below are marked."

/** G-30: the title of the missing-day card, "No moonrise on Fri 4 Dec" or "No moonrise on 2 of the next 30 days". */
internal fun moonMissingTitle(event: LunarEvent, rows: List<PreviewDay>): String {
    val word = moonEventWord(event)
    val missing = rows.count { it.occurrences.isEmpty() }
    return if (rows.firstOrNull()?.occurrences?.isEmpty() != false) {
        "No $word on ${SHORT_DATE.format(rows.first().date)}"
    } else {
        "No $word on $missing of the next $PREVIEW_DAYS days"
    }
}

/** G-52: the heading of a monthly moon preview. */
internal fun monthlyHeading(event: LunarEvent): String =
    if (event == LunarEvent.FULL_MOON) "Next three full moon nights" else "Next three new moon nights"

/** G-31: "Clamped on 3 of 30 days" or, for the three nights, "Clamped on 2 of these 3 nights". */
internal fun clampedTitle(group: PreviewGroup, days: Int): String =
    if (group == PreviewGroup.MONTHLY_MOON) "Clamped on $days of these $PREVIEW_NIGHTS nights"
    else "Clamped on $days of $PREVIEW_DAYS days"

/** G-32: what a clamped day means. */
internal fun clampedBody(kind: AnchorKind): String =
    "On those days the bound decides the time, not the ${bodyWord(kind)}."

/** G-36, G-38 and G-53: the clamp section's texts for a kind. */
internal fun clampOffText(kind: AnchorKind): String = "Off. The alarm follows the ${bodyWord(kind)}."

internal fun clampIntro(kind: AnchorKind): String =
    "A limit stops the alarm following the ${bodyWord(kind)} past a wall-clock time. It " +
        "is off by default because the drift is the feature: the preview above " +
        "exists so you can decide you do not need this."

/** G-53, daily moon rules only. */
internal const val CLAMP_MOON_DRIFT =
    "The moon's times move later each day, by about 50 minutes on average, so a limit decides the time on many days."

/** G-47 and the others: why Save is off. */
internal fun saveBlockedReason(draft: RuleDraft, offset: Int?): String = when {
    draft.place == null -> "Choose a city before saving. The ${bodyWord(draft.kind)} has to be measured somewhere."
    offset == null -> "The offset must be whole numbers: up to 36 hours and up to 59 minutes."
    draft.earliest != null && draft.latest != null && !draft.earliest.isBefore(draft.latest) ->
        "The earliest bound has to come before the latest one on the same day. A window that " +
            "wraps past midnight is not something a single day's clamp can express."
    else -> "This alarm is not complete yet."
}

/** The draft with its kind switched: direction, offset, place and clamp are kept, the anchor is the new kind's first (SPEC 7.7). */
internal fun RuleDraft.withKind(kind: AnchorKind): RuleDraft =
    if (this.kind == kind) this else copy(anchor = Anchor.first(kind))

/* ------------------------------------------------------------------ *
 * The screen
 * ------------------------------------------------------------------ */

@Composable
fun RuleEditorScreen(
    draft: RuleDraft,
    onDraftChange: (RuleDraft) -> Unit,
    catalogue: CatalogueState,
    onSave: (AlarmRule) -> Unit,
    onCancel: () -> Unit,
    // modifier comes first among the optional parameters. Compose's ModifierParameter
    // lint check enforces this, and the build runs lint with warningsAsErrors, so the
    // other order fails CI on tag rather than at desk. Convention, not taste: callers
    // expect to pass a modifier positionally after the required arguments.
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var editingBound by remember { mutableStateOf<ClampBoundField?>(null) }

    val suggestions = remember(query, catalogue) {
        val ready = catalogue as? CatalogueState.Ready
        if (ready == null || query.isBlank()) emptyList() else CitySearch.search(ready.table, query)
    }

    val offset = parseOffsetMinutes(draft.offsetHours, draft.offsetMinutes)
    val rule = draft.toRule()

    // The preview does not depend on the name, so it is keyed on the rule WITHOUT its label: typing
    // in the Name box must not recompute it on the main thread (fix round 2; a full moon night
    // preview costs about 13 ms warm on a desktop JVM and more on a phone). A sun preview is tens of
    // microseconds, a moon one more, and it starts at the next fire time (see [editorPreview]).
    val previewKey = rule?.copy(label = "")
    val summary = remember(previewKey) {
        if (rule == null) {
            null
        } else {
            val zone = resolveZone(rule.zoneId)
            editorPreview(rule, LocalDate.now(zone), zone, Instant.now())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
    ) {
        EditorHeader(
            title = if (draft.isNew) "New alarm" else "Edit alarm",
            onCancel = onCancel,
        )
        Spacer(Modifier.height(20.dp))

        SunCard {
            SectionLabel("Name")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onDraftChange(draft.copy(label = clampLabel(it))) },
                singleLine = true,
                placeholder = {
                    Text(
                        // Shows the name the rule will actually be saved under, so an empty
                        // box is a preview rather than a blank.
                        text = defaultLabel(
                            draft.anchor,
                            draft.direction,
                            offset ?: 0,
                            draft.place?.let(PlaceDisplay::name).orEmpty(),
                            cutAtComma = draft.place?.let { !PlaceDisplay.isTyped(it) } ?: true,
                        ),
                        color = Sun.TextMuted,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(14.dp),
                colors = sunFieldColours(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        SunCard {
            SectionLabel("Track")
            Spacer(Modifier.height(8.dp))
            KindPicker(
                kind = draft.kind,
                onSelect = { onDraftChange(draft.withKind(it)) },
            )
            Spacer(Modifier.height(10.dp))
            AnchorPicker(
                anchor = draft.anchor,
                onSelect = { onDraftChange(draft.copy(anchor = it)) },
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("When")
            Spacer(Modifier.height(8.dp))
            DirectionPicker(
                direction = draft.direction,
                onSelect = { onDraftChange(draft.copy(direction = it)) },
            )

            if (draft.direction != Direction.AT) {
                Spacer(Modifier.height(12.dp))
                OffsetFields(
                    hours = draft.offsetHours,
                    minutes = draft.offsetMinutes,
                    onHoursChange = { onDraftChange(draft.copy(offsetHours = it)) },
                    onMinutesChange = { onDraftChange(draft.copy(offsetMinutes = it)) },
                    invalid = offset == null,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                // An offset that cannot be read has no timing to describe: the summary says so instead
                // of repeating "At moonrise" for it (fix round 2, G-54).
                text = if (offset == null) TIMING_INVALID_OFFSET else describeTiming(draft.anchor, draft.direction, offset),
                style = MaterialTheme.typography.titleMedium,
                color = if (offset == null) Sun.TextSecondary else Sun.Gold,
            )
        }

        Spacer(Modifier.height(16.dp))

        SunCard {
            SectionLabel("Where the ${bodyWord(draft.kind)} is measured")
            Spacer(Modifier.height(8.dp))
            Text(
                text = placeIntro(draft.place, draft.kind),
                style = MaterialTheme.typography.bodySmall,
                color = Sun.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            CityPicker(
                state = catalogue,
                query = query,
                onQueryChange = { query = it },
                suggestions = suggestions,
                onSelect = { city: City ->
                    // Focus released and keyboard hidden before the place is handed on (SPEC 3.4,
                    // D31): the bottom bar stays hidden while the keyboard is open.
                    applyChosenPlace(
                        chosen = city,
                        release = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                        onChosen = { chosen ->
                            onDraftChange(
                                draft.copy(
                                    place = Place(
                                        label = "${chosen.name}, ${chosen.country}",
                                        latDeg = chosen.lat,
                                        lonDeg = chosen.lon,
                                        zoneId = chosen.tz,
                                        fromCatalogue = true,
                                    ),
                                ),
                            )
                            query = ""
                        },
                    )
                },
                selectedLabel = draft.place?.let(PlaceDisplay::name),
                onClear = { onDraftChange(draft.copy(place = null)) },
                suggestionLabel = { "Choose ${it.name} for this alarm" },
            )
            draft.place?.let { place ->
                Spacer(Modifier.height(10.dp))
                // Two pieces, so "Europe/" never parts from "Oslo." (R13, D138; the words are TEXTS G-20).
                ZoneCaption(EDITOR_ZONE_LEAD, place.zoneId, Sun.TextMuted)
            }
        }

        // Sun only: a fixed wall time is matched against a sun event (SPEC 7.7).
        if (draft.requestedTime != null && draft.place != null && draft.anchor is Anchor.Sun) {
            Spacer(Modifier.height(16.dp))
            MatchRequestedTimeCard(
                requested = draft.requestedTime,
                draft = draft,
                onApply = onDraftChange,
            )
        }

        Spacer(Modifier.height(16.dp))
        PreviewCard(preview = summary, rule = rule, offsetInvalid = offset == null)

        Spacer(Modifier.height(16.dp))
        ClampCard(
            draft = draft,
            onDraftChange = onDraftChange,
            onEditBound = { editingBound = it },
        )

        Spacer(Modifier.height(16.dp))
        SunCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Vibrate",
                        style = MaterialTheme.typography.titleSmall,
                        color = Sun.TextPrimary,
                    )
                    Text(
                        text = "In addition to the sound, when the phone allows it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sun.TextSecondary,
                    )
                }
                Switch(
                    checked = draft.vibrate,
                    onCheckedChange = { onDraftChange(draft.copy(vibrate = it)) },
                    colors = sunSwitchColours(),
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        if (rule == null) {
            Text(
                text = saveBlockedReason(draft, offset),
                style = MaterialTheme.typography.bodySmall,
                color = Sun.Rose,
            )
            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = { rule?.let(onSave) },
            enabled = rule != null,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Sun.Gold,
                contentColor = Sun.OnAccent,
                disabledContainerColor = Sun.Surface,
                disabledContentColor = Sun.TextMuted,
            ),
            modifier = Modifier.fillMaxWidth().growsWithText(CREATE_ALARM_MIN_HEIGHT),
        ) {
            Text(if (draft.isNew) "Create alarm" else "Save changes")
        }

        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete this alarm", color = Sun.Rose)
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    editingBound?.let { field ->
        TimeDialog(
            title = if (field == ClampBoundField.EARLIEST) "Never before" else "Never after",
            initial = when (field) {
                ClampBoundField.EARLIEST -> draft.earliest ?: LocalTime.of(6, 0)
                ClampBoundField.LATEST -> draft.latest ?: LocalTime.of(9, 0)
            },
            onDismiss = { editingBound = null },
            onPick = { picked ->
                onDraftChange(
                    when (field) {
                        ClampBoundField.EARLIEST -> draft.copy(earliest = picked)
                        ClampBoundField.LATEST -> draft.copy(latest = picked)
                    },
                )
                editingBound = null
            },
        )
    }
}

/** Which of the two clamp bounds a time dialog is currently editing. */
internal enum class ClampBoundField { EARLIEST, LATEST }

/* ------------------------------------------------------------------ *
 * Pieces
 * ------------------------------------------------------------------ */

@Composable
private fun EditorHeader(title: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Sun.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) { Text("Cancel", color = Sun.TextSecondary) }
    }
}

/** The two-chip switch between the sun and the moon (G-4). Keeps everything but the anchor. */
@Composable
private fun KindPicker(kind: AnchorKind, onSelect: (AnchorKind) -> Unit) {
    ChoiceRow(listOf("Sun", "Moon")) { index, modifier, fill ->
        if (index == 0) ChoiceChip("Sun", kind == AnchorKind.SUN, { onSelect(AnchorKind.SUN) }, modifier, fill)
        else ChoiceChip("Moon", kind == AnchorKind.MOON, { onSelect(AnchorKind.MOON) }, modifier, fill)
    }
}

/**
 * The anchors of the chosen kind as chips. For the sun, three chips, and a fourth note only when a
 * stored rule uses something else; for the moon, the five events with the line that says what the
 * chosen one is (G-8).
 *
 * The sun's note is read-only and cannot be selected. It exists so that opening a rule created by
 * a future version, or by hand in the JSON store, does not quietly rewrite its anchor to
 * sunrise the moment the editor renders. Losing someone's "astronomical dawn" rule by opening
 * it would be a data-loss bug dressed up as a simplification.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnchorPicker(anchor: Anchor, onSelect: (Anchor) -> Unit) {
    when (anchor) {
        is Anchor.Sun -> {
            ChoiceRow(EDITOR_ANCHORS.map { anchorLabel(it) }) { index, modifier, fill ->
                val candidate = EDITOR_ANCHORS[index]
                ChoiceChip(
                    label = anchorLabel(candidate),
                    selected = anchor.event == candidate,
                    onSelect = { onSelect(Anchor.Sun(candidate)) },
                    modifier = modifier,
                    fillWidth = fill,
                )
            }
            if (anchor.event !in EDITOR_ANCHORS) {
                Spacer(Modifier.height(10.dp))
                Notice(
                    title = "This alarm tracks ${anchorLabel(anchor).lowercase(Locale.UK)}",
                    body = "That is not one of the three this version offers. It is kept exactly as " +
                        "it is unless you pick one of the buttons above, which would replace it.",
                    accent = Sun.Blue,
                )
            }
        }

        is Anchor.Moon -> {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EDITOR_MOON_ANCHORS.forEach { candidate ->
                    ChoiceChip(
                        label = candidate.displayName,
                        selected = anchor.event == candidate,
                        onSelect = { onSelect(Anchor.Moon(candidate)) },
                        fillWidth = false,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = anchor.event.description,
                style = MaterialTheme.typography.bodySmall,
                color = Sun.TextSecondary,
            )
        }
    }
}

@Composable
private fun DirectionPicker(direction: Direction, onSelect: (Direction) -> Unit) {
    val choices = listOf(Direction.BEFORE to "Before", Direction.AT to "At", Direction.AFTER to "After")
    ChoiceRow(choices.map { it.second }) { index, modifier, fill ->
        val (value, label) = choices[index]
        ChoiceChip(label, direction == value, { onSelect(value) }, modifier, fill)
    }
}

/**
 * A row of equal chips (R8, D96). The chips share the row equally while the widest word of every label
 * fits its share (a chip's own padding allowed for); when one does not, at a large font, the chips take
 * their own width and flow onto as many lines as they need, so no label breaks inside a word. [chip] gets
 * the index, the modifier to give the chip and whether it fills its width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(labels: List<String>, chip: @Composable (index: Int, modifier: Modifier, fillWidth: Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val style = fitStyle(MaterialTheme.typography.labelLarge)
        val equal = choiceChipsEqual(
            labelWordPx = labels.map { measurer.widestWord(it, style) },
            rowPx = constraints.maxWidth.toFloat(),
            gapPx = with(density) { 8.dp.toPx() },
            chromePx = with(density) { CHOICE_CHIP_CHROME.toPx() },
        )
        if (equal) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.indices.forEach { chip(it, Modifier.weight(1f), true) }
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                labels.indices.forEach { chip(it, Modifier, false) }
            }
        }
    }
}

/**
 * The decision of D96: the chips of a [ChoiceRow] share the row equally while the widest word of every label
 * ([labelWordPx], one entry per chip) fits its share of [rowPx] (less the [gapPx] between chips and the chip's own
 * [chromePx]); otherwise they take their own width and flow.
 */
internal fun choiceChipsEqual(labelWordPx: List<Float>, rowPx: Float, gapPx: Float, chromePx: Float): Boolean {
    val sharePx = (rowPx - gapPx * (labelWordPx.size - 1)) / labelWordPx.size - chromePx
    return labelWordPx.all { it <= sharePx }
}

/** A chip's own width beside its label: 16 dp of padding on each side (measured, 32 dp in all) and 4 dp of slack. */
private val CHOICE_CHIP_CHROME = 36.dp

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    // The sun's and the direction's chips share a row equally and centre their words; the moon's
    // five chips flow over several rows at their own width.
    fillWidth: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = { if (!selected) onSelect() },
        label = {
            Text(
                text = label,
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Sun.Surface,
            labelColor = Sun.TextSecondary,
            selectedContainerColor = Sun.CardHover,
            selectedLabelColor = Sun.Gold,
        ),
        border = controlBorder(selected),
        modifier = modifier,
    )
}

@Composable
private fun OffsetFields(
    hours: String,
    minutes: String,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    invalid: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OffsetField(
            value = hours,
            onValueChange = onHoursChange,
            unit = "hours",
            invalid = invalid,
            modifier = Modifier.weight(1f),
        )
        OffsetField(
            value = minutes,
            onValueChange = onMinutesChange,
            unit = "minutes",
            invalid = invalid,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OffsetField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    invalid: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        // Digits only, filtered here rather than validated later: a number keyboard still
        // offers a minus sign and a decimal point on plenty of keyboards, and the sign of an
        // offset belongs to the Before/After buttons alone.
        onValueChange = { typed -> onValueChange(typed.filter { it.isDigit() }.take(2)) },
        singleLine = true,
        isError = invalid,
        label = { Text(unit, color = Sun.TextMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(14.dp),
        colors = sunFieldColours(),
        modifier = modifier,
    )
}

/**
 * The preview: the header numbers first, then every row (SPEC 7.7, D47).
 *
 * The header is the part that does the work. Thirty timestamps are data; "earliest 05:41,
 * latest 07:16, a spread of 1 h 35 min" is the sentence that tells somebody whether they want
 * this alarm. The rows are underneath for the person who wants to check a particular date,
 * and because a run of "solar noon" rows in December is more convincing seen than counted.
 *
 * A daily moon rule has no Earliest, Latest or Spread (its time walks round the clock, so a spread
 * near 24 hours says nothing): one line on the daily drift stands there, and the days without the
 * event are rows of their own. A monthly moon rule lists the next three nights.
 */
@Composable
private fun PreviewCard(preview: EditorPreview?, rule: AlarmRule?, offsetInvalid: Boolean = false) {
    SunCard {
        val moon = (rule?.anchor as? Anchor.Moon)?.event
        SectionLabel(
            if (moon != null && !moon.daily) monthlyHeading(moon) else "Next $PREVIEW_DAYS days",
        )
        Spacer(Modifier.height(8.dp))

        if (preview == null || rule == null || preview.rows.isEmpty()) {
            Text(
                text = previewPlaceholder(offsetInvalid),
                style = MaterialTheme.typography.bodySmall,
                color = Sun.TextMuted,
            )
            return@SunCard
        }

        val summary = preview.summary
        val zone = resolveZone(rule.zoneId)
        if (preview.group != PreviewGroup.MONTHLY_MOON) {
            Text(
                text = "Twinlit has no fixed alarm time. This is what the rule actually does over " +
                    "the next month.",
                style = MaterialTheme.typography.bodySmall,
                color = Sun.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
        }

        when (preview.group) {
            PreviewGroup.SUN -> {
                SpreadRow("Earliest", summary.earliest, zone)
                Spacer(Modifier.height(8.dp))
                SpreadRow("Latest", summary.latest, zone)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Spread",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sun.TextMuted,
                        modifier = Modifier.width(previewLabelWidth()),
                    )
                    Text(
                        text = if (summary.spreadMinutes == 0) {
                            "Under a minute across the whole month"
                        } else {
                            "${formatOffset(summary.spreadMinutes)} across the month"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = Sun.Orange,
                    )
                }
            }

            PreviewGroup.DAILY_MOON -> Text(
                text = moonDriftLine(moon ?: LunarEvent.MOONRISE),
                style = MaterialTheme.typography.titleSmall,
                color = Sun.Orange,
            )

            PreviewGroup.MONTHLY_MOON -> Unit
        }

        if (preview.group == PreviewGroup.SUN && summary.fallbackDays > 0) {
            Spacer(Modifier.height(14.dp))
            Notice(
                title = fallbackTitle(rule.anchor, summary),
                body = "The sun does not reach that point on ${summary.fallbackDays} of these " +
                    "days at ${rule.displayPlaceName()}. Those alarms ring at solar noon instead, so " +
                    "the alarm never simply goes missing. The rows below are marked.",
                accent = Sun.Orange,
            )
        }

        if (preview.group == PreviewGroup.DAILY_MOON && preview.missingDays > 0 && moon != null) {
            Spacer(Modifier.height(14.dp))
            Notice(
                title = moonMissingTitle(moon, preview.rows),
                body = moonMissingBody(moon, preview.missingDays, rule.displayPlaceName()),
                accent = Sun.Blue,
            )
        }

        if (summary.clampedDays > 0) {
            Spacer(Modifier.height(10.dp))
            Notice(
                title = clampedTitle(preview.group, summary.clampedDays),
                body = clampedBody(rule.kind),
                accent = Sun.Blue,
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Sun.Border)
        preview.rows.forEach { day ->
            if (day.occurrences.isNotEmpty()) {
                day.occurrences.forEach { row -> PreviewRow(row, zone) }
            } else if (moon != null) {
                MissingDayRow(day.date, moon)
            }
        }
    }
}

/** "No sunrise on 6 of the next 30 days", with the anchor's own word. */
private fun fallbackTitle(anchor: Anchor, summary: PreviewSummary): String {
    val first = summary.rows.firstOrNull { it.usedFallback }
    val event = anchorLabel(anchor).lowercase(Locale.UK)
    return if (first != null && first == summary.rows.firstOrNull()) {
        // The nearest row is the one the user is about to be woken by, so it gets named
        // rather than counted: "no sunrise tomorrow" is the warning the owner asked for.
        "No $event on ${SHORT_DATE.format(first.anchorDate)}"
    } else {
        "No $event on ${summary.fallbackDays} of the next $PREVIEW_DAYS days"
    }
}

/** A day of a daily moon preview with no event: the day stays in the list and says so (G-30). */
@Composable
private fun MissingDayRow(date: LocalDate, event: LunarEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SHORT_DATE.format(date),
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextSecondary,
            modifier = Modifier.width(previewLabelWidth()),
        )
        Text(
            text = "No ${moonEventWord(event)} on ${SHORT_DATE.format(date)}",
            style = MaterialTheme.typography.bodySmall,
            color = Sun.Blue,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The width of the first column of the 30-day preview: 84 dp, or wider when a word of the column does
 * not fit 84 dp at the font in use ("Earliest" at font scale 2.0): the widest of the column's words at
 * the style the column is set in, and 10 dp, so a label never breaks and never touches the clock (R8, D96)
 * and the rows stay aligned.
 */
@Composable
private fun previewLabelWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = fitStyle(MaterialTheme.typography.bodySmall)
    val widest = PREVIEW_COLUMN_WORDS.maxOf { measurer.widestWord(it, style) }
    return previewLabelColumnDp(with(density) { widest.toDp() }.value).dp
}

/** The first column of the preview is never narrower than this (R4), and is [PREVIEW_COLUMN_SLACK_DP] wider than its widest word. */
internal const val PREVIEW_COLUMN_MIN_DP = 84f
internal const val PREVIEW_COLUMN_SLACK_DP = 10f

/** The decision of D96: 84 dp, or the widest word of the column plus 10 dp when that is more, so a label never breaks and never touches the clock beside it. */
internal fun previewLabelColumnDp(widestWordDp: Float): Float = maxOf(PREVIEW_COLUMN_MIN_DP, widestWordDp + PREVIEW_COLUMN_SLACK_DP)

/** The longest first-column texts of the preview: the labels, and a short date at its widest. */
internal val PREVIEW_COLUMN_WORDS = listOf("Earliest", "Latest", "Spread", "Wed 30 Sep", "Mon 28 Sep")

@Composable
private fun SpreadRow(label: String, occurrence: Occurrence?, zone: ZoneId) {
    if (occurrence == null) return
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextMuted,
            modifier = Modifier.width(previewLabelWidth()),
        )
        Text(
            text = CLOCK.format(occurrence.fireAt.atZone(zone)),
            style = MaterialTheme.typography.titleMedium,
            color = Sun.TextPrimary,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = SHORT_DATE.format(occurrence.anchorDate),
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextSecondary,
        )
    }
}

@Composable
private fun PreviewRow(row: Occurrence, zone: ZoneId) {
    val fireDate = row.fireAt.atZone(zone).toLocalDate()
    val dayShift = fireDate.toEpochDay() - row.anchorDate.toEpochDay()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SHORT_DATE.format(row.anchorDate),
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextSecondary,
            modifier = Modifier.width(previewLabelWidth()),
        )
        Text(
            text = CLOCK.format(row.fireAt.atZone(zone)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.usedFallback) Sun.Orange else Sun.TextPrimary,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildList {
                // The offset can push a fire time off the day its anchor belongs to. Saying so
                // per row is the only way the calendar reads honestly, because the left-hand
                // date is the anchor's, not the alarm's.
                if (dayShift == 1L) add("next day")
                if (dayShift == -1L) add("day before")
                if (dayShift > 1L) add("$dayShift days later")
                if (dayShift < -1L) add("${-dayShift} days earlier")
                if (row.usedFallback) add("solar noon")
                if (row.clamped) add("clamped")
            }.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = if (row.usedFallback) Sun.Orange else Sun.TextMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The clamp: collapsed, and off, until somebody asks for it (owner's confirmed decision 2).
 *
 * The copy inside argues against using it, which is deliberate. A clamp stops the alarm
 * tracking the sun, which is the only thing Twinlit does that a normal alarm clock cannot, so
 * the honest presentation is "here it is, here is what it costs you".
 */
@Composable
private fun ClampCard(
    draft: RuleDraft,
    onDraftChange: (RuleDraft) -> Unit,
    onEditBound: (ClampBoundField) -> Unit,
) {
    SunCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (draft.clampOpen) "Hide the limits" else "Show the limits",
                ) { onDraftChange(draft.copy(clampOpen = !draft.clampOpen)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Limits (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = Sun.TextPrimary,
                )
                Text(
                    text = when {
                        draft.earliest == null && draft.latest == null -> clampOffText(draft.kind)
                        draft.earliest != null && draft.latest != null ->
                            "Between ${CLOCK.format(draft.earliest)} and ${CLOCK.format(draft.latest)}"
                        draft.earliest != null -> "Never before ${CLOCK.format(draft.earliest)}"
                        else -> "Never after ${CLOCK.format(draft.latest)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draft.earliest == null && draft.latest == null) Sun.TextMuted else Sun.Blue,
                )
            }
            Text(
                text = if (draft.clampOpen) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                color = Sun.TextSecondary,
            )
        }

        if (draft.clampOpen) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = clampIntro(draft.kind),
                style = MaterialTheme.typography.bodySmall,
                color = Sun.TextSecondary,
            )
            if (previewGroup(draft.anchor) == PreviewGroup.DAILY_MOON) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = CLAMP_MOON_DRIFT,
                    style = MaterialTheme.typography.bodySmall,
                    color = Sun.TextSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            BoundRow(
                label = "Never before",
                value = draft.earliest,
                onEdit = { onEditBound(ClampBoundField.EARLIEST) },
                onClear = { onDraftChange(draft.copy(earliest = null)) },
            )
            Spacer(Modifier.height(8.dp))
            BoundRow(
                label = "Never after",
                value = draft.latest,
                onEdit = { onEditBound(ClampBoundField.LATEST) },
                onClear = { onDraftChange(draft.copy(latest = null)) },
            )
        }
    }
}

@Composable
private fun BoundRow(
    label: String,
    value: LocalTime?,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Sun.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        val shape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .background(Sun.Surface, shape)
                .border(1.dp, if (value == null) Sun.Border else Sun.BorderActive, shape)
                .clickable(role = Role.Button, onClickLabel = "Set $label") { onEdit() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = value?.let { CLOCK.format(it) } ?: "Not set",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == null) Sun.TextMuted else Sun.TextPrimary,
                // "Not set" inherits Inter from the style; a set time is monospaced.
                fontFamily = if (value == null) null else FontFamily.Monospace,
            )
        }
        if (value != null) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClickLabel = "Clear $label") { onClear() }
                    .clearAndSetSemantics {
                        contentDescription = "Clear $label"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Sun.TextSecondary, fontSize = 20.sp)
            }
        }
    }
}

/**
 * The offer to reproduce a wall time asked for by another app.
 *
 * `AlarmClock.ACTION_SET_ALARM` names an hour and a minute, which Twinlit has no way of
 * honouring as such: every alarm here is anchored to the sun. Rather than refuse or, worse,
 * pretend, the editor computes the offset that lands on that time **today** and then lets the
 * preview show how far it walks afterwards. The user gets what they asked for tonight and can
 * see what it becomes by next month.
 */
@Composable
private fun MatchRequestedTimeCard(
    requested: LocalTime,
    draft: RuleDraft,
    onApply: (RuleDraft) -> Unit,
) {
    SunCard(borderColour = Sun.BorderActive) {
        Text(
            text = "Another app asked for ${CLOCK.format(requested)}",
            style = MaterialTheme.typography.titleSmall,
            color = Sun.Gold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Twinlit alarms track the sun or the moon, so there is no fixed time to set. This " +
                "matches ${CLOCK.format(requested)} on today's date and then follows " +
                "${anchorNoun(draft.anchor)} from there.",
            style = MaterialTheme.typography.bodySmall,
            color = Sun.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { onApply(matchRequestedTime(draft, requested)) }) {
            Text("Match it today", color = Sun.Gold)
        }
    }
}

/**
 * Set [RuleDraft.direction] and the offset boxes so that today's fire time is [requested].
 *
 * The anchor instant comes from [OccurrenceEngine], not from a second call into the solar
 * layer, so the offset this produces is the offset the scheduler will actually use. Returns
 * the draft unchanged when there is no place yet, or when the day has no anchor at all and
 * the engine has already substituted solar noon: matching a time against a substitution would
 * bake today's fallback into a permanent offset.
 */
internal fun matchRequestedTime(draft: RuleDraft, requested: LocalTime): RuleDraft {
    // A fixed time is matched against a sun event only: the moon has days with no event to match against.
    if (draft.anchor !is Anchor.Sun) return draft
    val probe = draft.copy(direction = Direction.AT).toRule() ?: return draft
    val zone = resolveZone(probe.zoneId)
    val today = LocalDate.now(zone)
    val occurrence = OccurrenceEngine.occurrenceFor(probe, today) ?: return draft
    if (occurrence.usedFallback) return draft

    val target: Instant = today.atTime(requested).atZone(zone).toInstant()
    val deltaMinutes = Math.round((target.toEpochMilli() - occurrence.anchorAt.toEpochMilli()) / 60_000.0)
    val magnitude = Math.abs(deltaMinutes).toInt()
    return draft.copy(
        direction = if (deltaMinutes < 0) Direction.BEFORE else Direction.AFTER,
        offsetHours = (magnitude / 60).toString(),
        offsetMinutes = (magnitude % 60).toString(),
        requestedTime = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    // 24-hour, always. The rest of Twinlit prints HH:mm:ss, the product is British and
    // Norwegian, and a clamp bound of "7" that turns out to have meant the evening is exactly
    // the kind of mistake an alarm clock must not make possible.
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = Sun.Surface) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Sun.TextPrimary,
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Sun.TextSecondary) }
                    TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) {
                        Text("Set", color = Sun.Gold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun sunFieldColours() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Sun.TextPrimary,
    unfocusedTextColor = Sun.TextPrimary,
    disabledTextColor = Sun.TextMuted,
    focusedContainerColor = Sun.Surface,
    unfocusedContainerColor = Sun.Surface,
    disabledContainerColor = Sun.Surface,
    focusedBorderColor = Sun.BorderActive,
    unfocusedBorderColor = Sun.Border,
    disabledBorderColor = Sun.Border,
    errorBorderColor = Sun.Rose,
    cursorColor = Sun.Gold,
)

@Composable
internal fun sunSwitchColours() = SwitchDefaults.colors(
    checkedThumbColor = Sun.OnAccent,
    checkedTrackColor = Sun.Gold,
    checkedBorderColor = Sun.Gold,
    uncheckedThumbColor = Sun.TextMuted,
    uncheckedTrackColor = Sun.Surface,
    // The light palettes' hairline Border is 1.3 to 1 on white: the off switch's outline is the
    // muted text colour there (WCAG 1.4.11, fix round 1).
    uncheckedBorderColor = if (LocalSunTokens.current.isLight) Sun.TextMuted else Sun.Border,
)

/** Thirty days, which is the window the owner asked for and roughly one month of drift. */
internal const val PREVIEW_DAYS: Int = 30

/** The nights a monthly moon rule's preview lists (SPEC 7.7). */
internal const val PREVIEW_NIGHTS: Int = 3

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
