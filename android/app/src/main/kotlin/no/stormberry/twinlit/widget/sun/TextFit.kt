package no.stormberry.twinlit.widget.sun

import kotlin.math.floor
import kotlin.math.min

/**
 * Text sizes for the widgets (1.1.9), one sizing path on EVERY API level.
 *
 * Autosize is not used (except for the hint before the first render): it works per view, so lines
 * that must match would shrink by different amounts, a ticking Chronometer would change size as
 * its digits change, and `setTextViewTextSize` is ignored while autosize is on. Instead the
 * renderer measures the texts it is about to show at the size the launcher reports, picks the
 * largest size per group at which every text of the group fits, and sets it with
 * `setTextViewTextSize` in dp. Rows that must look alike (the medium widget's two lines, the large
 * widget's table rows) are one group and get one size.
 *
 * Pure: the measuring function is injected, so TextFitTest runs it on the JVM with the app's own
 * Inter font file. On the phone SunWidgets measures with `Paint` and the font the layout uses
 * (Inter from Android 8, the system sans on 7 and 7.1), with tabular digits.
 */
object TextFit {

    /** A text may use this share of its cell's width; the rest is the safety margin. */
    const val MARGIN = 0.9f

    /** Sizes are multiples of this, in dp. */
    const val STEP = 0.25f

    /** The width in dp of [text] drawn at [sizeDp]. */
    fun interface Measure {
        fun widthDp(text: String, sizeDp: Float): Float
    }

    /** A group's size range in dp: the floor, and the maximum (already multiplied by the font scale). */
    data class Range(val floorDp: Float, val maxDp: Float)

    /** A text and the width its view has, in dp. [mustFit] false: it may ellipsise (the place name). */
    data class Cell(val text: String, val widthDp: Float, val mustFit: Boolean = true)

    /** One row of text: its group, its height in dp and its cells side by side. */
    data class Row(val group: Int, val heightDp: Float, val cells: List<Cell>)

    /**
     * A size per group, and whether every text of that group fits at it (false: at the floor and
     * still too wide or tall); [misfits] names the texts that do not, for tests and logs.
     */
    class Result(val sizes: FloatArray, val fits: BooleanArray, val misfits: List<String> = emptyList()) {
        val allFit: Boolean get() = fits.all { it }
    }

    /**
     * For each group: start at its maximum, lower it until every row's line height
     * ([lineRatio] times the size) fits the row and every text fits its cell with [MARGIN], and
     * never go below the floor.
     */
    fun fit(rows: List<Row>, ranges: List<Range>, measure: Measure, lineRatio: Float): Result {
        val sizes = FloatArray(ranges.size)
        val fits = BooleanArray(ranges.size) { true }
        val misfits = mutableListOf<String>()
        for ((g, range) in ranges.withIndex()) {
            val mine = rows.filter { it.group == g }
            var size = range.maxDp
            for (row in mine) size = min(size, row.heightDp / lineRatio)
            for (row in mine) for (cell in row.cells) {
                if (!cell.mustFit || cell.text.isEmpty()) continue
                val room = MARGIN * cell.widthDp
                val w = measure.widthDp(cell.text, size)
                if (w > room && w > 0f) size = size * room / w
                while (size > range.floorDp && measure.widthDp(cell.text, size) > room) size -= STEP
            }
            size = maxOf(snap(size), range.floorDp)
            val before = misfits.size
            // At the floor a text may still not fit: say which, so a test or a log can show it.
            for (row in mine) {
                if (row.heightDp < lineRatio * size - 0.01f) misfits += "row height ${row.heightDp} dp at $size dp"
                for (cell in row.cells) {
                    if (cell.mustFit && measure.widthDp(cell.text, size) > MARGIN * cell.widthDp) {
                        misfits += "\"${cell.text}\" in ${cell.widthDp} dp at $size dp"
                    }
                }
            }
            if (misfits.size > before) fits[g] = false
            sizes[g] = size
        }
        return Result(sizes, fits, misfits)
    }

    /**
     * The largest size from [startDp] down to [floorDp] at which [text] fits [widthDp] with
     * [MARGIN]; the floor when it never does (the view then ends in an ellipsis). For a text that
     * may shrink on its own, such as the place name in the large widget's header.
     */
    fun shrinkToFit(text: String, widthDp: Float, startDp: Float, floorDp: Float, measure: Measure): Float {
        var size = startDp
        val room = MARGIN * widthDp
        if (text.isNotEmpty()) {
            val w = measure.widthDp(text, size)
            if (w > room && w > 0f) size = size * room / w
            while (size > floorDp && measure.widthDp(text, size) > room) size -= STEP
        }
        return maxOf(snap(size), floorDp)
    }

    private fun snap(size: Float): Float = floor(size / STEP) * STEP
}

