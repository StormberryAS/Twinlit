package no.stormberry.twinlit.ui.alarms

import no.stormberry.twinlit.alarm.OccurrenceEngine
import no.stormberry.twinlit.alarm.testRule
import no.stormberry.twinlit.alarm.testMoonRule
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Fix round 2: the editor preview starts at the next fire time, says what is wrong with the offset, and is keyed without the label. */
class EditorPreviewNowTest {

    private val oslo = ZoneId.of("Europe/Oslo")

    @Test
    fun `a sun preview opened after today's sunrise starts with the next fire time, as the list does`() {
        val rule = testRule()
        val today = LocalDate.of(2026, 11, 20)
        val calendar = editorPreview(rule, today, oslo)
        val todayRow = calendar.rows.first().occurrence!!
        assertEquals(today, todayRow.anchorDate)
        val now = todayRow.fireAt.plusSeconds(3600)
        val preview = editorPreview(rule, today, oslo, now)
        assertEquals(30, preview.rows.size)
        assertEquals(today.plusDays(1), preview.rows.first().occurrence!!.anchorDate)
        assertEquals(OccurrenceEngine.nextOccurrence(rule, now), preview.rows.first().occurrence)
        assertEquals(preview.summary.rows.first(), preview.rows.first().occurrence)
        // Before today's sunrise nothing changes.
        val early = editorPreview(rule, today, oslo, todayRow.fireAt.minusSeconds(3600))
        assertEquals(calendar.rows.map { it.occurrence }, early.rows.map { it.occurrence })
    }

    @Test
    fun `an offset that carries a fire time onto a later day keeps the row of the earlier anchor`() {
        // Sunset plus eight hours anchors on one day and rings on the next morning.
        val rule = testRule(anchor = no.stormberry.twinlit.alarm.model.Anchor.Sun(no.stormberry.twinlit.solar.SolarEvent.SUNSET), direction = Direction.AFTER, offsetMinutes = 480)
        val today = LocalDate.of(2026, 6, 21)
        val yesterdayRow = OccurrenceEngine.occurrenceFor(rule, today.minusDays(1))!!
        val now = yesterdayRow.fireAt.minusSeconds(600)
        val preview = editorPreview(rule, today, oslo, now)
        assertEquals(yesterdayRow, preview.rows.first().occurrence)
        assertEquals(30, preview.rows.size)
    }

    @Test
    fun `a daily moon preview and a monthly one start after now too`() {
        val moonrise = testMoonRule(event = LunarEvent.MOONRISE)
        val today = LocalDate.of(2026, 11, 20)
        val first = OccurrenceEngine.occurrenceFor(moonrise, today)!!
        val preview = editorPreview(moonrise, today, oslo, first.fireAt.plusSeconds(60))
        assertEquals(30, preview.rows.size)
        assertTrue(preview.rows.flatMap { it.occurrences }.all { it.fireAt.isAfter(first.fireAt) })
        val full = testMoonRule(event = LunarEvent.FULL_MOON)
        val night = OccurrenceEngine.nextNights(full, LocalDate.of(2026, 11, 20), 1).single()
        val after = editorPreview(full, night.anchorDate, oslo, night.fireAt.plusSeconds(60))
        assertEquals(3, after.rows.size)
        assertTrue(after.rows.first().occurrence!!.fireAt.isAfter(night.fireAt))
    }

    @Test
    fun `an invalid offset is named in the timing line and in the empty preview`() {
        assertEquals("Fix the offset to see the timing.", TIMING_INVALID_OFFSET)
        assertTrue(previewPlaceholder(true).startsWith("Fix the offset above and the next 30 fire times appear here"))
        assertTrue(previewPlaceholder(false).startsWith("Choose a city and the next 30 fire times appear here"))
    }

    @Test
    fun `the preview is keyed on the rule without its label`() {
        val src = TestFiles.require("${TestFiles.KOTLIN}/ui/alarms/RuleEditorScreen.kt").readText()
        assertTrue(src.contains("rule?.copy(label = \"\")"))
        assertTrue(src.contains("remember(previewKey)"))
    }
}
