package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.awt.font.FontRenderContext

/**
 * Text fitting (1.1.9): one sizing path on every API. With an injected width function
 * the rules are checked exactly; with the app's own Inter font file (measured through java.awt) the
 * layouts are checked at their smallest sizes with the longest texts they can show. The on-device
 * render test, which also covers Android's own line metrics, the system font on Android 7 and
 * launcher padding, is still to do.
 */
class TextFitTest {

    /** A simple monospace measure: every character 0.6 em. */
    private val mono = TextFit.Measure { text, size -> text.length * 0.6f * size }

    @Test
    fun `the fitted size never exceeds a cell, never goes below the floor, and is shared by its group`() {
        val rows = listOf(
            TextFit.Row(0, 20f, listOf(TextFit.Cell("Sunrise 08:17", 70f), TextFit.Cell("Sunset 15:48", 70f))),
            TextFit.Row(0, 20f, listOf(TextFit.Cell("Sunrise tomorrow at 08:19", 150f))),
        )
        val r = TextFit.fit(rows, listOf(TextFit.Range(8f, 16f)), mono, 1.2f)
        assertTrue(r.allFit)
        val size = r.sizes[0]
        for (row in rows) for (c in row.cells) assertTrue("${c.text} at $size", mono.widthDp(c.text, size) <= TextFit.MARGIN * c.widthDp)
        assertTrue(size * 1.2f <= 20f)
        assertTrue(size >= 8f)
        assertEquals(0f, size % TextFit.STEP, 0f)
    }

    @Test
    fun `height limits the size, and a text too wide even at the floor reports it`() {
        val tall = TextFit.fit(listOf(TextFit.Row(0, 12f, listOf(TextFit.Cell("ab", 500f)))), listOf(TextFit.Range(8f, 24f)), mono, 1.2f)
        assertEquals(10f, tall.sizes[0], 0f)
        val wide = TextFit.fit(listOf(TextFit.Row(0, 40f, listOf(TextFit.Cell("x".repeat(80), 100f)))), listOf(TextFit.Range(8f, 16f)), mono, 1.2f)
        assertEquals(8f, wide.sizes[0], 0f)
        assertTrue(!wide.allFit)
        val ellipsis = TextFit.fit(listOf(TextFit.Row(0, 40f, listOf(TextFit.Cell("x".repeat(80), 100f, mustFit = false)))), listOf(TextFit.Range(8f, 16f)), mono, 1.2f)
        assertTrue(ellipsis.allFit)
    }

    // ------------------------------------------------------------------ with the real font

    private val inter: Font by lazy {
        Font.createFont(Font.TRUETYPE_FONT, TestFiles.require("${TestFiles.RES}/font/inter_medium.ttf"))
    }
    private val frc = FontRenderContext(null, true, true)
    private val interMeasure = TextFit.Measure { text, size ->
        // Tabular digits: Inter's tnum figures are about as wide as its widest default figure, so
        // every digit is measured as a zero, which is at least that wide.
        val tabular = text.replace(Regex("[0-9]"), "0")
        inter.deriveFont(size).getStringBounds(tabular, frc).width.toFloat()
    }
    private val interLine: Float by lazy {
        val lm = inter.deriveFont(100f).getLineMetrics("Hg", frc)
        (lm.ascent + lm.descent) / 100f
    }

    private val worstLarge = WidgetFit.LargeTexts(
        headerPlace = "Longyearbyen, Svalbard and Jan Mayen",
        headerDate = "Wed 30 Sept",
        sunrise = "Sunrise 08:17",
        sunset = "Sunset 15:48",
        polar = null,
        line = "Next sunrise Wed 30 Sept, 11:39",
        noonLabel = "Solar noon",
        noon = "12:03",
        bands = listOf(
            Triple("Golden hour", "until 09:37 +1", "from 14:29 +1"),
            Triple("Civil", "07:28 −1", "16:37 +1"),
            Triple("Nautical", "06:36", "17:29"),
            Triple("Astronomical", "05:47", "18:18"),
        ),
        spans = listOf(null, null, null, "All day and night"),
        heads = "Dawn" to "Dusk",
    )

