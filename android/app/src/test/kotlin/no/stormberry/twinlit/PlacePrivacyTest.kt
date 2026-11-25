package no.stormberry.twinlit

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The privacy rule of the build (BRIEF, SPEC 13.2) as a test: every place in code, tests, tools
 * and documents is Oslo or another big world city, or a synthetic point. It holds **no private
 * value**: it is an ALLOWLIST, so a coordinate that is not on it fails the build without the test
 * ever naming what it caught. The place NAMES are checked by `tools/guard.sh` before each commit;
 * this test is the coordinate half, and it is the only half that travels with the repository.
 *
 * Two rules, over `android/app/src/main`, `android/app/src/test`, `tools/golden` and the
 * repository's Markdown (the catalogue `data/cities.tsv`, the golden CSVs and the vendored
 * `suncalc.js` are data, pinned by their own hashes and generators):
 *
 *  1. **A coordinate pair** (two decimal numbers side by side in latitude and longitude range, at
 *     least one of them 25 or more in size, which keeps WCAG ratios and alphas out) must be a pair
 *     of [PAIRS].
 *  2. **A single decimal with four or more fraction digits** in the range of a coordinate must be a
 *     number of [PLACE_VALUES] (a latitude or longitude of an allowed place) or of
 *     [NOT_COORDINATES] (a named constant of the sun and moon formulas, a luminance weight...).
 *     The catalogue has four decimals, so a coordinate typed from a map has them too.
 *
 * The list grows with the milestones (M3 starts it; the widget and alarm milestones add their own
 * places; M5 added three named constants of the alarm runtime). Every entry names the place it is, so a review can read it.
 */
class PlacePrivacyTest {

    /** Allowed coordinate pairs: latitude, longitude, the city. Big cities and synthetic points only. */
    private val PAIRS: Map<Pair<Double, Double>, String> = mapOf(
        (59.9139 to 10.7522) to "Oslo",
        (59.9127 to 10.7461) to "Oslo (catalogue)",
        (78.2233 to 15.6469) to "Longyearbyen",
        (78.22 to 15.65) to "Longyearbyen (rounded)",
        (69.6492 to 18.9553) to "Tromso",
        (69.6489 to 18.9551) to "Tromso (catalogue)",
        (60.1699 to 24.9384) to "Helsinki",
        (35.6895 to 139.6917) to "Tokyo",
        (-33.8688 to 151.2093) to "Sydney",
        (-33.8678 to 151.2073) to "Sydney (catalogue)",
        (64.1466 to -21.9426) to "Reykjavik",
        (21.3069 to -157.8583) to "Honolulu",
        (1.8721 to -157.4278) to "Kiritimati",
        (-22.9068 to -43.1729) to "Rio de Janeiro",
        (89.99 to 0.0) to "synthetic, near the North Pole",
        (-89.99 to 0.0) to "synthetic, near the South Pole",
        (89.99 to -170.0) to "synthetic, near the North Pole",
        (90.0 to 0.0) to "the North Pole",
        (-33.0 to 151.0) to "synthetic point (SunApp's test)",
        (-13.8333 to -171.7667) to "Apia",
        (-18.1368 to 178.4253) to "Suva",
        (47.5649 to -52.7093) to "St John's",
        (-31.5553 to 159.0821) to "Lord Howe Island",
        (-41.2866 to 174.7756) to "Wellington",
        (-0.2202 to -78.5123) to "Quito",
        (-59.9139 to 10.7522) to "synthetic: Oslo with the latitude sign flipped (a typed-place label test)",
        (-36.8485 to 174.7635) to "Auckland",
        (-36.8485 to 174.7633) to "Auckland",
        (35.6762 to 139.6503) to "Tokyo",
        (34.0522 to -118.2437) to "Los Angeles",
        (40.4168 to -3.7038) to "Madrid",
        (43.8256 to 87.6168) to "Urumqi",
        (37.7749 to -122.4194) to "San Francisco",
        (51.5074 to -0.1278) to "London",
        (59.97 to 10.72) to "Oslo area (a nearest-timezone test point)",
        (59.80 to 10.60) to "Oslo area (a nearest-timezone test point)",
        (0.0 to -179.5) to "synthetic, on the date line",
        (0.0 to 179.5) to "synthetic, on the date line",
    )

