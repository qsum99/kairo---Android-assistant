package com.kairo.assistant.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "KairoAccessibility"

/**
 * Represents a single interactive element on the screen.
 * Parsed from the Android Accessibility UI tree.
 */
data class ScreenElement(
    val index: Int,
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val children: List<ScreenElement> = emptyList()
) {
    /**
     * Returns a short human-readable label for this element.
     */
    fun label(): String {
        return text ?: contentDescription ?: className.substringAfterLast('.')
    }
}

/**
 * Result of reading the current screen layout.
 */
data class ScreenLayout(
    val packageName: String,
    val activityName: String,
    val elements: List<ScreenElement>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AccessibilityService that reads the UI tree of any app on screen
 * and can perform gestures (tap, type, scroll, swipe).
 *
 * Architecture inspired by PrivateAgent (orailnoor/private-agent).
 * Implemented natively in Kotlin for better Android integration.
 */
class KairoAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: KairoAccessibilityService? = null

        /**
         * Check if the service is currently running and connected.
         */
        val isRunning: Boolean get() = instance != null

        /**
         * Get the singleton instance (only available while service is connected).
         */
        fun getInstance(): KairoAccessibilityService? = instance
    }

    // Track current window for change detection
    private var _currentPackage: String = ""
    private var _currentActivity: String = ""

    /** Currently foreground package name. */
    val currentPackage: String get() = _currentPackage

    /** Currently foreground activity name. */
    val currentActivity: String get() = _currentActivity

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: ""
                val cls = event.className?.toString() ?: ""
                if (pkg != _currentPackage || cls != _currentActivity) {
                    _currentPackage = pkg
                    _currentActivity = cls
                    Log.d(TAG, "Window changed: $pkg / $cls")
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Screen content updated — agent can re-read if needed
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    // ──────────────────────────────────────────────
    // SCREEN READING
    // ──────────────────────────────────────────────

    /**
     * Read the current screen's complete UI tree.
     * Returns a [ScreenLayout] with all interactive elements and their positions.
     */
    fun getScreenLayout(): ScreenLayout {
        val rootNode = rootInActiveWindow ?: return ScreenLayout(
            packageName = currentPackage,
            activityName = currentActivity,
            elements = emptyList()
        )

        val elements = mutableListOf<ScreenElement>()
        var index = 0
        parseNode(rootNode, elements, index)
        rootNode.recycle()

        return ScreenLayout(
            packageName = currentPackage,
            activityName = currentActivity,
            elements = elements
        )
    }

    /**
     * Recursively parse an AccessibilityNodeInfo tree into ScreenElements.
     * Only includes elements that are interactive or have meaningful text.
     */
    private fun parseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<ScreenElement>,
        startIndex: Int
    ): Int {
        var index = startIndex
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Include element if it's interactive or has text
        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isCheckable

        if (hasText || isInteractive) {
            val className = node.className?.toString() ?: "View"
            elements.add(
                ScreenElement(
                    index = index,
                    className = className,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked
                )
            )
            index++
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            index = parseNode(child, elements, index)
            child.recycle()
        }

        return index
    }

    /**
     * Find a specific element by its text content.
     */
    fun findElementByText(text: String): ScreenElement? {
        val layout = getScreenLayout()
        return layout.elements.find {
            it.text?.contains(text, ignoreCase = true) == true ||
            it.contentDescription?.contains(text, ignoreCase = true) == true
        }
    }

    /**
     * Find all clickable elements on the current screen.
     */
    fun findClickableElements(): List<ScreenElement> {
        return getScreenLayout().elements.filter { it.isClickable }
    }

    /**
     * Find all editable (text input) elements on the current screen.
     */
    fun findEditableElements(): List<ScreenElement> {
        return getScreenLayout().elements.filter { it.isEditable }
    }

    // ──────────────────────────────────────────────
    // MEANINGFUL CHANGE DETECTION
    // ──────────────────────────────────────────────

    /**
     * Compute a cheap signature of a screen layout for change detection.
     * Two layouts are "meaningfully different" when the window (package/activity)
     * or the set of visible element labels changes. Scroll offsets and
     * animations that don't alter the element set are ignored.
     */
    fun signatureOf(layout: ScreenLayout): Int {
        var hash = 31 * layout.packageName.hashCode() + layout.activityName.hashCode()
        for (element in layout.elements) {
            hash = 31 * hash + element.label().hashCode()
        }
        return hash
    }

    /**
     * Signature of whatever is currently on screen.
     */
    fun currentSignature(): Int = signatureOf(getScreenLayout())

    /**
     * Wait until the screen changes *meaningfully* relative to [baselineSignature],
     * or [timeoutMs] elapses. Returns true if a meaningful change was observed.
     *
     * A change only counts once the new signature has been stable for [settleMs],
     * so streaming animations and transitional frames don't trigger early returns.
     */
    suspend fun waitForMeaningfulChange(
        baselineSignature: Int,
        timeoutMs: Long = 2000,
        settleMs: Long = 300
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var pendingSignature = -1
        var pendingSince = 0L

        while (SystemClock.uptimeMillis() < deadline) {
            val signature = currentSignature()
            when {
                // Fell back to baseline — whatever flickered wasn't a real change
                signature == baselineSignature -> {
                    pendingSignature = -1
                    pendingSince = 0L
                }
                // New changed signature seen — start the settle window
                signature != pendingSignature -> {
                    pendingSignature = signature
                    pendingSince = SystemClock.uptimeMillis()
                }
                // Changed signature held stable long enough — real change
                SystemClock.uptimeMillis() - pendingSince >= settleMs -> return true
            }
            delay(120)
        }

        // Timed out — a net difference still counts as a change, otherwise "no effect"
        return currentSignature() != baselineSignature
    }

    /**
     * Wait until the foreground window belongs to [packageName], or timeout.
     * Used instead of blind sleeps after launching an app.
     */
    suspend fun waitForPackage(packageName: String, timeoutMs: Long = 5000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (currentPackage == packageName) return true
            delay(200)
        }
        val arrived = currentPackage == packageName
        if (!arrived) {
            Log.w(TAG, "waitForPackage timed out: wanted $packageName, foreground is $currentPackage")
        }
        return arrived
    }

    // ──────────────────────────────────────────────
    // GESTURE EXECUTION
    // ──────────────────────────────────────────────

    /**
     * Tap at specific screen coordinates.
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        Log.d(TAG, "Tap at ($x, $y)")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        return dispatchGestureAndWait(gesture)
    }

    /**
     * Tap on a specific ScreenElement (uses center of its bounds).
     */
    suspend fun tapElement(element: ScreenElement): Boolean {
        val centerX = element.bounds.centerX()
        val centerY = element.bounds.centerY()
        return tap(centerX, centerY)
    }

    /**
     * Long press at specific screen coordinates.
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 1000): Boolean {
        Log.d(TAG, "Long press at ($x, $y) for ${durationMs}ms")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return dispatchGestureAndWait(gesture)
    }

    /**
     * Type text into the currently focused text field.
     */
    fun typeText(text: String): Boolean {
        Log.d(TAG, "Typing: '$text'")
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        return if (focusedNode != null) {
            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focusedNode.recycle()
            rootNode.recycle()
            result
        } else {
            rootNode.recycle()
            false
        }
    }

    /**
     * Swipe between two points on screen.
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean {
        Log.d(TAG, "Swipe ($x1,$y1) → ($x2,$y2)")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return dispatchGestureAndWait(gesture)
    }

    /**
     * Scroll in a direction on screen.
     */
    suspend fun scroll(direction: ScrollDirection): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        return when (direction) {
            ScrollDirection.UP -> swipe(centerX, centerY + 400, centerX, centerY - 400, 400)
            ScrollDirection.DOWN -> swipe(centerX, centerY - 400, centerX, centerY + 400, 400)
            ScrollDirection.LEFT -> swipe(centerX + 400, centerY, centerX - 400, centerY, 400)
            ScrollDirection.RIGHT -> swipe(centerX - 400, centerY, centerX + 400, centerY, 400)
        }
    }

    /**
     * Press the back button.
     */
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Press the home button.
     */
    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Open the recent apps / overview screen.
     */
    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Open the notification shade.
     */
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    // ──────────────────────────────────────────────
    // INTERNAL HELPERS
    // ──────────────────────────────────────────────

    /**
     * Dispatch a gesture and suspend until it completes.
     */
    private suspend fun dispatchGestureAndWait(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        return suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            dispatchGesture(gesture, callback, null)
        }
    }
}

/**
 * Scroll direction for the gesture executor.
 */
enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}
