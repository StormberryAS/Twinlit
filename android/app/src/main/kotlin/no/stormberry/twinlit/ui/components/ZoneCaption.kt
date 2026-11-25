package no.stormberry.twinlit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer

/** The two pieces of a zone caption, for example "Times shown in" and "Europe/Oslo.": set together they read "Times shown in Europe/Oslo.". */
internal class ZoneCaptionPieces(val lead: String, val zone: String)

/**
 * The caption's words are the same as ever (TEXTS E-26 on the Moon tab, G-20 in the alarm editor); only the way
 * they are cut into pieces is new (R9, D102; the alarm editor, R13, D138).
 */
internal fun zoneCaptionPieces(lead: String, zoneId: String) = ZoneCaptionPieces(lead, "$zoneId.")

/** TEXTS E-26: the Moon tab's lead, "Times shown in". */
internal const val MOON_ZONE_LEAD = "Times shown in"

/** TEXTS G-20: the alarm editor's lead, "Times computed and shown in". */
internal const val EDITOR_ZONE_LEAD = "Times computed and shown in"

/**
 * "{lead} {zone}." under the Moon times and in the alarm editor's place card. The zone id is a piece of its own,
 * so a line breaks between "in" and the id and never at the id's slash while the id fits a line (R9, D102; the
 * Sun card's Timezone row does the same, D87; the alarm editor, R13, D138). An id wider than a line still wraps,
 * at its slash, inside its own piece. The pieces are one node for a screen reader.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ZoneCaption(lead: String, zoneId: String, color: Color, modifier: Modifier = Modifier) {
    val pieces = zoneCaptionPieces(lead, zoneId)
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val styled = fitStyle(style)
    // The width of the space the single Text used to have between "in" and the id.
    val space = with(density) { (measurer.lineWidth("a a", styled) - measurer.lineWidth("aa", styled)).toDp() }
    FlowRow(
        modifier = modifier.semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(space),
    ) {
        Text(pieces.lead, style = style, color = color)
        Text(pieces.zone, style = style, color = color)
    }
}
