package no.stormberry.twinlit.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.stormberry.twinlit.ui.alarms.PREVIEW_COLUMN_MIN_DP
import no.stormberry.twinlit.ui.alarms.PREVIEW_COLUMN_SLACK_DP
import no.stormberry.twinlit.ui.alarms.PREVIEW_COLUMN_WORDS
import no.stormberry.twinlit.ui.alarms.choiceChipsEqual
import no.stormberry.twinlit.ui.alarms.previewLabelColumnDp
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R9 (D96, D97): the alarm editor's choice chips and preview column, and the minimum heights, are proved
 * through the app's own decision code (`choiceChipsEqual`, `previewLabelColumnDp`, `growsWithText` and the
 * height constants the screens use), not through a copy of the rules. The widths are the advance widths of
 * the bundled Inter. `LayoutAuditTest` (androidTest) repeats the proof with the real text engine on a device.
 */
class EditorFitTest {

    private val font = Font.load(TestFiles.require("${TestFiles.RES}/font/inter_regular.ttf").readBytes())

    /** Inter, [sp] with [tracking] sp after each character, at [scale], in dp. */
    private fun inter(text: String, sp: Double, tracking: Double, scale: Double) =
        text.sumOf { (font.advance(it) * sp / font.unitsPerEm + tracking) * scale }

    /** labelLarge: 14 sp, 0.1 sp tracking. */
    private fun chipWord(label: String, scale: Double) = wordsOf(label).maxOf { inter(it, 14.0, 0.1, scale) }.toFloat()

    private fun equal(labels: List<String>, screen: Double, scale: Double): Boolean = choiceChipsEqual(
        labelWordPx = labels.map { chipWord(it, scale) },
        rowPx = (screen - 72.0).toFloat(),
        gapPx = 8f,
        chromePx = 36f,
    )

    @Test
    fun `the choice chips share the row while the widest word of every label fits its share, else they flow (D96)`() {
        val sunMoon = listOf("Sun", "Moon")
        val events = listOf("Sunrise", "Solar noon", "Sunset")
        val direction = listOf("Before", "At", "After")
        // Normal size: equal chips for all three rows on both phones.
        for (screen in listOf(411.0, 360.0)) for (labels in listOf(sunMoon, events, direction)) {
            assertTrue("$labels at $screen, 1.0", equal(labels, screen, 1.0))
        }
        // Two short labels always fit.
        assertTrue(equal(sunMoon, 360.0, 2.0))
        assertTrue(equal(sunMoon, 411.0, 2.0))
        // At 360 dp and font scale 1.3 or 2.0 "Sunrise", "Before" and "After" do not fit a third of the row.
        assertFalse(equal(events, 360.0, 1.3))
        assertFalse(equal(events, 360.0, 2.0))
        assertFalse(equal(direction, 360.0, 2.0))
        assertFalse(equal(events, 411.0, 2.0))
        // One label that does not fit its share puts the whole row in a flow.
        assertFalse(choiceChipsEqual(listOf(10f, 10f, 200f), rowPx = 288f, gapPx = 8f, chromePx = 36f))
        // The share is the row less the gaps, divided by the chips, less the chip's chrome.
        assertTrue(choiceChipsEqual(listOf(54f, 54f, 54f), rowPx = 288f, gapPx = 8f, chromePx = 36f))
        assertFalse(choiceChipsEqual(listOf(55f, 54f, 54f), rowPx = 288f, gapPx = 8f, chromePx = 36f))
    }

    @Test
    fun `the first column of the preview is 84 dp or the widest word plus 10 dp, so Earliest never breaks (D96)`() {
        assertEquals(84f, PREVIEW_COLUMN_MIN_DP, 0f)
        assertEquals(10f, PREVIEW_COLUMN_SLACK_DP, 0f)
        assertTrue(PREVIEW_COLUMN_WORDS.containsAll(listOf("Earliest", "Latest", "Spread")))
        fun column(scale: Double): Float {
            // bodySmall: 12 sp, 0.4 sp tracking.
            val widest = PREVIEW_COLUMN_WORDS.maxOf { w -> wordsOf(w).maxOf { inter(it, 12.0, 0.4, scale) } }.toFloat()
            return previewLabelColumnDp(widest)
        }
        // Normal size: the column is the 84 dp it always was. At 2.0 it grows to hold its widest word.
        assertEquals(84f, column(1.0), 0f)
        assertTrue(column(2.0) > 84f)
        for (scale in listOf(1.0, 1.3, 2.0)) {
            val col = column(scale)
            for (w in PREVIEW_COLUMN_WORDS) for (word in wordsOf(w)) {
                assertTrue("$word at $scale", inter(word, 12.0, 0.4, scale) + PREVIEW_COLUMN_SLACK_DP <= col + 0.01)
            }
        }
        assertEquals(84f, previewLabelColumnDp(0f), 0f)
        assertEquals(84f, previewLabelColumnDp(74f), 0f)
        assertEquals(84.5f, previewLabelColumnDp(74.5f), 0f)
        assertEquals(110f, previewLabelColumnDp(100f), 0f)
    }

