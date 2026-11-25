package no.stormberry.twinlit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import no.stormberry.twinlit.ui.components.CITY_LIST_DATE
import no.stormberry.twinlit.ui.components.FooterIcons
import no.stormberry.twinlit.ui.components.FooterLines
import no.stormberry.twinlit.ui.components.FooterTargets
import no.stormberry.twinlit.ui.components.LICENCE_BSD_2_CLAUSE
import no.stormberry.twinlit.ui.components.LICENCE_CC_BY
import no.stormberry.twinlit.ui.theme.MoonDusk
import no.stormberry.twinlit.ui.theme.MoonLight
import no.stormberry.twinlit.ui.theme.MoonNight
import no.stormberry.twinlit.ui.theme.PaletteExpected
import no.stormberry.twinlit.ui.theme.SunDusk
import no.stormberry.twinlit.ui.theme.SunLight
import no.stormberry.twinlit.ui.theme.SunNight
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.time.LocalDate
import kotlin.math.pow

/**
 * The parts of the footer that are not layout (SunApp's `FooterTest`, adapted): where every link
 * goes, that the icon row's marks are well formed, the licence credit that the licences ask of every
 * binary (L-4, fix round 1), and the legal line, SunApp 2.0.0's, restored by Marcos on 2026-11-21
 * (R5 D5, D19).
 */
class FooterTest {

    private val footerSource get() = TestFiles.require("${TestFiles.KOTLIN}/ui/components/Footer.kt").readText()

    @Test
    fun `the links CheckLink's footer has go to the same places`() {
        assertEquals("https://stormberry.as", FooterTargets.SITE)
        assertEquals("https://linkedin.com/company/StormberryAS", FooterTargets.LINKEDIN)
        assertEquals("https://github.com/StormberryAS", FooterTargets.GITHUB)
        assertEquals("nostr:npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa", FooterTargets.NOSTR)
        assertEquals("http://stormberryr7fz5xdopi7f265d7id27bgizc6uxeliaepzstompcfpyd.onion", FooterTargets.ONION)
        assertEquals("https://virksomhet.brreg.no/nb/oppslag/enheter/937751249", FooterTargets.ORG_REGISTER)
    }

    @Test
    fun `Privacy is the company page and Disclaimer is Twinlit's own page (D21)`() {
        assertEquals("https://stormberry.as/privacy.html", FooterTargets.PRIVACY)
        assertEquals("https://twinlit.stormberry.as/disclaimer.html", FooterTargets.DISCLAIMER)
        // SunApp's page is SunApp's own, so it is not used.
        assertFalse(FooterTargets.all.any { it.contains("sun.stormberry.as") || it.contains("moon.stormberry.as") })
    }

    @Test
    fun `every target is a plain web address or a nostr address, with no sign-in details`() {
        for (url in FooterTargets.all) {
            val uri = URI(url)
            assertTrue(url, uri.scheme in setOf("https", "http", "nostr"))
            assertEquals(url, null, uri.rawUserInfo)
            if (uri.scheme == "nostr") {
                assertTrue(url, uri.isOpaque && !uri.rawSchemeSpecificPart.startsWith("/"))
            } else {
                assertFalse(url, uri.host.isNullOrEmpty())
            }
        }
        // Thirteen links: wordmark, four icons, the register, Privacy and Disclaimer, the five of the credit.
        assertEquals(13, FooterTargets.all.size)
        assertEquals(13, FooterTargets.all.toSet().size)
    }

    @Test
    fun `the credit sentence links each part to its source and each licence to its text`() {
        assertEquals("https://github.com/mourner/suncalc", FooterTargets.SUNCALC)
        assertEquals("https://github.com/mourner/suncalc/blob/master/LICENSE", FooterTargets.SUNCALC_LICENCE)
        assertEquals("https://www.geonames.org/", FooterTargets.GEONAMES)
        assertEquals("https://www.kartverket.no/en/api-and-data", FooterTargets.KARTVERKET)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", FooterTargets.CC_BY)
        assertEquals(5, FooterTargets.credits.size)
    }

    @Test
    fun `the footer carries the L-4 credit line that the licences of SunCalc and the city data ask for`() {
        // GeoNames and Kartverket data (CC BY 4.0) and SunCalc's formulae (BSD 2-Clause) ship in the
        // APK; the attribution is in the app (TEXTS.md L-4) and in NOTICE, never in neither.
        val kt = footerSource
        assertTrue(kt.contains("""append("Runs entirely on your device: our own code, ")"""))
        assertTrue(kt.contains("""url("SunCalc", FooterTargets.SUNCALC)"""))
        assertTrue(kt.contains("""append("'s formulae (")"""))
        assertTrue(kt.contains("url(LICENCE_BSD_2_CLAUSE, FooterTargets.SUNCALC_LICENCE)"))
        assertTrue(kt.contains("""append(") and city data from ")"""))
        assertTrue(kt.contains("""url("GeoNames", FooterTargets.GEONAMES)"""))
        assertTrue(kt.contains("""url("Kartverket", FooterTargets.KARTVERKET)"""))
        assertTrue(kt.contains("url(LICENCE_CC_BY, FooterTargets.CC_BY)"))
        assertTrue(kt.contains("""append("). Nothing is sent.")"""))
        // Kartverket is credited without naming a place.
        assertFalse(kt.contains("stedsnavn"))
    }

