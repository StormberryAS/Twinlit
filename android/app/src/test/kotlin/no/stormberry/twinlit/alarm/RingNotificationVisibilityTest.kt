package no.stormberry.twinlit.alarm

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0, M11. The foreground ring notification is public on the lock screen, as the backup ring
 * notification is, so its text and Dismiss are not hidden by "hide sensitive content". The
 * project has no Robolectric, and a Notification cannot be built in a plain JVM test, so the two
 * builders are source checks (the style of the other screens' tests here).
 */
class RingNotificationVisibilityTest {

    private fun service(): String {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .flatMap { dir ->
                sequenceOf("src/main/kotlin/no/stormberry/twinlit", "app/src/main/kotlin/no/stormberry/twinlit",
                    "android/app/src/main/kotlin/no/stormberry/twinlit").map { File(dir, it) }
            }
            .firstOrNull { it.isDirectory } ?: error("main sources not found")
        return File(root, "alarm/AlarmService.kt").readText()
    }

    @Test fun `public is the value the platform calls public`() {
        // android.app.Notification.VISIBILITY_PUBLIC is 1; the compat constant must be the same.
        assertEquals(1, NotificationCompat.VISIBILITY_PUBLIC)
    }

    @Test fun `the foreground ring notification is public`() {
        val body = service().substringAfter("fun buildRingNotification(").substringBefore(".build()")
        assertTrue(body.contains(".setVisibility(NotificationCompat.VISIBILITY_PUBLIC)"))
        assertTrue(!body.contains("VISIBILITY_PRIVATE") && !body.contains("VISIBILITY_SECRET"))
    }

    @Test fun `the backup ring notification is public`() {
        val body = service().substringAfter("fun buildBackupNotification(").substringBefore("val notification = builder.build()")
        assertTrue(body.contains(".setVisibility(NotificationCompat.VISIBILITY_PUBLIC)"))
    }

    @Test fun `no notification builder in the service is left without a visibility`() {
        val text = service()
        val builders = Regex("""NotificationCompat\.Builder\(""").findAll(text).count()
        val visibilities = Regex("""\.setVisibility\(NotificationCompat\.VISIBILITY_PUBLIC\)""").findAll(text).count()
        assertEquals(builders, visibilities)
    }
}
