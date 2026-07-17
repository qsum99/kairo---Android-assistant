package com.kairo.assistant.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class MicButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMicButtonIdleState() {
        var clicked = false
        composeTestRule.setContent {
            MicButton(
                isListening = false,
                isProcessing = false,
                onClick = { clicked = true },
                modifier = Modifier.size(100.dp)
            )
        }

        // Verify correct start icon content description is displayed
        composeTestRule.onNodeWithContentDescription("Start listening").assertIsDisplayed()
        
        // Perform click and check that listener is called
        composeTestRule.onNodeWithContentDescription("Start listening").performClick()
        assertTrue(clicked)
    }

    @Test
    fun testMicButtonListeningState() {
        var clicked = false
        composeTestRule.setContent {
            MicButton(
                isListening = true,
                isProcessing = false,
                onClick = { clicked = true },
                modifier = Modifier.size(100.dp)
            )
        }

        // Verify correct stop icon content description is displayed
        composeTestRule.onNodeWithContentDescription("Stop listening").assertIsDisplayed()

        // Perform click and check that listener is called
        composeTestRule.onNodeWithContentDescription("Stop listening").performClick()
        assertTrue(clicked)
    }
}
