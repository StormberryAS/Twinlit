package no.stormberry.twinlit.ui

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status and navigation bars (SPEC 4.8). The default `enableEdgeToEdge()` picks the icon
 * colour from the phone's own mode, which is not the app's theme (it drew a dark clock on SunApp's
 * dark sky in light mode, SunApp 1.1.8). Twinlit styles the bars itself: transparent, with dark
 * icons when the chrome palette is light (the Sun theme) and light icons otherwise. The ring
 * screen keeps SunApp's always-dark form (milestone M5).
 */
class SystemBarsTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    @Test
    fun `a light chrome gives dark icons and a dark chrome gives light icons, both transparent`() {
        val helper = source("ui/SystemBars.kt")
        assertTrue(helper.contains("fun ComponentActivity.applySystemBars(isLight: Boolean)"))
        assertTrue(helper.contains("SystemBarStyle.light(Color.TRANSPARENT, NAV_BAR_DARK_SCRIM)"))
        assertFalse(helper.contains("SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)"))
        assertTrue(helper.contains("SystemBarStyle.dark(Color.TRANSPARENT)"))
        assertFalse(helper.contains("SystemBarStyle.auto"))
        assertTrue(helper.contains("statusBarStyle = barStyle(isLight)"))
        assertTrue(helper.contains("navigationBarStyle = barStyle(isLight)"))
    }

    @Test
    fun `the light style carries a visible dark scrim for API 24 and 25, where dark navigation icons do not exist`() {
        // Below API 26 androidx paints the navigation bar with the style's darkScrim. A transparent
        // one left white Back and Home buttons on the Sun theme's white bottom bar (1.37:1, fix
        // round 1). The scrim must be translucent but visible, and dark enough for white buttons.
        val argb = NAV_BAR_DARK_SCRIM
        val alpha = (argb ushr 24) and 0xFF
        assertTrue("scrim alpha $alpha", alpha in 0x60..0xC0)
        val over = { channel: Int -> ((argb shr channel) and 0xFF) * alpha / 255 + 255 * (255 - alpha) / 255 }
        // White buttons on the scrim over a white bar: at least 3:1 (WCAG 1.4.11, graphical objects).
        fun lin(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val l = 0.2126 * lin(over(16)) + 0.7152 * lin(over(8)) + 0.0722 * lin(over(0))
        val ratio = (1.0 + 0.05) / (l + 0.05)
        assertTrue("white buttons on the scrim over white: $ratio", ratio >= 3.0)
    }

    @Test
    fun `the always-dark form for the ring screen stays, with both bars dark`() {
        val helper = source("ui/SystemBars.kt")
        // One in barStyle and one for each bar of the dark helper.
        assertEquals(3, Regex("""SystemBarStyle\.dark\(Color\.TRANSPARENT\)""").findAll(helper).count())
        assertTrue(helper.contains("statusBarStyle = SystemBarStyle.dark"))
        assertTrue(helper.contains("navigationBarStyle = SystemBarStyle.dark"))
    }

    @Test
    fun `the main activity styles the bars from the chrome, and nothing calls the default style`() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("applySystemBars("))
        assertTrue(main.contains("onBarsLight"))
        assertTrue(source("ui/App.kt").contains("SideEffect { onBarsLight(chrome.isLight) }"))
        val all = TestFiles.require(TestFiles.KOTLIN).walk()
            .filter { it.isFile && it.extension == "kt" && it.name != "SystemBars.kt" }
        for (f in all) assertFalse(f.name, Regex("""\benableEdgeToEdge\(""").containsMatchIn(f.readText()))
    }

    @Test
    fun `the content stays clear of the bars`() {
        // The top bar pads for the status bar and the Material navigation bar for the navigation
        // bar, so no text is drawn behind a transparent bar.
        assertTrue(source("ui/TopBar.kt").contains(".statusBarsPadding()"))
        assertTrue(source("ui/BottomBar.kt").contains("NavigationBar("))
        assertFalse(source("ui/BottomBar.kt").contains("windowInsets = WindowInsets(0"))
    }

    @Test
    fun `a configuration change the activity handles itself styles the bars again from the cached lightness`() {
        // R4-F item 1. MainActivity lists orientation, screenSize, density, fontScale and uiMode in
        // configChanges, and the platform resets the icon appearance on such a change. The
        // barsLight cache stops the Compose callback from restyling them, so the activity must.
        val main = source("MainActivity.kt")
        val body = main.substringAfter("override fun onConfigurationChanged(newConfig: Configuration) {")
            .substringBefore("\n    }\n")
        assertTrue("onConfigurationChanged is missing", main.contains("override fun onConfigurationChanged(newConfig: Configuration)"))
        assertTrue(main.contains("import android.content.res.Configuration"))
        // After super, from the cache, never forced to a default lightness.
        assertTrue(body.indexOf("super.onConfigurationChanged(newConfig)") in 0 until body.indexOf("applySystemBars(barsLight ?: false)"))
        // And once more after the platform's own relayout (API 36 reset the bars after this call).
        assertTrue(body.contains("window.decorView.post { applySystemBars(barsLight ?: false) }"))
        // The cache itself stays: the Compose callback does not restyle on every composition.
        assertTrue(main.contains("if (barsLight != light) {"))
        val manifest = TestFiles.require("${TestFiles.MAIN}/AndroidManifest.xml").readText()
        for (token in listOf("orientation", "fontScale", "density", "uiMode")) {
            assertTrue("manifest configChanges lacks $token", manifest.contains(token))
        }
    }
}
