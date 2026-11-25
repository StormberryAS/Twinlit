package no.stormberry.twinlit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.test.InstrumentationTestCase
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import no.stormberry.twinlit.MainActivity
import no.stormberry.twinlit.alarm.AlarmScheduling
import no.stormberry.twinlit.alarm.RingLook
import no.stormberry.twinlit.alarm.RingScreen
import no.stormberry.twinlit.alarm.RingingAlarm
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.ui.theme.TwinlitTheme
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.AlarmStore
import no.stormberry.twinlit.data.InputMode
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.Settings
import no.stormberry.twinlit.data.SharedPreferencesRuleFile
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.theme.Appearance

/**
 * R8, round 9: the real app drawn at a chosen width and font scale on a device, with every text
 * measured by the real text engine. Fails on any text that breaks inside a word or a number
 * (the line ends where the next line begins with no space between, the no-break space counts as
 * no space), on any text the engine says overflows its box (cut off), and on any text that ends
 * outside the window, or that the engine dropped part of (softWrap false, maxLines 1, a narrow box).
 *
 * It hosts [TwinlitApp] in the activity's window at 411 and 360 dp wide and font scale 1.0, 1.3 and
 * 2.0 (a [Density] with the device's density and that font scale, and a required width), then
 * reads the semantics tree of the Compose root: every node with a text layout is checked, on
 * the Sun tab and the Moon tab in both place modes, the Alarms tab with alarms, and the editor. The
 * windows that are not part of that root (the Appearance sheet, the permission sheet, the ring
 * screen) are not reached by this test; they are checked by screenshot.
 *
 * Platform runner and JUnit 3, no library. Run: `:app:assembleDebugAndroidTest`, install both APKs
 * on a `twinlit-*` emulator only, then `adb -s emulator-<port> shell am instrument -w -e class
 * no.stormberry.twinlit.ui.LayoutAuditTest no.stormberry.twinlit.debug.test/android.test.InstrumentationTestRunner`.
 * A finding is logged under the tag `LayoutAudit` and fails the test; the place is Oslo only.
 */
@OptIn(InternalComposeUiApi::class)
class LayoutAuditTest : InstrumentationTestCase() {

    private val oslo = Place("Oslo, Norway", 59.9127, 10.7461, "Europe/Oslo", fromCatalogue = true)
    private lateinit var activity: Activity
    private val polar = Place("78.0000 N, 15.0000 E", 78.0, 15.0, "Arctic/Longyearbyen", fromCatalogue = false, name = "Arctic test station, far north")
    private val findings = ArrayList<String>()
    private var inspected = 0
    private val seen = HashSet<String>()

    private val target: Context get() = instrumentation.targetContext

    /** The two preference files as they were before the test (R13-F, D148): settings and alarm rules. */
    private val saved = HashMap<String, Map<String, Any?>>()

    override fun setUp() {
        super.setUp()
        for (name in STORES) saved[name] = target.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
        val settings = Settings(target)
        settings.place = oslo
        settings.mode = InputMode.CITY
        settings.appearance = Appearance.SUN
        settings.firstRunNoticeSeenVersion = FIRST_RUN_NOTICE_VERSION
        val rules = listOf(
            rule("a", "Wake up with the sun", Anchor.Sun(SolarEvent.SUNRISE), Direction.BEFORE, 30, true),
            rule("b", "Evening walk", Anchor.Sun(SolarEvent.SUNSET), Direction.AFTER, 45, true),
            rule("c", "Noon", Anchor.Sun(SolarEvent.SOLAR_NOON), Direction.AT, 0, false),
            rule("d", "Moonrise watch", Anchor.Moon(LunarEvent.MOONRISE), Direction.AT, 0, true),
            rule("e", "Moonset, long label that goes on and on", Anchor.Moon(LunarEvent.MOONSET), Direction.BEFORE, 90, true),
        )
        SharedPreferencesRuleFile(target).write(AlarmStore.encode(rules))
    }

