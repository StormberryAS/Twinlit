package no.stormberry.twinlit.ui.alarms

import no.stormberry.twinlit.alarm.defaultLabel
import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.alarm.testRule
import no.stormberry.twinlit.data.AlarmStore
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.displayLabel
import no.stormberry.twinlit.data.hasGeneratedLabel
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.widget.TestFiles
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO
import no.stormberry.twinlit.widget.WidgetTestPlaces.TROMSO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0, S11. A rule name that the app made ("At sunrise, Oslo") follows the event, the
 * direction, the offset and the place in the editor, as the name of a new rule does. A name the
 * user typed never changes. A rule that 1.1.8 saved with the generated name is treated the same,
 * because nothing is stored to tell the two apart. Saving keeps the name following a renamed
 * place (S6).
 */
class GeneratedNameEditorTest {

    private val typedPlace = Place(
        formatCoordinates(OSLO.latDeg, OSLO.lonDeg), OSLO.latDeg, OSLO.lonDeg, OSLO.zoneId,
        fromCatalogue = false, name = "Bunker",
    )

    /** What the editor saves for a draft that has no typed name, built the way a new rule is. */
    private fun newRule(anchor: SolarEvent = SolarEvent.SUNRISE, place: Place = OSLO) =
        RuleDraft(id = "n", anchor = Anchor.Sun(anchor), place = place).toRule()!!

    @Test
    fun `an opened rule with a generated name starts with the name field empty`() {
        val rule = newRule()
        assertEquals("At sunrise, Oslo", rule.label)
        assertTrue(rule.hasGeneratedLabel())
        assertEquals("", rule.toDraft().label)
        // A typed place too, named or not.
        val named = newRule(place = typedPlace)
        assertEquals("At sunrise, Bunker", named.label)
        assertEquals("", named.toDraft().label)
        val unnamed = newRule(place = typedPlace.copy(name = ""))
        assertEquals("At sunrise, Your coordinates", unnamed.label)
        assertEquals("", unnamed.toDraft().label)
    }

