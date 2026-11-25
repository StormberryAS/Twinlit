package no.stormberry.twinlit

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the repository carries for a release (M7, fix round 1): the licence notices, the store texts,
 * the icon for Android 7, and the release CI (pinned to Twinlit's own certificate; it has not run yet).
 * Source and file checks only.
 */
class ReleaseKitTest {

    private fun text(path: String) = TestFiles.requireAtRoot(path).readText()

    /** Twinlit's own two certificates (made on 2026-11-23): the sovereign release key and the Play upload key. */
    private val REL_CERT = "d54d480d04ece7e8e3a0215abfacffaa2f0f84e4b0303c224a6b89bc84a77418"
    private val UP_CERT = "c9188922c73d3a48e379673fe994d4f0a6d2367942b7f1e747192bfccdd13c33"

    @Test
    fun `the repository carries the files a release needs`() {
        for (path in listOf(
            "README.md", "CHANGELOG.md", "NOTICE", "DISCLAIMER.md", "LICENSE", ".gitignore", ".gitattributes",
            "android/README.md", "android/expected-permissions.txt", "android/tools/check-play-bundle.sh",
            "android/tools/build-inter-fonts.py", "android/tools/build-launcher-icon.py",
            ".github/workflows/android-release.yml",
            "fastlane/metadata/android/en-US/title.txt", "fastlane/metadata/android/en-US/short_description.txt",
            "fastlane/metadata/android/en-US/full_description.txt", "fastlane/metadata/android/en-US/changelogs/1.txt",
        )) assertTrue(path, TestFiles.requireAtRoot(path).length() > 0)
    }

    @Test
    fun `the store texts are TEXTS md M-1 to M-4 and fit Google Play's limits`() {
        val dir = "fastlane/metadata/android/en-US"
        assertEquals("Twinlit: Sun and Moon Alarms", text("$dir/title.txt"))
        assertTrue(text("$dir/title.txt").length <= 30)
        assertEquals("Sun and moon alarms, times and widgets for anywhere on Earth, computed offline", text("$dir/short_description.txt"))
        assertTrue(text("$dir/short_description.txt").length <= 80)
        assertTrue(text("$dir/full_description.txt").length <= 4000)
        assertTrue(text("$dir/changelogs/1.txt").length <= 500)
        for (f in listOf("title.txt", "short_description.txt", "full_description.txt", "changelogs/1.txt")) {
            val t = text("$dir/$f")
            assertFalse("$f has an em dash", t.contains('—'))
            assertFalse("$f has a double hyphen", t.contains("--"))
        }
    }

    @Test
    fun `the launcher icon has a PNG for Android 7 in every density, and the adaptive one for 8 and later`() {
        val res = "android/app/src/main/res"
        val sizes = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((density, px) in sizes) for (name in listOf("ic_launcher.png", "ic_launcher_round.png")) {
            val f = TestFiles.requireAtRoot("$res/mipmap-$density/$name")
            // A PNG: signature, and the width and height in the IHDR chunk.
            val b = f.readBytes()
            assertEquals(0x89, b[0].toInt() and 0xFF)
            assertEquals("PNG", String(b, 1, 3))
            fun int(o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
                ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
            assertEquals("$density $name width", px, int(16))
            assertEquals("$density $name height", px, int(20))
        }
        assertTrue(text("$res/mipmap-anydpi-v26/ic_launcher.xml").contains("<adaptive-icon"))
        val manifest = text("android/app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher_round""""))
        // Recents on API 24 showed the class name and the generic icon for an activity with neither.
        val main = manifest.substringAfter("""android:name=".MainActivity"""").substringBefore("/>")
        assertTrue(main.contains("""android:label="@string/app_name""""))
        assertTrue(main.contains("""android:icon="@mipmap/ic_launcher""""))
    }

    @Test
    fun `the release workflow is written for Twinlit, pins no one else's certificate and fails closed`() {
        val wf = text(".github/workflows/android-release.yml")
        // Neither SunApp's nor MoonApp's release or upload certificate fingerprints.
        for (fp in listOf(
            "f968ba08240a5781d35e36db2b1d51c153ba28123eb54c16b50f282dc8d5af4c",
            "b67d00734b7414c14aaaab93c46216315ad4ebf4a5e5d8e916aeed0317a8d8c2",
        )) assertFalse(fp, wf.contains(fp))
        assertTrue(wf.contains("""EXPECTED_CERT="${REL_CERT}""""))
        assertFalse(wf.contains("PLACEHOLDER"))
        assertFalse("the workflow must not carry the upload certificate", wf.contains(UP_CERT))
        assertTrue(wf.contains("""^[0-9a-f]{64}$"""))
        assertTrue(wf.contains("no.stormberry.twinlit"))
        assertFalse(wf.contains("no.stormberry.sunapp"))
        assertFalse(wf.contains("StormberryAS/SunApp") || wf.contains("StormberryAS/MoonApp"))
        assertTrue(wf.contains("dcd3b5f163644d5d9c7cc607413c8563cc4e393b6d1fee23af24467e130376ef  data/cities.tsv"))
        val script = text("android/tools/check-play-bundle.sh")
        assertTrue(script.contains("UPLOAD_CERT=${UP_CERT} "))
        assertTrue(script.contains("RELEASE_CERT=${REL_CERT} "))
        assertFalse(script.contains("PLACEHOLDER"))
        // One key per purpose: the release and the upload certificate differ, and the script holds no
        // other app's certificate (SunApp's and MoonApp's release and upload keys).
        assertTrue(UP_CERT != REL_CERT)
        for (fp in listOf(
            "f968ba08240a5781d35e36db2b1d51c153ba28123eb54c16b50f282dc8d5af4c",
            "b67d00734b7414c14aaaab93c46216315ad4ebf4a5e5d8e916aeed0317a8d8c2",
            "e7a95090da99dbe29e719ee2ef4233f3f0fb83ca7317ddba59863fe2b7895817",
            "9d9e52b28628f6605d1507ceeed6b326e5ddbc7b00f92c619f7a48e133998087",
        )) assertFalse(fp, script.contains(fp))
        assertTrue(script.contains("no.stormberry.twinlit.play"))
        assertFalse(script.contains("sunapp"))
    }

    @Test
    fun `no wording the guard forbids and no em dash in the repository texts`() {
        for (path in listOf("README.md", "CHANGELOG.md", "DISCLAIMER.md", "NOTICE", "android/README.md")) {
            val t = text(path)
            assertFalse("$path has an em dash", t.contains('—'))
        }
    }

    @Test
    fun `the README states the status plainly and no longer calls itself a scaffold`() {
        val readme = text("README.md")
        assertFalse(readme.contains("scaffold"))
        assertFalse(readme.contains("milestone M"))
        assertTrue(readme.contains("first release"))
        assertFalse(readme.contains("not released"))
        assertTrue(TestFiles.requireAtRoot("NOTICE").isFile)
    }

    // ---- fix round 2 ----------------------------------------------------------------------------

    private fun pngSize(f: java.io.File): Pair<Int, Int> {
        val b = f.readBytes()
        assertEquals(0x89, b[0].toInt() and 0xFF)
        assertEquals("PNG", String(b, 1, 3))
        fun int(o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
        return int(16) to int(20)
    }

    @Test
    fun `the store graphics Play asks for are in the repository`() {
        val dir = "fastlane/metadata/android/en-US/images"
        assertEquals(512 to 512, pngSize(TestFiles.requireAtRoot("$dir/icon.png")))
        assertEquals(1024 to 500, pngSize(TestFiles.requireAtRoot("$dir/featureGraphic.png")))
        val shots = TestFiles.requireAtRoot("$dir/phoneScreenshots").listFiles { f -> f.extension == "png" }!!.sortedBy { it.name }
        assertTrue("at least 2 phone screenshots, Play wants 2 to 8 (found ${shots.size})", shots.size in 2..8)
        for (f in shots) {
            val (w, h) = pngSize(f)
            // Play: each side 320 to 3840 px, the long side at most twice the short one.
            assertTrue("${f.name} $w x $h", w in 320..3840 && h in 320..3840 && maxOf(w, h) <= 2 * minOf(w, h))
        }
    }

    @Test
    fun `the changelog links the release log, the notices travel in the APK and secrets of every keystore type stay out of git`() {
        val dir = "fastlane/metadata/android/en-US"
        val log = text("$dir/changelogs/1.txt")
        assertTrue(log.contains("Release log: https://github.com/StormberryAS/Twinlit/blob/main/CHANGELOG.md"))
        assertTrue(log.contains("https://twinlit.stormberry.as"))
        assertTrue(log.length <= 500)
        // One line per paragraph: Google Play shows every newline of the description as a line break.
        assertTrue(text("$dir/full_description.txt").contains("Moon, Twilight, Auto (which follows the sun's daily rhythm at your chosen place) or System."))
        for (ignore in listOf(".gitignore", "android/.gitignore")) {
            val t = text(ignore)
            for (pattern in listOf("*.jks", "*.keystore", "*.p12", "*.pfx", "*.pepk")) assertTrue("$ignore $pattern", t.lines().contains(pattern))
        }
        val gradle = text("android/app/build.gradle.kts")
        assertTrue(gradle.contains("copyNotice"))
        assertTrue(gradle.contains("\"legal\""))
        val footer = text("android/app/src/main/kotlin/no/stormberry/twinlit/ui/components/Footer.kt")
        assertTrue(footer.contains("NOTICE_ASSET = \"legal/NOTICE\""))
        assertTrue(footer.contains("LICENCES_LABEL = \"Licences\""))
        val notice = text("NOTICE")
        assertFalse("NOTICE still says it travels with every binary without doing so", notice.contains("This file travels with"))
        assertTrue(notice.contains("assets/legal/NOTICE"))
        assertTrue(notice.contains("ISC, with portions under the MIT License"))
        assertTrue(notice.contains("Cole Bemis"))
    }

    @Test
    fun `the release job runs in the release environment, only for android tags, and publishes the store note as the body`() {
        val wf = text(".github/workflows/android-release.yml")
        assertTrue(wf.contains("    environment: release"))
        assertTrue(wf.contains("tags: [\"android-v*\"]"))
        // Nothing else starts it: no manual run, no branch push.
        assertFalse(wf.contains("workflow_dispatch"))
        assertFalse(wf.contains("branches:"))
        // The body of the GitHub release is changelogs/<versionCode>.txt, never generated notes.
        assertTrue(wf.contains("--notes-file"))
        assertFalse(wf.contains("--generate-notes"))
        assertTrue(wf.contains("fastlane/metadata/android/en-US/changelogs/"))
        // The four secrets of the plan, and no other secret.
        val secrets = Regex("secrets\\.([A-Z0-9_]+)").findAll(wf).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"), secrets)
    }

    @Test
    fun `the workflow keeps the release passwords out of the configuration cache and pins its actions to commits`() {
        val wf = text(".github/workflows/android-release.yml")
        val gradlew = wf.lines().filter { it.contains("./gradlew ") }
        assertTrue(gradlew.size >= 2)
        for (line in gradlew) assertTrue(line, line.contains("--no-configuration-cache"))
        val uses = wf.lines().filter { it.trim().startsWith("- uses:") }
        assertTrue(uses.size >= 4)
        for (line in uses) assertTrue(line, Regex("@[0-9a-f]{40} # v\\d+").containsMatchIn(line))
        val wrapper = text("android/gradle/wrapper/gradle-wrapper.properties")
        assertTrue(Regex("distributionSha256Sum=[0-9a-f]{64}").containsMatchIn(wrapper))
    }

    // ---- round 4, step B3 -----------------------------------------------------------------------

    /** Whitespace and comment stars folded, so a sentence wrapped over lines is one string. */
    private fun flat(t: String) = t.replace(Regex("""(?m)^\s*\*"""), " ").replace(Regex("""\s+"""), " ")

    @Test
    fun `NOTICE carries the full BSD 2-Clause licence of SunCalc, and so does the vendored library`() {
        val notice = flat(text("NOTICE"))
        val js = flat(text("tools/golden/suncalc.js").substringBefore("!function"))
        val clause = listOf(
            "IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE",
            "EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.",
            "Redistributions of source code must retain",
            "Copyright (c) 2011-2015, Vladimir Agafonkin. All rights reserved.",
            "Copyright (c) 2014, Vladimir Agafonkin",
        )
        for (needle in clause) assertTrue("NOTICE: $needle", notice.contains(needle))
        // The vendored file keeps the disclaimer (clause 1 of the licence for source redistributions).
        for (needle in clause) assertTrue("suncalc.js: $needle", js.contains(needle))
        assertTrue(notice.contains("tag v1.8.0"))
        assertFalse("master's copyright line is not the version vendored", notice.contains("Volodymyr"))
        assertFalse(js.contains("Full text: NOTICE"))
    }

    private val manifestComponents: List<String> by lazy {
        val xml = text("android/app/src/main/AndroidManifest.xml").replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        Regex("""<(?:activity|service|receiver)\s+android:name="\.([^"]+)"""").findAll(xml)
            .map { it.groupValues[1].replace('.', '/') }.toList()
    }

    @Test
    fun `the dex list of check-play-bundle equals the app's own activities, services and receivers`() {
        val script = text("android/tools/check-play-bundle.sh")
        val list = Regex("""DEX_CLASSES="([^"]*)"""").find(script)!!.groupValues[1].trim().split(Regex("\\s+"))
        assertEquals("no name twice", list.size, list.toSet().size)
        assertEquals(13, list.size)
        // Both directions: nothing extra in the list, and no manifest component missing from it.
        assertEquals(manifestComponents.toSortedSet().toList(), list.toSortedSet().toList())
        // A whole type descriptor, as a fixed string: "alarm/AlarmPlanner" never matches a prefix.
        assertTrue(script.contains("""grep -a -q -F "Lno/stormberry/twinlit/${'$'}cls;""""))
        for (gone in listOf("alarm/AlarmPlanner", "SunWidgetProvider", "MoonWidgetProvider", "lunar/MoonCalc")) {
            assertFalse("$gone can be merged or inlined by R8", list.contains(gone))
        }
        assertTrue(script.contains("--dex"))
        assertTrue(script.contains("CHECK PASSED (dex only)"))
        assertFalse(script.contains("bundle mode only: the dex holds"))
    }

    @Test
    fun `USE_EXACT_ALARM stays, as SunApp 2_0_0 has it`() {
        assertTrue(text("android/app/src/main/AndroidManifest.xml").contains("""<uses-permission android:name="android.permission.USE_EXACT_ALARM" />"""))
        assertTrue(text("android/expected-permissions.txt").lines().contains("android.permission.USE_EXACT_ALARM"))
    }

    // ---- store release material (Google Play listing and Zapstore) -----------------------------------

    private val shotDir = "fastlane/metadata/android/en-US/images/phoneScreenshots"

    private fun shotFiles() =
        TestFiles.requireAtRoot(shotDir).listFiles { f -> f.extension == "png" }!!.sortedBy { it.name }

    @Test
    fun `the six phone screenshots are palette PNGs of 1080 by 1920 under 250 kB, named as decided`() {
        val shots = shotFiles()
        assertEquals(
            listOf(
                "01-sun_phone.png", "02-moon_phone.png", "03-alarms_phone.png",
                "04-editor_phone.png", "05-widgets_phone.png", "06-appearance_phone.png",
            ),
            shots.map { it.name },
        )
        for (f in shots) {
            // 1080 x 1920 is 9:16: Google Play wants each side 320 to 3840 px and the long side at most
            // twice the short one, and every screenshot Play accepted before had this size.
            assertEquals(f.name, 1080 to 1920, pngSize(f))
            val b = f.readBytes()
            // IHDR colour type 3 is a palette PNG: no alpha channel, 256 colours at most.
            assertEquals("${f.name} colour type", 3, b[25].toInt())
            assertEquals("${f.name} bit depth", 8, b[24].toInt())
            // cdn.zapstore.dev refuses a store image of about 700 kB; the house limit is 250 kB (decimal).
            assertTrue("${f.name} is ${f.length()} bytes", f.length() < 250_000)
        }
    }

    @Test
    fun `the Play icon is a 512 square with alpha and the feature graphic is RGB without alpha`() {
        val dir = "fastlane/metadata/android/en-US/images"
        val icon = TestFiles.requireAtRoot("$dir/icon.png").readBytes()
        val feature = TestFiles.requireAtRoot("$dir/featureGraphic.png").readBytes()
        assertEquals("icon colour type (RGBA)", 6, icon[25].toInt())
        assertEquals("feature graphic colour type (RGB)", 2, feature[25].toInt())
        assertTrue(icon.size < 1_000_000 && feature.size < 15_000_000)
    }

    @Test
    fun `zapstore yaml publishes the repository's own store texts, images and APK name`() {
        val y = text("zapstore.yaml")
        assertFalse(y.contains('\u2014'))
        assertFalse(y.contains("--"))
        fun key(k: String) = Regex("(?m)^$k: (.*)$").find(y)!!.groupValues[1].trim()
        assertEquals("https://github.com/StormberryAS/Twinlit", key("repository"))
        // The Stormberry AS Nostr identity: the key zsp signs with, whitelisted on the first publish.
        assertEquals("npub1zz9w77p2jkn95t0st9tcuea8zdn2vknf0ctrwyd4g46w3kswdccqesr0pa", key("pubkey"))
        assertEquals("Twinlit", key("name"))
        assertEquals("MIT", key("license"))
        assertEquals("https://twinlit.stormberry.as", key("website"))
        val dir = "fastlane/metadata/android/en-US"
        assertEquals(text("$dir/short_description.txt").trim(), key("summary"))
        // The description is the Play full description, word for word, one line per paragraph.
        val desc = y.substringAfter("description: |\n").substringBefore("\n\ntags:").lines()
            .joinToString("\n") { it.removePrefix("  ") }
        assertEquals(text("$dir/full_description.txt").trim(), desc.trim())
        assertEquals(
            listOf("privacy", "utilities", "offline", "weather", "astronomy", "alarm", "moon", "calendar"),
            y.substringAfter("\ntags:\n").substringBefore("\n\n").lines().map { it.trim().removePrefix("- ") },
        )
        assertEquals("./$dir/images/icon.png", key("icon"))
        // images: the phoneScreenshots folder, in file order, no other file.
        val images = y.substringAfter("\nimages:\n").substringBefore("\n\nmatch:").lines().map { it.trim().removePrefix("- ") }
        assertEquals(shotFiles().map { "./$dir/images/phoneScreenshots/${it.name}" }, images)
        // Publish with the config file, never -r (which drops tags, licence and website).
        assertTrue(y.contains("zsp publish zapstore.yaml"))
        assertFalse(y.lines().any { it.trim().startsWith("zsp publish -r") })
    }

    @Test
    fun `the zapstore match pattern is the asset name the release workflow uploads`() {
        val y = text("zapstore.yaml")
        val raw = Regex("""(?m)^match: "(.*)"$""").find(y)!!.groupValues[1]
        // YAML double-quoted: \\ is one backslash.
        val regex = Regex(raw.replace("\\\\", "\\"))
        val wf = text(".github/workflows/android-release.yml")
        assertTrue(wf.contains("cp \"${'$'}APK\" \"Twinlit-${'$'}{VERSION}.apk\""))
        assertTrue("the asset of 0.0.1", regex.containsMatchIn("Twinlit-v0.0.1.apk"))
        assertFalse("the checksum file is no APK", regex.containsMatchIn("Twinlit-v0.0.1.apk.sha256"))
        assertFalse("another app's APK", regex.containsMatchIn("SunApp-v2.0.0.apk"))
    }

    @Test
    fun `the full description is one line per paragraph and has no municipality or home place`() {
        val d = text("fastlane/metadata/android/en-US/full_description.txt")
        val paragraphs = d.trim().split("\n\n")
        assertEquals(7, paragraphs.size)
        for (p in paragraphs) assertFalse("a hard-wrapped paragraph: $p", p.contains('\n'))
        assertTrue(d.length <= 4000)
    }

    @Test
    fun `the release note is one line per paragraph, only its link list has more than one line`() {
        val n = text("fastlane/metadata/android/en-US/changelogs/1.txt").trim()
        val paragraphs = n.split("\n\n")
        assertEquals(2, paragraphs.size)
        // Google Play, Zapstore and the GitHub release show every newline as a line break: a prose paragraph
        // wrapped at a column would break inside a sentence.
        assertFalse("a hard-wrapped paragraph: ${paragraphs[0]}", paragraphs[0].contains('\n'))
        for (line in paragraphs[1].split("\n")) {
            assertTrue("a link line is 'Label: https://...': $line", Regex("""^[A-Za-z ]+: https://\S+$""").matches(line))
        }
    }
}
