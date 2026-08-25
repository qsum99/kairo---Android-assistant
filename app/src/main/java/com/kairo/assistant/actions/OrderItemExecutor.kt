package com.kairo.assistant.actions

import android.content.Context
import android.util.Log
import com.kairo.assistant.intelligence.commerce.OrderAgent
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlinx.coroutines.runBlocking

private const val TAG = "OrderItemExecutor"

/**
 * Executes ORDER_ITEM intents by parsing the target item and app,
 * then launching the OrderAgent to automate the search and add-to-cart flow.
 */
class OrderItemExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val extra = command.extra ?: ""
        val parts = extra.split("|")

        val item = parts.getOrNull(0)?.trim()?.ifBlank { command.target } ?: "item"
        val packageName = parts.getOrNull(1)?.trim() ?: ""
        val appDisplayName = parts.getOrNull(2)?.trim() ?: (command.target ?: "Store")

        Log.d(TAG, "Executing order item: item='$item', app='$appDisplayName', pkg='$packageName'")

        val orderAgent = OrderAgent(context)

        return try {
            val result = runBlocking {
                orderAgent.startOrder(
                    item = item,
                    packageName = packageName,
                    appDisplayName = appDisplayName
                )
            }

            ActionResult(
                success = result.success,
                message = result.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Order item execution failed", e)
            ActionResult(
                success = false,
                message = "Failed to start ordering $item: ${e.message}"
            )
        }
    }
}