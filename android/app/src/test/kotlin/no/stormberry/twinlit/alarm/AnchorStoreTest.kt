package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.AlarmStore
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The `kind` of an alarm in the store, in the device-protected mirror and in the pending snoozes
 * (SPEC 7.3, D38): a stored rule without `kind` reads as a sun rule, a moon rule round-trips, an
 * unknown kind or a mismatched anchor degrades and is switched off, `PendingSnooze` version 1 reads
 * as sun and version 2 round-trips, and every owed Missed entry carries the kind.
 */
class AnchorStoreTest {

    private fun document(vararg rules: no.stormberry.twinlit.alarm.model.AlarmRule): JSONObject = JSONObject(AlarmStore.encode(rules.toList()))

    private fun firstRule(doc: JSONObject): JSONObject = doc.getJSONArray("rules").getJSONObject(0)

    // ----------------------------------------------------------------------------------- rules

    @Test
    fun `a stored rule without kind reads as a sun rule and stays switched on`() {
        val doc = document(testRule(anchor = Anchor.Sun(SolarEvent.SUNSET)))
        firstRule(doc).remove("kind")

        val rule = AlarmStore.decode(doc.toString()).single()

        assertEquals(Anchor.Sun(SolarEvent.SUNSET), rule.anchor)
        assertEquals(AnchorKind.SUN, rule.kind)
        assertTrue(rule.enabled)
    }

    @Test
    fun `a rule SunApp wrote reads as a sun rule, every one of the fourteen solar events`() {
        for (event in SolarEvent.entries) {
            val doc = document(testRule(anchor = Anchor.Sun(event)))
            firstRule(doc).remove("kind")
            val rule = AlarmStore.decode(doc.toString()).single()
            assertEquals(event.name, Anchor.Sun(event), rule.anchor)
            assertTrue(event.name, rule.enabled)
        }
    }

    @Test
    fun `a moon rule round-trips, every event with every direction`() {
        val rules = LunarEvent.entries.flatMap { event ->
            Direction.entries.map { direction ->
                testMoonRule(event = event, id = "$event-$direction", direction = direction, offsetMinutes = 45)
            }
        }
        val back = AlarmStore.decode(AlarmStore.encode(rules))

        assertEquals(rules, back)
        assertTrue(back.all { it.kind == AnchorKind.MOON })
    }

    @Test
    fun `the stored form names the kind and the event`() {
        val moon = firstRule(document(testMoonRule(event = LunarEvent.FULL_MOON)))
        assertEquals("moon", moon.getString("kind"))
        assertEquals("FULL_MOON", moon.getString("anchor"))
        val sun = firstRule(document(testRule(anchor = Anchor.Sun(SolarEvent.SUNRISE))))
        assertEquals("sun", sun.getString("kind"))
        assertEquals("SUNRISE", sun.getString("anchor"))
    }

    @Test
    fun `the device-protected mirror carries the kind, so a moon alarm rings before the first unlock`() {
        // The mirror is the same encoding (AndroidAlarmSink writes AlarmStore.encode into rules_mirror_json), read back by decode.
        val rules = listOf(testRule(id = "a"), testMoonRule(id = "b", event = LunarEvent.MOONSET))
        val mirror = AlarmStore.encode(rules)
        val rows = JSONObject(mirror).getJSONArray("rules")
        assertEquals(listOf("sun", "moon"), (0 until rows.length()).map { rows.getJSONObject(it).getString("kind") })
        assertEquals(listOf(AnchorKind.SUN, AnchorKind.MOON), AlarmStore.decode(mirror).map { it.kind })
    }

    @Test
    fun `an unknown kind degrades to sunrise and is switched off`() {
        val doc = document(testMoonRule(event = LunarEvent.MOONSET))
        firstRule(doc).put("kind", "planet")

        val rule = AlarmStore.decode(doc.toString()).single()

        assertEquals(Anchor.Sun(SolarEvent.SUNRISE), rule.anchor)
        assertFalse(rule.enabled)
        // Nothing else of the rule is lost, so the editor can repair it.
        assertEquals("moon-rule-1", rule.id)
        assertEquals(59.9139, rule.latDeg, 0.0)
    }

