package no.stormberry.twinlit.widget.sun

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import no.stormberry.twinlit.R
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.widget.ShowToday
import java.time.Instant
import java.time.ZoneId

/**
 * The Sun widgets' renderer (1.1.9): builds one [WidgetState], renders the three sizes and pushes
 * them to the launcher. What SunApp did in the same object, the entry points, the lock, the two
 * alarms and the event receiver switch, is shared with the moon family and lives in
 * `widget/WidgetRefresh.kt` (SPEC 8.3): one call redraws both families and arms one refresh alarm.
 *
 * ### No permission, in either edition
 *
 * Plain RemoteViews, no Glance and no WorkManager. Freshness comes from a Chronometer that counts
 * down inside the launcher, and from two inexact alarms (`setWindow`, see [WidgetSchedule]): the
 * refresh (`RTC`, never wakes the phone) and the handover to the static line before sunrise or
 * sunset (`RTC_WAKEUP`, at most two a day, only while the line ticks). Neither is an exact alarm or
 * an alarm clock, so neither needs a permission, and the wake lock of a wakeup broadcast is held by
 * the system, not by the app. The rest comes from the system's own widget updates (after a reboot
 * and an app update) and four system broadcasts (`WidgetEventReceiver`). On Android 9 to 11 (API 28
 * to 30) the line never ticks and the handover alarm is never armed ([CountdownMath.neverTicks]).
 *
 * ### Kept out of the alarm feature
 *
 * Nothing here imports the alarm package (WidgetIsolationTest), so the widgets share no code with
 * the alarm runtime and need no permission of their own, in either edition.
 *
 * ### Never throws into a receiver
 *
 * [render] runs on a short-lived thread inside a catch (`WidgetRefresh`). When it fails, the
 * launcher keeps the previous widget on screen.
 */
object SunWidgets {

    /** The provider minimums (dp), used for text fitting until the launcher reports a size. */
    private val SMALL_MIN = SizeDp(110f, 40f)
    private val MEDIUM_MIN = SizeDp(180f, 40f)


    // The widget palette: colors.xml holds the same values (WidgetColorsTest checks they agree and the 4.5 to 1 contrast rule).
    private const val TEXT_PRIMARY = 0xFFF0F4FF.toInt()
    private const val TEXT_SECONDARY = 0xFFC2CBE3.toInt()
    private const val GOLD = 0xFFFFD700.toInt()
    private const val BLUE = 0xFF84D5F9.toInt()

    private data class SizeDp(val w: Float, val h: Float)

    // ------------------------------------------------------------------ entry points

    /** The providers of the family, for `WidgetRefresh` and the widget tests. */
    val PROVIDERS = listOf(SunWidgetSmall::class.java, SunWidgetMedium::class.java, SunWidgetLarge::class.java)

