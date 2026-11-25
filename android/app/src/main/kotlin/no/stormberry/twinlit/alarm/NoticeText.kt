package no.stormberry.twinlit.alarm

/**
 * The words of the Missed and Snoozed notices, and the spacing of every clock and every quantity
 * the notices and the ring screen show (R10, D108 to D111).
 *
 * Pure Kotlin, so the plain JVM tests can run it; [AlarmService] calls it for each notice.
 *
 * - **Titles** (D23 option A, Marcos 2026-11-22): the title is a fixed short phrase, so Android never
 *   cuts it; the alarm's own name moves to the front of the text.
 * - **Joining**: the text is `{name}. {text}` (a full stop and one space), or `{name} {text}` when the
 *   name already ends in a full stop, a question mark, an exclamation mark or an ellipsis. The collapsed
 *   notice shows one line, so the name comes first and the line reads as two short sentences.
 * - **No-break spaces**: a time and its AM or PM marker, and a number and its unit ("10 minutes",
 *   "3 h 29 min" as "3 h" and "29 min"), never part on a line break. Only a plain space or a narrow
 *   no-break space becomes a no-break space; no word changes.
 */
internal object NoticeText {
    /** TEXTS I-8, replaced by D23 option A (Marcos, 2026-11-22). */
    const val MISSED_TITLE = "Missed alarm"

    /** TEXTS I-6, replaced by D23 option A (Marcos, 2026-11-22). */
    const val SNOOZED_TITLE = "Alarm snoozed"

    const val NO_BREAK_SPACE = ' '
    private const val NARROW_NO_BREAK_SPACE = ' '

    /** The units the notices and the alarm names print after a number. */
    private val NUMBER_THEN_UNIT = Regex("""(?<=\d) (?=(?:h|min|minutes?|hours?|days?)(?![\p{L}\d]))""")

    /** A name that ends like this already ends a sentence: no full stop is added. */
    private const val SENTENCE_END = ".!?…"

    /**
     * A time as the phone's own format prints it ("4:57 PM", "16:57"): the space before AM or PM
     * (a plain space, or the narrow no-break space newer ICU uses) is a no-break space.
     */
    fun clock(formatted: String): String =
        formatted.replace(' ', NO_BREAK_SPACE).replace(NARROW_NO_BREAK_SPACE, NO_BREAK_SPACE)

    /** "10 minutes", "3 h 29 min": the space between a number and its unit is a no-break space. */
    fun units(text: String): String = NUMBER_THEN_UNIT.replace(text, NO_BREAK_SPACE.toString())

    /** A line break (and the spaces around it) in a typed name or place becomes one plain space: a notice shows one paragraph. */
    private val LINE_BREAKS = Regex("""[\s\u0085\u2028\u2029]*[\r\n\u000B\u000C\u0085\u2028\u2029][\s\u0085\u2028\u2029]*""")

    /**
     * A typed alarm name or place name as the notices show it (R12-F, D132): no line breaks (the alarm editor's
     * name can come pre-filled from another app and a single-line field does not filter a stored text), trimmed.
     */
    fun flat(label: String): String = LINE_BREAKS.replace(label.trim(), " ")

    /** The notice text: the alarm's name, then [text] (D23 option A). A blank name leaves [text] alone. */
    fun named(label: String, text: String): String {
        val name = units(flat(label))
        if (name.isEmpty()) return units(text)
        val gap = if (name.last() in SENTENCE_END) " " else ". "
        return name + gap + units(text)
    }

    /**
     * A name up to this many characters flows with the sentence in one text view, as the notice always did
     * ("Wake up with the sun. Ringing again at 5:17 PM."), unless it is wider than that many of the widest
     * letters ("W", see [flows]). Measured by NoticeDeviceTest at font scale 2.0 on 360 dp: 32 "W" with the
     * longest sentence are 223 dp high on API 36 and 274 dp on API 33 (R12, D124, R12-F, D131).
     */
    const val FLOW_NAME_MAX = 32

    /**
     * Whether [text] (an alarm's name, or a place name in a ring notice) flows with the sentence: at most
     * [FLOW_NAME_MAX] characters and, when [width] measures text, not wider than [FLOW_NAME_MAX] "W" (the widest
     * letter). The count alone would let 32 wide characters (Chinese, Japanese, U+FDFD) flow and grow the notice
     * without a bound (R12-F, D132). [width] is the text's width in any one unit; the default is the character count.
     */
    fun flows(text: String, width: (String) -> Float = { it.length.toFloat() }): Boolean =
        text.length <= FLOW_NAME_MAX && width(text) <= width("W".repeat(FLOW_NAME_MAX))

    /**
     * The expanded notice's two parts (R12, D124): the name with its full stop (null when the name is blank
     * or [flows], so it flows with the sentence) and the sentence. Any longer name, generated or typed, sits in
     * its own view, which shows at most three lines and ends with an ellipsis, so the sentence with the time is
     * always whole (D131). Joined with one plain space, the parts are the same words as [named] (a test pins it).
     */
    class Parts(val name: String?, val sentence: String)

    fun parts(label: String, text: String, width: (String) -> Float = { it.length.toFloat() }): Parts {
        val name = units(flat(label))
        if (flows(name, width)) return Parts(null, named(label, text))
        val stop = if (name.last() in SENTENCE_END) "" else "."
        return Parts(name + stop, units(text))
    }

    /** What the ring notices add when the sun did not reach the point (TEXTS I-12). */
    const val SUN_FALLBACK_SENTENCE = "The sun did not reach that point today, so this alarm used solar noon."

    /**
     * The ring and backup ring notice's summary in the expanded view (R12-F, D130): the time, the place, the
     * anchor text, "snoozed" and the sun fallback sentence, in that order, as one paragraph ([rest] only) when the
     * place is short or missing. A long place ([flows] false: a catalogue label can have 67 characters) gets its
     * own view of at most two lines that ends with an ellipsis, and the time stays first in a line of its own:
     * [lead] "5:17 PM,", [place] "Karachi ... Pakistan,", [rest] the other words. [joined] is the collapsed text.
     */
    class SummaryParts(val lead: String?, val place: String?, val rest: String) {
        fun joined(): String = listOfNotNull(lead, place, rest.takeIf { it.isNotEmpty() }).joinToString(" ")
    }

    fun summary(
        time: String, placeName: String, anchorText: String, snoozed: Boolean, sunFallback: Boolean,
        width: (String) -> Float = { it.length.toFloat() },
    ): SummaryParts {
        val place = flat(placeName)
        val tail = listOfNotNull(anchorText.takeIf { it.isNotBlank() }, "snoozed".takeIf { snoozed })
        val warning = SUN_FALLBACK_SENTENCE.takeIf { sunFallback }
        if (place.isEmpty() || flows(place, width)) {
            val line = (listOf(time) + listOfNotNull(place.takeIf { it.isNotEmpty() }) + tail).joinToString(", ")
            return SummaryParts(null, null, units(if (warning != null) "$line. $warning" else line))
        }
        val more = tail.joinToString(", ")
        val rest = when {
            warning == null -> more
            more.isEmpty() -> warning
            else -> "$more. $warning"
        }
        val stop = if (tail.isNotEmpty()) "," else if (warning != null) "." else ""
        return SummaryParts(units("$time,"), units(place + stop), units(rest))
    }
}
