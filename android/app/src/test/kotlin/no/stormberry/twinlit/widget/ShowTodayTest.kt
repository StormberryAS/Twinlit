package no.stormberry.twinlit.widget

import android.app.PendingIntent
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget tap (SPEC 8.1 and 8.2), the part that runs on the JVM: SunApp's `ShowTodayTest` for two
 * actions and two request codes. The tap itself is an emulator check (build-notes/M6.md).
 */
class ShowTodayTest {

    @Test
    fun `two explicit actions of the app's own, the three activity flags, immutable`() {
        assertEquals("no.stormberry.twinlit.action.SHOW_SUN_TODAY", ShowToday.ACTION_SUN)
        assertEquals("no.stormberry.twinlit.action.SHOW_MOON_TODAY", ShowToday.ACTION_MOON)
        assertEquals(ShowToday.ACTION_SUN, ShowToday.sun.action)
        assertEquals(ShowToday.ACTION_MOON, ShowToday.moon.action)
        assertNotEquals(ShowToday.ACTION_SUN, ShowToday.ACTION_MOON)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ShowToday.ACTIVITY_FLAGS,
        )
        assertTrue(ShowToday.PENDING_FLAGS and PendingIntent.FLAG_IMMUTABLE != 0)
        assertTrue(ShowToday.PENDING_FLAGS and PendingIntent.FLAG_UPDATE_CURRENT != 0)
    }

    @Test
    fun `the two taps have distinct request codes, 7 for sun and 8 for moon`() {
        assertEquals(7, ShowToday.REQUEST_CODE_SUN)
        assertEquals(8, ShowToday.REQUEST_CODE_MOON)
        assertEquals(7, ShowToday.sun.requestCode)
        assertEquals(8, ShowToday.moon.requestCode)
    }

    @Test
    fun `the tap carries no extras, and MainActivity serves both actions and refreshes the widgets on every resume`() {
        val src = TestFiles.require("${TestFiles.KOTLIN}/widget/ShowToday.kt").readText()
        assertFalse(src.contains("putExtra"))
        assertTrue(src.contains("Intent(context, MainActivity::class.java)"))
        val main = TestFiles.require("${TestFiles.KOTLIN}/MainActivity.kt").readText()
        assertTrue(main.contains("override fun onNewIntent(intent: Intent)"))
        assertTrue(main.contains("ShowToday.ACTION_SUN"))
        assertTrue(main.contains("ShowToday.ACTION_MOON"))
        assertTrue(main.contains("FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY"))
        // The widget refresh on resume is unconditional (no edition switch exists).
        val resume = main.substringAfter("override fun onResume()").substringBefore("\n    }\n")
        val refresh = resume.lines().first { it.contains("WidgetRefresh.refreshAllAsync") }
        assertFalse(refresh, refresh.contains("if "))
    }

    @Test
    fun `the root resets a tab's date only when a request arrives, never when the tab is composed again`() {
        val app = TestFiles.require("${TestFiles.KOTLIN}/ui/App.kt").readText()
        // Keyed on the count of requests, guarded by "> 0": a recomposition or a visit to another
        // tab never runs the reset (the reset lives in the root, not in the tab).
        val sun = app.substringAfter("LaunchedEffect(sunTodayRequests) {").substringBefore("\n    }\n")
        assertTrue(sun.contains("if (sunTodayRequests > 0)"))
        assertTrue(sun.contains("tab = Tab.SUN"))
        assertTrue(sun.contains("sunEpochDay = PlaceDay.today("))
        val moon = app.substringAfter("LaunchedEffect(moonTodayRequests) {").substringBefore("\n    }\n")
        assertTrue(moon.contains("if (moonTodayRequests > 0)"))
        assertTrue(moon.contains("tab = Tab.MOON"))
        assertTrue(moon.contains("moonEpochDay = PlaceDay.today("))
        // And the screens never write the date by themselves at composition.
        for (screen in listOf("ui/sun/SunTimesScreen.kt", "ui/moon/MoonTimesScreen.kt")) {
            val text = TestFiles.require("${TestFiles.KOTLIN}/$screen").readText()
            assertFalse(screen, text.contains("sunEpochDay =") || text.contains("moonEpochDay ="))
        }
    }

    @Test
    fun `every place change redraws both widget families`() {
        val app = TestFiles.require("${TestFiles.KOTLIN}/ui/App.kt").readText()
        val change = app.substringAfter("fun changePlace(next: Place?) {").substringBefore("\n    }\n")
        assertTrue(change.contains("settings.place = next"))
        assertTrue(change.contains("WidgetRefresh.refreshAllAsync(context)"))
    }
}
