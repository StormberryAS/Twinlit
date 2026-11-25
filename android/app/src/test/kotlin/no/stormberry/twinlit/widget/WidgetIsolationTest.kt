package no.stormberry.twinlit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The widgets stay out of the alarm feature (1.1.9; both families since M6): no widget file may import
 * `no.stormberry.twinlit.alarm`, so the widgets need no permission and share no code with the
 * alarm runtime (they are the same in both editions since 2.0.0); and no widget component looks
 * like an alarm one.
 */
class WidgetIsolationTest {

    private fun sources(): List<File> {
        val dir = TestFiles.require("${TestFiles.KOTLIN}/widget")
        // Every file of both families and the shared infrastructure, subfolders included.
        val widget = dir.walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertEquals(20, widget.size)
        assertTrue(widget.any { it.parentFile.name == "sun" } && widget.any { it.parentFile.name == "moon" })
        val engines = listOf(
            "solar/PlaceDay.kt", "solar/SunDay.kt", "solar/SunCrossings.kt", "data/Settings.kt",
            "lunar/MoonCalc.kt", "lunar/MoonPhase.kt", "lunar/LunarDayKind.kt", "lunar/LunarEvent.kt",
        ).map { TestFiles.require("${TestFiles.KOTLIN}/$it") }
        return widget + engines
    }

    @Test
    fun `no widget source imports the alarm package or the alarm APIs`() {
        for (f in sources()) {
            val code = f.readText()
            assertFalse(f.name, code.contains("no.stormberry.twinlit.alarm"))
            for (call in listOf("setExact", "setAlarmClock", "AllowWhileIdle", "WAKE_LOCK", "WakeLock", "IgnoringBatteryOptimizations")) {
                assertFalse("${f.name}: $call", code.contains(call))
            }
            // No PowerManager: since 2.0.0 the widgets do not read Battery Saver (they never tick on Android 9 to 11).
            assertFalse("${f.name}: PowerManager", code.contains("PowerManager"))
        }
    }

    @Test
    fun `the widget receivers - not exported, the event receiver disabled, none direct boot aware, none named alarm`() {
        val manifest = TestFiles.require("${TestFiles.MAIN}/AndroidManifest.xml").readText()
        val receivers = Regex("""<receiver\s+android:name="(\.widget\.[A-Za-z.]+)"([^>]*)>""").findAll(manifest).toList()
        assertEquals(
            listOf(
                ".widget.sun.SunWidgetSmall", ".widget.sun.SunWidgetMedium", ".widget.sun.SunWidgetLarge",
                ".widget.moon.MoonWidgetSmall", ".widget.moon.MoonWidgetMedium", ".widget.moon.MoonWidgetLarge",
                ".widget.WidgetEventReceiver",
            ),
            receivers.map { it.groupValues[1] },
        )
        for (r in receivers) {
            val attrs = r.groupValues[2]
            assertFalse(r.groupValues[1], r.groupValues[1].contains("alarm", ignoreCase = true))
            assertTrue(r.groupValues[1], attrs.contains("android:exported=\"false\""))
            assertFalse(r.groupValues[1], attrs.contains("directBootAware"))
        }
        assertTrue(receivers.last().groupValues[2].contains("android:enabled=\"false\""))
        // Battery Saver changes reach run-time receivers only; a manifest entry would be dead.
        assertFalse(manifest.contains("POWER_SAVE_MODE_CHANGED"))
    }

    @Test
    fun `no source registers a receiver through the androidx shim, whose permission the manifest removes`() {
        val manifest = TestFiles.require("${TestFiles.MAIN}/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION\"\n        tools:node=\"remove\""))
        val all = TestFiles.require(TestFiles.KOTLIN).walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(all.size >= 20)
        for (f in all) {
            val code = f.readText()
            assertFalse(f.name, code.contains("ContextCompat.registerReceiver"))
            assertFalse(f.name, code.contains("RECEIVER_NOT_EXPORTED"))
        }
    }

    @Test
    fun `the main manifest gains no permission for the widgets`() {
        val manifest = TestFiles.require("${TestFiles.MAIN}/AndroidManifest.xml").readText()
            .replace(Regex("<!--[\\s\\S]*?-->"), "")
        val requested = Regex("""<uses-permission\s+android:name="([^"]+)"\s*(?:android:maxSdkVersion="\d+"\s*)?/>""")
            .findAll(manifest).map { it.groupValues[1] }.toSet()
        val expected = TestFiles.require("android/expected-permissions.txt").readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        assertEquals(expected, requested)
    }
}
