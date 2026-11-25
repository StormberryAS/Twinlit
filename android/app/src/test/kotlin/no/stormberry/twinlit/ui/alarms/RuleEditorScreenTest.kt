package no.stormberry.twinlit.ui.alarms

import no.stormberry.twinlit.alarm.LONGYEARBYEN_LAT
import no.stormberry.twinlit.alarm.LONGYEARBYEN_LON
import no.stormberry.twinlit.alarm.OSLO_LAT
import no.stormberry.twinlit.alarm.OSLO_LON
import no.stormberry.twinlit.alarm.OccurrenceEngine
import no.stormberry.twinlit.alarm.anchorLabel
import no.stormberry.twinlit.alarm.defaultLabel
import no.stormberry.twinlit.alarm.describeTiming
import no.stormberry.twinlit.alarm.formatOffset
import no.stormberry.twinlit.alarm.PreviewDay
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.alarm.model.Clamp
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.alarm.testMoonRule
import no.stormberry.twinlit.alarm.testRule
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.lunar.LunarEvent
import no.stormberry.twinlit.solar.SolarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The pure half of the rule editor: the draft-to-rule mapping, the offset parsing, the
 * sentences, and the reduction that turns thirty occurrences into the one number the screen
 * is built around.
 *
 * The solar figures below are not snapshots of this code. Each was produced by running the
 * web app's own bundled `suncalc.js` under node with
 * `Intl.DateTimeFormat('en-GB', { hour12: false, timeZone: 'Europe/Oslo' })`, which is what
 * sun.stormberry.as prints, so a disagreement here is a real disagreement between the two
 * surfaces rather than a stale expectation.
 */
class RuleEditorScreenTest {

    private val oslo: ZoneId = ZoneId.of("Europe/Oslo")
    private val osloPlace = Place("Oslo, Norway", OSLO_LAT, OSLO_LON, "Europe/Oslo", true)

    /* -------------------------------------------------------------- *
     * Owner's confirmed decision 5
     * -------------------------------------------------------------- */

    @Test
    fun `the picker offers exactly three anchors, in solar order`() {
        assertEquals(
            listOf(SolarEvent.SUNRISE, SolarEvent.SOLAR_NOON, SolarEvent.SUNSET),
            EDITOR_ANCHORS,
        )
    }

    @Test
    fun `the preview window is thirty days`() {
        assertEquals(30, PREVIEW_DAYS)
    }

    /* -------------------------------------------------------------- *
     * Offsets
     * -------------------------------------------------------------- */

    @Test
    fun `offset boxes combine into minutes`() {
        assertEquals(90, parseOffsetMinutes("1", "30"))
        assertEquals(0, parseOffsetMinutes("0", "0"))
        assertEquals(45, parseOffsetMinutes("", "45"))
        assertEquals(120, parseOffsetMinutes("2", ""))
    }

    @Test
    fun `an offset that is not a plain number is rejected rather than guessed at`() {
        assertNull(parseOffsetMinutes("x", "0"))
        // The sign belongs to Direction. A minus typed here would mean the opposite of the
        // button the user pressed, so it is refused rather than folded through abs.
        assertNull(parseOffsetMinutes("-1", "0"))
        assertNull(parseOffsetMinutes("0", "60"))
        assertNull(parseOffsetMinutes("37", "0"))
    }

    @Test
    fun `offsets read as English`() {
        assertEquals("0 min", formatOffset(0))
        assertEquals("45 min", formatOffset(45))
        assertEquals("1 h", formatOffset(60))
        assertEquals("1 h 30 min", formatOffset(90))
    }

    @Test
    fun `timing sentences match the three-way control`() {
        assertEquals("At sunrise", describeTiming(Anchor.Sun(SolarEvent.SUNRISE), Direction.AT, 0))
        assertEquals("30 min before sunrise", describeTiming(Anchor.Sun(SolarEvent.SUNRISE), Direction.BEFORE, 30))
        assertEquals("1 h 15 min after sunset", describeTiming(Anchor.Sun(SolarEvent.SUNSET), Direction.AFTER, 75))
        // A magnitude of zero is "at", whichever side is selected: "0 min after sunset" is
        // not a thing anybody means.
        assertEquals("At sunset", describeTiming(Anchor.Sun(SolarEvent.SUNSET), Direction.AFTER, 0))
        assertEquals("At solar noon", describeTiming(Anchor.Sun(SolarEvent.SOLAR_NOON), Direction.AT, 0))
    }

