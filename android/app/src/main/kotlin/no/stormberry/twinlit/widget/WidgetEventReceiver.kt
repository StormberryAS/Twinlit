package no.stormberry.twinlit.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Redraws the widgets of both families when the clock, the day or the language may have moved under
 * them (SPEC 8.3; SunApp 1.1.9 and MoonApp 1.0.8 each had one, Twinlit has this one):
 *
 * - [WidgetRefresh.ACTION_REFRESH]: the inexact, non-wakeup refresh alarm (WidgetSchedule);
 * - [WidgetRefresh.ACTION_HANDOVER]: the inexact wakeup alarm that replaces the ticking Sun line with
 *   the static one before the last stretch;
 * - TIME_SET and TIMEZONE_CHANGED: a Chronometer's base follows the wall clock only through a
 *   render, and "today" may have changed;
 * - LOCALE_CHANGED: the digit check that decides whether the Sun line may tick;
 * - MY_PACKAGE_REPLACED: Twinlit was updated.
 *
 * The four system broadcasts are on Android's list of implicit broadcasts that manifest receivers
 * still get from Android 8; none needs a permission. NOT exported: the system sends them from its
 * own uid, and the two alarms are this app's own PendingIntents, so no other app can start a
 * refresh. Disabled in the manifest and enabled only while a widget is placed
 * ([WidgetRefresh.setEventReceiver]). Separate from `alarm/SystemEventReceiver`; the two do not
 * depend on each other. Any other action is ignored.
 */
class WidgetEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            WidgetRefresh.ACTION_REFRESH,
            WidgetRefresh.ACTION_HANDOVER,
            -> WidgetRefresh.refreshInBackground(this, context)
            else -> Unit
        }
    }
}
