package no.stormberry.twinlit.ui.moon

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.stormberry.labs.carousel.LabsApp
import no.stormberry.labs.carousel.LabsCarousel
import no.stormberry.labs.carousel.LabsCarouselColors
import no.stormberry.twinlit.data.InputMode
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.lunar.LunarDayKind
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.lunar.MoonCalc
import no.stormberry.twinlit.lunar.MoonPhase
import no.stormberry.twinlit.solar.PlaceDay
import no.stormberry.twinlit.ui.CatalogueState
import no.stormberry.twinlit.ui.PickerColours
import no.stormberry.twinlit.ui.PlacePicker
import no.stormberry.twinlit.ui.components.AppFooter
import no.stormberry.twinlit.ui.components.DateBar
import no.stormberry.twinlit.ui.components.FooterColours
import no.stormberry.twinlit.ui.components.InputCard
import no.stormberry.twinlit.ui.components.MoonHero
import no.stormberry.twinlit.ui.components.MOON_ZONE_LEAD
import no.stormberry.twinlit.ui.components.RowSpec
import no.stormberry.twinlit.ui.components.ZoneCaption
import no.stormberry.twinlit.ui.components.displayBarDate
import no.stormberry.twinlit.ui.components.fitDateText
import no.stormberry.twinlit.ui.components.fitStyle
import no.stormberry.twinlit.ui.components.formatBarDate
import no.stormberry.twinlit.ui.components.lineWidth
import no.stormberry.twinlit.ui.components.rowsNeedStacking
import no.stormberry.twinlit.ui.components.moonDateBarColours
import no.stormberry.twinlit.ui.components.drawSkyGlow
import no.stormberry.twinlit.ui.theme.LocalMoonTokens
import no.stormberry.twinlit.ui.theme.Moon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Moon tab (SPEC 6): MoonApp's Moon times screen, what the moon is doing, where the chosen
 * place is, on the day you pick. Top to bottom (round R4, Marcos, 2026-11-20): the hero, the input
 * (the shared [PlacePicker], in the Moon palette, as on the Sun tab), the shared [DateBar], then
 * the output: the phase card and the times card, or the empty state before a place is chosen.
 *
 * Ported from MoonApp `68c7596` (`ui/MoonTimesScreen.kt`), keeping its rules:
 *
 *  - **Everything is sampled at 12:00 UTC of the selected date**, which is what the web app does
 *    (`app.js:315`); [MoonPhase.noonUtc] is the single place that decision lives. Illumination is
 *    rounded as the web rounds it, not truncated.
 *  - **The place's zone**, not UTC and not the phone's, for every day boundary and for "today".
 *  - **Three polar messages, not one**: the moon up all day, the moon down all day, and a date the
 *    place's clocks skipped; a transit time is suppressed on an always-down day.
 *  - The next new moon night and full moon night on or after this date, in the place's zone (R5).
 *
 * What changed, and only this (SPEC 6.2, R4):
 *
 *  - **The place is the app's one [Place]**, handed in with [onPlaceChange]; there is no Oslo
 *    default. With no place the two cards are replaced by one empty-state card (E-27, E-28).
 *    MoonApp's rewriting of every alarm on a place change is dropped (D-M1): each alarm keeps the
 *    place it was made for.
 *  - **The picker and the date bar are the Sun tab's** (R4): MoonApp's own picker card and date
 *    bar are gone. The picker keeps its half-typed text itself ([PlacePicker]); the mode (City
 *    search or Coordinates) is the root's, shared with the Sun tab.
 *  - **The date is held by the root** ([dateEpochDay]), as for the Sun tab, so a place chosen on
 *    another tab moves it and it survives a visit to another tab. Not persisted.
 *  - The colours come from the `Moon` facade, which follows the theme (SPEC 4). The Labs carousel
 *    and the footer end the page, in the Moon palette of the theme in effect.
 *
 * @param dateEpochDay the shown date as an epoch day, so shifting a day is an addition
 */
