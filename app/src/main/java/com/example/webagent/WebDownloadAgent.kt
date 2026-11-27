package com.example.webagent

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "WebDownloadAgent"

enum class WebAgentPhase {
    IDLE,
    SCANNING,
    AWAITING_CONFIRMATION,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class DiscoveredDownload(
    val sourcePageUrl: String,
    val downloadUrl: String,
    val fileName: String,
    val host: String,
    val extension: String,
    val isApk: Boolean,
    val isSuspicious: Boolean,
    val reasonText: String,
)

data class WebAgentUiState(
    val isVisible: Boolean = false,
    val phase: WebAgentPhase = WebAgentPhase.IDLE,
    val headerTitle: String = "",
    val statusText: String = "",
    val progressPercent: Int = 0,
    val discovered: DiscoveredDownload? = null,
    val downloadId: Long = -1L,
    val localUriString: String = "",
    val mimeType: String = "",
)

data class ParsedDownloadQuery(
    val explicitUrl: String?,
    val preferredExtension: String?,
    val searchQuery: String,
    val queryTokens: List<String>,
)

class WebDownloadAgent(
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(WebAgentUiState())
    val state: StateFlow<WebAgentUiState> = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var activeJob: Job? = null

    fun startAutonomousTask(
        rawRequest: String,
        confirmBeforeDownload: Boolean,
        onStatusEvent: ((String, Boolean) -> Unit)? = null,
    ) {
        val requestText = rawRequest.trim()
        if (requestText.isBlank()) return

        activeJob?.cancel()
        val parsed = parseDownloadRequest(requestText)

        _state.value = WebAgentUiState(
            isVisible = true,
            phase = WebAgentPhase.SCANNING,
            headerTitle = "WEB AJANI // TARAMA",
            statusText = if (parsed.explicitUrl != null) {
                "${extractHost(parsed.explicitUrl)} sayfası taranıyor…"
            } else {
                "\"${parsed.searchQuery}\" için indirme bağlantısı aranıyor…"
            },
            progressPercent = 0,
        )

        activeJob = scope.launch {
            try {
                val discovered = discoverDownloadTarget(parsed)
                if (discovered == null) {
                    val failMsg = "Hedef sitede doğrudan indirilebilir bir dosya bağlantısı bulunamadı."
                    _state.value = _state.value.copy(
                        phase = WebAgentPhase.FAILED,
                        headerTitle = "WEB AJANI // HATA",
                        statusText = failMsg,
                    )
                    onStatusEvent?.invoke(failMsg, true)
                    return@launch
                }

                if (confirmBeforeDownload && (discovered.isApk || discovered.isSuspicious)) {
                    _state.value = _state.value.copy(
                        phase = WebAgentPhase.AWAITING_CONFIRMATION,
                        headerTitle = "WEB AJANI // GÜVENLİK ONAYI",
                        statusText = "${discovered.fileName} (${discovered.host}) indirilmeye hazır. ${discovered.reasonText}",
                        discovered = discovered,
                    )
                    onStatusEvent?.invoke(
                        "${discovered.fileName} bulundu, indirme için onayın bekleniyor.",
                        false,
                    )
                } else {
                    beginDownloadInternal(discovered, onStatusEvent)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Web download agent failed", e)
                val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Web ajanı bağlantı sırasında hata ile karşılaştı."
                _state.value = _state.value.copy(
                    phase = WebAgentPhase.FAILED,
                    headerTitle = "WEB AJANI // HATA",
                    statusText = msg,
                )
                onStatusEvent?.invoke(msg, true)
            }
        }
    }

    fun confirmPendingDownload(onStatusEvent: ((String, Boolean) -> Unit)? = null) {
        val current = _state.value
        val discovered = current.discovered ?: return
        if (current.phase != WebAgentPhase.AWAITING_CONFIRMATION) return

        activeJob?.cancel()
        activeJob = scope.launch {
            beginDownloadInternal(discovered, onStatusEvent)
        }
    }

    fun cancelOrDismiss() {
        activeJob?.cancel()
        val id = _state.value.downloadId
        if (id > 0L && _state.value.phase == WebAgentPhase.DOWNLOADING) {
            try {
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(id)
            } catch (_: Exception) {
            }
        }
        _state.value = WebAgentUiState()
    }

    fun openDownloadedFile(): Pair<Boolean, String> {
        return openDownloadedFile(appContext, _state.value)
    }

    private suspend fun beginDownloadInternal(
        discovered: DiscoveredDownload,
        onStatusEvent: ((String, Boolean) -> Unit)?,
    ) {
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) {
            val msg = "Android DownloadManager servisi kullanılamıyor."
            _state.value = _state.value.copy(
                phase = WebAgentPhase.FAILED,
                headerTitle = "WEB AJANI // HATA",
                statusText = msg,
            )
            onStatusEvent?.invoke(msg, true)
            return
        }

        val mime = guessMimeType(discovered.extension)
        val downloadId = try {
            val request = DownloadManager.Request(Uri.parse(discovered.downloadUrl)).apply {
                setTitle(discovered.fileName)
                setDescription("JARVIS Web Ajanı indiriyor (${discovered.host})")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, discovered.fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (mime.isNotBlank()) {
                    setMimeType(mime)
                }
            }
            dm.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager enqueue failed", e)
            val msg = "İndirme başlatılamadı: ${e.localizedMessage ?: "yetki/bağlantı hatası"}"
            _state.value = _state.value.copy(
                phase = WebAgentPhase.FAILED,
                headerTitle = "WEB AJANI // HATA",
                statusText = msg,
            )
            onStatusEvent?.invoke(msg, true)
            return
        }

        _state.value = _state.value.copy(
            phase = WebAgentPhase.DOWNLOADING,
            headerTitle = "WEB AJANI // İNDİRME",
            statusText = "${discovered.fileName} indiriliyor (%0)…",
            progressPercent = 0,
            discovered = discovered,
            downloadId = downloadId,
            mimeType = mime,
        )

        var done = false
        while (!done) {
            delay(450L)
            val query = DownloadManager.Query().setFilterById(downloadId)
            var cursor: Cursor? = null
            try {
                cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_RUNNING
                    val bytesSoFar = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                    val bytesTotal = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
                    val pct = if (bytesTotal > 0L) {
                        ((bytesSoFar * 100L) / bytesTotal).toInt().coerceIn(0, 100)
                    } else {
                        _state.value.progressPercent
                    }

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            done = true
                            val uri = dm.getUriForDownloadedFile(downloadId)
                            val resolvedMime = dm.getMimeTypeForDownloadedFile(downloadId) ?: mime
                            val completeMsg = "${discovered.fileName} İndirilenler klasörüne kaydedildi."
                            _state.value = _state.value.copy(
                                phase = WebAgentPhase.COMPLETED,
                                headerTitle = "WEB AJANI // TAMAMLANDI",
                                statusText = completeMsg,
                                progressPercent = 100,
                                localUriString = uri?.toString().orEmpty(),
                                mimeType = resolvedMime,
                            )
                            onStatusEvent?.invoke(completeMsg, false)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            done = true
                            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                            val failMsg = "İndirme başarısız oldu (kod: $reason)."
                            _state.value = _state.value.copy(
                                phase = WebAgentPhase.FAILED,
                                headerTitle = "WEB AJANI // HATA",
                                statusText = failMsg,
                            )
                            onStatusEvent?.invoke(failMsg, true)
                        }
                        else -> {
                            val sizeHint = if (bytesTotal > 0L) "%$pct" else "${bytesSoFar / 1024} KB"
                            _state.value = _state.value.copy(
                                progressPercent = pct,
                                statusText = "${discovered.fileName} indiriliyor ($sizeHint)…",
                            )
                        }
                    }
                } else {
                    done = true
                    _state.value = _state.value.copy(
                        phase = WebAgentPhase.FAILED,
                        headerTitle = "WEB AJANI // İPTAL",
                        statusText = "İndirme iptal edildi.",
                    )
                }
            } finally {
                cursor?.close()
            }
        }
    }

    private suspend fun discoverDownloadTarget(parsed: ParsedDownloadQuery): DiscoveredDownload? =
        withContext(Dispatchers.IO) {
            val seedUrls = mutableListOf<String>()
            if (!parsed.explicitUrl.isNullOrBlank()) {
                seedUrls.add(parsed.explicitUrl)
                // If user passed github.com/user/repo without /releases, also queue /releases
                if (parsed.explicitUrl.contains("github.com", ignoreCase = true) &&
                    !parsed.explicitUrl.contains("/releases", ignoreCase = true)
                ) {
                    seedUrls.add(parsed.explicitUrl.trimEnd('/') + "/releases/latest")
                }
            } else {
                val searchUrl = "https://html.duckduckgo.com/html/?q=" +
                    Uri.encode(parsed.searchQuery + if (parsed.preferredExtension != null) " ${parsed.preferredExtension}" else "")
                val searchHtml = fetchHtml(searchUrl)
                val searchLinks = extractDuckDuckGoLinks(searchHtml)
                seedUrls.addAll(searchLinks.take(4))
            }

            // First pass: check each seed URL via OkHttp (2-hop link resolution)
            for (seed in seedUrls.distinct()) {
                val direct = inspectUrlForDownload(
                    pageUrl = seed,
                    preferredExt = parsed.preferredExtension,
                    queryTokens = parsed.queryTokens,
                    depth = 0,
                )
                if (direct != null) return@withContext direct
            }

            // Second pass: Offscreen WebView JS discovery on top candidate page if HTTP failed (JS buttons/redirects)
            val fallbackPage = seedUrls.firstOrNull() ?: return@withContext null
            withContext(Dispatchers.Main) {
                updateScanningStatus("${extractHost(fallbackPage)} etkileşimli tarama yapılıyor…")
            }
            val webViewResult = withTimeoutOrNull(9_000L) {
                discoverWithOffscreenWebView(fallbackPage, parsed.preferredExtension, parsed.queryTokens)
            }
            webViewResult
        }

    private suspend fun updateScanningStatus(status: String) {
        if (_state.value.phase == WebAgentPhase.SCANNING) {
            _state.value = _state.value.copy(statusText = status)
        }
    }

    private fun inspectUrlForDownload(
        pageUrl: String,
        preferredExt: String?,
        queryTokens: List<String>,
        depth: Int,
    ): DiscoveredDownload? {
        if (looksLikeDirectBinaryUrl(pageUrl, preferredExt)) {
            return buildDiscovered(sourcePageUrl = pageUrl, downloadUrl = pageUrl, dispositionHeader = null)
        }

        val req = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", DESKTOP_MOBILE_UA)
            .get()
            .build()

        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val finalUrl = resp.request.url.toString()
                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val contentDisp = resp.header("Content-Disposition")

                if (isBinaryContentType(contentType) || looksLikeDirectBinaryUrl(finalUrl, preferredExt)) {
                    return@use buildDiscovered(
                        sourcePageUrl = pageUrl,
                        downloadUrl = finalUrl,
                        dispositionHeader = contentDisp,
                    )
                }

                if (!contentType.contains("html") && contentType.isNotBlank()) {
                    return@use null
                }

                val html = resp.body?.string()?.take(260_000).orEmpty()
                if (html.isBlank()) return@use null

                // Meta refresh redirect check
                val metaRefresh = Regex(
                    """<meta[^>]+http-equiv=["']?refresh["']?[^>]+url=([^"'>\s]+)""",
                    RegexOption.IGNORE_CASE,
                ).find(html)?.groupValues?.getOrNull(1)
                if (!metaRefresh.isNullOrBlank() && depth < 1) {
                    val resolved = resolveAbsoluteUrl(finalUrl, metaRefresh)
                    if (resolved != null) {
                        inspectUrlForDownload(resolved, preferredExt, queryTokens, depth + 1)?.let { return@use it }
                    }
                }

                val candidates = extractHtmlLinks(finalUrl, html)
                    .map { (href, text) ->
                        Triple(href, text, scoreCandidateLink(href, text, queryTokens, preferredExt))
                    }
                    .filter { it.third > 0 }
                    .sortedByDescending { it.third }

                // Check direct downloadable links first
                for ((href, _, _) in candidates.take(6)) {
                    if (looksLikeDirectBinaryUrl(href, preferredExt)) {
                        return@use buildDiscovered(
                            sourcePageUrl = finalUrl,
                            downloadUrl = href,
                            dispositionHeader = null,
                        )
                    }
                }

                // Follow up to 2 promising secondary download pages (e.g. GitHub releases / Download buttons)
                if (depth < 1) {
                    for ((href, _, score) in candidates.take(2)) {
                        if (score >= 40) {
                            inspectUrlForDownload(href, preferredExt, queryTokens, depth + 1)?.let {
                                return@use it
                            }
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP inspect failed for $pageUrl: ${e.message}")
            null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun discoverWithOffscreenWebView(
        pageUrl: String,
        preferredExt: String?,
        queryTokens: List<String>,
    ): DiscoveredDownload? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var finished = false
            val webView = WebView(appContext)

            fun finishOnce(result: DiscoveredDownload?) {
                if (finished) return
                finished = true
                try {
                    webView.stopLoading()
                    webView.destroy()
                } catch (_: Exception) {
                }
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadsImagesAutomatically = false
            webView.settings.userAgentString = DESKTOP_MOBILE_UA

            webView.setDownloadListener(DownloadListener { url, _, contentDisposition, mimetype, _ ->
                if (!url.isNullOrBlank() && (looksLikeDirectBinaryUrl(url, preferredExt) || isBinaryContentType(mimetype.orEmpty()))) {
                    finishOnce(buildDiscovered(pageUrl, url, contentDisposition))
                }
            })

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val nextUrl = request?.url?.toString().orEmpty()
                    if (looksLikeDirectBinaryUrl(nextUrl, preferredExt)) {
                        finishOnce(buildDiscovered(pageUrl, nextUrl, null))
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val currentUrl = url ?: pageUrl
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var out = [];
                            var els = document.querySelectorAll('a[href], button[data-href]');
                            for (var i = 0; i < Math.min(els.length, 120); i++) {
                                var href = els[i].href || els[i].getAttribute('data-href') || '';
                                var txt = (els[i].innerText || els[i].textContent || '').trim();
                                if (href) out.push(href + '|||' + txt);
                            }
                            return out.join('\n');
                        })();
                        """.trimIndent()
                    ) { raw ->
                        val cleaned = raw?.trim('"')
                            ?.replace("\\n", "\n")
                            ?.replace("\\/", "/")
                            .orEmpty()
                        val best = cleaned.lineSequence()
                            .mapNotNull { line ->
                                val parts = line.split("|||", limit = 2)
                                val href = parts.getOrNull(0)?.trim().orEmpty()
                                val text = parts.getOrNull(1)?.trim().orEmpty()
                                if (href.startsWith("http", ignoreCase = true) && looksLikeDirectBinaryUrl(href, preferredExt)) {
                                    href to scoreCandidateLink(href, text, queryTokens, preferredExt)
                                } else {
                                    null
                                }
                            }
                            .maxByOrNull { it.second }
                        if (best != null) {
                            finishOnce(buildDiscovered(currentUrl, best.first, null))
                        } else {
                            finishOnce(null)
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                try {
                    webView.stopLoading()
                    webView.destroy()
                } catch (_: Exception) {
                }
            }

            webView.loadUrl(pageUrl)
        }
    }

    private fun fetchHtml(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_MOBILE_UA)
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.take(220_000).orEmpty() else ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchHtml failed for $url: ${e.message}")
            ""
        }
    }

    private fun extractDuckDuckGoLinks(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val found = LinkedHashSet<String>()
        val uddgRegex = Regex("""uddg=([^&"']+)""")
        uddgRegex.findAll(html).forEach { match ->
            val encoded = match.groupValues.getOrNull(1).orEmpty()
            runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()?.let { decoded ->
                if (decoded.startsWith("http", ignoreCase = true) && !decoded.contains("duckduckgo.com")) {
                    found.add(decoded)
                }
            }
        }
        if (found.isEmpty()) {
            val directRegex = Regex("""href=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
            directRegex.findAll(html).forEach { match ->
                val url = match.groupValues[1]
                if (!url.contains("duckduckgo.com", ignoreCase = true)) {
                    found.add(url)
                }
            }
        }
        return found.toList()
    }

    private fun buildDiscovered(
        sourcePageUrl: String,
        downloadUrl: String,
        dispositionHeader: String?,
    ): DiscoveredDownload {
        val guessed = URLUtil.guessFileName(downloadUrl, dispositionHeader, null)
            .takeIf { it.isNotBlank() && it != "downloadfile.bin" }
            ?: Uri.parse(downloadUrl).lastPathSegment?.substringBefore('?')
            ?: "jarvis_indirme_${System.currentTimeMillis()}.apk"
        val cleanName = guessed.replace(Regex("""[^a-zA-Z0-9._-]"""), "_").take(96)
        val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val host = extractHost(downloadUrl)
        val (suspicious, reason) = evaluateSafety(downloadUrl, cleanName, ext)
        return DiscoveredDownload(
            sourcePageUrl = sourcePageUrl,
            downloadUrl = downloadUrl,
            fileName = cleanName,
            host = host,
            extension = ext,
            isApk = ext == "apk",
            isSuspicious = suspicious,
            reasonText = reason,
        )
    }

    companion object {
        private const val DESKTOP_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val DOWNLOAD_EXTENSIONS = setOf(
            "apk", "zip", "pdf", "tar", "gz", "rar", "7z", "mp3", "mp4", "docx", "xlsx", "csv", "txt"
        )

        private val TRUSTED_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "github-releases.githubusercontent.com",
            "f-droid.org",
            "download.videolan.org",
            "get.videolan.org",
            "play.google.com",
            "archive.org",
            "sourceforge.net",
        )

        fun parseDownloadRequest(rawText: String): ParsedDownloadQuery {
            val urlRegex = Regex("""(https?://[^\s]+|[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?)""")
            val explicitMatch = urlRegex.find(rawText)?.value?.trimEnd('.', ',', ';', ')', '"', '\'')
            val explicitUrl = explicitMatch?.let { candidate ->
                if (candidate.startsWith("http://", ignoreCase = true) ||
                    candidate.startsWith("https://", ignoreCase = true)
                ) {
                    candidate
                } else {
                    "https://$candidate"
                }
            }

            val lower = rawText.lowercase(Locale.ROOT)
            val preferredExt = DOWNLOAD_EXTENSIONS.firstOrNull { ext ->
                Regex("""\b$ext\b|\.$ext\b""").containsMatchIn(lower)
            }

            val cleanedQuery = rawText
                .replace(Regex("""^(?:hey\s+)?jarvis[\s,:]*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(sitesine|sayfasına|sayfasina|gir|girip|bul|bulup|telefona|cihaza|indir|indirme|dosyasını|dosyasini|lütfen|lutfen|bana|şu|su)\b""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifBlank { rawText.trim() }

            val tokens = cleanedQuery
                .lowercase(Locale.ROOT)
                .split(Regex("""[^a-zA-Z0-9çğıöşüÇĞIİÖŞÜ]+"""))
                .filter { it.length >= 2 }

            return ParsedDownloadQuery(
                explicitUrl = explicitUrl,
                preferredExtension = preferredExt,
                searchQuery = cleanedQuery,
                queryTokens = tokens,
            )
        }

        fun extractHtmlLinks(baseUrl: String, html: String): List<Pair<String, String>> {
            val results = LinkedHashMap<String, String>()
            val anchorRegex = Regex(
                """<a\b[^>]*href=["']([^"'#]+)["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            anchorRegex.findAll(html).forEach { match ->
                val rawHref = match.groupValues[1].trim()
                val text = match.groupValues[2]
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                val abs = resolveAbsoluteUrl(baseUrl, rawHref)
                if (abs != null && !results.containsKey(abs)) {
                    results[abs] = text
                }
            }
            val dataHrefRegex = Regex(
                """data-href=["']([^"'#]+)["']""",
                RegexOption.IGNORE_CASE,
            )
            dataHrefRegex.findAll(html).forEach { match ->
                val abs = resolveAbsoluteUrl(baseUrl, match.groupValues[1].trim())
                if (abs != null && !results.containsKey(abs)) {
                    results[abs] = ""
                }
            }
            return results.entries.map { it.key to it.value }
        }

        fun resolveAbsoluteUrl(baseUrl: String, rawHref: String): String? {
            if (rawHref.isBlank() ||
                rawHref.startsWith("javascript:", ignoreCase = true) ||
                rawHref.startsWith("mailto:", ignoreCase = true)
            ) {
                return null
            }
            return try {
                URI(baseUrl).resolve(rawHref).toString()
            } catch (_: Exception) {
                null
            }
        }

        fun looksLikeDirectBinaryUrl(url: String, preferredExt: String?): Boolean {
            val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
            if (preferredExt != null && clean.endsWith(".$preferredExt")) return true
            return DOWNLOAD_EXTENSIONS.any { ext -> clean.endsWith(".$ext") }
        }

        fun scoreCandidateLink(
            href: String,
            anchorText: String,
            queryTokens: List<String>,
            preferredExt: String?,
        ): Int {
            val h = href.lowercase(Locale.ROOT)
            val t = anchorText.lowercase(Locale.ROOT)
            var score = 0

            if (preferredExt != null && h.substringBefore('?').endsWith(".$preferredExt")) {
                score += 120
            } else if (DOWNLOAD_EXTENSIONS.any { h.substringBefore('?').endsWith(".$it") }) {
                score += 95
            }

            if (h.contains("/download") || h.contains("releases/download") || t.contains("download") || t.contains("indir")) {
                score += 45
            }
            if (h.contains("/releases")) {
                score += 35
            }
            for (token in queryTokens) {
                if (h.contains(token) || t.contains(token)) {
                    score += 20
                }
            }
            if (h.endsWith(".png") || h.endsWith(".jpg") || h.endsWith(".svg") || h.endsWith(".css") || h.endsWith(".js")) {
                score -= 100
            }
            return score
        }

        fun evaluateSafety(
            downloadUrl: String,
            fileName: String,
            extension: String,
        ): Pair<Boolean, String> {
            val host = extractHost(downloadUrl).lowercase(Locale.ROOT)
            val isHttps = downloadUrl.startsWith("https://", ignoreCase = true)
            val isTrustedHost = TRUSTED_HOSTS.any { host == it || host.endsWith(".$it") }
            return when {
                extension == "apk" && !isHttps ->
                    true to "Şifresiz HTTP üzerinden APK paketi. Kaynağı kontrol etmeden onaylama."
                extension == "apk" && !isTrustedHost ->
                    true to "Üçüncü taraf kaynaktan APK dosyası ($host). Yalnızca güvendiğin kaynakları onayla."
                extension == "apk" ->
                    false to "Güvenilir kaynaktan APK paketi tespit edildi ($host)."
                !isHttps ->
                    true to "Şifresiz HTTP bağlantısı üzerinden dosya indirilecek."
                else ->
                    false to "Güvenli HTTPS indirme bağlantısı doğrulandı ($host)."
            }
        }

        fun extractHost(url: String): String {
            return try {
                URI(url).host?.removePrefix("www.").orEmpty().ifBlank { "web" }
            } catch (_: Exception) {
                "web"
            }
        }

        private fun isBinaryContentType(contentType: String): Boolean {
            return contentType.contains("application/vnd.android.package-archive") ||
                contentType.contains("application/octet-stream") ||
                contentType.contains("application/pdf") ||
                contentType.contains("application/zip") ||
                contentType.contains("application/x-zip")
        }

        private fun guessMimeType(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }

        fun openDownloadedFile(context: Context, state: WebAgentUiState): Pair<Boolean, String> {
            val uriStr = state.localUriString
            if (uriStr.isBlank()) {
                return false to "Açılacak dosya yolu bulunamadı."
            }
            val uri = Uri.parse(uriStr)
            val isApk = state.discovered?.isApk == true ||
                state.mimeType == "application/vnd.android.package-archive"

            if (isApk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(permIntent)
                    true to "APK kurulum izni ekranı açılıyor."
                } catch (e: Exception) {
                    false to "Bilinmeyen kaynak izni ekranı açılamadı."
                }
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, state.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return try {
                context.startActivity(viewIntent)
                true to if (isApk) "APK kurulum ekranı açılıyor." else "Dosya açılıyor."
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open downloaded file", e)
                val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(downloadsIntent) }
                false to "Dosyayı açacak uygulama bulunamadı; İndirilenler açıldı."
            }
        }
    }
}