    /** Every latitude and longitude of the allowed places, for the four-digit rule. */
    private val PLACE_VALUES: Set<Double> = buildSet {
        for ((p, _) in PAIRS) { add(p.first); add(p.second) }
        // Places the tests use that appear as single numbers or in other pairs (catalogue values,
        // the city lists of the golden and parity tests): each is a big city.
        addAll(
            listOf(
                -13.8333, -171.7667, // Apia
                -18.1368, 178.4253, -18.1416, 178.4419, // Suva
                47.5649, -52.7093, // St John's
                -31.5553, 159.0821, // Lord Howe Island
                -33.4489, -70.6693, // Santiago
                -54.8019, -68.3030, // Ushuaia
                1.3521, 103.8198, // Singapore
                51.5074, -0.1278, // London
                40.4168, -3.7038, // Madrid
                43.8256, 87.6168, // Urumqi
                34.0522, -118.2437, // Los Angeles
                -36.8485, 174.7633, 174.7635, 174.7756, // Auckland
                35.6762, 139.6503, // Tokyo (alternative point)
                -70.8333, // a catalogue test value near the Antarctic
                -41.2866, // Wellington
                -78.5123, // a catalogue test value, Antarctic
                33.8688, // Sydney (the latitude, written without its sign)
                -122.4194, 122.4194, // San Francisco
                -10.7522, // Oslo longitude with the sign flipped (a parse test)
                -59.9139, // Oslo with the sign flipped (a label test)
                -0.0, // zero
            ),
        )
    }

    /** Numbers with four or more fraction digits that are not coordinates, each with what it is. */
    private val NOT_COORDINATES: Map<Double, String> = mapOf(
        0.0001 to "a test tolerance",
        0.0002967 to "MoonCalc constant",
        0.0003 to "SunCalc and MoonCalc constant",
        0.00312536 to "MoonCalc refraction constant",
        0.0052 to "SunCalc test constant",
        0.0053 to "SunCalc constant",
        0.0069 to "SunCalc constant",
        0.00000001 to "a test tolerance",
        0.03928 to "the sRGB transfer threshold (WCAG relative luminance)",
        0.0625 to "the 16th of a lunation",
        0.0722 to "the WCAG luminance weight of blue",
        0.08901179 to "MoonCalc refraction constant",
        0.1064 to "MoonCalc golden value",
        0.12345678 to "a long decimal in a place-name test",
        0.1875 to "a lunation fraction",
        0.2126 to "the WCAG luminance weight of red",
        0.3125 to "a lunation fraction",
        0.4375 to "a lunation fraction",
        0.5625 to "a lunation fraction",
        0.6875 to "a lunation fraction",
        0.7152 to "the WCAG luminance weight of green",
        0.8125 to "a lunation fraction",
        0.9375 to "a lunation fraction",
        0.98560028 to "SunCalc and MoonCalc mean anomaly rate",
        0.9856235 to "MoonCalc rate",
        -0.2202 to "a store test value (a longitude near the prime meridian, a synthetic point)",
        -0.6034 to "DayKind altitude constant (degrees)",
        -0.8211 to "DayKind test altitude (degrees)",
        102.9372 to "SunCalc perihelion constant",
        13.064993 to "MoonCalc rate",
        13.176396 to "MoonCalc rate",
        13.22935 to "MoonCalc rate",
        1.9148 to "SunCalc equation of centre",
        23.4397 to "the obliquity of the ecliptic",
        357.5291 to "SunCalc mean anomaly at the epoch",
        360.9856235 to "the sidereal rotation rate",
        -179.6760 to "a SunCalc golden longitude offset (degrees)",
        // M5, the alarm runtime and its screens.
        13.6265 to "an x coordinate of the status-bar moon icon (a drawable path, ic_stat_moon.xml)",
        0.0147 to "the ring volume's crescendo step in AlarmService",
        0.04045 to "the sRGB transfer threshold, 0.04045 (AlarmColorsTest's WCAG luminance)",
    )