@Composable
fun MoonTimesScreen(
    catalogue: CatalogueState,
    place: Place?,
    mode: InputMode,
    onModeChange: (InputMode) -> Unit,
    onPlaceChange: (Place?) -> Unit,
    dateEpochDay: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMoonTokens.current

    val moonPlace = remember(place) { place?.let(MoonPlace::of) }
    val zone = remember(moonPlace?.zoneId) { PlaceDay.zoneOf(moonPlace?.zoneId) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    val today = PlaceDay.today(zone)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Moon.Background)
            .drawBehind { drawSkyGlow(tokens.GlowTop, tokens.GlowLeft, tokens.GlowBottom) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp),
            // No side padding on the column itself: the other items take the 18 dp gutter from
            // [gutter], and the footer is full width, as MoonApp's was.
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoonHero(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))

            // The input first (R4), the Sun tab's input part exactly (R5, D14): the same card, the same
            // picker, 12 dp, the same date bar, in the Moon palette.
            Column(modifier = gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InputCard(fill = Moon.Card, border = Moon.Border) {
                    PlacePicker(
                        catalogue = catalogue,
                        place = place,
                        mode = mode,
                        onModeChange = onModeChange,
                        onPlaceChange = onPlaceChange,
                        colours = PickerColours.of(LocalMoonTokens.current),
                        suggestionLabel = { "Show moon times for ${it.name}" },
                    )
                }
                // The shared date bar, always shown, also before a place is chosen (as on the Sun tab).
                DateBar(
                    date = date,
                    isToday = date == today,
                    onShift = { onDateChange(dateEpochDay + it) },
                    onToday = { onDateChange(today.toEpochDay()) },
                    onPick = { onDateChange(it.toEpochDay()) },
                    colours = moonDateBarColours(),
                )
            }

            // The output comes after the input.
            if (moonPlace == null) {
                EmptyCard(modifier = gutter)
            } else {
                PhaseCard(
                    place = moonPlace,
                    date = date,
                    zone = zone,
                    modifier = gutter,
                )
                TimesCard(place = moonPlace, date = date, zone = zone, modifier = gutter)
            }

            // The website's "Explore the Stormberry Ecosystem" carousel, above the footer as there.
            // Every card opens its website through ACTION_VIEW: no INTERNET permission.
            Column {
                Spacer(Modifier.height(16.dp))
                LabsCarousel(current = LabsApp.TWINLIT, colors = moonCarouselColours())
            }

            // CheckLink's footer, edge to edge. The 16 dp spacing above it, and the 16 dp
            // here, make the 32 dp CheckLink leaves between its last card and its footer.
            Column {
                Spacer(Modifier.height(16.dp))
                AppFooter(moonFooterColours())
            }
        }
    }
}

/**
 * The colours of the Labs carousel on the Moon tab, MoonApp's own mapping: `--text-secondary`
 * heading, `--bg-card` cards with a `--border` edge, `--accent-teal` on the app's own card.
 */
@Composable
private fun moonCarouselColours() = LabsCarouselColors(
    title = Moon.TextSecondary,
    label = Moon.TextPrimary,
    cardFill = Moon.Card,
    cardBorder = Moon.Border,
    accent = Moon.Teal,
    rule = Moon.Border,
    checkmark = Moon.Background,
)

/** The footer's colours on the Moon tab: its own palette in the theme in effect. */
@Composable
private fun moonFooterColours() = FooterColours(
    background = Moon.Background,
    border = Moon.Border,
    textPrimary = Moon.TextPrimary,
    // MoonApp's body lines in TextSoft (9.99 to 1), its small print and icons in TextMutedReadable.
    textSecondary = Moon.TextSoft,
    smallPrint = Moon.TextMutedReadable,
    link = Moon.Blue,
)

/** The 18 dp side gutter of every item, the Sun tab's margin (R5, D13). */
private val gutter = Modifier.padding(horizontal = 18.dp)

/* ------------------------------------------------------------------ *
 * Formatting (pure)
 * ------------------------------------------------------------------ */

/**
 * The long date of the next-moon rows: "Wednesday 25 November 2026", MoonApp's `EEEE d MMMM yyyy`,
 * in Locale.UK whatever the phone's language (as the Sun tab, the widgets and the rest of the
 * English-only texts; MoonApp used the phone's locale, so a Norwegian phone showed a Norwegian
 * weekday between English labels). It is the date bar's own form ([displayBarDate], R7 D85): the space
 * before the year is a no-break space, so a date that does not fit one line breaks after the day and
 * never leaves the year alone, as on the Sun tab's Date row (D81).
 */
