package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/**
 * The Sun widgets' palette (res/values/colors.xml, the `widget_sun_` colours), read as the app reads it. The panel is 70 per cent
 * opaque (alpha B3), so the worst case for the text is the panel over a plain white wallpaper.
 * Every widget text colour must keep a WCAG contrast of at least 4.5 to 1 against the panel
 * composited over pure white and against the panel over pure black, so a colour or alpha change
 * cannot make the widgets unreadable again. Pure JVM: no Android framework.
 */
class WidgetColorsTest {

    private val colors: Map<String, Int> by lazy {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(TestFiles.require("${TestFiles.RES}/values/colors.xml"))
        val nodes = doc.getElementsByTagName("color")
        (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to parse(it.textContent.trim()) }
    }

    /** "#RRGGBB" or "#AARRGGBB" as an ARGB int. */
    private fun parse(hex: String): Int {
        val digits = hex.removePrefix("#")
        val v = digits.toLong(16)
        return if (digits.length == 6) (0xFF000000L or v).toInt() else v.toInt()
    }

    /** The widget text colours: every widget colour that is not the panel or the shadow. */
    private val textColors: Map<String, Int>
        get() = colors.filterKeys { it.startsWith("widget_sun_") && it != "widget_sun_panel" && it != "widget_sun_text_shadow" }

    private fun channel(argb: Int, shift: Int) = (argb ushr shift) and 0xFF

    /** The panel over an opaque backdrop of grey level [backdrop] (255 white, 0 black), per channel, unrounded. */
    private fun panelOver(backdrop: Int): DoubleArray {
        val panel = colors.getValue("widget_sun_panel")
        val a = channel(panel, 24) / 255.0
        return listOf(16, 8, 0).map { a * channel(panel, it) + (1 - a) * backdrop }.toDoubleArray()
    }

    private fun lin(c: Double): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(r: Double, g: Double, b: Double) = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

    private fun contrast(text: Int, backdrop: DoubleArray): Double {
        val lt = luminance(channel(text, 16).toDouble(), channel(text, 8).toDouble(), channel(text, 0).toDouble())
        val lb = luminance(backdrop[0], backdrop[1], backdrop[2])
        return (maxOf(lt, lb) + 0.05) / (minOf(lt, lb) + 0.05)
    }

    @Test
    fun `the panel is the app background at 70 per cent opacity`() {
        assertEquals(0xB3080C18L.toInt(), colors.getValue("widget_sun_panel"))
        assertEquals(0xB3, channel(colors.getValue("widget_sun_panel"), 24))
    }

    @Test
    fun `the colour check knows every widget text colour`() {
        assertEquals(
            setOf("widget_sun_text_primary", "widget_sun_text_secondary", "widget_sun_gold", "widget_sun_blue"),
            textColors.keys,
        )
    }

    @Test
    fun `every widget text colour has 4_5 to 1 over the panel on white`() {
        val white = panelOver(255)
        for ((name, c) in textColors) {
            val ratio = contrast(c, white)
            assertTrue("$name ${"%08X".format(c)} over white: ${"%.2f".format(ratio)} to 1", ratio >= 4.5)
        }
    }

    @Test
    fun `every widget text colour has 4_5 to 1 over the panel on black`() {
        val black = panelOver(0)
        for ((name, c) in textColors) {
            val ratio = contrast(c, black)
            assertTrue("$name ${"%08X".format(c)} over black: ${"%.2f".format(ratio)} to 1", ratio >= 4.5)
        }
    }

    @Test
    fun `the colours SunWidgets sets from code are the colours of colors_xml`() {
        val src = TestFiles.require("${TestFiles.KOTLIN}/widget/sun/SunWidgets.kt").readText()
        fun constant(name: String): Int {
            val m = Regex("""private const val $name = 0x([0-9A-Fa-f]{8})\.toInt\(\)""").find(src)
            assertTrue("$name is declared in SunWidgets.kt", m != null)
            return m!!.groupValues[1].toLong(16).toInt()
        }
        assertEquals(colors.getValue("widget_sun_text_primary"), constant("TEXT_PRIMARY"))
        assertEquals(colors.getValue("widget_sun_text_secondary"), constant("TEXT_SECONDARY"))
        assertEquals(colors.getValue("widget_sun_gold"), constant("GOLD"))
        assertEquals(colors.getValue("widget_sun_blue"), constant("BLUE"))
    }
}