    /**
     * The test seeds five alarm rules (four enabled), and the app arms them when it comes to the front, so
     * "Moonrise watch" rang on an emulator once (R13-F, D148). Whatever the outcome of the test, the activity is
     * finished, the settings and the rule file are put back as they were (a file that did not exist ends as an empty file),
     * and the planner is run once more, so it cancels the alarms of the seeded rules.
     */
    override fun tearDown() {
        try {
            if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
            for ((name, before) in saved) {
                val prefs = target.getSharedPreferences(name, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    clear()
                    for ((key, value) in before) when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }.commit()
            }
            AlarmScheduling.replan(target)
        } finally {
            super.tearDown()
        }
    }

    private fun rule(id: String, label: String, anchor: Anchor, direction: Direction, minutes: Int, enabled: Boolean) = AlarmRule(
        id = id, label = label, anchor = anchor, direction = direction, offsetMinutes = minutes,
        latDeg = oslo.latDeg, lonDeg = oslo.lonDeg, zoneId = oslo.zoneId, placeName = "Oslo, Norway", enabled = enabled,
    )

    fun testAllScreens() {
        val only = comboArgument()
        val activity = launch()
        this.activity = activity
        for (w in listOf(411, 360)) for (fs in listOf(1.0f, 1.3f, 2.0f)) {
            if (only != null && only != "$w@$fs") continue
            // The shared place of the three tabs: a catalogue city with alarms, a place typed as coordinates in
            // the polar night (a long name; the tiles say "Polar Night"), and no place at all (the empty cards).
            for ((name, place) in listOf("Oslo" to oslo, "typed polar" to polar, "no place" to null)) {
                Settings(target).place = place
                show(activity, w, fs)
                walk("$w dp x$fs, $name", tabsOnly = place != oslo)
            }
        }
        // The ring screen is its own activity and an always-dark look, so it is hosted here by itself, for a sun
        // ring and a moon ring (a long label, the detail line, the polar warning).
        for (w in listOf(411, 360)) for (fs in listOf(1.0f, 1.3f, 2.0f)) {
            if (only != null && only != "$w@$fs") continue
            for (kind in AnchorKind.entries) {
                showRing(activity, w, fs, kind)
                scan("$w dp x$fs / Ring screen (${kind.key})")
            }
        }
        for (f in findings) Log.w(TAG, f)
        Log.i(TAG, "findings: ${findings.size}, texts inspected: $inspected")
        assertTrue("layout findings: ${findings.size}, see logcat tag $TAG", findings.isEmpty())
    }

    /** A file `combo` in the app's files folder (`360@2.0`) limits a run to one width and font scale. */
    private fun comboArgument(): String? = try {
        java.io.File(target.filesDir, "combo").takeIf { it.exists() }?.readText()?.trim()
    } catch (e: java.io.IOException) {
        null
    }

