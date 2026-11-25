package no.stormberry.twinlit.ui

import no.stormberry.twinlit.theme.Appearance
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marcos approved these texts word for word (2026-11-17, "Perfect"): the menu title and the five
 * options. Not one character may change, and the order is the approved one. The expected strings
 * below are typed from TEXTS.md A-0 to A-5, not read from the code.
 */
class AppearanceTextsTest {

    @Test
    fun `the menu title is Appearance`() {
        assertEquals("Appearance", Appearance.TITLE)
    }

    @Test
    fun `the five options read exactly as approved, in the approved order`() {
        assertEquals(
            listOf(
                "Sun: light colours, easy to read in daylight.",
                "Moon: dark colours, gentle on the eyes at night.",
                "Twilight: a mix of both: dark, with warm accents.",
                "Auto: follows the sun's daily rhythm at your chosen place: Sun for the ten hours around solar noon, " +
                    "Twilight for two hours on each side, Moon for the rest.",
                "System: follows your phone's light or dark setting.",
            ),
            Appearance.entries.map { it.description },
        )
    }

    @Test
    fun `the word before the first colon is the option's name`() {
        assertEquals(listOf("Sun", "Moon", "Twilight", "Auto", "System"), Appearance.entries.map { it.label })
        assertEquals("light colours, easy to read in daylight.", Appearance.SUN.explanation)
    }

    @Test
    fun `no text has an em dash or a double hyphen, and all say your chosen place, never your location`() {
        for (a in Appearance.entries) {
            assertFalse(a.description, a.description.contains('—') || a.description.contains("--"))
            assertFalse(a.description, a.description.contains("location", ignoreCase = true))
        }
        assertTrue(Appearance.AUTO.description.contains("your chosen place"))
        assertFalse(Appearance.AUTO_NOTE.contains('—') || Appearance.AUTO_NOTE.contains("--"))
        assertEquals("No place chosen yet, so Auto follows System for now.", Appearance.AUTO_NOTE)
    }

    @Test
    fun `the sheet lists the options straight from the approved list and sets the title`() {
        val sheet = TestFiles.require("${TestFiles.KOTLIN}/ui/AppearanceSheet.kt").readText()
        assertTrue(sheet.contains("for (option in Appearance.entries)"))
        assertTrue(sheet.contains("text = Appearance.TITLE"))
        assertTrue(sheet.contains("selectable(selected = selected, role = Role.RadioButton"))
        // The Auto note shows only while no place is chosen, directly under the Auto row.
        assertTrue(sheet.contains("if (option == Appearance.AUTO && !hasPlace)"))
        // A choice applies at once and the sheet stays open: selecting does not dismiss.
        assertFalse(sheet.substringAfter("private fun OptionRow").contains("onDismiss"))
    }

    @Test
    fun `the top bar's button speaks the menu title`() {
        val bar = TestFiles.require("${TestFiles.KOTLIN}/ui/TopBar.kt").readText()
        assertTrue(bar.contains("contentDescription = Appearance.TITLE"))
        assertTrue(bar.contains("""text = "Twinlit""""))
    }
}