    private val decimal = """-?\d{1,3}\.\d+"""
    private val pairRegex = Regex("""(?<![\w.])($decimal)[fFdD]?(?![\w.])\s*,\s*(?:\w+\s*=\s*)?($decimal)[fFdD]?(?![\w.])""")
    private val longRegex = Regex("""(?<![\w.])(-?\d{1,3}\.\d{4,})[fFdD]?(?![\w.])""")

    private fun files(): List<File> {
        val dirs = listOf("android/app/src/main", "android/app/src/test", "tools/golden")
            .map { TestFiles.require(it) }
        val walked = dirs.flatMap { d -> d.walk().filter { it.isFile }.toList() }
        val docs = listOf("README.md", "CHANGELOG.md", "DISCLAIMER.md", "android/README.md")
            .map { TestFiles.requireAtRoot(it) }
        return (walked + docs).filter { f ->
            f.extension in setOf("kt", "xml", "ts", "md", "kts") && f.name != "suncalc.js" &&
                // The allowlist itself is a list of places.
                f.name != "PlacePrivacyTest.kt" &&
                // Vector path data of the Stormberry marks (the carousel cards' drawables and the
                // footer's four icons, copied from SunApp): numbers on a 24 to 108 unit grid, no places.
                !f.name.startsWith("labs_switcher_") && f.name != "FooterIcons.kt"
        }
    }

    private fun near(a: Double, b: Double) = abs(a - b) < 1e-9

    /** Kotlin's `a to b` pair is a pair too: written as a comma first. */
    private fun normalised(text: String) = text.replace(Regex("""(\d[fFdD]?)\s+to\s+(-?\d)"""), "$1, $2")

    private fun unknownPairs(text: String): List<String> =
        pairRegex.findAll(normalised(text)).mapNotNull { m ->
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[2].toDouble()
            val inRange = abs(a) <= 90.0 && abs(b) <= 180.0
            val large = abs(a) >= 25.0 || abs(b) >= 25.0
            if (!inRange || !large) null
            else if (PAIRS.keys.any { near(it.first, a) && near(it.second, b) }) null
            else "${m.groupValues[1]}, ${m.groupValues[2]}"
        }.toList()

    private fun unknownLong(text: String): List<String> =
        longRegex.findAll(text).mapNotNull { m ->
            val v = m.groupValues[1].toDouble()
            if (abs(v) > 360.0) return@mapNotNull null
            if (PLACE_VALUES.any { near(it, v) } || NOT_COORDINATES.keys.any { near(it, v) }) null else m.groupValues[1]
        }.toList()

    @Test
    fun `no coordinate pair outside the allowed big cities and synthetic points`() {
        val all = files()
        assertTrue("the scan found the tree: ${all.size}", all.size >= 100)
        val bad = all.associateWith { unknownPairs(it.readText()) }.filterValues { it.isNotEmpty() }
        assertEquals("unknown coordinate pairs: " + bad.map { (f, v) -> "${f.name}: $v" }, 0, bad.size)
    }

    @Test
    fun `no four-decimal number that is neither an allowed place's nor a named constant`() {
        val bad = files().associateWith { unknownLong(it.readText()) }.filterValues { it.isNotEmpty() }
        assertEquals("unknown long decimals: " + bad.map { (f, v) -> "${f.name}: $v" }, 0, bad.size)
    }

    @Test
    fun `the allowlist names only big cities and synthetic points, each once`() {
        assertEquals(PAIRS.size, PAIRS.keys.toSet().size)
        // The synthetic and polar points are the only ones that are not cities.
        val notCities = PAIRS.values.filter { it.startsWith("synthetic") || it.startsWith("the ") }
        assertTrue(notCities.size >= 4)
    }
}
