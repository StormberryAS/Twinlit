package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R10 (D108 to D110): the Missed and Snoozed notices are built from [NoticeText] and carry no
 * hyphenation; every clock that [AlarmService.formatTime] prints passes through [NoticeText.clock].
 * A source test, because the notification classes are stubs on the plain JVM (as
 * [RingNotificationSourceTest]); the posted notices are read back by the device test `NoticeDeviceTest`.
 */
class NoticeSourceTest {

    private val service = strip(TestFiles.require("${TestFiles.KOTLIN}/alarm/AlarmService.kt").readText())
    private val spans = strip(TestFiles.require("${TestFiles.KOTLIN}/alarm/NoticeSpans.kt").readText())

    @Test
    fun `the Snoozed notice has the fixed title and the name first in the text`() {
        val body = body("postSnoozedNotice")
        assertTrue(body.contains(".setContentTitle(NoticeText.SNOOZED_TITLE)"))
        assertFalse("the name is no longer in the title", body.contains("snooze.label} snoozed"))
        assertTrue(body.contains("val sentence = \"Ringing again at \${formatTime(context, snooze.at)}.\""))
        assertTrue(body.contains("withoutHyphenation(NoticeText.named(snooze.label, sentence))"))
        assertTrue(body.contains("val parts = NoticeText.parts(snooze.label, sentence, ::noticeWidth)"))
    }

    @Test
    fun `every Missed notice has the fixed title and the name first in the text`() {
        val body = body("postMissed")
        assertTrue(body.contains(".setContentTitle(NoticeText.MISSED_TITLE)"))
        assertFalse("the name is no longer in the title", body.contains("Missed \$label"))
        assertTrue(body.contains("val text = withoutHyphenation(NoticeText.named(label, body))"))
        // Missed, Replaced and "could not ring" all go through the one function.
        for (name in listOf("postMissedNotice", "postReplacedNotice", "postNotRungNotice")) {
            assertTrue("$name posts through postMissed", body(name).contains("postMissed("))
        }
    }

    @Test
    fun `the old title forms are gone`() {
        assertFalse(service.contains("\"Missed \$"))
        assertFalse(service.contains("} snoozed\""))
    }

    @Test
    fun `the clock keeps its marker with its time`() {
        assertTrue(service.substringAfter("fun formatTime(").substringBefore("/**").contains("NoticeText.clock(DateFormat.getTimeFormat(context).format(Date(millis)))"))
    }

    @Test
    fun `the notice text has no hyphenation, through a locale span over the whole text`() {
        assertTrue(spans.contains("setSpan(LocaleSpan(Locale.ROOT), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)"))
        // The same CharSequence is the collapsed text and the text of the expanded view.
        for (name in listOf("postSnoozedNotice", "postMissed")) {
            val b = body(name)
            assertTrue("$name sets the text without hyphenation", b.contains("val text = withoutHyphenation("))
            assertTrue(b.contains(".setContentText(text)"))
        }
    }

