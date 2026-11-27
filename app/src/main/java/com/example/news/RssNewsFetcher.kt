package com.example.news

import android.util.Log
import android.util.Xml
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

private const val TAG = "RssNewsFetcher"

data class RssFeedSource(
    val url: String,
    val sourceName: String,
    val defaultCategory: NewsCategory,
)

class RssNewsFetcher(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {

    private val defaultFeeds = listOf(
        RssFeedSource(
            url = "https://feeds.bbci.co.uk/turkce/rss.xml",
            sourceName = "BBC Türkçe",
            defaultCategory = NewsCategory.GUNDEM,
        ),
        RssFeedSource(
            url = "https://www.trthaber.com/sondakika.rss",
            sourceName = "TRT Haber",
            defaultCategory = NewsCategory.GUNDEM,
        ),
        RssFeedSource(
            url = "https://www.webtekno.com/rss.xml",
            sourceName = "Webtekno",
            defaultCategory = NewsCategory.TEKNOLOJI,
        ),
        RssFeedSource(
            url = "https://www.trthaber.com/spor_articles.rss",
            sourceName = "TRT Spor",
            defaultCategory = NewsCategory.SPOR,
        ),
        RssFeedSource(
            url = "https://www.trthaber.com/dunya_articles.rss",
            sourceName = "TRT Dünya",
            defaultCategory = NewsCategory.DUNYA,
        ),
        RssFeedSource(
            url = "https://feeds.bbci.co.uk/news/world/rss.xml",
            sourceName = "BBC World",
            defaultCategory = NewsCategory.DUNYA,
        ),
    )

    suspend fun fetchCandidateArticles(
        topicQuery: String = "",
        maxTotalArticles: Int = 24,
    ): List<RawRssArticle> = withContext(Dispatchers.IO) {
        val cleanTopic = topicQuery.trim()
        if (cleanTopic.isNotEmpty()) {
            return@withContext fetchTopicCandidateArticles(
                topicQuery = cleanTopic,
                maxTotalArticles = maxTotalArticles,
            )
        }

        val perFeedLists = coroutineScope {
            defaultFeeds.map { feed ->
                async {
                    fetchSingleFeed(feed)
                }
            }.awaitAll()
        }

        val combined = mutableListOf<RawRssArticle>()
        val seenTitles = HashSet<String>()
        val maxPerFeed = 5
        val indices = IntArray(perFeedLists.size)

        // Interleave across categories/sources so Gündem, Teknoloji, Spor, Dünya are balanced
        var added = true
        while (combined.size < maxTotalArticles && added) {
            added = false
            for (feedIdx in perFeedLists.indices) {
                val list = perFeedLists[feedIdx]
                val itemIdx = indices[feedIdx]
                if (itemIdx < list.size && itemIdx < maxPerFeed) {
                    indices[feedIdx] = itemIdx + 1
                    val candidate = list[itemIdx]
                    val titleKey = normalizeTitleKey(candidate.title)
                    if (titleKey.isNotEmpty() && seenTitles.add(titleKey)) {
                        combined.add(candidate)
                        if (combined.size >= maxTotalArticles) break
                    }
                    added = true
                }
            }
        }
        combined
    }

    private suspend fun fetchTopicCandidateArticles(
        topicQuery: String,
        maxTotalArticles: Int,
    ): List<RawRssArticle> = coroutineScope {
        val encodedQuery = URLEncoder.encode(topicQuery, "UTF-8")
        val inferredCategory = inferCategoryForTopic(topicQuery)

        val searchFeeds = listOf(
            RssFeedSource(
                url = "https://news.google.com/rss/search?q=$encodedQuery&hl=tr&gl=TR&ceid=TR:tr",
                sourceName = "Google Haberler",
                defaultCategory = inferredCategory,
            ),
            RssFeedSource(
                url = "https://www.bing.com/news/search?q=$encodedQuery&format=rss&setlang=tr&cc=TR",
                sourceName = "Bing Haberler",
                defaultCategory = inferredCategory,
            ),
        )

        val searchDeferred = searchFeeds.map { feed ->
            async { fetchSingleFeed(feed, maxItems = 15) }
        }
        val localDeferred = defaultFeeds.map { feed ->
            async {
                fetchSingleFeed(feed, maxItems = 14).filter { article ->
                    matchesTopic(article, topicQuery)
                }
            }
        }

        val localMatched = localDeferred.awaitAll().flatten()
        val searchResults = searchDeferred.awaitAll().flatten()

        // Prefer local matched articles with rich thumbnails first, then dedicated search results
        val combined = mutableListOf<RawRssArticle>()
        val seenTitles = HashSet<String>()
        for (article in (localMatched + searchResults)) {
            val key = normalizeTitleKey(article.title)
            if (key.isNotEmpty() && seenTitles.add(key)) {
                combined.add(article)
                if (combined.size >= maxTotalArticles) break
            }
        }
        combined
    }

    private fun fetchSingleFeed(feed: RssFeedSource, maxItems: Int = 8): List<RawRssArticle> {
        return try {
            val request = Request.Builder()
                .url(feed.url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) JARVIS-Briefing/1.0"
                )
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "RSS HTTP ${response.code} from ${feed.url}")
                    return emptyList()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()
                parseRssXml(
                    xmlContent = body,
                    sourceName = feed.sourceName,
                    defaultCategory = feed.defaultCategory,
                    maxItems = maxItems,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching RSS ${feed.url}: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val IMG_SRC_REGEX = Regex(
            """<img[^>]+src\s*=\s*["']([^"']+)["']""",
            setOf(RegexOption.IGNORE_CASE)
        )
        private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun parseRssXml(
            xmlContent: String,
            sourceName: String,
            defaultCategory: NewsCategory,
            maxItems: Int = 8,
        ): List<RawRssArticle> {
            val trimmed = xmlContent.trim()
            if (trimmed.isEmpty()) return emptyList()

            val results = mutableListOf<RawRssArticle>()
            val parser = createPullParser(trimmed)

            var insideItem = false
            var isAtomEntry = false
            var title = ""
            var description = ""
            var contentEncoded = ""
            var link = ""
            var pubDate = ""
            var itemSourceName = ""
            var imageUrl: String? = null
            var bestMediaWidth = -1

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT && results.size < maxItems) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase(Locale.ROOT).orEmpty()
                        if (tag == "item" || tag == "entry") {
                            insideItem = true
                            isAtomEntry = (tag == "entry")
                            title = ""
                            description = ""
                            contentEncoded = ""
                            link = ""
                            pubDate = ""
                            itemSourceName = ""
                            imageUrl = null
                            bestMediaWidth = -1
                        } else if (insideItem) {
                            when (tag) {
                                "title" -> title = safeNextText(parser)
                                "description", "summary", "subtitle" -> {
                                    val raw = safeNextText(parser)
                                    if (raw.isNotBlank()) description = raw
                                }
                                "content:encoded", "content" -> {
                                    val raw = safeNextText(parser)
                                    if (raw.isNotBlank()) contentEncoded = raw
                                }
                                "source" -> {
                                    val src = cleanHtmlText(safeNextText(parser))
                                    if (src.isNotBlank()) itemSourceName = src
                                }
                                "news:image" -> {
                                    val img = sanitizeImageUrl(safeNextText(parser))
                                    if (img != null && imageUrl == null) {
                                        imageUrl = img
                                    }
                                }
                                "link" -> {
                                    if (isAtomEntry) {
                                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                        val href = parser.getAttributeValue(null, "href").orEmpty()
                                        val type = parser.getAttributeValue(null, "type").orEmpty()
                                        if (rel == "enclosure" && type.startsWith("image", ignoreCase = true) && href.isNotBlank()) {
                                            imageUrl = imageUrl ?: sanitizeImageUrl(href)
                                        } else if (href.isNotBlank() && (rel.isEmpty() || rel == "alternate")) {
                                            link = href
                                        }
                                    } else {
                                        val textLink = safeNextText(parser)
                                        if (textLink.isNotBlank()) link = textLink
                                    }
                                }
                                "pubdate", "published", "updated", "dc:date" -> {
                                    val rawDate = safeNextText(parser)
                                    if (rawDate.isNotBlank()) pubDate = rawDate
                                }
                                "enclosure" -> {
                                    val url = parser.getAttributeValue(null, "url").orEmpty()
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (url.isNotBlank() && (type.startsWith("image", ignoreCase = true) || looksLikeImageUrl(url))) {
                                        if (imageUrl == null) {
                                            imageUrl = sanitizeImageUrl(url)
                                        }
                                    }
                                }
                                "media:content", "media:thumbnail", "itunes:image" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                        ?: parser.getAttributeValue(null, "href")
                                        ?: ""
                                    val medium = parser.getAttributeValue(null, "medium").orEmpty()
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                                    val isImageCandidate = medium.isEmpty() ||
                                        medium.equals("image", ignoreCase = true) ||
                                        type.startsWith("image", ignoreCase = true) ||
                                        looksLikeImageUrl(url)
                                    if (url.isNotBlank() && isImageCandidate && (imageUrl == null || width > bestMediaWidth)) {
                                        bestMediaWidth = width
                                        imageUrl = sanitizeImageUrl(url)
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase(Locale.ROOT).orEmpty()
                        if (insideItem && (tag == "item" || tag == "entry")) {
                            val resolvedSource = itemSourceName.ifBlank { sourceName }
                            var cleanTitle = cleanHtmlText(title)
                            if (itemSourceName.isNotBlank() && cleanTitle.endsWith(" - $itemSourceName")) {
                                cleanTitle = cleanTitle.removeSuffix(" - $itemSourceName").trim()
                            }
                            val rawBody = contentEncoded.ifBlank { description }
                            if (imageUrl == null && rawBody.isNotBlank()) {
                                imageUrl = extractFirstImageFromHtml(rawBody)
                            }
                            if (imageUrl == null && description.isNotBlank()) {
                                imageUrl = extractFirstImageFromHtml(description)
                            }
                            val cleanDesc = cleanHtmlText(description.ifBlank { contentEncoded })
                            if (cleanTitle.isNotBlank()) {
                                results.add(
                                    RawRssArticle(
                                        title = cleanTitle,
                                        description = cleanDesc.take(500),
                                        sourceName = resolvedSource,
                                        publishedAtText = formatPublishedTime(pubDate),
                                        link = link.trim(),
                                        imageUrl = imageUrl,
                                        defaultCategory = defaultCategory,
                                    )
                                )
                            }
                            insideItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
            return results
        }

        private fun createPullParser(xmlContent: String): XmlPullParser {
            val parser = try {
                Xml.newPullParser()
            } catch (_: Throwable) {
                XmlPullParserFactory.newInstance().newPullParser()
            }
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))
            return parser
        }

        private fun safeNextText(parser: XmlPullParser): String {
            return try {
                parser.nextText().orEmpty().trim()
            } catch (_: Exception) {
                ""
            }
        }

        fun extractFirstImageFromHtml(html: String): String? {
            val matches = IMG_SRC_REGEX.findAll(html)
            for (match in matches) {
                val candidate = sanitizeImageUrl(match.groupValues[1])
                if (candidate != null && !isLikelyTrackingPixel(candidate)) {
                    return candidate
                }
            }
            return null
        }

        private fun sanitizeImageUrl(raw: String): String? {
            val trimmed = raw.trim()
                .replace("&amp;", "&")
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("http://", ignoreCase = true) ->
                    "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> null
            }
        }

        private fun looksLikeImageUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains(".jpg") ||
                lower.contains(".jpeg") ||
                lower.contains(".png") ||
                lower.contains(".webp") ||
                lower.contains("ichef.bbci.co.uk") ||
                lower.contains("/image")
        }

        private fun isLikelyTrackingPixel(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains("1x1") ||
                lower.contains("feedburner") ||
                lower.contains("pixel.") ||
                lower.contains("scorecardresearch") ||
                lower.contains("doubleclick")
        }

        fun cleanHtmlText(raw: String): String {
            if (raw.isBlank()) return ""
            val stripped = HTML_TAG_REGEX.replace(raw, " ")
            val decoded = stripped
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&#8217;", "’")
                .replace("&#8220;", "“")
                .replace("&#8221;", "”")
                .replace("&#8211;", "–")
                .replace("&#8212;", "—")
            return WHITESPACE_REGEX.replace(decoded, " ").trim()
        }

        fun formatPublishedTime(rawDate: String): String {
            val trimmed = rawDate.trim()
            if (trimmed.isEmpty()) return "Güncel"
            val patterns = listOf(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            )
            val outFormat = SimpleDateFormat("HH:mm", Locale("tr", "TR")).apply {
                timeZone = TimeZone.getDefault()
            }
            for (pattern in patterns) {
                try {
                    val parsed = SimpleDateFormat(pattern, Locale.ENGLISH).parse(trimmed)
                    if (parsed != null) {
                        return outFormat.format(parsed)
                    }
                } catch (_: Exception) {
                }
            }
            return trimmed.take(16)
        }

        private fun normalizeTitleKey(title: String): String {
            return title.lowercase(Locale("tr", "TR"))
                .replace(Regex("""[^\p{L}\p{N}]"""), "")
                .take(40)
        }

        fun matchesTopic(article: RawRssArticle, topicQuery: String): Boolean {
            val tokens = foldTurkish(topicQuery)
                .split(Regex("""\s+"""))
                .filter { it.length >= 2 }
            if (tokens.isEmpty()) return true
            val haystack = foldTurkish("${article.title} ${article.description}")
            return tokens.all { token -> haystack.contains(token) }
        }

        fun inferCategoryForTopic(topicQuery: String): NewsCategory {
            val norm = foldTurkish(topicQuery)
            return when {
                listOf(
                    "besiktas", "bjk", "galatasaray", "fenerbahce", "trabzonspor",
                    "futbol", "basketbol", "super lig", "sampiyonlar ligi", "spor", "mac", "transfer",
                ).any { norm.contains(it) } -> NewsCategory.SPOR
                listOf(
                    "teknoloji", "yapay zeka", "android", "apple", "samsung", "google",
                    "yazilim", "uzay", "bilim", "oyun", "siber",
                ).any { norm.contains(it) } -> NewsCategory.TEKNOLOJI
                listOf(
                    "dunya", "avrupa", "amerika", "abd", "rusya", "ukrayna", "ortadogu", "global",
                ).any { norm.contains(it) } -> NewsCategory.DUNYA
                else -> NewsCategory.GUNDEM
            }
        }

        private fun foldTurkish(text: String): String {
            return text.lowercase(Locale.ROOT)
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u')
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }
}
