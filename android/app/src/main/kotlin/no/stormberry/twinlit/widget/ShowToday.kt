package no.stormberry.twinlit.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import no.stormberry.twinlit.MainActivity

/**
 * The widget tap (SPEC 8.1 and 8.2): opens Twinlit on the place's today, on the Sun tab for a sun
 * widget ([sun]) and on the Moon tab for a moon widget ([moon]), also when the app is already
 * running. SunApp's one `SHOW_TODAY` became two actions here, because Twinlit has two tabs that a
 * widget can ask for; MoonApp only opened the app. The intent names MainActivity explicitly and
 * carries an action of its own, so MainActivity needs no new intent filter and the launcher icon
 * keeps its behaviour (a running app comes forward on whatever date it showed).
 *
 * `CLEAR_TOP or SINGLE_TOP` brings the existing MainActivity (standard launch mode) forward and
 * delivers the intent to `onNewIntent`, so no second instance is stacked. The ring screen and the
 * AlarmClock trampoline have `taskAffinity=""`, so they live in tasks of their own and CLEAR_TOP
 * never closes a ringing alarm. No extras: the action is the whole message.
 */
object ShowToday {

    const val ACTION_SUN = "no.stormberry.twinlit.action.SHOW_SUN_TODAY"
    const val ACTION_MOON = "no.stormberry.twinlit.action.SHOW_MOON_TODAY"

    /** The PendingIntents' request codes; one tap target per family (SunApp used 7). */
    const val REQUEST_CODE_SUN = 7
    const val REQUEST_CODE_MOON = 8

    const val ACTIVITY_FLAGS: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

    const val PENDING_FLAGS: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** One family's tap target: its action and its PendingIntent request code. */
    class Target internal constructor(val action: String, val requestCode: Int) {

        fun intent(context: Context): Intent =
            Intent(context, MainActivity::class.java).setAction(action).addFlags(ACTIVITY_FLAGS)

        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(context, requestCode, intent(context), PENDING_FLAGS)
    }

    val sun = Target(ACTION_SUN, REQUEST_CODE_SUN)
    val moon = Target(ACTION_MOON, REQUEST_CODE_MOON)
}
