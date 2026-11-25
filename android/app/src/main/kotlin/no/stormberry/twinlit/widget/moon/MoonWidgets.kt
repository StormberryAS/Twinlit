package no.stormberry.twinlit.widget.moon

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import no.stormberry.twinlit.R
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.lunar.LunarDayKind
import no.stormberry.twinlit.widget.ShowToday
import java.time.Instant
import java.time.ZoneId

/**
 * The Moon widgets' renderer (1.0.8): renders the three sizes from one [WidgetState] and pushes
 * them to the launcher. What MoonApp did in the same object, the entry points, the lock, the
 * refresh alarm and the event receiver switch, is shared with the sun family and lives in
 * `widget/WidgetRefresh.kt` (SPEC 8.3): one call redraws both families and arms one refresh alarm.
 *
 * ### No permission, in either edition
 *
 * Plain RemoteViews and a Canvas-drawn bitmap, no Glance and no WorkManager. The refresh is ONE
 * inexact, non-wakeup alarm (`setWindow` with `RTC`, see [WidgetSchedule]), which needs no
 * permission, plus the system's own widget updates (after a reboot and after an app update) and
 * three system broadcasts that manifest receivers still get (`WidgetEventReceiver`). Nothing here
 * uses an exact alarm, a foreground service, a notification, a wake lock or a receiver registered
 * at run time. Both editions run the same code.
 *
 * ### Never throws into a receiver
 *
 * [render] runs on a short-lived thread inside a catch (`WidgetRefresh`; the 1.0.4 rule: a
 * receiver must never throw). When it fails, the launcher keeps the previous widget on screen.
 */
object MoonWidgets {

    /** Moon bitmap sizes, in dp. */
    private const val MOON_SMALL_DP = 56
    private const val MOON_MEDIUM_DP = 40
    private const val MOON_LARGE_DP = 72

    // The widget palette: the same values as res/values/colors.xml (the layouts read them there, the
    // spans below here), lightened for the 70 per cent panel (2.0.0). WidgetColoursTest fails if
    // these and the resource values differ, or if one drops below 4.6 to 1 on the panel.
    private const val TEXT_PRIMARY = 0xFFE8EEFF.toInt()
    private const val SILVER = 0xFFC8D8F0.toInt()
    private const val BLUE = 0xFFB4C8E7.toInt()
    private const val VIOLET = 0xFFCEC1EA.toInt()
    private const val TEAL = 0xFF79D2F9.toInt()
    private const val NEW_MOON = 0xFFC1C6DB.toInt()
    private const val SOFT = 0xFFBBC7E2.toInt()

    // ------------------------------------------------------------------ entry points

    /** The providers of the family, for `WidgetRefresh` and the widget tests. */
    val PROVIDERS = listOf(
        MoonWidgetSmall::class.java,
        MoonWidgetMedium::class.java,
        MoonWidgetLarge::class.java,
    )

