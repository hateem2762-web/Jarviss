package com.example.system

import com.example.model.ActionIntent
import com.example.model.JarvisAction
import java.util.Locale

object LocalQuickCommandMatcher {

    fun match(rawPrompt: String): JarvisAction? {
        val norm = normalize(rawPrompt)
        if (norm.isBlank()) return null

        // 1. "Bu ekranı not al" / "Şu ekranı not al" / "Bunu not defterine kaydet"
        val isScreenNoteCommand =
            (norm.contains("ekran") && (norm.contains("not al") || norm.contains("not et") || norm.contains("kaydet"))) ||
                norm.contains("not defterine kaydet") ||
                norm.contains("not defterine ekle") ||
                norm == "not al"
        if (isScreenNoteCommand) {
            return JarvisAction(intent = ActionIntent.CAPTURE_SCREEN_NOTE)
        }

        // 2. Flashlight
        if (norm in setOf("fener", "feneri ac", "fener ac", "flasi ac", "flas ac", "isigi ac")) {
            return JarvisAction(intent = ActionIntent.TORCH_CONTROL, target = "on")
        }
        if (norm in setOf("feneri kapat", "fener kapat", "feneri kapa", "flasi kapat", "flas kapat", "isigi kapat")) {
            return JarvisAction(intent = ActionIntent.TORCH_CONTROL, target = "off")
        }

        // 3. Battery status
        if (
            norm.contains("sarjim kac") ||
            norm.contains("sarj yuzde") ||
            norm.contains("pil yuzde") ||
            norm.contains("pil durumu") ||
            norm.contains("sarj durumu") ||
            norm.contains("batarya durumu") ||
            norm.contains("sarj oluyor mu")
        ) {
            return JarvisAction(intent = ActionIntent.BATTERY_STATUS)
        }

        // 4. Direct System Settings shortcuts
        if (norm.contains("ayar")) {
            when {
                norm.contains("wifi") || norm.contains("wi fi") || norm.contains("kablosuz") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "wifi")
                norm.contains("bluetooth") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "bluetooth")
                norm.contains("ses ayar") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "sound")
                norm.contains("erisilebilirlik") ->
                    return JarvisAction(intent = ActionIntent.OPEN_SYSTEM_SETTINGS, target = "accessibility")
            }
        }

        return null
    }

    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace('-', ' ')
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
