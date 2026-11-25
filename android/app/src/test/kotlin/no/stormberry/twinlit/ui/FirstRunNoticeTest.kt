package no.stormberry.twinlit.ui

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first-run notice (SPEC 9): whether it opens, and the text that must never drift from
 * TEXTS.md and DISCLAIMER.md. `shouldShowFirstRunNotice` takes two integers and touches no
 * Context, so every branch is asserted on a plain JVM.
 */
class FirstRunNoticeTest {

    @Test
    fun `a fresh install is shown the notice`() {
        // 0 is what SharedPreferences returns when the key has never been written.
        assertTrue(shouldShowFirstRunNotice(seenVersion = 0))
    }

    @Test
    fun `an install that has acknowledged this version is never shown it again`() {
        assertFalse(shouldShowFirstRunNotice(seenVersion = FIRST_RUN_NOTICE_VERSION))
    }

    @Test
    fun `materially new wording is shown to an install that read the old wording`() {
        assertTrue(shouldShowFirstRunNotice(seenVersion = 1, currentVersion = 2))
    }

    @Test
    fun `a downgrade or a restored backup does not resurrect a notice already read`() {
        assertFalse(shouldShowFirstRunNotice(seenVersion = 3, currentVersion = 2))
    }

    @Test
    fun `Twinlit 0_0_1 shows version 1, above the fresh-install sentinel`() {
        // A version of 0 would be indistinguishable from "seen nothing" and no install would ever see it.
        assertEquals(1, FIRST_RUN_NOTICE_VERSION)
        assertTrue(shouldShowFirstRunNotice(seenVersion = 0))
        assertFalse(shouldShowFirstRunNotice(seenVersion = 1))
    }

    @Test
    fun `the text is TEXTS md section K, and the disclaimer sentence is DISCLAIMER md's`() {
        assertEquals("A note before you start", FirstRunTexts.TITLE)
        assertEquals("A functioning prototype", FirstRunTexts.PROTOTYPE_TITLE)
        assertEquals("Calculated, not measured", FirstRunTexts.CALCULATED_TITLE)
        assertEquals("Alarms are best effort", FirstRunTexts.ALARMS_TITLE)
        assertEquals("Got it", FirstRunTexts.CONFIRM)
        // K-5 names the moon as well as the sun.
        assertTrue(FirstRunTexts.CALCULATED_BODY.contains("the moon's phase, moonrise and moonset"))
        // K-3 is the sentence of the disclaimer, with the name changed.
        val disclaimer = TestFiles.require("DISCLAIMER.md").readText().replace(Regex("\\s+"), " ").replace("**", "")
        val tail = "a functioning prototype, published to show what Stormberry AS builds. It is not a " +
            "certified instrument, not a professional service, and not a substitute for an authoritative source."
        assertTrue(disclaimer.contains("This is $tail"))
        assertEquals("Twinlit is $tail", FirstRunTexts.PROTOTYPE_BODY)
        // K-7 names Twinlit and the manufacturers.
        assertTrue(FirstRunTexts.ALARMS_BODY.contains("Do not rely on Twinlit as your only alarm"))
        assertTrue(FirstRunTexts.ALARMS_BODY.contains("Samsung and Xiaomi"))
    }

    @Test
    fun `it is wired into the root, recorded when closed, and Back closes it but a tap outside does not`() {
        val app = TestFiles.require("${TestFiles.KOTLIN}/ui/App.kt").readText()
        assertTrue(app.contains("shouldShowFirstRunNotice(noticeSeen)"))
        assertTrue(app.contains("settings.firstRunNoticeSeenVersion = FIRST_RUN_NOTICE_VERSION"))
        // R4 (Critic item 6): Light over light bars, golden hour in the Twilight theme, Dark otherwise, so the
        // Moon theme keeps Twilight Dark and FIXED mode with the Sun theme never gives a dark notice over light bars.
        val whole = app.replace(Regex("\\s+"), " ")
        assertTrue(whole.contains("chrome.isLight -> PaletteVariant.LIGHT theme == Theme.TWILIGHT -> PaletteVariant.DUSK else -> PaletteVariant.NIGHT"))
        val notice = TestFiles.require("${TestFiles.KOTLIN}/ui/FirstRunNotice.kt").readText()
        assertTrue(notice.contains("dismissOnClickOutside = false"))
        assertTrue(notice.contains("onDismissRequest = onDismiss"))
        // No edition switch: the same notice in both editions.
        assertFalse(notice.contains("Edition"))
    }
}
