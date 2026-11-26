package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ActionIntent
import com.example.model.InstalledAppInfo
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
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
import org.json.JSONObject

private const val TAG = "JarvisRepository"
private const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
private const val FALLBACK_MODEL = "gemini-3.8-flash"
private const val ATTEMPT_TIMEOUT_MS = 30_000L

data class JarvisAiReply(
    val text: String,
    val ttsSummary: String,
    val action: JarvisAction?,
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
                    "none"
                ),
                description = "Aksiyon niyeti."
            ),
            "target" to Schema.string(
                description = "open_app için uygulama adı; open_url için URL; search_youtube için sorgu; tap_coordinate için kayıtlı etiket; torch_control için 'on'|'off'|'toggle'; volume_control için '<media|ring|notification|alarm>:<up|down|mute|unmute|vibrate|max|set>:<0-100?>'; open_system_settings için 'wifi'|'bluetooth'|'sound'|'accessibility'|'notifications'|'battery'|'display'|'location'|'settings'; diğerlerinde boş string."
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
                description = "set_reminder için hatırlatma metni; diğer durumlarda boş string."
            ),
        ),
        optionalProperties = listOf("minutes", "seconds")
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
                "Firebase App Check doğrulaması reddedildi (403). Bu APK özel imza ile derlendiyse Release derlemesi kullan veya debug token ekle."
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

        val finalReply = reply.ifBlank {
            if (action != null) "${action.intent.displayLabel} hazırlanıyor." else "Tamam."
        }
        return JarvisAiReply(
            text = finalReply,
            ttsSummary = ttsSummary.ifBlank { finalReply },
            action = action,
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

            EKRAN NOKTASI TIKLAMA KURALI:
            - Kullanıcı ekrandaki bir butona/sekmeye tıklamak, basmak veya girmek istiyorsa ve yukarıdaki KAYITLI DOKUNMA ETİKETLERİ listesinde karşılık gelen bir etiket varsa mode="action", intent="tap_coordinate", target="<listeden birebir etiket>" seç!

            GÜNCEL ZAMAN: $nowFormatted
            CİHAZDAKİ UYGULAMALAR: $appsSummary
            $coordsSection
            KULLANICI BAĞLAMI: $contextSection

            ÇIKTI MODLARI:
            - MOD 1 (mode="action"): open_app, open_url, search_youtube, set_reminder, go_home, tap_coordinate, torch_control, volume_control, battery_status, open_system_settings veya capture_screen_note niyeti varsa mode="action", reply alanına tek satır onay yaz.
            - MOD 2 (mode="answer"): Bilgi sorusu, özet, metin üretimi veya sohbetse mode="answer", intent="none" seç.
        """.trimIndent()
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
