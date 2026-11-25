package no.stormberry.twinlit.ui

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text must never sit behind the status bar or the keyboard (SunApp 2.0.0, S10; adapted to the
 * Twinlit shell, SPEC 3.1). The cause was the window panning up (no soft-input mode set); the fix
 * is a source property, so it is pinned here, as MoonApp pins its M5. A unit test uses no emulator
 * or device, so the effect itself is checked by hand on one (milestone M1's smoke test).
 *
 * In Twinlit the keyboard has one more consequence: the bottom bar is hidden while it is open, so
 * a field near the bottom of a tab is never covered by the bar (D31).
 */
class KeyboardInsetsTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    @Test
    fun `the keyboard resizes the content and never pans the window`() {
        val text = source("MainActivity.kt")
        assertTrue(text.contains("window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)"))
        assertTrue(text.contains("import android.view.WindowManager"))
        assertFalse(text.contains("SOFT_INPUT_ADJUST_PAN"))
        val manifest = TestFiles.require("${TestFiles.MAIN}/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:windowSoftInputMode="adjustResize""""))
        assertFalse(manifest.contains("adjustPan"))
    }

    @Test
    fun `the tab body is shortened by the keyboard and the bottom bar gives way to it`() {
        val app = source("ui/App.kt")
        assertTrue(app.contains(".imePadding()"))
        assertTrue(app.contains("WindowInsets.isImeVisible"))
        assertTrue(app.contains("if (!imeVisible) {"))
        // The bar is the thing hidden, never the top bar or the tab.
        val hidden = app.substringAfter("if (!imeVisible) {").substringBefore("}")
        assertTrue(hidden.contains("BottomBar("))
        assertFalse(hidden.contains("TopBar("))
    }

    @Test
    fun `each bar takes its own system inset once`() {
        // The top bar takes the status bar inset, the Material navigation bar its own default
        // (the navigation bar inset); nothing pads for both bars twice.
        assertTrue(source("ui/TopBar.kt").contains(".statusBarsPadding()"))
        assertFalse(source("ui/TopBar.kt").contains("navigationBars"))
        val bottom = source("ui/BottomBar.kt")
        assertFalse(bottom.contains("statusBars"))
        assertFalse(bottom.contains("imePadding"))
    }

    @Test
    fun `the soft input mode is set after the window exists and before the content`() {
        val text = source("MainActivity.kt")
        val create = text.indexOf("super.onCreate(savedInstanceState)")
        val mode = text.indexOf("window.setSoftInputMode(")
        val content = text.indexOf("setContent {")
        assertTrue(create in 0 until mode && mode < content)
    }

    @Test
    fun `only the shared picker and the alarm editor host a text field, and only main activity hosts them`() {
        val withFields = TestFiles.require(TestFiles.KOTLIN).walk()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""\b(Outlined)?TextField\(""").containsMatchIn(it.readText()) }
            .map { it.name }.toSet()
        // The picker (city search, coordinates, name) is shown in the Sun and Moon tabs: one picker
        // (CityPicker, PlacePicker), in two palettes (R4); the Moon tab's rename dialog is gone (R5, D14).
        // The alarm editor (name and offset fields, with the picker's city search)
        // is the third host.
        assertTrue(
            withFields.toString(),
            withFields == setOf("CityPicker.kt", "PlacePicker.kt", "RuleEditorScreen.kt"),
        )
        // The tab bodies scroll, so the shortened window scrolls a focused field into sight.
        assertTrue(source("ui/sun/SunTimesScreen.kt").contains(".verticalScroll(rememberScrollState())"))
        // The Moon tab scrolls the same way (R4: a Column, no longer MoonApp's LazyColumn).
        assertTrue(source("ui/moon/MoonTimesScreen.kt").contains(".verticalScroll(rememberScrollState())"))
        assertFalse(source("ui/moon/MoonTimesScreen.kt").contains("LazyColumn("))
    }
}
