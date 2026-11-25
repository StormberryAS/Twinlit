package no.stormberry.twinlit.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R10 (D108 to D111): the words of the Missed and Snoozed notices (D23 option A, Marcos 2026-11-22), the
 * join of the alarm's name and the text, and the no-break spaces. Every test here fails on the code of
 * round 9, which had the titles "Missed {label}" and "{label} snoozed", and plain spaces.
 */
class NoticeTextTest {

    private val nbsp = ' '

    @Test
    fun `the two titles are the approved fixed phrases`() {
        assertEquals("Missed alarm", NoticeText.MISSED_TITLE)
        assertEquals("Alarm snoozed", NoticeText.SNOOZED_TITLE)
        // Short enough that no template cuts them, even at the largest font on a narrow phone.
        assertTrue(NoticeText.MISSED_TITLE.length <= 30)
        assertTrue(NoticeText.SNOOZED_TITLE.length <= 30)
    }

    @Test
    fun `the text opens with the alarm name, then the text the notice showed before`() {
        assertEquals(
            "At sunrise, Oslo. Ringing again at 5:17${nbsp}PM.",
            NoticeText.named("At sunrise, Oslo", "Ringing again at 5:17${nbsp}PM."),
        )
        assertEquals(
            "1${nbsp}h 42${nbsp}min after moonrise, Oslo. Rang at 4:57${nbsp}PM for 10${nbsp}minutes with no response.",
            NoticeText.named("1 h 42 min after moonrise, Oslo", "Rang at 4:57${nbsp}PM for 10 minutes with no response."),
        )
    }

    @Test
    fun `a name that already ends a sentence gets no second full stop`() {
        assertEquals("Wake up! Ringing again at 07:10.", NoticeText.named("Wake up!", "Ringing again at 07:10."))
        assertEquals("Gym. Ringing again at 07:10.", NoticeText.named("Gym.", "Ringing again at 07:10."))
        assertEquals("Up? Ringing again at 07:10.", NoticeText.named("Up?", "Ringing again at 07:10."))
        assertEquals("Up… Ringing again at 07:10.", NoticeText.named("Up…", "Ringing again at 07:10."))
        assertEquals("Gym. Ringing again at 07:10.", NoticeText.named("  Gym  ", "Ringing again at 07:10."))
    }

    @Test
    fun `a blank name leaves the text alone`() {
        assertEquals("Ringing again at 07:10.", NoticeText.named("", "Ringing again at 07:10."))
        assertEquals("Ringing again at 07:10.", NoticeText.named("   ", "Ringing again at 07:10."))
    }

    @Test
    fun `the collapsed one line text starts with the name and reads as two sentences`() {
        val line = NoticeText.named("At sunset, Oslo", "The phone could not ring this alarm on time.")
        assertTrue(line.startsWith("At sunset, Oslo. "))
        assertFalse("one line, no line feed", line.contains('\n'))
        assertEquals(2, line.split(". ").size)
    }

    @Test
    fun `a time keeps its AM or PM marker, whether the phone prints a plain or a narrow no-break space`() {
        assertEquals("4:57${nbsp}PM", NoticeText.clock("4:57 PM"))
        assertEquals("12:05${nbsp}AM", NoticeText.clock("12:05 AM"))
        assertEquals("16:57", NoticeText.clock("16:57"))
        assertFalse(NoticeText.clock("4:57 PM").contains(' '))
    }

    @Test
    fun `a number keeps its unit, and only a number and its unit`() {
        assertEquals("for 10${nbsp}minutes with", NoticeText.units("for 10 minutes with"))
        assertEquals("3${nbsp}h 29${nbsp}min", NoticeText.units("3 h 29 min"))
        assertEquals("1${nbsp}minute", NoticeText.units("1 minute"))
        assertEquals("2${nbsp}hours 3${nbsp}days", NoticeText.units("2 hours 3 days"))
        // The space between "h" and the next number stays a plain space: the line may break there.
        assertTrue(NoticeText.units("3 h 29 min").contains("h 29"))
        // Not a unit: a word that only starts like one, and a number followed by another word.
        assertEquals("5 hot days", NoticeText.units("5 hot days"))
        assertEquals("at 5 PM", NoticeText.units("at 5 PM"))
        assertEquals("Oslo 2 minutesx", NoticeText.units("Oslo 2 minutesx"))
    }

