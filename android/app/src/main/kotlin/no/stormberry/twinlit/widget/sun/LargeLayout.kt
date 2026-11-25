package no.stormberry.twinlit.widget.sun

/**
 * Which arrangement the large widget uses (1.1.9). Pure, so LargeLayoutTest covers it.
 *
 * - TALL: header, times, countdown, solar noon, golden hour, the Dawn and Dusk heads and the three
 *   twilight rows, one under the other. From 250 x 250 dp.
 * - WIDE: header, sunrise, sunset and countdown on the left; solar noon, golden hour and the three
 *   twilight rows on the right, with no column heads. From 380 x 150 dp (measured with Inter: at
 *   370 dp a golden hour cell with a day marker, "from 14:29 +1", does not fit at the 8 dp floor).
 * - COMPACT: TALL without the column heads, in smaller text. Everything below both, including the
 *   default 4x2 (292 x 184 dp on a 1080 x 1920 phone) and the smallest allowed size, 250 x 150.
 *
 * Every arrangement shows every row (the large widget cannot be made smaller than 250 x 150, so
 * no row is ever dropped). The text sizes are fitted to the size by [TextFit] on every API.
 *
 * On Android 12 and later the launcher chooses: SunWidgets hands it one RemoteViews per entry in
 * [entries] and it takes the fitting entry closest to the widget's size. Several TALL entries make
 * sure a tall widget is not given WIDE or COMPACT just because those fit too. [entries] adds the
 * sizes the launcher reports for the widget itself (2.0.0), so a widget between two entries of
 * [sizes] (360 dp wide, say, between 300 and 380) has its text fitted to its own size rather than
 * to the smaller entry, which drew every text, a long place name above all, much smaller than the
 * room it has. Android 7 to 11 have
 * no such mapping, so [pick] chooses from the reported size, and falls back to COMPACT (the
 * arrangement that fits the minimum) when no size is reported yet.
 *
 * The phone's font size: the TALL and WIDE thresholds are multiplied by [scaleFor], so a large
 * font keeps COMPACT until the widget is big enough for larger text. On Android 12 and later the
 * COMPACT entry at the widget's minimum, 250 x 150 dp, stays at every font size, so some entry
 * always fits the widget and no text is fitted to a box larger than the widget it is drawn in.
 * Above font scale 1.0 a second, scaled COMPACT entry is added, so a widget big enough for it
 * (the default 4x2, for example) still gets COMPACT text fitted to the larger box.
 */
object LargeLayout {

    enum class Arrangement { TALL, WIDE, COMPACT }

    const val TALL_MIN_WIDTH_DP = 250
    const val TALL_MIN_HEIGHT_DP = 250
    const val WIDE_MIN_WIDTH_DP = 380
    const val WIDE_MIN_HEIGHT_DP = 150
    const val COMPACT_MIN_WIDTH_DP = 250
    const val COMPACT_MIN_HEIGHT_DP = 150

    /** Android 12 and later, at the default font: the arrangement for each ideal size in dp. */
    val SIZES: List<Triple<Float, Float, Arrangement>> = listOf(
        Triple(TALL_MIN_WIDTH_DP.toFloat(), TALL_MIN_HEIGHT_DP.toFloat(), Arrangement.TALL),
        Triple(300f, TALL_MIN_HEIGHT_DP.toFloat(), Arrangement.TALL),
        Triple(380f, TALL_MIN_HEIGHT_DP.toFloat(), Arrangement.TALL),
        Triple(420f, 300f, Arrangement.TALL),
        Triple(WIDE_MIN_WIDTH_DP.toFloat(), WIDE_MIN_HEIGHT_DP.toFloat(), Arrangement.WIDE),
        Triple(COMPACT_MIN_WIDTH_DP.toFloat(), COMPACT_MIN_HEIGHT_DP.toFloat(), Arrangement.COMPACT),
    )

    /** How much larger the thresholds are at the phone's [fontScale]; never below 1. */
    fun scaleFor(fontScale: Float): Float = maxOf(1f, 0.5f + 0.5f * fontScale)

