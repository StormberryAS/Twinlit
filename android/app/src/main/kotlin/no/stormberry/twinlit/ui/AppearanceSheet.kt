package no.stormberry.twinlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import no.stormberry.twinlit.theme.Appearance
import no.stormberry.twinlit.ui.theme.ChromeColors

/**
 * The Appearance sheet (SPEC 4.7, DECISIONS D7): a modal bottom sheet in the chrome palette with
 * the title "Appearance" and the five approved options in the approved order, each a whole row
 * (at least 48 dp) that is a radio button for accessibility. A choice applies at once and the sheet
 * stays open, so the user sees the effect; Back, a swipe down or a tap on the scrim closes it.
 *
 * The texts are [Appearance.description], verbatim; the word before the first colon is set in
 * semibold, which changes no character. While no place is chosen, a note under the Auto row says
 * Auto follows System for now ([Appearance.AUTO_NOTE], approved by Marcos 2026-11-22).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    chrome: ChromeColors,
    selected: Appearance,
    hasPlace: Boolean,
    onSelect: (Appearance) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = chrome.Surface,
        contentColor = chrome.TextPrimary,
    ) {
        // The sheet's window sets its bar icons once, when it opens, from the sheet's content colour
        // (Material 3 1.4, `ModalBottomSheetDialogWrapper`); a theme chosen inside the open sheet
        // (D7) changed the chrome and left the icons as they were, dark icons on a dark scrim. So the
        // icons follow the chrome here, on every change (fix round 2).
        val sheetView = LocalView.current
        SideEffect {
            // LocalView is the compose view; the dialog layout that owns the window is one of its parents.
            generateSequence(sheetView.parent) { it.parent }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = chrome.isLight
                    isAppearanceLightNavigationBars = chrome.isLight
                }
            }
        }
        // Scrolls: Material 3 1.4's sheet never scrolls content taller than the window (with
        // skipPartiallyExpanded it is pinned at offset 0 and the overflow is cut off), so in landscape
        // and at 200 per cent text the last options could not be reached (fix round 2; WCAG 1.4.4).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = Appearance.TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = chrome.TextPrimary,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            Column(modifier = Modifier.selectableGroup()) {
                for (option in Appearance.entries) {
                    OptionRow(option, option == selected, chrome) { onSelect(option) }
                    if (option == Appearance.AUTO && !hasPlace) {
                        Text(
                            text = Appearance.AUTO_NOTE,
                            style = MaterialTheme.typography.bodySmall,
                            color = chrome.TextSecondary,
                            modifier = Modifier.padding(start = 24.dp + 24.dp + 12.dp, end = 24.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: Appearance, selected: Boolean, chrome: ChromeColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            // The row is the control; the button only draws the state.
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = chrome.Accent,
                unselectedColor = chrome.TextSecondary,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = buildAnnotatedString {
                // "Sun:" in semibold, then the rest; the characters add up to Appearance.description.
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(option.label + ":") }
                append(option.description.removePrefix(option.label + ":"))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = chrome.TextPrimary,
        )
    }
}
