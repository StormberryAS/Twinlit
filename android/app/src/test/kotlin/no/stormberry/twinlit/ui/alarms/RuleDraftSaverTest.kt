package no.stormberry.twinlit.ui.alarms

import androidx.compose.runtime.saveable.SaverScope
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * An open alarm editor survives a tab switch, a rotation and a process restart (fix round 1): the
 * draft is saved through [RuleDraftSaver], and System Back closes the editor instead of leaving the app.
 */
class RuleDraftSaverTest {

    private val scope = SaverScope { true }
    private val oslo = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)

    private fun roundTrip(draft: RuleDraft?): RuleDraft? {
        val saved = with(RuleDraftSaver) { scope.save(draft) }
        return saved?.let { RuleDraftSaver.restore(it) }
    }

    @Test
    fun `a sun draft with a catalogue place comes back exactly`() {
        val draft = RuleDraft(
            id = "r1", label = "Wake", anchor = Anchor.Sun(SolarEvent.SUNSET), direction = Direction.BEFORE,
            offsetHours = "1", offsetMinutes = "30", place = oslo, clampOpen = true,
            earliest = LocalTime.of(6, 15), latest = LocalTime.of(9, 0), vibrate = false, enabled = false,
            isNew = false, requestedTime = LocalTime.of(7, 30),
        )
        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun `a moon draft with a typed place and its name comes back exactly`() {
        val typed = Place("Cabin", 35.6762, 139.6503, "Asia/Tokyo", fromCatalogue = false, name = "Cabin")
        val draft = RuleDraft(id = "r2", anchor = Anchor.Moon(LunarEvent.FULL_MOON), place = typed)
        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun `a draft with no place and no times comes back with none`() {
        val draft = RuleDraft(id = "r3")
        val back = roundTrip(draft)
        assertEquals(draft, back)
        assertNull(back!!.place)
        assertNull(back.earliest)
    }

    @Test
    fun `no draft saves as nothing, so a closed editor stays closed`() {
        assertNull(with(RuleDraftSaver) { scope.save(null) })
    }

    @Test
    fun `the saved form holds only what a Bundle can keep`() {
        val saved = with(RuleDraftSaver) { scope.save(RuleDraft(id = "r4", place = oslo, earliest = LocalTime.NOON)) }
        assertNotNull(saved)
        assertTrue(saved is java.io.Serializable)
        for (v in (saved as Map<*, *>).values) assertTrue("$v", v == null || v is String || v is Boolean || v is Double)
    }

    @Test
    fun `an unreadable map restores to no editor rather than a half-filled one`() {
        val good = draftToMap(RuleDraft(id = "r5"))
        assertNull(draftFromMap(good + ("kind" to "star")))
        assertNull(draftFromMap(good + ("event" to "NO_SUCH_EVENT")))
        assertNull(draftFromMap(good + ("direction" to "SIDEWAYS")))
        assertNull(draftFromMap(good - "id"))
        assertNull(draftFromMap(good + ("earliest" to "not a time")))
        assertNull(RuleDraftSaver.restore("not a map"))
    }

    @Test
    fun `the Alarms root keeps the draft saveable and Back closes the editor`() {
        val src = TestFiles.require("${TestFiles.KOTLIN}/ui/alarms/AlarmsScreen.kt").readText()
        assertTrue(src.contains("var draft by rememberSaveable(stateSaver = RuleDraftSaver)"))
        assertTrue(src.contains("BackHandler(enabled = editing != null) { draft = null }"))
        // The tab body is composed inside its own SaveableStateProvider, so a tab switch keeps it.
        assertTrue(TestFiles.require("${TestFiles.KOTLIN}/ui/App.kt").readText().contains("tabState.SaveableStateProvider(tab)"))
    }

    @Test
    fun `System Back on the Moon or Alarms tab returns to the Sun tab`() {
        val app = TestFiles.require("${TestFiles.KOTLIN}/ui/App.kt").readText()
        assertTrue(app.contains("BackHandler(enabled = tab != Tab.SUN) { tab = Tab.SUN }"))
        // Registered before the tab bodies, so the editor's own handler (registered later) takes Back first.
        assertTrue(app.indexOf("BackHandler(enabled = tab != Tab.SUN)") < app.indexOf("tabState.SaveableStateProvider(tab)"))
    }
}
