package no.stormberry.labs.carousel

/**
 * The cards of the Stormberry Labs carousel, the "Explore the Stormberry Ecosystem" row the
 * Labs websites show above their footer. Pure data with no Android in it, so a plain JUnit test
 * can pin the order, the names and the addresses (LabsAppsTest), and the same file serves every
 * app: SunApp, UsernameGenerator, MoonApp and Twinlit take it (Twinlit with one more card, its own).
 *
 * ### The order
 *
 * Marcos's canonical order (set 2026-10-15, changed 2026-10-30: EclipseTracker right after
 * MoonApp, GDPRLetterBuilder right after UsernameGenerator; changed 2026-11-23: Twinlit's card
 * comes after UsernameGenerator and before GDPRLetterBuilder, D20, D136). A carousel shows Stormberry Labs
 * first, then this list without the app it is in, then that app last, marked as the current one.
 * The websites' carousels follow the same rule. So inside Twinlit its own card is last, marked "This app"
 * (Marcos, 2026-11-23: "Last, as other apps", D144). The place after UsernameGenerator and before
 * GDPRLetterBuilder shows in another app's carousel only when that app adds a Twinlit card (SunApp 2.0.0
 * and MoonApp 2.0.0 have none today) and on the websites when they add Twinlit. Twinlit has no website
 * carousel to be checked against yet (LabsCarouselWebParityTest is not carried over), so LabsAppsTest
 * retypes the order.
 * When the order changes, change [CANONICAL] here and the websites in the same round.
 *
 * ### The addresses
 *
 * Every card opens the app's website in the browser. The Labs card opens
 * https://stormberry.as/labs.html, the address every web carousel uses.
 */
enum class LabsApp(val slug: String, val title: String, val url: String) {
    LABS("labs", "Stormberry Labs", "https://stormberry.as/labs.html"),
    METADATA_SCRUBBER("metadata", "MetadataScrubber", "https://metadata.stormberry.as"),
    CHECK_LINK("check", "CheckLink", "https://check.stormberry.as"),
    USERNAME_GENERATOR("username", "UsernameGenerator", "https://username.stormberry.as"),

    /** Twinlit's own card (TEXTS.md L-18, approved by Marcos 2026-11-22; DECISIONS D20, D136): the address SunApp and MoonApp 2.0.0 already promise. */
    TWINLIT("twinlit", "Twinlit", "https://twinlit.stormberry.as"),
    GDPR_LETTER_BUILDER("gdpr", "GDPRLetterBuilder", "https://gdpr.stormberry.as"),
    SUN_APP("sun", "SunApp", "https://sun.stormberry.as"),
    MOON_APP("moon", "MoonApp", "https://moon.stormberry.as"),
    ECLIPSE_TRACKER("eclipse", "EclipseTracker", "https://eclipse.stormberry.as"),
    SERVICE_SCHEDULER("scheduler", "ServiceScheduler", "https://scheduler.stormberry.as"),
    PHOTO_TIMER("photo", "PhotoTimer", "https://photo.stormberry.as"),
    LUNAR_GARDEN("garden", "LunarGarden", "https://garden.stormberry.as"),
    PLANET_PULSE("planet", "PlanetPulse", "https://planet.stormberry.as"),
    STAR_MAP("star", "StarMap", "https://star.stormberry.as"),
    TIDE_TRACKER("tide", "TideTracker", "https://tide.stormberry.as"),
    ASTRO_QUIZ("astro", "AstroQuiz", "https://astro.stormberry.as"),
    CHEAP_BEER("beer", "CheapBeer", "https://beer.stormberry.as"),
    SOLAR_FLARE("flare", "SolarFlare", "https://flare.stormberry.as"),
    METEOR_WATCH("meteor", "MeteorWatch", "https://meteor.stormberry.as"),
    NIGHT_MODE_SCHEDULER("nightmode", "NightModeScheduler", "https://nightmode.stormberry.as"),
    WEATHER_WHISPER("weather", "WeatherWhisper", "https://weather.stormberry.as"),
    ECO_COMPASS("eco", "EcoCompass", "https://eco.stormberry.as"),
    ;

    companion object {
        /** The apps in the canonical order, Stormberry Labs not included. */
        val CANONICAL: List<LabsApp> = entries.filter { it != LABS }

        /**
         * The cards of one set, as the carousel in [current] shows them: Stormberry Labs first,
         * the canonical order without [current], then [current] last. [current] is an app that
         * has a carousel, so never [LABS].
         */
        fun cardsFor(current: LabsApp): List<LabsApp> {
            require(current != LABS) { "the Labs page is not an app with a carousel" }
            return listOf(LABS) + CANONICAL.filter { it != current } + current
        }
    }
}

/** The words of the carousel, the web's word for word (LabsCarouselWebParityTest pins the first two). */
object LabsCarouselText {
    /** The region's heading: the web's `<h2 class="switcher-title">`. */
    const val TITLE = "Explore the Stormberry Ecosystem"

    /** The pause tick box: the web's `<label class="switcher-pause-label">`. */
    const val PAUSE = "Pause the animation"

    /** What a screen reader says after the current app's name, as the web's aria-current does. */
    const val CURRENT = "This app"

    /** TalkBack's "Double-tap to ..." hint on every card. */
    const val OPEN_ACTION = "open the website"

    /** Shown when no app on the device can open a web address. */
    const val NO_BROWSER = "No app on this device can open that link."
}
