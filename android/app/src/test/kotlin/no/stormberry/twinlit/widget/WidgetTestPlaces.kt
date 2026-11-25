package no.stormberry.twinlit.widget

import no.stormberry.twinlit.data.Place

/**
 * The ONLY places the widget tests, previews and examples use (1.1.9, privacy rule): catalogue
 * cities with their catalogue coordinates (`data/cities.tsv`), two well-known island points that
 * the catalogue lacks (Kiritimati and Lord Howe Island, for their unusual zones), and synthetic
 * points at the poles. PlacePrivacyTest allows no other coordinate in the files this work touches.
 */
object WidgetTestPlaces {

    val OSLO = Place("Oslo, Norway", 59.9127, 10.7461, "Europe/Oslo", fromCatalogue = true)
    val LONGYEARBYEN = Place("Longyearbyen, Svalbard and Jan Mayen", 78.2233, 15.6469, "Arctic/Longyearbyen", fromCatalogue = true)
    val TROMSO = Place("Tromsø, Norway", 69.6489, 18.9551, "Europe/Oslo", fromCatalogue = true)
    val SYDNEY = Place("Sydney, Australia", -33.8678, 151.2073, "Australia/Sydney", fromCatalogue = true)
    val APIA = Place("Apia, Samoa", -13.8333, -171.7667, "Pacific/Apia", fromCatalogue = true)
    val SUVA = Place("Suva, Fiji", -18.1368, 178.4253, "Pacific/Fiji", fromCatalogue = true)
    val ST_JOHNS = Place("St. John's, Canada", 47.5649, -52.7093, "America/St_Johns", fromCatalogue = true)

    /** Not in the catalogue: a UTC+14 zone (Line Islands). */
    val KIRITIMATI = Place("Kiritimati, Kiribati", 1.8721, -157.4278, "Pacific/Kiritimati", fromCatalogue = false)

    /** Not in the catalogue: a zone with a 30 minute daylight saving shift. */
    val LORD_HOWE = Place("Lord Howe Island, Australia", -31.5553, 159.0821, "Australia/Lord_Howe", fromCatalogue = false)

    /** Synthetic points near and at the poles, zone UTC. */
    val NEAR_NORTH_POLE = Place("89.99 N, 0 E", 89.99, 0.0, "UTC", fromCatalogue = false)
    val NEAR_SOUTH_POLE = Place("89.99 S, 0 E", -89.99, 0.0, "UTC", fromCatalogue = false)
    val NORTH_POLE = Place("90 N", 90.0, 0.0, "UTC", fromCatalogue = false)

    /** Far from solar time: 89.99 N, 170 W with an Oslo clock (a known approximation, pinned). */
    val POLE_FAR_ZONE = Place("89.99 N, 170 W", 89.99, -170.0, "Europe/Oslo", fromCatalogue = false)

    val ALL: List<Place> = listOf(
        OSLO, LONGYEARBYEN, TROMSO, SYDNEY, APIA, SUVA, ST_JOHNS, KIRITIMATI, LORD_HOWE,
        NEAR_NORTH_POLE, NEAR_SOUTH_POLE, NORTH_POLE, POLE_FAR_ZONE,
    )
}