internal fun formatMoonDate(date: LocalDate): String = displayBarDate(date)

/**
 * A time on the card: "18:42" in the place's zone, or an en dash (U+2013) when the event does not
 * happen that day, the same text as on the home screen widgets (MoonApp 1.0.8; Locale.UK whatever
 * the phone's language, so the digits never change script).
 */
internal fun cardTime(at: Instant?, zone: ZoneId): String =
    at?.atZone(zone)?.format(TIME_FORMAT) ?: NO_TIME

internal const val NO_TIME = "–"

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

/** "Illumination" as the web rounds it (`app.js:357`, Math.round), not truncated. */
internal fun percentText(fraction: Double): String = "${Math.round(fraction * 100)}%"

/** "384,400 km": the distance with thousands separators. */
internal fun distanceText(km: Double): String = "${"%,d".format(Locale.UK, Math.round(km))} km"

/** TEXTS.md E-27 and E-28 (approved by Marcos 2026-11-22), the Moon tab's empty state. */
internal const val EMPTY_TITLE = "No place chosen yet"
internal const val EMPTY_BODY =
    "Choose a city above, or type coordinates. Twinlit then shows the moon's phase, moonrise, " +
        "transit and moonset for that place, computed on this device."

/** TEXTS.md E-18, E-19, E-20 to E-22 (SHIPPED), the polar and skipped-date notices. */
internal const val SKIPPED_TITLE = "This date did not happen here"
internal fun skippedBody(zone: ZoneId) = "The clocks in ${zone.id} skipped it. Please select another date."
internal const val ALWAYS_UP_TITLE = "The moon is above the horizon all day"
internal const val ALWAYS_DOWN_TITLE = "The moon stays below the horizon all day"
internal const val NO_RISE_SET_BODY = "It neither rises nor sets on this date at this latitude."

/* ------------------------------------------------------------------ *
 * Screen pieces
 * ------------------------------------------------------------------ */

@Composable
private fun EmptyCard(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Moon.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            PolarNotice(EMPTY_TITLE, EMPTY_BODY, Moon.Silver)
        }
    }
}

@Composable
private fun PhaseCard(
    place: MoonPlace,
    date: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val noon = remember(date) { MoonPhase.noonUtc(date) }
    val illum = remember(noon) { MoonCalc.illumination(noon) }
    val position = remember(noon, place.latDeg, place.lonDeg) {
        MoonCalc.position(noon, place.latDeg, place.lonDeg)
    }
    // The next new moon night and full moon night on or after this date, in the place's zone (R5, D7):
    // the nights a full or new moon night alarm rings in, so the card and the Alarms tab agree.
    val nights = remember(date, zone) { MoonPhase.nextNights(date, zone) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Moon.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                MoonPhase.name(illum.phase),
                style = MaterialTheme.typography.titleMedium,
                color = Moon.Silver,
            )
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val style = fitStyle(MaterialTheme.typography.bodyMedium)
                val rowPx = constraints.maxWidth.toFloat()
                val gapPx = with(density) { STAT_LABEL_GAP.toPx() }
                val illumination = percentText(illum.fraction)
                val distance = distanceText(position.distanceKm)
                val newNight = nights.newNight
                val fullNight = nights.fullNight
                val rows = listOf(
                    RowSpec("Illumination", style, illumination, style),
                    RowSpec("Distance", style, distance, style),
                    RowSpec("New moon night", style, newNight?.let(::formatBarDate) ?: "Not found", style),
                    RowSpec("Full moon night", style, fullNight?.let(::formatBarDate) ?: "Not found", style),
                )
                val stacked = measurer.rowsNeedStacking(rowPx, STAT_LABEL_SHARE, gapPx, rows)
                val valuePx = if (stacked) rowPx else rowPx * (1f - STAT_LABEL_SHARE)
                // The date's form follows the width it gets (R8, D90): the no-break space before the year
                // while "November 2026" fits, a plain space when it does not.
                fun dateText(date: LocalDate?): String =
                    date?.let { fitDateText(it, valuePx) { text -> measurer.lineWidth(text, style) } } ?: "Not found"
                Column(modifier = Modifier.fillMaxWidth()) {
                    StatRow("Illumination", illumination, stacked)
                    StatRow("Distance", distance, stacked)
                    StatRow("New moon night", dateText(newNight), stacked)
                    StatRow("Full moon night", dateText(fullNight), stacked)
                }
            }

            Spacer(Modifier.height(12.dp))
            // A place typed as coordinates shows its name, whole, or "Your coordinates" when it has
            // none, with the coordinates under it (W5).
            Text(
                place.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Moon.TextPrimary,
            )
            Text(
                place.coordinateLine,
                style = MaterialTheme.typography.bodySmall,
                color = Moon.TextSecondary,
            )
        }
    }
}

