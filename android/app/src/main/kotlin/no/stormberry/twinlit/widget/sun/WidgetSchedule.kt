package no.stormberry.twinlit.widget.sun

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * When the widgets must be redrawn (1.1.9). Pure, so WidgetScheduleTest covers it.
 *
 * Two alarms, two PendingIntents, neither needs a permission (both are inexact `setWindow`):
 *
 * 1. The **refresh** alarm, `RTC` (never wakes the phone), at the earliest of:
 *    - `target - 24 h`, when the line changes from a date to the countdown;
 *    - `target + 1 s`, when the sun has risen or set and the target, the state and the small
 *      widget's event change;
 *    - the city's next midnight `+ 1 s` (from the zone's rules, so 23 and 25 hour days and a
 *      missing midnight are handled), when "today" changes;
 *    - `now + 3 h`, the safety net for a missed broadcast or a font change.
 * 2. The **handover** alarm, `RTC_WAKEUP`, at `target - TICK_MIN`, armed ONLY while the rendered
 *    line ticks, and cancelled otherwise. It replaces the ticking Chronometer with the static
 *    "Sunset at 15:48" line before it goes below one hour ("59:59" would read as a clock time) or
 *    below zero. It is separate from the refresh alarm because a non-wakeup alarm is held while
 *    the screen is off: with one alarm slot, an earlier `RTC` refresh (the 3 hour net, midnight)
 *    would keep the wakeup handover from ever being armed overnight.
 *
 * The model ticks only while more than [TICK_MIN] is left, so a render never ticks with its
 * handover already in the past. When the platform delivers the handover within [WINDOW] of its
 * trigger, it lands between `target - 70 min` and `target - 60 min`, and the Chronometer never
 * shows a value under one hour.
 *
 * What the platform does not promise: no alarm without a permission fires at a fixed time while
 * the phone is in Doze. A `setWindow` alarm, a wakeup one too, is deferred to Doze's next
 * maintenance window, and at the latest delivered when the screen comes on. After a still night
 * the handover can therefore come late, and until that broadcast has been rendered (usually a
 * second or two after the screen comes on) the widget can show the Chronometer under one hour or
 * below zero. Background restriction holds these alarms for longer, and so does Battery Saver on
 * Android 9 to 11, which is why the line does not tick in the first state and never ticks on those
 * API levels, 28 to 30 ([CountdownMath.tickingAllowed]); there no handover alarm is armed at all.
 */
object WidgetSchedule {

    /** The window of both alarms: the Android 12 minimum for `setWindow`. */
    val WINDOW: Duration = Duration.ofMinutes(10)

    /** The longest the widgets go without a render, whatever else happens. */
    val MAX_GAP: Duration = Duration.ofHours(3)

    /** The last stretch before the event is always the static line, never the Chronometer. */
    val LAST_STRETCH: Duration = Duration.ofMinutes(60)

    /** Beyond this the countdown line is a date ("Next sunrise Tue 16 Feb"). */
    val DATE_FORM_AFTER: Duration = Duration.ofHours(24)

    /**
     * The line ticks only while more than this is left: [LAST_STRETCH] plus the handover's
     * [WINDOW]. The handover alarm triggers at `target - TICK_MIN`.
     */
    val TICK_MIN: Duration = LAST_STRETCH.plus(WINDOW)

    /** The two alarms to arm after a render. [handover] null means "cancel it". */
    data class Plan(val refresh: Instant, val handover: Instant?)

    /** The handover instant for a ticking line aimed at [target]. */
    fun handoverAt(target: Instant): Instant = target.minus(TICK_MIN)

    /**
     * The alarms after a render at [now] for a place in [zone], whose countdown aims at [target]
     * (null: no target), with [ticking] true when the rendered line is the Chronometer.
     */
    fun plan(now: Instant, zone: ZoneId, target: Instant?, ticking: Boolean): Plan {
        val today = now.atZone(zone).toLocalDate()
        val candidates = mutableListOf(
            now.plus(MAX_GAP),
            today.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(1),
        )
        if (target != null) {
            candidates += target.minus(DATE_FORM_AFTER)
            candidates += target.plusSeconds(1)
        }
        val refresh = candidates.filter { it.isAfter(now) }.min()
        val handover = if (ticking && target != null) handoverAt(target).takeIf { it.isAfter(now) } else null
        return Plan(refresh, handover)
    }
}