    @Test
    fun `a rule that 1_1_8 saved with the generated name is treated the same`() {
        // Stored by 1.1.8: the label is the default made at the time, the place name the catalogue label.
        val oldCatalogue = testRule(label = "At sunset, Oslo", anchor = Anchor.Sun(SolarEvent.SUNSET), placeName = "Oslo, Norway",
            latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertTrue(oldCatalogue.hasGeneratedLabel())
        assertEquals("", oldCatalogue.toDraft().label)
        val oldOffset = testRule(label = "1 h 30 min before sunrise, Oslo", anchor = Anchor.Sun(SolarEvent.SUNRISE),
            direction = Direction.BEFORE, offsetMinutes = 90, placeName = "Oslo, Norway",
            latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertEquals("", oldOffset.toDraft().label)
        // A typed place of 1.1.8: the stored name is the coordinates, the default label is cut at the comma.
        val coordinates = formatCoordinates(OSLO.latDeg, OSLO.lonDeg)
        val oldTyped = testRule(label = "At sunrise, ${coordinates.substringBefore(',')}", placeName = coordinates,
            latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertTrue(oldTyped.hasGeneratedLabel())
        assertEquals("", oldTyped.toDraft().label)
    }

    @Test
    fun `a name the user typed is shown as typed and never changes`() {
        val typed = testRule(label = "Sunset walk", anchor = Anchor.Sun(SolarEvent.SUNSET), placeName = "Oslo, Norway",
            latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertFalse(typed.hasGeneratedLabel())
        val draft = typed.toDraft()
        assertEquals("Sunset walk", draft.label)
        // Every kind of change leaves it alone.
        for (changed in listOf(
            draft.copy(anchor = Anchor.Sun(SolarEvent.SUNRISE)),
            draft.copy(direction = Direction.BEFORE, offsetHours = "1", offsetMinutes = "15"),
            draft.copy(place = TROMSO),
        )) assertEquals("Sunset walk", changed.toRule()!!.label)
        // Not even a typed name that only looks like a generated one with another place or event.
        val nearly = testRule(label = "At sunrise, Helsinki", anchor = Anchor.Sun(SolarEvent.SUNRISE), placeName = "Oslo, Norway",
            latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertFalse(nearly.hasGeneratedLabel())
        assertEquals("At sunrise, Helsinki", nearly.toDraft().label)
        val wrongEvent = testRule(label = "At sunset, Oslo", anchor = Anchor.Sun(SolarEvent.SUNRISE), placeName = "Oslo, Norway",
            latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertFalse(wrongEvent.hasGeneratedLabel())
        assertEquals("At sunset, Oslo", wrongEvent.toDraft().label)
    }

    @Test
    fun `changing the event gives the generated name of the new event`() {
        val draft = newRule().toDraft()
        assertEquals("At sunset, Oslo", draft.copy(anchor = Anchor.Sun(SolarEvent.SUNSET)).toRule()!!.label)
        assertEquals("At solar noon, Oslo", draft.copy(anchor = Anchor.Sun(SolarEvent.SOLAR_NOON)).toRule()!!.label)
        // The preview in the empty field is the same text.
        val sunset = draft.copy(anchor = Anchor.Sun(SolarEvent.SUNSET))
        assertEquals(sunset.toRule()!!.label, defaultLabel(sunset.anchor, sunset.direction, 0, no.stormberry.twinlit.data.PlaceDisplay.name(sunset.place!!)))
    }

    @Test
    fun `changing the direction or the offset gives the generated name of the new timing`() {
        val draft = newRule(SolarEvent.SUNSET).toDraft()
        val before = draft.copy(direction = Direction.BEFORE, offsetHours = "1", offsetMinutes = "44")
        assertEquals("1 h 44 min before sunset, Oslo", before.toRule()!!.label)
        val after = draft.copy(direction = Direction.AFTER, offsetHours = "0", offsetMinutes = "20")
        assertEquals("20 min after sunset, Oslo", after.toRule()!!.label)
        // A rule with an offset keeps following when the offset changes again.
        val stored = before.toRule()!!
        assertTrue(stored.hasGeneratedLabel())
        assertEquals("30 min before sunset, Oslo", stored.toDraft().copy(offsetHours = "0", offsetMinutes = "30").toRule()!!.label)
        assertEquals("At sunset, Oslo", stored.toDraft().copy(direction = Direction.AT).toRule()!!.label)
    }

    @Test
    fun `changing the place gives the generated name of the new place`() {
        val draft = newRule().toDraft()
        assertEquals("At sunrise, Tromsø", draft.copy(place = TROMSO).toRule()!!.label)
        assertEquals("At sunrise, Bunker", draft.copy(place = typedPlace).toRule()!!.label)
    }

    @Test
    fun `a rule opened and saved without a change is the same rule`() {
        for (rule in listOf(newRule(), newRule(SolarEvent.SUNSET, typedPlace), newRule(SolarEvent.SOLAR_NOON, typedPlace.copy(name = "")))) {
            assertEquals(rule, rule.toDraft().toRule())
        }
    }

    @Test
    fun `a rule changed in the editor and saved is stored under the new generated name`() {
        val original = newRule()
        val saved = original.toDraft().copy(anchor = Anchor.Sun(SolarEvent.SUNSET)).toRule()!!
        assertEquals("At sunset, Oslo", saved.label)
        // Through the store, as the app does.
        val back = AlarmStore.decode(AlarmStore.encode(listOf(saved))).single()
        assertEquals(saved, back)
        assertEquals("At sunset, Oslo", back.displayLabel())
        // Still generated, so a second edit follows again.
        assertTrue(back.hasGeneratedLabel())
        assertEquals("At sunrise, Oslo", back.toDraft().copy(anchor = Anchor.Sun(SolarEvent.SUNRISE)).toRule()!!.label)
    }

    @Test
    fun `a saved rule keeps following a renamed place`() {
        // S6 after S11: the rule was changed in the editor, then the typed place was renamed.
        val before = newRule(place = typedPlace).toDraft(typedPlace)
            .copy(anchor = Anchor.Sun(SolarEvent.SUNSET), direction = Direction.BEFORE, offsetHours = "1", offsetMinutes = "0")
        val saved: AlarmRule = before.toRule()!!
        assertEquals("1 h before sunset, Bunker", saved.label)
        val renamed = typedPlace.copy(name = "Cabin")
        assertEquals("1 h before sunset, Cabin", saved.displayLabel(renamed))
        // Opened while the place has the new name, saved again: the stored name takes it.
        val reopened = saved.toDraft(renamed)
        assertEquals("", reopened.label)
        assertEquals("1 h before sunset, Cabin", reopened.toRule()!!.label)
        // A typed name never follows.
        val typedName = saved.copy(label = "Wake me")
        assertEquals("Wake me", typedName.displayLabel(renamed))
        assertEquals("Wake me", typedName.toDraft(renamed).toRule()!!.label)
    }

    @Test
    fun `a field the user empties is the generated name, as for a new rule`() {
        val typed = testRule(label = "Wake me", placeName = "Oslo, Norway", latDeg = OSLO.latDeg, lonDeg = OSLO.lonDeg)
        assertEquals("At sunrise, Oslo", typed.toDraft().copy(label = "").toRule()!!.label)
        assertEquals("At sunrise, Oslo", typed.toDraft().copy(label = "   ").toRule()!!.label)
    }

    @Test
    fun `the editor shows the generated name as the preview of an empty field`() {
        val editor = TestFiles.require("${TestFiles.KOTLIN}/ui/alarms/RuleEditorScreen.kt").readText()
        assertTrue(editor.contains("label = if (hasGeneratedLabel()) \"\" else displayLabel(current)"))
        // The empty field shows the name the rule will be saved under, and the save falls back to it.
        assertTrue(editor.contains("placeholder = {"))
        assertTrue(editor.contains("clampLabel(label.trim()).trim().ifEmpty { defaultLabel("))
        // The one test is shared with the list, so the two never disagree.
        val display = TestFiles.require("${TestFiles.KOTLIN}/data/PlaceDisplay.kt").readText()
        assertEquals(2, Regex("""hasGeneratedLabel\(\)""").findAll(display).count())
    }

}
