package no.stormberry.twinlit.alarm

import android.text.SpannableString
import android.text.Spanned
import android.text.style.LocaleSpan
import java.util.Locale

/**
 * Notice text with automatic hyphenation turned off (R10, D110).
 *
 * The system notification template of Android 10 hyphenates a long word at a narrow width or a large
 * font ("min-" / "utes", found on an API 29 emulator in R9). The text view has no switch an app can
 * reach, but it picks its hyphenation patterns from the locale of the text, and a [LocaleSpan] over the
 * whole text sets that locale. [Locale.ROOT] ("und") has no patterns, so no word is split. The words,
 * the line breaks between words and the language the screen reader speaks are not changed: a screen
 * reader takes its language from the span only where the app set one, and Twinlit sets none.
 */
internal fun withoutHyphenation(text: String): CharSequence =
    SpannableString(text).apply {
        setSpan(LocaleSpan(Locale.ROOT), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
