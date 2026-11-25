package no.stormberry.twinlit.ui.components

import java.nio.ByteBuffer

/** A TrueType font reduced to what the width budget needs: `head`, `hhea`, `hmtx` and a format 4 `cmap`. */
internal class Font(private val unitsPerEm_: Int, private val glyphOf: (Int) -> Int, private val advances: IntArray) {
    val unitsPerEm: Int get() = unitsPerEm_
    fun advance(c: Char): Double {
        val g = glyphOf(c.code)
        check(g != 0) { "no glyph for U+%04X".format(c.code) }
        return advances[g.coerceAtMost(advances.size - 1)].toDouble()
    }

    companion object {
        fun load(bytes: ByteArray): Font {
            val b = ByteBuffer.wrap(bytes)
            val tables = HashMap<String, Int>()
            val count = b.getShort(4).toInt() and 0xFFFF
            for (i in 0 until count) {
                val at = 12 + 16 * i
                val tag = String(bytes, at, 4, Charsets.ISO_8859_1)
                tables[tag] = b.getInt(at + 8)
            }
            val head = tables.getValue("head")
            val unitsPerEm = b.getShort(head + 18).toInt() and 0xFFFF
            val hhea = tables.getValue("hhea")
            val metrics = b.getShort(hhea + 34).toInt() and 0xFFFF
            val hmtx = tables.getValue("hmtx")
            val advances = IntArray(metrics) { b.getShort(hmtx + 4 * it).toInt() and 0xFFFF }

            // The (3, 1) cmap subtable, format 4.
            val cmap = tables.getValue("cmap")
            val subtables = b.getShort(cmap + 2).toInt() and 0xFFFF
            var format4 = -1
            for (i in 0 until subtables) {
                val platform = b.getShort(cmap + 4 + 8 * i).toInt() and 0xFFFF
                val encoding = b.getShort(cmap + 6 + 8 * i).toInt() and 0xFFFF
                if (platform == 3 && encoding == 1) format4 = cmap + b.getInt(cmap + 8 + 8 * i)
            }
            check(format4 >= 0 && (b.getShort(format4).toInt() and 0xFFFF) == 4) { "no format 4 cmap" }
            val segX2 = b.getShort(format4 + 6).toInt() and 0xFFFF
            val seg = segX2 / 2
            val endCodes = format4 + 14
            val startCodes = endCodes + segX2 + 2
            val idDeltas = startCodes + segX2
            val idRangeOffsets = idDeltas + segX2
            val glyphOf = { code: Int ->
                var glyph = 0
                for (s in 0 until seg) {
                    val end = b.getShort(endCodes + 2 * s).toInt() and 0xFFFF
                    if (code > end) continue
                    val start = b.getShort(startCodes + 2 * s).toInt() and 0xFFFF
                    if (code < start) break
                    val delta = b.getShort(idDeltas + 2 * s).toInt()
                    val rangeAt = idRangeOffsets + 2 * s
                    val range = b.getShort(rangeAt).toInt() and 0xFFFF
                    glyph = if (range == 0) {
                        (code + delta) and 0xFFFF
                    } else {
                        val g = b.getShort(rangeAt + range + 2 * (code - start)).toInt() and 0xFFFF
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    break
                }
                glyph
            }
            return Font(unitsPerEm, glyphOf, advances)
        }
    }
}