    @Test
    fun `a moon event name under kind sun degrades to sunrise and is switched off`() {
        val doc = document(testRule())
        firstRule(doc).put("anchor", "MOONRISE")

        val rule = AlarmStore.decode(doc.toString()).single()

        assertEquals(Anchor.Sun(SolarEvent.SUNRISE), rule.anchor)
        assertFalse(rule.enabled)
    }

    @Test
    fun `a sun event name under kind moon degrades to moonrise and is switched off`() {
        val doc = document(testMoonRule(event = LunarEvent.NEW_MOON))
        firstRule(doc).put("anchor", "SUNSET")

        val rule = AlarmStore.decode(doc.toString()).single()

        assertEquals(Anchor.Moon(LunarEvent.MOONRISE), rule.anchor)
        assertFalse(rule.enabled)
    }

    @Test
    fun `an unknown event name under either kind degrades to that kind's first event and is switched off`() {
        for ((kind, rule, first) in listOf(
            Triple("sun", testRule(), Anchor.Sun(SolarEvent.SUNRISE)),
            Triple("moon", testMoonRule(), Anchor.Moon(LunarEvent.MOONRISE)),
        )) {
            val doc = document(rule)
            firstRule(doc).put("anchor", "BLUE_MOON_HOUR")
            val read = AlarmStore.decode(doc.toString()).single()
            assertEquals(kind, first, read.anchor)
            assertFalse(kind, read.enabled)
        }
    }

    @Test
    fun `a kind that is not text degrades the rule and nothing throws`() {
        val doc = document(testRule())
        firstRule(doc).put("kind", 7)
        val rule = AlarmStore.decode(doc.toString()).single()
        assertFalse(rule.enabled)
        assertEquals(Anchor.Sun(SolarEvent.SUNRISE), rule.anchor)
    }

    // ------------------------------------------------------------------------------ snoozes

    @Test
    fun `a pending snooze round-trips with its kind in version 2`() {
        val snooze = PendingSnooze("r1", 1_800_000_000_000L, "Up", AnchorKind.MOON)
        val json = snooze.toJson()

        assertEquals(2, json.getInt("v"))
        assertEquals("moon", json.getString("kind"))
        assertEquals(snooze, PendingSnooze.parse(JSONObject(json.toString())))
        assertEquals(snooze, FireRules.decodeSnoozes(FireRules.encodeSnoozes(listOf(snooze))).single())
    }

    @Test
    fun `a sun snooze is written as version 2 with kind sun`() {
        val json = PendingSnooze("r1", 1_800_000_000_000L, "Up").toJson()
        assertEquals(2, json.getInt("v"))
        assertEquals("sun", json.getString("kind"))
        assertEquals(AnchorKind.SUN, PendingSnooze.parse(JSONObject(json.toString()))!!.kind)
    }

    @Test
    fun `a version 1 snooze reads as sun`() {
        val v1 = JSONObject().put("v", 1).put("rule", "r1").put("at", 1_800_000_000_000L).put("label", "Up")
        val read = PendingSnooze.parse(v1)
        assertNotNull(read)
        assertEquals(AnchorKind.SUN, read!!.kind)
        assertEquals(PendingSnooze("r1", 1_800_000_000_000L, "Up"), read)
        // A document SunApp 2.0.0 left behind in the same shape (a list of version 1 rows) is read whole.
        assertEquals(listOf(read), FireRules.decodeSnoozes(JSONArray().put(v1).toString()))
    }

    @Test
    fun `a version 2 snooze with a missing or unknown kind reads as sun`() {
        val noKind = JSONObject().put("v", 2).put("rule", "r1").put("at", 1_800_000_000_000L).put("label", "Up")
        assertEquals(AnchorKind.SUN, PendingSnooze.parse(noKind)!!.kind)
        val unknown = JSONObject().put("v", 2).put("rule", "r1").put("at", 1_800_000_000_000L).put("label", "Up").put("kind", "planet")
        assertEquals(AnchorKind.SUN, PendingSnooze.parse(unknown)!!.kind)
        val notText = JSONObject().put("v", 2).put("rule", "r1").put("at", 1_800_000_000_000L).put("label", "Up").put("kind", 3)
        assertEquals(AnchorKind.SUN, PendingSnooze.parse(notText)!!.kind)
    }

