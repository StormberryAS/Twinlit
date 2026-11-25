package no.stormberry.labs.carousel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The order and the addresses of the carousel cards. */
class LabsAppsTest {

    /** Marcos's canonical order, retyped on purpose: changing it must be a deliberate act in two places. */
    private val canonical = listOf(
        "MetadataScrubber", "CheckLink", "UsernameGenerator", "Twinlit", "GDPRLetterBuilder",
        "SunApp", "MoonApp", "EclipseTracker", "ServiceScheduler", "PhotoTimer", "LunarGarden",
        "PlanetPulse", "StarMap", "TideTracker", "AstroQuiz", "CheapBeer", "SolarFlare",
        "MeteorWatch", "NightModeScheduler", "WeatherWhisper", "EcoCompass",
    )

    @Test fun theAppsAreInTheCanonicalOrder() {
        assertEquals(canonical, LabsApp.CANONICAL.map { it.title })
    }

    @Test fun everyCarouselStartsWithLabsAndEndsWithItsOwnApp() {
        for (current in LabsApp.CANONICAL) {
            val cards = LabsApp.cardsFor(current)
            assertEquals(22, cards.size)
            assertEquals(LabsApp.LABS, cards.first())
            assertEquals(current, cards.last())
            assertEquals("each app once", cards.toSet().size, cards.size)
            // Between the two ends: the canonical order with the own app taken out.
            assertEquals(LabsApp.CANONICAL - current, cards.drop(1).dropLast(1))
        }
    }

    @Test fun twinlitsCarouselShowsItsOwnCardLastAndTheOtherTwoSunAndMoonCardsBeforeIt() {
        val cards = LabsApp.cardsFor(LabsApp.TWINLIT)
        assertEquals(LabsApp.TWINLIT, cards.last())
        assertTrue(LabsApp.SUN_APP in cards && LabsApp.MOON_APP in cards)
        assertEquals("Twinlit", LabsApp.TWINLIT.title)
        assertEquals("twinlit", LabsApp.TWINLIT.slug)
    }

    @Test fun twinlitSitsAfterUsernameGeneratorAndBeforeGdprLetterBuilder() {
        // Marcos, 2026-11-23: "Twinlit goes after usernamegenerator and before gdprletterbuilder" (D20, D136).
        val order = LabsApp.CANONICAL
        val at = order.indexOf(LabsApp.TWINLIT)
        assertEquals(LabsApp.USERNAME_GENERATOR, order[at - 1])
        assertEquals(LabsApp.GDPR_LETTER_BUILDER, order[at + 1])
        // In another app's carousel the card keeps that place; in Twinlit's own it is the last card.
        val sun = LabsApp.cardsFor(LabsApp.SUN_APP)
        assertEquals(LabsApp.USERNAME_GENERATOR, sun[sun.indexOf(LabsApp.TWINLIT) - 1])
        assertEquals(LabsApp.GDPR_LETTER_BUILDER, sun[sun.indexOf(LabsApp.TWINLIT) + 1])
    }

    @Test fun theLabsPageHasNoCarouselOfItsOwn() {
        val failed = runCatching { LabsApp.cardsFor(LabsApp.LABS) }.isFailure
        assertTrue(failed)
    }

    @Test fun everyCardOpensItsWebsite() {
        assertEquals("https://stormberry.as/labs.html", LabsApp.LABS.url)
        for (app in LabsApp.CANONICAL) {
            assertTrue(app.url, Regex("""https://[a-z]+\.stormberry\.as""").matches(app.url))
        }
        assertEquals("https://username.stormberry.as", LabsApp.USERNAME_GENERATOR.url)
        assertEquals("https://sun.stormberry.as", LabsApp.SUN_APP.url)
        assertEquals("https://moon.stormberry.as", LabsApp.MOON_APP.url)
        // Twinlit's own card opens the address SunApp 2.0.0 and MoonApp 2.0.0 already promise (D20, D44, D136).
        assertEquals("https://twinlit.stormberry.as", LabsApp.TWINLIT.url)
        assertEquals("addresses are unique", LabsApp.entries.size, LabsApp.entries.map { it.url }.toSet().size)
    }

    @Test fun slugsAreResourceNames() {
        for (app in LabsApp.entries) {
            assertTrue(app.slug, Regex("[a-z0-9_]+").matches(app.slug))
        }
    }
}
