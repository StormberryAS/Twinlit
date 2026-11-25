package no.stormberry.twinlit.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.test.InstrumentationTestCase
import android.text.Spanned
import android.text.style.LocaleSpan
import no.stormberry.twinlit.alarm.model.AnchorKind
import java.util.Locale

/**
 * R10 (D108 to D111), R12 (D123, D124): the Missed, Snoozed, ring and backup ring notices as the system holds them. Posts four notices through
 * the app's own code and reads them back from the notification manager: the fixed titles, the alarm's name
 * first in the text, no-break spaces between a time and its marker and between a number and its unit, and
 * a [LocaleSpan] over the whole text (no hyphenation). The notices stay in the shade afterwards, so a person
 * can look at them; the next run clears them first. Emulators only (the places are Oslo or none).
 *
 * Platform runner and JUnit 3, no library, as `LayoutAuditTest`: `adb -s emulator-<port> shell am instrument -w
 * -e class no.stormberry.twinlit.alarm.NoticeDeviceTest no.stormberry.twinlit.debug.test/android.test.InstrumentationTestRunner`.
 */
class NoticeDeviceTest : InstrumentationTestCase() {

    private val target: Context get() = instrumentation.targetContext
    private val nbsp = ' '

    private companion object {
        const val RING_TAG = "ring-test"
        const val RING_ID = 0x7E57

        /** The design budget of the Android documentation for a custom expanded view, in dp (D131). */
        const val BUDGET_DP = 256
    }

    fun testPostedNoticesCarryTheDecidedWords() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = target.getSystemService(NotificationManager::class.java)
        manager.cancelAll()

        val now = System.currentTimeMillis()
        val generated = "1 h 42 min after moonrise, Oslo"
        val typed = "Antidisestablishmentarianism and other extraordinarily long words"
        AlarmService.postSnoozedNotice(target, PendingSnooze("snooze-1", now + 9 * 60_000L, "Wake up with the sun", AnchorKind.SUN))
        AlarmService.postMissedNotice(target, ringing("missed-1", generated, now - 600_000L, AnchorKind.MOON))
        AlarmService.postMissedNotice(target, ringing("missed-2", typed, now - 650_000L, AnchorKind.SUN), replaced = true)
        AlarmService.postNotRungNotice(target, "missed-3", "Evening walk", AnchorKind.SUN)

        // notify() is asynchronous: wait (at most five seconds) until the system holds all four.
        var posted = held(manager)
        var waited = 0
        while (posted.size < 4 && waited < 5_000) {
            Thread.sleep(100)
            waited += 100
            posted = held(manager)
        }
        assertEquals("four notices posted: ${posted.keys}", 4, posted.size)

        val snoozed = posted.getValue("snooze-1").notification.extras
        assertEquals("Alarm snoozed", snoozed.getCharSequence(Notification.EXTRA_TITLE).toString())
        val snoozedText = snoozed.getCharSequence(Notification.EXTRA_TEXT)!!
        assertTrue(snoozedText.toString(), snoozedText.toString().startsWith("Wake up with the sun. Ringing again at "))
        assertNoPlainSpaceBeforeMarker(snoozedText.toString())
        assertWholeLocaleSpan(snoozedText)
        // The expanded notice is the notice's own view (D117), not the BigText template that rewrites spaces.
        assertTrue(snoozed.getString(Notification.EXTRA_TEMPLATE).orEmpty(), snoozed.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("DecoratedCustomViewStyle"))

        val missed = posted.getValue("missed-1").notification.extras
        assertEquals("Missed alarm", missed.getCharSequence(Notification.EXTRA_TITLE).toString())
        val missedText = missed.getCharSequence(Notification.EXTRA_TEXT)!!
        assertTrue(missedText.toString(), missedText.toString().startsWith("1${nbsp}h 42${nbsp}min after moonrise, Oslo. Rang at "))
        assertTrue(missedText.toString(), missedText.toString().endsWith(" for 10${nbsp}minutes with no response."))
        assertNoPlainSpaceBeforeMarker(missedText.toString())
        assertWholeLocaleSpan(missedText)

        val replaced = posted.getValue("missed-2").notification.extras
        assertEquals("Missed alarm", replaced.getCharSequence(Notification.EXTRA_TITLE).toString())
        val replacedText = replaced.getCharSequence(Notification.EXTRA_TEXT)!!
        assertTrue(replacedText.toString(), replacedText.toString().startsWith("$typed. Rang at "))
        assertWholeLocaleSpan(replacedText)