    @Test
    fun `every licence label stays on one line`() {
        for (label in listOf(LICENCE_BSD_2_CLAUSE, LICENCE_CC_BY)) {
            assertFalse(label, label.contains(' '))
            assertFalse(label, label.contains('-'))
        }
        assertEquals("BSD 2‑Clause", LICENCE_BSD_2_CLAUSE)
        assertEquals("CC BY 4.0", LICENCE_CC_BY)
    }

    // SunApp 2a26f98 Footer.kt line 312; the line itself is not written in the tests.
    private val SUNAPP_LEGAL_LINE_SHA256 = "e912a82e2fe0a2adf032998e84c6b648e9cadb6c27892e89c35ba73813785109"

    // SunApp 2a26f98 DISCLAIMER.md line 125.
    private val SUNAPP_DISCLAIMER_LINE_SHA256 = "e48f6e7769006b60c47108642fc0ebd81215b6344a4511067de5ff394d0811a0"

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `the legal line is SunApp 2_0_0's line with the registered municipality, plus Licences (R5 D5)`() {
        val kt = footerSource
        assertTrue(kt.contains("""append("© 2026 Stormberry AS · ")"""))
        // The organisation number uses no-break spaces, as the web footer's nowrap does.
        assertTrue(kt.contains("url(\"Org.nr.\u00A0NO\u00A0937\u00A0751\u00A0249\", FooterTargets.ORG_REGISTER)"))
        // Exactly one line carries the company's address, and it is SunApp's line byte for byte.
        val lines = kt.split("\n").filter { it.contains(", Norway · \")") }
        assertEquals(1, lines.size)
        assertEquals(SUNAPP_LEGAL_LINE_SHA256, sha256(lines[0]))
        assertFalse(kt.contains("""append(" · Norway · ")"""))
        assertTrue(kt.contains("""url("Privacy", FooterTargets.PRIVACY)"""))
        assertTrue(kt.contains("""url("Disclaimer", FooterTargets.DISCLAIMER)"""))
        assertFalse(kt.contains("lastUpdate") || kt.contains("LastUpdate"))
        assertEquals("Your places and alarms never leave this device. Disconnect and try it.", FooterLines.PRIVACY_LINE)
        assertEquals("Alarm permissions only. No internet. No tracking.", FooterLines.PERMISSIONS_LINE)
    }

    @Test
    fun `DISCLAIMER md names the company as SunApp 2_0_0 does (R5 D5)`() {
        val lines = TestFiles.require("DISCLAIMER.md").readText().split("\n")
            .filter { it.startsWith("**Stormberry AS**, org.nr.") }
        assertEquals(1, lines.size)
        assertEquals(SUNAPP_DISCLAIMER_LINE_SHA256, sha256(lines[0]))
    }

