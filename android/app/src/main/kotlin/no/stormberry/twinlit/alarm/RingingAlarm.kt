package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.alarm.model.AnchorKind

/**
 * What is ringing right now, as far as the ring screen needs to know (SunApp's `RingingAlarm`,
 * which lived in `AlarmService.kt`; the service itself comes in M5, this plain data class is
 * needed earlier by [FireRules.replaced]).
 *
 * Passed to the ring screen through process-local state rather than through the activity's
 * intent, because the two have to stay in step: an auto-silence, a snooze from the
 * notification shade, or a second alarm arriving all change what should be on screen while
 * the activity is already open. An intent is a snapshot; this is the current answer.
 *
 * [kind] is the body the alarm follows (D38). The look of the ring screen, the small icon and the
 * moon wording are chosen from it, never by looking the rule up by id: that lookup fails for a
 * rule deleted after it was armed, and before the first unlock when the mirror has not been read.
 */
data class RingingAlarm(
    val ruleId: String?,
    val label: String,
    val placeName: String,
    val anchorText: String,
    val fireAtMillis: Long,
    val usedFallback: Boolean,
    val isSnooze: Boolean,
    val ringtoneUri: String?,
    val vibrate: Boolean,
    /** When this ring began, which is what its ten-minute timeout counts from (1.1.4). */
    val startedAtMillis: Long = fireAtMillis,
    /** The body the alarm follows; a ring that carries none is a sun ring. */
    val kind: AnchorKind = AnchorKind.SUN,
) {
    /** One ring of one alarm: a snooze of it rings with another [fireAtMillis]. */
    val key: String get() = "${ruleId.orEmpty()}@$fireAtMillis"
}