    @Test
    fun `no word changes`() {
        val samples = listOf(
            "Rang at 4:57 PM for 10 minutes with no response.",
            "Ringing again at 5:17 PM.",
            "3 h 29 min after moonrise, Oslo",
            "The phone could not ring this alarm on time.",
        )
        for (text in samples) {
            val out = NoticeText.named("30 min before sunset, Oslo", NoticeText.clock(text))
            val back = out.replace(nbsp, ' ')
            assertEquals(
                "30 min before sunset, Oslo. " + text,
                back,
            )
        }
    }

    // R12 (D124): a long typed name has its own view; the words stay the same.

    @Test
    fun `a name that fits the flow is not split from the sentence`() {
        for (label in listOf("", "  ", "At sunrise, Oslo", "W".repeat(NoticeText.FLOW_NAME_MAX), "1 h 42 min after moonrise, Oslo")) {
            val parts = NoticeText.parts(label, "Ringing again at 5:17${nbsp}PM.")
            assertEquals("no own view for \"$label\"", null, parts.name)
            assertEquals(NoticeText.named(label, "Ringing again at 5:17${nbsp}PM."), parts.sentence)
        }
    }

    @Test
    fun `a longer name goes to its own view and the sentence is only the sentence`() {
        val typed = "Wake me up at the right time for the long hike to the mountain cabin"
        val parts = NoticeText.parts(typed, "Ringing again at 5:17${nbsp}PM.")
        assertEquals("$typed.", parts.name)
        assertEquals("Ringing again at 5:17${nbsp}PM.", parts.sentence)
        val wide = "W".repeat(NoticeText.FLOW_NAME_MAX + 1)
        assertEquals("$wide.", NoticeText.parts(wide, "x").name)
        // A name that ends a sentence gets no second full stop, as in the flow.
        assertEquals("$typed!", NoticeText.parts("$typed!", "x").name)
        assertEquals("$typed\u2026", NoticeText.parts("$typed\u2026", "x").name)
    }

    @Test
    fun `the two parts say the same words as the joined text, for every case`() {
        val sentences = listOf(
            "Rang at 4:57${nbsp}PM for 10 minutes with no response.",
            "Rang at 4:57${nbsp}PM with no answer until the next alarm began.",
            "The phone could not ring this alarm on time.",
            "Ringing again at 5:17${nbsp}PM.",
        )
        for (label in listOf("", "Evening walk", "Done!", "W".repeat(32), "W".repeat(33), "1 h 42 min after moonrise and then some more words", "a".repeat(200), "b".repeat(199) + ".")) {
            for (sentence in sentences) {
                val parts = NoticeText.parts(label, sentence)
                val joined = if (parts.name == null) parts.sentence else parts.name + " " + parts.sentence
                assertEquals("label \"${label.take(12)}...\"", NoticeText.named(label, sentence), joined)
            }
        }
    }

    // R12-F (D130 to D132).

    /** The summary as summaryFor built it before R12-F (D118), the reference for the split. */
    private fun oldSummary(time: String, place: String, anchor: String, snoozed: Boolean, fallback: Boolean): String {
        val parts = mutableListOf(time)
        if (place.isNotBlank()) parts += place
        if (anchor.isNotBlank()) parts += anchor
        if (snoozed) parts += "snoozed"
        val line = NoticeText.units(parts.joinToString(", "))
        return if (fallback) "$line. The sun did not reach that point today, so this alarm used solar noon." else line
    }

    /** A measure with wide characters: one unit a character, "W" two, a Chinese character three. */
    private val wide: (String) -> Float = { t -> t.sumOf { c -> if (c == 'W') 2 else if (c.code in 0x4E00..0x9FFF) 3 else 1 }.toFloat() }

