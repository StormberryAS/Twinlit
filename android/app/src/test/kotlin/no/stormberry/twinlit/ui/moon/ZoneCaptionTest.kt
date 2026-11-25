package no.stormberry.twinlit.ui.moon

import no.stormberry.twinlit.ui.components.EDITOR_ZONE_LEAD
import no.stormberry.twinlit.ui.components.MOON_ZONE_LEAD
import no.stormberry.twinlit.ui.components.zoneCaptionPieces
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * R9 (D102): the Moon tab caption "Times shown in {zone}." has the same words as ever, cut into two pieces, so the
 * id never breaks at its slash while it fits a line. R13 (D138): the alarm editor's caption "Times computed and shown
 * in {zone}." (TEXTS G-20) is cut the same way, by the same component.
 */
class ZoneCaptionTest {

    @Test
    fun `the two pieces read as the caption of TEXTS E-26, and the zone id is a piece of its own`() {
        val p = zoneCaptionPieces(MOON_ZONE_LEAD, "Europe/Oslo")
        assertEquals("Times shown in", p.lead)
        assertEquals("Europe/Oslo.", p.zone)
        assertEquals("Times shown in Europe/Oslo.", "${p.lead} ${p.zone}")
        // Every id of the zone database keeps the same words: the lead, a space, the id and a full stop.
        for (id in ZoneId.getAvailableZoneIds()) {
            val q = zoneCaptionPieces(MOON_ZONE_LEAD, id)
            assertEquals("Times shown in $id.", "${q.lead} ${q.zone}")
            assertFalse(q.lead.contains('/'))
        }
    }

    @Test
    fun `the alarm editor caption has the words of TEXTS G-20 in two pieces`() {
        val p = zoneCaptionPieces(EDITOR_ZONE_LEAD, "Europe/Oslo")
        assertEquals("Times computed and shown in", p.lead)
        assertEquals("Europe/Oslo.", p.zone)
        assertEquals("Times computed and shown in Europe/Oslo.", "${p.lead} ${p.zone}")
        for (id in ZoneId.getAvailableZoneIds()) {
            val q = zoneCaptionPieces(EDITOR_ZONE_LEAD, id)
            assertEquals("Times computed and shown in $id.", "${q.lead} ${q.zone}")
            assertFalse(q.lead.contains('/'))
        }
    }

    @Test
    fun `the editor and the Moon tab use the one ZoneCaption component and no caption is a single Text any more`() {
        fun src(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()
        val editor = src("ui/alarms/RuleEditorScreen.kt")
        assertTrue(editor.contains("ZoneCaption(EDITOR_ZONE_LEAD, place.zoneId,"))
        assertFalse("the caption is one Text again", editor.contains("Times computed and shown in \${"))
        assertFalse(editor.contains("\"Times computed and shown in"))
        val moon = src("ui/moon/MoonTimesScreen.kt")
        assertTrue(moon.contains("ZoneCaption(MOON_ZONE_LEAD, zone.id,"))
        assertFalse(moon.contains("private fun ZoneCaption"))
        val component = src("ui/components/ZoneCaption.kt")
        assertTrue(component.contains("const val MOON_ZONE_LEAD = \"Times shown in\""))
        assertTrue(component.contains("const val EDITOR_ZONE_LEAD = \"Times computed and shown in\""))
        assertTrue(component.contains("internal fun zoneCaptionPieces(lead: String, zoneId: String) = ZoneCaptionPieces(lead, \"\$zoneId.\")"))
        assertTrue(component.contains("FlowRow("))
        assertTrue(component.contains("semantics(mergeDescendants = true)"))
    }
}
