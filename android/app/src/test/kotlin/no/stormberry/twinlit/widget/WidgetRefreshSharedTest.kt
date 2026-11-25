package no.stormberry.twinlit.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared widget infrastructure (SPEC 8.3): ONE event receiver, ONE refresh entry point for both
 * families, one refresh alarm and the Sun handover, with distinct actions and request codes. The
 * parts that run on a phone (what the refresh draws, the alarms in `dumpsys alarm`) are the M6
 * emulator checks; this class pins the structure the JVM can see, from the source.
 */
class WidgetRefreshSharedTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    private val refresh = source("widget/WidgetRefresh.kt")
    private val receiver = source("widget/WidgetEventReceiver.kt")

    @Test
    fun `the actions and the request codes are distinct and of the app`() {
        assertEquals("no.stormberry.twinlit.widget.REFRESH", WidgetRefresh.ACTION_REFRESH)
        assertEquals("no.stormberry.twinlit.widget.HANDOVER", WidgetRefresh.ACTION_HANDOVER)
        assertEquals(0x5B10, WidgetRefresh.REQ_REFRESH)
        assertEquals(0x5B12, WidgetRefresh.REQ_HANDOVER)
        val taps = listOf(ShowToday.REQUEST_CODE_SUN, ShowToday.REQUEST_CODE_MOON)
        val all = taps + listOf(WidgetRefresh.REQ_REFRESH, WidgetRefresh.REQ_HANDOVER)
        assertEquals("four PendingIntents, four codes", 4, all.toSet().size)
        assertNotEquals(WidgetRefresh.ACTION_REFRESH, WidgetRefresh.ACTION_HANDOVER)
        // SunApp's and MoonApp's own actions did not survive the merge.
        assertFalse(refresh.contains("sunapp"))
        assertFalse(refresh.contains("moonapp"))
    }

    @Test
    fun `one receiver serves both families and both alarms, and nothing else`() {
        for (action in listOf(
            "Intent.ACTION_TIME_CHANGED", "Intent.ACTION_TIMEZONE_CHANGED", "Intent.ACTION_LOCALE_CHANGED",
            "Intent.ACTION_MY_PACKAGE_REPLACED", "WidgetRefresh.ACTION_REFRESH", "WidgetRefresh.ACTION_HANDOVER",
        )) assertTrue(action, receiver.contains(action))
        assertTrue(receiver.contains("WidgetRefresh.refreshInBackground(this, context)"))
        assertTrue(receiver.contains("else -> Unit"))
        // Neither family keeps a receiver of its own.
        val files = TestFiles.require("${TestFiles.KOTLIN}/widget").walk().filter { it.isFile && it.extension == "kt" }.toList()
        assertEquals(
            listOf("WidgetEventReceiver.kt"),
            files.filter { it.readText().contains(": BroadcastReceiver()") }.map { it.name },
        )
    }

    @Test
    fun `one entry point redraws both families under one lock, and the providers use it`() {
        assertEquals(1, Regex("""private val lock = Any\(\)""").findAll(refresh).count())
        assertTrue(refresh.contains("SunWidgets.render(context, mgr, place)"))
        assertTrue(refresh.contains("MoonWidgets.render(context, mgr, place)"))
        // Each family is drawn only if it has a widget placed.
        assertTrue(refresh.contains("if (sunPlaced) SunWidgets.render"))
        assertTrue(refresh.contains("if (moonPlaced) MoonWidgets.render"))
        // The one place is the app's own, read the way the alarm service reads it (null when unreadable).
        assertTrue(refresh.contains("Settings.placeOrNull(context)"))
        // The family objects own no lock, no alarm and no receiver switch any more.
        for (family in listOf("sun/SunWidgets.kt", "moon/MoonWidgets.kt")) {
            // Code only: the KDoc still says where the alarms went.
            val text = source("widget/$family").replace(Regex("/\\*[\\s\\S]*?\\*/"), "").replace(Regex("//.*"), "")
            for (gone in listOf("AlarmManager", "setWindow", "goAsync", "synchronized", "setComponentEnabledSetting", "Thread(")) {
                assertFalse("$family: $gone", text.contains(gone))
            }
        }
        for (provider in listOf("sun/SunWidgetProvider.kt", "moon/MoonWidgetProvider.kt")) {
            val text = source("widget/$provider")
            assertTrue(provider, text.contains("WidgetRefresh.refreshInBackground(this, context)"))
            assertTrue(provider, text.contains("WidgetRefresh.setEventReceiver(context, true)"))
            assertTrue(provider, text.contains("WidgetRefresh.cancelIfNone(context)"))
        }
    }

    @Test
    fun `the receiver is on while either family has a widget and off when none is, and cancelIfNone checks both`() {
        assertTrue(refresh.contains("val sunPlaced = SunWidgets.hasAny(context)"))
        assertTrue(refresh.contains("val moonPlaced = MoonWidgets.hasAny(context)"))
        assertTrue(refresh.contains("if (!sunPlaced && !moonPlaced) {"))
        assertTrue(refresh.contains("fun hasAny(context: Context): Boolean = SunWidgets.hasAny(context) || MoonWidgets.hasAny(context)"))
        val cancel = refresh.substringAfter("fun cancelIfNone(context: Context) {").substringBefore("\n    }\n")
        assertTrue(cancel.contains("if (!hasAny(context))"))
        assertTrue(cancel.contains("setEventReceiver(context, false)"))
        // On before the alarms are armed, because the alarms are delivered to it.
        val all = refresh.substringAfter("fun refreshAll(context: Context)")
        assertTrue(all.indexOf("setEventReceiver(context, true)") in 0 until all.indexOf("arm(context, refresh"))
    }

    @Test
    fun `the refresh alarm is inexact RTC with a window and the handover is RTC_WAKEUP, neither exact`() {
        assertTrue(refresh.contains("am.setWindow(AlarmManager.RTC, refresh.toEpochMilli(), window,"))
        assertTrue(refresh.contains("am.setWindow(AlarmManager.RTC_WAKEUP, handover.toEpochMilli(), window, handoverIntent)"))
        assertTrue(refresh.contains("am.cancel(handoverIntent)"))
        assertFalse(refresh.contains("setExact"))
        assertFalse(refresh.contains("setAlarmClock"))
        // Both alarms go to the receiver by explicit component, so no filter can match a user's alarm.
        assertTrue(refresh.contains("Intent(action).setClass(context, WidgetEventReceiver::class.java)"))
        // The refresh is due at the earlier of the two families' times; the handover is the Sun's only.
        assertTrue(refresh.contains("val refresh = listOfNotNull(sunPlan?.refresh, moonNext).min()"))
        assertTrue(refresh.contains("arm(context, refresh, sunPlan?.handover)"))
    }

    @Test
    fun `a missing place gives both families their placeholder, never a default city`() {
        val moon = source("widget/moon/MoonWidgets.kt")
        assertTrue(moon.contains("if (place == null) {"))
        assertTrue(moon.contains("WidgetText.CHOOSE_PLACE"))
        assertFalse(moon.contains("Oslo"))
        val sun = source("widget/sun/WidgetModel.kt")
        assertTrue(sun.contains("if (place == null) return null"))
    }
}
