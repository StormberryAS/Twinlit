package no.stormberry.twinlit.ui.moon

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The typed text of the shared picker follows the place it belongs to (round R4, CHANGES-R4.md
 * section 4 and Critic R4, item 1). `rememberSaveable(place)` does not compare its inputs when
 * Compose restores a saved value, so after "type coordinates on the Sun tab, choose another place on
 * the Moon tab, go back" the Sun picker would show the OLD coordinates beside the NEW place, and
 * "Use these coordinates" would silently put the old place back. The fix is a source property: the
 * body of the picker sits inside `key(placeKey)`, and the fields are plain `rememberSaveable`.
 * The same check on a device is in the round R4 verification.
 */
class MoonPickerStateTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    /** Without comments, so a doc comment may name `rememberSaveable(place)` to say why it is not used. */
    private fun code(path: String) = source(path)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")

    @Test
    fun `the picker keeps its five fields in rememberSaveable inside key of the place`() {
        val picker = code("ui/PlacePicker.kt")
        assertFalse("no rememberSaveable(place)", picker.contains("rememberSaveable(place"))
        assertTrue(picker.contains("val placeKey = place?.let { \"\${it.latDeg},\${it.lonDeg},\${it.fromCatalogue},\${it.name}\" } ?: \"none\""))
        val body = picker.substringAfter("key(placeKey) {").substringBefore("/** The place a catalogue city makes")
        for (field in listOf("query", "latText", "lonText", "nameText", "coordinateError"))
            assertTrue("$field", Regex("""var $field by rememberSaveable \{""").containsMatchIn(body))
        // None of them lives outside the key block.
        val before = picker.substringBefore("key(placeKey) {")
        for (field in listOf("query", "latText", "lonText", "nameText", "coordinateError"))
            assertFalse("$field before the key", Regex("""var $field by""").containsMatchIn(before))
    }

    @Test
    fun `the root passes the one stored mode to both tabs`() {
        val app = source("ui/App.kt")
        assertEquals(
            "both tabs read mode and write it back",
            2, Regex("""mode = mode,\s+onModeChange = \{ mode = it; settings\.mode = it \}""").findAll(app).count(),
        )
        assertTrue(app.substringAfter("SunTimesScreen(").substringBefore("dateEpochDay").contains("mode = mode"))
        assertTrue(app.substringAfter("MoonTimesScreen(").substringBefore("dateEpochDay").contains("mode = mode"))
    }
}
