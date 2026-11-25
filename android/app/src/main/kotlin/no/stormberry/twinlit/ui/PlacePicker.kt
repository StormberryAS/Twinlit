package no.stormberry.twinlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.stormberry.twinlit.cities.City
import no.stormberry.twinlit.cities.CitySearch
import no.stormberry.twinlit.data.InputMode
import no.stormberry.twinlit.data.Place
import no.stormberry.twinlit.data.PlaceDisplay
import no.stormberry.twinlit.data.PlaceName
import no.stormberry.twinlit.ui.components.ModeTabs
import no.stormberry.twinlit.ui.components.SectionLabel
import no.stormberry.twinlit.ui.components.fitStyle
import no.stormberry.twinlit.ui.components.lineWidth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import no.stormberry.twinlit.ui.components.APPLY_COORDINATES_MIN_HEIGHT
import no.stormberry.twinlit.ui.components.growsWithText

/**
 * The shared place picker (SPEC 3.4, R4 section 4): the mode tabs "City search" and
 * "Coordinates", the city search with its suggestions, the coordinate fields with the optional
 * name. Shown in the Sun tab, in SunApp's look, and in the Moon tab, the same picker in the Moon
 * palette ([colours], [PickerColours.of]); MoonApp's own picker card is gone (round R4, Marcos,
 * 2026-11-20: "make the location selection for the moon part as similar to the sun part as
 * possible"). The alarm editor uses [CityPicker] with the same colours.
 *
 * The logic is SunApp's `SunTimesScreen` code, lifted out of that screen: the catalogue search,
 * the timezone a typed pair inherits from the nearest catalogue city, the name rule (W5), the
 * parsing of a decimal comma and a Unicode minus. What changed: the place is not kept here but
 * handed to [onPlaceChange], because ONE place is shared by every tab (SPEC 3.3); and applying a
 * place releases the focus AND hides the keyboard first ([applyChosenPlace]), because the bottom
 * bar stays hidden while the keyboard is open (D31; SunApp only cleared the focus).
 *
 * The typed text (search, latitude, longitude, name) is saved with the place it belongs to, so it
 * survives scrolling, a rotation and a visit to another tab, and never leaks onto another place.
 *
 * @param place the shared place, or null before one is chosen
 * @param colours the palette of the picker; the Sun-style tokens in effect when none is given
 * @param suggestionLabel the spoken label of a suggestion ("Show moon times for ..." on the Moon tab)
 * @param mode which half is showing; the caller stores it (Settings.mode)
 * @param onPlaceChange the new place, or null when the user clears the selection
 */
@Composable
fun PlacePicker(
    catalogue: CatalogueState,
    place: Place?,
    mode: InputMode,
    onModeChange: (InputMode) -> Unit,
    onPlaceChange: (Place?) -> Unit,
    modifier: Modifier = Modifier,
    colours: PickerColours = pickerColours(),
    suggestionLabel: (City) -> String = { "Show sun times for ${it.name}" },
) {
    // Forced focus release and the keyboard hidden, with the API 24 to 29 fallback (PlaceFocus.kt).
    val release = rememberPlaceRelease()

    // The typed input follows the place (Critic R4, item 1). The tabs sit in a saveable state holder,
    // and `rememberSaveable(place)` would hand back the OLD text beside a NEW place after a tab visit
    // (Compose does not compare the inputs when it restores). `key()` enters the composite key, so a
    // saved entry of another place is never matched, and the same place (a rotation, a tab visit)
    // restores the half-typed text.
    val placeKey = place?.let { "${it.latDeg},${it.lonDeg},${it.fromCatalogue},${it.name}" } ?: "none"

    key(placeKey) {
        var query by rememberSaveable { mutableStateOf("") }
        // Typed coordinates and their name are prefilled from the shared place when it was typed, so
        // opening the tab on a typed place and pressing the button again renames it.
        var latText by rememberSaveable { mutableStateOf(place?.takeIf { !it.fromCatalogue }?.latDeg?.toString() ?: "") }
        var lonText by rememberSaveable { mutableStateOf(place?.takeIf { !it.fromCatalogue }?.lonDeg?.toString() ?: "") }
        var nameText by rememberSaveable {
            mutableStateOf(place?.takeIf { !it.fromCatalogue }?.let { PlaceDisplay.givenName(it) } ?: "")
        }
        var coordinateError by rememberSaveable { mutableStateOf<String?>(null) }

        val suggestions = remember(query, catalogue) {
            val ready = catalogue as? CatalogueState.Ready
            if (ready == null || query.isBlank()) emptyList() else CitySearch.search(ready.table, query)
        }

        fun selectCity(city: City) {
            applyChosenPlace(placeFromCity(city), release) { next ->
                onPlaceChange(next)
                // Clearing the query closes the suggestion list, which is what the web app's
                // dropdown does on selection.
                query = ""
                coordinateError = null
            }
        }

        fun applyCoordinates() {
            val lat = parseCoordinate(latText)
            val lon = parseCoordinate(lonText)
            val ready = catalogue as? CatalogueState.Ready
            coordinateError = when {
                lat == null || lon == null ->
                    "Enter a number for both latitude and longitude, for example 59,91 and 10,75."
                lat < -90.0 || lat > 90.0 ->
                    "Latitude must be between -90 and 90."
                lon < -180.0 || lon > 180.0 ->
                    "Longitude must be between -180 and 180."
                ready == null ->
                    "The catalogue is still loading. It carries the timezone table, so give it a moment."
                else -> {
                    // No coordinate carries a timezone, so it is inherited from the nearest catalogue
                    // city, exactly as the web app does it. The device zone is the last resort and only
                    // reachable with an empty table, which a parse failure would already have reported.
                    val zone = CitySearch.nearestTimezone(ready.table, lat, lon) ?: ZoneId.systemDefault().id
                    val next = placeFromCoordinates(lat, lon, zone, nameText)
                    nameText = next.name
                    applyChosenPlace(next, release) { onPlaceChange(it) }
                    null
                }
            }
        }

        CompositionLocalProvider(LocalPickerColours provides colours) {
            Column(modifier = modifier.fillMaxWidth()) {
                ModeTabs(
                    mode = mode,
                    onSelect = {
                        onModeChange(it)
                        coordinateError = null
                    },
                )
                Spacer(Modifier.height(16.dp))

                when (mode) {
                    InputMode.CITY -> CityPicker(
                        state = catalogue,
                        query = query,
                        onQueryChange = { query = it },
                        suggestions = suggestions,
                        onSelect = ::selectCity,
                        selectedLabel = place?.takeIf { it.fromCatalogue }?.label,
                        onClear = { onPlaceChange(null) },
                        suggestionLabel = suggestionLabel,
                    )

                    InputMode.COORDINATES -> CoordinateFields(
                        latText = latText,
                        lonText = lonText,
                        onLatChange = { latText = it; coordinateError = null },
                        onLonChange = { lonText = it; coordinateError = null },
                        nameText = nameText,
                        onNameChange = { nameText = PlaceName.typing(it) },
                        error = coordinateError,
                        onApply = ::applyCoordinates,
                    )
                }
            }
        }
    }
}