    @Test
    fun `the expanded notice is the notice's own view, never the BigText template (R14 D117, R12 D123)`() {
        // Android 16's BigText template rewrites no-break spaces to plain spaces and drops word joiners before
        // it wraps the text; the device test reads the shade and proves it. A text in our own view is not rewritten.
        // The ring and backup ring notices have it too (R12, D123); no notice of the app uses BigText.
        assertFalse("no BigTextStyle anywhere in the service", service.contains("BigTextStyle"))
        for ((name, args) in listOf(
            "postSnoozedNotice" to "NoticeText.SNOOZED_TITLE, parts.sentence, parts.name",
            "postMissed" to "NoticeText.MISSED_TITLE, parts.sentence, parts.name",
            "buildRingNotification" to "alarm, summary",
            "buildBackupNotification" to "alarm, summary",
        )) {
            val b = body(name)
            assertTrue("$name sets the decorated custom view style", b.contains(".setStyle(NotificationCompat.DecoratedCustomViewStyle())"))
            val call = if (name.startsWith("build")) "ringBigView(context, $args)" else "noticeBigView(context, $args)"
            assertTrue("$name sets the expanded view ($call)", b.contains(".setCustomBigContentView($call)"))
        }
        // The ring notices' view is the same own view, with the alarm's name (one line) as its title and the summary in its parts.
        assertTrue(service.substringAfter("fun ringBigView(").substringBefore("fun noticeWidth(").contains("noticeBigView(context, NoticeText.flat(alarm.label), summary.rest, lead = summary.lead, place = summary.place)"))
        // Everything else of the ring notices stays (R12, D123): the channel, the category, the full-screen intent,
        // the priority, the actions, the title (the alarm's name, D109) and the collapsed text.
        for ((name, channel) in listOf("buildRingNotification" to "CHANNEL_RING", "buildBackupNotification" to "CHANNEL_RING_BACKUP")) {
            val b = body(name)
            assertTrue(b.contains("Builder(context, $channel)"))
            assertTrue(b.contains(".setContentTitle(alarm.label)"))
            assertTrue(b.contains(".setContentText(summary.joined())"))
            assertTrue(b.contains(".setCategory(NotificationCompat.CATEGORY_ALARM)"))
            assertTrue(b.contains(".setPriority(NotificationCompat.PRIORITY_MAX)"))
            assertTrue(b.contains(".setFullScreenIntent(ring, true)"))
            assertTrue(b.contains(".addAction(0, \"Snooze \${AlarmFireReceiver.SNOOZE_MINUTES} min\", snoozePendingIntent(context, alarm))"))
            assertTrue(b.contains(".addAction(0, \"Dismiss\", dismissPendingIntent(context, alarm.ruleId))"))
            assertTrue(b.contains(".setSmallIcon(smallIconFor(alarm.kind))"))
        }
        assertTrue(body("buildRingNotification").contains(".setOngoing(true)"))
        val view = service.substringAfter("fun noticeBigView(").substringBefore("fun ringBigView(")
        assertTrue(view.contains("R.layout.notice_big_text"))
        assertTrue(view.contains("setTextViewText(R.id.notice_title, title)"))
        assertTrue(view.contains("setTextViewText(R.id.notice_name, name)"))
        assertTrue(view.contains("setTextViewText(R.id.notice_text, text)"))
        val layout = TestFiles.require("${TestFiles.RES}/layout/notice_big_text.xml").readText().replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(layout.contains("@+id/notice_title"))
        val textView = layout.substringAfter("@+id/notice_text")
        assertTrue("hyphenation is off in the view", textView.contains("android:hyphenationFrequency=\"none\""))
    }