    @Test
    fun `an unnamed rule is labelled from what it does and where`() {
        assertEquals(
            "30 min before sunrise, Oslo",
            defaultLabel(Anchor.Sun(SolarEvent.SUNRISE), Direction.BEFORE, 30, "Oslo, Norway"),
        )
        assertEquals("At sunset", defaultLabel(Anchor.Sun(SolarEvent.SUNSET), Direction.AT, 0, ""))
    }

    /* -------------------------------------------------------------- *
     * Draft to rule and back
     * -------------------------------------------------------------- */

    @Test
    fun `a draft without a place cannot be saved`() {
        assertNull(RuleDraft(id = "a").toRule())
    }

    @Test
    fun `a draft becomes the rule the engine will schedule`() {
        val rule = RuleDraft(
            id = "a",
            label = "  Wake  ",
            anchor = Anchor.Sun(SolarEvent.SUNSET),
            direction = Direction.BEFORE,
            offsetHours = "1",
            offsetMinutes = "5",
            place = osloPlace,
        ).toRule()

        assertNotNull(rule)
        assertEquals("Wake", rule!!.label)
        assertEquals(Anchor.Sun(SolarEvent.SUNSET), rule.anchor)
        assertEquals(Direction.BEFORE, rule.direction)
        assertEquals(65, rule.offsetMinutes)
        assertEquals(-65, rule.signedOffsetMinutes)
        assertEquals("Europe/Oslo", rule.zoneId)
        assertEquals("Oslo, Norway", rule.placeName)
        // Owner's confirmed decision 2: no clamp unless one was asked for.
        assertNull(rule.clamp)
    }

    @Test
    fun `an open but empty clamp section is not a clamp`() {
        val rule = RuleDraft(id = "a", place = osloPlace, clampOpen = true).toRule()
        assertNull(rule!!.clamp)
    }

    @Test
    fun `crossed clamp bounds block the save rather than being silently reordered`() {
        // The engine resolves this deterministically (the ceiling is applied last and wins),
        // but a rule that means the opposite of what its two boxes say must not be creatable.
        val draft = RuleDraft(
            id = "a",
            place = osloPlace,
            earliest = LocalTime.of(9, 0),
            latest = LocalTime.of(7, 0),
        )
        assertNull(draft.toRule())
    }

    @Test
    fun `opening a stored rule and saving it again changes nothing`() {
        val original = testRule(
            id = "keep-me",
            label = "Sunset walk",
            anchor = Anchor.Sun(SolarEvent.SUNSET),
            direction = Direction.AFTER,
            offsetMinutes = 95,
            placeName = "Oslo, Norway",
            clamp = Clamp(earliest = LocalTime.of(6, 30), latest = LocalTime.of(8, 0)),
            vibrate = false,
        )
        assertEquals(original, original.toDraft().toRule())
    }

    @Test
    fun `an anchor the picker does not offer survives being opened`() {
        // Eleven of the fourteen events are not on the picker. Opening a rule that uses one
        // must not quietly rewrite it to sunrise: that would be data loss disguised as a
        // simplification.
        val original = testRule(anchor = Anchor.Sun(SolarEvent.NAUTICAL_DAWN), placeName = "Oslo, Norway")
        assertEquals(Anchor.Sun(SolarEvent.NAUTICAL_DAWN), original.toDraft().anchor)
        assertEquals(original, original.toDraft().toRule())
    }

    /* -------------------------------------------------------------- *
     * The preview summary, which is the whole mitigation for having no clamp
     * -------------------------------------------------------------- */

    @Test
    fun `an Oslo sunrise rule drifts more than an hour in a month`() {
        val rule = testRule(anchor = Anchor.Sun(SolarEvent.SUNRISE), direction = Direction.AT)
        val rows = OccurrenceEngine.preview(rule, LocalDate.of(2026, 8, 21), PREVIEW_DAYS)
        val summary = summarisePreview(rows, oslo)

        // suncalc.js under node (the vendored tools/golden/suncalc.js, noon-UTC anchoring, Oslo
        // 59.9139 N 10.7522 E): 2026-08-21 sunrise 05:46 Europe/Oslo, 2026-09-19 sunrise 06:54.
        // Sixty-eight minutes of drift inside one preview window is exactly the surprise the
        // owner's decision 2 says the preview exists to remove.
        assertEquals(30, summary.rows.size)
        assertEquals(LocalDate.of(2026, 8, 21), summary.earliest!!.anchorDate)
        assertEquals(LocalDate.of(2026, 9, 19), summary.latest!!.anchorDate)
        assertEquals(68, summary.spreadMinutes)
        assertEquals(0, summary.fallbackDays)
        assertEquals(0, summary.clampedDays)
    }

