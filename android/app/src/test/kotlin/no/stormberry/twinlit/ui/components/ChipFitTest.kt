package no.stormberry.twinlit.ui.components

import no.stormberry.twinlit.ui.COORDINATE_FIELD_CHROME
import no.stormberry.twinlit.ui.COORDINATE_FIELD_GAP
import no.stormberry.twinlit.ui.WIDEST_COORDINATE
import no.stormberry.twinlit.ui.coordinateFieldPx
import no.stormberry.twinlit.ui.coordinateFieldsSideBySide
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R8 (D93, D94): the mode chips and the coordinate fields keep every word whole at 411 and 360 dp and
 * font scale 1.0, 1.3 and 2.0. Pure decisions, measured with the advance widths of the bundled Inter;
 * `LayoutAuditTest` (androidTest) repeats the proof with the real text engine on a device.
 */
class ChipFitTest {

    private val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())

    private fun inter(text: String, sp: Double, tracking: Double, scale: Double) =
        text.sumOf { (font.advance(it) * sp / font.unitsPerEm + tracking) * scale }

    private fun mono(text: String, sp: Double, scale: Double) = text.length * sp * 0.6 * scale

    @Test
    fun `the mode chips step down together to 12 sp and then stand one over the other (D93)`() {
        // Inter semibold is a little wider than regular: 6 per cent.
        fun width(label: String, sp: Float) = (inter(label, sp.toDouble(), 0.1, 1.0) * 1.06).toFloat()
        val labels = MODE_CHIP_LABELS
        fun size(screen: Double, scale: Double): Float? {
            val half = ((screen - 72.0 - 8.0) / 2.0 - 36.0).toFloat()
            return modeChipSize(labels, 14f, half / scale.toFloat()) { l, sp -> width(l, sp) }
        }
        // Normal size: one line at 14 sp on both phones.
        assertEquals(14f, size(411.0, 1.0)!!, 0.01f)
        assertEquals(14f, size(360.0, 1.0)!!, 0.01f)
        // 1.3 on 360 dp: a step down, never below 12 sp.
        val s = size(360.0, 1.3)
        assertNotNull(s)
        assertTrue("$s", s!! < 14f && s >= 12f)
        // 2.0: not at 12 sp either, so the chips are stacked.
        assertNull(size(360.0, 2.0))
        assertNull(size(411.0, 2.0))
        assertEquals(12f, MODE_CHIP_MIN_SIZE, 0f)
        // The same size for both chips: one answer for the list of labels.
        assertNull(modeChipSize(listOf("City search", "Coordinates"), 14f, 10f) { _, sp -> sp * 100f })
        assertEquals(12f, modeChipSize(listOf("a", "b"), 14f, 1200f) { _, sp -> sp * 100f }!!, 0f)
    }


    @Test
    fun `a coordinate field takes the widest number in its width or the two fields stand one over the other (D94)`() {
        // The app's own decision (coordinateFieldsSideBySide, coordinateFieldPx, WIDEST_COORDINATE, the chrome and
        // the gap): "-122.4194" at bodyLarge 16 sp in the platform monospace face (0.6 em a character), the caption
        // "LONGITUDE" at 11 sp in Inter with 0.5 sp tracking.
        fun sideBySide(screen: Double, scale: Double): Boolean = coordinateFieldsSideBySide(
            fieldPx = coordinateFieldPx((screen - 72.0).toFloat(), COORDINATE_FIELD_GAP.value),
            numberPx = mono(WIDEST_COORDINATE, 16.0, scale).toFloat(),
            chromePx = COORDINATE_FIELD_CHROME.value,
            captionPx = inter("LONGITUDE", 11.0, 0.5, scale).toFloat(),
        )
        assertEquals("-122.4194", WIDEST_COORDINATE)
        assertEquals(9, WIDEST_COORDINATE.length)
        assertEquals(36f, COORDINATE_FIELD_CHROME.value, 0f)
        assertEquals(10f, COORDINATE_FIELD_GAP.value, 0f)
        assertEquals(139f, coordinateFieldPx(360f - 72f, COORDINATE_FIELD_GAP.value), 0f)
        assertTrue(sideBySide(411.0, 1.0))
        assertTrue(sideBySide(360.0, 1.0))
        assertFalse("a negative longitude of 100 degrees or more does not fit 139 dp at 1.3", sideBySide(360.0, 1.3))
        assertTrue(sideBySide(411.0, 1.3))
        assertFalse(sideBySide(360.0, 2.0))
        assertFalse(sideBySide(411.0, 2.0))
        // Either condition alone stacks the fields: a number that does not fit, a caption that does not fit.
        assertFalse(coordinateFieldsSideBySide(fieldPx = 100f, numberPx = 70f, chromePx = 36f, captionPx = 10f))
        assertFalse(coordinateFieldsSideBySide(fieldPx = 100f, numberPx = 10f, chromePx = 36f, captionPx = 101f))
        assertTrue(coordinateFieldsSideBySide(fieldPx = 100f, numberPx = 64f, chromePx = 36f, captionPx = 100f))
    }
}
