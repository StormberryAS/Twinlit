package no.stormberry.twinlit.alarm

import androidx.compose.ui.graphics.Color
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.ui.theme.SunNight

/**
 * The look of the ring screen for one kind of alarm (SPEC 7.9, D24), as plain values.
 *
 * The ring screen is always dark, in every theme, so it reads the plain value objects
 * ([SunNight] and the fixed moon colours below) and never the themed `Sun` and `Moon` facades
 * (SPEC 4.2): a facade follows the Appearance choice, and an alarm that rings at 06:00 must not
 * turn light because the app's theme is Sun. Kept free of Android and of composition so that
 * `RingLookTest` can pin it on the JVM.
 *
 * The look is chosen from the [AnchorKind] the ring itself carries (D38), never by looking the
 * rule up by id: a rule deleted after it was armed, or a mirror not read yet before the first
 * unlock, would otherwise turn a moon ring into a sun ring.
 *
 *  - [SUN]: SunApp's ring screen exactly (Sun Night tokens: the time in Gold, the label in
 *    TextPrimary, the detail line in TextSecondary, the orange fallback warning on CardHover,
 *    Snooze outlined, Dismiss in Gold).
 *  - [MOON]: MoonApp's lunar look with SunApp's layout (background `#0F172A`, the full moon
 *    emoji above the time, the time and the buttons in lunar cyan `#38BDF8`, the label white, the
 *    detail line `#94A3B8`).
 */
internal data class RingLook(
    val kind: AnchorKind,
    val background: Color,
    val time: Color,
    val label: Color,
    val detail: Color,
    val snoozeText: Color,
    val snoozeBorder: Color,
    val dismissContainer: Color,
    val dismissContent: Color,
    /** The emoji above the time, or null (the sun look has none). Decorative. */
    val emoji: String?,
    /**
     * Between the parts of the detail line. A comma for both kinds, as the notifications use it
     * ("Oslo, Norway, 4 h 57 min before moonrise"). SunApp joined with a comma; MoonApp printed
     * "$eventName \u2022 $cityName" (a bullet, event first), which Twinlit does not copy (fix round 2).
     */
    val detailSeparator: String,
) {
    companion object {
        /** The fixed moon ring colours (MoonApp 68c7596 `RingActivity`). */
        val MoonBackground = Color(0xFF0F172A)
        val MoonCyan = Color(0xFF38BDF8)
        val MoonLabel = Color(0xFFFFFFFF)
        val MoonDetail = Color(0xFF94A3B8)

        private val SunLook = RingLook(
            kind = AnchorKind.SUN,
            background = SunNight.Background,
            time = SunNight.Gold,
            label = SunNight.TextPrimary,
            detail = SunNight.TextSecondary,
            snoozeText = SunNight.TextPrimary,
            snoozeBorder = SunNight.Border,
            dismissContainer = SunNight.Gold,
            dismissContent = SunNight.Background,
            emoji = null,
            detailSeparator = ", ",
        )

        private val MoonLook = RingLook(
            kind = AnchorKind.MOON,
            background = MoonBackground,
            time = MoonCyan,
            label = MoonLabel,
            detail = MoonDetail,
            snoozeText = MoonCyan,
            snoozeBorder = MoonCyan,
            dismissContainer = MoonCyan,
            dismissContent = MoonBackground,
            emoji = "🌕",
            detailSeparator = ", ",
        )

        /** The look of a ring of [kind]. */
        fun of(kind: AnchorKind): RingLook = when (kind) {
            AnchorKind.SUN -> SunLook
            AnchorKind.MOON -> MoonLook
        }

        /** The look of [alarm], from the kind the ring carries. */
        fun of(alarm: RingingAlarm): RingLook = of(alarm.kind)
    }

    /**
     * The line under the name: the place, the anchor words and "snoozed", whichever are there,
     * joined by this look's separator ("Oslo, 30 min before sunrise", "Oslo, at moonrise"). A number
     * and its unit never part on a line break (R14, D118: [NoticeText.units], no word changes).
     */
    fun detailLine(alarm: RingingAlarm): String = NoticeText.units(listOfNotNull(
        alarm.placeName.takeIf { it.isNotBlank() },
        alarm.anchorText.takeIf { it.isNotBlank() },
        "snoozed".takeIf { alarm.isSnooze },
    ).joinToString(detailSeparator))
}
