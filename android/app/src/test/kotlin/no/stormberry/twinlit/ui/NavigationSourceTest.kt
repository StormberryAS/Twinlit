package no.stormberry.twinlit.ui

import no.stormberry.twinlit.ui.components.MOON_CHIP_FRACTION
import no.stormberry.twinlit.ui.theme.Tab
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The navigation shell (SPEC 3.1 and 14.2): the bottom bar's three items and words, the original
 * icon drawables (BRIEF, Marcos 2026-11-19: "Moon tab should keep moon icon, sun tab with sun
 * icon"), since M5 the SHOW_ALARMS intent part and since M6 the SHOW_SUN_TODAY and SHOW_MOON_TODAY
 * parts (the widget taps, SPEC 3.2).
 */
class NavigationSourceTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    private fun sha256(path: String): String {
        val bytes = TestFiles.require("${TestFiles.RES}/$path").readBytes()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `the bar has exactly three tabs, Sun, Moon and Alarms, in that order`() {
        assertEquals(listOf(Tab.SUN, Tab.MOON, Tab.ALARMS), Tab.entries)
        assertEquals(listOf("Sun", "Moon", "Alarms"), Tab.entries.map { tabLabel(it) })
        val bar = source("ui/BottomBar.kt")
        // One NavigationBarItem, built for each entry of the enum: no fourth item can hide in the file.
        assertEquals(1, Regex("""NavigationBarItem\(""").findAll(bar).count())
        assertTrue(bar.contains("for (tab in Tab.entries)"))
        assertTrue(bar.contains("alwaysShowLabel = true"))
        assertTrue(bar.contains("NavigationBar("))
    }

    @Test
    fun `the sun mark is SunApp's ic_app_logo and the moon mark is MoonApp's, byte for byte`() {
        // git show 2a26f98:android/app/src/main/res/drawable/ic_app_logo.xml (SunApp)
        assertEquals("e18531e6c3f2000cf8082edf45d58acf0f862bdf3831f13da547c29704dae9ee", sha256("drawable/ic_sun_mark.xml"))
        // git show 68c7596:android/app/src/main/res/drawable/ic_app_logo.xml (MoonApp)
        assertEquals("cb5a7690de9ad807f9e417d8a1c72bc4d435eb7e02c416327c95e811a19a9d36", sha256("drawable/ic_moon_mark.xml"))
    }

    @Test
    fun `the Sun tab draws the sun mark untinted and the Alarms tab a tinted clock`() {
        val bar = source("ui/BottomBar.kt")
        assertTrue(bar.contains("Tab.SUN -> Image("))
        assertTrue(bar.contains("painterResource(R.drawable.ic_sun_mark)"))
        assertTrue(bar.contains("Tab.ALARMS -> Box("))
        assertTrue(bar.contains("Icon(\n                                    painter = painterResource(R.drawable.ic_tab_alarm)"))
        assertTrue(bar.contains("painterResource(R.drawable.ic_tab_alarm)"))
        // The two marks keep their own colours: they are drawn with Image, never tinted.
        assertFalse(bar.contains("ic_sun_mark), contentDescription = null, tint"))
        // The icons are decorative: the label names the tab. The moon mark's own Image says so too.
        assertEquals(2, Regex("""contentDescription = null""").findAll(bar).count())
        assertEquals(1, Regex("""contentDescription = null""").findAll(source("ui/components/MoonMark.kt")).count())
    }

    @Test
    fun `every place that draws the moon mark draws it through MoonMark on the night chip`() {
        val offenders = TestFiles.require(TestFiles.KOTLIN).walk()
            .filter { it.isFile && it.extension == "kt" && it.name != "MoonMark.kt" }
            .filter { it.readText().contains("R.drawable.ic_moon_mark") }
            .map { it.name }.toList()
        assertTrue("drawn outside MoonMark: $offenders", offenders.isEmpty())
        val mark = source("ui/components/MoonMark.kt")
        assertTrue(mark.contains("R.drawable.ic_moon_mark"))
        assertTrue(mark.contains("val MoonChipColour = Color(0xFF050810)"))
        assertTrue(source("ui/BottomBar.kt").contains("Tab.MOON -> MoonMark("))
    }

    @Test
    fun `the night chip is as wide as the shadow ellipse and no wider, so it never shows as a ring`() {
        // Viewport units of the drawable (108): the lit disc is centred at (54, 54) with r 26 and
        // the shadow ellipse at (62, 54) with rx 22 and ry 26 reaches to x 84, about 30 units from
        // the centre. The first build's chip (r 34) stuck out four units beyond it and showed as a
        // black ring on the halo (fix round 1).
        var farthest = 26.0
        for (i in 0 until 3600) {
            val a = Math.toRadians(i / 10.0)
            val x = 62 + 22 * Math.cos(a) - 54
            val y = 26 * Math.sin(a)
            farthest = maxOf(farthest, Math.sqrt(x * x + y * y))
        }
        val chipRadius = MOON_CHIP_FRACTION * 108.0 / 2.0
        assertEquals(30.0, chipRadius, 1e-4)
        assertTrue("the chip (r=$chipRadius) covers the shadow (r=$farthest)", chipRadius >= farthest - 0.1)
        assertTrue("the chip (r=$chipRadius) does not stick out beyond the shadow", chipRadius <= farthest + 0.1)
    }

    @Test
    fun `the chip is a parameter every caller sets from its own ground, and only a light ground draws it`() {
        val mark = source("ui/components/MoonMark.kt")
        assertTrue(mark.contains("fun MoonMark(size: Dp, chip: Boolean, modifier: Modifier = Modifier"))
        assertTrue(mark.contains("if (chip) {"))
        assertTrue(source("ui/BottomBar.kt").contains("MoonMark(size = ICON_SIZE, chip = chrome.isLight)"))
        assertTrue(source("ui/components/Chrome.kt").contains("chip = tokens.isLight"))
        assertTrue(source("ui/alarms/AlarmsScreen.kt").contains("chip = LocalSunTokens.current.isLight"))
        // No caller passes a literal true (the first build's chip everywhere).
        TestFiles.require(TestFiles.KOTLIN).walk().filter { it.isFile && it.extension == "kt" }.forEach {
            assertFalse(it.name, Regex("""MoonMark\([^)]*chip = true""").containsMatchIn(it.readText()))
        }
    }

    @Test
    fun `the top bar holds the name and one button and the sheet opens from it`() {
        val bar = source("ui/TopBar.kt")
        assertEquals(1, Regex("""IconButton\(""").findAll(bar).count())
        assertTrue(bar.contains("onClick = onAppearance"))
        assertTrue(source("ui/App.kt").contains("TopBar(chrome = chrome, onAppearance = { showAppearance = true })"))
    }

    @Test
    fun `the cold start opens the Sun tab, except for a pending hand-off, the next-alarm chip or a Moon widget`() {
        val app = source("ui/App.kt")
        assertTrue(app.contains("alarmsRequests > 0 || AlarmIntentHandoff.pending(context) -> Tab.ALARMS"))
        assertTrue(app.contains("moonTodayRequests > 0 -> Tab.MOON"))
        assertTrue(app.contains("else -> Tab.SUN"))
        // A later request opens its tab too (SPEC 3.2).
        assertTrue(app.contains("LaunchedEffect(alarmsRequests) { if (alarmsRequests > 0) tab = Tab.ALARMS }"))
    }

    @Test
    fun `MainActivity handles the three tab requests in onCreate and in onNewIntent, and a Recents relaunch is not a request`() {
        val activity = source("MainActivity.kt")
        val create = activity.substringAfter("override fun onCreate").substringBefore("override fun onNewIntent")
        val newIntent = activity.substringAfter("override fun onNewIntent").substringBefore("override fun onResume")
        val helpers = activity.substringAfter("private fun isRequest").substringBefore("override fun onResume")
        // One place counts a request and one place tells whether an action is one; both entry points use them.
        for ((name, body) in listOf("onCreate" to create, "onNewIntent" to newIntent)) {
            assertTrue(name, body.contains("countRequest(") || body.contains("isRequest("))
            assertTrue(name, body.contains("setIntent(Intent(Intent.ACTION_MAIN))"))
        }
        assertTrue(create.contains("countRequest(intent?.action)"))
        assertTrue(newIntent.contains("countRequest(intent.action)"))
        assertTrue(helpers.contains("AndroidAlarmSink.ACTION_SHOW_ALARMS -> alarmsRequests.intValue++"))
        assertTrue(helpers.contains("ShowToday.ACTION_SUN -> sunTodayRequests.intValue++"))
        assertTrue(helpers.contains("ShowToday.ACTION_MOON -> moonTodayRequests.intValue++"))
        // The relaunch from Recents replays the old intent and must not open the tab again, and a
        // process recreation (a saved state) keeps the user's own tab.
        assertTrue(newIntent.contains("FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY"))
        assertTrue(create.contains("savedInstanceState == null"))
        // The action itself is the next-alarm chip's and the AlarmClock trampoline's, one string.
        val sink = source("alarm/AndroidAlarmSink.kt")
        assertTrue(sink.contains("const val ACTION_SHOW_ALARMS: String = \"no.stormberry.twinlit.action.SHOW_ALARMS\""))
        assertTrue(sink.contains(".setAction(ACTION_SHOW_ALARMS)"))
        assertTrue(source("alarm/AlarmIntentActivity.kt").contains(".setAction(AndroidAlarmSink.ACTION_SHOW_ALARMS)"))
        // The Alarms tab reads the hand-off again for every request.
        assertTrue(source("ui/alarms/AlarmsScreen.kt").contains("LaunchedEffect(handoffRequests)"))
        assertTrue(source("ui/App.kt").contains("handoffRequests = alarmsRequests"))
    }

    @Test
    fun `a widget tap opens its tab on the place's today, a date picked by hand survives a recomposition (ShowToday part)`() {
        val app = source("ui/App.kt")
        // The two requests are counts the root receives; each LaunchedEffect is keyed on its count.
        assertTrue(app.contains("sunTodayRequests: Int,"))
        assertTrue(app.contains("moonTodayRequests: Int,"))
        for ((count, tab, date) in listOf(
            Triple("sunTodayRequests", "Tab.SUN", "sunEpochDay"),
            Triple("moonTodayRequests", "Tab.MOON", "moonEpochDay"),
        )) {
            val effect = app.substringAfter("LaunchedEffect($count) {").substringBefore("\n    }\n")
            assertTrue(count, effect.contains("if ($count > 0)"))
            assertTrue(count, effect.contains("tab = $tab"))
            assertTrue(count, effect.contains("$date = PlaceDay.today(PlaceDay.zoneOf(place?.zoneId)).toEpochDay()"))
        }
        // The two actions carry the SPEC 2.3 names and the two request codes (ShowTodayTest has the rest).
        val show = source("widget/ShowToday.kt")
        assertTrue(show.contains("\"no.stormberry.twinlit.action.SHOW_SUN_TODAY\""))
        assertTrue(show.contains("\"no.stormberry.twinlit.action.SHOW_MOON_TODAY\""))
        assertFalse("SunApp's one SHOW_TODAY is gone", show.contains("action.SHOW_TODAY"))
        // MainActivity passes both counts to the root.
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("sunTodayRequests = sunTodayRequests.intValue"))
        assertTrue(activity.contains("moonTodayRequests = moonTodayRequests.intValue"))
    }

    @Test
    fun `MainActivity re-plans on resume off the main thread and opens the ring screen while an alarm rings`() {
        val activity = source("MainActivity.kt")
        assertTrue(activity.contains("replanInBackground()"))
        assertTrue(activity.contains("AlarmScheduling.replan(appContext)"))
        assertTrue(activity.contains("\"twinlit-resume-replan\""))
        assertTrue(activity.contains("snapshotFlow { AlarmService.ringing.value }.collect { showRingScreenIfRinging() }"))
        assertTrue(activity.contains("repeatOnLifecycle(Lifecycle.State.RESUMED)"))
    }
}
