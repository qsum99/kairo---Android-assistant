package com.kairo.assistant.nlu

import com.kairo.assistant.data.AppResolver
import com.kairo.assistant.data.ContactResolver
import com.kairo.assistant.nlu.models.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleBasedParserTest {

    private lateinit var contactResolver: ContactResolver
    private lateinit var appResolver: AppResolver
    private lateinit var parser: RuleBasedParser

    @Before
    fun setUp() {
        contactResolver = ContactResolver(null).apply {
            setContactsForTesting(
                listOf(
                    Pair("Mom", "1234567890"),
                    Pair("Dad", "0987654321"),
                    Pair("John", "5551234567"),
                    Pair("John Doe", "2222222222")
                )
            )
        }

        appResolver = AppResolver(null).apply {
            setAppsForTesting(
                listOf(
                    Pair("WhatsApp", "com.whatsapp"),
                    Pair("Facebook", "com.facebook.katana"),
                    Pair("Spotify", "com.spotify.music")
                )
            )
        }

        parser = RuleBasedParser(contactResolver, appResolver)
    }

    @Test
    fun testCallIntentMatching() {
        val command1 = parser.tryMatch("call Mom")
        assertEquals(IntentType.CALL, command1.intent)
        assertEquals("Mom", command1.target)
        assertEquals("1234567890", command1.extra)

        val command2 = parser.tryMatch("give dad a ring")
        assertEquals(IntentType.CALL, command2.intent)
        assertEquals("Dad", command2.target)
        assertEquals("0987654321", command2.extra)
    }

    @Test
    fun testCallDisambiguation() {
        // "john d" matches John Doe (0.75) and John (0.67) which are both above 0.55
        val command = parser.tryMatch("call john d")
        assertEquals(IntentType.CALL, command.intent)
        assertTrue(command.extra?.startsWith("disambiguate") == true)
        assertTrue(command.extra?.contains("John Doe:2222222222") == true)
        assertTrue(command.extra?.contains("John:5551234567") == true)
    }

    @Test
    fun testCallExactMatchBypassesDisambiguation() {
        // "John" is an exact match for "John", while "John Doe" is a fuzzy match.
        // It should bypass disambiguation and return "John" directly.
        val command = parser.tryMatch("call John")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("John", command.target)
        assertEquals("5551234567", command.extra)
    }

    @Test
    fun testCallNotFound() {
        val command = parser.tryMatch("call Alice")
        assertEquals(IntentType.CALL, command.intent)
        assertEquals("Alice", command.target)
        assertEquals("not_found", command.extra)
    }

    @Test
    fun testSmsIntentMatching() {
        val command1 = parser.tryMatch("text Mom saying I am late")
        assertEquals(IntentType.SEND_SMS, command1.intent)
        assertEquals("Mom", command1.target)
        assertEquals("1234567890|I am late", command1.extra)
    }

    @Test
    fun testSmsDisambiguation() {
        val command = parser.tryMatch("text john d saying hello")
        assertEquals(IntentType.SEND_SMS, command.intent)
        assertTrue(command.extra?.startsWith("disambiguate|hello|") == true)
    }

    @Test
    fun testSmsNotFound() {
        val command = parser.tryMatch("text Alice saying hello")
        assertEquals(IntentType.SEND_SMS, command.intent)
        assertEquals("Alice", command.target)
        assertEquals("not_found|hello", command.extra)
    }

    @Test
    fun testOpenAppIntentMatching() {
        val command1 = parser.tryMatch("open WhatsApp")
        assertEquals(IntentType.OPEN_APP, command1.intent)
        assertEquals("WhatsApp", command1.target)
        assertEquals("com.whatsapp", command1.extra)
    }

    @Test
    fun testAlarmIntentMatching() {
        val command1 = parser.tryMatch("set alarm for 7:30 am")
        assertEquals(IntentType.SET_ALARM, command1.intent)
        assertEquals("07:30", command1.target)

        val command2 = parser.tryMatch("wake me up at 6 pm")
        assertEquals(IntentType.SET_ALARM, command2.intent)
        assertEquals("18:00", command2.target)
    }

    @Test
    fun testBluetoothIntentMatching() {
        val command1 = parser.tryMatch("turn on bluetooth")
        assertEquals(IntentType.TOGGLE_BLUETOOTH, command1.intent)
        assertEquals("on", command1.target)

        val command2 = parser.tryMatch("disable bluetooth")
        assertEquals(IntentType.TOGGLE_BLUETOOTH, command2.intent)
        assertEquals("off", command2.target)
    }

    @Test
    fun testSettingsIntentMatching() {
        val command = parser.tryMatch("go to settings")
        assertEquals(IntentType.OPEN_SETTINGS, command.intent)
        assertEquals("settings", command.target)
    }

    @Test
    fun testGoogleSearchIntentMatching() {
        val command1 = parser.tryMatch("google who is the president of USA")
        assertEquals(IntentType.GOOGLE_SEARCH, command1.intent)
        assertEquals("who is the president of USA", command1.target)

        val command2 = parser.tryMatch("google")
        assertEquals(IntentType.GOOGLE_SEARCH, command2.intent)
        assertEquals("", command2.target)
    }

    @Test
    fun testBingSearchIntentMatching() {
        val command1 = parser.tryMatch("bing who is the president of USA")
        assertEquals(IntentType.BING_SEARCH, command1.intent)
        assertEquals("who is the president of USA", command1.target)

        val command2 = parser.tryMatch("bing")
        assertEquals(IntentType.BING_SEARCH, command2.intent)
        assertEquals("", command2.target)
    }

    @Test
    fun testTorchIntentMatching() {
        val command1 = parser.tryMatch("turn on the torch")
        assertEquals(IntentType.TOGGLE_TORCH, command1.intent)
        assertEquals("on", command1.target)

        val command2 = parser.tryMatch("turn off flashlight")
        assertEquals(IntentType.TOGGLE_TORCH, command2.intent)
        assertEquals("off", command2.target)

        val command3 = parser.tryMatch("torch on")
        assertEquals(IntentType.TOGGLE_TORCH, command3.intent)
        assertEquals("on", command3.target)

        val command4 = parser.tryMatch("flashlight off")
        assertEquals(IntentType.TOGGLE_TORCH, command4.intent)
        assertEquals("off", command4.target)
    }

    @Test
    fun testLockDeviceIntentMatching() {
        val command1 = parser.tryMatch("lock screen")
        assertEquals(IntentType.LOCK_DEVICE, command1.intent)

        val command2 = parser.tryMatch("lock the screen")
        assertEquals(IntentType.LOCK_DEVICE, command2.intent)

        val command3 = parser.tryMatch("lock device")
        assertEquals(IntentType.LOCK_DEVICE, command3.intent)

        val command4 = parser.tryMatch("lock my phone")
        assertEquals(IntentType.LOCK_DEVICE, command4.intent)
    }

    @Test
    fun testUnknownIntentMatching() {
        val command = parser.tryMatch("what is the weather today")
        assertEquals(IntentType.UNKNOWN, command.intent)
    }
}
