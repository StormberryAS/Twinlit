package no.stormberry.twinlit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import no.stormberry.twinlit.ui.theme.LocalSunTokens
import no.stormberry.twinlit.ui.theme.MoonTokens
import no.stormberry.twinlit.ui.theme.SunTokens

/**
 * The colours of the shared place picker (R4, section 4): one picker, two palettes. The Sun tab and
 * the alarm editor draw it in the Sun-style tokens in effect; the Moon tab hands in the Moon
 * palette, so the same picker reads as MoonApp's silver and cyan there.
 *
 * Every role names what it colours, not which token it is, so a palette picks its own token for
 * it ([of]). [PlacePicker] provides the set with [LocalPickerColours]; the pieces below it
 * ([CityPicker], the chips, the labels) read [pickerColours].
 */
@Immutable
data class PickerColours(
    /** Captions, placeholders, hints, the "Missing a place?" text, the "No match" line. */
    val label: Color,
    /** Field text and suggestion names. */
    val text: Color,
    /** The unselected chip label, the "x" glyphs, a suggestion's time zone. */
    val textSecondary: Color,
    /** Field containers, the suggestion list, an unselected chip. */
    val field: Color,
    /** A selected chip and the selected place row. */
    val selectedFill: Color,
    val border: Color,
    val borderActive: Color,
    /** A selected chip's label, the selected place's label, the cursor and the button. */
    val accent: Color,
    val onAccent: Color,
    /**
     * The selected place's name in the picker's selected row (R7, D89): the tab's own colour for a
     * place, gold on the Sun tab and in the alarm editor, lunar cyan (the Moon palette's `Teal`) on the
     * Moon tab, so the name is clearly coloured on both. A text colour: 4.5 to 1 over the selected row's
     * real layering in every palette (`PlaceNameColourTest`).
     */
    val placeName: Color,
    val error: Color,
    /** The outline of an unselected chip in a light palette (fix round 1), else null. */
    val chipOutline: Color?,
    /**
     * The outline of an unselected place mode chip, in every palette (R5, D13 and D14): the label token at
     * 70 per cent in the light palettes and at 80 per cent in the dark ones (R6; at 70 per cent it was
     * 2.97 to 1 over the sky glow in SunNight and MoonNight, 3.55 to 1 or more at 80).
     */
    val modeOutline: Color,
) {
    companion object {
        fun of(t: SunTokens) = PickerColours(
            label = t.TextMuted,
            text = t.TextPrimary,
            textSecondary = t.TextSecondary,
            field = t.Surface,
            selectedFill = t.CardHover,
            border = t.Border,
            borderActive = t.BorderActive,
            accent = t.Gold,
            // The button text of today: the Sun tab put its Background on the Gold button (Critic R4, item 3).
            onAccent = t.Background,
            placeName = t.Gold,
            error = t.Rose,
            chipOutline = if (t.isLight) t.TextMuted.copy(alpha = 0.7f) else null,
            modeOutline = t.TextMuted.copy(alpha = if (t.isLight) 0.7f else 0.8f),
        )

        fun of(t: MoonTokens) = PickerColours(
            label = t.TextHint,
            text = t.TextPrimary,
            textSecondary = t.TextSecondary,
            field = t.Surface,
            selectedFill = t.CardHover,
            border = t.Border,
            borderActive = t.BorderActive,
            accent = t.Silver,
            onAccent = t.OnAccent,
            // Lunar cyan: #4FC3F7 in the Night and Dusk palettes (4.85 to 1 or more over the selected row),
            // #006A93 in the Light palette's own cyan token, where #4FC3F7 would be 1.66 to 1 (R7, D89).
            placeName = t.Teal,
            error = t.Error,
            chipOutline = if (t.isLight) t.TextHint.copy(alpha = 0.7f) else null,
            modeOutline = t.TextHint.copy(alpha = if (t.isLight) 0.7f else 0.8f),
        )
    }
}

/** Set by [PlacePicker] around its body; null outside a picker. */
val LocalPickerColours = staticCompositionLocalOf<PickerColours?> { null }

/**
 * The picker colours in effect: the set a [PlacePicker] provided, else the Sun-style tokens in
 * effect, so the alarm editor's own captions and the Alarms list draw what they always drew.
 */
@Composable
@ReadOnlyComposable
fun pickerColours(): PickerColours = LocalPickerColours.current ?: PickerColours.of(LocalSunTokens.current)
