package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.AlarmRule
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.AlarmStore
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.displayLabel
import no.stormberry.twinlit.data.displayPlaceName
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.ui.alarms.PLACE_INTRO_CATALOGUE
import no.stormberry.twinlit.ui.alarms.PLACE_INTRO_TYPED
import no.stormberry.twinlit.ui.alarms.RuleDraft
import no.stormberry.twinlit.alarm.defaultLabel
import no.stormberry.twinlit.ui.formatCoordinates
import no.stormberry.twinlit.ui.alarms.placeIntro
import no.stormberry.twinlit.ui.alarms.toDraft
import no.stormberry.twinlit.ui.alarms.toRule
import no.stormberry.twinlit.widget.TestFiles
import no.stormberry.twinlit.widget.WidgetTestPlaces.OSLO as OSLO_PLACE
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The name of a place typed as coordinates in the alarms (2.0.0, owner's decision W5 of
 * 2026-11-19): a rule saves the name (or "Your coordinates"), shows it whole, and shows the
 * current name of its place while the place is the one the user last looked at, so a rename
 * reaches the list, the notification and the ring screen. A catalogue city is unchanged.
 */
class NamedPlaceAlarmTest {

    private val coordinates = formatCoordinates(OSLO_PLACE.latDeg, OSLO_PLACE.lonDeg)

    private val typed = Place(coordinates, OSLO_PLACE.latDeg, OSLO_PLACE.lonDeg, OSLO_PLACE.zoneId, fromCatalogue = false)

    private val decimal = Regex("""\d[.,]\d""")

    private fun noDigits(vararg texts: String) {
        for (t in texts) {
            assertFalse(t, decimal.containsMatchIn(t))
            assertFalse(t, t.contains(coordinates))
        }
    }

    private fun ringing(rule: AlarmRule, current: Place?) = AlarmService.alarmFor(
        ruleId = rule.id,
        rule = rule,
        fireAtMillis = 1_000L,
        usedFallback = false,
        isSnooze = false,
        startedAtMillis = 1_000L,
        currentPlace = current,
    )

    @Test
    fun `a rule for a named typed place saves the name and the kind`() {
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Bunker")).toRule()
        assertNotNull(rule)
        assertEquals("Bunker", rule!!.placeName)
        assertTrue(rule.placeTyped)
        assertEquals("At sunrise, Bunker", rule.label)
        assertEquals("Bunker", rule.displayPlaceName())
        assertEquals("At sunrise, Bunker", rule.displayLabel())
        noDigits(rule.label, rule.placeName, rule.displayLabel(), rule.displayPlaceName())
    }

    @Test
    fun `a comma in the name is not cut, in the place or in the name the rule gets`() {
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Home, sweet home")).toRule()!!
        assertEquals("Home, sweet home", rule.placeName)
        assertEquals("Home, sweet home", rule.displayPlaceName())
        assertEquals("At sunrise, Home, sweet home", rule.label)
        // The editor's placeholder for the empty box is the same call.
        assertEquals(
            "At sunset, Home, sweet home",
            defaultLabel(Anchor.Sun(SolarEvent.SUNSET), Direction.AT, 0, "Home, sweet home", cutAtComma = false),
        )
        // A catalogue label is still cut at its first comma.
        assertEquals("At sunrise, Oslo", defaultLabel(Anchor.Sun(SolarEvent.SUNRISE), Direction.AT, 0, "Oslo, Norway"))
    }

    @Test
    fun `a typed place with no name is still Your coordinates`() {
        val rule = RuleDraft(id = "a", place = typed).toRule()!!
        assertEquals("Your coordinates", rule.placeName)
        assertTrue(rule.placeTyped)
        assertEquals("At sunrise, Your coordinates", rule.label)
        noDigits(rule.label, rule.placeName, rule.displayLabel(), rule.displayPlaceName())
    }