        val notRung = posted.getValue("missed-3").notification.extras
        assertEquals("Missed alarm", notRung.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Evening walk. The phone could not ring this alarm on time.", notRung.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    /**
     * R14 (D117, R10 LOW 1): the EXPANDED notices as the shade draws them, at font scale 2.0. Each notice is
     * posted alone, the shade is opened, the notice is expanded, and the place of every character is read from
     * the accessibility tree ([ShadeReader]). A pair the app joined (a time and its AM or PM, a number and its
     * unit) must sit on one line; a pair on two lines fails the test, and so does a shade text that differs from
     * the posted one. Android 16 wrapped "6:57" and "PM." although U+00A0 was posted, so this test reads the
     * shade, not only the posted text. Run on a 360 dp phone (`wm density 480` on a 1080 px emulator).
     */
    fun testExpandedNoticesKeepEachPairOnOneLine() {
        if (Build.VERSION.SDK_INT < 26) return // character places start at API 26
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val shade = ShadeReader(instrumentation)
        val manager = target.getSystemService(NotificationManager::class.java)
        val oldScale = shade.shell("settings get system font_scale").trim().takeIf { it != "null" && it.isNotEmpty() } ?: "1.0"
        shade.shell("settings put system font_scale 2.0")
        try {
            val now = System.currentTimeMillis()
            val cases = linkedMapOf<String, () -> Unit>()
            // The name's length moves every line break, so some wrap lands next to the time, as on the R10 phones.
            for (pad in 0..9) {
                cases["snoozed+$pad"] = { AlarmService.postSnoozedNotice(target, PendingSnooze("shade-1", now + 9 * 60_000L, "Wake up with the sun" + "n".repeat(pad), AnchorKind.SUN)) }
            }
            cases.putAll(listOf(
                "missed" to { AlarmService.postMissedNotice(target, ringing("shade-2", "1 h 42 min after moonrise, Oslo", now - 600_000L, AnchorKind.MOON)) },
                "replaced" to { AlarmService.postMissedNotice(target, ringing("shade-3", "Evening walk", now - 650_000L, AnchorKind.SUN), replaced = true) },
            ))
            // The ring notice and the backup ring notice (R12, D123): the summary "..., 5 h 8 min before sunset" holds
            // a number and its unit. The place's length moves every line break, as the Snoozed name does.
            for (pad in 0..9) {
                val alarm = ringing("shade-4", "Sunrise walk", now, AnchorKind.SUN, "Oslo" + "o".repeat(pad), "5 h 8 min before sunrise")
                cases["ring+$pad"] = { postRing(AlarmService.buildRingNotification(target, alarm)) }
                if (pad <= 3) cases["backup+$pad"] = { postRing(AlarmService.buildBackupNotification(target, alarm)) }
            }
            val broken = mutableListOf<String>()
            for ((name, post) in cases) {
                manager.cancelAll()
                Thread.sleep(300)
                post()
                var held = held(manager)
                var waited = 0
                while (held.isEmpty() && waited < 5_000) { Thread.sleep(100); waited += 100; held = held(manager) }
                assertEquals("$name posted", 1, held.size)
                val posted = held.values.first().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                if (name.startsWith("ring") || name.startsWith("backup")) assertTrue("$name: the posted summary keeps its pair: $posted", posted.contains("5${nbsp}h 8${nbsp}min") || posted.contains("8${nbsp}min"))
                shade.collapseShade()
                Thread.sleep(500)
                assertTrue("$name: the shade did not open (no notification list in the tree):\n${shade.describe()}", shade.expandShade())
                val read = shade.expandedText()
                assertNotNull("$name: no expanded text in the shade:\n${shade.describe()}", read)
                broken += brokenPairs(posted, read!!).map { "$name: $it" }
                shade.collapseShade()
            }
            assertTrue("pairs split by a line break or a changed text in the shade:\n${broken.joinToString("\n")}", broken.isEmpty())
        } finally {
            shade.shell("settings put system font_scale $oldScale")
            manager.cancelAll()
        }
    }

    /** Posts a ring or a backup ring notification the way the service does, under a test tag. */
    private fun postRing(notification: Notification) {
        AlarmService.ensureChannels(target)
        target.getSystemService(NotificationManager::class.java).notify(RING_TAG, RING_ID, notification)
    }

    /**
     * R12 (D123): the ring notification and the backup ring notification have the notice's own expanded view
     * and keep everything else: the title is the alarm's name, the collapsed text is the summary, the alarm
     * category, the full-screen intent, the two actions, the channel, the ongoing flag (ring) and the small icon.
     */
    fun testRingNoticesHaveTheOwnViewAndKeepTheirOtherParts() {
        val now = System.currentTimeMillis()
        val alarm = ringing("ring-1", "Sunrise walk", now, AnchorKind.SUN, "Oslo", "5 h 8 min before sunrise")
        val summary = AlarmService.summaryFor(target, alarm)
        assertTrue(summary, summary.endsWith("5${nbsp}h 8${nbsp}min before sunrise"))
        for ((name, n) in listOf("ring" to AlarmService.buildRingNotification(target, alarm), "backup" to AlarmService.buildBackupNotification(target, alarm))) {
            val extras = n.extras
            assertTrue("$name: the decorated custom view style, not BigText: ${extras.getString(Notification.EXTRA_TEMPLATE)}",
                extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("DecoratedCustomViewStyle"))
            assertNotNull("$name: an own expanded view", n.bigContentView)
            assertEquals("$name: the title is the alarm's name", "Sunrise walk", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("$name: the collapsed text", summary, extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals("$name: category", Notification.CATEGORY_ALARM, n.category)
            assertNotNull("$name: the full-screen intent", n.fullScreenIntent)
            assertEquals("$name: the actions", listOf("Snooze 9 min", "Dismiss"), n.actions.map { it.title.toString() })
            assertEquals("$name: the channel", if (name == "ring") AlarmService.CHANNEL_RING else AlarmService.CHANNEL_RING_BACKUP, n.channelId)
            assertTrue("$name: heads-up priority", n.priority == Notification.PRIORITY_MAX)
            assertEquals("$name: public on the lock screen", Notification.VISIBILITY_PUBLIC, n.visibility)
            assertNotNull("$name: a small icon", n.smallIcon)
        }
        val ring = AlarmService.buildRingNotification(target, alarm)
        assertTrue("the ring notice is ongoing", ring.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("the ring notice is not auto-cancelled", ring.flags and Notification.FLAG_AUTO_CANCEL == 0)
        val backup = AlarmService.buildBackupNotification(target, alarm)
        assertTrue("the backup ring repeats until answered", backup.flags and Notification.FLAG_INSISTENT != 0)
        assertNotNull("the backup ring tells the receiver when it is swiped", backup.deleteIntent)
    }

    /**
     * R13 (D142): the ring notice as a heads-up, collapsed (the shade closed), shows the standard parts of the
     * system template: the alarm's name as the title, the summary as the text and the actions "Snooze 9 min" and
     * "Dismiss". The own expanded view must not change what the collapsed notice shows. The heads-up is read from
     * SystemUI's interactive windows; the picture is kept in the app's cache folder (`headsup-ring.png`).
     */
    fun testTheRingHeadsUpKeepsTheStandardTitleTextAndActions() {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val shade = ShadeReader(instrumentation)
        val manager = target.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        shade.collapseShade()
        shade.shell("input keyevent KEYCODE_HOME")
        Thread.sleep(800)
        val now = System.currentTimeMillis()
        val alarm = ringing("ring-1", "Sunrise walk", now, AnchorKind.SUN, "Oslo", "5 h 8 min before sunrise")
        val summary = AlarmService.summaryFor(target, alarm).replace(nbsp, ' ')
        postRing(AlarmService.buildRingNotification(target, alarm))
        var seen: List<String> = emptyList()
        var shot: Bitmap? = null
        val until = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < until) {
            val texts = shade.systemUiTexts().map { it.replace(nbsp, ' ') }
            if (texts.any { it == "Sunrise walk" } && texts.any { it == "Snooze 9 min" }) {
                seen = texts
                shot = shade.screenshot()
                break
            }
            Thread.sleep(150)
        }
        try {
            shot?.let { b -> runCatching { java.io.File(target.cacheDir, "headsup-ring.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
            assertTrue("the heads-up never showed the title and the Snooze action: ${shade.systemUiTexts()}", seen.isNotEmpty())
            assertTrue("the title, the alarm's name: $seen", "Sunrise walk" in seen)
            assertTrue("the text, the summary \"$summary\": $seen", summary in seen)
            assertTrue("the action Snooze 9 min: $seen", "Snooze 9 min" in seen)
            assertTrue("the action Dismiss: $seen", "Dismiss" in seen)
        } finally {
            manager.cancelAll()
        }
    }

    /** One notice of [testALongTypedNameGivesWayAndTheTimeStaysWhole] and what the shade must show for it. */
    private class Case(
        val post: () -> Unit,
        /** The name view's text (null: the name flows, no name view). */
        val name: String?,
        /** The text of `notice_text`: the sentence, or the rest of a ring notice's summary. */
        val sentence: String,
        val ringTitle: Boolean = false,
        /** Whether the name view is cut (null: either; it depends on the font scaling of the API level). */
        val cutName: Boolean? = true,
        /** The ring notice's time line and long place (null: the place flows). */
        val lead: String? = null,
        val place: String? = null,
        val cutPlace: Boolean? = null,
        /**
         * The ceiling, in dp, from the top of the title to the bottom of the sentence on API 34 and later, where a font
         * scale above 1.0 grows the text less than linearly (a regression guard at the measured height, see the test).
         */
        val ceiling: Int = BUDGET_DP,
        /** The same on API 33 and earlier, where the font scale is linear: 2.0 doubles every text (measured, see the test). */
        val linearCeiling: Int = ceiling,
    )

    /**
     * R12 (D124), R12-F (D130 to D132): a long name or a long place gives way, with an ellipsis, and the sentence with
     * the time stays whole, in every expanded notice, at font scale 2.0 on a 360 dp phone. For each notice the test
     * reads the shade ([ShadeReader]) and checks:
     *  - the text of the sentence is the posted one, every character of it has a place, and the last one lies
     *    inside the view, the window and the screen;
     *  - a long name has its own view of at most three lines, a long place of a ring notice has its own of at most
     *    two (with the time first, in its own view), a name or place that fits has none;
     *  - a cut view ends with an ellipsis. The screenshot says what is drawn at the end ([endsWithEllipsis]: ink low in
     *    the line, none in the middle); the layout says whether the view is cut ([isCut]: the hidden characters have no
     *    width on API 36, or share one place at the end of the last line on API 33 and API 29). A view that must be cut
     *    passes when the screenshot shows its ellipsis, because an API level may report hidden characters in a way
     *    [isCut] does not know (API 29 with Tibetan marks); a view the layout calls cut must show one;
     *  - a ring notice's title (the name) is one line with the same ellipsis;
     *  - the height of the view, from the top of the title to the bottom of the sentence, is logged for each case
     *    (tag NoticeDeviceTest) and kept under the ceiling of the case. 256 dp is the design budget of the Android
     *    documentation, not a limit the shade enforces (it drew 345 dp and 472 dp whole, D131), so the ceilings are
     *    regression guards at the measured heights of the API level that sets them, nothing more.
     * The proof covers API 36, API 33 and API 29 (D131). API 34 and API 35 were not run; API 24 and API 25 were not
     * run, and below API 26 the test is skipped (no character places).
     */
    fun testALongTypedNameGivesWayAndTheTimeStaysWhole() {
        if (Build.VERSION.SDK_INT < 26) return // character places start at API 26
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val shade = ShadeReader(instrumentation)
        val manager = target.getSystemService(NotificationManager::class.java)
        val oldScale = shade.shell("settings get system font_scale").trim().takeIf { it != "null" && it.isNotEmpty() } ?: "1.0"
        shade.shell("settings put system font_scale 2.0")
        val dp = target.resources.displayMetrics.density
        val typed = "Wake me up at the right time for the long hike to the mountain cabin and bring the thermos coffee " +
            "and the warm socks and the map and the headlamp and lots of water for everybody who comes along please"
        assertEquals(200, typed.length)
        val tibetan = "བོད་སྐད་".repeat(25)
        assertEquals(200, tibetan.length)
        val chinese = "早上七点叫我起床去山上散步然后回家吃饭再去上班不要忘记带伞谢谢你"
        assertEquals(32, chinese.length)
        val catalogue = "Karachi University Employees Co-operative Housing Society, Pakistan"
        assertEquals(67, catalogue.length)
        val longAnchor = "11 h 59 min after the end of the golden hour"
        try {
            val now = System.currentTimeMillis()
            fun at(millis: Long) = AlarmService.formatTime(target, millis).replace(nbsp, ' ')
            fun ring(id: String, label: String, place: String, anchor: String, fallback: Boolean = false, snooze: Boolean = false) =
                RingingAlarm(
                    ruleId = id, label = label, placeName = place, anchorText = anchor, fireAtMillis = now, usedFallback = fallback,
                    isSnooze = snooze, ringtoneUri = null, vibrate = false, kind = AnchorKind.SUN,
                )
            /** A number and its unit joined by a no-break space, written here on its own (not through the app's NoticeText). */
            fun nb(text: String) = Regex("""(?<=\d) (?=(?:h|min)\b)""").replace(text, nbsp.toString())
            val sunWarning = "The sun did not reach that point today, so this alarm used solar noon."
            /**
             * A ring or backup ring notice, with what its expanded view must show worked out here from the words: a place of
             * up to 32 characters flows in one paragraph with the time; a longer place has the time in a line of its own,
             * the place (at most two lines, an ellipsis) and the rest (D130).
             */
            fun ringCase(alarm: RingingAlarm, backup: Boolean = false, ceiling: Int = BUDGET_DP, linearCeiling: Int = ceiling): Case {
                val time = AlarmService.formatTime(target, alarm.fireAtMillis)
                val tail = listOfNotNull(alarm.anchorText.takeIf { it.isNotBlank() }, "snoozed".takeIf { alarm.isSnooze }).joinToString(", ")
                val build = { if (backup) AlarmService.buildBackupNotification(target, alarm) else AlarmService.buildRingNotification(target, alarm) }
                val split = alarm.placeName.length > 32
                val rest = nb(listOf(tail, sunWarning.takeIf { alarm.usedFallback }).filterNotNull().filter { it.isNotEmpty() }.joinToString(". "))
                val whole = nb(listOf(time, alarm.placeName, tail).filter { it.isNotEmpty() }.joinToString(", ")) +
                    if (alarm.usedFallback) ". $sunWarning" else ""
                val stop = if (tail.isNotEmpty()) "," else if (alarm.usedFallback) "." else ""
                return Case(
                    { postRing(build()) }, null, if (split) rest else whole, ringTitle = alarm.label == typed, cutName = null,
                    lead = if (split) "$time," else null, place = if (split) alarm.placeName + stop else null,
                    cutPlace = if (split) true else null, ceiling = ceiling, linearCeiling = linearCeiling,
                )
            }
            val cases = linkedMapOf(
                "snoozed" to Case({ AlarmService.postSnoozedNotice(target, PendingSnooze("long-1", now + 9 * 60_000L, typed, AnchorKind.SUN)) },
                    "$typed.", "Ringing again at ${at(now + 9 * 60_000L)}."),
                "missed" to Case({ AlarmService.postMissedNotice(target, ringing("long-2", typed, now - 600_000L, AnchorKind.SUN)) },
                    "$typed.", "Rang at ${at(now - 600_000L)} for 10 minutes with no response."),
                "replaced" to Case({ AlarmService.postMissedNotice(target, ringing("long-3", typed, now - 650_000L, AnchorKind.SUN), replaced = true) },
                    "$typed.", "Rang at ${at(now - 650_000L)} with no answer until the next alarm began.", linearCeiling = 285),
                "not rung" to Case({ AlarmService.postNotRungNotice(target, "long-4", typed, AnchorKind.SUN) },
                    "$typed.", "The phone could not ring this alarm on time."),
                // 33 wide characters: over the count; whether they fit in three lines depends on the font scaling of the API level.
                "wide name, over the limit" to Case({ AlarmService.postSnoozedNotice(target, PendingSnooze("long-5", now + 9 * 60_000L, "W".repeat(33), AnchorKind.SUN)) },
                    "${"W".repeat(33)}.", "Ringing again at ${at(now + 9 * 60_000L)}.", cutName = null),
                "wide name, flows" to Case({ AlarmService.postMissedNotice(target, ringing("long-6", "W".repeat(32), now - 650_000L, AnchorKind.SUN), replaced = true) },
                    null, "${"W".repeat(32)}. Rang at ${at(now - 650_000L)} with no answer until the next alarm began.", linearCeiling = 280),
                // 32 Chinese characters are wider than 32 W: their own view, not the flow (D132).
                "chinese name, 32 characters" to Case({ AlarmService.postMissedNotice(target, ringing("long-10", chinese, now - 650_000L, AnchorKind.SUN), replaced = true) },
                    "$chinese.", "Rang at ${at(now - 650_000L)} with no answer until the next alarm began.", cutName = null, linearCeiling = 285),
                // A tall fall-back font (Tibetan) does not make the three lines of the name taller than three lines (D132).
                "tibetan name, 200 characters" to Case({ AlarmService.postSnoozedNotice(target, PendingSnooze("long-11", now + 9 * 60_000L, tibetan, AnchorKind.SUN)) },
                    "$tibetan.", "Ringing again at ${at(now + 9 * 60_000L)}."),
                // A stored name with line breaks is one paragraph (D132).
                "name with line breaks" to Case({ AlarmService.postSnoozedNotice(target, PendingSnooze("long-12", now + 9 * 60_000L, typed.replace(" the ", "\n\n the "), AnchorKind.SUN)) },
                    "$typed.", "Ringing again at ${at(now + 9 * 60_000L)}."),
                "ring" to ringCase(ring("long-7", typed, "Oslo", "5 h 8 min before sunrise")),
                "ring, sun did not reach it" to ringCase(ring("long-8", typed, "Oslo", "11 h 59 min before sunrise", fallback = true)),
                "backup ring" to ringCase(ring("long-9", typed, "Oslo", "5 h 8 min before sunrise"), backup = true),
                // A long catalogue place (67 characters): the time first, the place in two lines at most, then the rest (D130).
                "ring, long place" to ringCase(ring("long-13", "Sunrise walk", catalogue, "5 h 8 min before sunrise")),
                "ring, long place, the longest rest" to ringCase(ring("long-14", "Sunrise walk", catalogue, longAnchor, fallback = true, snooze = true), ceiling = 360, linearCeiling = 390),
                "backup ring, long place" to ringCase(ring("long-15", "Sunrise walk", catalogue, longAnchor), backup = true),
                "ring, long place, long name" to ringCase(ring("long-16", typed, catalogue, longAnchor, snooze = true)),
            )
            val failures = mutableListOf<String>()
            for ((label, case) in cases) {
                manager.cancelAll()
                Thread.sleep(300)
                case.post()
                var held = held(manager)
                var waited = 0
                while (held.isEmpty() && waited < 5_000) { Thread.sleep(100); waited += 100; held = held(manager) }
                assertEquals("$label posted", 1, held.size)
                shade.collapseShade()
                Thread.sleep(500)
                assertTrue("$label: the shade did not open (no notification list in the tree):\n${shade.describe()}", shade.expandShade())
                var read = shade.readNotice()
                assertNotNull("$label: no expanded notice in the shade:\n${shade.describe()}", read)
                // A view that is expected may not be visible yet while the shade settles: read again (up to four times).
                var again = 0
                while (again < 4 && (case.name != null && read!!.name == null || case.place != null && (read!!.lead == null || read.place == null))) {
                    Thread.sleep(700)
                    read = shade.readNotice() ?: read
                    again++
                }
                Thread.sleep(800) // the shade finishes its animation before the places and the screenshot are taken
                read = shade.readNotice() ?: read // the places read while the shade still moved (title and text from two frames) are dropped
                val shot = shade.screenshot()
                val found = longNameFailures(label, read!!, case, dp, shot).map { "$label: $it" }
                if ((found.isNotEmpty() || java.io.File(target.cacheDir, "keep-shots").exists()) && shot != null) {
                    // A failed case keeps its picture in the app's cache folder: adb shell run-as <package> ls cache.
                    // With a file named keep-shots in that folder every case keeps its picture (to look at the notices).
                    runCatching { java.io.File(target.cacheDir, "notice-${label.replace(Regex("[^A-Za-z0-9]+"), "-")}.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
                failures += found
                shade.collapseShade()
            }
            assertTrue("the time sentence is cut or a name or place does not give way:\n${failures.joinToString("\n")}", failures.isEmpty())
        } finally {
            shade.shell("settings put system font_scale $oldScale")
            manager.cancelAll()
        }
    }

    private fun longNameFailures(label: String, read: ShadeReader.NoticeRead, case: Case, dp: Float, shot: Bitmap?): List<String> {
        val out = mutableListOf<String>()
        fun plain(s: String) = s.replace(nbsp, ' ')
        val text = read.text
        if (plain(text.text) != plain(case.sentence)) out += "the sentence differs: expected \"${case.sentence}\", the shade shows \"${text.text}\""
        // The sentence is whole and inside the view, the window and the screen.
        // A space at the end of a line is not drawn and some system images give it no place (API 36 with a 440 dpi
        // panel, R13, D137); a cut text always loses a visible character too, so spaces are not counted.
        val unplaced = text.rects.indices.count { text.rects[it] == null && !text.text[it].isWhitespace() }
        if (unplaced > 0) out += "$unplaced of ${text.text.length} characters of the sentence have no place (cut or off the screen)"
        val last = text.rects.lastOrNull()
        if (last != null && (last.bottom > text.window.bottom + 1 || last.bottom > text.screen.bottom + 1)) {
            out += "the last character of the sentence ends at ${last.bottom.toInt()}, below its view (window ${text.window.bottom}, view ${text.screen.bottom})"
        }
        if (last != null && last.bottom > target.resources.displayMetrics.heightPixels) {
            out += "the last character of the sentence ends at ${last.bottom.toInt()}, below the screen (${target.resources.displayMetrics.heightPixels})"
        }
        // The height of the whole custom view, from the title to the end of the sentence, against the case's ceiling.
        val title = read.title
        if (title != null) {
            val height = (text.screen.bottom - title.screen.top) / dp
            android.util.Log.i(
                "NoticeDeviceTest",
                "$label: API ${Build.VERSION.SDK_INT}: ${height.toInt()} dp high (design budget $BUDGET_DP); " +
                    "title ${(title.screen.bottom - title.screen.top) / dp} dp, name ${read.name?.let { (it.screen.bottom - it.screen.top) / dp }} dp, " +
                    "place ${read.place?.let { (it.screen.bottom - it.screen.top) / dp }} dp, sentence ${(text.screen.bottom - text.screen.top) / dp} dp",
            )
            val ceiling = if (Build.VERSION.SDK_INT >= 34) case.ceiling else case.linearCeiling
            if (height > ceiling) out += "the notice view is ${height.toInt()} dp high, over its ceiling of $ceiling dp"
        } else {
            out += "no title view"
        }
        if (case.name != null) {
            val nameView = read.name
            if (nameView == null) {
                out += "the long name has no view of its own"
            } else {
                if (plain(nameView.text) != plain(case.name)) out += "the name differs: expected \"${case.name}\", the shade shows \"${nameView.text}\""
                out += endsLikeAnEllipsis("the name", nameView, maxLines = 3, case.cutName, shot)
            }
        } else if (read.name != null) {
            out += "a name that fits has a view of its own"
        }
        if (case.place != null) {
            val leadView = read.lead
            val placeView = read.place
            if (leadView == null || placeView == null) {
                out += "the long place has no view of its own (lead ${leadView != null}, place ${placeView != null})"
            } else {
                if (plain(leadView.text) != plain(case.lead.orEmpty())) out += "the time line differs: expected \"${case.lead}\", the shade shows \"${leadView.text}\""
                if (plain(placeView.text) != plain(case.place)) out += "the place differs: expected \"${case.place}\", the shade shows \"${placeView.text}\""
                if (leadView.screen.top >= placeView.screen.top) out += "the time is not above the place"
                if (leadView.rects.any { it == null }) out += "the time line has characters without a place"
                out += endsLikeAnEllipsis("the place", placeView, maxLines = 2, case.cutPlace, shot)
            }
        } else if (read.lead != null || read.place != null) {
            out += "a place that fits has a view of its own"
        }
        if (case.ringTitle) {
            if (title == null) out += "no ring title" else out += endsLikeAnEllipsis("the ring title", title, maxLines = 1, true, shot)
        }
        return out
    }

    /**
     * The view shows at most [maxLines] lines; when [expectCut] is set it must (or must not) be cut, and a cut view
     * must end with an ellipsis. Whether the view is cut comes from the layout ([isCut]), what is drawn at the end from
     * the screenshot ([endsWithEllipsis]).
     */
    private fun endsLikeAnEllipsis(what: String, view: ShadeReader.ViewRead, maxLines: Int, expectCut: Boolean?, shot: Bitmap?): List<String> {
        val out = mutableListOf<String>()
        val placed = view.rects.filterNotNull()
        val lines = placed.map { it.top.toInt() }.distinct().size
        if (lines > maxLines) out += "$what has $lines lines, over $maxLines"
        val layoutCut = isCut(view)
        val drawn = if (shot == null) "$what: no screenshot to look for the ellipsis" else endsWithEllipsis(what, view, shot)
        android.util.Log.i(
            "NoticeDeviceTest",
            "$what: ${view.text.length} characters, ${placed.size} with a place, $lines lines, cut by the layout $layoutCut, " +
                "ellipsis drawn ${drawn == null}, view ${view.screen}, last places " +
                view.rects.takeLast(4).map { r -> r?.let { "${it.left.toInt()}-${it.right.toInt()}@${it.top.toInt()}" } },
        )
        // The layout does not always say so (API 29 puts the hidden Tibetan marks beside one another), so a view that must be cut
        // passes when the screenshot shows its ellipsis; a view the layout calls cut must show one.
        if (expectCut == true && drawn != null) out += if (layoutCut) drawn else "$what is not cut at all or shows no ellipsis ($drawn)"
        if (expectCut == false && layoutCut) out += "$what is cut although it fits in $maxLines lines"
        if (expectCut == null && layoutCut && drawn != null) out += drawn
        return out
    }

    /**
     * Whether the layout hides the end of the text. The hidden characters of an ellipsised view have no width (API 36),
     * no place at all, or all the same place at the end of the last line (API 29 and
     * API 33; on API 29 the last character, the name's full stop, keeps a place of its own beside them). So the end is
     * hidden when the last character has no width or place, or when at least two of the last four pairs share one place.
     * A text that is whole never ends so.
     */
    private fun isCut(view: ShadeReader.ViewRead): Boolean {
        val rects = view.rects
        val last = rects.lastOrNull() ?: return view.rects.isNotEmpty()
        if (last.width() <= 0f) return true
        val stacked = rects.takeLast(4).zipWithNext().count { (a, b) ->
            a != null && b != null && a.left == b.left && a.right == b.right && a.top == b.top
        }
        return stacked >= 2
    }

    /**
     * Looks at the screenshot at the end of the view's last line: the rightmost ink there must be an ellipsis, which is
     * ink low in the line (the dots on the baseline) and none in the middle, within half an em before the last inked
     * column (a letter has ink in the middle). Null when it is; else the finding.
     */
    private fun endsWithEllipsis(what: String, view: ShadeReader.ViewRead, shot: Bitmap): String? {
        val placed = view.rects.filterNotNull()
        if (placed.isEmpty()) return "$what shows no character"
        val top = placed.maxOf { it.top }
        val bottom = minOf(placed.maxOf { it.bottom }, view.screen.bottom.toFloat())
        val h = bottom - top
        if (h <= 0f) return "$what: its last line has no height"
        val bg = shot.getPixel((view.screen.left - 12).coerceAtLeast(0), ((top + bottom) / 2).toInt().coerceIn(0, shot.height - 1))
        fun inked(x: Int, y: Int): Boolean {
            val c = shot.getPixel(x.coerceIn(0, shot.width - 1), y.coerceIn(0, shot.height - 1))
            val diff = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(bg)) +
                Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(bg)) +
                Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(bg))
            return diff > 120
        }
        var right = -1
        for (x in view.screen.right.coerceAtMost(shot.width - 1) downTo view.screen.left) {
            if ((top + h * 0.2f).toInt().until((top + h * 0.9f).toInt()).any { y -> inked(x, y) }) { right = x; break }
        }
        if (right < 0) return "$what: nothing is drawn on its last line"
        val em = h / 1.17f
        val from = (right - em * 0.5f).toInt()
        fun ink(a: Float, b: Float): Int {
            var n = 0
            for (x in from..right) for (y in (top + h * a).toInt() until (top + h * b).toInt()) if (inked(x, y)) n++
            return n
        }
        val low = ink(0.66f, 0.86f)
        val middle = ink(0.30f, 0.58f)
        return if (low < 6 || middle > 0) "$what: its end is not an ellipsis: $low inked pixels on the baseline, $middle in the middle of the line (last line $top to $bottom, ink ends at $right)" else null
    }

    /**
     * The pairs the app joined that the shade breaks: for each joiner in [posted] (a no-break space, or a plain
     * space with a word joiner on each side) the characters before and after it must share a line. A shade
     * text that is not the posted text (apart from no-break spaces and word joiners) is one finding.
     */
    private fun brokenPairs(posted: String, shown: ShadeReader.ShadeText): List<String> {
        val wj = '\u2060'
        val nb = '\u00a0'
        val plain = StringBuilder()
        val glue = mutableListOf<Int>()
        for ((i, c) in posted.withIndex()) {
            if (c == wj) continue
            if (c == nb || (c == ' ' && i > 0 && i + 1 < posted.length && posted[i - 1] == wj && posted[i + 1] == wj)) glue += plain.length
            plain.append(c)
        }
        val shownChars = StringBuilder()
        val tops = mutableListOf<Float?>()
        for ((i, c) in shown.text.withIndex()) {
            if (c == wj) continue
            shownChars.append(c)
            tops += shown.tops[i]
        }
        if (shownChars.toString().replace(nb, ' ') != plain.toString().replace(nb, ' ')) {
            return listOf("the shade text differs: posted \"$plain\", shown \"$shownChars\"")
        }
        val out = mutableListOf<String>()
        for (g in glue) {
            val before = tops.getOrNull(g - 1)
            val after = tops.getOrNull(g + 1)
            if (before == null || after == null) {
                out += "no character place around \"${plain.substring(maxOf(0, g - 8), minOf(plain.length, g + 9))}\" (an unreadable layout counts as a failure)"
            } else if (Math.abs(before - after) > 4f) {
                out += "\"${plain.substring(maxOf(0, g - 8), minOf(plain.length, g + 9))}\" is split over two lines"
            }
        }
        if (out.isNotEmpty()) {
            out += "the shade's text: " + shown.text.map { if (it.code in 32..126) it.toString() else "\\u%04x".format(it.code) }.joinToString("") +
                " ; lines: " + tops.map { it?.toInt() }.joinToString(",")
        }
        return out
    }

    /**
     * The app's own notices by tag. Android groups four or more notices of one app under a summary that SystemUI posts
     * for itself ("Aggregate_AlertingSection"): it is not a notice of the app and arrives a moment after the
     * fourth, so it is left out (R13, D137; API 36 showed it in one run of three).
     */
    private fun held(manager: NotificationManager) =
        manager.activeNotifications
            .filter { it.packageName == target.packageName && it.tag != null && !it.tag.startsWith("0|") && !it.tag.startsWith("g:") && !it.key.contains("Aggregate_") }
            .associateBy { it.tag }

    /** The time of a ring on this phone, in the phone's own 12-hour or 24-hour setting, joined to its marker. */
    fun testTheClockKeepsItsMarker() {
        val text = AlarmService.formatTime(target, 13 * 3_600_000L + 57 * 60_000L)
        assertFalse(text, Regex("""\d [AaPp]""").containsMatchIn(text))
        assertFalse(text, text.contains(' '))
    }

    private fun ringing(
        id: String, label: String, at: Long, kind: AnchorKind,
        placeName: String = "Oslo", anchorText: String = "", usedFallback: Boolean = false,
    ) = RingingAlarm(
        ruleId = id, label = label, placeName = placeName, anchorText = anchorText, fireAtMillis = at,
        usedFallback = usedFallback, isSnooze = false, ringtoneUri = null, vibrate = false, kind = kind,
    )

    private fun assertNoPlainSpaceBeforeMarker(text: String) {
        assertFalse(text, Regex("""\d [AaPp][Mm]\b""").containsMatchIn(text))
        assertFalse(text, text.contains(' '))
    }

    private fun assertWholeLocaleSpan(text: CharSequence) {
        assertTrue("a Spanned text: $text", text is Spanned)
        val spans = (text as Spanned).getSpans(0, text.length, LocaleSpan::class.java)
        assertEquals("one locale span over $text", 1, spans.size)
        assertEquals(0, text.getSpanStart(spans[0]))
        assertEquals(text.length, text.getSpanEnd(spans[0]))
        assertEquals(Locale.ROOT.toLanguageTag(), spans[0].locale?.toLanguageTag())
    }
}
