package no.stormberry.twinlit.alarm

import androidx.compose.ui.graphics.Color
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.data.AlarmStore
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kind travels with every ring (D38, SPEC 7.5 and 7.9): the look of the ring screen, the small
 * icon and the words are chosen from the `kind` the ring carries, never by looking the rule up by
 * id, because a lookup fails for a rule deleted after it was armed and before the first unlock,
 * when the mirror has not been read. Every case of SPEC 14.2 is here: a sun ring, a moon ring, a
 * snoozed moon alarm, a moon alarm before the first unlock (from the device-protected mirror), a
 * backup ring for a moon alarm, a Missed notice for a deleted moon rule, and a missing kind, which
 * is the sun look. The look itself is read from plain values, never the themed facade (always dark).
 *
 * The intents and notifications cannot be built on the plain JVM (the framework classes are stubs),
 * so the paths a `kind` takes through them are pinned as source, and the parts that are pure
 * (`RingLook`, `AlarmService.alarmFor`, the store, `PendingSnooze`) are run.
 */
class RingLookTest {

    private fun source(path: String) = TestFiles.require("${TestFiles.KOTLIN}/$path").readText()

    private fun ring(kind: AnchorKind, snooze: Boolean = false, fallback: Boolean = false) = RingingAlarm(
        ruleId = "r", label = "Label", placeName = "Oslo", anchorText = "at moonrise", fireAtMillis = 1_000L,
        usedFallback = fallback, isSnooze = snooze, ringtoneUri = null, vibrate = true, kind = kind,
    )

    // ------------------------------------------------------------------ the look, case by case

    @Test
    fun `a sun ring is SunApp's ring screen exactly, on the Sun Night values`() {
        val look = RingLook.of(ring(AnchorKind.SUN))
        assertEquals(AnchorKind.SUN, look.kind)
        assertEquals(SunNight.Background, look.background)
        assertEquals(SunNight.Gold, look.time)
        assertEquals(SunNight.TextPrimary, look.label)
        assertEquals(SunNight.TextSecondary, look.detail)
        assertEquals(SunNight.TextPrimary, look.snoozeText)
        assertEquals(SunNight.Border, look.snoozeBorder)
        assertEquals(SunNight.Gold, look.dismissContainer)
        assertEquals(SunNight.Background, look.dismissContent)
        assertNull("the sun screen has no emoji", look.emoji)
        assertEquals("Oslo, at moonrise", look.detailLine(ring(AnchorKind.SUN)))
    }

    @Test
    fun `the line under the name keeps each number with its unit, with no word changed (R14, D118)`() {
        val nb = '\u00a0'
        val r = ring(AnchorKind.SUN).copy(anchorText = "5 h 8 min before sunset", isSnooze = true)
        val line = RingLook.of(r).detailLine(r)
        assertEquals("Oslo, 5${nb}h 8${nb}min before sunset, snoozed", line)
        assertEquals("Oslo, 5 h 8 min before sunset, snoozed", line.replace(nb, ' '))
        // A word that only starts like a unit is left alone.
        val w = ring(AnchorKind.MOON).copy(anchorText = "2 hotels away")
        assertEquals("Oslo, 2 hotels away", RingLook.of(w).detailLine(w))
    }

    @Test
    fun `a moon ring is MoonApp's lunar look with SunApp's layout`() {
        val look = RingLook.of(ring(AnchorKind.MOON))
        assertEquals(AnchorKind.MOON, look.kind)
        assertEquals(Color(0xFF0F172A), look.background)
        assertEquals(Color(0xFF38BDF8), look.time)
        assertEquals(Color(0xFFFFFFFF), look.label)
        assertEquals(Color(0xFF94A3B8), look.detail)
        assertEquals(Color(0xFF38BDF8), look.snoozeText)
        assertEquals(Color(0xFF38BDF8), look.snoozeBorder)
        assertEquals(Color(0xFF38BDF8), look.dismissContainer)
        assertEquals(Color(0xFF0F172A), look.dismissContent)
        assertEquals("the full moon above the time", "🌕", look.emoji)
        // "<place>, <anchor text>", the comma of the sun ring and the notifications (SPEC 7.9).
        assertEquals("Oslo, at moonrise", look.detailLine(ring(AnchorKind.MOON)))
        assertNotEquals(RingLook.of(AnchorKind.SUN), look)
    }

    @Test
    fun `a snoozed moon alarm keeps the moon look and says it is snoozed`() {
        val snoozed = ring(AnchorKind.MOON, snooze = true)
        assertEquals(AnchorKind.MOON, RingLook.of(snoozed).kind)
        assertEquals("Oslo, at moonrise, snoozed", RingLook.of(snoozed).detailLine(snoozed))
        // The kind is part of the snooze record (version 2), so it survives the store and a reboot.
        val back = FireRules.decodeSnoozes(FireRules.encodeSnoozes(listOf(PendingSnooze("r", 5L, "Label", AnchorKind.MOON))))
        assertEquals(AnchorKind.MOON, back.single().kind)
        // The snooze the receiver stores takes its kind from the ring's own intent.
        val receiver = source("alarm/AlarmFireReceiver.kt")
        assertTrue(receiver.contains("val kind = AnchorKind.fromStored(intent.getStringExtra(EXTRA_KIND))"))
        assertTrue(receiver.contains("PendingSnooze(ruleId, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, label, kind)"))
        // Snooze, from the ring screen and from the notification, sends the ring's kind.
        assertTrue(source("alarm/RingActivity.kt").contains("alarm.kind,"))
        assertTrue(source("alarm/AlarmService.kt").contains("AlarmFireReceiver.snoozeIntent(context, alarm.ruleId, alarm.label, alarm.kind)"))
    }

