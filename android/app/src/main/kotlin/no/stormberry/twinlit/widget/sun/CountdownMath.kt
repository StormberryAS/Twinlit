package no.stormberry.twinlit.widget.sun

import java.time.Instant

/**
 * The two clocks of one render, read once and next to each other (1.1.9):
 * `SystemClock.elapsedRealtime()` (what a Chronometer's base is in; it counts deep sleep) and
 * `System.currentTimeMillis()` (what the solar instants are in).
 */
data class ClockPair(val elapsedMs: Long, val wallMs: Long)

/** The arithmetic of the ticking line. Pure, so CountdownMathTest feeds it any clock pair. */
object CountdownMath {

    /**
     * The Chronometer base for a countdown to [target]: the elapsed-realtime instant at which the
     * wall clock reaches [target]. The launcher shows `base - elapsedRealtime()`, the time left.
     * A wall clock change after this render does not move the base; TIME_SET re-renders.
     */
    fun base(target: Instant, clocks: ClockPair): Long = clocks.elapsedMs + (target.toEpochMilli() - clocks.wallMs)

    /** The probe for the phone's digit format: `DateUtils.formatElapsedTime` of 1 h 2 min 3 s. */
    const val DIGIT_PROBE_SECONDS: Long = 3723L

    /**
     * Whether the launcher would print the ticking value with Latin digits and colons. The
     * Chronometer formats in the phone's locale; when that gives anything but `1:02:03` for the
     * probe (Arabic, Persian or Bengali digits, another separator), the static line is used.
     */
    fun digitsOk(formatted: String?): Boolean = formatted == "1:02:03"

    /** Android 9 (API 28) and Android 11 (API 30): the levels where the widgets never tick (P17). */
    private const val NEVER_TICKS_FROM = 28
    private const val NEVER_TICKS_TO = 30

    /**
     * Whether the widgets never tick on [sdk]: Android 9 to 11 (API 28 to 30). There Battery Saver
     * holds every alarm that is not "while idle" for an app that is not in the foreground, and a
     * widget host is not exempt, so the handover alarm that replaces the ticking Chronometer
     * before it goes below one hour could come late, and a stalled widget would show a wrong or
     * negative countdown. The owner chose the safe form for these levels (P17, 2026-11-18): fixed
     * lines only ("Sunset at 15:48"), and no countdown wake-up alarm is ever armed there.
     */
    fun neverTicks(sdk: Int): Boolean = sdk in NEVER_TICKS_FROM..NEVER_TICKS_TO

    /**
     * Whether the countdown line may tick (the Chronometer) instead of showing the static line.
     * The Chronometer is only safe while the handover alarm can be trusted to replace it before it
     * goes below one hour, and while the launcher prints the value with Latin digits. So it is
     * refused:
     * - on Android 9 to 11 (API 28 to 30), always: see [neverTicks];
     * - while the app is background restricted ([backgroundRestricted], Android 9 and later): its
     *   alarms are held;
     * - when the phone does not print the probe as `1:02:03` ([digitsOk]).
     */
    fun tickingAllowed(sdk: Int, backgroundRestricted: Boolean, digitsOk: Boolean): Boolean =
        !neverTicks(sdk) && !backgroundRestricted && digitsOk
}
