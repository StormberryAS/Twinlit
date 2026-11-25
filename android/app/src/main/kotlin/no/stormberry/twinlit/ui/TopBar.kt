package no.stormberry.twinlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.stormberry.twinlit.R
import no.stormberry.twinlit.theme.Appearance
import no.stormberry.twinlit.ui.theme.ChromeColors
import no.stormberry.twinlit.ui.components.TOP_BAR_MIN_HEIGHT
import no.stormberry.twinlit.ui.components.growsWithText

/**
 * The slim top bar (SPEC 3.1): the word "Twinlit" at the start and one Appearance button at the
 * end. Nothing else lives here. 48 dp high plus the status bar inset (the bar's background runs
 * under the status bar, edge to edge), a hairline under it.
 */
@Composable
fun TopBar(chrome: ChromeColors, onAppearance: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(chrome.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .growsWithText(TOP_BAR_MIN_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Twinlit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = chrome.TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                    .semantics { heading() },
            )
            IconButton(onClick = onAppearance, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_appearance),
                    // TEXTS.md A-0: the button's spoken label is the menu title.
                    contentDescription = Appearance.TITLE,
                    tint = chrome.TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        HorizontalDivider(color = chrome.Border)
    }
}