    @Test
    fun `a catalogue city is unchanged`() {
        val rule = RuleDraft(id = "a", place = OSLO_PLACE).toRule()!!
        assertEquals("Oslo, Norway", rule.placeName)
        assertFalse(rule.placeTyped)
        assertEquals("At sunrise, Oslo", rule.label)
        assertEquals("Oslo, Norway", rule.displayPlaceName())
        // A name on a catalogue place is ignored.
        val named = RuleDraft(id = "a", place = OSLO_PLACE.copy(name = "Home")).toRule()!!
        assertEquals("Oslo, Norway", named.placeName)
        assertFalse(named.placeTyped)
    }

    @Test
    fun `renaming the place renames the rule in the list, the notification and the ring screen`() {
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Bunker")).toRule()!!
        // The place now has another name: shown at once, with no change to the stored rule.
        val renamed = typed.copy(name = "Shelter")
        assertEquals("Shelter", rule.displayPlaceName(renamed))
        assertEquals("Shelter", ringing(rule, renamed).placeName)
        // The name taken away: back to Your coordinates.
        assertEquals("Your coordinates", rule.displayPlaceName(typed))
        assertEquals("Your coordinates", ringing(rule, typed).placeName)
        // The current place cannot be read (a locked device): the saved name.
        assertEquals("Bunker", ringing(rule, null).placeName)
        // The saved name only is what was stored.
        assertEquals("Bunker", rule.placeName)
    }

    @Test
    fun `a name given to the place after the rule was made reaches the rule`() {
        val rule = RuleDraft(id = "a", place = typed).toRule()!!
        assertEquals("Your coordinates", rule.placeName)
        assertEquals("Home", ringing(rule, typed.copy(name = "Home")).placeName)
    }

    @Test
    fun `a rule of another place keeps its name, whatever the current place is called`() {
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Bunker")).toRule()!!
        val elsewhere = typed.copy(latDeg = -33.0, lonDeg = 151.0, name = "Cabin")
        assertEquals("Bunker", ringing(rule, elsewhere).placeName)
        // A catalogue rule never takes a typed place's name, even at the same spot.
        val city = RuleDraft(id = "b", place = OSLO_PLACE).toRule()!!
        assertEquals("Oslo, Norway", ringing(city, typed.copy(name = "Home")).placeName)
    }

    @Test
    fun `no ring text holds digits, named or not`() {
        for (name in listOf("", "Bunker", "Home, sweet home")) {
            val rule = RuleDraft(id = "a", place = typed.copy(name = name)).toRule()!!
            val alarm = ringing(rule, typed.copy(name = name))
            noDigits(alarm.label, alarm.placeName, alarm.anchorText)
            assertFalse(alarm.placeName, alarm.placeName.isBlank())
        }
    }

    @Test
    fun `a name that looks like numbers or coordinates is shown as typed in the list, the editor and the ring texts`() {
        // Owner's decision, 2026-11-19: the name is free text, it is up to the user.
        for (name in listOf("24", "59.9139, 10.7522", "Hut 59.9139", "Cabin, north", coordinates)) {
            val rule = RuleDraft(id = "a", place = typed.copy(name = name)).toRule()!!
            assertTrue(name, rule.placeTyped)
            assertEquals(name, name, rule.placeName)
            assertEquals(name, "At sunrise, $name", rule.label)
            // The list, with the place the user last looked at and without it.
            for (current in listOf(null, typed.copy(name = name))) {
                assertEquals(name, name, rule.displayPlaceName(current))
                assertEquals(name, "At sunrise, $name", rule.displayLabel(current))
                // The notification and the ring screen.
                val alarm = ringing(rule, current)
                assertEquals(name, name, alarm.placeName)
                assertEquals(name, "At sunrise, $name", alarm.label)
            }
            // The editor opens it as it is, and saving gives the same rule back.
            val draft = rule.toDraft()
            assertEquals(name, name, draft.place!!.name)
            // The name the app made starts the editor empty (S11); saving makes the same name again.
            assertEquals(name, "", draft.label)
            assertEquals(name, "At sunrise, $name", draft.toRule()!!.label)
            assertEquals(rule, draft.toRule())
            // And the store keeps it.
            val back = AlarmStore.decode(AlarmStore.encode(listOf(rule))).single()
            assertEquals(name, name, back.displayPlaceName())
            assertEquals(name, "At sunrise, $name", back.displayLabel())
        }
        // A typed place with no name still says so, and a catalogue place is unchanged.
        val unnamed = RuleDraft(id = "b", place = typed).toRule()!!
        assertEquals("Your coordinates", unnamed.displayPlaceName())
        assertEquals("Oslo, Norway", RuleDraft(id = "c", place = OSLO_PLACE).toRule()!!.displayPlaceName())
    }