/** The place a catalogue city makes: it carries its own IANA zone, so there is nothing to resolve. */
internal fun placeFromCity(city: City): Place = Place(
    label = "${city.name}, ${city.country}",
    latDeg = city.lat,
    lonDeg = city.lon,
    zoneId = city.tz,
    fromCatalogue = true,
)

/** The place typed coordinates make; a blank [name] means no name, the same place with a new name is the rename. */
internal fun placeFromCoordinates(latDeg: Double, lonDeg: Double, zoneId: String, name: String): Place = Place(
    label = formatCoordinates(latDeg, lonDeg),
    latDeg = latDeg,
    lonDeg = lonDeg,
    zoneId = zoneId,
    fromCatalogue = false,
    name = PlaceName.sanitise(name),
)

/** The widest number a coordinate field takes: a negative longitude with four decimals. */
internal const val WIDEST_COORDINATE = "-122.4194"
internal val COORDINATE_FIELD_GAP = 10.dp

/** A field's own width beside its text: 16 dp of padding on each side, the 1 dp outline and slack. */
internal val COORDINATE_FIELD_CHROME = 36.dp

/**
 * The decision of D94: the latitude and longitude fields stand side by side while the widest number a field
 * takes ([numberPx] of [WIDEST_COORDINATE], with [chromePx] of padding and outline) and the caption
 * ([captionPx] of "LONGITUDE") fit one field of [fieldPx]; otherwise one over the other.
 */
internal fun coordinateFieldsSideBySide(fieldPx: Float, numberPx: Float, chromePx: Float, captionPx: Float): Boolean =
    numberPx + chromePx <= fieldPx && captionPx <= fieldPx

/** The width of one of two fields side by side: half the row less the gap between them (D94). */
internal fun coordinateFieldPx(rowPx: Float, gapPx: Float): Float = (rowPx - gapPx) / 2f

/** What the name does, as literally as the app does it (2.0.0). */
internal const val NAME_HELP =
    "Shown instead of Your coordinates in the app, on the widgets and in the alarms. " +
        "Up to ${PlaceName.MAX_LENGTH} characters; leave empty for no name."

