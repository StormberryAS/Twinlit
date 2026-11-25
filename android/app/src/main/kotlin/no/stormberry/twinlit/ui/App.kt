package no.stormberry.twinlit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.stormberry.twinlit.cities.CityAssets
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.Settings
import no.stormberry.twinlit.solar.PlaceDay
import no.stormberry.twinlit.theme.Appearance
import no.stormberry.twinlit.theme.AutoSchedule
import no.stormberry.twinlit.theme.Theme
import no.stormberry.twinlit.theme.ThemeResolver
import no.stormberry.twinlit.alarm.AlarmIntentHandoff
import no.stormberry.twinlit.alarm.AlarmScheduling
import no.stormberry.twinlit.data.SharedPreferencesRuleFile
import no.stormberry.twinlit.ui.alarms.AlarmsRoot
import no.stormberry.twinlit.ui.moon.MoonTimesScreen
import no.stormberry.twinlit.ui.sun.SunTimesScreen
import no.stormberry.twinlit.ui.theme.PaletteVariant
import no.stormberry.twinlit.ui.theme.Palettes
import no.stormberry.twinlit.ui.theme.Tab
import no.stormberry.twinlit.ui.theme.TwinlitChromeTheme
import no.stormberry.twinlit.ui.theme.TwinlitTheme
import no.stormberry.twinlit.widget.WidgetRefresh
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The app's composable root (SPEC 3): the slim top bar, the selected tab's body, and the bottom
 * bar. Three things live here because they belong to the whole app and to no one tab:
 *
 *  - **The shared place** (SPEC 3.3, P1): one nullable [Place], read from and written to
 *    [Settings] here and handed to every tab. Changing it from any tab re-evaluates the theme
 *    (Auto needs it). There is no default place: with none chosen Auto behaves as System.
 *  - **The theme** (SPEC 4): the user's [Appearance] choice, resolved by [ThemeResolver] against
 *    the place, the clock and the phone's own dark setting. Re-resolved at the first composition,
 *    when the choice, the place or the phone's setting changes, on every resume, and, while the app
 *    is in the foreground with Auto and a place, at each boundary of the schedule (and at least
 *    once a minute, which covers clock and zone changes while it is open).
 *  - **The city catalogue**, parsed once off the main thread and shared by every picker.
 *
 * @param foreground whether the activity is resumed; the Auto loop only runs then
 * @param resumeCount increases on every resume, which re-reads the clock
 * @param alarmsRequests how many times something asked for the Alarms tab (the next-alarm chip, the
 *   AlarmClock `SHOW_ALARMS` and `SET_ALARM` trampoline, SPEC 3.2); each new count opens it and lets
 *   the Alarms tab read the hand-off the trampoline left
 * @param sunTodayRequests how many times a Sun widget was tapped (`SHOW_SUN_TODAY`, SPEC 3.2): each new
 *   count opens the Sun tab on the place's today
 * @param moonTodayRequests the same for a Moon widget: the Moon tab on the place's today
 * @param onBarsLight tells the activity whether the chrome is light, for the system bar icons
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TwinlitApp(
    foreground: Boolean,
    resumeCount: Int,
    alarmsRequests: Int,
    sunTodayRequests: Int,
    moonTodayRequests: Int,
    onBarsLight: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    // Cold start opens Sun (as SunApp opened on Sun times), except for a pending AlarmClock
    // hand-off and the next-alarm chip, which open Alarms (SPEC 3.2). Only peeked at here, to choose
    // the tab; the Alarms tab is what consumes the hand-off, so the seed cannot be lost.
    // A cold start from the Moon widget opens Moon (SPEC 3.2).
    var tab by rememberSaveable {
        mutableStateOf(
            when {
                alarmsRequests > 0 || AlarmIntentHandoff.pending(context) -> Tab.ALARMS
                moonTodayRequests > 0 -> Tab.MOON
                else -> Tab.SUN
            },
        )
    }
    LaunchedEffect(alarmsRequests) { if (alarmsRequests > 0) tab = Tab.ALARMS }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    // The first-run notice (SPEC 9): shown while the version this install has seen is below this
    // build's. Recorded when it is closed, so Back and "Got it" both count as read.
    var noticeSeen by remember { mutableIntStateOf(settings.firstRunNoticeSeenVersion) }

    var place by remember { mutableStateOf(settings.place) }
    var mode by remember { mutableStateOf(settings.mode) }
    var appearance by remember { mutableStateOf(settings.appearance) }

    // The Sun tab's date (SPEC 3.2, D10): held here, not in the tab, so it survives a visit to
    // another tab and a place chosen on another tab can move it. An epoch day, so shifting a day
    // is an addition. Not persisted: the place's today, whenever the app starts.
    var sunEpochDay by remember {
        mutableLongStateOf(PlaceDay.today(PlaceDay.zoneOf(place?.zoneId)).toEpochDay())
    }

    // The Moon tab's date, held here for the same reasons (SPEC 3.2, 3.3).
    var moonEpochDay by remember {
        mutableLongStateOf(PlaceDay.today(PlaceDay.zoneOf(place?.zoneId)).toEpochDay())
    }

    // A widget tap (SPEC 3.2, 8): the tab on the place's today. Keyed on the COUNT, so the date is
    // reset when a request arrives and never when the tab is composed again (a date picked by hand
    // survives a visit to another tab). The first run of an effect sees the count the activity was
    // created with: 0 for a normal start, 1 for a cold start from the widget, whose date is already
    // today, so the reset is harmless there.
    LaunchedEffect(sunTodayRequests) {
        if (sunTodayRequests > 0) {
            tab = Tab.SUN
            sunEpochDay = PlaceDay.today(PlaceDay.zoneOf(place?.zoneId)).toEpochDay()
        }
    }
    LaunchedEffect(moonTodayRequests) {
        if (moonTodayRequests > 0) {
            tab = Tab.MOON
            moonEpochDay = PlaceDay.today(PlaceDay.zoneOf(place?.zoneId)).toEpochDay()
        }
    }

    var catalogue by remember { mutableStateOf<CatalogueState>(CatalogueState.Loading) }
    LaunchedEffect(Unit) {
        catalogue = withContext(Dispatchers.IO) {
            try {
                CatalogueState.Ready(CityAssets.load(context))
            } catch (e: IOException) {
                CatalogueState.Failed(
                    "The city catalogue could not be read from the app package (${e.message}). " +
                        "Coordinates still work.",
                )
            } catch (e: IllegalArgumentException) {
                CatalogueState.Failed(
                    "The city catalogue in this build is malformed (${e.message}). " +
                        "Coordinates still work.",
                )
            }
        }
    }

    // The theme in effect. `now` is re-read on resume and by the Auto loop below.
    val systemDark = isSystemInDarkTheme()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(resumeCount) { now = Instant.now() }
    LaunchedEffect(appearance, place?.latDeg, place?.lonDeg, foreground) {
        now = Instant.now()
        val p = place
        if (foreground && appearance == Appearance.AUTO && p != null) {
            while (true) {
                val at = Instant.now()
                val next = AutoSchedule.nextChange(at, p.latDeg, p.lonDeg)
                val wait = minOf(Duration.between(at, next).toMillis() + 1_000L, 60_000L)
                delay(wait.coerceAtLeast(1_000L))
                now = Instant.now()
            }
        }
    }
    val theme: Theme = ThemeResolver.resolve(appearance, place, now, systemDark)

    val chrome = Palettes.chrome(theme, tab)
    SideEffect { onBarsLight(chrome.isLight) }

    // A new place (SPEC 3.3): a tab that showed the old place's today moves to the new place's
    // today, so it keeps agreeing with the widgets; a date picked by hand stays.
    fun changePlace(next: Place?) {
        val oldZone = PlaceDay.zoneOf(place?.zoneId)
        val newZone = PlaceDay.zoneOf(next?.zoneId)
        sunEpochDay = PlaceDay.afterPlaceChange(LocalDate.ofEpochDay(sunEpochDay), oldZone, newZone).toEpochDay()
        moonEpochDay = PlaceDay.afterPlaceChange(LocalDate.ofEpochDay(moonEpochDay), oldZone, newZone).toEpochDay()
        place = next
        settings.place = next
        // The widgets follow the place at once (SPEC 8.3); off the main thread, in a catch.
        WidgetRefresh.refreshAllAsync(context)
    }

    val tabState = rememberSaveableStateHolder()
    // The bottom bar gives way to the keyboard, so a field near the bottom is never covered (D31).
    val imeVisible = WindowInsets.isImeVisible

    // System Back on the Moon or Alarms tab returns to Sun, the start tab, as the Material bottom
    // navigation pattern asks, instead of leaving the app. Registered before the tab bodies, so the
    // alarm editor's own handler (registered later, when its tab is composed) takes Back first.
    BackHandler(enabled = tab != Tab.SUN) { tab = Tab.SUN }

    // The non-text focus target of LocalFocusSink (PlaceFocus.kt): a picker moves the focus here once a
    // place is applied, so the search field does not get it back and reopen the keyboard on API 24.
    val focusSink = remember { FocusRequester() }

    // The side insets: the system bars AND the display cutout. enableEdgeToEdge puts the window in
    // short-edges cutout mode, so in landscape on a notched phone the title, the cards and the bottom
    // bar ran under the cutout (inherited from SunApp; fix round 2).
    val sideInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

    TwinlitChromeTheme(chrome) {
        CompositionLocalProvider(LocalFocusSink provides focusSink) {
            // The root keeps clear of the side system bars as well (SunApp and MoonApp padded the whole
            // root with the system bars): with three-button navigation in landscape the navigation bar
            // sits at the side and covered the Appearance button and the cards' right edge. The
            // background is applied first, so the chrome colour still runs under the transparent bars.
            // Top and bottom stay with the top bar and the Material navigation bar.
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(chrome.Background)
                    .windowInsetsPadding(sideInsets)
                    .consumeWindowInsets(sideInsets)
                    .focusRequester(focusSink)
                    .focusable(),
            ) {
                TopBar(chrome = chrome, onAppearance = { showAppearance = true })

                // The tab's own screen, kept with its scroll state while the user moves between tabs.
                // Clipped: the sky glow of a tab is drawn from its own top edge outwards and, unclipped,
                // painted over the top bar and the status bar (fix round 2).
                Box(modifier = Modifier.weight(1f).clipToBounds().imePadding()) {
                    tabState.SaveableStateProvider(tab) {
                        when (tab) {
                            Tab.SUN -> TwinlitTheme(Palettes.sun(Palettes.variantFor(theme, Tab.SUN))) {
                                SunTimesScreen(
                                    catalogue = catalogue,
                                    place = place,
                                    mode = mode,
                                    onModeChange = { mode = it; settings.mode = it },
                                    onPlaceChange = ::changePlace,
                                    dateEpochDay = sunEpochDay,
                                    onDateChange = { sunEpochDay = it },
                                )
                            }

                            Tab.MOON -> {
                                TwinlitTheme(Palettes.moon(Palettes.variantFor(theme, Tab.MOON))) {
                                    MoonTimesScreen(
                                        catalogue = catalogue,
                                        place = place,
                                        mode = mode,
                                        onModeChange = { mode = it; settings.mode = it },
                                        onPlaceChange = ::changePlace,
                                        dateEpochDay = moonEpochDay,
                                        onDateChange = { moonEpochDay = it },
                                    )
                                }
                            }

                            Tab.ALARMS -> TwinlitTheme(
                                Palettes.twilight(Palettes.variantFor(theme, Tab.ALARMS)),
                            ) {
                                val ruleFile = remember { SharedPreferencesRuleFile(context) }
                                AlarmsRoot(
                                    ruleFile = ruleFile,
                                    // The shared place is offered as the starting place of a new alarm,
                                    // never imposed (SPEC 3.3): each alarm keeps the place it was made for.
                                    defaultPlace = place,
                                    catalogue = catalogue,
                                    // The one wire between the alarm list and AlarmManager: the list
                                    // writes its rules, then says so; the re-plan reads them back.
                                    onRulesChanged = { AlarmScheduling.replan(context) },
                                    handoffRequests = alarmsRequests,
                                )
                            }
                        }
                    }
                }

                if (!imeVisible) {
                    BottomBar(selected = tab, chrome = chrome, onSelect = { tab = it })
                }
            }

            if (shouldShowFirstRunNotice(noticeSeen)) {
                FirstRunNotice(
                    tokens = Palettes.twilight(
                        when {
                            chrome.isLight -> PaletteVariant.LIGHT
                            theme == Theme.TWILIGHT -> PaletteVariant.DUSK
                            else -> PaletteVariant.NIGHT
                        },
                    ),
                    onDismiss = {
                        settings.firstRunNoticeSeenVersion = FIRST_RUN_NOTICE_VERSION
                        noticeSeen = FIRST_RUN_NOTICE_VERSION
                    },
                )
            }

            if (showAppearance) {
                AppearanceSheet(
                    chrome = chrome,
                    selected = appearance,
                    hasPlace = place != null,
                    onSelect = {
                        appearance = it
                        settings.appearance = it
                    },
                    onDismiss = { showAppearance = false },
                )
            }
        }
    }
}
