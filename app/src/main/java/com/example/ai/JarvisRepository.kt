package com.example.ai

import android.util.Log
import com.example.model.ActionIntent
import com.example.model.InstalledAppInfo
import com.example.model.JarvisAction
import com.example.model.JarvisMessage
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
                    "none"
                ),
                description = "Aksiyon niyeti. Ekrandaki kayıtlı bir noktaya dokunmak için 'tap_coordinate', metin yanıtları için 'none' ver."
            ),
            "target" to Schema.string(
                description = "open_app için uygulama adı, open_url için URL, search_youtube için arama sorgusu, tap_coordinate için kayıtlı etiket listesinden birebir eşleşen label; diğer durumlarda boş string."
            ),
            "minutes" to Schema.integer(
                description = "set_reminder için dakika sayısı. Kullanıcı SADECE saniye belirttiyse BURAYI null bırak!",
                nullable = true
            ),
            "seconds" to Schema.integer(
                description = "set_reminder için saniye sayısı (örn: '10 saniye sonra' -> 10). Saniye yoksa null bırak.",
                nullable = true
            ),
            "message" to Schema.string(
                description = "set_reminder için hatırlatma metni veya aksiyon açıklaması; diğer durumlarda boş string."
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
            model = primaryModel,
            modelLabel = PRIMARY_MODEL,
            promptText = fullInput,
            timeoutMs = ATTEMPT_TIMEOUT_MS,
        )

        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        val primaryError = primaryResult.exceptionOrNull()
        if (primaryError is TimeoutCancellationException) {
            Log.w(TAG, "Primary model $PRIMARY_MODEL timed out; retrying with $FALLBACK_MODEL")
            val fallbackResult = executeStreamWithTimeout(
                model = fallbackModel,
                modelLabel = FALLBACK_MODEL,
                promptText = fullInput,
                timeoutMs = ATTEMPT_TIMEOUT_MS,
            )
            return@withContext fallbackResult.fold(
                onSuccess = { Result.success(it) },
                onFailure = { err ->
                    val message = if (err is TimeoutCancellationException) {
                        "Bağlantı zaman aşımına uğradı. İnternet bağlantını kontrol edip tekrar dene."
                    } else {
                        err.localizedMessage ?: "JARVIS şu anda yanıt üretemedi."
                    }
                    Result.failure(Exception(message))
                }
            )
        }

        Result.failure(
            Exception(primaryError?.localizedMessage ?: "JARVIS şu anda yanıt üretemedi.")
        )
    }

    private suspend fun executeStreamWithTimeout(
        model: GenerativeModel,
        modelLabel: String,
        promptText: String,
        timeoutMs: Long,
    ): Result<JarvisAiReply> {
        val startMs = System.currentTimeMillis()
        val accumulated = StringBuilder()

        return try {
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
            Result.success(parseStructuredJson(rawJson))
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

    private fun parseStructuredJson(rawJson: String): JarvisAiReply {
        val json = JSONObject(rawJson)
        val mode = json.optString("mode", "answer").lowercase(Locale.ROOT)
        val reply = json.optString("reply", "").trim()
        val ttsSummary = json.optString("ttsSummary", "").trim()
        val intent = ActionIntent.fromWire(json.optString("intent", "none"))
        val target = json.optString("target", "").let {
            if (it == "null") "" else it.trim()
        }
        val minutes = if (json.has("minutes") && !json.isNull("minutes")) {
            json.optInt("minutes").takeIf { it > 0 }
        } else {
            null
        }
        val seconds = if (json.has("seconds") && !json.isNull("seconds")) {
            json.optInt("seconds").takeIf { it > 0 }
        } else {
            null
        }
        val message = json.optString("message", "").let {
            if (it == "null") "" else it.trim()
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

            HATIRLATICI SÜRE KURALI:
            - Kullanıcı saniye belirtirse (ör. "10 saniye sonra") minutes=null, seconds=10 ver! Saniyeyi dakikaya çevirme.
            - Kullanıcı dakika belirtirse minutes=5, seconds=null ver. Süre yoksa minutes=10, seconds=null ver.

            EKRAN NOKTASI TIKLAMA KURALI:
            - Kullanıcı ekrandaki bir butona/sekmeye tıklamak, basmak veya girmek istiyorsa ve yukarıdaki KAYITLI DOKUNMA ETİKETLERİ listesinde karşılık gelen bir etiket varsa mode="action", intent="tap_coordinate", target="<listeden birebir etiket>" seç!

            GÜNCEL ZAMAN: $nowFormatted
            CİHAZDAKİ UYGULAMALAR: $appsSummary
            $coordsSection
            KULLANICI BAĞLAMI: $contextSection

            ÇIKTI MODLARI:
            - MOD 1 (mode="action"): open_app, open_url, search_youtube, set_reminder, go_home veya tap_coordinate niyeti varsa mode="action", reply alanına tek satır onay yaz.
            - MOD 2 (mode="answer"): Bilgi sorusu, özet, metin üretimi veya sohbetse mode="answer", intent="none" seç.
        """.trimIndent()
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
