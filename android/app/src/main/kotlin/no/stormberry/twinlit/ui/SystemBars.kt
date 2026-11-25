package no.stormberry.twinlit.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge to edge with transparent bars whose ICONS follow the chrome palette (SPEC 4.8): dark icons
 * when the chrome is light (the Sun theme), light icons when it is dark. `enableEdgeToEdge()` with
 * no style would pick the icon colour from the phone's own light or dark mode, which is not the
 * app's theme: a phone in light mode drew a dark clock on SunApp's dark sky (SunApp 1.1.8).
 *
 * Called once before the content, and again whenever the chrome's lightness changes. The screens
 * keep their content clear of the bars with window insets (MainActivity, RingActivity).
 */
fun ComponentActivity.applySystemBars(isLight: Boolean) {
    enableEdgeToEdge(
        statusBarStyle = barStyle(isLight),
        navigationBarStyle = barStyle(isLight),
    )
}

/**
 * The scrim that stands behind the system bars on API 24 and 25, where dark navigation icons do not
 * exist (`SystemBarStyle.light` paints the bar with its `darkScrim` there, API below 26). Fully
 * transparent, the phone's white Back and Home buttons sat on the white bottom bar of the Sun theme
 * at 1.37:1 (fix round 1). This is androidx's own default (50 per cent of `#1B1B1B`): light buttons
 * are readable on it and the bar still looks like part of the theme. API 26 and later ignore it.
 */
const val NAV_BAR_DARK_SCRIM: Int = 0x801B1B1B.toInt()

private fun barStyle(isLight: Boolean): SystemBarStyle =
    if (isLight) {
        SystemBarStyle.light(Color.TRANSPARENT, NAV_BAR_DARK_SCRIM)
    } else {
        SystemBarStyle.dark(Color.TRANSPARENT)
    }

/**
 * The always-dark form for the ring screen (SunApp's `enableDarkEdgeToEdge`, milestone M5): a
 * ringing alarm is dark in every theme, so its bars are transparent with light icons.
 */
fun ComponentActivity.enableDarkEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}