    /** Whether any widget of any size is placed. */
    fun hasAny(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context) ?: return false
        return PROVIDERS.any { (mgr.getAppWidgetIds(ComponentName(context, it)) ?: IntArray(0)).isNotEmpty() }
    }

    /**
     * Redraws every placed Sun widget of every size for [place] (null: none chosen yet, the
     * widgets show their hint) and returns the alarms to arm next ([WidgetSchedule.Plan]).
     * Reads the clock and the phone's state, so never on the main thread, and only under the
     * lock of `WidgetRefresh`.
     */
    fun render(context: Context, mgr: AppWidgetManager, place: Place?): WidgetSchedule.Plan {
        val ids = PROVIDERS.associateWith { mgr.getAppWidgetIds(ComponentName(context, it)) ?: IntArray(0) }

        // The two clocks, read once and next to each other (CountdownMath).
        val wall = System.currentTimeMillis()
        val clocks = ClockPair(SystemClock.elapsedRealtime(), wall)
        val now = Instant.ofEpochMilli(wall)
        val phoneZone = ZoneId.systemDefault()
        val state = WidgetModel.build(now, place, phoneZone, tickingAllowed(context))
        val fit = Fitter(context)

        for (id in ids.getValue(SunWidgetSmall::class.java)) {
            val size = sizeOf(mgr.getAppWidgetOptions(id), SMALL_MIN)
            mgr.updateAppWidget(id, renderSmall(context, state, size.w, size.h, fit))
        }
        for (id in ids.getValue(SunWidgetMedium::class.java)) {
            val size = sizeOf(mgr.getAppWidgetOptions(id), MEDIUM_MIN)
            mgr.updateAppWidget(id, renderMedium(context, state, size.w, size.h, fit, clocks))
        }
        val large = ids.getValue(SunWidgetLarge::class.java)
        if (large.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && state != null) {
                // Per widget: each carries an entry at its own reported size (LargeLayout.entries).
                for (id in large) mgr.updateAppWidget(id, renderLargeSized(context, state, fit, clocks, reportedSizes(mgr.getAppWidgetOptions(id))))
            } else {
                for (id in large) {
                    val options = mgr.getAppWidgetOptions(id)
                    val arrangement = pick(options, fit.fontScale)
                    val min = minimumOf(arrangement)
                    val size = sizeOf(options, min)
                    mgr.updateAppWidget(id, renderLarge(context, state, arrangement, size.w, size.h, fit, clocks))
                }
            }
        }

        return if (state == null) {
            WidgetSchedule.plan(now, phoneZone, null, ticking = false)
        } else {
            WidgetSchedule.plan(now, state.zone, state.target, ticking = state.line is CountdownLine.Ticking)
        }
    }

    /**
     * Whether the line may tick, from the phone's state now: see [CountdownMath.tickingAllowed]
     * (never on Android 9 to 11, not while background restricted, and only when the phone prints
     * the probe as `1:02:03`).
     */
    private fun tickingAllowed(context: Context): Boolean {
        val sdk = Build.VERSION.SDK_INT
        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        } else {
            false
        }
        val digits = CountdownMath.digitsOk(DateUtils.formatElapsedTime(CountdownMath.DIGIT_PROBE_SECONDS))
        return CountdownMath.tickingAllowed(sdk, restricted, digits)
    }

    // ------------------------------------------------------------------ sizes and fitting

    /**
     * The size to fit the text to: the smaller of the portrait and landscape sizes the launcher
     * reports, so the text fits both; [fallback] (the provider minimum) when none is reported.
     */
    private fun sizeOf(options: Bundle?, fallback: SizeDp): SizeDp {
        fun smallest(a: String, b: String): Int? =
            listOf(options?.getInt(a) ?: 0, options?.getInt(b) ?: 0).filter { it > 0 }.minOrNull()
        val w = smallest(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = smallest(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return SizeDp(w?.toFloat() ?: fallback.w, h?.toFloat() ?: fallback.h)
    }

    private fun minimumOf(a: LargeLayout.Arrangement): SizeDp = when (a) {
        LargeLayout.Arrangement.TALL -> SizeDp(LargeLayout.TALL_MIN_WIDTH_DP.toFloat(), LargeLayout.TALL_MIN_HEIGHT_DP.toFloat())
        LargeLayout.Arrangement.WIDE -> SizeDp(LargeLayout.WIDE_MIN_WIDTH_DP.toFloat(), LargeLayout.WIDE_MIN_HEIGHT_DP.toFloat())
        LargeLayout.Arrangement.COMPACT -> SizeDp(LargeLayout.COMPACT_MIN_WIDTH_DP.toFloat(), LargeLayout.COMPACT_MIN_HEIGHT_DP.toFloat())
    }

    /**
     * Android 12 and later: the sizes in dp the launcher reports for one widget, one for each
     * orientation (OPTION_APPWIDGET_SIZES), or none before it reports any.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun reportedSizes(options: Bundle?): List<Pair<Float, Float>> =
        options?.let { BundleCompat.getParcelableArrayList(it, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java) }
            ?.map { it.width to it.height }
            .orEmpty()

    /** Android 7 to 11: the arrangement for one widget's reported size. */
    private fun pick(options: Bundle?, fontScale: Float): LargeLayout.Arrangement = LargeLayout.pick(
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0,
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0,
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0,
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0,
        fontScale,
    )

    /**
     * Measures text as the layouts draw it: Inter Medium on Android 8 and later, the system sans
     * on 7 and 7.1, tabular digits. Used under the lock of `WidgetRefresh` only (a Paint is not thread safe).
     */
    internal class Fitter(context: Context) {
        val fontScale: Float = context.resources.configuration.fontScale
        private val density: Float = context.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context)
            fontFeatureSettings = "tnum"
            isSubpixelText = true
        }
        val measure = TextFit.Measure { text, sizeDp ->
            paint.textSize = sizeDp * density
            paint.measureText(text) / density
        }
        val lineRatio: Float = run {
            paint.textSize = 100f
            val fm = paint.fontMetrics
            (fm.descent - fm.ascent) / 100f
        }

        private fun font(context: Context): Typeface =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.resources.getFont(R.font.inter_medium)
                } catch (_: Resources.NotFoundException) {
                    Typeface.DEFAULT
                }
            } else {
                Typeface.DEFAULT
            }
    }

    private fun RemoteViews.size(id: Int, sizeDp: Float) = setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, sizeDp)

    // ------------------------------------------------------------------ rendering

    private fun color(t: WidgetText.Tint): Int = when (t) {
        WidgetText.Tint.PRIMARY -> TEXT_PRIMARY
        WidgetText.Tint.SECONDARY -> TEXT_SECONDARY
        WidgetText.Tint.GOLD -> GOLD
        WidgetText.Tint.BLUE -> BLUE
    }

    /** "Sunrise 08:17": the label in the secondary colour, the time in the primary one. */
    private fun labelled(label: String, value: String): CharSequence = SpannableStringBuilder()
        .append(label, ForegroundColorSpan(TEXT_SECONDARY), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        .append(" ")
        .append(value, ForegroundColorSpan(TEXT_PRIMARY), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    /** The whole widget is one tap target with one spoken description. */
    private fun RemoteViews.finish(context: Context, description: String): RemoteViews {
        setOnClickPendingIntent(android.R.id.background, ShowToday.sun.pendingIntent(context))
        setContentDescription(android.R.id.background, description)
        return this
    }

    /** The widget has data: show the content, hide the hint every layout starts with. */
    private fun RemoteViews.started(): RemoteViews {
        setViewVisibility(R.id.content, View.VISIBLE)
        setViewVisibility(R.id.hint, View.GONE)
        return this
    }

    /** No place chosen yet: the hint view says so; the tap opens the app to choose one. */
    private fun RemoteViews.noPlace(context: Context): RemoteViews {
        setViewVisibility(R.id.content, View.INVISIBLE)
        setViewVisibility(R.id.hint, View.VISIBLE)
        setTextViewText(R.id.hint, WidgetText.CHOOSE_PLACE)
        return finish(context, WidgetText.CHOOSE_PLACE)
    }

    internal fun renderSmall(context: Context, s: WidgetState?, w: Float, h: Float, fit: Fitter): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_sun_small)
        if (s == null) return v.noPlace(context)
        v.started()
        val t = WidgetText.small(s)
        v.setTextViewText(R.id.small_label, t.label)
        v.setTextColor(R.id.small_label, color(t.labelTint))
        v.setTextViewText(R.id.small_value, t.value)
        val r = WidgetFit.small(w, h, t.label, t.value, fit.fontScale, fit.measure, fit.lineRatio)
        v.size(R.id.small_label, r.sizes[0])
        v.size(R.id.small_value, r.sizes[1])
        return v.finish(context, WidgetText.describeSmall(s))
    }

    /** The times line into times_rise and times_set, or the polar label into times_polar. */
    private fun RemoteViews.timesLine(s: WidgetState): Pair<Pair<String, String>?, String?> =
        when (val l = WidgetText.timesLine(s)) {
            is WidgetText.TimesLine.Normal -> {
                setTextViewText(R.id.times_rise, labelled(WidgetText.SUNRISE, l.sunrise))
                setTextViewText(R.id.times_set, labelled(WidgetText.SUNSET, l.sunset))
                setViewVisibility(R.id.times_rise, View.VISIBLE)
                setViewVisibility(R.id.times_set, View.VISIBLE)
                setViewVisibility(R.id.times_polar, View.GONE)
                Pair("${WidgetText.SUNRISE} ${l.sunrise}" to "${WidgetText.SUNSET} ${l.sunset}", null)
            }
            is WidgetText.TimesLine.Polar -> {
                setTextViewText(R.id.times_polar, l.label)
                setTextColor(R.id.times_polar, color(l.tint))
                setViewVisibility(R.id.times_rise, View.GONE)
                setViewVisibility(R.id.times_set, View.GONE)
                setViewVisibility(R.id.times_polar, View.VISIBLE)
                Pair(null, l.label)
            }
        }

    /**
     * The countdown line: the Chronometer (counting down to the target in the launcher's process)
     * or the static text, never both. Returns the text to fit (the ticking line at its widest).
     */
    private fun RemoteViews.countdown(line: WidgetText.LineText, clocks: ClockPair): String = when (line) {
        WidgetText.LineText.Hidden -> {
            setChronometer(R.id.count_chrono, 0L, null, false)
            setViewVisibility(R.id.count_chrono, View.GONE)
            setViewVisibility(R.id.count_static, View.GONE)
            ""
        }
        is WidgetText.LineText.Static -> {
            setChronometer(R.id.count_chrono, 0L, null, false)
            setViewVisibility(R.id.count_chrono, View.GONE)
            setTextViewText(R.id.count_static, line.text)
            setTextColor(R.id.count_static, color(line.tint))
            setViewVisibility(R.id.count_static, View.VISIBLE)
            line.text
        }
        is WidgetText.LineText.Ticking -> {
            setViewVisibility(R.id.count_static, View.GONE)
            setChronometer(R.id.count_chrono, CountdownMath.base(line.target, clocks), line.format, true)
            setChronometerCountDown(R.id.count_chrono, true)
            setTextColor(R.id.count_chrono, color(line.tint))
            setViewVisibility(R.id.count_chrono, View.VISIBLE)
            WidgetText.widestTicking(line.format)
        }
    }

    internal fun renderMedium(context: Context, s: WidgetState?, w: Float, h: Float, fit: Fitter, clocks: ClockPair): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_sun_medium)
        if (s == null) return v.noPlace(context)
        v.started()
        val (times, polar) = v.timesLine(s)
        val lineText = v.countdown(WidgetText.line(s, withTime = false), clocks)
        val size = WidgetFit.medium(w, h, times, polar, lineText, fit.fontScale, fit.measure, fit.lineRatio).sizes[0]
        for (id in intArrayOf(R.id.times_rise, R.id.times_set, R.id.times_polar, R.id.count_chrono, R.id.count_static)) v.size(id, size)
        return v.finish(context, WidgetText.describeMedium(s))
    }

    /**
     * Android 12 and later: one RemoteViews per entry, the ideal sizes and the widget's own reported
     * sizes ([LargeLayout.entries]); the launcher picks the fitting one closest to the widget.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun renderLargeSized(context: Context, s: WidgetState, fit: Fitter, clocks: ClockPair, reported: List<Pair<Float, Float>>): RemoteViews {
        val bySize = LinkedHashMap<SizeF, RemoteViews>()
        for ((w, h, arrangement) in LargeLayout.entries(fit.fontScale, reported)) {
            bySize[SizeF(w, h)] = renderLarge(context, s, arrangement, w, h, fit, clocks)
        }
        return RemoteViews(bySize)
    }

    private val BAND_IDS = listOf(
        intArrayOf(R.id.golden_label, R.id.golden_a, R.id.golden_b, R.id.golden_span),
        intArrayOf(R.id.civil_label, R.id.civil_a, R.id.civil_b, R.id.civil_span),
        intArrayOf(R.id.nautical_label, R.id.nautical_a, R.id.nautical_b, R.id.nautical_span),
        intArrayOf(R.id.astro_label, R.id.astro_a, R.id.astro_b, R.id.astro_span),
    )

    internal fun renderLarge(
        context: Context,
        s: WidgetState?,
        arrangement: LargeLayout.Arrangement,
        w: Float,
        h: Float,
        fit: Fitter,
        clocks: ClockPair,
    ): RemoteViews {
        val layout = when (arrangement) {
            LargeLayout.Arrangement.TALL -> R.layout.widget_sun_large_tall
            LargeLayout.Arrangement.WIDE -> R.layout.widget_sun_large_wide
            LargeLayout.Arrangement.COMPACT -> R.layout.widget_sun_large_compact
        }
        val v = RemoteViews(context.packageName, layout)
        if (s == null) return v.noPlace(context)
        v.started()

        val date = WidgetText.headerDate(s)
        v.setTextViewText(R.id.header_place, s.placeLabel)
        v.setTextViewText(R.id.header_date, date)
        v.setTextColor(R.id.header_date, TEXT_SECONDARY)

        val (times, polar) = v.timesLine(s)
        // The date form gives the time too, except in the narrow left column of WIDE.
        val lineText = v.countdown(WidgetText.line(s, withTime = arrangement != LargeLayout.Arrangement.WIDE), clocks)

        v.setTextViewText(R.id.noon_label, WidgetText.SOLAR_NOON)
        v.setTextViewText(R.id.noon_value, WidgetText.time(s.solarNoon, s.zone))

        val bands = listOf(
            WidgetText.GOLDEN_HOUR to WidgetText.golden(s),
            WidgetText.CIVIL to WidgetText.twilight(s.civil, s),
            WidgetText.NAUTICAL to WidgetText.twilight(s.nautical, s),
            WidgetText.ASTRONOMICAL to WidgetText.twilight(s.astronomical, s),
        )
        val cells = mutableListOf<Triple<String, String?, String?>>()
        val spans = mutableListOf<String?>()
        bands.forEachIndexed { i, (label, text) ->
            val (labelId, aId, bId, spanId) = BAND_IDS[i].toList()
            v.setTextViewText(labelId, label)
            when (text) {
                is WidgetText.BandText.Cells -> {
                    v.setTextViewText(aId, text.first)
                    v.setTextViewText(bId, text.second)
                    v.setViewVisibility(aId, View.VISIBLE)
                    v.setViewVisibility(bId, View.VISIBLE)
                    v.setViewVisibility(spanId, View.GONE)
                    cells += Triple(label, text.first, text.second)
                    spans += null
                }
                is WidgetText.BandText.Span -> {
                    v.setTextViewText(spanId, text.text)
                    v.setViewVisibility(aId, View.GONE)
                    v.setViewVisibility(bId, View.GONE)
                    v.setViewVisibility(spanId, View.VISIBLE)
                    cells += Triple(label, null, null)
                    spans += text.text
                }
            }
        }
        if (arrangement == LargeLayout.Arrangement.TALL) {
            v.setTextViewText(R.id.heads_label, "")
            v.setTextViewText(R.id.heads_dawn, WidgetText.DAWN)
            v.setTextViewText(R.id.heads_dusk, WidgetText.DUSK)
        }

        fun texts(headerDate: String) = WidgetFit.LargeTexts(
            headerPlace = s.placeLabel,
            headerDate = headerDate,
            sunrise = times?.first,
            sunset = times?.second,
            polar = polar,
            line = lineText,
            noonLabel = WidgetText.SOLAR_NOON,
            noon = WidgetText.time(s.solarNoon, s.zone),
            bands = cells,
            spans = spans,
            heads = WidgetText.DAWN to WidgetText.DUSK,
        )
        var r = WidgetFit.large(arrangement, w, h, texts(date), fit.fontScale, fit.measure, fit.lineRatio)
        if (!r.fits[1] && date != WidgetText.shortDate(s.today)) {
            // A long zone id: fit the header to the date, and let the zone part end in an ellipsis
            // (it is in the spoken description in full).
            r = WidgetFit.large(arrangement, w, h, texts(WidgetText.shortDate(s.today)), fit.fontScale, fit.measure, fit.lineRatio)
        }
        val table = r.sizes[0]
        val header = r.sizes[1]
        v.size(R.id.header_place, r.sizes[2])
        v.size(R.id.header_date, header)
        val tableIds = mutableListOf(
            R.id.times_rise, R.id.times_set, R.id.times_polar, R.id.count_chrono, R.id.count_static,
            R.id.noon_label, R.id.noon_value,
        )
        BAND_IDS.forEach { ids -> tableIds += ids.toList() }
        if (arrangement == LargeLayout.Arrangement.TALL) tableIds += listOf(R.id.heads_label, R.id.heads_dawn, R.id.heads_dusk)
        for (id in tableIds) v.size(id, table)
        return v.finish(context, WidgetText.describeLarge(s))
    }
}
