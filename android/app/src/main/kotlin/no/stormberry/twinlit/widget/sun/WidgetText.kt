package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.widget.WidgetPlaceName
import no.stormberry.twinlit.data.PlaceDisplay
import no.stormberry.twinlit.solar.DayKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every word the widgets show or speak (1.1.9). Pure, so
 * WidgetTextTest checks each string. Fixed English, dates and times with `Locale.UK`, times in the
 * place's zone, `HH:mm` cut off (not rounded) from the card's `HH:mm:ss`, so the widget always
 * shows a prefix of what the app shows. A missing time is an en dash (U+2013).
 *
 * The one value not formatted here is the ticking Chronometer's: the launcher formats it, in the
 * phone's locale, which is why [WidgetModel] only lets it tick when the phone prints `1:02:03`.
 */
object WidgetText {

    const val SUNRISE = "Sunrise"
    const val SUNSET = "Sunset"
    const val TOMORROW = " tomorrow"
    const val TOMORROW_AT = " tomorrow at "
    const val ALWAYS_UP = "Always Up"
    const val ALWAYS_DOWN = "Always Down"
    const val DAYLIGHT_LEFT = "Daylight left %s"
    const val SUNRISE_IN = "Sunrise in %s"
    const val SUNSET_AT = "Sunset at "
    const val SUNRISE_AT = "Sunrise at "
    const val NEXT_SUNRISE = "Next sunrise "
    const val NEXT_SUNSET = "Next sunset "
    const val SOLAR_NOON = "Solar noon"
    const val GOLDEN_HOUR = "Golden hour"
    const val UNTIL = "until "
    const val FROM = "from "
    const val ALL_DAY = "All day"
    const val ALL_NIGHT = "All night"
    const val ALL_DAY_AND_NIGHT = "All day and night"
    const val DAWN = "Dawn"
    const val DUSK = "Dusk"
    const val CIVIL = "Civil"
    const val NAUTICAL = "Nautical"
    const val ASTRONOMICAL = "Astronomical"
    const val NEXT_DAY_MARK = " +1"
    const val PREVIOUS_DAY_MARK = " −1"
    const val MISSING = "–"
    const val SEPARATOR = " · "
    const val ZONE_NOTE = "Times shown in %s."
    const val CHOOSE_PLACE = "Choose a place in Twinlit to start the widget."
    const val THE_NEXT_DAY = " the next day"
    const val THE_PREVIOUS_DAY = " the previous day"

    /**
     * The name the widgets give a place typed as
     * coordinates. A widget is on the home screen for anyone near the phone, in screenshots and
     * screen sharing, and its description is read to every accessibility service, so typed
     * coordinates (perhaps a home position) are never printed or spoken there, not even rounded.
     */
    const val YOUR_COORDINATES = PlaceDisplay.YOUR_COORDINATES

