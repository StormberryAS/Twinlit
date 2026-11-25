package no.stormberry.twinlit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Twinlit backs up NOTHING (SunApp 1.1.9 rule, kept). In both backup rule formats a section without an
 * `<include>` means "everything", so "nothing" has to be written as an exclusion of every domain.
 * Without it the chosen place (`shared_prefs/twinlit.xml`) and the alarm store would leave the
 * device in a cloud backup or a device transfer, and the footer's "Your places and alarms never
 * leave this device" would be false.
 */
class BackupRulesTest {

    @Test
    fun `Auto Backup on Android 11 and earlier excludes every domain and includes nothing`() {
        val root = parse("android/app/src/main/res/xml/backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertExcludesEverything("full-backup-content", root)
    }

    @Test
    fun `cloud backup and device transfer on Android 12 and later exclude every domain`() {
        val root = parse("android/app/src/main/res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", root.tagName)
        assertEquals(0, root.getElementsByTagName("include").length)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = root.getElementsByTagName(section)
            assertEquals("exactly one <$section>", 1, nodes.length)
            assertExcludesEverything(section, nodes.item(0) as Element)
        }
    }

    @Test
    fun `the manifest points at the two rule files`() {
        val manifest = requireFile("android/app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    private fun assertExcludesEverything(what: String, section: Element) {
        assertEquals("$what has an <include>", 0, section.getElementsByTagName("include").length)
        val excludes = section.getElementsByTagName("exclude")
        val covered = (0 until excludes.length).map { excludes.item(it) as Element }
            .filter { it.getAttribute("path") == "." }
            .map { it.getAttribute("domain") }
            .toSet()
        assertEquals("$what must exclude the whole of every domain", DOMAINS, covered)
    }

    private fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(requireFile(path)).documentElement

    private fun requireFile(path: String): File {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(4)
            .map { File(it, path) }
            .firstOrNull { it.isFile }
        // Hard failure, not a skip (fix round 1).
        assertNotNull("$path not reachable from the test's working directory", f)
        return f!!
    }

    private companion object {
        /** Every domain the two formats know, credential-protected and device-protected. */
        val DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
