package no.stormberry.twinlit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.stormberry.twinlit.R
import no.stormberry.twinlit.ui.components.MoonMark
import no.stormberry.twinlit.ui.theme.ChromeColors
import no.stormberry.twinlit.ui.theme.Tab

/**
 * The bottom bar (SPEC 3.1): a Material 3 [NavigationBar] with exactly three items, labelled
 * "Sun", "Moon" and "Alarms" (Marcos's words, TEXTS.md B-2 to B-4), labels always shown.
 *
 * The icons are the original ones (Marcos, 2026-11-19, "Moon tab should keep moon icon, sun tab
 * with sun icon"): the Sun tab SunApp's sun mark (`ic_sun_mark`, untinted, in full colour), the
 * Moon tab MoonApp's moon mark (`ic_moon_mark`, untinted, always on its night chip through
 * [MoonMark], D36), the Alarms tab a new alarm-clock vector tinted like the label. They are
 * decorative (no content description): the label names the tab.
 *
 * Colours come from the chrome palette: container Surface, indicator CardHover, the selected
 * label in the tab's accent (Gold, Silver or the Twilight amber, in every theme, R5) and semibold, the
 * others TextSecondary; the selected item also carries a 48 by 3 dp mark in the accent at its top (R5,
 * D10), the cue that keeps 3 to 1 against the bar.
 */
@Composable
fun BottomBar(
    selected: Tab,
    chrome: ChromeColors,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(color = chrome.Border)
        NavigationBar(
            containerColor = chrome.Surface,
            contentColor = chrome.TextSecondary,
            tonalElevation = 0.dp,
        ) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = chrome.Accent,
                selectedTextColor = chrome.Accent,
                indicatorColor = chrome.CardHover,
                unselectedIconColor = chrome.TextSecondary,
                unselectedTextColor = chrome.TextSecondary,
            )
            for (tab in Tab.entries) {
                val isSelected = tab == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { if (!isSelected) onSelect(tab) },
                    icon = {
                        when (tab) {
                            Tab.SUN -> Image(
                                painter = painterResource(R.drawable.ic_sun_mark),
                                contentDescription = null,
                                modifier = Modifier.size(ICON_SIZE),
                            )
                            // The bare mark on a dark bar, as MoonApp draws it; the night chip only on a light bar.
                            Tab.MOON -> MoonMark(size = ICON_SIZE, chip = chrome.isLight)
                            // Centred in a box as tall as the two marks, so the three labels line up.
                            Tab.ALARMS -> Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_tab_alarm),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    },
                    label = {
                        Text(
                            text = tabLabel(tab),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = colors,
                    // R5 (D10): the selected tab is marked in its own accent, a bar at the top of the item,
                    // 3 to 1 or more against the bar in every theme (BottomBarCueTest).
                    modifier = if (isSelected) Modifier.drawBehind { drawSelectionMark(chrome.Accent) } else Modifier,
                )
            }
        }
    }
}

/** The selected tab's mark (R5, D10): a 48 by 3 dp bar with round ends, centred at the top of the item. */
private fun DrawScope.drawSelectionMark(colour: Color) {
    val w = SELECTION_MARK_WIDTH.toPx()
    val h = SELECTION_MARK_HEIGHT.toPx()
    drawRoundRect(
        color = colour,
        topLeft = Offset((size.width - w) / 2f, 0f),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f),
    )
}

internal val SELECTION_MARK_WIDTH = 48.dp
internal val SELECTION_MARK_HEIGHT = 3.dp

/** The mark icons are drawn a little larger than the 24 dp tinted one: their art sits inside a wide margin. */
private val ICON_SIZE = 32.dp

/** TEXTS.md B-2, B-3, B-4: Marcos's tab words. */
internal fun tabLabel(tab: Tab): String = when (tab) {
    Tab.SUN -> "Sun"
    Tab.MOON -> "Moon"
    Tab.ALARMS -> "Alarms"
}