    /**
     * [SIZES] for the phone's [fontScale]: the TALL and WIDE entries multiplied by [scaleFor], and
     * the COMPACT entry kept at the unscaled minimum (250 x 150 dp). Android 12 and later use the
     * smallest entry when none fits, and each entry's texts are fitted to that entry's size; a
     * COMPACT entry only in its scaled size would be larger than a widget at its minimum, and its
     * rows would be cut. Above font scale 1.0 the scaled COMPACT entry is added as well, so a
     * widget between the two gets COMPACT text fitted to the larger box. At font scale 1.0 and
     * below the two would be the same size, so the scaled one is left out (each size is a key in
     * the launcher's map and must be unique).
     */
    fun sizes(fontScale: Float = 1f): List<Triple<Float, Float, Arrangement>> {
        val k = scaleFor(fontScale)
        val out = mutableListOf<Triple<Float, Float, Arrangement>>()
        for ((w, h, a) in SIZES) {
            if (a == Arrangement.COMPACT) {
                out += Triple(w, h, a)
                if (k > 1f) out += Triple(w * k, h * k, a)
            } else {
                out += Triple(w * k, h * k, a)
            }
        }
        return out
    }

    /**
     * Android 7 to 11: the arrangement for the widget as it shows in portrait, [minWidth] wide and
     * [maxHeight] high. COMPACT when no size is reported yet (all zero, the first update on some
     * launchers), because it is the one that fits the minimum size.
     */
    fun pick(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int, fontScale: Float = 1f): Arrangement {
        if (maxHeight <= 0 && minHeight <= 0) return Arrangement.COMPACT
        val k = scaleFor(fontScale)
        val height = maxOf(maxHeight, minHeight)
        val width = if (minWidth > 0) minWidth else maxWidth
        return when {
            height >= TALL_MIN_HEIGHT_DP * k && width >= TALL_MIN_WIDTH_DP * k -> Arrangement.TALL
            width >= WIDE_MIN_WIDTH_DP * k && height >= WIDE_MIN_HEIGHT_DP * k -> Arrangement.WIDE
            else -> Arrangement.COMPACT
        }
    }

    /**
     * What Android 12 and later choose from [sizes] for a widget [width] x [height] dp: the
     * fitting entry closest to the size, or the smallest entry when none fits. The platform's own
     * rule, restated so LargeLayoutTest can check the entries above.
     */
    fun chosenOnApi31(width: Float, height: Float, fontScale: Float = 1f): Arrangement =
        chosenEntryOnApi31(width, height, fontScale).third

    /** The entry of [sizes] (its ideal width and height in dp, and its arrangement) behind [chosenOnApi31]. */
    fun chosenEntryOnApi31(width: Float, height: Float, fontScale: Float = 1f): Triple<Float, Float, Arrangement> =
        chosenEntry(sizes(fontScale), width, height)

    /** The platform's rule over any list of entries: the fitting entry closest to the size, else the smallest. */
    fun chosenEntry(all: List<Triple<Float, Float, Arrangement>>, width: Float, height: Float): Triple<Float, Float, Arrangement> {
        val fitting = all.filter { (w, h) -> w <= width && h <= height }
        return fitting.minByOrNull { (w, h) -> (width - w) * (width - w) + (height - h) * (height - h) }
            ?: all.minBy { (w, h) -> w * h }
    }

    /** The most sized RemoteViews one widget may carry (the platform's limit). */
    const val MAX_ENTRIES = 16

    /**
     * Android 12 and later: [sizes], and an entry at each size the launcher reports for this
     * widget ([reported], width and height in dp; a size for each orientation), with the
     * arrangement the launcher would take from [sizes] at that size. The launcher then takes the
     * widget's own size when it fits, so the texts are fitted to the box they are drawn in; when it
     * does not (a size reported a little larger than the view), the entries of [sizes] are there as
     * before. A size that is not positive, or is already an entry, adds nothing, and there are
     * never more than [MAX_ENTRIES].
     */
    fun entries(fontScale: Float, reported: List<Pair<Float, Float>>): List<Triple<Float, Float, Arrangement>> {
        val base = sizes(fontScale)
        val out = base.toMutableList()
        for ((w, h) in reported) {
            if (out.size >= MAX_ENTRIES) break
            if (!(w > 0f && h > 0f && w.isFinite() && h.isFinite())) continue
            if (out.any { it.first == w && it.second == h }) continue
            out += Triple(w, h, chosenEntry(base, w, h).third)
        }
        return out
    }
}
