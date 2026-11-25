package no.stormberry.twinlit.ui.sun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import no.stormberry.labs.carousel.LabsApp
import no.stormberry.labs.carousel.LabsCarousel
import no.stormberry.labs.carousel.LabsCarouselColors
import no.stormberry.twinlit.data.InputMode
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.PlaceDisplay
import no.stormberry.twinlit.solar.DayKind
import no.stormberry.twinlit.solar.PlaceDay
import no.stormberry.twinlit.solar.SunTimesResult
import no.stormberry.twinlit.solar.computeSunTimes
import no.stormberry.twinlit.ui.CatalogueState
import no.stormberry.twinlit.ui.PlacePicker
import no.stormberry.twinlit.ui.components.AppFooter
import no.stormberry.twinlit.ui.components.DateBar
import no.stormberry.twinlit.ui.components.displayBarDate
import no.stormberry.twinlit.ui.components.sunDateBarColours
import no.stormberry.twinlit.ui.components.FooterColours
import no.stormberry.twinlit.ui.components.Notice
import no.stormberry.twinlit.ui.components.SunCard
import no.stormberry.twinlit.ui.components.SunHero
import no.stormberry.twinlit.ui.components.drawSkyGlow
import no.stormberry.twinlit.ui.displayCoordinates
import no.stormberry.twinlit.ui.theme.LocalSunTokens
import no.stormberry.twinlit.ui.theme.Sun
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Sun tab (SPEC 5): SunApp's Sun times screen, in the information architecture of the web
 * app and in its order: the place picker first, the date underneath it, then the coordinates,
 * date and timezone summary, then sunrise, solar noon and sunset side by side, then the
 * day-length bar.
 *
 * Ported from SunApp `2a26f98` (`ui/SunTimesScreen.kt`). What changed, and only this:
 *
 *  - **The place is shared** (SPEC 3.3). It is not read from or written to `Settings` here: the
 *    root holds one place for all three tabs and hands it in with [onPlaceChange]. The catalogue
 *    is loaded once by the root too. The picker logic (search, typed coordinates, the name rule,
 *    focus release) is `ui/PlacePicker.kt`, lifted out of this screen in milestone M1.
 *  - **The date is held by the root** ([dateEpochDay]), because a place chosen on another tab
 *    must move this tab's date as well (`PlaceDay.afterPlaceChange`, SPEC 3.3), and because the
 *    tab's composition is dropped while another tab shows (its state is kept by the root, so the
 *    date and the scroll position survive a visit to another tab). Not persisted: opening the app
 *    tomorrow shows tomorrow.
 *  - **The hero** says "Sun times" and the colours come from the `Sun` facade, which follows the
 *    theme (SPEC 4). The Labs carousel and the footer end the page (M7, fix round 1); a 40 dp
 *    spacer ends the column. The widget refresh on a place change moves to the root (M6).
 *
 * No ViewModel. State is `remember` plus `mutableStateOf`, as SunApp has it.
 *
 * @param dateEpochDay the shown date as an epoch day, so shifting a day is an addition
 */
@Composable
fun SunTimesScreen(
    catalogue: CatalogueState,
    place: Place?,
    mode: InputMode,
    onModeChange: (InputMode) -> Unit,
    onPlaceChange: (Place?) -> Unit,
    dateEpochDay: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSunTokens.current
    val date = LocalDate.ofEpochDay(dateEpochDay)

    // Pure, cheap and keyed on exactly the two things it depends on, so scrolling and typing
    // never recompute it.
    val result = remember(place, date) { place?.let { computeSunTimes(it, date) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Sun.Background)
            .drawBehind { drawSkyGlow(tokens.GlowWarm, tokens.GlowGold, tokens.GlowCool) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 40.dp)) {
                SunHero()
                Spacer(Modifier.height(32.dp))

                SunCard {
                    PlacePicker(
                        catalogue = catalogue,
                        place = place,
                        mode = mode,
                        onModeChange = onModeChange,
                        onPlaceChange = onPlaceChange,
                    )
                }

                Spacer(Modifier.height(12.dp))
                // The one date bar of the Sun and Moon tabs, in its own box under the input (R4).
                val today = PlaceDay.today(PlaceDay.zoneOf(place?.zoneId))
                DateBar(
                    date = date,
                    isToday = date == today,
                    onShift = { days -> onDateChange(dateEpochDay + days) },
                    onToday = { onDateChange(today.toEpochDay()) },
                    onPick = { picked -> onDateChange(picked.toEpochDay()) },
                    colours = sunDateBarColours(),
                )

                Spacer(Modifier.height(20.dp))

                if (result == null) {
                    EmptyResults()
                } else {
                    SunResults(result = result, placeLabel = place?.let(PlaceDisplay::name).orEmpty())
                }

                Spacer(Modifier.height(40.dp))
            }
            // The website's "Explore the Stormberry Ecosystem" carousel, above the footer as there;
            // the footer ends the page (SPEC 10.1, 10.3). Every card opens its website through
            // ACTION_VIEW: no INTERNET permission.
            LabsCarousel(current = LabsApp.TWINLIT, colors = sunCarouselColours())
            AppFooter(sunFooterColours())
        }
    }
}