    @Test
    fun `a moon alarm before the first unlock keeps its kind, from the mirror and with no rule at all`() {
        // The device-protected mirror is the store's own encoding, which carries kind (AnchorStoreTest).
        val rule = testMoonRule(event = LunarEvent.MOONSET)
        val mirrored = AlarmStore.decode(AlarmStore.encode(listOf(rule))).single()
        assertEquals(AnchorKind.MOON, mirrored.kind)
        val fromMirror = AlarmService.alarmFor("a", mirrored, 1_000L, usedFallback = false, isSnooze = false, startedAtMillis = 1_000L)
        assertEquals(AnchorKind.MOON, fromMirror.kind)
        assertEquals("at moonset", fromMirror.anchorText)
        // Even with no rule readable (a locked device, a mirror not written yet) the ring's own kind decides.
        val lockedNoRule = AlarmService.alarmFor(
            ruleId = "a", rule = null, fireAtMillis = 1_000L, usedFallback = false, isSnooze = false,
            startedAtMillis = 1_000L, kind = AnchorKind.MOON,
        )
        assertEquals(AnchorKind.MOON, lockedNoRule.kind)
        assertEquals(AnchorKind.MOON, RingLook.of(lockedNoRule).kind)
        assertEquals("Twinlit alarm", lockedNoRule.label)
        // The receiver reads the kind from the fire intent, never from the rule it may not find.
        val receiver = source("alarm/AlarmFireReceiver.kt")
        assertTrue(receiver.contains("kind = kind,"))
        // The fire intent, the backstop intent and the Snooze intent each put the kind on the extras.
        assertEquals(3, Regex("""\.putExtra\(EXTRA_KIND, kind\.key\)""").findAll(receiver).count())
        // The sink puts the planned alarm's kind on the PendingIntent, and the planner takes it from the rule.
        val sink = source("alarm/AndroidAlarmSink.kt")
        assertTrue(sink.contains("kind = planned.kind"))
        assertTrue(sink.contains("kind = next.kind"))
        assertTrue(source("alarm/AlarmPlanner.kt").contains("PlannedAlarm(it, requestCodeFor(rule.id), rule.kind)"))
    }

    @Test
    fun `a backup ring for a moon alarm carries the kind in its intents and its icon`() {
        val service = source("alarm/AlarmService.kt")
        // The alarm rides in the extras of the backup ring's screen intent and of its gone intent, kind included.
        assertTrue(service.contains(".putExtra(AlarmFireReceiver.EXTRA_KIND, alarm.kind.key)"))
        assertTrue(service.contains("kind = AnchorKind.fromStored(intent.getStringExtra(AlarmFireReceiver.EXTRA_KIND))"))
        assertEquals("the ring intent, the screen intent and the gone intent all go through putAlarm", 3, Regex("""putAlarm\(""").findAll(service).count() - 1)
        // The small icon of the foreground ring and of the backup ring is chosen from the ring's kind.
        assertEquals(2, Regex("""\.setSmallIcon\(smallIconFor\(alarm\.kind\)\)""").findAll(service).count())
        assertNotEquals(AlarmService.smallIconFor(AnchorKind.SUN), AlarmService.smallIconFor(AnchorKind.MOON))
        // And the ring screen shows what the backup carried: it is the same RingingAlarm.
        assertTrue(source("alarm/RingActivity.kt").contains("RingLook.of(alarm?.kind ?: AnchorKind.SUN)"))
    }

    @Test
    fun `a Missed notice for a deleted moon rule keeps its icon, from the kind in the owed entry`() {
        // The owed entries are PendingSnooze records: the kind is in them, so no rule is needed.
        val owed = PendingSnooze("deleted-rule", 7L, "Moon walk", AnchorKind.MOON)
        assertEquals(AnchorKind.MOON, PendingSnooze.parse(owed.toJson())!!.kind)
        val sink = source("alarm/AndroidAlarmSink.kt")
        assertTrue(sink.contains("AlarmService.postNotRungNotice(context, it.ruleId, it.label, it.kind)"))
        assertTrue(sink.contains("AlarmService.postReplacedNotice(context, it.ruleId.takeIf { id -> id != UNKNOWN_RULE }, it.label, it.at, it.kind)"))
        assertTrue(sink.contains("kind = alarm.kind,"))
        // All three notices (snoozed, missed, replaced) pick the icon from the kind they are given.
        val service = source("alarm/AlarmService.kt")
        assertTrue(service.contains(".setSmallIcon(smallIconFor(snooze.kind))"))
        assertTrue(service.contains(".setSmallIcon(smallIconFor(kind))"))
        assertTrue(service.contains("postMissed(context, alarm.ruleId, alarm.label, \"Rang at ${'$'}{formatTime(context, alarm.fireAtMillis)} for ${'$'}minutes minutes with no response.\", alarm.kind)"))
        // No notice or ring is chosen by looking a rule up: the service never reads the rule store.
        assertFalse(service.contains("AlarmScheduling.ruleFor"))
        assertFalse(service.contains("AlarmStore"))
        assertFalse(service.contains("SharedPreferencesRuleFile"))
    }