    @Test
    fun `every large arrangement fits its longest texts at its smallest size, at font scales 1 and 2`() {
        val cases = listOf(
            Triple(LargeLayout.Arrangement.COMPACT, LargeLayout.COMPACT_MIN_WIDTH_DP, LargeLayout.COMPACT_MIN_HEIGHT_DP),
            Triple(LargeLayout.Arrangement.TALL, LargeLayout.TALL_MIN_WIDTH_DP, LargeLayout.TALL_MIN_HEIGHT_DP),
            Triple(LargeLayout.Arrangement.WIDE, LargeLayout.WIDE_MIN_WIDTH_DP, LargeLayout.WIDE_MIN_HEIGHT_DP),
        )
        for ((a, w, h) in cases) for (scale in listOf(1f, 2f)) {
            // WIDE's date form has no time (SunWidgets.renderLarge).
            val base = if (a == LargeLayout.Arrangement.WIDE) worstLarge.copy(line = "Next sunrise Wed 30 Sept") else worstLarge
            for (texts in listOf(base, base.copy(polar = "Always Down", sunrise = null, sunset = null, line = "Daylight left 00:00:00"))) {
                val r = WidgetFit.large(a, w.toFloat(), h.toFloat(), texts, scale, interMeasure, interLine)
                assertTrue("$a $w x $h at $scale: ${r.sizes.toList()} ${r.misfits}", r.allFit)
            }
        }
    }

    @Test
    fun `the small and medium widgets fit their longest texts at their smallest sizes`() {
        for (scale in listOf(1f, 2f)) {
            for (w in listOf(110f, 117f)) {
                for ((label, value) in listOf("Sunrise tomorrow" to "08:19", "Always Down" to "Sunrise 16 Feb", "Sunset" to "Sunset 30 Sept")) {
                    val r = WidgetFit.small(w, 40f, label, value, scale, interMeasure, interLine)
                    assertTrue("small $w: $label / $value ${r.sizes.toList()}", r.allFit)
                }
            }
            for (line in listOf("Sunrise tomorrow at 08:19", "Daylight left 00:00:00", "Next sunrise Wed 30 Sept")) {
                val r = WidgetFit.medium(180f, 40f, "Sunrise 08:17" to "Sunset 15:48", null, line, scale, interMeasure, interLine)
                assertTrue("medium: $line ${r.sizes.toList()}", r.allFit)
            }
        }
    }

    @Test
    fun `at the default 4x2 the compact rows are at least the 10sp base size`() {
        val r = WidgetFit.large(LargeLayout.Arrangement.COMPACT, 292f, 184f, worstLarge.copy(line = "Daylight left 00:00:00"), 1f, interMeasure, interLine)
        assertTrue(r.sizes.toList().toString(), r.sizes[0] >= 10f)
    }

    @Test
    fun `the place name in the header shrinks on its own and is shown in full`() {
        // "Your coordinates" was cut to "Your coordin..." on the large widget at 4 of 5 columns
        // (about 250 dp wide, at the 12 sp of its header). It now gets its own, smaller size.
        for (place in listOf("Your coordinates", "Tromsø", "Kuala Lumpur", "Buenos Aires", "Bunker", "Home, sweet")) {
            for ((a, w, h) in listOf(
                Triple(LargeLayout.Arrangement.COMPACT, 250f, 150f),
                Triple(LargeLayout.Arrangement.COMPACT, 292f, 184f),
                Triple(LargeLayout.Arrangement.TALL, 250f, 250f),
                Triple(LargeLayout.Arrangement.WIDE, 380f, 150f),
            )) {
                val texts = worstLarge.copy(headerPlace = place, headerDate = "Thu 19 Nov", line = "Daylight left 00:00:00")
                val r = WidgetFit.large(a, w, h, texts, 1f, interMeasure, interLine)
                val leftW = (w - 2 * WidgetFit.LARGE_PAD_H).let { if (a == LargeLayout.Arrangement.WIDE) (it - WidgetFit.WIDE_GAP) * WidgetFit.WIDE_LEFT else it }
                val size = r.sizes[2]
                assertTrue("$place $a $w: place $size dp, header ${r.sizes[1]} dp", size <= r.sizes[1])
                val room = leftW - interMeasure.widthDp(texts.headerDate, r.sizes[1]) - WidgetFit.HEADER_GAP
                assertTrue(
                    "$place $a $w x $h: needs ${interMeasure.widthDp(place, size)} dp of ${TextFit.MARGIN * room} dp at $size dp",
                    interMeasure.widthDp(place, size) <= TextFit.MARGIN * room,
                )
            }
        }
    }

