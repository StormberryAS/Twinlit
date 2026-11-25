package no.stormberry.twinlit.widget.sun

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import no.stormberry.twinlit.widget.WidgetRefresh
import android.util.Log

/**
 * The three home screen widgets (1.1.9). Each size is its own provider, because a launcher lists
 * one picker entry per provider; all three draw from [SunWidgets], which redraws every placed
 * widget of every size at once.
 *
 * After a reboot (once the phone is unlocked) and after an app update the system sends
 * APPWIDGET_UPDATE itself, which lands in [onUpdate] and arms the refresh again, so no
 * BOOT_COMPLETED receiver and no permission is needed. Not directBootAware: the place is read from
 * credential-protected storage, which APPWIDGET_UPDATE after unlock can always read.
 *
 * Every entry point is wrapped: a receiver must never throw.
 */
abstract class SunWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            WidgetRefresh.refreshInBackground(this, context)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not start the widget refresh", e)
        }
    }

    /** A resize or rotation: the text sizes and, before Android 12, the large arrangement follow. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        try {
            WidgetRefresh.refreshInBackground(this, context)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not start the widget refresh", e)
        }
    }

    /** The first widget of this size is placed: enable [WidgetEventReceiver] at once. */
    override fun onEnabled(context: Context) {
        try {
            WidgetRefresh.setEventReceiver(context, true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not enable the widget receiver", e)
        }
    }

    /** The last widget of this size is gone; the alarms and the receiver stay while another size is placed. */
    override fun onDisabled(context: Context) {
        try {
            WidgetRefresh.cancelIfNone(context)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not cancel the widget refresh", e)
        }
    }

    private companion object {
        const val TAG = "SunWidgets"
    }
}

/** 2x1 "Next sun event": the next sunrise or sunset and its time. */
class SunWidgetSmall : SunWidgetProvider()

/** 3x1 "Sun times": sunrise, sunset and the countdown line. */
class SunWidgetMedium : SunWidgetProvider()

/** 4x2, resizable, "Sun times and twilight": the medium content plus solar noon, golden hour and twilight. */
class SunWidgetLarge : SunWidgetProvider()
