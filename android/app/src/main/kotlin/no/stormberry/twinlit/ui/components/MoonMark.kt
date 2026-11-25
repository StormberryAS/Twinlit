package no.stormberry.twinlit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import no.stormberry.twinlit.R

/** MoonApp's own background, `#050810`: the colour the moon mark's shadow was drawn to read against. */
val MoonChipColour = Color(0xFF050810)

/**
 * The night chip's diameter as a fraction of the mark's size: 60 of the drawable's 108 viewport
 * units, a circle from x 24 to x 84 around the centre (54, 54). The lit disc spans x 28 to 80 and
 * the shadow ellipse reaches x 84, so the chip is exactly as wide as the shadow's own extent and
 * does not stick out beyond the mark as a ring (it was 68/108 in the first build, which showed as a
 * black ring around the moon on every halo, fix round 1).
 */
const val MOON_CHIP_FRACTION = 60f / 108f

/**
 * The moon mark (`ic_moon_mark`, MoonApp's `ic_app_logo`, byte-identical), drawn as MoonApp draws
 * it, bare, unless [chip] asks for the "night chip" (D36).
 *
 * MoonApp draws the crescent by painting a dark ellipse (`#F2090D1E` to `#D90A1020`) over the lit
 * disc, and the ellipse reaches past the disc's edge. That shadow is invisible on a dark ground, so
 * on the Night and Dusk palettes the bare drawable shows the crescent exactly as MoonApp does, with
 * its halo behind it and no ring (the first build drew the chip there too, and at the halo's peak it
 * showed as a black ring). On a LIGHT ground the shadow would show as a dark blob beside a pale
 * disc, so there, and only there, the mark sits on its own `#050810` circle.
 *
 * @param chip true on a light ground (`isLight` of the palette, the chrome or the tokens behind it),
 *   false on a dark one. Every caller says which, so no place can draw the chip by habit.
 */
@Composable
fun MoonMark(size: Dp, chip: Boolean, modifier: Modifier = Modifier, description: String? = null) {
    val spoken = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Box(modifier = modifier.size(size).then(spoken), contentAlignment = Alignment.Center) {
        if (chip) {
            Box(
                modifier = Modifier
                    .size(size * MOON_CHIP_FRACTION)
                    .background(MoonChipColour, CircleShape),
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_moon_mark),
            // Decorative unless [description] names it on the box above: the label or title next to
            // it names it otherwise.
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