    @Test
    fun `a name of 20 characters uses the room the date leaves and shrinks only as much as it must`() {
        // The 1080 px screenshot of the 2.0.0 draft: "ABCDEFGHIJKLMNOPQRST" ended at x 420 while the
        // date started at x 763, because the place had a fixed 55 per cent of the header and the date
        // 45 per cent, however short the date was. The place now has the row less the date and a gap.
        // The other cause, a widget fitted to a smaller entry than its own size, is the next test.
        val name = "ABCDEFGHIJKLMNOPQRST"
        for ((a, w, h) in listOf(
            Triple(LargeLayout.Arrangement.COMPACT, 250f, 150f),
            Triple(LargeLayout.Arrangement.COMPACT, 292f, 184f),
            Triple(LargeLayout.Arrangement.TALL, 250f, 250f),
            Triple(LargeLayout.Arrangement.TALL, 420f, 300f),
            Triple(LargeLayout.Arrangement.WIDE, 380f, 150f),
        )) {
            val texts = worstLarge.copy(headerPlace = name, headerDate = "Thu 19 Nov", line = "Daylight left 00:00:00")
            val r = WidgetFit.large(a, w, h, texts, 1f, interMeasure, interLine)
            val leftW = (w - 2 * WidgetFit.LARGE_PAD_H).let { if (a == LargeLayout.Arrangement.WIDE) (it - WidgetFit.WIDE_GAP) * WidgetFit.WIDE_LEFT else it }
            val header = r.sizes[1]
            val size = r.sizes[2]
            val room = TextFit.MARGIN * (leftW - interMeasure.widthDp(texts.headerDate, header) - WidgetFit.HEADER_GAP)
            val floor = when (a) {
                LargeLayout.Arrangement.COMPACT -> WidgetFit.COMPACT.floorDp
                LargeLayout.Arrangement.WIDE -> WidgetFit.WIDE.floorDp
                LargeLayout.Arrangement.TALL -> WidgetFit.FULL.floorDp
            }
            // It fits, or it is at the floor of its arrangement (and ends in an ellipsis there).
            assertTrue("$a $w: needs ${interMeasure.widthDp(name, size)} dp of $room dp at $size dp", interMeasure.widthDp(name, size) <= room || size == floor)
            // One step (0.25 dp) larger would not fit, unless the place is already at the header's size.
            val larger = size + TextFit.STEP
            assertTrue("$a $w: $size dp is smaller than the room needs", larger > header || interMeasure.widthDp(name, larger) > room)
            // The date keeps its size and its room.
            assertTrue("$a $w: date ${interMeasure.widthDp(texts.headerDate, header)} dp", interMeasure.widthDp(texts.headerDate, header) <= TextFit.MARGIN * leftW * WidgetFit.DATE_SHARE)
            // The old fixed split (55 per cent of the row) gave a smaller size, except where both are at the floor (WIDE's narrow column).
            val old = TextFit.shrinkToFit(name, leftW * 11 / 20, header, floor, interMeasure)
            assertTrue("$a $w: now $size dp, with the fixed split $old dp", size > old || (size == floor && old == floor))
        }
    }

