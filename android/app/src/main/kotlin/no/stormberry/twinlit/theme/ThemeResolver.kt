package no.stormberry.twinlit.theme

import no.stormberry.twinlit.data.Place
import java.time.Instant

/**
 * From the user's [Appearance] choice to the [Theme] in effect (SPEC 4.5). Pure.
 *
 *  - Sun, Moon and Twilight are themselves;
 *  - System: the phone dark means Moon, the phone light means Sun (DECISIONS D6);
 *  - Auto: the [AutoSchedule] at the chosen place; with no place chosen yet it behaves as System.
 */
object ThemeResolver {

    fun resolve(choice: Appearance, place: Place?, now: Instant, systemDark: Boolean): Theme = when (choice) {
        Appearance.SUN -> Theme.SUN
        Appearance.MOON -> Theme.MOON
        Appearance.TWILIGHT -> Theme.TWILIGHT
        Appearance.SYSTEM -> system(systemDark)
        Appearance.AUTO ->
            if (place == null) system(systemDark) else AutoSchedule.phaseAt(now, place.latDeg, place.lonDeg)
    }

    private fun system(systemDark: Boolean): Theme = if (systemDark) Theme.MOON else Theme.SUN
}