    @Test
    fun `a button or bar that carries words has a minimum height, never a fixed one (D97)`() {
        // A modifier is equal to another when it is the same element with the same limits, so this compares what
        // the app's growsWithText gives with a minimum-only height and with a fixed height.
        for ((name, min) in listOf(
            "Create alarm" to CREATE_ALARM_MIN_HEIGHT,
            "Create the first alarm" to FIRST_ALARM_MIN_HEIGHT,
            "the top bar" to TOP_BAR_MIN_HEIGHT,
            "Use these coordinates" to APPLY_COORDINATES_MIN_HEIGHT,
        )) {
            assertEquals("$name has a minimum height and no maximum", Modifier.heightIn(min = min), Modifier.growsWithText(min))
            assertNotEquals("$name must not be a fixed height", Modifier.height(min), Modifier.growsWithText(min))
            assertNotEquals("$name must not be capped", Modifier.heightIn(min = min, max = min), Modifier.growsWithText(min))
        }
        assertEquals(52.dp, CREATE_ALARM_MIN_HEIGHT)
        assertEquals(50.dp, FIRST_ALARM_MIN_HEIGHT)
        assertEquals(48.dp, TOP_BAR_MIN_HEIGHT)
        assertEquals(48.dp, APPLY_COORDINATES_MIN_HEIGHT)
    }

    @Test
    fun `the four screens set their button and bar heights through growsWithText with these constants (D97)`() {
        val kotlin = TestFiles.KOTLIN
        fun code(name: String) = TestFiles.require("$kotlin/$name").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)^\s*//.*$"""), "")
        for ((file, use) in listOf(
            "ui/alarms/RuleEditorScreen.kt" to "growsWithText(CREATE_ALARM_MIN_HEIGHT)",
            "ui/alarms/AlarmsScreen.kt" to "growsWithText(FIRST_ALARM_MIN_HEIGHT)",
            "ui/TopBar.kt" to "growsWithText(TOP_BAR_MIN_HEIGHT)",
            "ui/PlacePicker.kt" to "growsWithText(APPLY_COORDINATES_MIN_HEIGHT)",
        )) {
            val c = code(file)
            assertTrue("$file uses $use", c.contains(use))
            assertFalse("$file fixes a height of 48, 50 or 52 dp", Regex("""\.height\((48|50|52)\.dp\)""").containsMatchIn(c))
        }
    }

    /**
     * R9-F (D104, D105): the unit tests above prove the decision functions, and this test proves that the three
     * screens still take their branch from what those functions return. A call site changed to
     * `true || sunTilesSideBySide(...)` (or `return 84.dp` for the preview column) keeps every decision test
     * green, so the sources are read here, comments stripped and white space made single, for the call and for
     * the branch that uses its result.
     */
    @Test
    fun `the Sun tiles, the coordinate fields, the choice chips and the preview column take their form from the decision functions (D92, D94, D96)`() {
        val kotlin = TestFiles.KOTLIN
        fun code(name: String) = TestFiles.require("$kotlin/$name").readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)(?<!:)//.*$"""), "")
            .replace(Regex("""\s+"""), " ")
        val pins = listOf(
            "ui/sun/Results.kt" to listOf("val sideBySide = sunTilesSideBySide(", "if (sideBySide) {"),
            "ui/PlacePicker.kt" to listOf(
                "val fieldPx = coordinateFieldPx(constraints.maxWidth",
                "val sideBySide = coordinateFieldsSideBySide(",
                "if (sideBySide) {",
            ),
            "ui/alarms/RuleEditorScreen.kt" to listOf(
                "val equal = choiceChipsEqual(",
                "if (equal) {",
                "return previewLabelColumnDp(",
                "Modifier.width(previewLabelWidth())",
            ),
        )
        for ((file, wanted) in pins) {
            val c = code(file)
            for (w in wanted) assertTrue("$file holds `$w`", c.contains(w))
        }
        // No result of a decision function is overridden: nothing is OR-ed or AND-ed with the call, and no
        // branch is forced.
        val forced = Regex("""(true|false)\s*(\|\||&&)|(\|\||&&)\s*(true|false)\b|\bif \((true|false)\)""")
        for ((file, _) in pins) assertFalse("$file forces a layout branch", forced.containsMatchIn(code(file)))
        // The preview column's width is the decision function's result, never a literal.
        val rule = code("ui/alarms/RuleEditorScreen.kt")
        assertFalse("the preview column is a literal", Regex("""return \d+(\.\d+)?\.dp""").containsMatchIn(rule))
    }
}
