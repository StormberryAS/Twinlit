package no.stormberry.twinlit.cities

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * The city catalogue is the same public file SunApp and MoonApp ship, byte for byte (SPEC 13.3,
 * D29). It holds hand-added rows near places the build tonight must never use in a test, a
 * preview or a screenshot; a pin proves the file is the one that was reviewed and that nobody has
 * "cleaned" it to make a test pass. A deliberate catalogue update changes this constant in the
 * same commit as the file, and the guard's pin (tools/guard.sh).
 */
class CatalogueHashTest {

    @Test
    fun `the catalogue is byte for byte the shared one`() {
        val bytes = TestCatalogue.file.readBytes()
        assertEquals("size", 930_549, bytes.size)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("dcd3b5f163644d5d9c7cc607413c8563cc4e393b6d1fee23af24467e130376ef", sha)
    }

    @Test
    fun `it parses to the published size`() {
        assertEquals(25_007, TestCatalogue.table.cities.size)
    }
}