    private fun launch(): Activity {
        val intent = Intent(target, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return instrumentation.startActivitySync(intent)
    }

    private fun show(activity: Activity, widthDp: Int, fontScale: Float) {
        instrumentation.runOnMainSync {
            val view = ComposeView(activity)
            view.setContent { Host(widthDp, fontScale) }
            activity.setContentView(view)
        }
        settle()
    }

    @Composable
    private fun Host(widthDp: Int, fontScale: Float) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.requiredWidth(widthDp.dp).fillMaxHeight()) {
                    TwinlitApp(
                        foreground = true,
                        resumeCount = 1,
                        alarmsRequests = 0,
                        sunTodayRequests = 0,
                        moonTodayRequests = 0,
                        onBarsLight = {},
                    )
                }
            }
        }
    }

    private fun showRing(activity: Activity, widthDp: Int, fontScale: Float, kind: AnchorKind) {
        val alarm = RingingAlarm(
            ruleId = "ring", label = "Wake up with the sun, a long label", placeName = "Oslo, Norway",
            anchorText = if (kind == AnchorKind.SUN) "Sunrise" else "Moonrise",
            fireAtMillis = 1_795_000_000_000L, usedFallback = true, isSnooze = false, ringtoneUri = null, vibrate = true,
            kind = kind,
        )
        instrumentation.runOnMainSync {
            val view = ComposeView(activity)
            view.setContent {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.requiredWidth(widthDp.dp).fillMaxHeight()) {
                            val content: @Composable () -> Unit = {
                                RingScreen(alarm = alarm, look = RingLook.of(kind), onSnooze = {}, onDismiss = {})
                            }
                            if (kind == AnchorKind.MOON) TwinlitTheme(MoonNight, content) else TwinlitTheme(SunNight, content)
                        }
                    }
                }
            }
            activity.setContentView(view)
        }
        settle()
    }

    private fun settle() {
        Thread.sleep(900)
        Thread.sleep(300)
    }

    /** Visits every screen the root can reach and scans each one. */
    private fun walk(tag: String, tabsOnly: Boolean) {
        for (tab in listOf("Sun", "Moon")) {
            click(tab, exact = true, last = true)
            for (mode in listOf("City search", "Coordinates", "City search")) {
                if (!click(mode, exact = true)) continue
                if (mode == "City search" && seen.add("$tag $tab city-done")) scan("$tag / $tab / City search")
                if (mode == "Coordinates") scan("$tag / $tab / Coordinates")
            }
        }
        if (tabsOnly) return
        click("Alarms", exact = true, last = true)
        scan("$tag / Alarms")
        if (click("New", exact = false)) {
            scan("$tag / Editor (sun)")
            if (click("Moon", exact = true)) scan("$tag / Editor (moon)")
        }
    }

    /** The Compose root of the window. Main thread only. */
    private fun root(): RootForTest? {
        var found: RootForTest? = null
        fun visit(v: View) {
            if (found != null) return
            if (v is RootForTest) { found = v; return }
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(activity.window.decorView)
        return found
    }

    private fun scan(where: String) {
        settle()
        instrumentation.runOnMainSync {
            val rootNode = root()?.semanticsOwner?.unmergedRootSemanticsNode ?: return@runOnMainSync
            visit(rootNode, where)
        }
    }

    private fun visit(node: SemanticsNode, where: String) {
        val getter = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        if (getter != null) {
            val out = ArrayList<TextLayoutResult>()
            if (getter(out) && out.isNotEmpty()) inspect(node, out[0], where)
        }
        for (child in node.children) visit(child, where)
    }

    private fun inspect(node: SemanticsNode, layout: TextLayoutResult, where: String) {
        val text = layout.layoutInput.text.text
        if (text.isEmpty()) return
        inspected++
        val pieces = ArrayList<String>()
        var broken = false
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i, visibleEnd = false)
            pieces.add(text.substring(start, end).trimEnd())
            if (i < layout.lineCount - 1 && end in 1 until text.length) {
                val before = text[end - 1]
                val after = text[end]
                val ok = before.isWhitespace() && before != ' ' || after.isWhitespace() && after != ' ' ||
                    before == '\n' || before == '/' || before == '-'
                if (!ok) broken = true
                // A break after a slash is allowed only inside a word that is wider than its box. A word with a
                // slash that fits its box on one line (a zone id such as "Europe/Oslo.") must not break there
                // (R9, D102: the Moon caption broke "Europe/" and "Oslo." at 2.0).
                if (before == '/' && wordFitsBox(text, end, node, layout)) broken = true
            }
        }
        // The zone id of the Oslo place stays whole (R8, D87 fallback only for an id wider than the column).
        if (text == "Europe/Oslo" && layout.lineCount > 1) broken = true
        // A node that an ancestor clips (a chip, a clipped row) is narrower in the root than its own size, or
        // holds a line that ends beyond the visible bounds. Only the width is compared: a column may scroll
        // under the window vertically, and a node scrolled out of the window has an empty rectangle there,
        // so nothing can be said about it.
        val visible = node.boundsInRoot
        val onScreen = visible.width > 0f && visible.height > 0f
        val clipped = onScreen && (visible.width < node.size.width - 4f ||
            (0 until layout.lineCount).any { layout.getLineRight(it) - layout.getLineLeft(it) > visible.width + 4f })
        // A text with softWrap false and maxLines 1 in a box narrower than it needs is neither wider than
        // its box nor clipped by an ancestor: the engine lays out only what fits and drops the rest ("City
        // seai" for "City search"), so every line fits its box and the tests above are silent.
        // The cut shows as text left over after the last line.
        val dropped = text.substring(layout.getLineEnd(layout.lineCount - 1, visibleEnd = false)).isNotBlank()
        // The engine's own verdict (`didOverflow*`) is true for every text here, and the paragraph's width
        // is the constraint, not the text. A text is cut when a line reaches past its box, when its
        // lines are taller than its box, when a line ends in an ellipsis, when an ancestor clips it, or when the engine dropped part of it.
        val cut = dropped || clipped || (0 until layout.lineCount).any { layout.getLineRight(it) - layout.getLineLeft(it) > node.size.width + 4f || layout.isLineEllipsized(it) } ||
            layout.multiParagraph.height > node.size.height + 2f
        if (broken || cut) {
            val key = "$where | ${text.take(60)} | ${pieces.joinToString("|")}"
            if (seen.add(key)) {
                val chain = generateSequence(node.parent) { it.parent }.take(3).joinToString(",") { "${it.size.width}" }
                val dbg = " (box ${node.size.width}x${node.size.height} visible ${node.boundsInRoot.width}x${node.boundsInRoot.height} in $chain, text ${layout.size.width}x${layout.size.height}, mp ${layout.multiParagraph.height}, line ${(0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }})"
                findings.add("${if (broken) "BREAK" else "CUT"}$dbg [$where] \"${text.replace(' ', '~')}\" lines: ${pieces.joinToString(" // ").replace(' ', '~')}")
            }
        }
    }

    /**
     * Whether the word of [text] that holds the break at [end] (a run with no plain space and no line feed) fits
     * the node's box on one line, measured with the node's own style: then a break inside it is a defect.
     */
    private fun wordFitsBox(text: String, end: Int, node: SemanticsNode, layout: TextLayoutResult): Boolean {
        var from = end - 1
        while (from > 0 && text[from - 1] != ' ' && text[from - 1] != '\n') from--
        var to = end
        while (to < text.length && text[to] != ' ' && text[to] != '\n') to++
        val word = text.substring(from, to)
        val input = layout.layoutInput
        val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
        val width = measurer.measure(word, input.style, softWrap = false, maxLines = 1, constraints = Constraints()).size.width
        return width <= node.size.width
    }

    private fun click(label: String, exact: Boolean, last: Boolean = false): Boolean {
        var done = false
        instrumentation.runOnMainSync {
            val rootNode = root()?.semanticsOwner?.unmergedRootSemanticsNode ?: return@runOnMainSync
            val hits = ArrayList<SemanticsNode>()
            fun find(n: SemanticsNode) {
                val texts = n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()
                val desc = n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()
                val match = (texts + desc).any { if (exact) it == label else it.startsWith(label) }
                if (match) hits.add(n)
                n.children.forEach(::find)
            }
            find(rootNode)
            for (n in (if (last) hits.reversed() else hits)) {
                var cur: SemanticsNode? = n
                while (cur != null && !cur.config.contains(SemanticsActions.OnClick)) cur = cur.parent
                val action = cur?.config?.getOrNull(SemanticsActions.OnClick)?.action
                if (action != null) { action(); done = true; break }
            }
        }
        if (done) settle()
        return done
    }

    private companion object {
        const val TAG = "LayoutAudit"
        /** The preference files the test changes: the settings and the alarm rules. */
        val STORES = listOf(Settings.PREFS_NAME, AlarmStore.PREFS_NAME)
    }
}
