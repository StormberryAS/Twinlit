package no.stormberry.twinlit.widget.moon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/**
 * The Moon widgets' panel (`widget_moon_` colours) is 70 per cent opaque (2.0.0, Marcos 2026-11-19), so the wallpaper shows
 * through and the worst case for the text is a plain white wallpaper. This test reads the colours
 * from res/values/colors.xml (no Android framework) and holds every widget text colour to 4.6 to 1
 * (WCAG, normal text) against the panel over pure white and over pure black, so a later change
 * cannot make a label unreadable again. It also checks that the colours MoonWidgets.kt sets in
 * code are the same as the resource values, and that every text colour of the layouts is one it
 * checks.
 */
class WidgetColoursTest {

    private fun file(vararg candidates: String): File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { dir -> candidates.asSequence().map { File(dir, it) } }
            .firstOrNull { it.exists() } ?: error("${candidates.first()} not found")

    private fun main(rel: String): File = file("src/main/$rel", "app/src/main/$rel", "android/app/src/main/$rel")

    /** name to ARGB (an unsigned 32-bit pattern in a Long), from colors.xml. */
    private val colours: Map<String, Long> by lazy {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(main("res/values/colors.xml")).documentElement
        val nodes = root.getElementsByTagName("color")
        (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to parse(it.textContent.trim()) }
    }

    private fun parse(hex: String): Long {
        assertTrue(hex, Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(hex))
        val digits = hex.substring(1)
        return if (digits.length == 6) 0xFF000000L or digits.toLong(16) else digits.toLong(16)
    }

    private fun alpha(c: Long) = ((c shr 24) and 0xFF).toInt()
    private fun red(c: Long) = ((c shr 16) and 0xFF).toInt()
    private fun green(c: Long) = ((c shr 8) and 0xFF).toInt()
    private fun blue(c: Long) = (c and 0xFF).toInt()

    /** The panel over a solid background, channel by channel, exact (not rounded to 8 bits). */
    private fun panelOver(background: Int): DoubleArray {
        val panel = colours.getValue("widget_moon_panel")
        val a = alpha(panel) / 255.0
        return rgb(panel).map { a * it + (1 - a) * background }.toDoubleArray()
    }

    private fun channel(v: Double): Double {
        val c = v / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: DoubleArray) = 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])

    private fun contrast(a: DoubleArray, b: DoubleArray): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun rgb(c: Long) = doubleArrayOf(red(c).toDouble(), green(c).toDouble(), blue(c).toDouble())

    /** Every widget colour except the panel and the hairline is a text colour. */
    private val textColours: Map<String, Long> by lazy {
        colours.filterKeys { it.startsWith("widget_moon_") && it != "widget_moon_panel" && it != "widget_moon_border" }
    }

    @Test fun `the widget panel is 70 per cent opaque`() {
        val panel = colours.getValue("widget_moon_panel")
        assertEquals(0xB3, alpha(panel))
        assertEquals(0x050810, (panel and 0xFFFFFF).toInt())
        assertEquals(0.70, alpha(panel) / 255.0, 0.005)
    }

    @Test fun `there are widget text colours to check`() {
        assertEquals(
            setOf(
                "widget_moon_text_primary", "widget_moon_text_soft", "widget_moon_text_secondary", "widget_moon_blue",
                "widget_moon_violet", "widget_moon_teal", "widget_moon_silver", "widget_moon_new_moon",
            ),
            textColours.keys,
        )
    }

    @Test fun `every widget text colour reaches 4_6 to 1 on the panel over pure white and over pure black`() {
        val overWhite = panelOver(255)
        val overBlack = panelOver(0)
        // The composite over white is a mid grey, #505257 once rounded to 8 bits.
        assertEquals(79.5, overWhite[0], 0.1)
        for ((name, c) in textColours) {
            assertEquals(name, 0xFF, alpha(c))
            val white = contrast(rgb(c), overWhite)
            val black = contrast(rgb(c), overBlack)
            val whiteRounded = contrast(rgb(c), overWhite.map { Math.round(it).toDouble() }.toDoubleArray())
            assertTrue("$name over white: $white", white >= 4.6)
            assertTrue("$name over white, composite rounded to 8 bits: $whiteRounded", whiteRounded >= 4.6)
            assertTrue("$name over black: $black", black >= 4.6)
        }
    }

    /** The widget labels that the layouts and styles colour must all be colours this test checks. */
    @Test fun `every text colour a widget layout or style uses is checked`() {
        val used = mutableSetOf<String>()
        val pattern = Regex("""textColor"?\s*(?:=\s*"|>)\s*@color/(\w+)""")
        val dirs = listOf(main("res/layout"), main("res/values"))
        for (dir in dirs) {
            for (f in dir.listFiles().orEmpty().filter { it.name.startsWith("widget_") && it.extension == "xml" }) {
                // widget_styles.xml holds both families' styles: this test is about the moon colours.
                pattern.findAll(f.readText()).forEach { if (it.groupValues[1].startsWith("widget_moon_")) used += it.groupValues[1] }
            }
        }
        assertTrue("no text colour found in the widget layouts", used.isNotEmpty())
        assertEquals(emptySet<String>(), used - textColours.keys)
    }

    /** MoonWidgets.kt sets some labels in code (the 2x1's spans, the polar labels): same values. */
    @Test fun `the colours MoonWidgets sets in code are the resource values`() {
        val source = main("kotlin/no/stormberry/twinlit/widget/moon/MoonWidgets.kt").readText()
        val byConstant = mapOf(
            "TEXT_PRIMARY" to "widget_moon_text_primary", "SOFT" to "widget_moon_text_soft", "BLUE" to "widget_moon_blue",
            "VIOLET" to "widget_moon_violet", "TEAL" to "widget_moon_teal", "SILVER" to "widget_moon_silver", "NEW_MOON" to "widget_moon_new_moon",
        )
        for ((constant, resource) in byConstant) {
            val m = Regex("""private const val $constant = 0x([0-9A-Fa-f]{8})\.toInt\(\)""").find(source)
            assertTrue("$constant not found in MoonWidgets.kt", m != null)
            assertEquals(constant, colours.getValue(resource), m!!.groupValues[1].toLong(16))
        }
    }
}
