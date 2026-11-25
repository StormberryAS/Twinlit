package no.stormberry.twinlit.widget.sun

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The widget layouts and provider files (1.1.9), parsed as XML. */
class WidgetLayoutTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(f: File): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(f).documentElement
    }

    /** The Sun family's part of values/widget_styles.xml (the file holds the moon family's styles after it). */
    private fun sunStyles(): String =
        TestFiles.require("${TestFiles.RES}/values/widget_styles.xml").readText().substringBefore("the moon family")

    private fun layout(name: String) = parse(TestFiles.require("${TestFiles.RES}/layout/$name.xml"))

    private fun Element.attr(name: String): String = getAttributeNS(android, name)

    private fun Element.all(): List<Element> {
        val out = mutableListOf(this)
        val nodes = getElementsByTagName("*")
        for (i in 0 until nodes.length) out += nodes.item(i) as Element
        return out
    }

    private fun Element.byId(id: String): Element? = all().firstOrNull { it.attr("id") == "@+id/$id" }

    private val widgetLayouts = listOf(
        "widget_sun_small", "widget_sun_medium", "widget_sun_large_tall", "widget_sun_large_wide", "widget_sun_large_compact",
    )
    private val previews = listOf("widget_sun_small_preview", "widget_sun_medium_preview", "widget_sun_large_preview")

    @Test
    fun `each initial layout shows the hint and hides the content until the first render`() {
        for (size in listOf("small", "medium", "large")) {
            val info = parse(TestFiles.require("${TestFiles.RES}/xml/widget_sun_${size}_info.xml"))
            val initial = info.attr("initialLayout").removePrefix("@layout/")
            val root = layout(initial)
            val hint = assertNotNullAndGet(root.byId("hint"), "$initial hint")
            assertTrue(hint.attr("visibility").isEmpty() || hint.attr("visibility") == "visible")
            assertEquals("@string/widget_sun_hint", hint.attr("text"))
            assertEquals("$initial content", "invisible", root.byId("content")!!.attr("visibility"))
            assertEquals("0", info.attr("updatePeriodMillis"))
            assertEquals("@layout/widget_sun_${size}_preview", info.attr("previewLayout"))
        }
        assertEquals("widget_sun_large_compact", parse(TestFiles.require("${TestFiles.RES}/xml/widget_sun_large_info.xml")).attr("initialLayout").removePrefix("@layout/"))
    }

    private fun assertNotNullAndGet(e: Element?, what: String): Element {
        assertNotNull(what, e)
        return e!!
    }

    @Test
    fun `left to right, one description target, the ticking view hidden from TalkBack, RemoteViews classes only`() {
        val allowed = setOf("FrameLayout", "LinearLayout", "TextView", "Chronometer", "ImageView")
        for (name in widgetLayouts + previews) {
            val root = layout(name)
            assertEquals(name, "ltr", root.attr("layoutDirection"))
            assertEquals(name, "@android:id/background", root.attr("id"))
            for (e in root.all()) assertTrue("$name: ${e.tagName}", e.tagName in allowed)
        }
        for (name in widgetLayouts) {
            val root = layout(name)
            val content = root.byId("content")!!
            assertEquals("noHideDescendants", content.attr("importantForAccessibility"))
            val chrono = root.all().filter { it.tagName == "Chronometer" }
            if (name != "widget_sun_small") {
                assertEquals(name, 1, chrono.size)
                assertTrue(content.all().contains(chrono.single()))
                assertEquals("true", chrono.single().attr("countDown"))
            }
        }
    }

    @Test
    fun `the large layouts have every view the renderer fills`() {
        val common = listOf(
            "content", "hint", "header_place", "header_date", "times_rise", "times_set", "times_polar",
            "count_chrono", "count_static", "noon_label", "noon_value",
        ) + listOf("golden", "civil", "nautical", "astro").flatMap { p -> listOf("_label", "_a", "_b", "_span").map { p + it } }
        for (name in listOf("widget_sun_large_tall", "widget_sun_large_wide", "widget_sun_large_compact")) {
            val root = layout(name)
            for (id in common) assertNotNull("$name: $id", root.byId(id))
            val heads = listOf("heads_label", "heads_dawn", "heads_dusk").map { root.byId(it) }
            if (name == "widget_sun_large_tall") heads.forEach { assertNotNull(it) } else heads.forEach { assertEquals(null, it) }
        }
    }

    @Test
    fun `the layout geometry is the geometry TextFit assumes`() {
        fun pads(name: String) = layout(name).let { listOf(it.attr("paddingStart"), it.attr("paddingTop"), it.attr("paddingEnd"), it.attr("paddingBottom")) }
        fun dp(v: Float) = "${v.toInt()}dp"
        assertEquals(listOf(dp(WidgetFit.SMALL_PAD_H), dp(WidgetFit.SMALL_PAD_V), dp(WidgetFit.SMALL_PAD_H), dp(WidgetFit.SMALL_PAD_V)), pads("widget_sun_small"))
        assertEquals(listOf(dp(WidgetFit.MEDIUM_PAD_H), dp(WidgetFit.MEDIUM_PAD_V), dp(WidgetFit.MEDIUM_PAD_H), dp(WidgetFit.MEDIUM_PAD_V)), pads("widget_sun_medium"))
        for (name in listOf("widget_sun_large_tall", "widget_sun_large_wide", "widget_sun_large_compact")) {
            assertEquals(listOf(dp(WidgetFit.LARGE_PAD_H), dp(WidgetFit.LARGE_PAD_V), dp(WidgetFit.LARGE_PAD_H), dp(WidgetFit.LARGE_PAD_V)), pads(name))
            val root = layout(name)
            for (p in listOf("golden", "civil", "nautical", "astro")) {
                assertEquals("${(WidgetFit.LABEL_SHARE * 100).toInt()}", root.byId("${p}_label")!!.attr("layout_weight"))
                assertEquals("${(WidgetFit.VALUE_SHARE * 100).toInt()}", root.byId("${p}_a")!!.attr("layout_weight"))
                assertEquals("${(WidgetFit.VALUE_SHARE * 100).toInt()}", root.byId("${p}_b")!!.attr("layout_weight"))
                assertEquals("${(WidgetFit.VALUE_SHARE * 200).toInt()}", root.byId("${p}_span")!!.attr("layout_weight"))
            }
            // The place takes the rest of the header row; the date is as wide as its text, after a gap.
            val place = root.byId("header_place")!!
            val date = root.byId("header_date")!!
            assertEquals("1", place.attr("layout_weight"))
            assertEquals("0dp", place.attr("layout_width"))
            assertEquals("wrap_content", date.attr("layout_width"))
            assertEquals("", date.attr("layout_weight"))
            assertEquals(dp(WidgetFit.HEADER_GAP), date.attr("layout_marginStart"))
        }
        val wide = layout("widget_sun_large_wide").byId("content")!!
        val columns = wide.childNodes.let { n -> (0 until n.length).map { n.item(it) }.filterIsInstance<Element>() }
        assertEquals(listOf("${(WidgetFit.WIDE_LEFT * 100).toInt()}", "${((1 - WidgetFit.WIDE_LEFT) * 100).toInt()}"), columns.map { it.attr("layout_weight") })
        assertEquals(dp(WidgetFit.WIDE_GAP), columns[1].attr("layout_marginStart"))
        assertEquals(dp(WidgetFit.COL_GAP), layout("widget_sun_medium").byId("times_set")!!.attr("layout_marginStart"))
    }

    @Test
    fun `no autosize except the hint, and the Inter font only from Android 8`() {
        val base = sunStyles()
        assertFalse(base.contains("autoSize"))
        assertFalse(base.contains("@font/"))
        val v26 = parse(TestFiles.require("${TestFiles.RES}/values-v26/widget_styles.xml"))
        val styles = v26.getElementsByTagName("style")
        for (i in 0 until styles.length) {
            val st = styles.item(i) as Element
            if (st.getElementsByTagName("item").let { items -> (0 until items.length).any { (items.item(it) as Element).getAttribute("name").contains("autoSize") } }) {
                assertEquals("Widget.Twinlit.Sun.HintFit", st.getAttribute("name"))
            }
        }
        for (name in widgetLayouts + previews) {
            assertFalse(name, TestFiles.require("${TestFiles.RES}/layout/$name.xml").readText().contains("autoSize"))
        }
    }

    @Test
    fun `the previews show Oslo, the privacy rule's place`() {
        val large = layout("widget_sun_large_preview")
        assertEquals("Oslo", large.byId("header_place")!!.attr("text"))
    }

    /**
     * Every widget text has a soft shadow (Widget.Twinlit.Sun.Text, radius 3). A view group cuts each
     * child's drawing at the child's bounds by default, and with clipToPadding at its padding box:
     * the shadow of a text that ends at its view's edge was cut there, on the left of a start-aligned
     * text and on the right of an end-aligned one ("Thu 19 Nov", "from 14:19"). A TextView itself
     * widens its own clip for its shadow; the cut is the parent's. No layout adds size for it (that
     * would change the size the arrangements need, LargeLayout); instead no view group of any widget
     * layout, the root included, clips its children to their bounds or to its padding, so the shadow
     * reaches the panel's own padding (the root's clipToOutline still keeps it inside the panel).
     */
    @Test
    fun `no widget layout clips a text shadow on any side`() {
        val names = widgetLayouts + previews
        assertEquals(8, names.size)
        var groups = 0
        var texts = 0
        for (name in names) {
            val root = layout(name)
            val all = root.all()
            val viewGroups = all.filter { it.tagName == "LinearLayout" || it.tagName == "FrameLayout" }
            assertTrue(name, viewGroups.isNotEmpty())
            for (g in viewGroups) {
                val what = "$name ${g.tagName} ${g.attr("id")}"
                assertEquals(what, "false", g.attr("clipChildren"))
                assertEquals(what, "false", g.attr("clipToPadding"))
                groups++
            }
            // Only these view groups: a RelativeLayout, GridLayout or other group would need the same.
            assertEquals(name, emptyList<String>(), all.filter { it.tagName.endsWith("Layout") && it.tagName != "LinearLayout" && it.tagName != "FrameLayout" }.map { it.tagName })
            // Every text sits inside a group that does not clip it, up to the root.
            texts += all.count { it.tagName == "TextView" || it.tagName == "Chronometer" }
        }
        assertTrue("view groups checked: $groups", groups >= 54)
        assertTrue("texts under them: $texts", texts >= 120)
        // The shadow is on the base text style and on the hint, which the layouts take as styles.
        assertTrue(sunStyles().contains("android:shadowRadius"))
    }
}