/* ------------------------------------------------------------------ *
 * Screen pieces
 * ------------------------------------------------------------------ */

/** TEXTS.md D-22 to D-25 (SHIPPED): the polar notices, above the cards they explain. */
internal const val MIDNIGHT_SUN_TITLE = "Midnight sun"
internal const val MIDNIGHT_SUN_BODY =
    "The sun does not set on this date at this place, so there is no " +
        "sunrise and no sunset to give. Solar noon still applies: it is when " +
        "the sun is at its highest."
internal const val POLAR_NIGHT_TITLE = "Polar night"
internal const val POLAR_NIGHT_BODY =
    "The sun does not rise on this date at this place, so there is no " +
        "sunrise and no sunset to give. Solar noon still applies: it is when " +
        "the sun comes closest to the horizon."

@Composable
private fun SunResults(result: SunTimesResult, placeLabel: String) {
    // The place name heads the results rather than sitting between the two cards:
    // it is the answer to "where are these times for", so it has to be read before
    // the times are, not after.
    if (placeLabel.isNotEmpty()) {
        Text(
            text = placeLabel,
            style = MaterialTheme.typography.titleMedium,
            color = Sun.TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
    }

    MetaCard(
        coordinates = displayCoordinates(result.latDeg, result.lonDeg),
        date = result.date,
        timezone = zoneParts(result.zone, result.date),
    )

    Spacer(Modifier.height(12.dp))

    // The polar warning goes ABOVE the cards, not below them, because it is the
    // explanation for what the sunrise and sunset cards are about to say. A note
    // under the fold reads as an afterthought to a reading that already confused
    // the user.
    when (result.kind) {
        DayKind.MIDNIGHT_SUN -> {
            Notice(
                title = MIDNIGHT_SUN_TITLE,
                body = MIDNIGHT_SUN_BODY,
                accent = Sun.Gold,
            )
            Spacer(Modifier.height(12.dp))
        }

        DayKind.POLAR_NIGHT -> {
            Notice(
                title = POLAR_NIGHT_TITLE,
                body = POLAR_NIGHT_BODY,
                accent = Sun.Blue,
            )
            Spacer(Modifier.height(12.dp))
        }

        DayKind.NORMAL -> Unit
    }

    SunCard {
        SunTimesRow(
            sunrise = sunFieldValue(result.sunrise, result.kind, result.zone),
            // Solar noon is the sun's transit rather than a horizon crossing, so
            // it exists on every day at every latitude and is never a polar label.
            solarNoon = SunFieldValue(
                text = formatClock(result.solarNoon, result.zone),
                colour = Sun.TextPrimary,
                monospace = true,
            ),
            sunset = sunFieldValue(result.sunset, result.kind, result.zone),
        )
        Spacer(Modifier.height(18.dp))
        DayLengthBar(
            value = formatDayLength(result.kind, result.dayLengthMinutes),
            fraction = dayLengthFraction(result.kind, result.dayLengthMinutes),
        )
    }
}

/* ------------------------------------------------------------------ *
 * Pure logic: no Compose, no Android, no state.
 *
 * Everything below is a function of its arguments, which is what makes the
 * screen's behaviour reviewable. The polar decision in particular is NOT taken
 * here: it is DayKindCalculator's, which is the transcription of app.js the two
 * surfaces are required to share.
 * ------------------------------------------------------------------ */

/**
 * "HH:mm:ss" in the SELECTED place's zone, never the device's.
 *
 * This is the whole point of carrying a zone with the place. Someone in Oslo
 * looking up Tokyo wants Tokyo's clock, and the instants the solar layer returns
 * are absolute, so the zone is the only thing that decides what they read as.
 *
 * A missing instant prints an en dash (U+2013), as the home screen widgets do (1.1.9).
 */
internal fun formatClock(instant: Instant?, zone: ZoneId): String =
    if (instant == null) "\u2013" else CLOCK_FORMAT.format(instant.atZone(zone))

/**
 * "Friday 21 August 2026", the "Date" row of the results card (R6, D21). The form of the date bar above
 * it (TEXTS.md D-30), without SunApp's comma, with the same no-break space before the year, so a long
 * date at a large font breaks after the day and never leaves the year alone on a line.
 */
internal fun formatFullDate(date: LocalDate): String = displayBarDate(date)

/**
 * "CEST / Europe/Oslo", or just the id when there is no abbreviation to show.
 *
 * The abbreviation is resolved at local noon on the date being displayed rather
 * than now, so a date inside British Summer Time reads BST even when it is looked
 * up in January. Where the platform has no short name it prints an offset such as
 * "GMT+02:00", which is repetitive next to the id but never wrong.
 */
internal fun formatZone(zone: ZoneId, date: LocalDate): String = zoneParts(zone, date).text

/**
 * The Timezone row's value in the two pieces it may break between (R7, D87): the abbreviation with its
 * slash ("CET /", null when there is nothing to show) and the zone [id] ("Europe/Oslo"). The row lays
 * the pieces out as a flow, so the only place a line can break is between them; the id stays whole
 * while it fits and wraps at its own slash only when it is wider than the column.
 */
internal data class ZoneParts(val abbreviation: String?, val id: String) {
    /** "CET / Europe/Oslo": the text a screen reader speaks and the older single string. */
    val text: String get() = if (abbreviation == null) id else "$abbreviation $id"
}

internal fun zoneParts(zone: ZoneId, date: LocalDate): ZoneParts {
    val abbreviation = try {
        ZONE_FORMAT.format(date.atTime(12, 0).atZone(zone))
    } catch (_: DateTimeException) {
        ""
    }
    return if (abbreviation.isEmpty() || abbreviation == zone.id) ZoneParts(null, zone.id) else ZoneParts("$abbreviation /", zone.id)
}

/** "7h 32m", or the polar constants the web app prints. */
internal fun formatDayLength(kind: DayKind, minutes: Long?): String = when {
    minutes != null -> "${minutes / 60}h ${minutes % 60}m"
    kind == DayKind.MIDNIGHT_SUN -> "24h 0m"
    else -> "0h 0m"
}

/** The bar's share of a full twenty-four hours, clamped to [0, 1]. */
internal fun dayLengthFraction(kind: DayKind, minutes: Long?): Float = when {
    minutes != null -> (minutes / 1440.0).coerceIn(0.0, 1.0).toFloat()
    kind == DayKind.MIDNIGHT_SUN -> 1f
    else -> 0f
}

/** Sunrise or sunset: a clock reading on a normal day, a polar label otherwise. */
@Composable
private fun sunFieldValue(instant: Instant?, kind: DayKind, zone: ZoneId): SunFieldValue = when (kind) {
    // The web app prefixes these with an emoji. Dropped here because the card is a
    // third of a phone wide and "🌞 Midnight Sun" wraps to three lines in it, while
    // the card already carries its own icon above the label.
    DayKind.MIDNIGHT_SUN -> SunFieldValue("Midnight Sun", Sun.Gold, monospace = false)
    DayKind.POLAR_NIGHT -> SunFieldValue("Polar Night", Sun.Blue, monospace = false)
    DayKind.NORMAL -> SunFieldValue(formatClock(instant, zone), Sun.TextPrimary, monospace = true)
}

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.UK)
private val ZONE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("zzz", Locale.UK)

/**
 * The colours of the Labs carousel on the Sun tab, SunApp's own mapping of the website's custom
 * properties: `--text-secondary` heading, `--bg-card` cards with a `--border` edge, `--accent-blue`
 * on the app's own card, `--border` above it.
 */
@Composable
private fun sunCarouselColours() = LabsCarouselColors(
    title = Sun.TextSecondary,
    label = Sun.TextPrimary,
    cardFill = Sun.Card,
    cardBorder = Sun.Border,
    accent = Sun.Blue,
    rule = Sun.Border,
    checkmark = Sun.Background,
)

/** The footer's colours on the Sun tab: its own palette in the theme in effect. */
@Composable
private fun sunFooterColours() = FooterColours(
    background = Sun.Background,
    border = Sun.Border,
    textPrimary = Sun.TextPrimary,
    textSecondary = Sun.TextSecondary,
    link = Sun.Blue,
)
