package com.kairo.assistant.intelligence.learning

import android.util.Log
import com.kairo.assistant.agent.AgentAction
import com.kairo.assistant.data.local.AppKnowledgeDao
import com.kairo.assistant.data.local.AppKnowledgeEntity
import com.kairo.assistant.screen.KairoAccessibilityService
import com.kairo.assistant.screen.ScreenLayoutParser
import com.kairo.assistant.screen.ScrollDirection
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AppKnowledgeManager"

/**
 * Manages learned app navigation flows.
 *
 * After each successful agent task, the executed step sequence is recorded
 * so the next identical task can replay instantly without LLM calls.
 *
 * Steps use *semantic anchors* (element text / content description) instead
 * of raw coordinates. On replay the anchor is re-resolved against the
 * current screen so the flow survives minor layout shifts.
 */
class AppKnowledgeManager(private val dao: AppKnowledgeDao) {

    /**
     * Minimum overlap ratio between stored task description and a new command
     * before we consider the stored flow reusable (cold-start guard).
     */
    private val OVERLAP_THRESHOLD = 0.4f

    // ── Lookup ──────────────────────────────────────────────────────────

    /**
     * Try to find a reusable learned flow for the given task.
     * Returns null if nothing is close enough or if the flow has decayed.
     */
    suspend fun findFlow(task: String, packageName: String? = null): LearnedFlow? {
        val candidates = dao.searchKnowledge(normalizeTask(task))

        return candidates
            .filter { packageName == null || it.packageName == packageName }
            .filter { it.successCount >= 2 }
            .filter { overlap(task, it.taskDescription) >= OVERLAP_THRESHOLD }
            .maxByOrNull { it.successCount }
            ?.toLearnedFlow()
    }

    // ── Record ──────────────────────────────────────────────────────────

    /**
     * Save a successful step sequence for future replay.
     * If a flow for the same task+package already exists, increment it
     * rather than creating a duplicate.
     */
    suspend fun recordSuccess(
        packageName: String,
        taskDescription: String,
        steps: List<RecordedStep>
    ) {
        if (steps.isEmpty()) return

        val stepsJson = serializeSteps(steps).toString()
        val normalizedTask = normalizeTask(taskDescription)

        // Check for existing flow to update
        val existing = dao.searchKnowledge(normalizedTask)
            .firstOrNull { it.packageName == packageName }

        if (existing != null) {
            dao.markSuccess(existing.id)
            // Overwrite steps if the sequence changed (agent may have found a better path)
            if (existing.stepsJson != stepsJson) {
                dao.insert(existing.copy(stepsJson = stepsJson, lastUsed = System.currentTimeMillis()))
            }
            Log.i(TAG, "Updated existing flow: ${existing.id}, successCount now ${existing.successCount + 1}")
        } else {
            val entity = AppKnowledgeEntity(
                packageName = packageName,
                taskDescription = taskDescription,
                stepsJson = stepsJson,
                successCount = 1
            )
            dao.insert(entity)
            Log.i(TAG, "Recorded new flow for '$taskDescription' in $packageName")
        }
    }

    /**
     * Record a replay failure — decay the flow so stale sequences
     * are eventually replaced by fresh LLM-discovered ones.
     */
    suspend fun recordFailure(packageName: String, taskDescription: String) {
        val existing = dao.searchKnowledge(normalizeTask(taskDescription))
            .firstOrNull { it.packageName == packageName } ?: return

        if (existing.successCount <= 2) {
            dao.delete(existing)
            Log.i(TAG, "Deleted low-confidence flow for '${existing.taskDescription}'")
        } else {
            // Soft decay: just don't increment; the caller won't offer it again
            // until next success
            Log.i(TAG, "Flow '${existing.taskDescription}' decayed (successCount=${existing.successCount})")
        }
    }

    // ── Serialization ───────────────────────────────────────────────────