    /** Whether any widget of any size is placed. */
    fun hasAny(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context) ?: return false
        return PROVIDERS.any { (mgr.getAppWidgetIds(ComponentName(context, it)) ?: IntArray(0)).isNotEmpty() }
    }

    /**
     * Redraws every placed Moon widget of every size for [place] (null: none chosen yet, the
     * widgets show their hint) and returns when the next refresh is due
     * ([WidgetSchedule.nextRefresh], in the place's zone, or the phone's with no place). Under the
     * lock of `WidgetRefresh` only; reads the clock, so never on the main thread.
     */
    fun render(context: Context, mgr: AppWidgetManager, place: Place?): Instant {
        val ids = PROVIDERS.associateWith { mgr.getAppWidgetIds(ComponentName(context, it)) ?: IntArray(0) }
        val now = Instant.now()
        val phoneZone = ZoneId.systemDefault()
        val density = context.resources.displayMetrics.density
        val small = ids.getValue(MoonWidgetSmall::class.java)
        val medium = ids.getValue(MoonWidgetMedium::class.java)
        val large = ids.getValue(MoonWidgetLarge::class.java)

        if (place == null) {
            // No place chosen yet (SPEC 8.3): every widget says so; the tap opens the Moon tab.
            if (small.isNotEmpty()) mgr.updateAppWidget(small, noPlace(context, R.layout.widget_moon_small))
            if (medium.isNotEmpty()) mgr.updateAppWidget(medium, noPlace(context, R.layout.widget_moon_medium))
            // The tall layout carries the hint; one view for every size is enough for a hint.
            if (large.isNotEmpty()) mgr.updateAppWidget(large, noPlace(context, R.layout.widget_moon_large_tall))
            return WidgetSchedule.nextRefresh(now, phoneZone)
        }

        val state = WidgetModel.build(now, place, phoneZone)
        if (small.isNotEmpty()) mgr.updateAppWidget(small, renderSmall(context, state, moon(state, MOON_SMALL_DP, density)))
        if (medium.isNotEmpty()) mgr.updateAppWidget(medium, renderMedium(context, state, moon(state, MOON_MEDIUM_DP, density)))
        if (large.isNotEmpty()) {
            val bmp = moon(state, MOON_LARGE_DP, density)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mgr.updateAppWidget(large, renderLargeSized(context, state, bmp))
            } else {
                val fontScale = fontScale(context)
                for (id in large) mgr.updateAppWidget(id, renderLarge(context, state, bmp, pick(mgr.getAppWidgetOptions(id), fontScale)))
            }
        }
        return WidgetSchedule.nextRefresh(now, state.zone)
    }

    // ------------------------------------------------------------------ rendering

    private fun moon(s: WidgetState, sizeDp: Int, density: Float): Bitmap =
        MoonDiscBitmap.draw(sizeDp, density, s.fraction, s.litOnRight)

    /** The whole widget is one tap target with one spoken description. */
    private fun RemoteViews.finish(context: Context, description: String): RemoteViews {
        setOnClickPendingIntent(android.R.id.background, ShowToday.moon.pendingIntent(context))
        setContentDescription(android.R.id.background, description)
        return this
    }

    /**
     * The widget has data: show its content and hide the hint that the first layout carries
     * ("Open Twinlit once to start the widget.", for a widget placed before the app was ever
     * opened). Only the three layouts a provider starts from have the hint.
     */
    private fun RemoteViews.started(): RemoteViews {
        setViewVisibility(R.id.content, View.VISIBLE)
        setViewVisibility(R.id.hint, View.GONE)
        return this
    }

    /**
     * No place chosen yet: the content stays hidden and the hint says so (TEXTS.md J-14); the tap
     * opens Twinlit on the Moon tab, where the place is chosen.
     */
    private fun noPlace(context: Context, layout: Int): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setViewVisibility(R.id.content, View.INVISIBLE)
            setViewVisibility(R.id.hint, View.VISIBLE)
            setTextViewText(R.id.hint, WidgetText.CHOOSE_PLACE)
        }.finish(context, WidgetText.CHOOSE_PLACE)

    internal fun renderSmall(context: Context, s: WidgetState, moon: Bitmap): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_moon_small).started().apply {
            setImageViewBitmap(R.id.moon, moon)
            setTextViewText(R.id.percent, WidgetText.percent(s.percent))
        }.finish(context, WidgetText.describeSmall(s))

    private fun coloured(text: String, color: Int): CharSequence =
        SpannableStringBuilder().append(text, ForegroundColorSpan(color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    /**
     * "Rise 18:42": the label in the event's accent, as on the card, the time in the text colour.
     * Also a compact phase row, "Full moon 24 Nov", with the label in the soft text colour.
     */
    private fun row(label: String, accent: Int, value: String): CharSequence =
        SpannableStringBuilder()
            .append(label, ForegroundColorSpan(accent), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            .append(" ")
            .append(value, ForegroundColorSpan(TEXT_PRIMARY), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    internal fun renderMedium(context: Context, s: WidgetState, moon: Bitmap): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_moon_medium).started().apply {
            setImageViewBitmap(R.id.moon, moon)
            setTextViewText(R.id.percent, WidgetText.percent(s.percent))
            // One text view, one line per row, so the rows share one autosized text size.
            val lines = WidgetText.timeLines(s)
            val text = SpannableStringBuilder()
            lines.forEachIndexed { i, line ->
                if (i > 0) text.append("\n")
                text.append(line(line))
            }
            setTextViewText(R.id.rows, text)
            // Exactly as many lines as rows, so autosize shrinks a long row instead of wrapping it.
            setInt(R.id.rows, "setMaxLines", lines.size)
        }.finish(context, WidgetText.describeMedium(s))

    /** Android 7 to 11: the arrangement for one widget's reported size. */
    private fun pick(options: Bundle?, fontScale: Float): LargeLayout.Arrangement = LargeLayout.pick(
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0,
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0,
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0,
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0,
        fontScale,
    )

    /** The phone's font size, which the large widget's arrangement depends on (LargeLayout). */
    private fun fontScale(context: Context): Float = context.resources.configuration.fontScale

    /** Android 12 and later: one RemoteViews per ideal size; the launcher picks the one that fits. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun renderLargeSized(context: Context, s: WidgetState, moon: Bitmap): RemoteViews {
        val bySize = LinkedHashMap<SizeF, RemoteViews>()
        for ((w, h, arrangement) in LargeLayout.sizes(fontScale(context))) bySize[SizeF(w, h)] = renderLarge(context, s, moon, arrangement)
        return RemoteViews(bySize)
    }

    internal fun renderLarge(context: Context, s: WidgetState, moon: Bitmap, arrangement: LargeLayout.Arrangement): RemoteViews =
        if (arrangement == LargeLayout.Arrangement.COMPACT) renderCompact(context, s, moon) else renderFull(context, s, moon, arrangement)

    /** One time line, coloured: the label in the event's accent, the time in the text colour. */
    private fun line(l: WidgetText.TimeLine): CharSequence = when (l.tint) {
        WidgetText.Tint.RISE -> row(l.label, BLUE, l.value.orEmpty())
        WidgetText.Tint.TRANSIT -> row(l.label, VIOLET, l.value.orEmpty())
        WidgetText.Tint.SET -> row(l.label, TEAL, l.value.orEmpty())
        WidgetText.Tint.ALWAYS_UP -> coloured(l.label, SILVER)
        WidgetText.Tint.ALWAYS_DOWN -> coloured(l.label, NEW_MOON)
    }

    /**
     * The time lines (or the polar label) into [row1], [row2] and [row3], one per view, for the
     * large widget's compact arrangement; a row with no line is hidden.
     */
    private fun RemoteViews.timeRows(s: WidgetState, row1: Int, row2: Int, row3: Int) {
        val lines = WidgetText.timeLines(s)
        listOf(row1, row2, row3).forEachIndexed { i, id ->
            val l = lines.getOrNull(i)
            if (l == null) {
                setViewVisibility(id, View.GONE)
            } else {
                setTextViewText(id, line(l))
                setViewVisibility(id, View.VISIBLE)
            }
        }
    }

    /** The compact arrangement (LargeLayout.COMPACT): the 2x1's rows, then one row per phase. */
    private fun renderCompact(context: Context, s: WidgetState, moon: Bitmap): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_moon_large_compact).apply {
            setImageViewBitmap(R.id.moon, moon)
            setTextViewText(R.id.percent, WidgetText.percent(s.percent))
            timeRows(s, R.id.row1, R.id.row2, R.id.row3)
            val rows = listOf(R.id.compact_phase1, R.id.compact_phase2, R.id.compact_phase3, R.id.compact_phase4)
            rows.forEachIndexed { i, id ->
                val e = s.phases.getOrNull(i)
                if (e == null) {
                    setViewVisibility(id, View.INVISIBLE)
                } else {
                    setViewVisibility(id, View.VISIBLE)
                    setTextViewText(id, row(WidgetText.phaseLabel(e.phase), SOFT, WidgetText.compactPhaseDate(e.date, s.today)))
                }
            }
        }.finish(context, WidgetText.describeLarge(s))

    private fun renderFull(context: Context, s: WidgetState, moon: Bitmap, arrangement: LargeLayout.Arrangement): RemoteViews {
        val tall = arrangement == LargeLayout.Arrangement.TALL
        val views = RemoteViews(context.packageName, if (tall) R.layout.widget_moon_large_tall else R.layout.widget_moon_large_wide)
        // The tall layout is the large widget's first layout, so it carries the hint.
        if (tall) views.started()
        return views.apply {
            setTextViewText(R.id.place, s.placeLabel)
            setTextViewText(R.id.date, if (tall) WidgetText.shortDate(s.today) else WidgetText.wideHeaderDate(s))
            setImageViewBitmap(R.id.moon, moon)
            setTextViewText(R.id.percent, WidgetText.percent(s.percent))

            setTextViewText(R.id.rise_label, WidgetText.MOONRISE)
            setTextViewText(R.id.transit_label, WidgetText.TRANSIT)
            setTextViewText(R.id.set_label, WidgetText.MOONSET)
            setTextViewText(R.id.rise_time, WidgetText.time(s.rise, s.zone))
            setTextViewText(R.id.transit_time, WidgetText.time(s.transit, s.zone))
            setTextViewText(R.id.set_time, WidgetText.time(s.set, s.zone))
            when (s.dayKind) {
                LunarDayKind.NORMAL -> {
                    setViewVisibility(R.id.polar, View.GONE)
                    for (r in listOf(R.id.row_rise, R.id.row_transit, R.id.row_set)) setViewVisibility(r, View.VISIBLE)
                }
                LunarDayKind.ALWAYS_UP -> {
                    setTextViewText(R.id.polar, WidgetText.ALWAYS_UP_LONG)
                    setTextColor(R.id.polar, SILVER)
                    setViewVisibility(R.id.polar, View.VISIBLE)
                    setViewVisibility(R.id.row_rise, View.GONE)
                    setViewVisibility(R.id.row_transit, View.VISIBLE)
                    setViewVisibility(R.id.row_set, View.GONE)
                }
                LunarDayKind.ALWAYS_DOWN -> {
                    setTextViewText(R.id.polar, WidgetText.ALWAYS_DOWN_LONG)
                    setTextColor(R.id.polar, NEW_MOON)
                    setViewVisibility(R.id.polar, View.VISIBLE)
                    for (r in listOf(R.id.row_rise, R.id.row_transit, R.id.row_set)) setViewVisibility(r, View.GONE)
                }
            }

            val cells = listOf(
                Triple(R.id.phase1, R.id.phase1_label, R.id.phase1_date),
                Triple(R.id.phase2, R.id.phase2_label, R.id.phase2_date),
                Triple(R.id.phase3, R.id.phase3_label, R.id.phase3_date),
                Triple(R.id.phase4, R.id.phase4_label, R.id.phase4_date),
            )
            cells.forEachIndexed { i, (cell, label, date) ->
                val e = s.phases.getOrNull(i)
                if (e == null) {
                    setViewVisibility(cell, View.INVISIBLE)
                } else {
                    setViewVisibility(cell, View.VISIBLE)
                    setTextViewText(label, WidgetText.phaseLabel(e.phase))
                    setTextViewText(date, WidgetText.phaseDate(e.date, s.today))
                }
            }

            if (tall) {
                if (WidgetText.showsZoneNote(s)) {
                    setTextViewText(R.id.note, WidgetText.zoneNote(s.zone))
                    setViewVisibility(R.id.note, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.note, View.GONE)
                }
            }
        }.finish(context, WidgetText.describeLarge(s))
    }
}