@Composable
private fun CoordinateFields(
    latText: String,
    lonText: String,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    nameText: String,
    onNameChange: (String) -> Unit,
    error: String?,
    onApply: () -> Unit,
) {
    val c = pickerColours()
    Column(modifier = Modifier.fillMaxWidth()) {
        // Latitude and longitude side by side while "-122.4194", the widest number a field takes (nine
        // monospace characters), and the field's caption fit a field; one over the other when not, so a
        // number is never cut or broken (R8, D94).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val numberStyle = fitStyle(MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            val captionStyle = fitStyle(MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp))
            val fieldPx = coordinateFieldPx(constraints.maxWidth.toFloat(), with(density) { COORDINATE_FIELD_GAP.toPx() })
            val sideBySide = coordinateFieldsSideBySide(
                fieldPx = fieldPx,
                numberPx = measurer.lineWidth(WIDEST_COORDINATE, numberStyle),
                chromePx = with(density) { COORDINATE_FIELD_CHROME.toPx() },
                captionPx = measurer.lineWidth("LONGITUDE", captionStyle),
            )
            val fields = listOf(
                Triple("Latitude", "59.9139", latText to onLatChange),
                Triple("Longitude", "10.7522", lonText to onLonChange),
            )
            if (sideBySide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(COORDINATE_FIELD_GAP),
                ) {
                    for ((label, placeholder, state) in fields) {
                        CoordinateField(label, placeholder, state.first, state.second, ImeAction.Next, {}, Modifier.weight(1f))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((label, placeholder, state) in fields) {
                        CoordinateField(label, placeholder, state.first, state.second, ImeAction.Next, {}, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "North and east are positive, south and west negative. A decimal comma " +
                "is understood as well as a decimal point.",
            style = MaterialTheme.typography.bodySmall,
            color = c.label,
        )

        Spacer(Modifier.height(12.dp))
        NameField(value = nameText, onChange = onNameChange, onDone = onApply)
        Spacer(Modifier.height(6.dp))
        Text(
            text = NAME_HELP,
            style = MaterialTheme.typography.bodySmall,
            color = c.label,
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = c.error,
            )
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().growsWithText(APPLY_COORDINATES_MIN_HEIGHT),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent,
                contentColor = c.onAccent,
            ),
        ) {
            // The web app's "Calculate Sun Times" button, kept for this mode only.
            // Typing coordinates has no moment of commitment the way tapping a city
            // does, and recomputing on every keystroke would rescan the catalogue
            // for a timezone while the user is still halfway through a number.
            Text("Use these coordinates", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CoordinateField(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = pickerColours()
    Column(modifier = modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(placeholder, color = c.label) },
            keyboardOptions = KeyboardOptions(
                // Decimal rather than Number, so the keyboard offers a separator
                // and a minus sign. Whether it offers a comma or a full stop is
                // the device's business; parseCoordinate accepts either.
                keyboardType = KeyboardType.Decimal,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone() },
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
                focusedContainerColor = c.field,
                unfocusedContainerColor = c.field,
                focusedBorderColor = c.borderActive,
                unfocusedBorderColor = c.border,
                cursorColor = c.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The optional name of a place typed as coordinates (2.0.0). The field itself holds at most
 * [PlaceName.MAX_LENGTH] characters (`PlaceName.typing`, applied by the caller as the user types),
 * and the model sanitises it again when the place is stored.
 */
@Composable
private fun NameField(
    value: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = pickerColours()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("Name (optional)")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text("For example Home", color = c.label) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = c.text,
                unfocusedTextColor = c.text,
                focusedContainerColor = c.field,
                unfocusedContainerColor = c.field,
                focusedBorderColor = c.borderActive,
                unfocusedBorderColor = c.border,
                cursorColor = c.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


/* ------------------------------------------------------------------ *
 * Pure helpers, no Compose.
 * ------------------------------------------------------------------ */

/** "59.9139 N, 10.7522 E", the web app's four decimals with hemispheres spelled out. */
internal fun formatCoordinates(latDeg: Double, lonDeg: Double): String = coordinateText(latDeg, lonDeg, ' ')

/**
 * [formatCoordinates] for the screen (R7, D86): a no-break space between each number and its
 * hemisphere letter ("59.9139\u00A0N, 10.7522\u00A0E"), so the only place a line can break is after the
 * comma and a letter is never alone on a line. Display only: the stored label of a typed place, and
 * every comparison with it, keep the plain space of [formatCoordinates].
 */
internal fun displayCoordinates(latDeg: Double, lonDeg: Double): String = coordinateText(latDeg, lonDeg, '\u00A0')

private fun coordinateText(latDeg: Double, lonDeg: Double, gap: Char): String {
    val lat = String.format(Locale.UK, "%.4f", abs(latDeg))
    val lon = String.format(Locale.UK, "%.4f", abs(lonDeg))
    // Hemisphere letters rather than signs: a minus in front of a longitude is
    // easy to misread on a phone, and "W" cannot be mistaken for anything else.
    val ns = if (latDeg < 0) "S" else "N"
    val ew = if (lonDeg < 0) "W" else "E"
    return "$lat$gap$ns, $lon$gap$ew"
}

/**
 * Reads a typed latitude or longitude, or null if it is not a number.
 *
 * Three tolerances, all of them things real keyboards produce. A decimal comma,
 * because that is what a Norwegian, German or Brazilian keyboard offers and
 * `toDouble` rejects. A Unicode minus (U+2212), because some keyboards and every
 * copy-and-paste from a typeset page uses it instead of a hyphen. A trailing
 * degree sign, because coordinates are usually written with one.
 */
internal fun parseCoordinate(raw: String): Double? {
    val cleaned = raw.trim()
        .replace('−', '-')
        .replace(',', '.')
        .removeSuffix("°")
        .trim()
    val value = cleaned.toDoubleOrNull() ?: return null
    // NaN and the infinities parse happily and would poison every downstream
    // comparison, so they are rejected here rather than checked for later.
    return if (value.isFinite()) value else null
}

