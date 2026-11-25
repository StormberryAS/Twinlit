package no.stormberry.twinlit.ui.components

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertFalse
import org.junit.Test

/** R8 (D97): a button or a bar keeps a minimum height, and no label column is fixed at 84 dp. */
class LayoutSourceTest {

    @Test
    fun `the new layout code never fixes a height a larger font can outgrow, and no label takes a fixed width of 84 dp`() {
        val kotlin = TestFiles.KOTLIN
        for (name in listOf("ui/PlacePicker.kt", "ui/alarms/RuleEditorScreen.kt", "ui/alarms/AlarmsScreen.kt", "ui/TopBar.kt")) {
            val code = TestFiles.require("$kotlin/$name").readText()
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""(?m)^\s*//.*$"""), "")
            // A button or bar keeps a minimum height, so its text can wrap and grow it.
            assertFalse("$name has a fixed-height button", Regex("""Button\([^)]*\)\s*\{""").containsMatchIn(code) && Regex("""fillMaxWidth\(\)\.height\(\d+\.dp\)""").containsMatchIn(code))
            assertFalse("$name has a fixed 84 dp label column", code.contains(".width(84.dp)"))
        }
    }
}
