package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.PlaceDisplay
import no.stormberry.twinlit.data.displayLabel
import no.stormberry.twinlit.data.displayPlaceName
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.ui.alarms.RuleDraft
import no.stormberry.twinlit.alarm.defaultLabel
import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.ui.alarms.toDraft
import no.stormberry.twinlit.ui.alarms.toRule
import no.stormberry.twinlit.widget.TestFiles
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO as OSLO_PLACE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A place the user typed as coordinates is never shown as numbers by an alarm (2.0.0, A6, owner's
 * decision D1 of 2026-11-18): not in the rule's label, not in the list, not in the alarm
 * notification, the backup ring notification or the ring screen, all of which can be read on the
 * lock screen. A catalogue city keeps its name. Examples use catalogue places only.
 */
class TypedCoordinatesAlarmTest {

    private val coordinates = formatCoordinates(OSLO_PLACE.latDeg, OSLO_PLACE.lonDeg)

    private val typed = Place(coordinates, OSLO_PLACE.latDeg, OSLO_PLACE.lonDeg, OSLO_PLACE.zoneId, fromCatalogue = false)

    /** Any decimal number: a coordinate, rounded or not. Times and dates in these texts have none. */
    private val decimal = Regex("""\d[.,]\d""")

    /** The rule as saved by SunApp 1.x for the typed place: the default label held its first half. */
    private val oldTypedRule = testRule(
        label = "At sunrise, ${coordinates.substringBefore(',')}",
        latDeg = OSLO_PLACE.latDeg,
        lonDeg = OSLO_PLACE.lonDeg,
        placeName = coordinates,
    )

    private val cityRule = testRule(
        label = "At sunrise, Oslo",
        latDeg = OSLO_PLACE.latDeg,
        lonDeg = OSLO_PLACE.lonDeg,
        placeName = "Oslo, Norway",
    )

    private fun noDigits(vararg texts: String) {
        for (t in texts) {
            assertFalse(t, decimal.containsMatchIn(t))
            assertFalse(t, t.contains(coordinates))
            assertFalse(t, t.contains(coordinates.substringBefore(',')))
        }
    }

    @Test
    fun `a typed place - the label made for a new rule holds no digit`() {
        val label = defaultLabel(Anchor.Sun(SolarEvent.SUNRISE), Direction.AT, 0, PlaceDisplay.name(typed))
        assertEquals("At sunrise, Your coordinates", label)
        noDigits(label)
        // The editor's placeholder for the empty box uses the same call.
        noDigits(defaultLabel(Anchor.Sun(SolarEvent.SUNSET), Direction.AFTER, 90, PlaceDisplay.name(typed)))
    }

    @Test
    fun `a typed place - the saved rule holds no digit in its label or place name`() {
        val rule = RuleDraft(id = "a", place = typed).toRule()
        assertNotNull(rule)
        assertEquals("Your coordinates", rule!!.placeName)
        assertEquals("At sunrise, Your coordinates", rule.label)
        noDigits(rule.label, rule.placeName, rule.displayLabel(), rule.displayPlaceName())
        // A label the user typed is kept as typed.
        assertEquals("Wake", RuleDraft(id = "a", label = "Wake", place = typed).toRule()!!.label)
    }

    @Test
    fun `a catalogue city keeps its name in the rule`() {
        val rule = RuleDraft(id = "a", place = OSLO_PLACE).toRule()!!
        assertEquals("Oslo, Norway", rule.placeName)
        assertEquals("At sunrise, Oslo", rule.label)
        assertEquals("Oslo, Norway", rule.displayPlaceName())
        assertEquals("At sunrise, Oslo", rule.displayLabel())
    }

    @Test
    fun `a rule saved before 2_0_0 for a typed place is shown without digits`() {
        assertEquals("At sunrise, Your coordinates", oldTypedRule.displayLabel())
        assertEquals("Your coordinates", oldTypedRule.displayPlaceName())
        noDigits(oldTypedRule.displayLabel(), oldTypedRule.displayPlaceName())
        // Opened in the editor: nothing with digits, and saving it again keeps it that way.
        val draft = oldTypedRule.toDraft()
        noDigits(draft.label, draft.place!!.label)
        val saved = draft.toRule()!!
        noDigits(saved.label, saved.placeName)
    }

    @Test
    fun `the ring notification and the ring screen carry no digits of a typed place`() {
        val ringing = AlarmService.alarmFor(
            ruleId = oldTypedRule.id,
            rule = oldTypedRule,
            fireAtMillis = 1_000L,
            usedFallback = true,
            isSnooze = false,
            startedAtMillis = 1_000L,
        )
        // The title of the notification (alarm.label), its summary line (placeName and anchorText)
        // and the ring screen read these fields and nothing else about the place.
        assertEquals("At sunrise, Your coordinates", ringing.label)
        assertEquals("Your coordinates", ringing.placeName)
        noDigits(ringing.label, ringing.placeName, ringing.anchorText)
    }

    @Test
    fun `the ring notification of a catalogue city keeps its name`() {
        val ringing = AlarmService.alarmFor(
            ruleId = cityRule.id,
            rule = cityRule,
            fireAtMillis = 1_000L,
            usedFallback = false,
            isSnooze = false,
            startedAtMillis = 1_000L,
        )
        assertEquals("At sunrise, Oslo", ringing.label)
        assertEquals("Oslo, Norway", ringing.placeName)
    }

    @Test
    fun `no alarm screen or notification prints a rule's label or place name unfiltered`() {
        for (name in listOf("ui/alarms/AlarmsScreen.kt", "ui/alarms/RuleEditorScreen.kt", "alarm/AlarmService.kt", "alarm/RingActivity.kt")) {
            val code = TestFiles.require("${TestFiles.KOTLIN}/$name").readText().lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }.joinToString("\n")
            assertFalse("$name reads rule.label", Regex("""\brule\??\.label\b""").containsMatchIn(code))
            assertFalse("$name reads rule.placeName", Regex("""\brule\??\.placeName\b""").containsMatchIn(code))
            assertFalse("$name uses place.label for a rule", code.contains("placeName = place.label"))
        }
        // The summary line of the notification and of the ring screen shows only the flattened place name.
        // (NoticeText.summary flattens it, R12-F D132; the ring's own place name is the one the service was given.)
        val service = TestFiles.require("${TestFiles.KOTLIN}/alarm/AlarmService.kt").readText()
        assertTrue(service.contains("placeName = alarm.placeName,"))
        assertTrue(TestFiles.require("${TestFiles.KOTLIN}/alarm/NoticeText.kt").readText().contains("val place = flat(placeName)"))
    }
}