    @Test
    fun `a snooze that is not exactly one this version reads is skipped`() {
        val good = PendingSnooze("r1", 1_800_000_000_000L, "Up", AnchorKind.MOON).toJson()
        val v1WithKind = JSONObject(good.toString()).put("v", 1)
        val extra = JSONObject(good.toString()).put("extra", true)
        val future = JSONObject(good.toString()).put("v", 3)
        val rows = JSONArray().put(good).put(v1WithKind).put(extra).put(future)
        assertEquals(1, FireRules.decodeSnoozes(rows.toString()).size)
        assertNull(PendingSnooze.parse(v1WithKind))
        assertNull(PendingSnooze.parse(extra))
        assertNull(PendingSnooze.parse(future))
    }

    @Test
    fun `an owed Missed entry for a deleted moon rule keeps its kind through the sort`() {
        val now = 1_800_000_000_000L
        val owedMoon = PendingSnooze("deleted-moon", now - 11 * 60_000L, "At moonrise, Oslo", AnchorKind.MOON)
        val owedSun = PendingSnooze("deleted-sun", now - 11 * 60_000L, "At sunrise, Oslo", AnchorKind.SUN)
        val lateMoon = PendingSnooze("live-moon", now - 11 * 60_000L, "At moonset, Oslo", AnchorKind.MOON)

        val sort = FireRules.sortSnoozes(listOf(owedMoon, owedSun, lateMoon), setOf("live-moon"), now, dropLate = true)

        assertEquals(listOf(AnchorKind.MOON, AnchorKind.SUN), sort.gone.map { it.kind })
        assertEquals(listOf(AnchorKind.MOON), sort.late.map { it.kind })
        // And the stored list of owed entries (the same record) round-trips with mixed kinds.
        val owed = FireRules.putSnooze(FireRules.putSnooze(emptyList(), owedMoon), owedSun)
        assertEquals(owed, FireRules.decodeSnoozes(FireRules.encodeSnoozes(owed)))
    }

    // ------------------------------------------------------------------ plan, ring and kind

    @Test
    fun `the planner carries the kind of each rule and of each snooze`() {
        val now = Instant.parse("2026-06-20T22:00:00Z")
        val plan = AlarmPlanner.plan(listOf(testRule(id = "s"), testMoonRule(id = "m", event = LunarEvent.LUNAR_TRANSIT)), now)
        assertEquals(setOf("s" to AnchorKind.SUN, "m" to AnchorKind.MOON), plan.map { it.occurrence.ruleId to it.kind }.toSet())

        val snooze = AlarmPlanner.snoozePlanned(PendingSnooze("m", 1_800_000_000_000L, "x", AnchorKind.MOON), ZoneId.of("Europe/Oslo"))
        assertEquals(AnchorKind.MOON, snooze.kind)
        assertEquals(AlarmPlanner.snoozeRequestCodeFor("m"), snooze.requestCode)
    }

    @Test
    fun `a ring carries its kind and a ring that carries none is a sun ring`() {
        val ring = RingingAlarm(
            ruleId = "m", label = "At moonrise, Oslo", placeName = "Oslo", anchorText = "at moonrise",
            fireAtMillis = 1_000L, usedFallback = false, isSnooze = false, ringtoneUri = null, vibrate = true,
            kind = AnchorKind.MOON,
        )
        assertEquals(AnchorKind.MOON, ring.kind)
        assertEquals(AnchorKind.SUN, ring.copy(kind = AnchorKind.fromStored(null)).kind)
        assertEquals("m@1000", ring.key)
        assertEquals(AnchorKind.SUN, AnchorKind.fromStored("planet"))
        assertEquals(AnchorKind.MOON, AnchorKind.fromStored("moon"))
    }
}