    private fun serializeSteps(steps: List<RecordedStep>): JSONArray {
        return JSONArray().apply {
            for (step in steps) {
                put(JSONObject().apply {
                    put("action", step.action)
                    put("elementText", step.elementText ?: "")
                    put("elementClass", step.elementClass ?: "")
                    put("nearText", step.nearText ?: "")
                    put("fallbackX", step.fallbackX)
                    put("fallbackY", step.fallbackY)
                    if (step.typeText.isNotBlank()) put("typeText", step.typeText)
                    if (step.scrollDir.isNotBlank()) put("scrollDir", step.scrollDir)
                })
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun normalizeTask(task: String): String {
        return task.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun overlap(a: String, b: String): Float {
        val wordsA = normalizeTask(a).split(" ").toSet()
        val wordsB = normalizeTask(b).split(" ").toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0f
        return wordsA.intersect(wordsB).size.toFloat() / maxOf(wordsA.size, wordsB.size)
    }

    private fun AppKnowledgeEntity.toLearnedFlow(): LearnedFlow? {
        return try {
            val steps = deserializeSteps(stepsJson)
            LearnedFlow(
                id = id,
                packageName = packageName,
                taskDescription = taskDescription,
                steps = steps,
                successCount = successCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize stepsJson", e)
            null
        }
    }

    private fun deserializeSteps(json: String): List<RecordedStep> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            RecordedStep(
                action = obj.getString("action"),
                elementText = obj.optString("elementText", "").ifBlank { null },
                elementClass = obj.optString("elementClass", "").ifBlank { null },
                nearText = obj.optString("nearText", "").ifBlank { null },
                fallbackX = obj.optInt("fallbackX", 540),
                fallbackY = obj.optInt("fallbackY", 960),
                typeText = obj.optString("typeText", ""),
                scrollDir = obj.optString("scrollDir", "")
            )
        }
    }
}

// ── Data classes ───────────────────────────────────────────────────────

/**
 * A learned flow ready for replay.
 */
data class LearnedFlow(
    val id: Long,
    val packageName: String,
    val taskDescription: String,
    val steps: List<RecordedStep>,
    val successCount: Int
)

/**
 * A single recorded step in a learned flow.
 *
 * Steps use semantic anchors (elementText / nearText) for re-resolution
 * on replay. fallbackX/Y are used only when the element can't be found
 * by text (e.g., canvas-based UIs).
 */
data class RecordedStep(
    val action: String,
    val elementText: String? = null,
    val elementClass: String? = null,
    val nearText: String? = null,
    val fallbackX: Int = 540,
    val fallbackY: Int = 960,
    val typeText: String = "",
    val scrollDir: String = ""
)

// ── Replay engine ──────────────────────────────────────────────────────

/**
 * Replays a learned flow against the live screen.
 * Returns true if every step succeeded, false if any step failed
 * (caller should fall back to LLM mode).
 */
object FlowReplayer {

    /**
     * Replay a learned flow step by step.
     *
     * @param flow The learned flow to replay
     * @param service The accessibility service for screen reading and gestures
     * @param onStep Called after each step with (stepIndex, description, success)
     * @return true if all steps completed successfully
     */
    suspend fun replay(
        flow: LearnedFlow,
        service: KairoAccessibilityService,
        onStep: (Int, String, Boolean) -> Unit = { _, _, _ -> }
    ): Boolean {
        Log.i(TAG, "Replaying learned flow: '${flow.taskDescription}' (${flow.steps.size} steps)")

        for ((index, step) in flow.steps.withIndex()) {
            val layout = service.getScreenLayout()

            // 1. Resolve the target element
            val element = resolveElement(step, layout)

            // 2. Execute the action
            val success = when {
                element != null -> {
                    when (step.action) {
                        "tap" -> {
                            val desc = "Tapping ${element.text ?: element.contentDescription ?: "element"}"
                            onStep(index, desc, true)
                            service.tap(element.bounds.centerX(), element.bounds.centerY())
                        }
                        "type" -> {
                            val desc = "Typing: ${step.typeText}"
                            onStep(index, desc, true)
                            service.typeText(step.typeText)
                        }
                        "scroll" -> {
                            val dir = ScrollDirection.valueOf(step.scrollDir.uppercase())
                            val desc = "Scrolling ${dir.name.lowercase()}"
                            onStep(index, desc, true)
                            service.scroll(dir)
                        }
                        "back" -> {
                            onStep(index, "Pressing back", true)
                            service.pressBack()
                        }
                        else -> {
                            onStep(index, "Unknown action: ${step.action}", false)
                            false
                        }
                    }
                }
                // Fallback: use raw coordinates when element not found
                step.action == "tap" -> {
                    val desc = "Tapping fallback coordinates (${step.fallbackX}, ${step.fallbackY})"
                    onStep(index, desc, true)
                    service.tap(step.fallbackX, step.fallbackY)
                }
                else -> {
                    Log.w(TAG, "Step $index: element not found and no fallback for ${step.action}")
                    onStep(index, "Element not found", false)
                    false
                }
            }

            if (!success) {
                Log.w(TAG, "Flow replay failed at step $index")
                return false
            }

            // 3. Wait for meaningful change after each step
            val baseline = service.currentSignature()
            service.waitForMeaningfulChange(baseline, 2500)
        }

        Log.i(TAG, "Flow replay completed successfully")
        return true
    }

    /**
     * Resolve a recorded step to a live ScreenElement.
     *
     * Strategy:
     * 1. If nearText is set, find the nearText element first, then pick the
     *    closest matching element to it (disambiguation for multiple "Add" buttons).
     * 2. Otherwise, find the first element matching elementText.
     */
    private fun resolveElement(step: RecordedStep, layout: com.kairo.assistant.screen.ScreenLayout): com.kairo.assistant.screen.ScreenElement? {
        val text = step.elementText ?: return null

        if (step.nearText != null) {
            // Disambiguation: find the anchor element, then pick the nearest match
            val anchor = layout.elements.find {
                it.text?.contains(step.nearText, ignoreCase = true) == true ||
                it.contentDescription?.contains(step.nearText, ignoreCase = true) == true
            }
            if (anchor != null) {
                val anchorBounds = anchor.bounds
                // Pick the matching element closest to the anchor
                return layout.elements
                    .filter {
                        it.text?.contains(text, ignoreCase = true) == true ||
                        it.contentDescription?.contains(text, ignoreCase = true) == true
                    }
                    .minByOrNull { distance(it.bounds, anchorBounds) }
            }
        }

        // Simple match: first element containing the text
        return layout.elements.find {
            it.text?.contains(text, ignoreCase = true) == true ||
            it.contentDescription?.contains(text, ignoreCase = true) == true
        }
    }

    private fun distance(a: android.graphics.Rect, b: android.graphics.Rect): Int {
        val dx = a.centerX() - b.centerX()
        val dy = a.centerY() - b.centerY()
        return dx * dx + dy * dy
    }
}