    @Test
    fun `a rule name the user typed around such a place name is shown as typed`() {
        // The 1.x scrub of coordinates in a label is for rules without the typed marker only: a
        // rule of a typed place keeps the user's text, place name and all (owner's decision, 2026-11-19).
        for (name in listOf("24", "59.9139, 10.7522", coordinates, coordinates.substringBefore(','))) {
            val rule = RuleDraft(id = "a", label = "Wake at $name", place = typed.copy(name = name)).toRule()!!
            assertTrue(name, rule.placeTyped)
            assertEquals(name, "Wake at $name", rule.label)
            for (current in listOf(null, typed.copy(name = name))) {
                assertEquals(name, "Wake at $name", rule.displayLabel(current))
                assertEquals(name, "Wake at $name", ringing(rule, current).label)
                assertEquals(name, "Wake at $name", rule.toDraft(current).label)
            }
            assertEquals(name, "Wake at $name", AlarmStore.decode(AlarmStore.encode(listOf(rule))).single().displayLabel())
        }
    }

    @Test
    fun `the kind and the name survive the store, and an old document has neither`() {
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Home, sweet home")).toRule()!!
        val back = AlarmStore.decode(AlarmStore.encode(listOf(rule))).single()
        assertEquals(rule, back)
        assertTrue(back.placeTyped)
        assertEquals("Home, sweet home", back.displayPlaceName())

        // A catalogue rule is stored as it was before 2.0.0: no key.
        val city = RuleDraft(id = "b", place = OSLO_PLACE).toRule()!!
        val json = JSONObject(AlarmStore.encode(listOf(city))).getJSONArray("rules").getJSONObject(0)
        assertFalse(json.has("placeTyped"))
        assertEquals(city, AlarmStore.decode(AlarmStore.encode(listOf(city))).single())

        // A document written by SunApp before 2.0.0 for a typed place: the coordinates as the place
        // name, no key. It reads as a typed place and is shown without digits.
        val old = JSONObject(AlarmStore.encode(listOf(rule)))
        val oldRule = old.getJSONArray("rules").getJSONObject(0)
        oldRule.remove("placeTyped")
        oldRule.put("placeName", coordinates)
        oldRule.put("label", "At sunrise, ${coordinates.substringBefore(',')}")
        val read = AlarmStore.decode(old.toString()).single()
        assertFalse(read.placeTyped)
        assertEquals("Your coordinates", read.displayPlaceName())
        assertEquals("At sunrise, Your coordinates", read.displayLabel())
        // And gets the current name when the place is the same.
        assertEquals("Home", read.displayPlaceName(typed.copy(name = "Home")))
    }

    @Test
    fun `opening a rule in the editor keeps the typed place and its name, and saving keeps them`() {
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Bunker")).toRule()!!
        val draft = rule.toDraft()
        val place = draft.place!!
        assertFalse(place.fromCatalogue)
        assertEquals("Bunker", place.name)
        noDigits(draft.label, place.label)
        assertEquals(rule, draft.toRule())

        // Opened while the place has a new name: the editor and the saved rule take it.
        val renamed = rule.toDraft(typed.copy(name = "Shelter"))
        assertEquals("Shelter", renamed.place!!.name)
        assertEquals("Shelter", renamed.toRule()!!.placeName)

        // A rule with no name opens with none, and a catalogue rule opens as before.
        val unnamed = RuleDraft(id = "b", place = typed).toRule()!!.toDraft()
        assertEquals("", unnamed.place!!.name)
        assertFalse(unnamed.place!!.fromCatalogue)
        val city = RuleDraft(id = "c", place = OSLO_PLACE).toRule()!!.toDraft()
        assertTrue(city.place!!.fromCatalogue)
        assertEquals("Oslo, Norway", city.place!!.label)
    }

