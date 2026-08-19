package com.kairo.assistant.screen

import android.graphics.Rect

/**
 * Converts a [ScreenLayout] into a human/AI-readable text format.
 *
 * Output example:
 * ```
 * Screen: com.whatsapp — ChatActivity
 * [0] ActionBar: "Mom" (0,80 → 1080,168) — clickable
 * [1] RecyclerView (0,168 → 1080,1776) — scrollable
 * [2] TextView: "Hi dear" (120,400 → 600,460)
 * [3] EditText: "Type a message" (60,1776 → 920,1860) — editable
 * [4] ImageButton: "Send" (920,1776 → 1080,1860) — clickable
 * ```
 *
 * This format is fed to the on-device LLM for decision-making.
 */
object ScreenLayoutParser {

    /**
     * Convert a ScreenLayout to a compact text representation for the LLM.
     *
     * @param layout The screen layout to convert
     * @param maxElements Maximum number of elements to include (to stay within token limits)
     */
    fun toText(layout: ScreenLayout, maxElements: Int = 30): String {
        val sb = StringBuilder()
        sb.appendLine("Screen: ${layout.packageName} — ${layout.activityName.substringAfterLast('.')}")
        sb.appendLine("---")

        val elements = layout.elements.take(maxElements)

        for (element in elements) {
            sb.append("[${element.index}] ")
            sb.append(element.className.substringAfterLast('.'))

            // Add text/label
            val label = element.text ?: element.contentDescription
            if (!label.isNullOrBlank()) {
                // Truncate long text
                val displayText = if (label.length > 50) label.take(47) + "..." else label
                sb.append(": \"$displayText\"")
            }

            // Add bounds
            sb.append(" (${element.bounds.left},${element.bounds.top}")
            sb.append(" → ${element.bounds.right},${element.bounds.bottom})")

            // Add interaction flags
            val flags = mutableListOf<String>()
            if (element.isClickable) flags.add("clickable")
            if (element.isEditable) flags.add("editable")
            if (element.isScrollable) flags.add("scrollable")
            if (element.isCheckable) {
                flags.add(if (element.isChecked) "checked" else "unchecked")
            }
            if (flags.isNotEmpty()) {
                sb.append(" — ${flags.joinToString(", ")}")
            }

            sb.appendLine()
        }

        if (layout.elements.size > maxElements) {
            sb.appendLine("... and ${layout.elements.size - maxElements} more elements")
        }

        return sb.toString()
    }

    /**
     * Create a minimal text representation with only clickable/editable elements.
     * Used for agent decisions where we only care about interactive elements.
     */
    fun toInteractiveText(layout: ScreenLayout, maxElements: Int = 20): String {
        val sb = StringBuilder()
        sb.appendLine("App: ${layout.packageName.substringAfterLast('.')}")

        val interactive = layout.elements.filter {
            it.isClickable || it.isEditable || it.isScrollable
        }.take(maxElements)

        for (element in interactive) {
            sb.append("[${element.index}] ")

            val typeLabel = when {
                element.isEditable -> "Input"
                element.isScrollable -> "Scroll"
                element.className.contains("Button", ignoreCase = true) -> "Button"
                element.className.contains("Image", ignoreCase = true) -> "Image"
                else -> "Tap"
            }
            sb.append("$typeLabel")

            val label = element.text ?: element.contentDescription
            if (!label.isNullOrBlank()) {
                val displayText = if (label.length > 40) label.take(37) + "..." else label
                sb.append(": \"$displayText\"")
            }

            // Center coordinates for tapping
            sb.append(" at (${element.bounds.centerX()},${element.bounds.centerY()})")

            sb.appendLine()
        }

        if (interactive.isEmpty()) {
            sb.appendLine("No interactive elements found on screen.")
        }

        return sb.toString()
    }

    /**
     * Find the best element matching a description.
     * Used when the AI says "tap the send button" to find the right element.
     */
    fun findBestMatch(layout: ScreenLayout, description: String): ScreenElement? {
        val lowerDesc = description.lowercase()

        // Exact text match first
        layout.elements.find {
            it.text?.lowercase() == lowerDesc ||
            it.contentDescription?.lowercase() == lowerDesc
        }?.let { return it }

        // Partial text match
        layout.elements.find {
            it.text?.lowercase()?.contains(lowerDesc) == true ||
            it.contentDescription?.lowercase()?.contains(lowerDesc) == true
        }?.let { return it }

        // Class name match (e.g., "button" matches any Button)
        layout.elements.find {
            it.className.lowercase().contains(lowerDesc) && it.isClickable
        }?.let { return it }

        return null
    }
}
