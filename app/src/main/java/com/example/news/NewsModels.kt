package com.example.news

import java.util.Locale

enum class NewsCategory(val label: String) {
    GUNDEM("GÜNDEM"),
    TEKNOLOJI("TEKNOLOJİ"),
    SPOR("SPOR"),
    DUNYA("DÜNYA");

    companion object {
        fun fromWire(value: String?): NewsCategory {
            val norm = value
                ?.trim()
                ?.uppercase(Locale("tr", "TR"))
                ?.replace('İ', 'I')
                ?.replace('Ü', 'U')
                ?.replace('Ö', 'O')
                ?.replace('Ş', 'S')
                ?.replace('Ç', 'C')
                .orEmpty()
            return when {
                norm.contains("TEKNOLOJ") || norm.contains("BILIM") || norm.contains("TECH") -> TEKNOLOJI
                norm.contains("SPOR") || norm.contains("SPORT") -> SPOR
                norm.contains("DUNYA") || norm.contains("WORLD") || norm.contains("GLOBAL") -> DUNYA
                else -> GUNDEM
            }
        }
    }
}

data class RawRssArticle(
    val title: String,
    val description: String,
    val sourceName: String,
    val publishedAtText: String,
    val link: String,
    val imageUrl: String?,
    val defaultCategory: NewsCategory,
)

data class NewsBriefingItem(
    val id: String,
    val category: NewsCategory,
    val headline: String,
    val briefSummary: String,
    val detailedSummary: String,
    val sourceName: String,
    val publishedAtText: String,
    val imageUrl: String?,
    val articleUrl: String = "",
)

data class NewsBriefingState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val topicQuery: String = "",
    val items: List<NewsBriefingItem> = emptyList(),
    val currentIndex: Int = 0,
    val isTtsPaused: Boolean = false,
    val isSpeakingBriefing: Boolean = false,
    val showingDetail: Boolean = false,
    val errorMessage: String? = null,
    val gestureSensorActive: Boolean = false,
    val gestureStatusText: String = "",
    val lastGestureLabel: String = "",
) {
    val currentItem: NewsBriefingItem?
        get() = items.getOrNull(currentIndex)
}