    @Test
    fun `a missing kind is the sun look`() {
        assertEquals(AnchorKind.SUN, AnchorKind.fromStored(null))
        assertEquals(AnchorKind.SUN, AnchorKind.fromStored("planet"))
        assertEquals(AnchorKind.SUN, RingingAlarm("r", "l", "", "", 1L, false, false, null, true).kind)
        assertEquals(RingLook.of(AnchorKind.SUN), RingLook.of(RingingAlarm("r", "l", "", "", 1L, false, false, null, true)))
        // A snooze without a kind (version 1) reads as sun; the receiver's default is the same.
        val v1 = org.json.JSONObject().put("v", 1).put("rule", "r").put("at", 5L).put("label", "x")
        assertEquals(AnchorKind.SUN, PendingSnooze.parse(v1)!!.kind)
        assertTrue(source("alarm/AlarmService.kt").contains("AnchorKind.fromStored(intent.getStringExtra(AlarmFireReceiver.EXTRA_KIND))"))
        // alarmFor with no rule and no kind is a sun ring.
        val bare = AlarmService.alarmFor("r", null, 1L, usedFallback = false, isSnooze = false, startedAtMillis = 1L)
        assertEquals(AnchorKind.SUN, bare.kind)
    }

    // ----------------------------------------------------------- always dark, never a light palette

    @Test
    fun `the ring screen reads the plain values and never a themed facade or a light palette`() {
        val activity = source("alarm/RingActivity.kt")
        val code = activity.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }.joinToString("\n")
        // No Sun. or Moon. token in the ring code: every colour comes from the look or from SunNight.
        assertFalse(Regex("""\bSun\.[A-Z]""").containsMatchIn(code))
        assertFalse(Regex("""\bMoon\.[A-Z]""").containsMatchIn(code))
        assertTrue(code.contains("TwinlitTheme(SunNight)"))
        // A moon ring takes Material's colour scheme from the Moon palette, not the Sun one (fix round 1).
        assertTrue(code.contains("TwinlitTheme(MoonNight)"))
        assertTrue(code.contains("if (kind == AnchorKind.MOON)"))
        assertTrue(code.contains("enableDarkEdgeToEdge()"))
        assertFalse(code.contains("SunLight") || code.contains("TwilightLight") || code.contains("MoonLight"))
        val look = source("alarm/RingLook.kt")
        assertFalse(Regex("""\bSun\.[A-Z]""").containsMatchIn(look))
        assertTrue(look.contains("import no.stormberry.twinlit.ui.theme.SunNight"))
        // Both looks are dark: every background is darker than half grey.
        for (kind in AnchorKind.entries) {
            val bg = RingLook.of(kind).background
            assertTrue("$kind background", 0.2126 * bg.red + 0.7152 * bg.green + 0.0722 * bg.blue < 0.1)
        }
    }

    @Test
    fun `the sun's polar fallback line is shown for sun rings only`() {
        val code = source("alarm/RingActivity.kt")
        assertTrue(code.contains("if (alarm.usedFallback && alarm.kind == AnchorKind.SUN)"))
        // And the notification sentence adds it for sun rules only.
        assertTrue(source("alarm/AlarmService.kt").contains("sunFallback = alarm.usedFallback && alarm.kind == AnchorKind.SUN"))
    }

    @Test
    fun `the moon emoji is decorative on the ring screen`() {
        assertTrue(source("alarm/RingActivity.kt").contains("Modifier.clearAndSetSemantics { }"))
    }

    @Test
    fun `the ring screen keeps SunApp's mechanics for both looks`() {
        val code = source("alarm/RingActivity.kt")
        for (needle in listOf(
            "setShowWhenLocked(true)", "setTurnScreenOn(true)", "FLAG_KEEP_SCREEN_ON", "OnBackPressedCallback(true)",
            "TextAutoSize.StepBased", "WindowInsets.safeDrawing", "verticalScroll(rememberScrollState())",
            "SnoozeButton(onSnooze, look, Modifier.weight(1f))", "DismissButton(onDismiss, look, Modifier.weight(1f), minHeight = 56.dp)",
            "minHeight = 64.dp", "Snooze \${AlarmFireReceiver.SNOOZE_MINUTES} minutes", "text = \"Dismiss\"",
        )) assertTrue(needle, code.contains(needle))
    }
}
