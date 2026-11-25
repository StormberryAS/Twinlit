package no.stormberry.twinlit.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ring service is a foreground service of type systemExempted, in both editions (2.0.0; it was
 * mediaPlayback). The manifest, the permission list, the README and the code must say the same
 * thing, and lint cannot check any of it: the type is a string in the manifest and an int in
 * code. These are source tests, because the framework classes are stubs on the plain JVM.
 */
class ForegroundServiceTypeTest {

    @Test
    fun `the main manifest declares the ring service as systemExempted and its permission`() {
        val manifest = repoText("android/app/src/main/AndroidManifest.xml")
        val service = Regex("""<service\s+android:name="\.alarm\.AlarmService"[^>]*/>""").find(manifest)
        assertTrue("AlarmService is declared", service != null)
        assertTrue(service!!.value, service.value.contains("""android:foregroundServiceType="systemExempted""""))
        assertTrue(
            manifest.contains("""<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />"""),
        )
        assertTrue(manifest.contains("""<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />"""))
    }

    @Test
    fun `no media playback type or permission is left in the manifest, the code or the permission list`() {
        val manifest = stripXmlComments(repoText("android/app/src/main/AndroidManifest.xml"))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertFalse(manifest.contains("mediaPlayback"))
        val code = repoText("android/app/src/main/kotlin/no/stormberry/twinlit/alarm/AlarmService.kt")
            .lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }
            .joinToString("\n")
        assertFalse(code.contains("FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK"))
        assertTrue(code.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED"))
        assertFalse(permissionList().contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertTrue(permissionList().contains("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"))
        assertEquals(9, permissionList().size)
    }

    @Test
    fun `the type is requested only from API 34, where the constant exists and a type is required`() {
        val code = repoText("android/app/src/main/kotlin/no/stormberry/twinlit/alarm/AlarmService.kt")
        val body = code.substringAfter("private fun foregroundType()").substringBefore("private fun autoSilence")
        assertTrue(body, body.contains("Build.VERSION_CODES.UPSIDE_DOWN_CAKE"))
        assertFalse(body, body.contains("VERSION_CODES.Q"))
        assertTrue(body, Regex("""else \{\s*0\s*\}""").containsMatchIn(body))
    }

    @Test
    fun `the README lists the new permission and no longer the old one`() {
        val readme = repoText("android/README.md")
        assertTrue(readme.contains("| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` |"))
        assertFalse(readme.contains("FOREGROUND_SERVICE_MEDIA_PLAYBACK` |"))
    }

    private fun permissionList(): Set<String> =
        repoText("android/expected-permissions.txt").lines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

    private fun stripXmlComments(s: String) = s.replace(Regex("<!--[\\s\\S]*?-->"), "")

    private fun repoText(path: String): String {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4).map { File(it, path) }.firstOrNull { it.isFile }
        // Hard failure, not a skip (fix round 1).
        assertNotNull("$path not reachable from the test's working directory", f)
        return f!!.readText()
    }
}
