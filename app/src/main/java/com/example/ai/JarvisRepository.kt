package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ActionIntent
import com.example.model.InstalledAppInfo
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
import com.example.model.JarvisMood
import com.example.news.NewsBriefingItem
import com.example.news.NewsCategory
import com.example.news.RawRssArticle
import com.example.system.ReminderDurationParser
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "JarvisRepository"
private const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
private const val FALLBACK_MODEL = "gemini-3.8-flash"
private const val ATTEMPT_TIMEOUT_MS = 30_000L

data class JarvisAiReply(
    val text: String,
    val ttsSummary: String,
    val action: JarvisAction?,
    val mood: JarvisMood = JarvisMood.NEUTRAL,
    val topicTitle: String = "",
    val visualCues: List<String> = emptyList(),
)

data class WhatsAppEvaluation(
    val importanceScore: Int,
    val importanceReason: String,
    val summary: String,
    val draftReply: String,
)

data class ScreenNoteAiResult(
    val title: String,
    val content: String,
)

class JarvisRepository {

    private val jarvisSchema = Schema.obj(
        properties = mapOf(
            "mode" to Schema.enumeration(
                values = listOf("action", "answer"),
                description = "MOD 1 için 'action' (aksiyon/komut niyeti), MOD 2 için 'answer' (kısa yanıt veya üretilen içerik)."
            ),
            "reply" to Schema.string(
                description = "MOD 1'de tek satırlık kısa onay ifadesi; MOD 2'de kısa, net yanıt veya istenen temiz metin."
            ),
            "ttsSummary" to Schema.string(
                description = "Sesli okuma (TTS) için 1-2 cümlelik çok kısa Türkçe özet."
            ),
            "intent" to Schema.enumeration(
                values = listOf(
                    "open_app",
                    "open_url",
                    "search_youtube",
                    "set_reminder",
                    "go_home",
                    "tap_coordinate",
                    "torch_control",
                    "volume_control",
                    "battery_status",
                    "open_system_settings",
                    "capture_screen_note",
                    "open_news_briefing",
                    "news_control",
                    "web_download_agent",
                    "schedule_message",
                    "air_gesture_control",
                    "none"
                ),
                description = "Aksiyon niyeti."
            ),
            "target" to Schema.string(
                description = "open_app için uygulama adı; open_url için URL; search_youtube için sorgu; tap_coordinate için kayıtlı etiket; torch_control için 'on'|'off'|'toggle'; volume_control için '<media|ring|notification|alarm>:<up|down|mute|unmute|vibrate|max|set>:<0-100?>'; open_system_settings için 'wifi'|'bluetooth'|'sound'|'accessibility'|'notifications'|'battery'|'display'|'location'|'settings'; open_news_briefing için genel gündemde boş string '', belirli bir konu/takım/kişi/alan istendiyse sadece temiz arama konusu (ör. 'Beşiktaş', 'ekonomi', 'yapay zeka'); news_control için 'next'|'previous'|'detail'|'pause'|'resume'|'close'; web_download_agent için aranacak/indirilecek site URL'si, uygulama veya dosya adı (ör. 'F-Droid APK', 'https://f-droid.org'); schedule_message için '<uygulama>:<virgülle_alıcılar>:<HH:MM>' (ör. 'TikTok:Ahmet,Ayşe:23:00'); air_gesture_control için 'on'|'off'|'on_video'|'on_all'; diğerlerinde boş string."
            ),
            "minutes" to Schema.integer(
                description = "set_reminder için kullanıcının istediği TAM dakika sayısı (ör. '3 dakika' -> 3, 'yarım saat' -> 30, '1 saat' -> 60). Kullanıcı SADECE saniye belirttiyse null bırak.",
                nullable = true
            ),
            "seconds" to Schema.integer(
                description = "set_reminder için saniye sayısı (ör. '10 saniye sonra' -> 10). Saniye yoksa null bırak.",
                nullable = true
            ),
            "message" to Schema.string(
                description = "set_reminder için hatırlatma metni; schedule_message için gönderilecek tam mesaj metni (ör. '.'); diğer durumlarda boş string."
            ),
            "mood" to Schema.enumeration(
                values = listOf("neutral", "serious", "analytical", "energetic", "alert"),
                description = "Yanıtın tonu/duygusu: neutral (normal/bilgi), serious (odaklı/uyarı/planlama), analytical (düşünceli/teknik/analiz), energetic (onay/esprili/hızlı işlem), alert (kritik uyarı/hata)."
            ),
            "topic_title" to Schema.string(
                description = "Konuşulan konuya uygun 2-3 kelimelik kısa büyük harfli HUD başlığı (ör. STRATEJİ ANALİZİ, HİKAYE KURGUSU, SİSTEM DURUMU, İLETİŞİM PROTOKOLÜ)."
            ),
            "visual_cues" to Schema.array(
                items = Schema.string(),
                description = "Yanıt veya anlatım sırasında parçacık bulutunun yanında sırayla canlandırılacak 1-4 sembolik sahne anahtarı. Tercih edilen anahtarlar: eagle_crest, stadium, fist_impact, ball_sport, trophy_cup, download_file, globe_network, shield_alert, message_send, timer_clock, weather_storm, weather_sun, rocket_launch, brain_spark, money_chart, music_wave, code_terminal, flashlight_beam, battery_power, note_scroll, heart_health, flame_energy."
            ),
        ),
        optionalProperties = listOf("minutes", "seconds", "mood", "topic_title", "visual_cues")
    )