    @Test
    fun `the ring summary is the same text as before for every short place, and its parts say the same words for every long place`() {
        val places = listOf("", " ", "Oslo", "W".repeat(32), "Karachi University Employees Co-operative Housing Society, Pakistan", "W".repeat(33), "a".repeat(40))
        val anchors = listOf("", "at sunrise", "11 h 59 min after the end of the golden hour")
        for (place in places) for (anchor in anchors) for (snoozed in listOf(false, true)) for (fallback in listOf(false, true)) {
            val time = "5:17${nbsp}PM"
            val parts = NoticeText.summary(time, place, anchor, snoozed, fallback)
            // The text with a plain space instead of the paragraph break is the old text, trimmed of the place's edges.
            assertEquals(
                "place \"${place.take(10)}\" anchor \"$anchor\" snoozed $snoozed fallback $fallback",
                oldSummary(time, place.trim(), anchor, snoozed, fallback), parts.joined(),
            )
            val flows = place.isBlank() || NoticeText.flows(place.trim())
            assertEquals("a short or missing place keeps one paragraph", flows, parts.lead == null && parts.place == null)
            if (!flows) {
                assertEquals("5:17${nbsp}PM,", parts.lead)
                assertTrue("the place line holds the place: ${parts.place}", parts.place!!.startsWith(place.trim()))
            }
        }
    }

    @Test
    fun `a long place keeps the time first and a unit with its number`() {
        val place = "Karachi University Employees Co-operative Housing Society, Pakistan"
        val parts = NoticeText.summary("5:17${nbsp}PM", place, "11 h 59 min after the end of the golden hour", true, true)
        assertEquals("5:17${nbsp}PM,", parts.lead)
        assertEquals("$place,", parts.place)
        assertEquals(
            "11${nbsp}h 59${nbsp}min after the end of the golden hour, snoozed. The sun did not reach that point today, so this alarm used solar noon.",
            parts.rest,
        )
        // No more words after the place: no comma, and a full stop only before the sun sentence.
        assertEquals(place, NoticeText.summary("5:17${nbsp}PM", place, "", false, false).place)
        assertEquals("", NoticeText.summary("5:17${nbsp}PM", place, "", false, false).rest)
        assertEquals("$place.", NoticeText.summary("5:17${nbsp}PM", place, "", false, true).place)
    }

    @Test
    fun `a name flows only when it is short and not wider than 32 of the widest letters`() {
        assertTrue(NoticeText.flows("W".repeat(32), wide))
        assertFalse("33 characters", NoticeText.flows("W".repeat(33), wide))
        // 32 Chinese characters are 96 units, 32 W are 64: the name is wider than the bound although the count is the same.
        assertFalse(NoticeText.flows("\u4e2d".repeat(32), wide))
        assertTrue(NoticeText.flows("\u4e2d".repeat(21), wide))
        assertFalse(NoticeText.flows("\u4e2d".repeat(22), wide))
        // The default measure is the count: a name of 32 characters flows, a name of 33 does not.
        assertTrue(NoticeText.flows("a".repeat(32)))
        assertFalse(NoticeText.flows("a".repeat(33)))
        // The parts follow the same rule.
        assertEquals(null, NoticeText.parts("W".repeat(32), "x", wide).name)
        assertEquals("${"\u4e2d".repeat(32)}.", NoticeText.parts("\u4e2d".repeat(32), "x", wide).name)
        // A blank name never asks for its own view.
        assertEquals(null, NoticeText.parts("", "x", wide).name)
    }

    @Test
    fun `a name or a place with line breaks is one line`() {
        assertEquals("Wake up. Then go", NoticeText.flat("  Wake up.\n\r\n  Then go \n"))
        assertEquals("a b c d e", NoticeText.flat("a\u2028b\u2029c\u0085d\u000Be"))
        assertEquals("two  spaces stay", NoticeText.flat("two  spaces stay"))
        assertEquals("Oslo", NoticeText.flat("\n Oslo\n"))
        val typed = "Wake me up at the right time\nfor the long hike to the mountain cabin"
        assertEquals("Wake me up at the right time for the long hike to the mountain cabin.", NoticeText.parts(typed, "x").name)
        assertEquals("Wake up. Rang at 5.", NoticeText.named("Wake up\n", "Rang at 5."))
        assertEquals("Evening walk. x", NoticeText.named("Evening\nwalk", "x"))
        // A place with a line break is one line in the ring summary.
        assertEquals("5:17${nbsp}PM, Oslo, Norway", NoticeText.summary("5:17${nbsp}PM", "Oslo,\nNorway", "", false, false).joined())
    }

    @Test
    fun `a name of 200 characters is the longest a person can type`() {
        assertEquals(200, PendingSnooze.MAX_LABEL)
    }
}
