package com.kairo.assistant.intelligence.commerce

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.agent.AgentEngine
import com.kairo.assistant.nlu.rules.OrderIntentMatcher

private const val TAG = "OrderAgent"

/**
 * Commerce-specific agent that automates ordering flows on quick commerce apps.
 *
 * Workflow:
 * 1. Open the target app (Blinkit, Zepto, etc.)
 * 2. Find and tap the search bar
 * 3. Type the item name
 * 4. Identify and add the first relevant result to cart
 */
class OrderAgent(private val context: Context) {

    suspend fun startOrder(
        item: String,
        packageName: String,
        appDisplayName: String
    ): OrderResult {
        Log.i(TAG, "Starting order: item=$item app=$appDisplayName pkg=$packageName")

        if (packageName.isBlank()) {
            return OrderResult(false, "I don't know which app to use. Try: order $item from Blinkit")
        }

        // Launch the app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                return OrderResult(false, "$appDisplayName is not installed on this device.")
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app", e)
            return OrderResult(false, "Failed to open $appDisplayName")
        }

        // Wait for app to load
        kotlinx.coroutines.delay(2500)

        // Delegate to AgentEngine for intelligent screen-based automation
        val agentEngine = AgentEngine(context)
        val taskCommand = buildString {
            append("Search for '")
            append(item)
            append("' in the app and add it to cart. ")
            append("First find and tap the search bar or search icon, ")
            append("then type '")
            append(item)
            append("', then find the first matching product and tap 'Add' or '+' button.")
        }

        try {
            agentEngine.executeTask(userCommand = taskCommand, maxSteps = 10)
            return OrderResult(
                true,
                "I've opened $appDisplayName and started searching for '$item'. Check the screen to confirm and complete checkout."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Order agent task failed", e)
            return OrderResult(
                false,
                "I opened $appDisplayName but had an issue. Please search for '$item' manually."
            )
        }
    }

    fun getSupportedApps(): Map<String, String> = OrderIntentMatcher.APP_PACKAGES
}

data class OrderResult(val success: Boolean, val message: String)