    @Test
    fun `a window that crosses midnight measures as minutes, not as most of a day`() {
        // Sunset plus four hours in Oslo rings at 00:56 on 21 August and at 23:29 on 19
        // September (suncalc.js under node), so the fire time crosses back over midnight inside
        // the window. Compared as absolute minutes past midnight that is a spread of 22 hours
        // 33 minutes, which would make the headline number of the whole screen nonsense.
        val rule = testRule(
            anchor = Anchor.Sun(SolarEvent.SUNSET),
            direction = Direction.AFTER,
            offsetMinutes = 240,
        )
        val rows = OccurrenceEngine.preview(rule, LocalDate.of(2026, 8, 21), PREVIEW_DAYS)
        val summary = summarisePreview(rows, oslo)

        assertEquals(87, summary.spreadMinutes)
        assertEquals("00:56", formatFireClock(rows.first().fireAt, oslo))
        assertEquals("23:29", formatFireClock(rows.last().fireAt, oslo))
    }

    @Test
    fun `polar night is counted, not hidden`() {
        // Longyearbyen has no sunrise on any day in December, so every row in the window
        // falls back to solar noon and the editor has thirty warnings to show rather than
        // thirty missing alarms.
        val rule = testRule(
            anchor = Anchor.Sun(SolarEvent.SUNRISE),
            latDeg = LONGYEARBYEN_LAT,
            lonDeg = LONGYEARBYEN_LON,
            placeName = "Longyearbyen, Norway",
        )
        val summary = summarisePreview(
            OccurrenceEngine.preview(rule, LocalDate.of(2026, 12, 1), PREVIEW_DAYS),
            oslo,
        )
        assertEquals(30, summary.fallbackDays)
        assertTrue(summary.rows.all { it.usedFallback })
    }

    @Test
    fun `an empty preview reduces to nothing rather than throwing`() {
        val summary = summarisePreview(emptyList(), oslo)
        assertNull(summary.earliest)
        assertNull(summary.latest)
        assertEquals(0, summary.spreadMinutes)
    }

    @Test
    fun `the summary carries the engine's own rows through untouched`() {
        // The preview must never be a second implementation of the occurrence arithmetic.
        // Identity, not equality: these are the objects OccurrenceEngine produced.
        val rows = OccurrenceEngine.preview(testRule(), LocalDate.of(2026, 8, 21), PREVIEW_DAYS)
        assertSame(rows, summarisePreview(rows, oslo).rows)
    }

    @Test
    fun `differences fold into plus or minus twelve hours`() {
        assertEquals(0, wrapHalfDay(0))
        assertEquals(10, wrapHalfDay(10))
        assertEquals(-10, wrapHalfDay(1430))
        assertEquals(-10, wrapHalfDay(-10))
        assertEquals(719, wrapHalfDay(719))
        assertEquals(-720, wrapHalfDay(720))
    }

    /* -------------------------------------------------------------- *
     * The AlarmClock SET_ALARM bridge
     * -------------------------------------------------------------- */

    @Test
    fun `matching a requested wall time lands on it today and then lets go`() {
        val draft = RuleDraft(id = "a", anchor = Anchor.Sun(SolarEvent.SUNRISE), place = osloPlace)
        val matched = matchRequestedTime(draft, LocalTime.of(7, 0))
        val rule = matched.toRule()!!

        val today = LocalDate.now(oslo)
        val fired = OccurrenceEngine.occurrenceFor(rule, today)!!.fireAt.atZone(oslo).toLocalTime()
        // Offsets are whole minutes and sunrise has seconds, so a minute of slack is the
        // exact resolution of the thing being asserted.
        assertTrue(
            "fired at $fired",
            Duration.between(LocalTime.of(7, 0), fired).abs().toMinutes() <= 1,
        )
        // Consumed: the offer must not still be on screen after it has been taken.
        assertNull(matched.requestedTime)
    }

    @Test
    fun `there is nothing to match against without a place`() {
        val draft = RuleDraft(id = "a", requestedTime = LocalTime.of(7, 0))
        assertEquals(draft, matchRequestedTime(draft, LocalTime.of(7, 0)))
    }

    /* -------------------------------------------------------------- *
     * The moon in the editor (SPEC 7.7, D47)
     * -------------------------------------------------------------- */

