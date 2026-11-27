package com.example

import com.example.accessibility.JarvisAccessibilityService
import com.example.automation.ScheduledMessageParser
import com.example.gesture.GlobalAirGestureDirection
import com.example.gesture.GlobalAirGestureTracker
import com.example.model.ActionIntent
import com.example.model.HudTopicResolver
import com.example.model.JarvisAction
import com.example.model.JarvisMood
import com.example.model.TapCoordinateItem
import com.example.model.VisualCueResolver
import com.example.news.HandSwipeGesture
import com.example.news.NewsCategory
import com.example.news.RawRssArticle
import com.example.news.RssNewsFetcher
import com.example.news.SwipeGestureTracker
import com.example.system.CoordinateMatcher
import com.example.system.JarvisTtsVoiceHelper
import com.example.system.LocalQuickCommandMatcher
import com.example.system.ReminderDurationParser
import com.example.system.WakeWordManager
import com.example.system.WhatsAppMessageFilter
import com.example.webagent.WebDownloadAgent
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

    @Test
    fun news_natural_triggers_and_controls_match_accurately() {
        assertEquals(
            ActionIntent.OPEN_NEWS_BRIEFING,
            LocalQuickCommandMatcher.match("Bana haberleri de")?.intent
        )
        assertEquals(
            ActionIntent.OPEN_NEWS_BRIEFING,
            LocalQuickCommandMatcher.match("Gündemde ne var?")?.intent
        )
        assertEquals(
            ActionIntent.OPEN_NEWS_BRIEFING,
            LocalQuickCommandMatcher.match("Bugün neler oluyor?")?.intent
        )
        assertEquals(
            ActionIntent.OPEN_NEWS_BRIEFING,
            LocalQuickCommandMatcher.match("Şöyle bi haberlere bakalım")?.intent
        )

        // When NOT in news mode, "sonraki haber" doesn't hijack normal chat
        assertNull(LocalQuickCommandMatcher.match("Sonraki haber", isNewsModeActive = false))

        // When IN news mode, natural controls map cleanly
        val next = LocalQuickCommandMatcher.match("Sonraki haber", isNewsModeActive = true)
        assertEquals(ActionIntent.NEWS_CONTROL, next?.intent)
        assertEquals("next", next?.target)

        val prev = LocalQuickCommandMatcher.match("Önceki haber", isNewsModeActive = true)
        assertEquals(ActionIntent.NEWS_CONTROL, prev?.intent)
        assertEquals("previous", prev?.target)

        val detail = LocalQuickCommandMatcher.match("Detaylandır", isNewsModeActive = true)
        assertEquals(ActionIntent.NEWS_CONTROL, detail?.intent)
        assertEquals("detail", detail?.target)

        val close = LocalQuickCommandMatcher.match("Sohbete dön", isNewsModeActive = true)
        assertEquals(ActionIntent.NEWS_CONTROL, close?.intent)
        assertEquals("close", close?.target)
    }

    @Test
    fun air_swipe_gesture_tracker_detects_horizontal_swipes_and_enforces_cooldown() {
        val tracker = SwipeGestureTracker(cooldownMs = 1_200L)

        // Swipe right across 3 frames
        assertNull(tracker.onHandPosition(0.25f, 0.50f, 1_000L))
        assertNull(tracker.onHandPosition(0.42f, 0.51f, 1_120L))
        assertEquals(
            HandSwipeGesture.SWIPE_RIGHT,
            tracker.onHandPosition(0.62f, 0.50f, 1_240L)
        )

        // Within 1.2s cooldown -> suppressed even for large motion
        assertNull(tracker.onHandPosition(0.70f, 0.50f, 1_500L))
        assertNull(tracker.onHandPosition(0.45f, 0.50f, 1_650L))
        assertNull(tracker.onHandPosition(0.20f, 0.50f, 1_800L))

        // After cooldown expires -> SWIPE_LEFT detected
        assertNull(tracker.onHandPosition(0.75f, 0.50f, 2_500L))
        assertNull(tracker.onHandPosition(0.55f, 0.51f, 2_600L))
        assertEquals(
            HandSwipeGesture.SWIPE_LEFT,
            tracker.onHandPosition(0.35f, 0.50f, 2_720L)
        )

        // Vertical-dominant motion should not trigger horizontal swipe
        assertNull(tracker.onHandPosition(0.50f, 0.20f, 4_000L))
        assertNull(tracker.onHandPosition(0.58f, 0.50f, 4_120L))
        assertNull(tracker.onHandPosition(0.65f, 0.85f, 4_240L))
    }

    @Test
    fun news_category_and_rss_text_image_helpers_work() {
        assertEquals(NewsCategory.TEKNOLOJI, NewsCategory.fromWire("TEKNOLOJİ"))
        assertEquals(NewsCategory.SPOR, NewsCategory.fromWire("spor"))
        assertEquals(NewsCategory.DUNYA, NewsCategory.fromWire("Dünya"))
        assertEquals(NewsCategory.GUNDEM, NewsCategory.fromWire("bilinmeyen"))

        assertEquals(
            "https://cdn.example.com/photo.jpg",
            RssNewsFetcher.extractFirstImageFromHtml(
                "<p>Haber <img src=\"http://cdn.example.com/photo.jpg\" /></p>"
            )
        )
        assertEquals(
            "Merhaba dünya & gelişmeler",
            RssNewsFetcher.cleanHtmlText("<p>Merhaba   dünya &amp; <b>gelişmeler</b></p>")
        )
    }

    @Test
    fun rss_fetcher_helpers_filter_tracking_pixels_and_format_dates() {
        val htmlWithTracker = """
            <img src="https://cdn.example.com/pixel-1x1.gif"/>
            <img src="https://pixel.quantserve.com/pixel.gif"/>
            <img src="//cdn.example.com/match.png"/>
            <p>Kritik maçta &amp; üç puan.</p>
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/match.png",
            RssNewsFetcher.extractFirstImageFromHtml(htmlWithTracker)
        )
        assertEquals(
            "Kritik maçta & üç puan.",
            RssNewsFetcher.cleanHtmlText(htmlWithTracker)
        )
        assertEquals("Güncel", RssNewsFetcher.formatPublishedTime(""))
        assertEquals(5, RssNewsFetcher.formatPublishedTime("Fri, 27 Nov 2026 08:30:00 +0300").length)
    }

    @Test
    fun tts_voice_helper_ranks_male_turkish_voices_over_female_voices() {
        assertTrue(JarvisTtsVoiceHelper.isLikelyMaleVoiceName("tr-tr-x-ama-local"))
        assertTrue(JarvisTtsVoiceHelper.isLikelyMaleVoiceName("tr-tr-x-tmc-network"))
        assertTrue(JarvisTtsVoiceHelper.isLikelyMaleVoiceName("tr_TR#male_1-local"))
        assertFalse(JarvisTtsVoiceHelper.isLikelyMaleVoiceName("tr-tr-x-efu-local"))
        assertFalse(JarvisTtsVoiceHelper.isLikelyMaleVoiceName("tr_TR#female_1-local"))

        assertTrue(JarvisTtsVoiceHelper.isLikelyFemaleVoiceName("tr-tr-x-mfm-local"))
        assertTrue(JarvisTtsVoiceHelper.isLikelyFemaleVoiceName("tr-tr-x-cfs-network"))
        assertTrue(JarvisTtsVoiceHelper.isLikelyFemaleVoiceName("tr_TR#female_2-local"))
        assertFalse(JarvisTtsVoiceHelper.isLikelyFemaleVoiceName("tr-tr-x-ama-local"))

        val maleScore = JarvisTtsVoiceHelper.scoreVoiceForGender(
            voiceName = "tr-tr-x-ama-local",
            isLocal = true,
            preferMale = true,
        )
        val femaleScore = JarvisTtsVoiceHelper.scoreVoiceForGender(
            voiceName = "tr-tr-x-efu-local",
            isLocal = true,
            preferMale = true,
        )
        assertTrue(maleScore > femaleScore)
    }

    @Test
    fun topic_specific_news_requests_extract_clean_query_without_breaking_messaging() {
        val bjkNews = LocalQuickCommandMatcher.match(
            "Beşiktaş hakkında haber ver ne var ne yok",
            isNewsModeActive = true,
        )
        assertEquals(ActionIntent.OPEN_NEWS_BRIEFING, bjkNews?.intent)
        assertEquals("Beşiktaş", bjkNews?.target)

        val aiNews = LocalQuickCommandMatcher.match("Yapay zeka gelişmeleri hakkında haberleri aç")
        assertEquals(ActionIntent.OPEN_NEWS_BRIEFING, aiNews?.intent)
        assertEquals("Yapay zeka", aiNews?.target)

        val economyNews = LocalQuickCommandMatcher.match("Ekonomi haberlerini anlat")
        assertEquals(ActionIntent.OPEN_NEWS_BRIEFING, economyNews?.intent)
        assertEquals("Ekonomi", economyNews?.target)

        val generalNews = LocalQuickCommandMatcher.match("Bana gündemden bahset")
        assertEquals(ActionIntent.OPEN_NEWS_BRIEFING, generalNews?.intent)
        assertEquals("", generalNews?.target)

        // Person notification should NOT hijack into News Mode
        assertNull(LocalQuickCommandMatcher.match("Ahmet'e haber ver toplantı ertelendi"))

        val sample = RawRssArticle(
            title = "Beşiktaş'ta yeni teknik direktör gündemi",
            description = "Siyah beyazlı ekipte maç hazırlıkları sürüyor.",
            sourceName = "TRT Spor",
            publishedAtText = "12:30",
            link = "https://example.com",
            imageUrl = null,
            defaultCategory = NewsCategory.SPOR,
        )
        assertTrue(RssNewsFetcher.matchesTopic(sample, "Beşiktaş"))
        assertFalse(RssNewsFetcher.matchesTopic(sample, "Ekonomi"))
        assertEquals(NewsCategory.SPOR, RssNewsFetcher.inferCategoryForTopic("Beşiktaş"))
        assertEquals(NewsCategory.TEKNOLOJI, RssNewsFetcher.inferCategoryForTopic("Yapay zeka"))
    }

    @Test
    fun visual_cue_resolver_produces_ordered_scene_cues_from_ai_and_narration() {
        val cues = VisualCueResolver.resolveCues(
            aiCues = emptyList(),
            text = "Beşiktaş bugün stadyumda kavga etti ve gerilim yükseldi.",
            actionIntent = null,
        )
        assertEquals(
            listOf("eagle_crest", "stadium", "fist_impact"),
            cues.map { it.key },
        )

        val aiDriven = VisualCueResolver.resolveCues(
            aiCues = listOf("rocket_launch", "globe_network"),
            text = "Yeni uydu fırlatıldı.",
            actionIntent = null,
        )
        assertEquals("rocket_launch", aiDriven.first().key)
        assertEquals("globe_network", aiDriven[1].key)

        val actionFallback = VisualCueResolver.resolveCues(
            aiCues = emptyList(),
            text = "Tamamlandı.",
            actionIntent = ActionIntent.WEB_DOWNLOAD_AGENT,
        )
        assertEquals(listOf("download_file"), actionFallback.map { it.key })
    }

    @Test
    fun scheduled_message_parser_and_quick_matcher_extract_app_recipients_message_and_time() {
        val cmd1 = ScheduledMessageParser.parse(
            "Saat 23:00'ten sonra TikTok'ta Ahmet ve Ayşe'ye '.' mesajı at"
        )
        assertEquals("TikTok", cmd1?.appName)
        assertEquals("com.zhiliaoapp.musically", cmd1?.appPackage)
        assertEquals(listOf("Ahmet", "Ayşe"), cmd1?.recipients)
        assertEquals(".", cmd1?.messageText)
        assertEquals(23, cmd1?.hour)
        assertEquals(0, cmd1?.minute)
        assertTrue(cmd1?.repeatDaily == true)

        val cmd2 = ScheduledMessageParser.parse(
            "22:30'da WhatsApp'ta Ali'ye \"Selam nasılsın\" gönder tek sefer"
        )
        assertEquals("WhatsApp", cmd2?.appName)
        assertEquals("com.whatsapp", cmd2?.appPackage)
        assertEquals(listOf("Ali"), cmd2?.recipients)
        assertEquals("Selam nasılsın", cmd2?.messageText)
        assertEquals(22, cmd2?.hour)
        assertEquals(30, cmd2?.minute)
        assertFalse(cmd2?.repeatDaily ?: true)

        val quickSched = LocalQuickCommandMatcher.match(
            "Saat 23:00'ten sonra TikTok'ta Ahmet ve Ayşe'ye '.' mesajı at"
        )
        assertEquals(ActionIntent.SCHEDULE_MESSAGE, quickSched?.intent)

        val quickWeb = LocalQuickCommandMatcher.match(
            "Jarvis f-droid.org sitesine gir VLC APK dosyasını bulup telefona indir"
        )
        assertEquals(ActionIntent.WEB_DOWNLOAD_AGENT, quickWeb?.intent)

        // TTS voice pack phrase should NOT trigger web download agent
        assertNull(LocalQuickCommandMatcher.match("Ses paketi indir"))
    }

    @Test
    fun web_download_agent_helpers_parse_score_and_evaluate_safety_accurately() {
        val parsed = WebDownloadAgent.parseDownloadRequest(
            "Jarvis f-droid.org sitesine gir VLC APK indir"
        )
        assertEquals("https://f-droid.org", parsed.explicitUrl)
        assertEquals("apk", parsed.preferredExtension)
        assertTrue(parsed.queryTokens.contains("vlc"))

        val html = """
            <html><body>
              <a href="/img/logo.png">Logo</a>
              <a href="/repo/org.videolan.vlc_130500.apk">VLC APK İndir</a>
              <a href="/docs/guide.pdf">Kullanım Kılavuzu PDF</a>
            </body></html>
        """.trimIndent()
        val links = WebDownloadAgent.extractHtmlLinks("https://f-droid.org/packages/org.videolan.vlc/", html)
        assertEquals(3, links.size)

        val apkScore = WebDownloadAgent.scoreCandidateLink(
            href = links[1].first,
            anchorText = links[1].second,
            queryTokens = listOf("vlc"),
            preferredExt = "apk",
        )
        val imgScore = WebDownloadAgent.scoreCandidateLink(
            href = links[0].first,
            anchorText = links[0].second,
            queryTokens = listOf("vlc"),
            preferredExt = "apk",
        )
        assertTrue(apkScore > 100)
        assertTrue(imgScore < 0)

        val (trustedRequiresConfirm, _) = WebDownloadAgent.evaluateSafety(
            downloadUrl = "https://f-droid.org/repo/app.apk",
            fileName = "app.apk",
            extension = "apk",
        )
        assertFalse(trustedRequiresConfirm)

        val (untrustedRequiresConfirm, _) = WebDownloadAgent.evaluateSafety(
            downloadUrl = "https://random-mirror.example/app.apk",
            fileName = "app.apk",
            extension = "apk",
        )
        assertTrue(untrustedRequiresConfirm)
    }

    @Test
    fun global_air_gesture_tracker_detects_4_directions_and_enforces_cooldown_and_presence() {
        val tracker = GlobalAirGestureTracker()

        // Hand enters frame -> tracked
        val f1 = tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.75f, timestampMs = 1_000L)
        assertTrue(f1.isHandTracked)
        assertNull(f1.gesture)

        // Hand moves UP (y decreases) -> SWIPE_UP
        tracker.onFrame(hasHand = true, userSpaceX = 0.51f, y = 0.62f, timestampMs = 1_120L)
        val up = tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.50f, timestampMs = 1_240L)
        assertEquals(GlobalAirGestureDirection.SWIPE_UP, up.gesture)
        assertTrue(up.isHandTracked)

        // Within 1.1s cooldown -> suppressed
        tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.30f, timestampMs = 1_500L)
        tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.48f, timestampMs = 1_620L)
        val suppressed = tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.70f, timestampMs = 1_740L)
        assertNull(suppressed.gesture)

        // After 1.1s cooldown -> SWIPE_DOWN (y increases)
        tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.32f, timestampMs = 2_450L)
        tracker.onFrame(hasHand = true, userSpaceX = 0.51f, y = 0.45f, timestampMs = 2_560L)
        val down = tracker.onFrame(hasHand = true, userSpaceX = 0.50f, y = 0.58f, timestampMs = 2_680L)
        assertEquals(GlobalAirGestureDirection.SWIPE_DOWN, down.gesture)

        // After cooldown -> SWIPE_RIGHT
        tracker.onFrame(hasHand = true, userSpaceX = 0.25f, y = 0.50f, timestampMs = 3_900L)
        tracker.onFrame(hasHand = true, userSpaceX = 0.40f, y = 0.51f, timestampMs = 4_020L)
        val right = tracker.onFrame(hasHand = true, userSpaceX = 0.56f, y = 0.50f, timestampMs = 4_140L)
        assertEquals(GlobalAirGestureDirection.SWIPE_RIGHT, right.gesture)

        // After cooldown -> SWIPE_LEFT
        tracker.onFrame(hasHand = true, userSpaceX = 0.75f, y = 0.50f, timestampMs = 5_400L)
        tracker.onFrame(hasHand = true, userSpaceX = 0.58f, y = 0.50f, timestampMs = 5_520L)
        val left = tracker.onFrame(hasHand = true, userSpaceX = 0.42f, y = 0.51f, timestampMs = 5_640L)
        assertEquals(GlobalAirGestureDirection.SWIPE_LEFT, left.gesture)

        // Hand disappears -> holds for <700ms, then expires
        val briefDrop = tracker.onFrame(hasHand = false, userSpaceX = 0f, y = 0f, timestampMs = 5_900L)
        assertTrue(briefDrop.isHandTracked)
        val expiredDrop = tracker.onFrame(hasHand = false, userSpaceX = 0f, y = 0f, timestampMs = 6_400L)
        assertFalse(expiredDrop.isHandTracked)
    }

    @Test
    fun global_air_gesture_voice_commands_and_video_package_filter_work() {
        val offCmd = LocalQuickCommandMatcher.match("Jarvis, el kontrolünü kapat")
        assertEquals(ActionIntent.AIR_GESTURE_CONTROL, offCmd?.intent)
        assertEquals("off", offCmd?.target)

        val stopCmd = LocalQuickCommandMatcher.match("El hareketiyle kaydırmayı durdur")
        assertEquals(ActionIntent.AIR_GESTURE_CONTROL, stopCmd?.intent)
        assertEquals("off", stopCmd?.target)

        val onCmd = LocalQuickCommandMatcher.match("El kontrolünü aç")
        assertEquals(ActionIntent.AIR_GESTURE_CONTROL, onCmd?.intent)
        assertEquals("on", onCmd?.target)

        val videoCmd = LocalQuickCommandMatcher.match("Sadece TikTok'ta el kontrolünü aç")
        assertEquals(ActionIntent.AIR_GESTURE_CONTROL, videoCmd?.intent)
        assertEquals("on_video", videoCmd?.target)

        assertTrue(JarvisAccessibilityService.isVideoFeedPackage("com.zhiliaoapp.musically"))
        assertTrue(JarvisAccessibilityService.isVideoFeedPackage("com.instagram.android"))
        assertTrue(JarvisAccessibilityService.isVideoFeedPackage("com.google.android.youtube"))
        assertFalse(JarvisAccessibilityService.isVideoFeedPackage("com.whatsapp"))
        assertFalse(JarvisAccessibilityService.isVideoFeedPackage("com.android.settings"))
    }

    @Test
    fun open_and_play_content_intent_and_matcher_work_correctly() {
        val spotifyCmd = LocalQuickCommandMatcher.match("Spotify'da Duman aç")
        assertEquals(ActionIntent.OPEN_AND_PLAY_CONTENT, spotifyCmd?.intent)
        assertEquals("spotify:Duman", spotifyCmd?.target)

        val ytCmd = LocalQuickCommandMatcher.match("YouTube'da bilim videosu başlat")
        assertEquals(ActionIntent.OPEN_AND_PLAY_CONTENT, ytCmd?.intent)
        assertEquals("youtube:bilim videosu", ytCmd?.target)

        val ttCmd = LocalQuickCommandMatcher.match("TikTok'ta eğlenceli videolar aç")
        assertEquals(ActionIntent.OPEN_AND_PLAY_CONTENT, ttCmd?.intent)
        assertEquals("tiktok:eğlenceli videolar", ttCmd?.target)

        val moodMusicCmd = LocalQuickCommandMatcher.match("Bana moduma uygun bir şeyler aç")
        assertEquals(ActionIntent.OPEN_AND_PLAY_CONTENT, moodMusicCmd?.intent)
        assertTrue(moodMusicCmd?.target?.startsWith("default:") == true)

        val header = HudTopicResolver.resolveHeader(
            isListening = false,
            isProcessing = false,
            hasError = false,
            hasProactiveAlert = false,
            latestActionIntent = ActionIntent.OPEN_AND_PLAY_CONTENT,
            aiTopicTitle = "",
        )
        assertEquals("MEDYA // OYNATICI", header)

        val mood = HudTopicResolver.moodForAction(ActionIntent.OPEN_AND_PLAY_CONTENT, JarvisMood.NEUTRAL)
        assertEquals(JarvisMood.ENERGETIC, mood)
    }
}