    @Test
    fun `each social icon is a 48 dp target with the spoken Open label (L-6)`() {
        val kt = footerSource
        assertTrue(kt.contains("""onClickLabel = "Open ${'$'}label""""))
        assertTrue(kt.contains(".size(48.dp)"))
        for (name in listOf("LinkedIn", "GitHub", "Nostr", "Tor onion site")) assertTrue(name, kt.contains("\"$name\""))
    }

    @Test
    fun `NOTICE documents every credit and what was left out of the footer, and the README names them too`() {
        val footer = FooterTargets.all.joinToString("\n")
        for (left in listOf("lucide", "JetBrains", "androidx", "desugar", "openjdk", "apache.org", "rsms.me", "openfontlicense")) {
            assertFalse(left, footer.contains(left, ignoreCase = true))
        }
        val text = TestFiles.require("NOTICE").readText()
        val needles = listOf(
            "SunCalc", "Inter", "GeoNames", "Kartverket", "BSD 2-Clause", "OFL-1.1", "CC BY 4.0",
            "desugar_jdk_libs", "Classpath Exception", "Lucide", "ISC", "ostrich", "onion",
            "Kotlin", "Jetpack", "Material", "Apache License 2.0",
            // The BSD 2-Clause terms themselves: clause 2 asks for them in the materials of a binary.
            "Copyright (c) 2011-2015, Vladimir Agafonkin. All rights reserved.",
            "Redistributions in binary form must reproduce the above copyright notice",
            "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS",
        )
        for (needle in needles) assertTrue(needle, text.contains(needle))
        val noticeUrls = listOf(
            FooterTargets.SUNCALC, "https://rsms.me/inter/", FooterTargets.GEONAMES,
            "https://github.com/google/desugar_jdk_libs", "https://openjdk.org/legal/gplv2+ce.html",
            "https://github.com/lucide-icons/lucide", "https://github.com/lucide-icons/lucide/blob/main/LICENSE",
            "https://github.com/JetBrains/kotlin", "https://github.com/androidx/androidx",
            "https://www.apache.org/licenses/LICENSE-2.0", FooterTargets.CC_BY,
        )
        for (url in noticeUrls) assertTrue(url, text.contains(url))
        val readme = TestFiles.requireAtRoot("README.md").readText()
        for (needle in listOf("SunCalc", "GeoNames", "Kartverket", "Inter", "Lucide", "Kotlin", "Jetpack", "NOTICE")) {
            assertTrue(needle, readme.contains(needle))
        }
    }

    @Test
    fun `the vendored suncalc js and the two ports carry the SunCalc copyright`() {
        val copyright = "Vladimir Agafonkin"
        val js = TestFiles.require("tools/golden/suncalc.js").readText().lines().take(16).joinToString("\n")
        assertTrue(js.contains("Copyright (c) 2011-2015, $copyright"))
        assertTrue(js.contains("Redistributions of source code must retain the above copyright notice"))
        assertTrue(TestFiles.require("${TestFiles.KOTLIN}/lunar/MoonCalc.kt").readText().contains(copyright))
        assertTrue(TestFiles.require("${TestFiles.KOTLIN}/solar/SunCalc.kt").readText().contains(copyright))
    }

    @Test
    fun `the four footer marks build, and the Nostr outline survives parsing whole`() {
        listOf(FooterIcons.linkedIn, FooterIcons.gitHub, FooterIcons.nostr, FooterIcons.tor)
            .forEach { assertTrue(it.name, it.defaultWidth.value == 24f) }
        val nostrNodes = (FooterIcons.nostr.root[0] as VectorPath).pathData
        assertEquals(138, nostrNodes.size)
        assertEquals(PathNode.Close, nostrNodes.last())
    }

    @Test
    fun `the city list date is a real date, and not one from the future`() {
        assertFalse(LocalDate.parse(CITY_LIST_DATE).isAfter(LocalDate.now().plusDays(1)))
    }

    @Test
    fun `the footer text and links are readable in every palette the Sun and Moon tabs can show`() {
        // The footer ends the page on the tab's own Background; the closing notes are drawn at 85 per
        // cent of TextSecondary. WCAG AA 4.5 to 1 in every theme (BRIEF).
        data class P(val name: String, val bg: Color, val secondary: Color, val primary: Color, val link: Color)
        val palettes = listOf(
            P("SunNight", SunNight.Background, SunNight.TextSecondary, SunNight.TextPrimary, SunNight.Blue),
            P("SunDusk", SunDusk.Background, SunDusk.TextSecondary, SunDusk.TextPrimary, SunDusk.Blue),
            P("SunLight", SunLight.Background, SunLight.TextSecondary, SunLight.TextPrimary, SunLight.Blue),
            P("MoonNight", MoonNight.Background, MoonNight.TextSecondary, MoonNight.TextPrimary, MoonNight.Blue),
            P("MoonDusk", MoonDusk.Background, MoonDusk.TextSecondary, MoonDusk.TextPrimary, MoonDusk.Blue),
            P("MoonLight", MoonLight.Background, MoonLight.TextSecondary, MoonLight.TextPrimary, MoonLight.Blue),
        )
        fun lum(c: Int, ch: Int): Double {
            val v = ((c shr ch) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        fun lumOf(argb: Int) = 0.2126 * lum(argb, 16) + 0.7152 * lum(argb, 8) + 0.0722 * lum(argb, 0)
        fun over(fg: Color, a: Float, bg: Color): Int {
            fun mix(ch: Int) = Math.round((((fg.toArgb() shr ch) and 0xFF) * a + ((bg.toArgb() shr ch) and 0xFF) * (1 - a)))
            return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        fun ratio(a: Int, b: Int): Double {
            val la = lumOf(a); val lb = lumOf(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
        for (p in palettes) {
            val bg = p.bg.toArgb() or (0xFF shl 24)
            assertTrue("${p.name} TextSecondary", ratio(p.secondary.toArgb(), bg) >= 4.5)
            val at85 = ratio(over(p.secondary, 0.85f, p.bg), bg)
            assertTrue("${p.name} TextSecondary at 85 per cent", at85 >= 4.5)
            // R4: the pinned values of CHANGES-R4 section 8 (SunDusk 9.31, MoonDusk 7.38, SunNight 5.50, MoonNight 4.99, ...);
            // this test composites in 8-bit steps, the table in floats, hence 0.05.
            assertEquals("${p.name} footer at 85 per cent", PaletteExpected.footer85.getValue(p.name), at85, 0.05)
            assertTrue("${p.name} TextPrimary", ratio(p.primary.toArgb(), bg) >= 4.5)
            assertTrue("${p.name} link", ratio(p.link.toArgb(), bg) >= 4.5)
        }
    }
}