    @Test
    fun `the longest name of a typed place (20 characters) shrinks to its floor and then ends in an ellipsis`() {
        // A name the user chose (2.0.0): at most 20 characters, shown whole. It may be wider than
        // "Your coordinates" in the widest letters; it then goes down to the floor and may end in
        // an ellipsis (the place cell is the one cell that may), and never grows past the header.
        val name = "W".repeat(20)
        for ((a, w, h) in listOf(
            Triple(LargeLayout.Arrangement.COMPACT, 250f, 150f),
            Triple(LargeLayout.Arrangement.TALL, 250f, 250f),
            Triple(LargeLayout.Arrangement.WIDE, 380f, 150f),
        )) {
            val r = WidgetFit.large(a, w, h, worstLarge.copy(headerPlace = name, line = "Daylight left 00:00:00"), 1f, interMeasure, interLine)
            assertTrue("$a: place ${r.sizes[2]} dp, header ${r.sizes[1]} dp", r.sizes[2] <= r.sizes[1])
            assertTrue("$a: floor", r.sizes[2] >= 8f)
        }
    }

    @Test
    fun `a name of 20 characters on a widget between two entries is fitted to the widget's own size`() {
        // The emulator's TALL widget of the 2.0.0 test, 361.5 x 331 dp. Fitted to the 300 x 250 entry
        // the launcher took before, every text was sized for a box 61 dp narrower than the widget; at
        // its own reported size (LargeLayout.entries) the name and the header have the room they are
        // drawn in.
        val name = "ABCDEFGHIJKLMNOPQRST"
        val texts = worstLarge.copy(headerPlace = name, headerDate = "Thu 19 Nov", line = "Daylight left 00:00:00")
        val (w, h) = 361.5f to 331f
        val before = LargeLayout.chosenEntryOnApi31(w, h)
        val own = LargeLayout.chosenEntry(LargeLayout.entries(1f, listOf(w to h)), w, h)
        assertEquals(Triple(w, h, LargeLayout.Arrangement.TALL), own)
        val old = WidgetFit.large(before.third, before.first, before.second, texts, 1f, interMeasure, interLine)
        val now = WidgetFit.large(own.third, own.first, own.second, texts, 1f, interMeasure, interLine)
        assertTrue("now ${now.sizes[2]} dp, before ${old.sizes[2]} dp", now.sizes[2] > old.sizes[2])
        assertTrue("header now ${now.sizes[1]} dp, before ${old.sizes[1]} dp", now.sizes[1] >= old.sizes[1])
        assertTrue(now.misfits.toString(), now.allFit)
        // The name fits the room the date leaves in the widget's own header, and one step more would
        // not, unless it is already at the header's size.
        val leftW = w - 2 * WidgetFit.LARGE_PAD_H
        val free = leftW - interMeasure.widthDp(texts.headerDate, now.sizes[1]) - WidgetFit.HEADER_GAP
        val room = TextFit.MARGIN * free
        assertTrue(interMeasure.widthDp(name, now.sizes[2]) <= room)
        assertTrue(now.sizes[2] + TextFit.STEP > now.sizes[1] || interMeasure.widthDp(name, now.sizes[2] + TextFit.STEP) > room)
        // So it uses most of the free part of the row (the rest is the 10 per cent safety margin).
        assertTrue("${interMeasure.widthDp(name, now.sizes[2])} dp of $free dp", interMeasure.widthDp(name, now.sizes[2]) > 0.85f * free || now.sizes[2] == now.sizes[1])
    }

    @Test
    fun `shrinkToFit stops at the floor and never grows`() {
        val mono = TextFit.Measure { text, size -> text.length * 0.6f * size }
        assertEquals(12f, TextFit.shrinkToFit("Oslo", 100f, 12f, 8f, mono), 0f)
        val fitted = TextFit.shrinkToFit("Your coordinates", 100f, 12f, 8f, mono)
        assertTrue("$fitted", fitted < 12f && fitted >= 8f && mono.widthDp("Your coordinates", fitted) <= TextFit.MARGIN * 100f)
        assertEquals(8f, TextFit.shrinkToFit("x".repeat(80), 100f, 12f, 8f, mono), 0f)
    }
}
