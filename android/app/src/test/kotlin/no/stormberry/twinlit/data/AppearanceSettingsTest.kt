package no.stormberry.twinlit.data

import no.stormberry.twinlit.theme.Appearance
import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC 4.6: the choice is stored by key, the default is Auto, and nothing unknown can crash or mislead. */
class AppearanceSettingsTest {

    @Test
    fun `nothing stored, or an unknown value, reads as Auto`() {
        assertEquals(Appearance.AUTO, Appearance.fromKey(null))
        assertEquals(Appearance.AUTO, Appearance.fromKey(""))
        assertEquals(Appearance.AUTO, Appearance.fromKey("dusk"))
        assertEquals(Appearance.AUTO, Appearance.fromKey("SUN"))
        assertEquals(Appearance.AUTO, Appearance.fromKey("0"))
    }

    @Test
    fun `the five keys round trip`() {
        assertEquals(listOf("sun", "moon", "twilight", "auto", "system"), Appearance.entries.map { it.key })
        for (a in Appearance.entries) assertEquals(a, Appearance.fromKey(a.key))
    }

    @Test
    fun `the choice lives in the credential-encrypted twinlit file under the key appearance`() {
        assertEquals("twinlit", Settings.PREFS_NAME)
        val source = TestFiles.require("${TestFiles.KOTLIN}/data/Settings.kt").readText()
        assertTrue(source.contains("""private const val KEY_APPEARANCE = "appearance""""))
        assertTrue(source.contains("Appearance.fromKey(prefs.getString(KEY_APPEARANCE, null))"))
        assertTrue(source.contains("putString(KEY_APPEARANCE, value.key)"))
        // Never by ordinal or by name.
        assertTrue(!source.contains("value.ordinal"))
    }

    @Test
    fun `the preference file is not backed up and not device protected`() {
        val source = TestFiles.require("${TestFiles.KOTLIN}/data/Settings.kt").readText()
        assertTrue(source.contains("context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)"))
        assertTrue(!source.contains("createDeviceProtectedStorageContext"))
    }
}