/**
 * The geometry of the widget layouts, in dp, and the rows [TextFit] fits for each (1.1.9). The
 * numbers here are the paddings and weights of the XML layouts; WidgetLayoutTest checks that the
 * two agree.
 */
object WidgetFit {

    const val SMALL_PAD_H = 10f
    const val SMALL_PAD_V = 6f
    const val MEDIUM_PAD_H = 12f
    const val MEDIUM_PAD_V = 6f
    const val LARGE_PAD_H = 12f
    const val LARGE_PAD_V = 8f

    /** The gap between the two times on one line ("Sunrise 08:17   Sunset 15:48"). */
    const val COL_GAP = 8f

    /** The gap between the two columns of the WIDE arrangement. */
    const val WIDE_GAP = 12f

    /** The WIDE arrangement's left column share (header, sunrise, sunset, countdown). */
    const val WIDE_LEFT = 0.42f

    /** The small widget's label row and value row share the height 2 : 3. */
    const val SMALL_LABEL_SHARE = 0.4f

    /** A table row: the label, then two value columns (the two together hold a spanning text). */
    const val LABEL_SHARE = 0.36f
    const val VALUE_SHARE = 0.32f

    /**
     * The header: the place and the date. The date is as wide as its text, at most [DATE_SHARE] of
     * the header, and the place takes all the rest, less [HEADER_GAP] (the layouts give the place
     * the weight and the date its own width: a fixed split left a long name a cell much narrower
     * than the room, and a short date a cell much wider than it needs). The date must fit at the
     * header's size; the place may be smaller than that (WidgetFit.large gives it its own size,
     * down to the floor) and ends in an ellipsis only when even the floor is too large.
     */
    const val DATE_SHARE = 0.45f

    /** The gap between the place and the date in the header (the date's start margin). */
    const val HEADER_GAP = 8f

    /** Size ranges, floor in dp and maximum in sp (multiplied by the font scale). */
    data class SpRange(val floorDp: Float, val maxSp: Float) {
        fun at(fontScale: Float) = TextFit.Range(floorDp, maxOf(floorDp, maxSp * fontScale))
    }

    val SMALL_LABEL = SpRange(8f, 14f)
    val SMALL_VALUE = SpRange(9f, 24f)
    val MEDIUM = SpRange(8f, 16f)
    val COMPACT = SpRange(8f, 14f)
    val FULL = SpRange(9f, 16f)

    /**
     * WIDE: the FULL maximum, the COMPACT floor. At its 380 dp minimum a golden hour cell with a
     * day marker ("from 14:29 +1") needs 8 dp; WIDE's countdown line leaves out the time of the
     * date form for the same reason ("Next sunrise Tue 16 Feb").
     */
    val WIDE = SpRange(8f, 16f)

    /** The texts of the large widget, as shown (the ticking line as its widest value). */
    data class LargeTexts(
        val headerPlace: String,
        val headerDate: String,
        val sunrise: String?,
        val sunset: String?,
        val polar: String?,
        val line: String?,
        val noonLabel: String,
        val noon: String,
        val bands: List<Triple<String, String?, String?>>,
        val spans: List<String?>,
        val heads: Pair<String, String>,
    )

    fun small(widthDp: Float, heightDp: Float, label: String, value: String, fontScale: Float, m: TextFit.Measure, lineRatio: Float): TextFit.Result {
        val w = widthDp - 2 * SMALL_PAD_H
        val h = heightDp - 2 * SMALL_PAD_V
        val rows = listOf(
            TextFit.Row(0, h * SMALL_LABEL_SHARE, listOf(TextFit.Cell(label, w))),
            TextFit.Row(1, h * (1 - SMALL_LABEL_SHARE), listOf(TextFit.Cell(value, w))),
        )
        return TextFit.fit(rows, listOf(SMALL_LABEL.at(fontScale), SMALL_VALUE.at(fontScale)), m, lineRatio)
    }

    /** [times] null: the polar line ([polar]) across the whole width. */
    fun medium(
        widthDp: Float,
        heightDp: Float,
        times: Pair<String, String>?,
        polar: String?,
        line: String?,
        fontScale: Float,
        m: TextFit.Measure,
        lineRatio: Float,
    ): TextFit.Result {
        val w = widthDp - 2 * MEDIUM_PAD_H
        val rowH = (heightDp - 2 * MEDIUM_PAD_V) / 2
        val first = if (times != null) {
            val half = (w - COL_GAP) / 2
            listOf(TextFit.Cell(times.first, half), TextFit.Cell(times.second, half))
        } else {
            listOf(TextFit.Cell(polar.orEmpty(), w))
        }
        val rows = listOf(
            TextFit.Row(0, rowH, first),
            TextFit.Row(0, rowH, listOf(TextFit.Cell(line.orEmpty(), w))),
        )
        return TextFit.fit(rows, listOf(MEDIUM.at(fontScale)), m, lineRatio)
    }