    @Test
    fun `the editor tells the truth about the place, typed coordinates are not a catalogue city`() {
        // The catalogue sentence is true of a city and of no place yet; it is false of typed coordinates.
        assertTrue(PLACE_INTRO_CATALOGUE.contains("city from the bundled catalogue"))
        assertEquals(PLACE_INTRO_CATALOGUE, placeIntro(null))
        assertEquals(PLACE_INTRO_CATALOGUE, placeIntro(OSLO_PLACE))
        assertEquals(PLACE_INTRO_CATALOGUE, RuleDraft(id = "c", place = OSLO_PLACE).toRule()!!.toDraft().place.let(::placeIntro))
        // A typed place, named or not, and a rule of a typed place opened again.
        assertEquals(PLACE_INTRO_TYPED, placeIntro(typed))
        assertEquals(PLACE_INTRO_TYPED, placeIntro(typed.copy(name = "Bunker")))
        val rule = RuleDraft(id = "a", place = typed.copy(name = "Bunker")).toRule()!!
        assertEquals(PLACE_INTRO_TYPED, placeIntro(rule.toDraft().place))
        // A place marked as a catalogue place whose label is its own coordinates is typed, as everywhere else.
        assertEquals(PLACE_INTRO_TYPED, placeIntro(typed.copy(fromCatalogue = true)))
        // The words: coordinates you typed, never the device's location; no city, no catalogue.
        assertEquals(
            "These are coordinates you typed, never your phone's position: Twinlit has no location " +
                "permission to ask for.",
            PLACE_INTRO_TYPED,
        )
        assertFalse(PLACE_INTRO_TYPED.contains("city"))
        assertFalse(PLACE_INTRO_TYPED.contains("catalogue"))
        noDigits(PLACE_INTRO_TYPED, PLACE_INTRO_CATALOGUE)
        // The editor reads the sentence from the draft's place.
        val editor = TestFiles.require("${TestFiles.KOTLIN}/ui/alarms/RuleEditorScreen.kt").readText()
        assertTrue(editor.contains("text = placeIntro(draft.place, draft.kind)"))
    }

    @Test
    fun `a name the app made follows the place name, a name the user edited never changes`() {
        val made = RuleDraft(id = "a", place = typed.copy(name = "Bunker")).toRule()!!
        assertEquals("At sunrise, Bunker", made.label)
        val cabin = typed.copy(name = "Cabin")
        // Renamed: the default follows, in the list, the editor and the ring.
        assertEquals("At sunrise, Cabin", made.displayLabel(cabin))
        // The editor opens it empty (S11), its preview and its saved name being the generated one.
        assertEquals("", made.toDraft(cabin).label)
        assertEquals("At sunrise, Cabin", made.toDraft(cabin).toRule()!!.label)
        assertEquals("At sunrise, Cabin", ringing(made, cabin).label)
        // No name now: Your coordinates, never numbers.
        assertEquals("At sunrise, Your coordinates", made.displayLabel(typed))
        // Without the current place (a locked device) or at another place: the name it was saved with.
        assertEquals("At sunrise, Bunker", made.displayLabel())
        assertEquals("At sunrise, Bunker", made.displayLabel(OSLO_PLACE))
        assertEquals("At sunrise, Bunker", made.displayLabel(typed.copy(latDeg = 10.0, name = "Cabin")))
        // Edited by the user: another text, never changed by a rename.
        val edited = RuleDraft(id = "b", label = "Wake up", place = typed.copy(name = "Bunker")).toRule()!!
        assertEquals("Wake up", edited.displayLabel(cabin))
        val partly = RuleDraft(id = "c", label = "At sunrise, Bunker door", place = typed.copy(name = "Bunker")).toRule()!!
        assertEquals("At sunrise, Bunker door", partly.displayLabel(cabin))
        noDigits(made.displayLabel(cabin), made.displayLabel(typed))
    }

