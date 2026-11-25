package no.stormberry.twinlit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator

/**
 * The name a user may give to a place typed as coordinates (2.0.0, owner's decision W5 of
 * 2026-11-19): trimmed, white space collapsed, control characters gone, at most 20 characters as a
 * person counts them, and never a split surrogate pair.
 */
class PlaceNameTest {

    private fun perceived(text: String): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var n = 0
        while (it.next() != BreakIterator.DONE) n++
        return n
    }

    private fun noLoneSurrogate(text: String) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                assertTrue("lone high surrogate in '$text'", i + 1 < text.length && Character.isLowSurrogate(text[i + 1]))
                i += 2
            } else {
                assertFalse("lone low surrogate in '$text'", Character.isLowSurrogate(c))
                i++
            }
        }
    }

    @Test
    fun `a plain name is kept, and blank is no name`() {
        assertEquals("Home", PlaceName.sanitise("Home"))
        assertEquals("Bunker", PlaceName.sanitise("Bunker"))
        assertEquals("", PlaceName.sanitise(""))
        assertEquals("", PlaceName.sanitise("   "))
        assertEquals("", PlaceName.sanitise(null))
        assertEquals("", PlaceName.sanitise(" \t\n "))
    }

    @Test
    fun `it is trimmed and the white space inside is collapsed`() {
        assertEquals("My cabin", PlaceName.sanitise("  My   cabin  "))
        assertEquals("My cabin", PlaceName.sanitise("My\t\tcabin"))
        assertEquals("My cabin", PlaceName.sanitise("My\ncabin"))
        assertEquals("My cabin", PlaceName.sanitise("My\r\n\r\ncabin"))
        // A no-break space and an ideographic space are white space too.
        assertEquals("My cabin", PlaceName.sanitise("My 　cabin"))
    }

    @Test
    fun `control and invisible characters are dropped`() {
        assertEquals("Home", PlaceName.sanitise("Ho\u0000me"))
        assertEquals("Home", PlaceName.sanitise("\u0007Home\u001B"))
        assertEquals("Home", PlaceName.sanitise("Ho\u007Fme\u0085"))
        // Zero width space, byte order mark, and the bidirectional overrides and isolates.
        assertEquals("Home", PlaceName.sanitise("Ho​me﻿"))
        assertEquals("Home", PlaceName.sanitise("‮Home‬⁦⁩"))
        // An unpaired surrogate is dropped, not kept as a broken character.
        assertEquals("Home", PlaceName.sanitise("Ho\uD83Dme"))
        assertEquals("Home", PlaceName.sanitise("Ho\uDE00me"))
    }

    @Test
    fun `at most 20 characters, in the model and as the user types`() {
        val long = "A".repeat(30)
        assertEquals(20, PlaceName.MAX_LENGTH)
        assertEquals("A".repeat(20), PlaceName.sanitise(long))
        assertEquals("A".repeat(20), PlaceName.typing(long))
        assertEquals("A".repeat(20), PlaceName.sanitise("A".repeat(20)))
        assertEquals("A".repeat(19), PlaceName.sanitise("A".repeat(19)))
        // A cut that leaves a space at the end removes it.
        assertEquals("A".repeat(19), PlaceName.sanitise("A".repeat(19) + " B"))
    }

    @Test
    fun `typing keeps a space at the end so that a second word can follow, sanitise does not`() {
        assertEquals("My ", PlaceName.typing("My "))
        assertEquals("My", PlaceName.sanitise("My "))
        assertEquals("My cabin", PlaceName.typing("My  cabin"))
        assertEquals("", PlaceName.typing("   "))
        assertEquals("Home", PlaceName.typing("  Home"))
    }

    @Test
    fun `an emoji is never split, and counts as one character`() {
        val grin = "😀"
        val twentyFive = grin.repeat(25)
        val cut = PlaceName.sanitise(twentyFive)
        noLoneSurrogate(cut)
        assertEquals(20, perceived(cut))
        assertEquals(grin.repeat(20), cut)
        noLoneSurrogate(PlaceName.typing(twentyFive))
        // Text and emoji together: 19 letters and two emoji keeps 19 letters and one emoji.
        val mixed = "B".repeat(19) + grin + grin
        assertEquals("B".repeat(19) + grin, PlaceName.sanitise(mixed))
    }

    @Test
    fun `a letter with its accent and a flag count once`() {
        // "e" and a combining acute accent (two code points, one character), and a flag (two regional indicators).
        val accented = "é"
        val flag = "🇳🇴"
        val cut = PlaceName.sanitise(accented.repeat(25))
        assertEquals(accented.repeat(20), cut)
        val flags = PlaceName.sanitise(flag.repeat(25))
        noLoneSurrogate(flags)
        assertEquals(flag.repeat(20), flags)
    }

    @Test
    fun `a subdivision flag keeps its tags, and tags anywhere else are dropped`() {
        fun cps(vararg c: Int) = c.joinToString("") { String(Character.toChars(it)) }
        // The flag of England: a black flag, the tags g b e n g, and the cancel tag (14 UTF-16 units).
        val england = cps(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F)
        assertEquals(england, PlaceName.sanitise(england))
        assertEquals("Home $england", PlaceName.sanitise("Home $england"))
        assertEquals(1, perceived(PlaceName.sanitise(england)))
        // Cut whole: five flags fill the bound of 80 units, the sixth is not split.
        val cut = PlaceName.sanitise(england.repeat(10))
        noLoneSurrogate(cut)
        assertEquals(england.repeat(5), cut)
        // Tags that hide text after a letter, or a flag without its cancel tag, are not kept.
        val hidden = cps(0xE0068, 0xE0069)
        assertEquals("Home", PlaceName.sanitise("Ho${hidden}me"))
        assertEquals(cps(0x1F3F4) + "x", PlaceName.sanitise(cps(0x1F3F4, 0xE0067, 0xE0062) + "x"))
        assertEquals(PlaceName.sanitise(england), PlaceName.sanitise(PlaceName.typing(england)))
    }

    @Test
    fun `a joined emoji sequence is kept whole or cut whole, never in the middle`() {
        // A family: three people joined by zero width joiners (eleven UTF-16 units, one character).
        val family = "👨‍👩‍👧"
        assertEquals(family, PlaceName.sanitise(family))
        val cut = PlaceName.sanitise(family.repeat(25))
        noLoneSurrogate(cut)
        assertTrue("never more than 20 characters: ${perceived(cut)}", perceived(cut) <= 20)
        // The hard bound in UTF-16 units: a long run of sequences cannot make a large name.
        assertTrue(cut.length <= 80)
        // Whatever was kept ends on a whole sequence.
        assertTrue(cut.isEmpty() || cut.endsWith("👧"))
    }

    @Test
    fun `sanitising twice changes nothing, and typing then sanitising equals sanitising`() {
        val samples = listOf(
            "  My   cabin  ", "A".repeat(40), "😀".repeat(30), "Ho\u0000me\n", "é".repeat(30),
            "Bunker, north", "  ", "x ".repeat(30),
        )
        for (raw in samples) {
            val once = PlaceName.sanitise(raw)
            assertEquals(raw, once, PlaceName.sanitise(once))
            assertEquals(raw, once, PlaceName.sanitise(PlaceName.typing(raw)))
            assertTrue(perceived(once) <= 20)
        }
    }

    @Test
    fun `a comma is part of the name`() {
        assertEquals("Home, sweet home", PlaceName.sanitise("Home, sweet home"))
    }
}
