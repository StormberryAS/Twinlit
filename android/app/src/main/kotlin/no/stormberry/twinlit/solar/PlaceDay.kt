package no.stormberry.twinlit.solar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which day is "today" for the chosen place (1.1.9). The Sun times screen and the home screen
 * widgets both start from here, so the two always agree: with Sydney chosen on a phone in Oslo
 * late in the evening it is already tomorrow in Sydney, and both show that date. Before 1.1.9 the
 * screen started at `LocalDate.now()`, the phone's date.
 *
 * Pure, so PlaceDayTest runs it on the JVM. In `solar/`, not `ui/`, so the widget code and the
 * solar layer never depend on the user interface.
 */
object PlaceDay {

    /**
     * The zone of a place: its stored IANA id, with the UTC fallback of [resolveZone] for an id
     * this device does not know. With no place at all ([id] null), the phone's own zone.
     */
    fun zoneOf(id: String?, phoneZone: ZoneId = ZoneId.systemDefault()): ZoneId =
        if (id == null) phoneZone else resolveZone(id)

    /** Today's date in [zone] at [now]. */
    fun today(zone: ZoneId, now: Instant = Instant.now()): LocalDate = now.atZone(zone).toLocalDate()

    /**
     * The date to show after the place changes from [oldZone] to [newZone]. A screen that showed
     * today for the old place moves to today for the new one, so it keeps agreeing with the
     * widget; a date picked by hand stays as it is.
     */
    fun afterPlaceChange(shown: LocalDate, oldZone: ZoneId, newZone: ZoneId, now: Instant = Instant.now()): LocalDate =
        if (shown == today(oldZone, now)) today(newZone, now) else shown
}
