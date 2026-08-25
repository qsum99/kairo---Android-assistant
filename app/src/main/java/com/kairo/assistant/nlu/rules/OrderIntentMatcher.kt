package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Matches quick commerce ordering intents such as:
 *   "order milk from Blinkit"
 *   "get me eggs on Zepto"
 *   "buy rice from Swiggy Instamart"
 *   "order 2 bananas from BigBasket"
 */
class OrderIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.ORDER_ITEM

    companion object {
        // "order/buy/get [item] from/on [app]"
        private val ORDER_FROM_PATTERN = Regex(
            """(?:order|buy|get|purchase|add)\s+(?:me\s+)?(.+?)\s+(?:from|on|via|through|using)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // "order [item] [app]" (less specific)
        private val ORDER_SIMPLE_PATTERN = Regex(
            """(?:order|buy)\s+(?:me\s+)?(.+?)\s+(blinkit|zepto|swiggy|bigbasket|jiomart|dunzo|bb\s*now|instamart)""",
            RegexOption.IGNORE_CASE
        )

        // "[app], order [item]" or "open [app] and order [item]"
        private val APP_FIRST_PATTERN = Regex(
            """(?:open\s+)?(.+?)\s*(?:,|and)\s*(?:order|buy|get|add)\s+(?:me\s+)?(.+)""",
            RegexOption.IGNORE_CASE
        )

        // Known quick commerce apps and their package names
        val APP_PACKAGES = mapOf(
            "blinkit" to "com.grofers.customerapp",
            "zepto" to "com.zepto.user",
            "swiggy" to "in.swiggy.android",
            "swiggy instamart" to "in.swiggy.android",
            "instamart" to "in.swiggy.android",
            "bigbasket" to "com.bigbasket.mobileapp",
            "bb now" to "com.bigbasket.mobileapp",
            "jiomart" to "com.jio.bapp",
            "dunzo" to "com.dunzo.user",
            "amazon" to "in.amazon.mShop.android.shopping",
            "amazon fresh" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android"
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        // Pattern 1: "order X from Y"
        ORDER_FROM_PATTERN.find(input)?.let { match ->
            val item = match.groupValues[1].trim()
            val appName = match.groupValues[2].trim()
            return buildOrderCommand(item, appName)
        }

        // Pattern 2: "order X blinkit"
        ORDER_SIMPLE_PATTERN.find(input)?.let { match ->
            val item = match.groupValues[1].trim()
            val appName = match.groupValues[2].trim()
            return buildOrderCommand(item, appName)
        }

        // Pattern 3: "open blinkit and order X"
        APP_FIRST_PATTERN.find(input)?.let { match ->
            val potentialApp = match.groupValues[1].trim().lowercase()
            val item = match.groupValues[2].trim()
            if (APP_PACKAGES.keys.any { potentialApp.contains(it) }) {
                return buildOrderCommand(item, potentialApp)
            }
        }

        return null
    }

    private fun buildOrderCommand(item: String, appName: String): ParsedCommand {
        val normalizedApp = appName.lowercase().trim()
        val packageName = APP_PACKAGES.entries.find { (key, _) ->
            normalizedApp.contains(key)
        }?.value ?: ""

        // extra = "item|packageName|appDisplayName"
        val extra = "$item|$packageName|$appName"

        return ParsedCommand(
            intent = IntentType.ORDER_ITEM,
            target = item,
            extra = extra,
            confidence = 0.90f
        )
    }
}