    private val schemaGenerationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = jarvisSchema
        temperature = 0.2f
    }

    private val primaryModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL,
            generationConfig = schemaGenerationConfig,
        )
    }

    private val fallbackModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL,
            generationConfig = schemaGenerationConfig,
        )
    }

    private val whatsAppEvaluationSchema = Schema.obj(
        properties = mapOf(
            "importanceScore" to Schema.integer(
                description = "1 (önemsiz sohbet/selamlaşma) ile 10 (çok acil/kritik iş, para, sağlık, toplantı, doğrudan önemli soru) arasında önem puanı."
            ),
            "importanceReason" to Schema.string(
                description = "Mesajın neden bu önem puanını aldığını açıklayan tek satırlık kısa Türkçe gerekçe."
            ),
            "summary" to Schema.string(
                description = "Mesajın kullanıcıya bildirilecek çok kısa 1 cümlelik Türkçe özeti."
            ),
            "draftReply" to Schema.string(
                description = "Kullanıcı adına gönderilmeye hazır, doğal, net ve kısa Türkçe WhatsApp cevap taslağı."
            ),
        )
    )

    private val whatsAppEvalConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = whatsAppEvaluationSchema
        temperature = 0.2f
    }

    private val whatsAppEvalModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL,
            generationConfig = whatsAppEvalConfig,
        )
    }

    private val screenNoteSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(
                description = "Not için 2-6 kelimelik kısa, açıklayıcı Türkçe başlık."
            ),
            "content" to Schema.string(
                description = "Ekrandan okunan veya düzenlenen temiz, okunabilir, eksiksiz not metni."
            ),
        )
    )

    private val screenNoteConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = screenNoteSchema
        temperature = 0.2f
    }

    private val screenNoteModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL,
            generationConfig = screenNoteConfig,
        )
    }

    private val newsBriefingSchema = Schema.obj(
        properties = mapOf(
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "source_index" to Schema.integer(
                            description = "Verilen ham haber listesindeki sıfır tabanlı indeks numarası."
                        ),
                        "category" to Schema.enumeration(
                            values = listOf("GÜNDEM", "TEKNOLOJİ", "SPOR", "DÜNYA"),
                            description = "Haberin kategorisi."
                        ),
                        "headline" to Schema.string(
                            description = "Tık tuzağı içermeyen, net, çarpıcı ve kısa Türkçe haber başlığı."
                        ),
                        "brief_summary" to Schema.string(
                            description = "JARVIS spiker üslubunda akıcı, 1-2 cümlelik kısa bülten özeti."
                        ),
                        "detailed_summary" to Schema.string(
                            description = "Kullanıcı 'detaylandır' dediğinde okunacak 3-4 cümlelik kapsamlı arka plan özeti."
                        ),
                    )
                ),
                description = "Tık tuzağı elendikten sonra seçilen en önemli 6 haber."
            )
        )
    )

    private val newsBriefingConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = newsBriefingSchema
        temperature = 0.2f
    }

    private val newsBriefingModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL,
            generationConfig = newsBriefingConfig,
        )
    }

    suspend fun askJarvis(
        userPrompt: String,
        userContext: String,
        recentMessages: List<JarvisMessage>,
        installedApps: List<InstalledAppInfo>,
        savedCoordinateLabels: List<String> = emptyList(),
        activeProfileLabel: String = "",
    ): Result<JarvisAiReply> = withContext(Dispatchers.IO) {
        val systemText = buildSystemPrompt(
            userContext = userContext,
            installedApps = installedApps,
            savedCoordinateLabels = savedCoordinateLabels,
            activeProfileLabel = activeProfileLabel,
        )
        val historyText = buildRecentHistoryBlock(recentMessages.takeLast(6))
        val fullInput = buildString {
            append(systemText)
            if (historyText.isNotBlank()) {
                append("\n\nSON KONUŞMA BAĞLAMI:\n")
                append(historyText)
            }
            append("\n\nKULLANICI GİRDİSİ:\n")
            append(userPrompt.trim())
        }

        val primaryResult = executeStreamWithTimeout(
            modelProvider = { primaryModel },
            modelLabel = PRIMARY_MODEL,
            promptText = fullInput,
            rawUserPrompt = userPrompt,
            timeoutMs = ATTEMPT_TIMEOUT_MS,
        )

        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        val primaryError = primaryResult.exceptionOrNull()
        if (primaryError is TimeoutCancellationException) {
            Log.w(TAG, "Primary model $PRIMARY_MODEL timed out; retrying with $FALLBACK_MODEL")
            val fallbackResult = executeStreamWithTimeout(
                modelProvider = { fallbackModel },
                modelLabel = FALLBACK_MODEL,
                promptText = fullInput,
                rawUserPrompt = userPrompt,
                timeoutMs = ATTEMPT_TIMEOUT_MS,
            )
            return@withContext fallbackResult.fold(
                onSuccess = { Result.success(it) },
                onFailure = { err ->
                    val message = if (err is TimeoutCancellationException) {
                        "Bağlantı zaman aşımına uğradı. İnternet bağlantını kontrol edip tekrar dene."
                    } else {
                        formatAiError(err)
                    }
                    Result.failure(Exception(message))
                }
            )
        }

        Result.failure(
            Exception(formatAiError(primaryError))
        )
    }

    private fun formatAiError(error: Throwable?): String {
        val raw = error?.localizedMessage.orEmpty()
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("default firebaseapp is not initialized") ||
                lower.contains("firebaseapp.initializeapp") ->
                "Firebase başlatılamadı: APK derlemesinde app/google-services.json eksik. GitHub Actions ile APK alıyorsanız app/google-services.json dosyasını repoya veya GOOGLE_SERVICES_JSON secret'ına ekleyin."
            lower.contains("403") ||
                lower.contains("app check") ||
                lower.contains("appcheck") ||
                lower.contains("attestation") ->
                "Firebase App Check doğrulaması reddedildi (403). Debug APK kullanıyorsan Ayarlar > Firebase App Check bölümünden Debug Token oluşturup Firebase Console'a ekle veya kayıtlı token'ı gir.${if (raw.isNotBlank()) "\nDetay: $raw" else ""}"
            lower.contains("network") || lower.contains("unable to resolve host") ->
                "İnternet bağlantısı kurulamadı. Bağlantını kontrol edip tekrar dene."
            raw.isNotBlank() -> raw
            else -> "JARVIS şu anda yanıt üretemedi."
        }
    }

    private suspend fun executeStreamWithTimeout(
        modelProvider: () -> GenerativeModel,
        modelLabel: String,
        promptText: String,
        rawUserPrompt: String,
        timeoutMs: Long,
    ): Result<JarvisAiReply> {
        val startMs = System.currentTimeMillis()
        val accumulated = StringBuilder()

        return try {
            val model = modelProvider()
            Log.d(TAG, "Dispatching JARVIS request on $modelLabel")
            withTimeout(timeoutMs) {
                model.generateContentStream(
                    content {
                        text(promptText)
                    }
                ).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                    }
                }
            }

            val rawJson = accumulated.toString().trim()
            if (rawJson.isBlank()) {
                return Result.failure(IllegalStateException("Model boş yanıt döndürdü."))
            }

            Log.d(TAG, "$modelLabel completed in ${System.currentTimeMillis() - startMs}ms")
            Result.success(parseStructuredJson(rawJson, rawUserPrompt))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "JARVIS inference timed out on $modelLabel after ${timeoutMs}ms", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "JARVIS inference failed on $modelLabel", e)
            Result.failure(e)
        }
    }

    private fun parseStructuredJson(rawJson: String, rawUserPrompt: String): JarvisAiReply {
        val json = JSONObject(rawJson)
        val mode = json.optString("mode", "answer").lowercase(Locale.ROOT)
        var reply = json.optString("reply", "").trim()
        var ttsSummary = json.optString("ttsSummary", "").trim()
        val intent = ActionIntent.fromWire(json.optString("intent", "none"))
        val target = json.optString("target", "").let {
            if (it == "null") "" else it.trim()
        }
        var minutes = if (json.has("minutes") && !json.isNull("minutes")) {
            json.optInt("minutes").takeIf { it > 0 }
        } else {
            null
        }
        var seconds = if (json.has("seconds") && !json.isNull("seconds")) {
            json.optInt("seconds").takeIf { it > 0 }
        } else {
            null
        }
        val message = json.optString("message", "").let {
            if (it == "null") "" else it.trim()
        }

        // Deterministic guard for reminder durations so user's exact requested minutes/seconds/hours are never replaced by 10m fallback
        if (intent == ActionIntent.SET_REMINDER) {
            val parsedDuration = ReminderDurationParser.parse(rawUserPrompt)
            if (parsedDuration != null && parsedDuration.totalMillis > 0L) {
                minutes = parsedDuration.minutes
                seconds = parsedDuration.seconds
            }
        }

        val action = if (mode == "action" && intent != ActionIntent.NONE) {
            JarvisAction(
                intent = intent,
                target = target,
                minutes = minutes,
                seconds = seconds,
                message = message,
            )
        } else {
            null
        }

        if (action != null && action.intent == ActionIntent.SET_REMINDER) {
            val label = action.formattedDurationLabel()
            val reminderText = action.message.ifBlank { action.target }
            if (reply.isBlank() || (minutes != 10 && reply.contains("10 dakika"))) {
                reply = if (reminderText.isNotBlank()) {
                    "$label sonrası için hatırlatıcı kuruldu: \"$reminderText\""
                } else {
                    "$label sonrası için hatırlatıcı kuruldu."
                }
                ttsSummary = reply
            }
        }

        val mood = JarvisMood.fromWire(json.optString("mood", "neutral"))
        val topicTitle = json.optString("topic_title", "").let {
            if (it == "null") "" else it.trim().uppercase(Locale("tr", "TR"))
        }
        val visualCues = mutableListOf<String>()
        val cuesArr = json.optJSONArray("visual_cues")
        if (cuesArr != null) {
            for (i in 0 until cuesArr.length()) {
                val cue = cuesArr.optString(i, "").trim()
                if (cue.isNotBlank() && cue != "null") {
                    visualCues.add(cue)
                }
            }
        }

        val finalReply = reply.ifBlank {
            if (action != null) "${action.intent.displayLabel} hazırlanıyor." else "Tamam."
        }
        return JarvisAiReply(
            text = finalReply,
            ttsSummary = ttsSummary.ifBlank { finalReply },
            action = action,
            mood = mood,
            topicTitle = topicTitle,
            visualCues = visualCues.distinct().take(4),
        )
    }

    private fun buildSystemPrompt(
        userContext: String,
        installedApps: List<InstalledAppInfo>,
        savedCoordinateLabels: List<String>,
        activeProfileLabel: String,
    ): String {
        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", Locale("tr", "TR"))
            .format(Date())
        val contextSection = userContext.trim().ifBlank {
            "Varsayılan: Kısa, net, Türkçe ve pratik ol."
        }
        val appsSummary = installedApps
            .take(35)
            .joinToString(", ") { it.label }
            .ifBlank { "Ayarlar, Kamera, Chrome, Saat, Takvim" }

        val coordsSection = if (savedCoordinateLabels.isNotEmpty()) {
            val profileNote = activeProfileLabel.ifBlank { "Aktif Profil" }
            "AKTİF OYUN/PROFİL ($profileNote) KAYITLI DOKUNMA ETİKETLERİ: ${savedCoordinateLabels.joinToString(", ")}"
        } else {
            "AKTİF OYUN KAYITLI NOKTALARI: Yok"
        }

        return """
            Sen kullanıcının kişisel, hızlı ve pratik yapay zeka asistanısın (JARVIS).
            Amacın uzun uzun konuşmak değil; kullanıcının söylediğini doğru anlamak, gerekirse doğrudan işlem/aksiyon niyeti çıkarmak, gerekirse kısa, net ve işe yarar içerik üretmektir.

            GENEL KURALLAR:
            1. Gereksiz gevezelik, yapay heyecan veya boş laf yapma. Net, direkt ve işe odaklı ol.
            2. Cevapları olabildiğince öz, okunaklı ve hızlı taranabilir tut. Metin üretimi (iş başvurusu, e-posta, özet) istendiğinde doğrudan kullanıma hazır temiz metni reply alanına yaz, ttsSummary alanında 1 cümlelik sesli özet ver.
            3. Sesle yazıya çevrilmiş metinlerdeki ufak yazım/noktalama hatalarını mantıksal olarak doğru tahmin et.
            4. Bilmediğin veya kesin olmayan bir şeyi uydurma; eksik bilgi varsa tek ve net bir soruyla sor.

            HATIRLATICI SÜRE KURALI (ÇOK ÖNEMLİ):
            - Kullanıcının belirttiği TAM süreyi çıkar; ASLA kafandan 10 dakikaya yuvarlama!
            - Saniye belirtirse (ör. "20 saniye sonra") minutes=null, seconds=20 ver.
            - Dakika belirtirse (ör. "3 dakika sonra", "15 dk") minutes=3 veya 15, seconds=null ver.
            - Saat belirtirse dakikaya çevir ("yarım saat" -> minutes=30, "1 saat" -> minutes=60, "1.5 saat" -> minutes=90).
            - Saat formatı verirse (ör. "saat 16:30'da") güncel saatten kalan dakikayı hesapla.
            - Sadece kullanıcı hiçbir süre vermediyse minutes=10 ver.

            DONANIM VE SİSTEM KONTROL KURALLARI:
            - Fener aç/kapat: intent="torch_control", target="on" | "off" | "toggle"
            - Ses seviyesi: intent="volume_control", target="<media|ring|notification|alarm>:<up|down|mute|unmute|vibrate|max|set>:<0-100?>"
              Örn: "medya sesini kıs" -> "media:down", "sesi %60 yap" -> "media:set:60", "telefonu sessize al" -> "ring:mute", "titreşime al" -> "ring:vibrate"
            - Şarj / pil durumu: intent="battery_status", target=""
            - Sistem ayarları aç (Wi-Fi, Bluetooth, Ses, Erişilebilirlik, Bildirim, Pil, Konum, Ekran): intent="open_system_settings", target="wifi"|"bluetooth"|"sound"|"accessibility"|"notifications"|"battery"|"location"|"display"|"settings"
            - Ekranı not al ("bu ekranı not al", "şu ekranı kaydet", "bunu not defterine kaydet"): intent="capture_screen_note", target=""
            - Haber / gündem aç ("bana haberleri de", "gündemden bahset", "gündemde ne var?", "Beşiktaş hakkında haber ver", "ekonomi haberleri"): intent="open_news_briefing". Genel gündem için target="". Kullanıcı belirli bir konu, takım, kişi veya alan hakkında haber istediyse target alanına SADECE temiz konuyu yaz (ör. "Beşiktaş", "ekonomi", "yapay zeka").
            - Haber kontrolü (kullanıcı haber sunumundayken "sonraki haber", "önceki haber", "bunu detaylandır", "duraklat", "devam et", "sohbete dön/kapat"): intent="news_control", target="next"|"previous"|"detail"|"pause"|"resume"|"close"
            - Otonom Web Gezintisi & Dosya/APK İndirme ("F-Droid sitesine gir APK indir", "şu PDF'i bulup telefona indir", "X uygulamasının APK'sını indir"): intent="web_download_agent", target="<hedef site URL'si veya uygulama/dosya adı>"
            - Mesaj Gönderme & Zamanlı Otomatik Mesaj ("WhatsApp'ta Ali'ye selam at", "5 dakika sonra TikTok'ta Ahmet'e '.' yaz", "Saat 23:00'da mesaj at", "TikTok'tan mesaj gönder"): KESİNLİKLE sohbet metni olarak yanıt verme! Her zaman intent="schedule_message", target="<uygulama>:<alıcı1,alıcı2>:<now | +Xm | HH:MM>", message="<gönderilecek mesaj>" döndür (ör. "WhatsApp:Ali:now", "TikTok:Ahmet:+5m", "WhatsApp::now"). Alıcı adı belirsiz veya yoksa alıcı kısmını boş bırak (uygulama içi kutucuk açılsın).
            - Uzaktan El Hareketiyle Telefon Kontrolü ("el kontrolünü aç", "el kontrolünü kapat", "el hareketiyle kaydırmayı durdur", "sadece TikTok'ta el kontrolünü aç"): intent="air_gesture_control", target="on"|"off"|"on_video"|"on_all"

            EKRAN NOKTASI TIKLAMA KURALI:
            - Kullanıcı ekrandaki bir butona/sekmeye tıklamak, basmak veya girmek istiyorsa ve yukarıdaki KAYITLI DOKUNMA ETİKETLERİ listesinde karşılık gelen bir etiket varsa mode="action", intent="tap_coordinate", target="<listeden birebir etiket>" seç!

            GÜNCEL ZAMAN: $nowFormatted
            CİHAZDAKİ UYGULAMALAR: $appsSummary
            $coordsSection
            KULLANICI BAĞLAMI: $contextSection

            ÇIKTI MODLARI, HUD ETİKETLERİ VE CANLI GÖRSEL SAHNELER:
            - MOD 1 (mode="action"): open_app, open_url, search_youtube, set_reminder, go_home, tap_coordinate, torch_control, volume_control, battery_status, open_system_settings, capture_screen_note, open_news_briefing, news_control, web_download_agent, schedule_message veya air_gesture_control niyeti varsa mode="action", reply alanına tek satır onay yaz.
            - MOD 2 (mode="answer"): Bilgi sorusu, özet, metin üretimi veya sohbetse mode="answer", intent="none" seç.
            - mood alanına yanıtın duygusunu (neutral, serious, analytical, energetic, alert) yaz.
            - topic_title alanına konuyu özetleyen 2-3 kelimelik kısa büyük harfli HUD başlığı yaz (ör. "STRATEJİ ANALİZİ", "GÜNLÜK PLAN", "SİSTEM DURUMU").
            - visual_cues alanına konuşulan/anlatılan sahneyi canlı sembollerle gösterecek 1-4 görsel anahtar yaz (ör. Beşiktaş stadyumda maç/kavga anlatıyorsan ["eagle_crest", "stadium", "fist_impact"]; indirme ise ["globe_network", "download_file"]; zamanlı mesaj ise ["timer_clock", "message_send"]).
        """.trimIndent()
    }

    suspend fun curateNewsBriefing(
        rawArticles: List<RawRssArticle>,
        userContext: String,
        topicQuery: String = "",
    ): Result<List<NewsBriefingItem>> = withContext(Dispatchers.IO) {
        if (rawArticles.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("RSS kaynaklarından haber alınamadı."))
        }

        val cleanTopic = topicQuery.trim()
        val candidatesText = buildString {
            rawArticles.forEachIndexed { idx, article ->
                append("[$idx] (${article.defaultCategory.label} | ${article.sourceName}) ")
                append("Başlık: ${article.title}")
                if (article.description.isNotBlank()) {
                    append(" | Özet: ${article.description.take(220)}")
                }
                append('\n')
            }
        }

        val contextHint = userContext.trim().ifBlank { "Objektif, sade ve akıcı Türkçe haber sunumu." }
        val focusInstructions = if (cleanTopic.isNotEmpty()) {
            """
            KULLANICI ÖZEL KONU İSTEDİ: "$cleanTopic"
            1. Tık tuzağı (clickbait) ve alakasız içerikleri kesinlikle ele; YALNIZCA "$cleanTopic" konusuyla doğrudan ilgili en güncel ve önemli en fazla 6 haberi seç.
            2. "$cleanTopic" ile ilgisiz genel haberleri KESİNLİKLE seçme.
            """.trimIndent()
        } else {
            """
            1. Tık tuzağı (clickbait), abartılı magazin veya boş içerikleri kesinlikle ele.
            2. Farklı kategorilerden (GÜNDEM, TEKNOLOJİ, SPOR, DÜNYA) dengeli biçimde EN ÖNEMLİ 6 haberi seç.
            """.trimIndent()
        }

        val prompt = """
            Sen JARVIS'in akıllı haber editörü ve baş spikerisin.
            Aşağıdaki ham RSS haber listesinden:
            $focusInstructions
            3. Her seçilen haber için:
               - source_index: Aşağıdaki listedeki [indeks] numarasını birebir koru.
               - category: GÜNDEM, TEKNOLOJİ, SPOR veya DÜNYA değerlerinden biri.
               - headline: Tık tuzağından arındırılmış, net, okunaklı Türkçe başlık.
               - brief_summary: Spiker tarzında akıcı, 1-2 cümlelik sesli okunmaya uygun kısa bülten özeti.
               - detailed_summary: Kullanıcı "detaylandır" dediğinde sunulacak 3-4 cümlelik daha kapsamlı özet (yalnızca verilen metne dayan, bilgi uydurma).

            KULLANICI TERCİHİ: $contextHint

            HAM HABER LİSTESİ:
            $candidatesText
        """.trimIndent()

        try {
            val accumulated = StringBuilder()
            withTimeout(ATTEMPT_TIMEOUT_MS) {
                newsBriefingModel.generateContentStream(
                    content {
                        text(prompt)
                    }
                ).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                    }
                }
            }

            val raw = accumulated.toString().trim()
            if (raw.isBlank()) {
                return@withContext Result.success(fallbackCurateNews(rawArticles, cleanTopic))
            }

            val root = JSONObject(raw)
            val arr = root.optJSONArray("items") ?: JSONArray()
            val curated = mutableListOf<NewsBriefingItem>()
            val usedIndices = HashSet<Int>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val srcIdx = obj.optInt("source_index", -1)
                if (srcIdx !in rawArticles.indices || !usedIndices.add(srcIdx)) continue
                val source = rawArticles[srcIdx]
                val category = NewsCategory.fromWire(obj.optString("category", source.defaultCategory.label))
                val headline = obj.optString("headline", "").trim().ifBlank { source.title }
                val brief = obj.optString("brief_summary", "").trim()
                    .ifBlank { source.description.ifBlank { source.title } }
                val detailed = obj.optString("detailed_summary", "").trim()
                    .ifBlank { source.description.ifBlank { brief } }

                curated.add(
                    NewsBriefingItem(
                        id = "news_${srcIdx}_${headline.hashCode()}",
                        category = category,
                        headline = headline,
                        briefSummary = brief,
                        detailedSummary = detailed,
                        sourceName = source.sourceName,
                        publishedAtText = source.publishedAtText,
                        imageUrl = source.imageUrl,
                        articleUrl = source.link,
                    )
                )
                if (curated.size >= 6) break
            }

            if (curated.isEmpty()) {
                Result.success(fallbackCurateNews(rawArticles, cleanTopic))
            } else {
                Result.success(curated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "curateNewsBriefing fallback activated: ${e.message}")
            // Still deliver curated RSS news cleanly if Gemini fails/offline or App Check blocks
            Result.success(fallbackCurateNews(rawArticles, cleanTopic))
        }
    }

    private fun fallbackCurateNews(
        rawArticles: List<RawRssArticle>,
        topicQuery: String = "",
    ): List<NewsBriefingItem> {
        val relevant = if (topicQuery.isNotBlank()) {
            rawArticles.filter { com.example.news.RssNewsFetcher.matchesTopic(it, topicQuery) }
                .ifEmpty { rawArticles }
        } else {
            rawArticles
        }
        return relevant.take(6).mapIndexed { index, article ->
            val brief = article.description
                .takeIf { it.isNotBlank() }
                ?.let { desc ->
                    if (desc.length > 180) desc.take(180).substringBeforeLast(" ") + "." else desc
                }
                ?: article.title
            NewsBriefingItem(
                id = "fallback_${index}_${article.title.hashCode()}",
                category = article.defaultCategory,
                headline = article.title,
                briefSummary = brief,
                detailedSummary = article.description.ifBlank { article.title },
                sourceName = article.sourceName,
                publishedAtText = article.publishedAtText,
                imageUrl = article.imageUrl,
                articleUrl = article.link,
            )
        }
    }

    suspend fun generateNoteFromScreen(
        extractedText: String,
        screenshot: Bitmap?,
        sourceAppName: String,
    ): Result<ScreenNoteAiResult> = withContext(Dispatchers.IO) {
        val cleanedText = extractedText.trim()
        val prompt = buildString {
            appendLine("Kullanıcı mevcut telefon ekranını JARVIS Not Defteri'ne kaydetmek istiyor.")
            appendLine("Kaynak Uygulama: ${sourceAppName.ifBlank { "Bilinmiyor" }}")
            appendLine("Görevlerin:")
            appendLine("1. Ekrandaki en önemli bilgileri, mesajları, başlıkları, fiyatları, tarihleri veya metni temiz ve okunaklı biçimde çıkar.")
            appendLine("2. Kısa ve anlamlı bir Türkçe başlık (title) üret.")
            appendLine("3. Eğer ekran görüntüsü verildiyse görseldeki metni de (OCR) oku; gereksiz sistem ikon yazılarını (pil, saat vb.) dahil etme.")
            if (cleanedText.isNotBlank()) {
                appendLine()
                appendLine("ERİŞİLEBİLİRLİK METNİ:")
                appendLine(cleanedText.take(4_500))
            }
        }

        try {
            val accumulated = StringBuilder()
            withTimeout(ATTEMPT_TIMEOUT_MS) {
                screenNoteModel.generateContentStream(
                    content {
                        if (screenshot != null) {
                            image(screenshot)
                        }
                        text(prompt)
                    }
                ).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                    }
                }
            }

            val raw = accumulated.toString().trim()
            if (raw.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Ekran notu üretilemedi."))
            }
            val json = JSONObject(raw)
            val title = json.optString("title", "").trim()
                .ifBlank { "${sourceAppName.ifBlank { "Ekran" }} Notu" }
            val noteContent = json.optString("content", "").trim()
                .ifBlank { cleanedText }
            Result.success(ScreenNoteAiResult(title = title, content = noteContent))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateNoteFromScreen failed", e)
            Result.failure(Exception(formatAiError(e)))
        }
    }

    suspend fun evaluateWhatsAppMessage(
        senderName: String,
        messageText: String,
        userContext: String,
    ): Result<WhatsAppEvaluation> = withContext(Dispatchers.IO) {
        val contextHint = userContext.trim().ifBlank {
            "Kısa, net, kibar ve doğal Türkçe yanıt ver."
        }
        val prompt = """
            Sen kullanıcının proaktif WhatsApp asistanısın (JARVIS).
            Gelen bir WhatsApp mesajını analiz edip önem derecesini puanla ve kullanıcı için hazır cevap taslağı oluştur.

            PUANLAMA REHBERİ:
            - 1-3: Gündelik selamlaşma, muhabbet, tek yönlü önemsiz bilgi, şaka, teşekkür.
            - 4-6: Normal planlama, aciliyeti olmayan soru veya sıradan hatırlatma.
            - 7-8: Önemli soru, randevu/toplantı değişikliği, iş/aile ile ilgili net aksiyon gerektiren mesaj.
            - 9-10: Acil durum, son dakika iptali, ödeme/finans/onay kodu, kritik iş veya sağlık konusu.

            KULLANICI BAĞLAMI: $contextHint
            GÖNDEREN: $senderName
            MESAJ: $messageText
        """.trimIndent()

        try {
            val accumulated = StringBuilder()
            withTimeout(ATTEMPT_TIMEOUT_MS) {
                whatsAppEvalModel.generateContentStream(
                    content {
                        text(prompt)
                    }
                ).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                    }
                }
            }
            val raw = accumulated.toString().trim()
            if (raw.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Boş değerlendirme yanıtı."))
            }
            val json = JSONObject(raw)
            val score = json.optInt("importanceScore", 1).coerceIn(1, 10)
            val reason = json.optString("importanceReason", "").trim()
                .ifBlank { "Mesaj içeriği dikkat gerektiriyor." }
            val summary = json.optString("summary", "").trim()
                .ifBlank { messageText.take(100) }
            val draft = json.optString("draftReply", "").trim()
                .ifBlank { "Mesajını aldım, birazdan dönüş yapacağım." }
            Result.success(
                WhatsAppEvaluation(
                    importanceScore = score,
                    importanceReason = reason,
                    summary = summary,
                    draftReply = draft,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "evaluateWhatsAppMessage failed", e)
            Result.failure(Exception(formatAiError(e)))
        }
    }

    private fun buildRecentHistoryBlock(messages: List<JarvisMessage>): String {
        if (messages.isEmpty()) return ""
        return messages.joinToString("\n") { msg ->
            val speaker = if (msg.isUser) "Kullanıcı" else "JARVIS"
            val actionTag = msg.action?.let { " [Aksiyon: ${it.intent.wireValue} -> ${it.target}]" } ?: ""
            "$speaker: ${msg.text}$actionTag"
        }
    }
}