@Composable
private fun TimesCard(place: MoonPlace, date: LocalDate, zone: ZoneId, modifier: Modifier = Modifier) {
    // The place's zone, not UTC: the whole fix for the app disagreeing with moon.stormberry.as,
    // see the KDoc on MoonCalc.times.
    val times = remember(place.latDeg, place.lonDeg, date, zone) {
        MoonCalc.times(date, place.latDeg, place.lonDeg, zone)
    }
    val kind = remember(place.latDeg, place.lonDeg, date, zone) {
        MoonCalc.dayKind(date, place.latDeg, place.lonDeg, zone)
    }
    // A date the place's clocks skipped (Pacific/Apia, 30 December 2011) has no times to show, and
    // the polar notice would contradict the dashes beside it.
    val occurs = remember(date, zone) { MoonCalc.dateOccurs(date, zone) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Moon.Card),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            if (!occurs) {
                PolarNotice(SKIPPED_TITLE, skippedBody(zone), Moon.Silver)
            } else {
                when (kind) {
                    LunarDayKind.ALWAYS_UP -> PolarNotice(ALWAYS_UP_TITLE, NO_RISE_SET_BODY, Moon.Silver)
                    LunarDayKind.ALWAYS_DOWN -> PolarNotice(ALWAYS_DOWN_TITLE, NO_RISE_SET_BODY, Moon.NewMoon)
                    LunarDayKind.NORMAL -> Unit
                }

                if (kind != LunarDayKind.NORMAL) Spacer(Modifier.height(12.dp))

                TimeRow("Moonrise", cardTime(times[LunarEvent.MOONRISE], zone), Moon.Blue)
                // Suppressed when the moon is below the horizon for the whole day: a peak altitude
                // for a moon that never comes up contradicts the rows around it.
                if (kind != LunarDayKind.ALWAYS_DOWN) {
                    TimeRow("Transit", cardTime(times[LunarEvent.LUNAR_TRANSIT], zone), Moon.Violet)
                }
                TimeRow("Moonset", cardTime(times[LunarEvent.MOONSET], zone), Moon.Teal)

                Spacer(Modifier.height(10.dp))
                ZoneCaption(MOON_ZONE_LEAD, zone.id, Moon.TextHint)
            }
        }
    }
}

/**
 * One label and value of the phase card. Side by side the columns are fixed shares (R7, D85): the label
 * takes [STAT_LABEL_SHARE] of the row and the value the rest, flush right, so a long date has a known width
 * and breaks after the day instead of running into the label. When a word of a label or a value does not
 * fit its column at a large font, the card puts every label over its value ([stacked], R8, D91).
 */
@Composable
private fun StatRow(label: String, value: String, stacked: Boolean) {
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Moon.TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Moon.TextPrimary, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Moon.TextSecondary,
                modifier = Modifier.weight(STAT_LABEL_SHARE).padding(end = STAT_LABEL_GAP),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = Moon.TextPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f - STAT_LABEL_SHARE),
            )
        }
    }
    Spacer(Modifier.height(if (stacked) 8.dp else 4.dp))
}

/** The label's share of a phase card row (R7, D85: 0.45) and the gap after it. */
internal const val STAT_LABEL_SHARE = 0.45f
internal val STAT_LABEL_GAP = 12.dp

@Composable
private fun TimeRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = accent)
        // A time is one line (R7, D88): "18:42" has no place to break, and now says so.
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Moon.TextPrimary, softWrap = false, maxLines = 1)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PolarNotice(title: String, body: String, accent: Color) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Moon.TextSecondary)
    }
}
