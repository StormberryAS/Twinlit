package no.stormberry.twinlit.data

import java.text.BreakIterator

/**
 * The name a user may give to a place they typed as coordinates ("Home", "Bunker", "Cabin"), 2.0.0.
 *
 * It is a field of its own on the stored [Place] (`Place.name`), never part of the place's label,
 * so that a typed place is still told by its label being its own coordinates, and a comma in a
 * name is never read as the end of a city. Only a place typed as coordinates can have one: a
 * catalogue place is named by the catalogue.
 *
 * The one rule for what a name may hold lives here and is applied twice, as the user types
 * ([typing]) and again when the name is stored, read or shown ([sanitise]): trimmed, runs of
 * white space (a tab, a new line from a paste, a no-break space) collapsed into one space, control
 * and invisible formatting characters dropped, and at most [MAX_LENGTH] characters as a person
 * counts them. Pure, so the tests can check it without a device.
 */
object PlaceName {

    /** The most characters a name may have, counted as a person sees them (see [limit]). */
    const val MAX_LENGTH = 20

    /**
     * A hard bound in UTF-16 units, so that a run of long emoji sequences (a family is eleven
     * units) can never make a name that is large in memory or unreadable on a widget.
     */
    private const val MAX_UNITS = 80

    /**
     * [raw] as the text field may hold it while the user is still typing: cleaned and limited, but
     * not trimmed at the end, because a space typed between two words must stay.
     */
    fun typing(raw: String): String = limit(clean(raw).trimStart())

    /** [raw] as it is stored and shown: [typing], trimmed, and limited again. Blank means no name. */
    fun sanitise(raw: String?): String {
        if (raw == null) return ""
        return limit(clean(raw).trim())
    }

    /**
     * Drops the characters a name must not carry and turns every kind of white space into one
     * space. Walks code points, so a surrogate pair is never split; an unpaired surrogate is
     * dropped.
     */
    private fun clean(raw: String): String {
        val out = StringBuilder(raw.length)
        var lastWasSpace = false
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (!lastWasSpace) out.append(' ')
                lastWasSpace = true
                continue
            }
            if (isDropped(cp)) continue
            out.appendCodePoint(cp)
            lastWasSpace = false
            if (cp == BLACK_FLAG) i = keepTagSequence(raw, i, out)
        }
        return out.toString()
    }

    /**
     * After a black flag, the tag characters of a subdivision flag (England, Scotland, Wales:
     * the flag, two to six tag letters or digits, the cancel tag). Tag characters are invisible
     * format characters, dropped anywhere else, so that they cannot hide text in a name; a whole
     * flag sequence is kept, so the flag is not turned into a plain black flag. Returns the index
     * after the kept sequence, or [start] when none follows (stray tags are then dropped).
     */
    private fun keepTagSequence(raw: String, start: Int, out: StringBuilder): Int {
        var j = start
        var tags = 0
        while (j < raw.length) {
            val cp = raw.codePointAt(j)
            if (cp == CANCEL_TAG && tags > 0) {
                out.append(raw, start, j + Character.charCount(cp))
                return j + Character.charCount(cp)
            }
            if (cp !in TAG_FIRST..TAG_LAST) return start
            j += Character.charCount(cp)
            tags++
        }
        return start
    }

    /**
     * Control characters (also 0x7F to 0x9F), unpaired surrogates, unassigned and private-use code
     * points, and the invisible formatting characters (bidirectional overrides and isolates, zero
     * width space, the byte order mark and the like), which could make a name read as something
     * else. Kept: the zero width joiner and non-joiner, which emoji sequences and several scripts
     * need, the variation selectors (a mark, not a format character), and the tags of a whole
     * subdivision flag ([keepTagSequence]).
     */
    private fun isDropped(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.CONTROL, Character.SURROGATE, Character.UNASSIGNED, Character.PRIVATE_USE -> true
        Character.FORMAT -> cp != ZERO_WIDTH_JOINER && cp != ZERO_WIDTH_NON_JOINER
        else -> false
    }

    private const val ZERO_WIDTH_NON_JOINER = 0x200C
    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val BLACK_FLAG = 0x1F3F4
    private const val TAG_FIRST = 0xE0020
    private const val TAG_LAST = 0xE007E
    private const val CANCEL_TAG = 0xE007F

    /**
     * The first [MAX_LENGTH] user-perceived characters of [text] (extended grapheme clusters: a
     * letter with its accents, a flag, a family emoji count once), and never more than
     * [MAX_UNITS] UTF-16 units. A cut falls on a boundary, so it never splits a surrogate pair or
     * a sequence, and a trailing space left by the cut is removed.
     */
    private fun limit(text: String): String {
        if (text.length <= MAX_LENGTH && text.none { Character.isSurrogate(it) || it == '‍' || Character.getType(it).toByte() == Character.NON_SPACING_MARK }) {
            return text
        }
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var end = 0
        var count = 0
        var next = iterator.next()
        while (next != BreakIterator.DONE && count < MAX_LENGTH && next <= MAX_UNITS) {
            end = next
            count++
            next = iterator.next()
        }
        // A trailing space is removed only where the cut left one: one typed at the end stays.
        return if (end < text.length) text.substring(0, end).trimEnd() else text
    }
}
