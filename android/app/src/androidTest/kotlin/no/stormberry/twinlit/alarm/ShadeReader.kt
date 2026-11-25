package no.stormberry.twinlit.alarm

import android.app.Instrumentation
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads a notice as the notification shade shows it (R14, D117): opens the shade, expands the notice,
 * and returns the text of the expanded text view with the place of every character, from the
 * accessibility tree (`UiAutomation`, the same tree `uiautomator dump` prints). The place of the
 * characters says where the lines break, which the posted text cannot say: on Android 16 the system
 * template wrapped "6:57" and "PM." although the posted text held a no-break space.
 *
 * Platform classes only, no library. Android 8 (API 26) and later, because the character places
 * (`EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY`) start there.
 */
internal class ShadeReader(private val instrumentation: Instrumentation) {

    private val ui: UiAutomation = instrumentation.uiAutomation.also { automation ->
        // The windows of every app on the screen, not only the active one (R13, D137): with the alarm's heads-up
        // pinned, the expanded shade does not always take focus: on API 36, API 33 and API 29 the launcher was the active
        // window on some reads while the shade was open, so `rootInActiveWindow` alone does not find it.
        automation.serviceInfo = (automation.serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    /** The expanded text of one notice: its text as the shade holds it, and the line of each character (top edge, px). */
    class ShadeText(val text: String, val tops: List<Float?>)

    /** Runs a shell command and waits for it to finish. */
    fun shell(command: String): String =
        ui.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
        }

    /**
     * Opens the shade and waits until its notification list is in the accessibility tree. On API 29 SystemUI ignores
     * `cmd statusbar expand-notifications` for some seconds after a font scale change (R12-F, D133), so the shade is
     * closed, left alone for 3 s and opened again, up to six times. Returns whether the list is in the tree; the
     * caller fails with [describe] when it is not.
     */
    fun expandShade(): Boolean {
        repeat(6) {
            shell("cmd statusbar expand-notifications")
            val until = System.currentTimeMillis() + 2_500
            while (System.currentTimeMillis() < until) {
                if (shadeInTree()) return true
                Thread.sleep(250)
            }
            shell("cmd statusbar collapse")
            Thread.sleep(3_000)
        }
        return false
    }

    /**
     * The root of the SystemUI window that holds the shade, or null. The active window is the first choice; when it
     * is another app's, the interactive windows are searched, SystemUI's first (R13, D137: on API 36, API 33 and
     * API 29 the launcher was the active window on some reads while the shade was open).
     */
    private fun shadeRoot(): AccessibilityNodeInfo? {
        val active = ui.rootInActiveWindow
        if (active != null && active.packageName?.toString() == SYSTEM_UI && holdsShade(active)) return active
        val roots = ui.windows.orEmpty()
            .sortedByDescending { it.layer }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == SYSTEM_UI }
        val found = roots.firstOrNull { holdsShade(it) }
        // Logged once per change, so a run shows whether the active window was the launcher (D137).
        val note = "the active window is ${active?.packageName}, the shade ${if (found != null) "was found in the interactive windows" else "is not in them"}"
        if (note != lastNote) Log.i("ShadeReader", note)
        lastNote = note
        return found
    }

    private var lastNote = ""

    private fun holdsShade(root: AccessibilityNodeInfo): Boolean = find(root) {
        val id = it.viewIdResourceName.orEmpty()
        id.endsWith("/notification_stack_scroller") || id.endsWith("/notice_title") || id.endsWith("/big_text") ||
            id.endsWith("/notice_text")
    } != null

    /** The shade is open when SystemUI's window holds its notification list (or a notice's own view). */
    private fun shadeInTree(): Boolean {
        val root = shadeRoot() ?: return false
        return find(root) {
            val id = it.viewIdResourceName.orEmpty()
            id.endsWith("/notification_stack_scroller") || id.endsWith("/notice_title") || id.endsWith("/big_text")
        } != null
    }

    fun collapseShade() {
        shell("cmd statusbar collapse")
    }

    /** One text view of the expanded notice: its text, the place of every character, and its bounds on the screen and in the window. */
    class ViewRead(val text: String, val rects: List<RectF?>, val screen: Rect, val window: Rect) {
        val tops: List<Float?> get() = rects.map { it?.top }
    }

