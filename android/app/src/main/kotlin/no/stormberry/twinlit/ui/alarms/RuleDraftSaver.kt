package no.stormberry.twinlit.ui.alarms

import androidx.compose.runtime.saveable.Saver
import no.stormberry.twinlit.alarm.model.Anchor
import no.stormberry.twinlit.alarm.model.AnchorKind
import no.stormberry.twinlit.alarm.model.Direction
import no.stormberry.twinlit.data.Place
import java.time.LocalTime

/**
 * Keeps an open alarm editor's [RuleDraft] across a tab switch, a rotation and a process restart
 * (fix round 1; the draft was held with a plain `remember`, so leaving the Alarms tab, including
 * for a draft an AlarmClock `SET_ALARM` hand-off had started, threw it away).
 *
 * Every field goes into a flat map of strings, booleans and numbers, which a `Bundle` can hold
 * (a `HashMap` is `Serializable`).
 * Coordinates are written as `Double` (a Bundle keeps those exactly) and times as ISO text. A map
 * that cannot be read back (a missing key, an unknown event name, a wrong type) restores to no
 * draft rather than to a half-filled one: the editor is simply closed.
 */
internal val RuleDraftSaver: Saver<RuleDraft?, Any> = Saver(
    // A HashMap is Serializable, so a Bundle can hold it, and its values are strings, booleans and
    // doubles.
    save = { draft -> draft?.let { HashMap(draftToMap(it)) } },
    restore = { value ->
        @Suppress("UNCHECKED_CAST")
        (value as? Map<String, Any?>)?.let(::draftFromMap)
    },
)

internal fun draftToMap(d: RuleDraft): Map<String, Any?> = buildMap {
    put("id", d.id)
    put("label", d.label)
    put("kind", d.anchor.kind.key)
    put("event", d.anchor.eventName)
    put("direction", d.direction.name)
    put("hours", d.offsetHours)
    put("minutes", d.offsetMinutes)
    d.place?.let {
        put("place_label", it.label)
        put("place_lat", it.latDeg)
        put("place_lon", it.lonDeg)
        put("place_zone", it.zoneId)
        put("place_cat", it.fromCatalogue)
        put("place_name", it.name)
    }
    put("clamp_open", d.clampOpen)
    put("earliest", d.earliest?.toString())
    put("latest", d.latest?.toString())
    put("vibrate", d.vibrate)
    put("enabled", d.enabled)
    put("is_new", d.isNew)
    put("requested", d.requestedTime?.toString())
}

internal fun draftFromMap(m: Map<String, Any?>): RuleDraft? = try {
    val kind = AnchorKind.parse(m["kind"] as? String) ?: return null
    val anchor = Anchor.of(kind, m["event"] as? String) ?: return null
    val place = if (m.containsKey("place_lat")) {
        Place(
            label = m["place_label"] as String,
            latDeg = (m["place_lat"] as Number).toDouble(),
            lonDeg = (m["place_lon"] as Number).toDouble(),
            zoneId = m["place_zone"] as String,
            fromCatalogue = m["place_cat"] as Boolean,
            name = m["place_name"] as String,
        )
    } else {
        null
    }
    RuleDraft(
        id = m["id"] as String,
        label = m["label"] as String,
        anchor = anchor,
        direction = Direction.valueOf(m["direction"] as String),
        offsetHours = m["hours"] as String,
        offsetMinutes = m["minutes"] as String,
        place = place,
        clampOpen = m["clamp_open"] as Boolean,
        earliest = (m["earliest"] as? String)?.let(LocalTime::parse),
        latest = (m["latest"] as? String)?.let(LocalTime::parse),
        vibrate = m["vibrate"] as Boolean,
        enabled = m["enabled"] as Boolean,
        isNew = m["is_new"] as Boolean,
        requestedTime = (m["requested"] as? String)?.let(LocalTime::parse),
    )
} catch (e: RuntimeException) {
    null
}
