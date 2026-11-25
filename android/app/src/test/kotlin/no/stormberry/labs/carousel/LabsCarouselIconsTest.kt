package no.stormberry.labs.carousel

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every card has its icon (SunApp's test of the same name), and every drawable the lookup names
 * exists in the repository, so a card never shows an empty gap where its icon should be. The SVG
 * hash part of SunApp's test is not carried over: Twinlit has no switcher-icons folder (SPEC 16).
 */
class LabsCarouselIconsTest {

    @Test fun everyCardHasAnIcon() {
        for (app in LabsApp.entries) {
            assertNotEquals(app.slug, 0, labsSwitcherIcon(app.slug))
        }
        assertEquals(0, labsSwitcherIcon("no-such-app"))
    }

    @Test fun everyCardsDrawableIsInTheRepository() {
        for (app in LabsApp.entries) {
            val file = TestFiles.require("${TestFiles.RES}/drawable/labs_switcher_${app.slug}.xml")
            assertTrue(file.name, file.length() > 0)
        }
        assertEquals(22, LabsApp.entries.size)
    }

    @Test fun twinlitsCardIconIsTheLauncherIconsTile() {
        val card = TestFiles.require("${TestFiles.RES}/drawable/labs_switcher_twinlit.xml").readText()
        // The b1 icon: the sky gradient, the gold sun with its gradient and rays, the silver moon.
        assertTrue(card.contains("#FF080C18"))
        assertTrue(card.contains("#FFFFD700"))
        assertTrue(card.contains("#FFC8D8F0"))
        assertTrue(card.contains("""android:width="32dp""""))
    }
}