    /** Every fixed string above, for the house style test (no em dash, no double hyphen). */
    val ALL_FIXED: List<String> = listOf(
        SUNRISE, SUNSET, TOMORROW, TOMORROW_AT, ALWAYS_UP, ALWAYS_DOWN, DAYLIGHT_LEFT, SUNRISE_IN,
        SUNSET_AT, SUNRISE_AT, NEXT_SUNRISE, NEXT_SUNSET, SOLAR_NOON, GOLDEN_HOUR, UNTIL, FROM,
        ALL_DAY, ALL_NIGHT, ALL_DAY_AND_NIGHT, DAWN, DUSK, CIVIL, NAUTICAL, ASTRONOMICAL,
        NEXT_DAY_MARK, PREVIOUS_DAY_MARK, MISSING, SEPARATOR, ZONE_NOTE, CHOOSE_PLACE,
        THE_NEXT_DAY, THE_PREVIOUS_DAY, YOUR_COORDINATES,
    )

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
    private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)
    private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

    /** The colour role of a text; SunWidgets maps it to the palette. */
    enum class Tint { PRIMARY, SECONDARY, GOLD, BLUE }

    // ------------------------------------------------------------------ pieces

    /**
     * The place's name on every widget, in the large header and in every description. A catalogue
     * place: the city only, the text before the first comma of its label ("Oslo, Norway" is
     * "Oslo"). A place typed as coordinates: the name the user gave it, whole (a comma in it is not
     * the end of a city, and it is not shortened here; the widgets fit it to the room they have),
     * or [YOUR_COORDINATES] when it has none, because its label is the coordinates themselves.
     * Never numbers. See [YOUR_COORDINATES]. Widget only: the app screens and the alarms
     * keep the whole label of a catalogue place through [PlaceDisplay.name].
     *
     * A second, defensive check, kept in [PlaceDisplay.name]: a label equal to the place's own
     * formatted coordinates is never shown, even when [Place.fromCatalogue] says true (a place
     * stored by mistake, or a missing key, which reads as true).
     */
    fun placeName(place: Place): String = WidgetPlaceName.of(place)

    /**
     * [name] up to its first comma, trimmed ("Oslo, Norway" is "Oslo"); see
     * [WidgetPlaceName.cityOnly], which both families share.
     */
    fun cityOnly(name: String): String = WidgetPlaceName.cityOnly(name)

    /** "15:48" in [zone], or the en dash. */
    fun time(t: Instant?, zone: ZoneId): String = t?.let { TIME.format(it.atZone(zone)) } ?: MISSING

    /** "Mon 16 Nov". */
    fun shortDate(d: LocalDate): String = SHORT_DATE.format(d)

    /** "15:48", "00:29 +1" or "23:58 −1": [t] with a marker when its local date is not [ref]. */
    fun marked(t: Instant?, zone: ZoneId, ref: LocalDate): String {
        if (t == null) return MISSING
        val d = t.atZone(zone).toLocalDate()
        return time(t, zone) + when {
            d.isAfter(ref) -> NEXT_DAY_MARK
            d.isBefore(ref) -> PREVIOUS_DAY_MARK
            else -> ""
        }
    }

    private fun spokenMarked(t: Instant, zone: ZoneId, ref: LocalDate): String {
        val d = t.atZone(zone).toLocalDate()
        return time(t, zone) + when {
            d.isAfter(ref) -> THE_NEXT_DAY
            d.isBefore(ref) -> THE_PREVIOUS_DAY
            else -> ""
        }
    }

    private fun event(up: Boolean): String = if (up) SUNSET else SUNRISE

    private fun lineTint(up: Boolean): Tint = if (up) Tint.GOLD else Tint.BLUE

    // ------------------------------------------------------------------ small 2x1

    /** The small widget's two lines. */
    data class Small(val label: String, val labelTint: Tint, val value: String)

    fun small(s: WidgetState): Small {
        val ev = event(s.up)
        val t = s.target
        return when (s.kind) {
            DayKind.MIDNIGHT_SUN -> Small(ALWAYS_UP, Tint.GOLD, "$ev ${t?.let { DAY_MONTH.format(it.atZone(s.zone)) } ?: MISSING}")
            DayKind.POLAR_NIGHT -> Small(ALWAYS_DOWN, Tint.BLUE, "$ev ${t?.let { DAY_MONTH.format(it.atZone(s.zone)) } ?: MISSING}")
            DayKind.NORMAL -> {
                if (t == null) return Small(ev, Tint.SECONDARY, MISSING)
                val d = t.atZone(s.zone).toLocalDate()
                when (d) {
                    s.today -> Small(ev, Tint.SECONDARY, time(t, s.zone))
                    s.today.plusDays(1) -> Small(ev + TOMORROW, Tint.SECONDARY, time(t, s.zone))
                    else -> Small(ev, Tint.SECONDARY, DAY_MONTH.format(d))
                }
            }
        }
    }

    // ------------------------------------------------------------------ the times line

    /** The medium widget's first line, also the large widget's times row. */
    sealed interface TimesLine {
        /** "Sunrise 08:17", "Sunset 15:48": the card's times, cut to minutes. */
        data class Normal(val sunrise: String, val sunset: String) : TimesLine

        /** "Always Up" in gold or "Always Down" in blue, the card's polar colours. */
        data class Polar(val label: String, val tint: Tint) : TimesLine
    }

    fun timesLine(s: WidgetState): TimesLine = when (s.kind) {
        DayKind.MIDNIGHT_SUN -> TimesLine.Polar(ALWAYS_UP, Tint.GOLD)
        DayKind.POLAR_NIGHT -> TimesLine.Polar(ALWAYS_DOWN, Tint.BLUE)
        DayKind.NORMAL -> TimesLine.Normal(time(s.sunrise, s.zone), time(s.sunset, s.zone))
    }

    // ------------------------------------------------------------------ the countdown line

    /** The countdown line as the renderer needs it. */
    sealed interface LineText {
        data object Hidden : LineText

        /** A text that names a fixed instant or a date. */
        data class Static(val text: String, val tint: Tint) : LineText

        /** The Chronometer: [format] holds one `%s` for the launcher's `H:MM:SS`. */
        data class Ticking(val format: String, val tint: Tint, val target: Instant) : LineText
    }

    /**
     * The countdown line; [withTime] adds the time to the date form, as the large widget shows it
     * ("Next sunrise Tue 16 Feb, 11:39").
     */
    fun line(s: WidgetState, withTime: Boolean): LineText = when (val l = s.line) {
        CountdownLine.Hidden -> LineText.Hidden
        is CountdownLine.DateForm -> {
            val date = SHORT_DATE.format(l.target.atZone(s.zone))
            val text = (if (l.up) NEXT_SUNSET else NEXT_SUNRISE) + date + if (withTime) ", " + time(l.target, s.zone) else ""
            LineText.Static(text, lineTint(l.up))
        }
        is CountdownLine.Ticking -> LineText.Ticking(if (l.up) DAYLIGHT_LEFT else SUNRISE_IN, lineTint(l.up), l.target)
        is CountdownLine.AtToday -> LineText.Static((if (l.up) SUNSET_AT else SUNRISE_AT) + time(l.target, s.zone), lineTint(l.up))
        is CountdownLine.AtTomorrow -> LineText.Static(event(l.up) + TOMORROW_AT + time(l.target, s.zone), lineTint(l.up))
    }

    /**
     * The widest text the ticking line can show, for text fitting: the format with the widest
     * value under 24 hours. Digits are tabular, so every digit is as wide as any other.
     */
    fun widestTicking(format: String): String = format.replace("%s", "00:00:00")

    // ------------------------------------------------------------------ large rows

    /** The large widget's header: the place, and the date with the zone when it differs. */
    fun headerDate(s: WidgetState): String =
        shortDate(s.today) + if (s.zoneDiffers) SEPARATOR + s.zone.id else ""

    /** One twilight or golden hour row's cells: two columns, or one text across both. */
    sealed interface BandText {
        data class Cells(val first: String, val second: String) : BandText
        data class Span(val text: String) : BandText
    }

    fun golden(s: WidgetState): BandText = when (val b = s.golden) {
        is Band.Times -> BandText.Cells(
            b.morning?.let { UNTIL + marked(it, s.zone, s.noonDate) } ?: MISSING,
            b.evening?.let { FROM + marked(it, s.zone, s.noonDate) } ?: MISSING,
        )
        Band.AllDay -> BandText.Span(ALL_DAY)
        Band.AllDayAndNight -> BandText.Span(ALL_DAY_AND_NIGHT)
        Band.AllNight -> BandText.Span(ALL_NIGHT)
        Band.None -> BandText.Cells(MISSING, MISSING)
    }

    fun twilight(b: Band, s: WidgetState): BandText = when (b) {
        is Band.Times -> BandText.Cells(marked(b.morning, s.zone, s.noonDate), marked(b.evening, s.zone, s.noonDate))
        Band.AllNight -> BandText.Span(ALL_NIGHT)
        Band.AllDayAndNight -> BandText.Span(ALL_DAY_AND_NIGHT)
        Band.AllDay -> BandText.Span(ALL_DAY)
        Band.None -> BandText.Cells(MISSING, MISSING)
    }

    // ------------------------------------------------------------------ spoken descriptions

    /** "Oslo: sunset at 15:48." Never contains the ticking value. */
    fun describeSmall(s: WidgetState): String = when (s.kind) {
        DayKind.NORMAL -> {
            val t = s.target
            if (t == null) "${s.placeLabel}." else "${s.placeLabel}: ${decapitalise(eventSentence(s.up, t, s))}"
        }
        else -> describePolar(s)
    }

    /** "Oslo: sunrise 08:17, sunset 15:48. Daylight until sunset at 15:48." */
    fun describeMedium(s: WidgetState): String {
        if (s.kind != DayKind.NORMAL) return describePolar(s)
        val head = "${s.placeLabel}: sunrise ${time(s.sunrise, s.zone)}, sunset ${time(s.sunset, s.zone)}."
        val t = s.target ?: return head
        val tail = if (s.up) "Daylight until sunset" + whenSuffix(t, s, on = true) else "Next sunrise" + whenSuffix(t, s, on = false)
        return "$head $tail"
    }

    /** The medium description, then solar noon, golden hour, twilight and the zone note. */
    fun describeLarge(s: WidgetState): String {
        val parts = mutableListOf(describeMedium(s), "Solar noon ${time(s.solarNoon, s.zone)}.")
        describeGolden(s)?.let { parts += it }
        for ((name, band) in listOf("Civil" to s.civil, "Nautical" to s.nautical, "Astronomical" to s.astronomical)) {
            describeTwilight(name, band, s)?.let { parts += it }
        }
        if (s.zoneDiffers) parts += ZONE_NOTE.format(Locale.UK, s.zone.id)
        return parts.joinToString(" ")
    }

    private fun describePolar(s: WidgetState): String {
        val first = if (s.kind == DayKind.MIDNIGHT_SUN) {
            "the sun stays above the horizon all day."
        } else {
            "the sun stays below the horizon all day."
        }
        val t = s.target ?: return "${s.placeLabel}: $first"
        val next = (if (s.up) "Next sunset" else "Next sunrise") + whenSuffix(t, s, on = false)
        return "${s.placeLabel}: $first $next"
    }

    /** "Sunset at 15:48.", "Sunrise tomorrow at 08:19." or "Next sunrise Tuesday 16 February at 11:39." */
    private fun eventSentence(up: Boolean, t: Instant, s: WidgetState): String {
        val d = t.atZone(s.zone).toLocalDate()
        val at = time(t, s.zone)
        return when (d) {
            s.today -> "${event(up)} at $at."
            s.today.plusDays(1) -> "${event(up)} tomorrow at $at."
            else -> "Next ${event(up).lowercase(Locale.UK)} ${LONG_DATE.format(d)} at $at."
        }
    }

    /** " at 15:48.", " tomorrow at 00:28." or " Wednesday 26 August at 00:28." ([on]: " on Wednesday ..."). */
    private fun whenSuffix(t: Instant, s: WidgetState, on: Boolean): String {
        val d = t.atZone(s.zone).toLocalDate()
        val at = time(t, s.zone)
        return when (d) {
            s.today -> " at $at."
            s.today.plusDays(1) -> " tomorrow at $at."
            else -> (if (on) " on " else " ") + LONG_DATE.format(d) + " at $at."
        }
    }

    private fun describeGolden(s: WidgetState): String? = when (val b = s.golden) {
        is Band.Times -> {
            val until = b.morning?.let { "until " + spokenMarked(it, s.zone, s.noonDate) }
            val from = b.evening?.let { "from " + spokenMarked(it, s.zone, s.noonDate) }
            "Golden hour " + listOfNotNull(until, from).joinToString(" and ") + "."
        }
        Band.AllDay -> "Golden hour all day."
        Band.AllDayAndNight -> "Golden hour all day and night."
        Band.AllNight, Band.None -> null
    }

    /**
     * "Civil twilight 07:28 to 16:37." For the "never darker than" states the sentence says
     * exactly that (at Longyearbyen on 21 June the sky is in daylight all night, so "civil
     * twilight all night" would be false).
     */
    private fun describeTwilight(name: String, b: Band, s: WidgetState): String? {
        val lower = name.lowercase(Locale.UK)
        return when (b) {
            is Band.Times -> {
                val a = b.morning?.let { spokenMarked(it, s.zone, s.noonDate) } ?: MISSING
                val z = b.evening?.let { spokenMarked(it, s.zone, s.noonDate) } ?: MISSING
                "$name twilight $a to $z."
            }
            Band.AllNight -> "Never darker than $lower twilight all night."
            Band.AllDayAndNight -> "Never darker than $lower twilight all day and night."
            Band.AllDay, Band.None -> null
        }
    }

    private fun decapitalise(text: String): String = text.replaceFirstChar { it.lowercase(Locale.UK) }
}
