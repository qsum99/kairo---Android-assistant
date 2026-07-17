package com.kairo.assistant.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class StatusIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testStatusIdleText() {
        composeTestRule.setContent {
            StatusIndicator(status = AssistantStatus.IDLE)
        }
        composeTestRule.onNodeWithText("Tap the mic to start").assertIsDisplayed()
    }

    @Test
    fun testStatusListeningText() {
        composeTestRule.setContent {
            StatusIndicator(status = AssistantStatus.LISTENING)
        }
        composeTestRule.onNodeWithText("Listening…").assertIsDisplayed()
    }

    @Test
    fun testStatusProcessingText() {
        composeTestRule.setContent {
            StatusIndicator(status = AssistantStatus.PROCESSING)
        }
        composeTestRule.onNodeWithText("Processing…").assertIsDisplayed()
    }

    @Test
    fun testStatusSpeakingText() {
        composeTestRule.setContent {
            StatusIndicator(status = AssistantStatus.SPEAKING)
        }
        composeTestRule.onNodeWithText("Speaking…").assertIsDisplayed()
    }

    @Test
    fun testStatusErrorText() {
        composeTestRule.setContent {
            StatusIndicator(status = AssistantStatus.ERROR)
        }
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }
}
