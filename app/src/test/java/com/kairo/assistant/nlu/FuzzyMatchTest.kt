package com.kairo.assistant.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun testSimilarityExactMatch() {
        val score = FuzzyMatch.similarity("Mom", "mom")
        assertEquals(1.0f, score, 0.001f)
    }

    @Test
    fun testSimilarityPartialMatch() {
        // Levenshtein distance between "John" and "Johnny" is 2
        // maxLen is 6
        // similarity = 1 - 2/6 = 0.667
        val score = FuzzyMatch.similarity("John", "Johnny")
        assertEquals(0.667f, score, 0.01f)
    }

    @Test
    fun testSimilarityNoMatch() {
        val score = FuzzyMatch.similarity("abc", "xyz")
        assertEquals(0.0f, score, 0.001f)
    }

    @Test
    fun testBestMatchSuccess() {
        val candidates = listOf("WhatsApp", "Facebook", "Instagram", "Mom")
        val match = FuzzyMatch.bestMatch("whatsap", candidates, 0.55f)
        assertNotNull(match)
        assertEquals("WhatsApp", match?.first)
    }

    @Test
    fun testBestMatchBelowThreshold() {
        val candidates = listOf("WhatsApp", "Facebook")
        val match = FuzzyMatch.bestMatch("instagram", candidates, 0.55f)
        assertNull(match)
    }
}
