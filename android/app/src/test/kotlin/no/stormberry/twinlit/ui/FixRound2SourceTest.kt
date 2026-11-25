package no.stormberry.twinlit.ui

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source checks for the UI defects of fix round 2 that a JVM test cannot render. Each one names the
 * defect it pins; the layout itself is looked at on an emulator (verify/r2fix).
 */
class FixRound2SourceTest {

    private fun src(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    @Test
    fun `the Appearance sheet scrolls and keeps its bar icons in step with the chrome`() {
        val sheet = src("ui/AppearanceSheet.kt")
        assertTrue(sheet.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(sheet.contains("isAppearanceLightStatusBars = chrome.isLight"))
        assertTrue(sheet.contains("isAppearanceLightNavigationBars = chrome.isLight"))
        assertTrue(sheet.contains("DialogWindowProvider"))
    }

    @Test
    fun `the tab box clips its sky glow and the root pads for the display cutout`() {
        val app = src("ui/App.kt")
        assertTrue(app.contains("Modifier.weight(1f).clipToBounds().imePadding()"))
        assertTrue(app.contains("WindowInsets.displayCutout"))
        assertTrue(app.contains(".windowInsetsPadding(sideInsets)"))
        assertTrue(app.contains(".consumeWindowInsets(sideInsets)"))
    }

    @Test
    fun `the Alarms tab draws the twilight sky`() {
        val alarms = src("ui/alarms/AlarmsScreen.kt")
        assertTrue(alarms.contains("drawSkyGlow(sky.GlowWarm, sky.GlowGold, sky.GlowCool)"))
        assertTrue(alarms.contains("A 30-second clock."))
        assertFalse(alarms.contains("A minute-resolution clock"))
    }

    @Test
    fun `the date arrows of the shared bar are 48 dp targets named by their label`() {
        val bar = src("ui/components/DateBar.kt")
        val arrow = bar.substringAfter("private fun DateArrow").substringBefore("@OptIn")
        assertTrue(arrow.contains(".size(48.dp)"))
        assertTrue(arrow.contains("contentDescription = label"))
        assertTrue(arrow.contains("role = Role.Button"))
        assertTrue(arrow.contains("clearAndSetSemantics"))
        // The Sun tab has no step button of its own any more.
        assertFalse(src("ui/sun/SunTimesScreen.kt").contains("StepButton"))
    }

    @Test
    fun `the glyph clear buttons are 48 dp targets with a spoken name`() {
        for ((file, names) in listOf(
            "ui/CityPicker.kt" to listOf("Clear the search text", "Clear the selected place"),
            "ui/alarms/RuleEditorScreen.kt" to listOf("Clear $" + "label"),
        )) {
            val text = src(file)
            assertTrue("$file minimum size", text.contains("minimumInteractiveComponentSize()"))
            for (name in names) assertTrue("$file $name", text.contains("contentDescription = \"$name\""))
            // The old 12 dp padding around a bare glyph is gone from the clear buttons.
            assertFalse("$file", Regex("onClear\\(\\) \\}\\s*\\.padding\\(12\\.dp\\)").containsMatchIn(text))
        }
    }

    @Test
    fun `the Moon tab footer keeps MoonApp's two text colours`() {
        val moon = src("ui/moon/MoonTimesScreen.kt")
        assertTrue(moon.contains("textSecondary = Moon.TextSoft"))
        assertTrue(moon.contains("smallPrint = Moon.TextMutedReadable"))
        val footer = src("ui/components/Footer.kt")
        assertTrue(footer.contains("val smallPrint: Color = textSecondary"))
    }
}
