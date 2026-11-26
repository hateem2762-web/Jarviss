package com.example

import com.example.model.ActionIntent
import com.example.model.JarvisAction
import com.example.model.TapCoordinateItem
import com.example.system.CoordinateMatcher
import com.example.system.WakeWordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun action_intent_mapping_is_accurate() {
        assertEquals(ActionIntent.OPEN_APP, ActionIntent.fromWire("open_app"))
        assertEquals(ActionIntent.OPEN_URL, ActionIntent.fromWire("open_url"))
        assertEquals(ActionIntent.SEARCH_YOUTUBE, ActionIntent.fromWire("search_youtube"))
        assertEquals(ActionIntent.SET_REMINDER, ActionIntent.fromWire("set_reminder"))
        assertEquals(ActionIntent.GO_HOME, ActionIntent.fromWire("go_home"))
        assertEquals(ActionIntent.TAP_COORDINATE, ActionIntent.fromWire("tap_coordinate"))
        assertEquals(ActionIntent.NONE, ActionIntent.fromWire("unknown"))
    }

    @Test
    fun reminder_seconds_precision_is_preserved() {
        val tenSecAction = JarvisAction(
            intent = ActionIntent.SET_REMINDER,
            minutes = null,
            seconds = 10,
            message = "Su iç",
        )
        assertEquals(10_000L, tenSecAction.totalDelayMillis())
        assertEquals("10 sn", tenSecAction.formattedDurationLabel())

        val jsonText = tenSecAction.toPrettyJson()
        assertTrue(jsonText.contains("\"minutes\": null"))
        assertTrue(jsonText.contains("\"seconds\": 10"))
    }

    @Test
    fun zero_token_coordinate_matcher_handles_turkish_and_azeri_commands() {
        val coordinates = listOf(
            TapCoordinateItem(
                id = 1L,
                profilePackageName = "com.game.football",
                profileName = "Football Manager",
                label = "Transfer",
                xPercent = 0.82f,
                yPercent = 0.19f,
                xPx = 885,
                yPx = 456,
                createdAtMillis = 1000L,
            ),
            TapCoordinateItem(
                id = 2L,
                profilePackageName = "com.game.football",
                profileName = "Football Manager",
                label = "Kadro",
                xPercent = 0.25f,
                yPercent = 0.88f,
                xPx = 270,
                yPx = 2112,
                createdAtMillis = 1001L,
            ),
            TapCoordinateItem(
                id = 3L,
                profilePackageName = "com.game.football",
                profileName = "Football Manager",
                label = "Göndər",
                xPercent = 0.5f,
                yPercent = 0.5f,
                xPx = 540,
                yPx = 1200,
                createdAtMillis = 1002L,
            ),
        )

        assertEquals(
            "Transfer",
            CoordinateMatcher.findZeroTokenMatch("Transfer'e tıkla", coordinates)?.label
        )
        assertEquals(
            "Kadro",
            CoordinateMatcher.findZeroTokenMatch("Kadroya gir", coordinates)?.label
        )
        assertEquals(
            "Göndər",
            CoordinateMatcher.findZeroTokenMatch("göndər butonuna bas", coordinates)?.label
        )
        assertNull(
            CoordinateMatcher.findZeroTokenMatch("Bugün hava nasıl?", coordinates)
        )
        assertEquals(
            "Transfer",
            CoordinateMatcher.findByModelLabel("transfer", coordinates)?.label
        )
    }

    @Test
    fun wake_word_detection_and_trailing_command_work_accurately() {
        assertTrue(WakeWordManager.containsWakeWord("Jarvis"))
        assertTrue(WakeWordManager.containsWakeWord("Hey Jarvis YouTube aç"))
        assertTrue(WakeWordManager.containsWakeWord("carvis selam"))
        assertTrue(WakeWordManager.containsWakeWord("Yarvis"))

        assertFalse(WakeWordManager.containsWakeWord("Merhaba dünya"))
        assertFalse(WakeWordManager.containsWakeWord("Servis aracı geldi"))
        assertFalse(WakeWordManager.containsWakeWord(""))

        assertNull(WakeWordManager.extractTrailingCommand("Jarvis"))
        assertEquals("YouTube aç", WakeWordManager.extractTrailingCommand("Jarvis YouTube aç"))
        assertEquals("hava nasıl", WakeWordManager.extractTrailingCommand("Hey carvis, hava nasıl?"))
    }
}