    /**
     * The expanded notice's own views (R12, D123, D124; R12-F, D130): the title, the long name (null when the name
     * flows), the time and the long place of a ring notice (null when the place flows), the text.
     */
    class NoticeRead(val title: ViewRead?, val name: ViewRead?, val lead: ViewRead?, val place: ViewRead?, val text: ViewRead) {
        /** Every view that is there has the place of at least one character (a view can be in the tree a moment before its layout). */
        fun laidOut(): Boolean = listOfNotNull(title, name, lead, place, text).all { v -> v.rects.any { it != null } }
    }

    /**
     * The expanded text view (`big_text` of the system template, or `notice_text` of the notices' own view) of the
     * one notice in the shade. Clicks the "Expand" buttons it finds until the view is there, and reads it again
     * while no character has a place yet. Null when the view never appears (the caller fails with [describe]); a
     * text that stays without places is returned as it is, so the caller still fails on it.
     */
    fun expandedText(): ShadeText? = readNotice()?.text?.let { ShadeText(it.text, it.tops) }

    /** [expandedText] with the notice's other views (title and name) and the bounds of each view. */
    fun readNotice(): NoticeRead? {
        var unreadable: NoticeRead? = null
        repeat(12) {
            val root = shadeRoot()
            if (root != null) {
                val big = find(root) { isNoticeText(it) && !it.text.isNullOrEmpty() }
                if (big != null) {
                    // A view can be in the tree a moment before its layout: then no character has a place
                    // yet (seen once in seven runs on API 36, R14, and in the title and name views, R12-F).
                    // Read again; give up only after the loop.
                    val read = NoticeRead(
                        title = find(root) { it.viewIdResourceName?.endsWith("/notice_title") == true }?.let { view(it) },
                        name = find(root) { it.viewIdResourceName?.endsWith("/notice_name") == true && it.isVisibleToUser }?.let { view(it) },
                        lead = find(root) { it.viewIdResourceName?.endsWith("/notice_lead") == true && it.isVisibleToUser }?.let { view(it) },
                        place = find(root) { it.viewIdResourceName?.endsWith("/notice_place") == true && it.isVisibleToUser }?.let { view(it) },
                        text = view(big),
                    )
                    if (read.laidOut()) return read
                    unreadable = read
                    Thread.sleep(500)
                    return@repeat
                }
                val expand = find(root) {
                    it.viewIdResourceName?.endsWith("/expand_button") == true &&
                        it.contentDescription?.toString() == "Expand" && it.isClickable
                }
                expand?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            Thread.sleep(500)
        }
        return unreadable
    }

    /**
     * Every text and content description in SystemUI's windows, the shade or a heads-up notice (R13, D142). Reads the
     * interactive windows, so a heads-up that has no focus is found too.
     */
    fun systemUiTexts(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo) {
            n.text?.toString()?.takeIf { it.isNotEmpty() }?.let(out::add)
            n.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let(out::add)
            for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
        }
        ui.windows.orEmpty().mapNotNull { it.root }.filter { it.packageName?.toString() == SYSTEM_UI }.forEach(::walk)
        return out
    }

    /** The screen as a bitmap, for what no text tree can say (an ellipsis is drawn, never in the text). */
    fun screenshot(): Bitmap? = ui.takeScreenshot()

    /** What the shade shows, as one line per text node, for a failure message. */
    fun describe(): String {
        val root = shadeRoot() ?: ui.rootInActiveWindow ?: return "no active window and no SystemUI window"
        val out = StringBuilder()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (!n.text.isNullOrEmpty()) out.append("  ".repeat(depth)).append(n.viewIdResourceName).append(": ").append(n.text).append('\n')
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out.toString()
    }

    /** The system template's `big_text`, or `notice_text` of the notices' own expanded view. */
    private fun isNoticeText(n: AccessibilityNodeInfo): Boolean {
        val id = n.viewIdResourceName ?: return false
        return id.endsWith("/big_text") || id.endsWith("/notice_text")
    }

    private fun view(node: AccessibilityNodeInfo): ViewRead {
        val text = node.text.toString()
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
        }
        node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)
        @Suppress("DEPRECATION")
        val rects = node.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
        val screen = Rect().also { node.getBoundsInScreen(it) }
        val window = Rect().also { if (Build.VERSION.SDK_INT >= 34) node.getBoundsInWindow(it) else node.getBoundsInScreen(it) }
        return ViewRead(text, List(text.length) { i -> rects?.getOrNull(i) as? RectF }, screen, window)
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
    }

    private fun find(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            find(child, match)?.let { return it }
        }
        return null
    }
}