    @Test
    fun `the default name follows with its timing, a comma in the name, and for a catalogue city not at all`() {
        val timed = RuleDraft(id = "a", anchor = Anchor.Sun(SolarEvent.SUNSET), direction = Direction.BEFORE, offsetHours = "1", offsetMinutes = "30", place = typed.copy(name = "Home, sweet home")).toRule()!!
        assertEquals("1 h 30 min before sunset, Home, sweet home", timed.label)
        assertEquals("1 h 30 min before sunset, Cabin", timed.displayLabel(typed.copy(name = "Cabin")))
        // The whole name of the saved rule is not mistaken for a default cut at its comma.
        val cutLike = RuleDraft(id = "b", label = "At sunrise, Home", place = typed.copy(name = "Home, sweet home")).toRule()!!
        assertEquals("At sunrise, Home", cutLike.displayLabel(typed.copy(name = "Cabin")))
        val city = RuleDraft(id = "c", place = OSLO_PLACE).toRule()!!
        assertEquals("At sunrise, Oslo", city.label)
        assertEquals("At sunrise, Oslo", city.displayLabel(typed.copy(name = "Cabin")))
        assertEquals("At sunrise, Oslo", city.displayLabel(OSLO_PLACE))
        val cityEdited = RuleDraft(id = "d", label = "At sunrise, Oslo, Norway", place = OSLO_PLACE).toRule()!!
        assertEquals("At sunrise, Oslo, Norway", cityEdited.displayLabel())
    }

    @Test
    fun `a rule saved before 2_0_0 for a typed place keeps its default name, without the numbers`() {
        val old = AlarmRule(
            id = "o",
            label = "At sunrise, ${coordinates.substringBefore(',')}",
            anchor = Anchor.Sun(SolarEvent.SUNRISE),
            direction = Direction.AT,
            offsetMinutes = 0,
            latDeg = OSLO_PLACE.latDeg,
            lonDeg = OSLO_PLACE.lonDeg,
            zoneId = OSLO_PLACE.zoneId,
            placeName = coordinates,
        )
        assertEquals("At sunrise, Your coordinates", old.displayLabel())
        assertEquals("At sunrise, Cabin", old.displayLabel(typed.copy(name = "Cabin")))
        noDigits(old.displayLabel(), old.displayLabel(typed))
    }

    @Test
    fun `the screens give the current place to every place name they show`() {
        val list = TestFiles.require("${TestFiles.KOTLIN}/ui/alarms/AlarmsScreen.kt").readText()
        assertEquals(2, Regex("""displayPlaceName\(currentPlace\)""").findAll(list).count())
        assertTrue(list.contains("toDraft(defaultPlace)"))
        // Three in the row (edit and delete click labels, the title) and two more since fix round 1 (the switch
        // and the delete control are named for TalkBack).
        assertEquals(5, Regex("""displayLabel\(currentPlace\)""").findAll(list).count())
        assertEquals(1, Regex("""displayLabel\(defaultPlace\)""").findAll(list).count())
        assertFalse(Regex("""displayLabel\(\)""").containsMatchIn(list))
        val service = TestFiles.require("${TestFiles.KOTLIN}/alarm/AlarmService.kt").readText()
        assertTrue(service.contains("rule?.displayLabel(currentPlace)"))
        assertFalse(Regex("""displayPlaceName\(\)""").containsMatchIn(list))
        val receiver = TestFiles.require("${TestFiles.KOTLIN}/alarm/AlarmFireReceiver.kt").readText()
        assertTrue(receiver.contains("currentPlace = Settings.placeOrNull(context)"))
    }
}