    private val moonDraft = RuleDraft(id = "m", anchor = Anchor.Moon(LunarEvent.MOONRISE), place = osloPlace)

    @Test
    fun `the picker offers the five moon events, daily ones first`() {
        assertEquals(
            listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET, LunarEvent.LUNAR_TRANSIT, LunarEvent.FULL_MOON, LunarEvent.NEW_MOON),
            EDITOR_MOON_ANCHORS,
        )
        assertEquals(
            listOf("Moonrise", "Moonset", "Moon at its highest", "Full moon night", "New moon night"),
            EDITOR_MOON_ANCHORS.map { it.displayName },
        )
    }

    @Test
    fun `switching the kind keeps direction, offset, place and limits, and takes the first anchor of the new kind`() {
        val sun = RuleDraft(
            id = "a", label = "Walk", anchor = Anchor.Sun(SolarEvent.SUNSET), direction = Direction.BEFORE,
            offsetHours = "1", offsetMinutes = "20", place = osloPlace, earliest = LocalTime.of(6, 0), latest = LocalTime.of(9, 0),
            vibrate = false,
        )
        val moon = sun.withKind(AnchorKind.MOON)
        assertEquals(Anchor.Moon(LunarEvent.MOONRISE), moon.anchor)
        assertEquals(AnchorKind.MOON, moon.kind)
        assertEquals(sun.copy(anchor = Anchor.Moon(LunarEvent.MOONRISE)), moon)
        // And back: sunrise, the first sun anchor.
        assertEquals(Anchor.Sun(SolarEvent.SUNRISE), moon.withKind(AnchorKind.SUN).anchor)
        // The same kind changes nothing, so choosing the chip already chosen keeps a picked anchor.
        val set = sun.copy(anchor = Anchor.Moon(LunarEvent.NEW_MOON))
        assertSame(set, set.withKind(AnchorKind.MOON))
    }

    @Test
    fun `a moon draft becomes a moon rule and opens again unchanged`() {
        val rule = moonDraft.copy(
            anchor = Anchor.Moon(LunarEvent.FULL_MOON), direction = Direction.AFTER, offsetHours = "0", offsetMinutes = "30",
        ).toRule()!!
        assertEquals(Anchor.Moon(LunarEvent.FULL_MOON), rule.anchor)
        assertEquals(AnchorKind.MOON, rule.kind)
        assertEquals("30 min after the full moon night time, Oslo", rule.label)
        assertEquals(rule, rule.toDraft().toRule())
        // The generated name follows the event while the field is empty.
        assertEquals("", rule.toDraft().label)
        assertEquals("At moonset, Oslo", rule.toDraft().copy(anchor = Anchor.Moon(LunarEvent.MOONSET), direction = Direction.AT).toRule()!!.label)
    }

    @Test
    fun `the three preview groups`() {
        assertEquals(PreviewGroup.SUN, previewGroup(Anchor.Sun(SolarEvent.SUNRISE)))
        assertEquals(PreviewGroup.SUN, previewGroup(Anchor.Sun(SolarEvent.NAUTICAL_DAWN)))
        for (daily in listOf(LunarEvent.MOONRISE, LunarEvent.MOONSET, LunarEvent.LUNAR_TRANSIT)) {
            assertEquals(PreviewGroup.DAILY_MOON, previewGroup(Anchor.Moon(daily)))
        }
        for (monthly in listOf(LunarEvent.FULL_MOON, LunarEvent.NEW_MOON)) {
            assertEquals(PreviewGroup.MONTHLY_MOON, previewGroup(Anchor.Moon(monthly)))
        }
    }

    @Test
    fun `a daily moon preview is thirty rows with the missing days marked, every row an occurrenceFor result`() {
        // Helsinki 2026-01-02 has no culmination (the website's own days, transit-parity.csv).
        val rule = testMoonRule(event = LunarEvent.LUNAR_TRANSIT, latDeg = 60.1699, lonDeg = 24.9384, zoneId = "Europe/Helsinki", placeName = "Helsinki")
        val from = LocalDate.of(2026, 1, 1)
        val preview = editorPreview(rule, from, ZoneId.of("Europe/Helsinki"))
        assertEquals(PreviewGroup.DAILY_MOON, preview.group)
        assertEquals(30, preview.rows.size)
        assertEquals(1, preview.missingDays)
        assertNull(preview.rows.single { it.date == LocalDate.of(2026, 1, 2) }.occurrence)
        for (row in preview.rows) assertEquals("${row.date}", OccurrenceEngine.occurrenceFor(rule, row.date), row.occurrence)
        // The summary counts the rows that exist, and the card says what the missing day is (G-29, G-30).
        assertEquals(29, preview.summary.rows.size)
        assertEquals("No highest point of the moon on 1 of the next 30 days", moonMissingTitle(LunarEvent.LUNAR_TRANSIT, preview.rows))
    }

    @Test
    fun `the missing-day card names the day when it is the first row and counts otherwise`() {
        val day = LocalDate.of(2026, 1, 1)
        val occurrence = OccurrenceEngine.occurrenceFor(testMoonRule(event = LunarEvent.MOONRISE), day)
        val firstMissing = listOf(PreviewDay(day, null), PreviewDay(day.plusDays(1), occurrence))
        assertEquals("No moonrise on Thu 1 Jan", moonMissingTitle(LunarEvent.MOONRISE, firstMissing))
        val laterMissing = listOf(PreviewDay(day, occurrence), PreviewDay(day.plusDays(1), null))
        assertEquals("No moonrise on 1 of the next 30 days", moonMissingTitle(LunarEvent.MOONRISE, laterMissing))
    }

    @Test
    fun `a monthly moon preview is the next three nights and nothing else`() {
        for (event in listOf(LunarEvent.FULL_MOON, LunarEvent.NEW_MOON)) {
            val rule = testMoonRule(event = event)
            val preview = editorPreview(rule, LocalDate.of(2027, 1, 10), oslo)
            assertEquals(PreviewGroup.MONTHLY_MOON, preview.group)
            assertEquals(3, preview.rows.size)
            assertEquals(0, preview.missingDays)
            assertTrue(preview.rows.all { it.occurrence != null })
            for (row in preview.rows) assertEquals(OccurrenceEngine.occurrenceFor(rule, row.date), row.occurrence)
        }
        assertEquals("Next three full moon nights", monthlyHeading(LunarEvent.FULL_MOON))
        assertEquals("Next three new moon nights", monthlyHeading(LunarEvent.NEW_MOON))
    }

    @Test
    fun `a sun preview is unchanged, thirty rows and no marked days`() {
        val rule = testRule()
        val preview = editorPreview(rule, LocalDate.of(2026, 8, 21), oslo)
        assertEquals(PreviewGroup.SUN, preview.group)
        assertEquals(30, preview.rows.size)
        assertEquals(0, preview.missingDays)
        assertEquals(68, preview.summary.spreadMinutes)
    }

    @Test
    fun `the moon's preview texts are TEXTS_md G-29, G-30, G-31, G-32, G-51 and G-53`() {
        assertEquals(
            "Moonrise comes later each day, by about 50 minutes on average. The daily step changes with the place and the season, so read the rows below.",
            moonDriftLine(LunarEvent.MOONRISE),
        )
        assertEquals("Moonset comes later each day, by about 50 minutes on average. The daily step changes with the place and the season, so read the rows below.", moonDriftLine(LunarEvent.MOONSET))
        assertTrue(moonDriftLine(LunarEvent.LUNAR_TRANSIT).startsWith("The moon's highest point comes later each day, by about 50 minutes on average"))
        assertEquals(
            "There is no moonrise on 2 of these days at Oslo, Norway. That is normal: the moon's times come later each day, " +
                "so now and then a calendar day has none, because one falls just before midnight and the next just after it. On those days this alarm does " +
                "not ring. The rows below are marked.",
            moonMissingBody(LunarEvent.MOONRISE, 2, "Oslo, Norway"),
        )
        assertEquals("Clamped on 4 of 30 days", clampedTitle(PreviewGroup.DAILY_MOON, 4))
        assertEquals("Clamped on 1 of 30 days", clampedTitle(PreviewGroup.SUN, 1))
        assertEquals("Clamped on 2 of these 3 nights", clampedTitle(PreviewGroup.MONTHLY_MOON, 2))
        assertEquals("On those days the bound decides the time, not the moon.", clampedBody(AnchorKind.MOON))
        assertEquals("On those days the bound decides the time, not the sun.", clampedBody(AnchorKind.SUN))
        assertEquals("Off. The alarm follows the moon.", clampOffText(AnchorKind.MOON))
        assertEquals("Off. The alarm follows the sun.", clampOffText(AnchorKind.SUN))
        assertTrue(clampIntro(AnchorKind.MOON).startsWith("A limit stops the alarm following the moon past a wall-clock time."))
        assertEquals("The moon's times move later each day, by about 50 minutes on average, so a limit decides the time on many days.", CLAMP_MOON_DRIFT)
        assertEquals("Choose a city before saving. The moon has to be measured somewhere.", saveBlockedReason(moonDraft.copy(place = null), 0))
        assertEquals("Choose a city before saving. The sun has to be measured somewhere.", saveBlockedReason(RuleDraft(id = "a"), 0))
    }

    @Test
    fun `the place line says moonrise for a moon rule and stays true of typed coordinates`() {
        assertEquals(PLACE_INTRO_CATALOGUE, placeIntro(null))
        assertEquals(PLACE_INTRO_CATALOGUE_MOON, placeIntro(null, AnchorKind.MOON))
        assertTrue(PLACE_INTRO_CATALOGUE_MOON.startsWith("An alarm needs a place before its moon time can be worked out."))
        val typed = Place("59.9139 N, 10.7522 E", OSLO_LAT, OSLO_LON, "Europe/Oslo", fromCatalogue = false)
        assertEquals(PLACE_INTRO_TYPED, placeIntro(typed, AnchorKind.MOON))
    }

    @Test
    fun `a fixed wall time from another app is matched against the sun only`() {
        val draft = moonDraft.copy(requestedTime = LocalTime.of(7, 0))
        assertEquals(draft, matchRequestedTime(draft, LocalTime.of(7, 0)))
    }

    @Test
    fun `a moon rule with limits keeps them, and a limit is read on the fire time's own day`() {
        val original = testMoonRule(event = LunarEvent.MOONSET, clamp = Clamp(earliest = LocalTime.of(20, 0), latest = null), label = "Night")
        assertEquals(original, original.toDraft().toRule())
        assertTrue(original.toDraft().clampOpen)
    }

    // R13 (D139): Marcos's review found a name of 203 characters that autocorrect had saved; the field holds the
    // one label limit the app has, PendingSnooze.MAX_LABEL.
    @Test
    fun `the name field holds back what is typed past the one label limit`() {
        val max = no.stormberry.twinlit.alarm.PendingSnooze.MAX_LABEL
        assertEquals(200, max)
        assertEquals("Wake", clampLabel("Wake"))
        assertEquals("a".repeat(max), clampLabel("a".repeat(max)))
        assertEquals("a".repeat(max), clampLabel("a".repeat(203)))
        assertEquals(max, clampLabel("word ".repeat(60)).length)
        // A pair of UTF-16 units is never cut in two: a face on the 200th place is dropped whole.
        val face = "\uD83D\uDE00"
        val cut = clampLabel("a".repeat(max - 1) + face + "tail")
        assertEquals("a".repeat(max - 1), cut)
        assertTrue(cut.none { it.isSurrogate() })
        assertEquals("a".repeat(max - 2) + face, clampLabel("a".repeat(max - 2) + face + "tail"))
    }

    @Test
    fun `a name over the limit, from an intent or saved earlier, is cut and trimmed when the draft becomes a rule`() {
        val long = "  " + "b".repeat(203) + "  "
        val rule = RuleDraft(id = "a", label = long, place = osloPlace).toRule()!!
        assertEquals("b".repeat(200), rule.label)
        // A cut that leaves a space at its end is trimmed too.
        val spaced = RuleDraft(id = "a", label = "c".repeat(199) + " d", place = osloPlace).toRule()!!
        assertEquals("c".repeat(199), spaced.label)
        // A name of exactly the limit is kept whole; a blank name still falls back to the generated one.
        assertEquals("e".repeat(200), RuleDraft(id = "a", label = "e".repeat(200), place = osloPlace).toRule()!!.label)
        assertTrue(RuleDraft(id = "a", label = "   ", place = osloPlace).toRule()!!.label.isNotEmpty())
    }

    @Test
    fun `the name field source clamps every change`() {
        val src = no.stormberry.twinlit.widget.TestFiles.require("${no.stormberry.twinlit.widget.TestFiles.KOTLIN}/ui/alarms/RuleEditorScreen.kt").readText()
        assertTrue(src.contains("onValueChange = { onDraftChange(draft.copy(label = clampLabel(it))) }"))
        assertTrue(src.contains("label = clampLabel(label.trim()).trim().ifEmpty {"))
        assertTrue(src.contains("internal fun clampLabel(text: String): String"))
        assertTrue(src.contains("PendingSnooze.MAX_LABEL"))
    }
}
