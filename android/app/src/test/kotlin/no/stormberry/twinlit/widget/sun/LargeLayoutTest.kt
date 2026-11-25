package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.TestFiles
import no.stormberry.twinlit.widget.sun.LargeLayout.Arrangement.COMPACT
import no.stormberry.twinlit.widget.sun.LargeLayout.Arrangement.TALL
import no.stormberry.twinlit.widget.sun.LargeLayout.Arrangement.WIDE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeLayoutTest {

    private fun minimum(a: LargeLayout.Arrangement): Pair<Int, Int> = when (a) {
        TALL -> LargeLayout.TALL_MIN_WIDTH_DP to LargeLayout.TALL_MIN_HEIGHT_DP
        WIDE -> LargeLayout.WIDE_MIN_WIDTH_DP to LargeLayout.WIDE_MIN_HEIGHT_DP
        COMPACT -> LargeLayout.COMPACT_MIN_WIDTH_DP to LargeLayout.COMPACT_MIN_HEIGHT_DP
    }

    @Test
    fun `Android 7 to 11 - pick, with COMPACT when no size is reported`() {
        assertEquals(COMPACT, LargeLayout.pick(0, 0, 0, 0))
        assertEquals(COMPACT, LargeLayout.pick(292, 160, 330, 184)) // the default 4x2
        assertEquals(TALL, LargeLayout.pick(260, 230, 300, 260))
        assertEquals(WIDE, LargeLayout.pick(380, 150, 420, 160))
        assertEquals(COMPACT, LargeLayout.pick(260, 150, 300, 170))
        // A large font raises the thresholds: the same TALL size is COMPACT at font scale 2.0.
        assertEquals(COMPACT, LargeLayout.pick(260, 230, 300, 260, fontScale = 2f))
    }

    /** The phone font sizes the tests cover: the default, and the larger Android settings. */
    private val fontScales = listOf(1f, 1.15f, 1.3f, 1.5f, 2f)

    @Test
    fun `Android 12 and later - the chosen entry always fits, over the whole resize range`() {
        for (scale in fontScales) {
            val k = LargeLayout.scaleFor(scale)
            var w = 250f
            while (w <= 560f) {
                var h = 150f
                while (h <= 480f) {
                    val (ew, eh, a) = LargeLayout.chosenEntryOnApi31(w, h, scale)
                    // The texts are fitted to the entry's size, so it must never exceed the widget.
                    assertTrue("$w x $h at $scale -> $a $ew x $eh", ew <= w && eh <= h)
                    val (mw, mh) = minimum(a)
                    if (a != COMPACT) assertTrue("$w x $h at $scale -> $a", mw * k <= w && mh * k <= h)
                    h += 10f
                }
                w += 10f
            }
        }
        assertEquals(COMPACT, LargeLayout.chosenOnApi31(292f, 184f))
        assertEquals(TALL, LargeLayout.chosenOnApi31(560f, 480f))
        assertEquals(WIDE, LargeLayout.chosenOnApi31(400f, 200f))
    }

    @Test
    fun `Android 12 and later - a widget at its minimum or the default 4x2 never gets a box larger than itself`() {
        for (scale in fontScales) {
            for ((w, h) in listOf(250f to 150f, 292f to 184f)) {
                val (ew, eh, a) = LargeLayout.chosenEntryOnApi31(w, h, scale)
                assertEquals("$w x $h at $scale", COMPACT, a)
                assertTrue("$w x $h at $scale -> $ew x $eh", ew <= w && eh <= h)
            }
            assertEquals(Triple(250f, 150f, COMPACT), LargeLayout.chosenEntryOnApi31(250f, 150f, scale))
        }
    }

    @Test
    fun `Android 12 and later - a larger font still gets larger COMPACT text on the default 4x2`() {
        // At font scale 1.0 the default 4x2 gets the unscaled COMPACT entry.
        assertEquals(Triple(250f, 150f, COMPACT), LargeLayout.chosenEntryOnApi31(292f, 184f, 1f))
        for (scale in listOf(1.15f, 1.3f)) {
            val k = LargeLayout.scaleFor(scale)
            val (ew, eh, a) = LargeLayout.chosenEntryOnApi31(292f, 184f, scale)
            assertEquals("292 x 184 at $scale", COMPACT, a)
            assertEquals("292 x 184 at $scale", 250f * k, ew, 0.001f)
            assertEquals("292 x 184 at $scale", 150f * k, eh, 0.001f)
            assertTrue("292 x 184 at $scale -> $ew x $eh", ew <= 292f && eh <= 184f && ew > 250f && eh > 150f)
        }
    }

    @Test
    fun `scale and sizes`() {
        assertEquals(1f, LargeLayout.scaleFor(0.85f), 0f)
        assertEquals(6, LargeLayout.sizes(0.85f).size)
        assertEquals(1.15f, LargeLayout.scaleFor(1.3f), 0.0001f)
        assertEquals(1.25f, LargeLayout.scaleFor(1.5f), 0f)
        assertEquals(1.5f, LargeLayout.scaleFor(2f), 0f)
        for (scale in fontScales) {
            val k = LargeLayout.scaleFor(scale)
            val sizes = LargeLayout.sizes(scale)
            // The unscaled COMPACT entry on every font scale, plus the scaled one above 1.0.
            val compact = sizes.filter { it.third == COMPACT }
            if (k > 1f) {
                assertEquals(7, sizes.size)
                assertEquals(listOf(Triple(250f, 150f, COMPACT), Triple(250f * k, 150f * k, COMPACT)), compact)
            } else {
                assertEquals(6, sizes.size)
                assertEquals(listOf(Triple(250f, 150f, COMPACT)), compact)
            }
            // Each size is a key in the launcher's map, so none may repeat; Android allows 16.
            assertEquals(sizes.size, sizes.map { it.first to it.second }.toSet().size)
            assertTrue(sizes.size <= 16)
            for ((w, h, a) in sizes.filter { it.third != COMPACT }) {
                val (mw, mh) = minimum(a)
                assertTrue("$a $w x $h at $scale", w >= mw * k - 0.01f && h >= mh * k - 0.01f)
            }
        }
    }

    @Test
    fun `Android 12 and later - the widget's own reported sizes are entries, so the texts fit the box they are drawn in`() {
        // The TALL widget of the 2.0.0 emulator test: 361.5 x 331 dp (its header row was 886 px wide
        // on a 420 dpi screen, plus the 12 dp side paddings). No entry of sizes() is closer than
        // 300 x 250, so its texts were fitted to a box 61 dp narrower than the widget.
        val widget = 361.5f to 331f
        assertEquals(Triple(300f, 250f, TALL), LargeLayout.chosenEntryOnApi31(widget.first, widget.second))
        for (scale in fontScales) {
            val reported = listOf(widget, 640f to 200f)
            val all = LargeLayout.entries(scale, reported)
            // Every ideal entry is kept, and each reported size is added with the arrangement those give it.
            assertEquals(LargeLayout.sizes(scale), all.take(LargeLayout.sizes(scale).size))
            for ((w, h) in reported) {
                val own = all.single { it.first == w && it.second == h }
                assertEquals("$w x $h at $scale", LargeLayout.chosenOnApi31(w, h, scale), own.third)
                // The launcher takes the widget's own entry, at its own size.
                assertEquals("$w x $h at $scale", own, LargeLayout.chosenEntry(all, w, h))
            }
            // Each size is a key in the launcher's map: none repeats, and never more than 16.
            assertEquals(all.size, all.map { it.first to it.second }.toSet().size)
            assertTrue(all.size <= LargeLayout.MAX_ENTRIES)
            // The chosen entry still never exceeds the widget, over the whole resize range.
            var w = 250f
            while (w <= 560f) {
                var h = 150f
                while (h <= 480f) {
                    val (ew, eh) = LargeLayout.chosenEntry(all, w, h)
                    assertTrue("$w x $h at $scale -> $ew x $eh", ew <= w && eh <= h)
                    h += 10f
                }
                w += 10f
            }
        }
        assertEquals(TALL, LargeLayout.chosenEntry(LargeLayout.entries(1f, listOf(widget)), widget.first, widget.second).third)
    }

    @Test
    fun `Android 12 and later - reported sizes that repeat an entry, are not positive or are too many add nothing`() {
        val base = LargeLayout.sizes(1f)
        assertEquals(base, LargeLayout.entries(1f, emptyList()))
        assertEquals(base, LargeLayout.entries(1f, listOf(300f to 250f, 0f to 200f, 300f to -1f, Float.NaN to 200f, Float.POSITIVE_INFINITY to 300f)))
        val many = (0 until 40).map { (260f + it) to (260f + it) }
        val capped = LargeLayout.entries(2f, many)
        assertEquals(LargeLayout.MAX_ENTRIES, capped.size)
        assertEquals(capped.size, capped.map { it.first to it.second }.toSet().size)
    }

    @Test
    fun `the renderer gives each large widget its own reported sizes`() {
        val code = TestFiles.require("${TestFiles.KOTLIN}/widget/sun/SunWidgets.kt").readText()
        assertTrue(code.contains("AppWidgetManager.OPTION_APPWIDGET_SIZES"))
        assertTrue(code.contains("LargeLayout.entries(fit.fontScale, reported)"))
        assertTrue(code.contains("for (id in large) mgr.updateAppWidget(id, renderLargeSized(context, state, fit, clocks, reportedSizes(mgr.getAppWidgetOptions(id))))"))
        assertTrue(!code.contains("LargeLayout.sizes(fit.fontScale)) {"))
    }
}
