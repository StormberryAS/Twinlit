package no.stormberry.twinlit

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest and the build script (SPEC 11 and 1.3): the nine permissions, the removals, no
 * backup, the launcher activity, since M5 the alarm components with their attributes, and the
 * editions rule. The alarm components came with M5 and the widget providers with M6, which makes
 * the component count final: three activities, one service, nine receivers (SPEC 11.2).
 */
class ManifestTest {

    private val manifest: String by lazy { TestFiles.require("${TestFiles.MAIN}/AndroidManifest.xml").readText() }
    private val gradle: String by lazy { TestFiles.require("android/app/build.gradle.kts").readText() }

    private fun withoutComments(xml: String) = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `the manifest declares exactly the nine permissions of expected-permissions txt`() {
        val declared = Regex("""<uses-permission\s+android:name="android\.permission\.([A-Z_]+)"((?:\s+android:maxSdkVersion="\d+")?)\s*/>""")
            .findAll(withoutComments(manifest)).map { it.groupValues[1] }.toSortedSet()
        val expected = TestFiles.require("android/expected-permissions.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.removePrefix("android.permission.") }.toSortedSet()
        assertEquals(expected, declared)
        assertEquals(9, declared.size)
    }

    @Test
    fun `the AlarmClock entry point is protected by a permission, which adds no uses-permission`() {
        val xml = withoutComments(manifest)
        val intent = component("activity", ".alarm.AlarmIntentActivity")
        assertTrue(intent.contains("""android:permission="com.android.alarm.permission.SET_ALARM""""))
        // Only that one component carries it, and it is not among the declared permissions.
        assertEquals(1, Regex("""android:permission=""").findAll(xml).count())
        assertFalse(Regex("""<uses-permission[^>]*com\.android\.alarm\.permission""").containsMatchIn(xml))
    }

    @Test
    fun `SCHEDULE_EXACT_ALARM stops at Android 12L and the removals are in place`() {
        val xml = withoutComments(manifest)
        assertTrue(Regex("""SCHEDULE_EXACT_ALARM"\s+android:maxSdkVersion="32"""").containsMatchIn(xml))
        for (gone in listOf("INTERNET", "ACCESS_NETWORK_STATE", "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION")) {
            assertTrue(gone, Regex("""android\.permission\.$gone"\s+tools:node="remove"""").containsMatchIn(xml))
        }
        assertEquals(2, Regex("""DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"\s+tools:node="remove"""").findAll(xml).count())
    }

    @Test
    fun `nothing leaves the device in a backup`() {
        val xml = withoutComments(manifest)
        assertTrue(xml.contains("""android:allowBackup="false""""))
        assertTrue(xml.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(xml.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    }

    @Test
    fun `the main activity is the exported launcher with the shell's configChanges`() {
        val xml = withoutComments(manifest)
        val activity = xml.substringAfter("<activity").substringBefore("</activity>")
        assertTrue(activity.contains("""android:name=".MainActivity""""))
        assertTrue(activity.contains("""android:exported="true""""))
        assertTrue(activity.contains("android.intent.category.LAUNCHER"))
        for (change in listOf("orientation", "screenSize", "screenLayout", "keyboardHidden", "uiMode", "density", "fontScale")) {
            assertTrue(change, activity.contains(change))
        }
        assertTrue(activity.contains("""android:windowSoftInputMode="adjustResize""""))
        assertTrue(xml.contains("""android:theme="@style/Theme.Twinlit""""))
        assertTrue(xml.contains("""android:label="@string/app_name""""))
    }

    /** One component's element from the comment-free manifest, by its android:name. */
    private fun component(tag: String, name: String): String {
        val xml = withoutComments(manifest)
        val start = Regex("""<$tag\s+android:name="${Regex.escape(name)}"""").find(xml)
        assertTrue("$tag $name is declared", start != null)
        val rest = xml.substring(start!!.range.first)
        // The element ends at its own end tag or at its self-closing slash, whichever is first
        // (an element with children has no self-closing slash before its end tag).
        val selfClose = rest.indexOf("/>").let { if (it < 0) Int.MAX_VALUE else it }
        val endTag = rest.indexOf("</$tag>").let { if (it < 0) Int.MAX_VALUE else it }
        val firstOpenEnd = rest.indexOf(">")
        val selfClosing = rest[firstOpenEnd - 1] == '/'
        return if (selfClosing) rest.substring(0, selfClose + 2) else rest.substring(0, endTag + "</$tag>".length)
    }

    @Test
    fun `the components are exactly the ones of SPEC 11_2, and the alarm ones have its attributes`() {
        val xml = withoutComments(manifest)
        // Three activities, one service, nine receivers: two alarm, six widget providers and the
        // one widget event receiver (milestone M6 added the last seven).
        assertEquals(3, Regex("""<activity\b""").findAll(xml).count())
        assertEquals(1, Regex("""<service\b""").findAll(xml).count())
        assertEquals(9, Regex("""<receiver\b""").findAll(xml).count())
        assertEquals(0, Regex("""<provider\b""").findAll(xml).count())

        val intent = component("activity", ".alarm.AlarmIntentActivity")
        for (needle in listOf(
            """android:exported="true"""", """android:excludeFromRecents="true"""", """android:noHistory="true"""",
            """android:taskAffinity=""""", "Theme.Translucent.NoTitleBar",
            // Only a caller that holds the AlarmClock permission may dismiss or snooze (fix round 1).
            """android:permission="com.android.alarm.permission.SET_ALARM"""",
            "android.intent.action.SET_ALARM", "android.intent.action.SHOW_ALARMS",
            "android.intent.action.DISMISS_ALARM", "android.intent.action.SNOOZE_ALARM", "android.intent.category.DEFAULT",
        )) assertTrue(needle, intent.contains(needle))

        val ring = component("activity", ".alarm.RingActivity")
        for (needle in listOf(
            """android:exported="false"""", """android:directBootAware="true"""", """android:launchMode="singleTask"""",
            """android:excludeFromRecents="true"""", """android:taskAffinity=""""", """android:showWhenLocked="true"""",
            """android:turnScreenOn="true"""", "uiMode",
        )) assertTrue(needle, ring.contains(needle))

        val service = component("service", ".alarm.AlarmService")
        for (needle in listOf("""android:exported="false"""", """android:directBootAware="true"""", """android:foregroundServiceType="systemExempted"""")) {
            assertTrue(needle, service.contains(needle))
        }

        val fire = component("receiver", ".alarm.AlarmFireReceiver")
        assertTrue(fire.contains("""android:exported="false"""") && fire.contains("""android:directBootAware="true""""))
        assertFalse(fire.contains("<intent-filter>"))

        val events = component("receiver", ".alarm.SystemEventReceiver")
        assertTrue(events.contains("""android:exported="true"""") && events.contains("""android:directBootAware="true""""))
        for (action in listOf(
            "android.intent.action.BOOT_COMPLETED", "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED", "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.TIME_SET", "android.intent.action.LOCALE_CHANGED",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )) assertTrue(action, events.contains(action))
        assertEquals(7, Regex("""<action\b""").findAll(events).count())
    }

    @Test
    fun `the six widget providers and the event receiver, SPEC 11_2 (milestone M6)`() {
        val providers = mapOf(
            ".widget.sun.SunWidgetSmall" to "widget_sun_small",
            ".widget.sun.SunWidgetMedium" to "widget_sun_medium",
            ".widget.sun.SunWidgetLarge" to "widget_sun_large",
            ".widget.moon.MoonWidgetSmall" to "widget_moon_small",
            ".widget.moon.MoonWidgetMedium" to "widget_moon_medium",
            ".widget.moon.MoonWidgetLarge" to "widget_moon_large",
        )
        for ((name, base) in providers) {
            val p = component("receiver", name)
            assertTrue(name, p.contains("""android:exported="false""""))
            assertFalse(name, p.contains("directBootAware"))
            assertFalse(name, p.contains("android:permission"))
            assertFalse(name, p.contains("android:enabled"))
            assertTrue(name, p.contains("""android:label="@string/${base}_label""""))
            assertTrue(name, p.contains("""<action android:name="android.appwidget.action.APPWIDGET_UPDATE" />"""))
            assertTrue(name, p.contains("""android:resource="@xml/${base}_info""""))
            assertEquals(name, 1, Regex("""<action\b""").findAll(p).count())
        }
        val events = component("receiver", ".widget.WidgetEventReceiver")
        assertTrue(events.contains("""android:enabled="false""""))
        assertTrue(events.contains("""android:exported="false""""))
        assertFalse(events.contains("directBootAware"))
        assertFalse(events.contains("android:permission"))
        for (action in listOf(
            "android.intent.action.TIME_SET", "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.LOCALE_CHANGED", "android.intent.action.MY_PACKAGE_REPLACED",
        )) assertTrue(action, events.contains(action))
        assertEquals(4, Regex("""<action\b""").findAll(events).count())
        // No widget component name says "alarm", and the widgets add no permission (nine, M1).
        val names = Regex("""<receiver\s+android:name="(\.widget\.[A-Za-z.]+)"""").findAll(withoutComments(manifest)).map { it.groupValues[1] }.toList()
        assertEquals(7, names.size)
        for (n in names) assertFalse(n, n.contains("alarm", ignoreCase = true))
    }

    @Test
    fun `no permission or component names a source app`() {
        assertFalse(manifest.contains("sunapp", ignoreCase = true))
        assertFalse(manifest.contains("moonapp", ignoreCase = true))
    }

    // ---------------------------------------------------------------- the editions rule (SPEC 1.3)

    @Test
    fun `the Play build changes the application ID, the signing, the code, the guards and the archive name only`() {
        assertTrue(gradle.contains("""applicationId = if (playBuild) "no.stormberry.twinlit.play" else "no.stormberry.twinlit""""))
        assertTrue(gradle.contains("""namespace = "no.stormberry.twinlit""""))
        assertTrue(gradle.contains("versionCode = playVersionCodeOffset + versionCode!!"))
        assertTrue(gradle.contains("val playVersionCodeOffset = 1000"))
        assertTrue(gradle.contains("""versionName = "0.0.1""""))
        assertTrue(gradle.contains("versionCode = 1\n"))
        assertTrue(gradle.contains("""archivesName = "twinlit-play""""))
        assertTrue(gradle.contains("""applicationIdSuffix = ".debug""""))
    }

    @Test
    fun `there is no flavour, no edition source set, no BuildConfig field and no Edition switch`() {
        assertFalse(gradle.contains("productFlavors"))
        assertFalse(gradle.contains("flavorDimensions"))
        assertFalse(gradle.contains("edition-play"))
        assertTrue(gradle.contains("buildConfig = false"))
        val tree = TestFiles.require("android/app/src").walk()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { it.name != "ManifestTest.kt" }
        for (f in tree) {
            val text = f.readText()
            assertFalse(f.path, Regex("""\bEdition\.\w+""").containsMatchIn(text))
            assertFalse(f.path, text.contains("BuildConfig."))
        }
        assertFalse(TestFiles.require("android/app/src").resolve("edition-play").exists())
    }

    @Test
    fun `the Play edition parser keeps SunApp's five-thing rule and refuses unknown values`() {
        assertTrue(gradle.contains("\"\", \"true\", \"1\", \"yes\" -> true"))
        assertTrue(gradle.contains("\"false\", \"0\", \"no\" -> false"))
        assertTrue(gradle.contains("is not a recognised"))
        assertTrue(gradle.contains("packageRelease") && gradle.contains("signReleaseBundle"))
    }

    @Test
    fun `no keystore is created or referenced by a path in the repository`() {
        assertFalse(gradle.contains(".jks\""))
        assertTrue(TestFiles.require("android/.gitignore").readText().contains("keystore.properties"))
    }
}
