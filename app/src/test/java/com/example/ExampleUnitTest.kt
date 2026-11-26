package com.example

import com.example.model.ActionIntent
import com.example.model.HudTopicResolver
import com.example.model.JarvisAction
import com.example.model.JarvisMood
import com.example.model.TapCoordinateItem
import com.example.system.CoordinateMatcher
import com.example.system.LocalQuickCommandMatcher
import com.example.system.ReminderDurationParser
import com.example.system.WakeWordManager
import com.example.system.WhatsAppMessageFilter
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
        assertEquals(ActionIntent.TORCH_CONTROL, ActionIntent.fromWire("torch_control"))
        assertEquals(ActionIntent.VOLUME_CONTROL, ActionIntent.fromWire("volume_control"))
        assertEquals(ActionIntent.BATTERY_STATUS, ActionIntent.fromWire("battery_status"))
        assertEquals(ActionIntent.OPEN_SYSTEM_SETTINGS, ActionIntent.fromWire("open_system_settings"))
        assertEquals(ActionIntent.CAPTURE_SCREEN_NOTE, ActionIntent.fromWire("capture_screen_note"))
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

    @Test
    fun whatsapp_message_filter_rejects_chatter_and_allows_important_messages() {
        assertTrue(WhatsAppMessageFilter.isWhatsAppPackage("com.whatsapp"))
        assertTrue(WhatsAppMessageFilter.isWhatsAppPackage("com.whatsapp.w4b"))
        assertFalse(WhatsAppMessageFilter.isWhatsAppPackage("com.instagram.android"))

        // Noise / chatter pre-filtered with zero API calls
        assertFalse(WhatsAppMessageFilter.shouldAnalyzeWithAi("Ahmet", "tamam"))
        assertFalse(WhatsAppMessageFilter.shouldAnalyzeWithAi("Ahmet", "hahaha"))
        assertFalse(WhatsAppMessageFilter.shouldAnalyzeWithAi("Ahmet", "👍👌"))
        assertFalse(WhatsAppMessageFilter.shouldAnalyzeWithAi("WhatsApp", "5 yeni mesaj"))
        assertFalse(WhatsAppMessageFilter.shouldAnalyzeWithAi("Ayşe", "2 sohbetten 4 mesaj"))
        assertFalse(WhatsAppMessageFilter.shouldAnalyzeWithAi("Mehmet", "Bu mesaj silindi."))

        // Important or urgent messages allowed through to Gemini
        assertTrue(WhatsAppMessageFilter.shouldAnalyzeWithAi("Ahmet", "Acil!"))
        assertTrue(
            WhatsAppMessageFilter.shouldAnalyzeWithAi(
                "Müdür",
                "Yarınki saat 10:00 toplantısı 14:30'a alındı, sunumu güncelleyebilir misin?"
            )
        )
    }

    @Test
    fun reminder_duration_parser_extracts_exact_durations_and_clock_times() {
        val threeMins = ReminderDurationParser.parse("3 dk sonra fırını kapatmayı hatırlat")
        assertEquals(3, threeMins?.minutes)
        assertNull(threeMins?.seconds)
        assertEquals(180_000L, threeMins?.totalMillis)

        val twentySecs = ReminderDurationParser.parse("20 saniye sonra bana haber ver")
        assertNull(twentySecs?.minutes)
        assertEquals(20, twentySecs?.seconds)
        assertEquals(20_000L, twentySecs?.totalMillis)

        val halfHour = ReminderDurationParser.parse("yarım saat sonra toplantı var")
        assertEquals(30, halfHour?.minutes)

        val onePointFiveHours = ReminderDurationParser.parse("1.5 saat sonra su içmeyi hatırlat")
        assertEquals(90, onePointFiveHours?.minutes)

        val bukukHours = ReminderDurationParser.parse("bir buçuk saat sonra ilacı hatırlat")
        assertEquals(90, bukukHours?.minutes)

        val wordMinutes = ReminderDurationParser.parse("beş dakika sonra beni uyar")
        assertEquals(5, wordMinutes?.minutes)

        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 26, 14, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val sameDayTarget = ReminderDurationParser.parse("saat 16:30'da toplantıyı hatırlat", cal.timeInMillis)
        assertEquals(150, sameDayTarget?.minutes)

        val nextDayTarget = ReminderDurationParser.parse("saat 09:00'da alarm kur", cal.timeInMillis)
        assertEquals(19 * 60, nextDayTarget?.minutes)
    }

    @Test
    fun local_quick_command_matcher_handles_notes_torch_battery_and_settings() {
        assertEquals(
            ActionIntent.CAPTURE_SCREEN_NOTE,
            LocalQuickCommandMatcher.match("Jarvis bu ekranı not al")?.intent
        )
        assertEquals(
            ActionIntent.CAPTURE_SCREEN_NOTE,
            LocalQuickCommandMatcher.match("Şu ekranı not al")?.intent
        )
        assertEquals(
            ActionIntent.CAPTURE_SCREEN_NOTE,
            LocalQuickCommandMatcher.match("Bunu not defterine kaydet")?.intent
        )

        val torchOn = LocalQuickCommandMatcher.match("Feneri aç")
        assertEquals(ActionIntent.TORCH_CONTROL, torchOn?.intent)
        assertEquals("on", torchOn?.target)

        val torchOff = LocalQuickCommandMatcher.match("Feneri kapat")
        assertEquals(ActionIntent.TORCH_CONTROL, torchOff?.intent)
        assertEquals("off", torchOff?.target)

        assertEquals(
            ActionIntent.BATTERY_STATUS,
            LocalQuickCommandMatcher.match("Şarjım kaç?")?.intent
        )

        val wifiSettings = LocalQuickCommandMatcher.match("Wi-Fi ayarlarını aç")
        assertEquals(ActionIntent.OPEN_SYSTEM_SETTINGS, wifiSettings?.intent)
        assertEquals("wifi", wifiSettings?.target)

        assertNull(LocalQuickCommandMatcher.match("Türkiye'nin başkenti neresidir?"))
    }

    @Test
    fun jarvis_mood_and_hud_topic_resolver_map_states_accurately() {
        assertEquals(JarvisMood.NEUTRAL, JarvisMood.fromWire("unknown"))
        assertEquals(JarvisMood.SERIOUS, JarvisMood.fromWire("SERIOUS"))
        assertEquals(JarvisMood.ANALYTICAL, JarvisMood.fromWire("analytical"))
        assertEquals(JarvisMood.ENERGETIC, JarvisMood.fromWire("energetic"))
        assertEquals(JarvisMood.ALERT, JarvisMood.fromWire("alert"))

        assertEquals(
            "SİSTEM UYARISI // KRİTİK",
            HudTopicResolver.resolveHeader(
                isListening = false,
                isProcessing = false,
                hasError = true,
                hasProactiveAlert = true,
                latestActionIntent = ActionIntent.CAPTURE_SCREEN_NOTE,
                aiTopicTitle = "STRATEJİ ANALİZİ",
            )
        )
        assertEquals(
            "SES KANALI // DİNLENİYOR",
            HudTopicResolver.resolveHeader(
                isListening = true,
                isProcessing = false,
                hasError = false,
                hasProactiveAlert = false,
                latestActionIntent = null,
                aiTopicTitle = "",
            )
        )
        assertEquals(
            "İLETİŞİM PROTOKOLÜ // TASLAK",
            HudTopicResolver.resolveHeader(
                isListening = false,
                isProcessing = false,
                hasError = false,
                hasProactiveAlert = true,
                latestActionIntent = null,
                aiTopicTitle = "",
            )
        )
        assertEquals(
            "GÖRSEL TARAMA // ARŞİV KAYDI",
            HudTopicResolver.resolveHeader(
                isListening = false,
                isProcessing = false,
                hasError = false,
                hasProactiveAlert = false,
                latestActionIntent = ActionIntent.CAPTURE_SCREEN_NOTE,
                aiTopicTitle = "",
            )
        )
        assertEquals(
            "HEDEF KİLİTLEME // OTOMASYON",
            HudTopicResolver.resolveHeader(
                isListening = false,
                isProcessing = false,
                hasError = false,
                hasProactiveAlert = false,
                latestActionIntent = ActionIntent.TAP_COORDINATE,
                aiTopicTitle = "",
            )
        )
        assertEquals(
            "SİSTEM DURUMU // ANALİZ",
            HudTopicResolver.resolveHeader(
                isListening = false,
                isProcessing = false,
                hasError = false,
                hasProactiveAlert = false,
                latestActionIntent = ActionIntent.BATTERY_STATUS,
                aiTopicTitle = "",
            )
        )
        assertEquals(
            "STRATEJİ ANALİZİ",
            HudTopicResolver.resolveHeader(
                isListening = false,
                isProcessing = false,
                hasError = false,
                hasProactiveAlert = false,
                latestActionIntent = null,
                aiTopicTitle = "Strateji Analizi",
            )
        )

        assertEquals(
            JarvisMood.ANALYTICAL,
            HudTopicResolver.moodForAction(ActionIntent.CAPTURE_SCREEN_NOTE, JarvisMood.NEUTRAL)
        )
        assertEquals(
            JarvisMood.ENERGETIC,
            HudTopicResolver.moodForAction(ActionIntent.TAP_COORDINATE, JarvisMood.NEUTRAL)
        )
        assertEquals(
            JarvisMood.SERIOUS,
            HudTopicResolver.moodForAction(ActionIntent.SET_REMINDER, JarvisMood.NEUTRAL)
        )
    }
}
