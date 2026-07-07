package com.kairo.assistant.nlu

import kotlin.math.max
import kotlin.math.min

/**
 * Utility object for fuzzy string matching using Levenshtein distance.
 */
object FuzzyMatch {

    /**
     * Returns a normalized similarity score between two strings (0.0 = no match, 1.0 = exact match).
     */
    fun similarity(a: String, b: String): Float {
        val lowerA = a.lowercase().trim()
        val lowerB = b.lowercase().trim()
        if (lowerA == lowerB) return 1.0f
        val maxLen = max(lowerA.length, lowerB.length)
        if (maxLen == 0) return 1.0f
        val distance = levenshtein(lowerA, lowerB)
        return 1.0f - (distance.toFloat() / maxLen.toFloat())
    }

    /**
     * Full dynamic-programming implementation of the Levenshtein edit distance.
     */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length

        // dp[i][j] = edit distance between a[0..i-1] and b[0..j-1]
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Finds the best matching candidate from the list that exceeds the given similarity threshold.
     *
     * @param query The search query string.
     * @param candidates List of candidate strings to match against.
     * @param threshold Minimum similarity score required (default 0.55).
     * @return A pair of (bestMatch, score) if a match above threshold is found, null otherwise.
     */
    fun bestMatch(
        query: String,
        candidates: List<String>,
        threshold: Float = 0.55f
    ): Pair<String, Float>? {
        var bestCandidate: String? = null
        var bestScore = 0.0f

        for (candidate in candidates) {
            val score = similarity(query, candidate)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        return if (bestCandidate != null && bestScore >= threshold) {
            Pair(bestCandidate, bestScore)
        } else {
            null
        }
    }
}
