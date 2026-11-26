package com.example.system

import com.example.model.TapCoordinateItem
import java.text.Normalizer

object CoordinateMatcher {

    private val tapStopWords = setOf(
        "tikla", "tıkla", "bas", "dokun", "gir", "sec", "seç", "ac", "aç", "git",
        "sekmesine", "sekmesi", "butonuna", "butonu", "tusuna", "tuşuna", "tusu",
        "yerine", "alanina", "ekranina", "menuye", "menusune", "koordinatina",
        "noktasina", "uzerine", "lutfen", "hemen", "simdi", "bana", "oraya"
    )

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'İ', 'i' -> sb.append('i')
                'I', 'ı' -> sb.append('i')
                'ş', 'Ş' -> sb.append('s')
                'ğ', 'Ğ' -> sb.append('g')
                'ü', 'Ü' -> sb.append('u')
                'ö', 'Ö' -> sb.append('o')
                'ç', 'Ç' -> sb.append('c')
                'ə', 'Ə' -> sb.append('e')
                '\'', '’', '`' -> sb.append(' ')
                else -> if (ch.isLetterOrDigit() || ch.isWhitespace()) {
                    sb.append(ch.lowercaseChar())
                } else {
                    sb.append(' ')
                }
            }
        }
        val decomposed = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
        return decomposed
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun findZeroTokenMatch(
        command: String,
        coordinates: List<TapCoordinateItem>,
    ): TapCoordinateItem? {
        if (coordinates.isEmpty()) return null
        val normalizedCommand = normalize(command)
        if (normalizedCommand.isBlank()) return null

        val tokens = normalizedCommand
            .split(' ')
            .filter { it.isNotBlank() && it !in tapStopWords }
        val commandCore = tokens.joinToString(" ")
        val hasExplicitTapVerb = normalizedCommand.split(' ').any { it in tapStopWords }

        var bestItem: TapCoordinateItem? = null
        var bestScore = 0

        for (item in coordinates) {
            val normLabel = normalize(item.label)
            if (normLabel.isBlank()) continue

            val score = when {
                normalizedCommand == normLabel -> 100
                commandCore == normLabel -> 95
                commandCore.isNotBlank() && tokens.any { token ->
                    matchesTurkishStem(token, normLabel)
                } -> 90
                normLabel.length >= 3 && commandCore.contains(normLabel) -> 85
                hasExplicitTapVerb && normLabel.length >= 3 && normalizedCommand.contains(normLabel) -> 80
                else -> 0
            }

            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }

        return if (bestScore >= 80) bestItem else null
    }

    fun findByModelLabel(
        labelQuery: String,
        coordinates: List<TapCoordinateItem>,
    ): TapCoordinateItem? {
        if (coordinates.isEmpty()) return null
        val target = normalize(labelQuery)
        if (target.isBlank()) return null

        return coordinates.firstOrNull { normalize(it.label) == target }
            ?: coordinates.firstOrNull {
                val nl = normalize(it.label)
                nl.contains(target) || target.contains(nl)
            }
    }

    private fun matchesTurkishStem(token: String, label: String): Boolean {
        if (token == label) return true
        if (label.length < 3 || token.length < label.length) return false
        if (!token.startsWith(label)) return false
        val suffix = token.removePrefix(label)
        return suffix in setOf(
            "e", "a", "ye", "ya", "ne", "na",
            "i", "yi", "ni",
            "de", "da", "te", "ta",
            "den", "dan", "ten", "tan",
            "le", "la", "yle", "yla",
        )
    }
}
