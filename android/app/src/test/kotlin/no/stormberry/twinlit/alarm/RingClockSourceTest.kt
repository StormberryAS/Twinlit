package no.stormberry.twinlit.alarm

import no.stormberry.twinlit.widget.TestFiles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clock on the ring screen, in the ringing notice's summary, in the Snoozed text and in the Missed
 * notices follows the phone's 12-hour or 24-hour setting, as SunApp 2.0.0 does (Marcos, 2026-11-21,
 * R5 D8, replacing D58 of round 1, which had forced 24-hour). The alarm list and the editor's preview
 * stay 24-hour `HH:mm`. A source test, because `DateFormat` is a stub on the plain JVM.
 */
class RingClockSourceTest {

    private val service = TestFiles.require("${TestFiles.KOTLIN}/alarm/AlarmService.kt").readText()

    @Test
    fun `the ring clock follows the phone's 12 or 24 hour setting, as SunApp 2_0_0`() {
        val body = service.substringAfter("fun formatTime(").substringBefore("/**")
        assertTrue(body, body.contains("DateFormat.getTimeFormat(context).format(Date(millis))"))
        assertTrue(service.contains("import android.text.format.DateFormat"))
        assertTrue(service.contains("import java.util.Date"))
        assertFalse(service.contains("RING_CLOCK"))
        assertFalse(service.contains("@Suppress(\"UNUSED_PARAMETER\")\n        fun formatTime"))
        // The list and the preview stay 24-hour.
        for (file in listOf("ui/alarms/AlarmsScreen.kt", "ui/alarms/RuleEditorScreen.kt")) {
            val text = TestFiles.require("${TestFiles.KOTLIN}/$file").readText()
            assertTrue(file, text.contains("DateTimeFormatter.ofPattern(\"HH:mm\", Locale.UK)"))
        }
    }
}