    /**
     * The sizes: [0] the table rows, [1] the header, [2] the place name in the header. The place
     * starts at the header's size and shrinks, alone, until it fits its cell ("Your coordinates"
     * at 4 of 5 columns), down to the floor of the arrangement; the date keeps the header size.
     * It is a size per view set with setTextViewTextSize like every other, so it works on every
     * API level (autosize in a RemoteViews layout needs Android 8, and one size per group is what
     * keeps the rows alike).
     */
    fun large(
        arrangement: LargeLayout.Arrangement,
        widthDp: Float,
        heightDp: Float,
        t: LargeTexts,
        fontScale: Float,
        m: TextFit.Measure,
        lineRatio: Float,
    ): TextFit.Result {
        val range = when (arrangement) {
            LargeLayout.Arrangement.COMPACT -> COMPACT
            LargeLayout.Arrangement.WIDE -> WIDE
            LargeLayout.Arrangement.TALL -> FULL
        }
        val inner = widthDp - 2 * LARGE_PAD_H
        val h = heightDp - 2 * LARGE_PAD_V
        val wide = arrangement == LargeLayout.Arrangement.WIDE
        val leftW = if (wide) (inner - WIDE_GAP) * WIDE_LEFT else inner
        val rightW = if (wide) (inner - WIDE_GAP) * (1 - WIDE_LEFT) else inner

        val left = mutableListOf<List<TextFit.Cell>>()
        val leftGroups = mutableListOf<Int>()
        val right = mutableListOf<List<TextFit.Cell>>()

        // The header: the date must fit its share; the place may ellipsise, and its width is the
        // rest of the row once the date's own width is known (below), so it is only a placeholder.
        left += listOf(TextFit.Cell(t.headerPlace, leftW - HEADER_GAP, mustFit = false), TextFit.Cell(t.headerDate, leftW * DATE_SHARE))
        leftGroups += 1
        if (t.polar != null) {
            left += listOf(TextFit.Cell(t.polar, leftW))
            leftGroups += 0
        } else if (wide) {
            left += listOf(TextFit.Cell(t.sunrise.orEmpty(), leftW))
            left += listOf(TextFit.Cell(t.sunset.orEmpty(), leftW))
            leftGroups += 0
            leftGroups += 0
        } else {
            val half = (leftW - COL_GAP) / 2
            left += listOf(TextFit.Cell(t.sunrise.orEmpty(), half), TextFit.Cell(t.sunset.orEmpty(), half))
            leftGroups += 0
        }
        left += listOf(TextFit.Cell(t.line.orEmpty(), leftW))
        leftGroups += 0

        val table = if (wide) right else left
        val tableW = if (wide) rightW else leftW
        fun row(label: String, a: String?, b: String?, span: String?): List<TextFit.Cell> {
            val cells = mutableListOf(TextFit.Cell(label, tableW * LABEL_SHARE))
            if (span != null) {
                cells += TextFit.Cell(span, tableW * 2 * VALUE_SHARE)
            } else {
                cells += TextFit.Cell(a.orEmpty(), tableW * VALUE_SHARE)
                cells += TextFit.Cell(b.orEmpty(), tableW * VALUE_SHARE)
            }
            return cells
        }
        val tableStart = table.size
        table += row(t.noonLabel, null, t.noon, null)
        table += row(t.bands[0].first, t.bands[0].second, t.bands[0].third, t.spans[0])
        if (arrangement == LargeLayout.Arrangement.TALL) table += row("", t.heads.first, t.heads.second, null)
        for (i in 1..3) table += row(t.bands[i].first, t.bands[i].second, t.bands[i].third, t.spans[i])
        if (!wide) repeat(table.size - tableStart) { leftGroups += 0 }

        val rows = mutableListOf<TextFit.Row>()
        // The left column (or the only one, when not WIDE) shares its height equally among its rows.
        val leftRowH = h / left.size
        left.forEachIndexed { i, cells -> rows += TextFit.Row(leftGroups[i], leftRowH, cells) }
        if (wide) {
            val rightRowH = h / right.size
            right.forEach { cells -> rows += TextFit.Row(0, rightRowH, cells) }
        }
        val r = TextFit.fit(rows, listOf(range.at(fontScale), range.at(fontScale)), m, lineRatio)
        // The place has the row less the date as wide as it is drawn at the header size, less the gap.
        val dateW = if (t.headerDate.isEmpty()) 0f else m.widthDp(t.headerDate, r.sizes[1])
        val placeW = maxOf(0f, leftW - dateW - HEADER_GAP)
        val place = TextFit.shrinkToFit(t.headerPlace, placeW, r.sizes[1], range.floorDp, m)
        return TextFit.Result(r.sizes + place, r.fits + true, r.misfits)
    }
}