    @Test
    fun `the sentence with the time is never cut, a name or a place gives way with an ellipsis (R12 D124, R12-F D130 to D132)`() {
        // The sentence (notice_text) and the time of a ring notice (notice_lead) have no line limit and no ellipsis,
        // so they are always whole; a long name has its own view (notice_name) of at most three lines, a long place
        // of a ring notice has its own (notice_place) of at most two, each ending with an ellipsis; the title is one
        // line with an ellipsis. The 256 dp of the Android documentation is a design budget, not a limit the shade
        // enforces (D131): the shade drew longer views whole.
        val layout = TestFiles.require("${TestFiles.RES}/layout/notice_big_text.xml").readText().replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        fun view(id: String) = layout.substringAfter("@+id/$id").substringBefore("/>")
        for (id in listOf("notice_text", "notice_lead")) {
            val v = view(id)
            assertTrue("$id wraps, never cut", !v.contains("maxLines") && !v.contains("ellipsize") && !v.contains("singleLine"))
        }
        val name = view("notice_name")
        assertTrue("the name ends with an ellipsis", name.contains("android:ellipsize=\"end\""))
        assertTrue("the name has at most three lines", name.contains("android:maxLines=\"3\""))
        assertTrue("the name view is hidden until a long name needs it", name.contains("android:visibility=\"gone\""))
        assertTrue("the name keeps hyphenation off", name.contains("android:hyphenationFrequency=\"none\""))
        val place = view("notice_place")
        assertTrue("the place ends with an ellipsis", place.contains("android:ellipsize=\"end\""))
        assertTrue("the place has at most two lines", place.contains("android:maxLines=\"2\""))
        assertTrue("the place view is hidden until a long place needs it", place.contains("android:visibility=\"gone\""))
        assertTrue("the lead is hidden until a long place needs it", view("notice_lead").contains("android:visibility=\"gone\""))
        // A script with a tall fall-back font (Tibetan) cannot make a limited view taller than its lines (D132).
        for ((id, v) in listOf("notice_name" to name, "notice_place" to place)) {
            assertTrue("$id has fallback line spacing off", v.contains("android:fallbackLineSpacing=\"false\""))
        }
        val title = view("notice_title")
        assertTrue(title.contains("android:ellipsize=\"end\"") && title.contains("android:maxLines=\"1\""))
        // Missed and Snoozed split the text through NoticeText.parts, and only a long name gets the view.
        for (n in listOf("postSnoozedNotice", "postMissed")) {
            assertTrue("$n splits the text with NoticeText.parts", body(n).contains("NoticeText.parts("))
        }
        val view = service.substringAfter("fun noticeBigView(").substringBefore("fun ringBigView(")
        assertTrue(view.contains("if (name != null)"))
        assertTrue(view.contains("setViewVisibility(R.id.notice_name, View.VISIBLE)"))
        assertTrue(view.contains("if (lead != null && place != null)"))
        assertTrue(view.contains("setViewVisibility(R.id.notice_place, View.VISIBLE)"))
        // The ring notices split the summary through NoticeText.summary, with the measured width.
        val summary = service.substringAfter("fun summaryParts(").substringBefore("fun formatTime(")
        assertTrue(summary.contains("NoticeText.summary("))
        assertTrue(summary.contains("width = ::noticeWidth"))
        assertTrue(service.contains("fun summaryFor(context: Context, alarm: RingingAlarm): String = summaryParts(context, alarm).joined()"))
        val notice = TestFiles.require("${TestFiles.KOTLIN}/alarm/NoticeText.kt").readText()
        assertTrue(notice.contains("const val FLOW_NAME_MAX = 32"))
        assertTrue(notice.contains("text.length <= FLOW_NAME_MAX && width(text) <= width(\"W\".repeat(FLOW_NAME_MAX))"))
        assertTrue(notice.contains("if (flows(name, width)) return Parts(null, named(label, text))"))
        assertTrue(notice.contains("if (place.isEmpty() || flows(place, width))"))
    }

    @Test
    fun `the summary of the ring notices is the one text of the collapsed and the expanded notice`() {
        // The collapsed text is the parts joined; the expanded view shows the same parts (lead, place, rest).
        for (name in listOf("buildRingNotification", "buildBackupNotification")) {
            val b = body(name)
            assertTrue(b.contains("val summary = summaryParts(context, alarm)"))
            assertTrue(b.contains(".setContentText(summary.joined())"))
        }
    }

    @Test
    fun `the ring screen and the ring notices keep each number with its unit (R14, D118)`() {
        val look = strip(TestFiles.require("${TestFiles.KOTLIN}/alarm/RingLook.kt").readText())
        assertTrue(look.substringAfter("fun detailLine(").contains("NoticeText.units(listOfNotNull("))
        val ring = strip(TestFiles.require("${TestFiles.KOTLIN}/alarm/RingActivity.kt").readText())
        assertTrue("the ring name", ring.contains("text = NoticeText.units(alarm.label)"))
        assertTrue("the sub-line comes from detailLine", ring.contains("look.detailLine(alarm)"))
        // The one summary of the ring notice and of the backup ring notice.
        assertTrue(service.substringAfter("fun summaryParts(").substringBefore("fun formatTime(").contains("NoticeText.summary("))
    }

    private fun body(name: String): String {
        val start = service.indexOf("fun $name(")
        assertTrue("fun $name not found", start >= 0)
        val open = service.indexOf('{', service.indexOf(')', start))
        var depth = 0
        for (i in open until service.length) {
            when (service[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return service.substring(open, i + 1)
            }
        }
        error("unbalanced braces after fun $name")
    }

    private fun strip(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { line ->
                val at = line.indexOf("//")
                if (at >= 0) line.substring(0, at) else line
            }
}
