package no.stormberry.twinlit.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import no.stormberry.twinlit.data.Settings
import no.stormberry.twinlit.widget.moon.MoonWidgets
import no.stormberry.twinlit.widget.sun.SunWidgets
import no.stormberry.twinlit.widget.sun.WidgetSchedule as SunSchedule
import java.time.Instant
import java.time.ZoneId

/**
 * The one entry point that redraws the home screen widgets of BOTH families and arms the alarms
 * (SPEC 8.3). SunApp had it in `SunWidgets` and MoonApp in `MoonWidgets`; Twinlit has one lock, one
 * short thread inside a catch, one refresh alarm and one event receiver for the six widgets.
 *
 * ### Who calls it
 *
 *  - each provider's `onUpdate` and `onAppWidgetOptionsChanged` ([refreshInBackground], which keeps the
 *    broadcast alive until the thread is done);
 *  - [WidgetEventReceiver], for the refresh and handover alarms and the four system broadcasts;
 *  - `MainActivity.onResume` and every change of the shared place ([refreshAllAsync]).
 *
 * ### The alarms, and why they are shared
 *
 *  - **Refresh** (`RTC`, never wakes the phone, `setWindow` 10 minutes, request code 0x5B10): at the
 *    earlier of the Sun family's [SunSchedule.plan] refresh time (only when a sun widget is placed)
 *    and the Moon family's next time ([no.stormberry.twinlit.widget.moon.WidgetSchedule.nextRefresh],
 *    the place's next midnight plus one second, at most three hours; only when a moon widget is
 *    placed). Both families draw on every refresh, so the earlier of two times serves both.
 *  - **Handover** (`RTC_WAKEUP`, `setWindow` 10 minutes, request code 0x5B12, action
 *    [ACTION_HANDOVER]): the Sun family's, exactly as SunApp, only while its countdown ticks.
 *
 * Neither is an exact alarm or an alarm clock, so neither needs a permission, and nothing here
 * imports the alarm package (WidgetIsolationTest). Both are sent to [WidgetEventReceiver] by
 * explicit component, so `Intent.filterEquals` can never match one of the user's alarms.
 *
 * ### Never throws into a receiver
 *
 * Every entry point runs [refreshAll] on a short-lived thread inside a catch (the 1.0.4 rule). When
 * it fails, the launcher keeps the previous widget on screen.
 */
object WidgetRefresh {

    private const val TAG = "TwinlitWidgets"

    /** The refresh alarm. Sent by explicit component only, so it needs no intent filter. */
    const val ACTION_REFRESH = "no.stormberry.twinlit.widget.REFRESH"

    /** The Sun family's handover alarm, a separate PendingIntent (its own action and request code). */
    const val ACTION_HANDOVER = "no.stormberry.twinlit.widget.HANDOVER"

    const val REQ_REFRESH = 0x5B10
    const val REQ_HANDOVER = 0x5B12

    /** One refresh at a time: a reboot can deliver six provider updates and a resume together. */
    private val lock = Any()

    // ------------------------------------------------------------------ entry points

    /** From a receiver: [refreshAll] off the main thread, keeping the broadcast alive until done. */
    fun refreshInBackground(receiver: BroadcastReceiver, context: Context) {
        val pending = receiver.goAsync()
        val app = context.applicationContext
        Thread({
            try {
                refreshAll(app)
            } catch (e: RuntimeException) {
                Log.e(TAG, "widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }, "twinlit-widget").start()
    }

    /** From the app (a new place, the app brought to the front): [refreshAll] on a thread. */
    fun refreshAllAsync(context: Context) {
        val app = context.applicationContext
        Thread({
            try {
                refreshAll(app)
            } catch (e: RuntimeException) {
                Log.e(TAG, "widget refresh failed", e)
            }
        }, "twinlit-widget").start()
    }

    /**
     * Redraws every placed widget of both families and arms the alarms; with no widget placed,
     * cancels both alarms and disables the receiver. Reads preferences, so never on the main thread.
     */
    fun refreshAll(context: Context) = synchronized(lock) {
        val mgr = AppWidgetManager.getInstance(context) ?: return@synchronized
        val sunPlaced = SunWidgets.hasAny(context)
        val moonPlaced = MoonWidgets.hasAny(context)
        if (!sunPlaced && !moonPlaced) {
            cancelAll(context)
            setEventReceiver(context, false)
            return@synchronized
        }
        // Before the alarms are armed: they are delivered to this receiver.
        setEventReceiver(context, true)

        // One place for both families: the one the app shows. Null until one is chosen (the
        // widgets then show their placeholder), and null when the file cannot be read.
        val place = Settings.placeOrNull(context)
        val sunPlan = if (sunPlaced) SunWidgets.render(context, mgr, place) else null
        val moonNext = if (moonPlaced) MoonWidgets.render(context, mgr, place) else null
        val refresh = listOfNotNull(sunPlan?.refresh, moonNext).min()
        arm(context, refresh, sunPlan?.handover)
    }

    /** Whether any widget of any size of either family is placed. */
    fun hasAny(context: Context): Boolean = SunWidgets.hasAny(context) || MoonWidgets.hasAny(context)

    // ------------------------------------------------------------------ the two alarms

    private fun alarmIntent(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(action).setClass(context, WidgetEventReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The refresh (`RTC`) and, while the Sun line ticks, the handover (`RTC_WAKEUP`), each with a
     * 10 minute window; arming again replaces the earlier one. Not `set()`: its implicit window is
     * 75 per cent of the delay.
     */
    private fun arm(context: Context, refresh: Instant, handover: Instant?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val window = SunSchedule.WINDOW.toMillis()
        am.setWindow(AlarmManager.RTC, refresh.toEpochMilli(), window, alarmIntent(context, ACTION_REFRESH, REQ_REFRESH))
        val handoverIntent = alarmIntent(context, ACTION_HANDOVER, REQ_HANDOVER)
        if (handover != null) {
            am.setWindow(AlarmManager.RTC_WAKEUP, handover.toEpochMilli(), window, handoverIntent)
        } else {
            am.cancel(handoverIntent)
        }
    }

    /** Cancels both alarms, and disables the receiver, when no widget of any size or family is left. */
    fun cancelIfNone(context: Context) {
        if (!hasAny(context)) {
            cancelAll(context)
            setEventReceiver(context, false)
        }
    }

    private fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(context, ACTION_REFRESH, REQ_REFRESH))
        am.cancel(alarmIntent(context, ACTION_HANDOVER, REQ_HANDOVER))
    }

    /**
     * Enables or disables [WidgetEventReceiver] (disabled in the manifest): on while any widget is
     * placed, off when the last one goes, so the system no longer starts the app for a clock
     * change. Our own component, so no permission. Writes only on a change: the manifest default
     * counts as disabled, so a phone that never had a widget is never written.
     */
    fun setEventReceiver(context: Context, on: Boolean) {
        val pm = context.packageManager
        val receiver = ComponentName(context, WidgetEventReceiver::class.java)
        val isOn = pm.getComponentEnabledSetting(receiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (isOn == on) return
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        pm.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
    }
}
