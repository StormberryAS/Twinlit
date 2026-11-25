package no.stormberry.twinlit.ui

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView

/**
 * Applying a chosen place, in the one order both picker looks use (MoonApp's fix, M10, kept for
 * both looks in Twinlit, SPEC 3.4): the focus is released and the keyboard hidden FIRST, then the
 * place is handed on. A field that keeps focus would open the keyboard again when the app comes
 * back to the front, and the bottom bar stays hidden while the keyboard is open (D31).
 *
 * A pure function so PickerFocusTest can pin the order without a device. Both apply points of both
 * looks (choosing a city, applying typed coordinates) go through it; none calls the place callback
 * directly.
 */
internal fun <T> applyChosenPlace(chosen: T, release: () -> Unit, onChosen: (T) -> Unit) {
    release()
    onChosen(chosen)
}

/**
 * A focus target that is not a text field, provided by the root (`ui/App.kt`). A place picker that has
 * just applied a place moves the focus HERE instead of only clearing it.
 *
 * Why: on API 24 to 29 `clearFocus(force = true)` followed by hiding the keyboard is undone within
 * milliseconds. Hiding the input method changes the window focus, the Compose view takes the focus
 * again, and the search field, the node it last had, gets it back and shows the keyboard (measured
 * on API 24: field focused false, then true 8 ms later, every time). That kept the bottom bar hidden
 * (D31) until Back was pressed, in MoonApp 2.0.0 and in Twinlit's first build. With the focus on a
 * harmless node, the node Compose gives it back to is that one.
 */
val LocalFocusSink = staticCompositionLocalOf<FocusRequester?> { null }

/**
 * What both picker looks do after a place is applied: move the focus off the field (to
 * [LocalFocusSink], or clear it forced when there is none) and hide the keyboard. On API 24 to 29
 * `SoftwareKeyboardController.hide()` is not enough on its own, so the view's own
 * `InputMethodManager.hideSoftInputFromWindow` is called as well; API 30 and later use the
 * controller only.
 */
@Composable
internal fun rememberPlaceRelease(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val sink = LocalFocusSink.current
    return remember(focusManager, keyboard, view, sink) {
        {
            releaseFocusAndKeyboard(
                moveFocus = { if (sink != null) sink.requestFocus() else focusManager.clearFocus(force = true) },
                hideKeyboard = { keyboard?.hide() },
                legacyHide = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                },
            )
        }
    }
}

/** The order of [rememberPlaceRelease]: the focus off the field, the controller, then the legacy hide. */
internal fun releaseFocusAndKeyboard(moveFocus: () -> Unit, hideKeyboard: () -> Unit, legacyHide: () -> Unit) {
    moveFocus()
    hideKeyboard()
    legacyHide()
}
