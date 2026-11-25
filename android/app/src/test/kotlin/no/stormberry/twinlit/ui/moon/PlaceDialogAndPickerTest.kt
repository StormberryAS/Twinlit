package no.stormberry.twinlit.ui.moon

import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.MoonLight
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MoonApp's `PlaceDialogAndPickerTest` (round 8, M1 and M4) on the Twinlit sources: a dialog's surface
 * colour is opaque in every Moon palette, and no name is ever refused. The picker part reads the shared
 * `ui/PlacePicker.kt` (R4). The rename dialog is gone since R5 (D14: the Moon tab's input part is the Sun
 * tab's). The colour is checked as a value; the rest are source checks, because no Compose UI test runs here.
 */
class PlaceDialogAndPickerTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    @Test
    fun `the surface colour of a dialog is fully opaque in every Moon palette`() {
        for (p in listOf(MoonNight, MoonDusk, MoonLight)) {
            assertEquals(1f, p.Surface.alpha, 0f)
            // The card colour is the see-through one the dialog must not have.
            assertTrue(p.Card.alpha < 1f)
        }
    }

    @Test
    fun `the picker field does not refuse a name`() {
        // Marcos, 2026-11-19: a name is free text, "it's up to the user".
        val picker = source("ui/PlacePicker.kt")
        assertFalse(picker.contains("nameProblem") || picker.contains("NAME_NEEDS_LETTER"))
        // The name is cleaned again when it is used by the picker.
        assertTrue(picker.contains("placeFromCoordinates(lat, lon, zone, nameText)"))
    }
}
