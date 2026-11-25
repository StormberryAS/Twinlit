package no.stormberry.twinlit

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import no.stormberry.twinlit.alarm.AlarmScheduling
import no.stormberry.twinlit.alarm.AlarmService
import no.stormberry.twinlit.alarm.AndroidAlarmSink
import no.stormberry.twinlit.alarm.RingActivity
import no.stormberry.twinlit.ui.TwinlitApp
import no.stormberry.twinlit.ui.applySystemBars
import no.stormberry.twinlit.widget.ShowToday
import no.stormberry.twinlit.widget.WidgetRefresh

/**
 * The one activity. The Compose root does its own layout against the system bars (the top bar
 * takes the status bar inset, the bottom bar the navigation bar inset, the tab's body the
 * keyboard's), so nothing here pads the content.
 *
 * Since milestone M5 it also runs the alarm side of SPEC 3.5: the re-plan on every resume (A-2), the
 * ring screen while an alarm rings (A-1) and the `SHOW_ALARMS` intent (the next-alarm chip and the
 * AlarmClock trampoline), which opens the Alarms tab.
 *
 * Since milestone M6 it also serves the home screen widgets (SPEC 3.2, 8): the tap on a sun widget
 * sends `SHOW_SUN_TODAY` (the Sun tab on the place's today) and on a moon widget `SHOW_MOON_TODAY`
 * (the Moon tab on the place's today); and every resume redraws both widget families, so a widget
 * never shows a place or a day older than the app.
 */
class MainActivity : ComponentActivity() {

    /** How many times this activity has resumed; the root re-reads the clock on each. */
    private val resumes = mutableIntStateOf(0)

    /**
     * How many times something asked for the Alarms tab: the next-alarm chip and the AlarmClock
     * trampoline send [AndroidAlarmSink.ACTION_SHOW_ALARMS]. Counted at a fresh launch and in
     * [onNewIntent], and a relaunch from Recents (which replays the old intent) is not counted.
     */
    private val alarmsRequests = mutableIntStateOf(0)

    /**
     * How many times a widget asked for the Sun tab on today, and for the Moon tab on today. The
     * root resets a tab's date only when the count CHANGES (never when the tab is composed again),
     * counted at a fresh launch and in [onNewIntent] under the rules of [alarmsRequests].
     */
    private val sunTodayRequests = mutableIntStateOf(0)
    private val moonTodayRequests = mutableIntStateOf(0)

    /** True between onResume and onPause: the Auto loop of the root runs only then. */
    private val inForeground = mutableStateOf(false)

    /** The lightness the system bars were last styled for, so a recomposition does not restyle them. */
    private var barsLight: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark bars until the first composition knows the theme (the window background is the
        // night colour in every theme, D45).
        applySystemBars(isLight = false)
        barsLight = false
        super.onCreate(savedInstanceState)
        // A cold start from the next-alarm chip opens on Alarms. Consumed, so a recreation of the
        // process does not read it again; a saved state means the user's own tab is restored.
        if (savedInstanceState == null) countRequest(intent?.action)
        if (isRequest(intent?.action)) setIntent(Intent(Intent.ACTION_MAIN))
        // S10 (SunApp 2.0.0). With no soft-input mode set, and a Compose root that is not a scroll
        // view, Android PANS the whole window when the keyboard opens: the screen slides up under
        // the status bar and its first lines sit behind the clock. ADJUST_RESIZE hands the keyboard
        // to the app as an inset instead, and the root's imePadding shortens the tab's body by it.
        // The manifest says the same; the mode is also set here, after the window exists and
        // before the content.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent {
            TwinlitApp(
                foreground = inForeground.value,
                resumeCount = resumes.intValue,
                alarmsRequests = alarmsRequests.intValue,
                sunTodayRequests = sunTodayRequests.intValue,
                moonTodayRequests = moonTodayRequests.intValue,
                onBarsLight = { light ->
                    if (barsLight != light) {
                        barsLight = light
                        applySystemBars(light)
                    }
                },
                modifier = Modifier,
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { AlarmService.ringing.value }.collect { showRingScreenIfRinging() }
            }
        }
    }

    /**
     * R4-F item 1. The manifest lists orientation, screenSize, density, fontScale and uiMode in
     * `configChanges`, so a rotation, a font size change or a display size change does not recreate
     * this activity. The platform still resets the system bars' icon appearance on such a change
     * (the Sun theme's dark clock turned white on the cream bar, 1.06:1, until the app restarted),
     * while [barsLight] still says the bars are styled, so the Compose callback never restyles
     * them (the theme's lightness did not change). Style them again here, after super, from the
     * cached lightness.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars(barsLight ?: false)
        // The platform lays the window out again after this call, so style once more after it.
        window.decorView.post { applySystemBars(barsLight ?: false) }
    }

    /**
     * The next-alarm chip while Twinlit is running (CLEAR_TOP brings this instance forward): the
     * Alarms tab. Consumed once; a relaunch from Recents is not a tap.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isRequest(intent.action)) {
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) countRequest(intent.action)
            setIntent(Intent(Intent.ACTION_MAIN))
        }
    }

    /** The three actions that ask for a tab: the next-alarm chip and the two widget taps. */
    private fun isRequest(action: String?): Boolean =
        action == AndroidAlarmSink.ACTION_SHOW_ALARMS || action == ShowToday.ACTION_SUN || action == ShowToday.ACTION_MOON

    private fun countRequest(action: String?) {
        when (action) {
            AndroidAlarmSink.ACTION_SHOW_ALARMS -> alarmsRequests.intValue++
            ShowToday.ACTION_SUN -> sunTodayRequests.intValue++
            ShowToday.ACTION_MOON -> moonTodayRequests.intValue++
        }
    }

    override fun onResume() {
        super.onResume()
        inForeground.value = true
        resumes.intValue++
        replanInBackground()
        // The widgets agree with the app on every resume (SPEC 8.3), whatever the alarms did.
        WidgetRefresh.refreshAllAsync(applicationContext)
    }

    /**
     * 1.1.4, A-2: re-plan whenever the app comes to the front. On Android 12 and 12L, switching
     * exact alarms off cancels every exact alarm with no broadcast to say so, and nothing re-armed
     * them before the next reboot, clock change or rule edit. Off the main thread, as
     * SystemEventReceiver does: the work is small, but it is a JSON parse and an astronomical
     * evaluation per rule. Runs in both editions, which are the same code.
     */
    private fun replanInBackground() {
        val appContext = applicationContext
        Thread({
            try {
                AlarmScheduling.replan(appContext)
            } catch (e: RuntimeException) {
                Log.e(TAG, "re-planning on resume failed", e)
            }
        }, "twinlit-resume-replan").start()
    }

    /**
     * 1.1.4, A-1: while this screen is in front, an alarm that rings brings the ring screen: at once
     * on resume when one already rings, and whenever one starts while it is open. With
     * notifications off or the ring channel turned down an alarm rings with nothing on screen to
     * stop it, so opening Twinlit has to be a way to stop it.
     */
    private fun showRingScreenIfRinging() {
        if (AlarmService.current() == null || isFinishing || isDestroyed) return
        try {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not open the ring screen", e)
        }
    }

    private companion object {
        const val TAG = "TwinlitMain"
    }

    override fun onPause() {
        inForeground.value = false
        super.onPause()
    }
}
