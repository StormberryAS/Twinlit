package no.stormberry.twinlit.ui

import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a place is applied (a city or typed coordinates) the picker lets go of the focus AND hides
 * the keyboard (MoonApp 2.0.0 M10, `PickerFocusTest`; SunApp only cleared the focus, in its screen).
 * Twinlit needs the hide in both looks: the bottom bar stays hidden while the keyboard is open
 * (D31), so a keyboard that stays open also keeps the navigation away. No Compose UI test runs in
 * this project: the order of the two steps is a pure function and the apply points are source
 * checks. One picker serves both tabs now (`PlacePicker`, R4).
 *
 * Manual check (emulator, milestone M1): type two letters in the search field, tap a city; the
 * keyboard closes, the field shows no cursor and the bottom bar is back. Type coordinates and tap
 * "Use these coordinates"; the same.
 */
class PickerFocusTest {

    private fun picker() = TestFiles.require("${TestFiles.KOTLIN}/ui/PlacePicker.kt").readText()
    private fun moonScreen() = TestFiles.require("${TestFiles.KOTLIN}/ui/moon/MoonTimesScreen.kt").readText()

    private val oslo = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)

    @Test
    fun `the focus and keyboard are released once, before the place is handed on`() {
        val steps = mutableListOf<String>()
        applyChosenPlace(oslo, { steps += "release" }) { steps += "chosen:${it.label}" }
        assertEquals(listOf("release", "chosen:Oslo, Norway"), steps)
    }

    @Test
    fun `the place is handed on unchanged`() {
        var got: Place? = null
        applyChosenPlace(oslo, {}) { got = it }
        assertEquals(oslo, got)
    }

    @Test
    fun `the picker releases the focus and hides the keyboard through the one shared helper`() {
        assertTrue(picker().contains("val release = rememberPlaceRelease()"))
        val helper = TestFiles.require("${TestFiles.KOTLIN}/ui/PlaceFocus.kt").readText()
        assertTrue(helper.contains("LocalFocusManager.current"))
        assertTrue(helper.contains("LocalSoftwareKeyboardController.current"))
                assertTrue(helper.contains("keyboard?.hide()"))
    }

    @Test
    fun `before API 30 the input method is also told to hide, and the focus goes to a non-text target first`() {
        val helper = TestFiles.require("${TestFiles.KOTLIN}/ui/PlaceFocus.kt").readText()
        assertTrue(helper.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.R"))
        assertTrue(helper.contains("hideSoftInputFromWindow(view.windowToken, 0)"))
        // The focus sink: on API 24 a plain clear is undone within milliseconds (the field gets the
        // focus back and reopens the keyboard), so the focus is moved to a node that is not a field.
        assertTrue(helper.contains("val LocalFocusSink"))
        assertTrue(helper.contains("sink.requestFocus()"))
        assertTrue(helper.contains("focusManager.clearFocus(force = true)"))
        val app = TestFiles.require("${TestFiles.KOTLIN}/ui/App.kt").readText()
        assertTrue(app.contains("LocalFocusSink provides focusSink"))
        assertTrue(app.contains(".focusRequester(focusSink)"))
        val steps = mutableListOf<String>()
        releaseFocusAndKeyboard({ steps += "focus" }, { steps += "hide" }) { steps += "legacy" }
        assertEquals(listOf("focus", "hide", "legacy"), steps)
    }

    @Test
    fun `both apply points go through applyChosenPlace and none calls onPlaceChange directly`() {
        val body = picker().substringAfter("fun PlacePicker(").substringBefore("/** The place a catalogue city makes")
        assertEquals("a city and typed coordinates", 2, Regex("""applyChosenPlace\(""").findAll(body).count())
        // The only other mention of the callback is clearing the selection and the call inside the helper's lambda.
        assertEquals(1, Regex("""\bonPlaceChange\(null\)""").findAll(body).count())
        assertFalse(body.contains("onPlaceChange(placeFrom"))
    }

    // ---- The Moon tab (R4): the same picker, in the Moon palette ----

    @Test
    fun `the Moon tab uses the shared picker and has no search or parser of its own`() {
        val text = moonScreen()
        assertTrue(text.contains("PlacePicker("))
        assertFalse(text.contains("CitySearch."))
        assertFalse(text.contains("parseCoordinate"))
        assertFalse(text.contains("applyChosenPlace"))
        assertFalse(TestFiles.require("${TestFiles.KOTLIN}/ui/moon").resolve("MoonPlacePicker.kt").exists())
    }

    @Test
    fun `the focus is released before a typed place is handed on, whichever the place`() {
        val steps = mutableListOf<String>()
        val typed = Place("59.9139 N, 10.7522 E", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = false, name = "Home")
        applyChosenPlace(typed, { steps += "release" }) { steps += "chosen:${it.name}" }
        assertEquals(listOf("release", "chosen:Home"), steps)
    }
}
