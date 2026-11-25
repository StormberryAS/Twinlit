package no.stormberry.twinlit.ui

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The name field of the Coordinates half of the shared place picker (SunApp 2.0.0): its help says what the app does, and the name is
 * free text: the field never refuses a name, whatever it looks like (owner's decision, 2026-11-19).
 */
class NameFieldTest {

    private val screen: String by lazy { TestFiles.require("${TestFiles.KOTLIN}/ui/PlacePicker.kt").readText() }

    @Test
    fun `the help under the field is short and says where the name is shown`() {
        assertEquals(
            "Shown instead of Your coordinates in the app, on the widgets and in the alarms. " +
                "Up to 20 characters; leave empty for no name.",
            NAME_HELP,
        )
        assertTrue(screen.contains("text = NAME_HELP"))
        assertFalse(screen.contains("instead of the coordinates"))
    }

    @Test
    fun `the field refuses nothing, and the name is typed through PlaceName only`() {
        assertFalse(screen.contains("nameProblem"))
        assertFalse(screen.contains("isError"))
        assertFalse(screen.contains("supportingText"))
        assertFalse(screen.contains("Add a letter"))
        assertFalse(screen.contains("Leave coordinates out"))
        // The button builds the place from the name as sanitised, with no check before it.
        assertTrue(screen.contains("name = PlaceName.sanitise(name)"))
        assertTrue(screen.contains("placeFromCoordinates(lat, lon, zone, nameText)"))
        // The name is typed through PlaceName.typing only: nothing else rewrites what the user typed.
        assertTrue(screen.contains("onNameChange = { nameText = PlaceName.typing(it) }"))
        // The prefill reads the stored name as it is: no guessing on the string.
        assertTrue(screen.contains("PlaceDisplay.givenName(it)"))
    }
}
