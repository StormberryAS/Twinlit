package no.stormberry.twinlit.theme

import no.stormberry.twinlit.data.Place
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** SPEC 4.5: the user's choice, the shared place, the clock and the phone's own setting give the theme. */
class ThemeResolverTest {

    private val oslo = Place("Oslo, Norway", 59.9139, 10.7522, "Europe/Oslo", fromCatalogue = true)

    // Oslo's solar noon on 2026-06-21 from suncalc.js under Node (see AutoScheduleTest).
    private val noon = Instant.parse("2026-06-21T11:19:58.863Z")

    private fun resolve(choice: Appearance, place: Place?, now: Instant, dark: Boolean) =
        ThemeResolver.resolve(choice, place, now, dark)

    @Test
    fun `Sun, Moon and Twilight are themselves, whatever the place, the hour or the phone`() {
        for (place in listOf(null, oslo)) for (dark in listOf(false, true)) for (now in listOf(noon, noon.plus(Duration.ofHours(12)))) {
            assertEquals(Theme.SUN, resolve(Appearance.SUN, place, now, dark))
            assertEquals(Theme.MOON, resolve(Appearance.MOON, place, now, dark))
            assertEquals(Theme.TWILIGHT, resolve(Appearance.TWILIGHT, place, now, dark))
        }
    }

    @Test
    fun `System follows the phone, dark is Moon and light is Sun`() {
        for (place in listOf(null, oslo)) for (now in listOf(noon, noon.plus(Duration.ofHours(12)))) {
            assertEquals(Theme.MOON, resolve(Appearance.SYSTEM, place, now, dark = true))
            assertEquals(Theme.SUN, resolve(Appearance.SYSTEM, place, now, dark = false))
        }
    }

    @Test
    fun `Auto with no place chosen is System`() {
        for (now in listOf(noon, noon.plus(Duration.ofHours(12)))) {
            assertEquals(Theme.MOON, resolve(Appearance.AUTO, null, now, dark = true))
            assertEquals(Theme.SUN, resolve(Appearance.AUTO, null, now, dark = false))
        }
    }

    @Test
    fun `Auto with a place follows the sun's rhythm there and ignores the phone's setting`() {
        for (dark in listOf(false, true)) {
            assertEquals(Theme.SUN, resolve(Appearance.AUTO, oslo, noon, dark))
            assertEquals(Theme.TWILIGHT, resolve(Appearance.AUTO, oslo, noon.plus(Duration.ofHours(6)), dark))
            assertEquals(Theme.MOON, resolve(Appearance.AUTO, oslo, noon.plus(Duration.ofHours(12)), dark))
        }
    }

    @Test
    fun `the default choice is Auto`() {
        assertEquals(Appearance.AUTO, Appearance.DEFAULT)
    }
}
