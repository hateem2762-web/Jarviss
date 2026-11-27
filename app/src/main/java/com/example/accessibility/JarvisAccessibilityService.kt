package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "JarvisA11yService"
private const val MAX_CAPTURED_TEXT_CHARS = 6_000
private const val MAX_SCREENSHOT_DIMENSION_PX = 1280

data class CapturedScreenText(
    val sourcePackage: String,
    val text: String,
)

data class ScheduledAutomationReport(
    val success: Boolean,
    val summary: String,
    val sentCount: Int,
    val totalCount: Int,
    val failedRecipients: List<String>,
)

enum class SwipeDirection {
    NEXT_UP,
    PREV_DOWN,
    LEFT,
    RIGHT,
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.d(TAG, "JarvisAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()?.trim().orEmpty()
            if (pkg.isNotEmpty() && !isIgnoredSystemOrSelfPackage(pkg)) {
                _currentForegroundPackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "JarvisAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isServiceConnected.value = false
    }

    private fun isIgnoredSystemOrSelfPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        return pkg == "com.android.systemui" ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("keyboard", ignoreCase = true) ||
            pkg.contains("gboard", ignoreCase = true)
    }

    suspend fun extractVisibleScreenText(): CapturedScreenText = withContext(Dispatchers.Default) {
        val candidateRoots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val pkg = activeRoot.packageName?.toString().orEmpty()
                if (!isIgnoredSystemOrSelfPackage(pkg)) {
                    candidateRoots.add(activeRoot)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading rootInActiveWindow", e)
        }

        if (candidateRoots.isEmpty()) {
            try {
                for (win in windows.orEmpty()) {
                    val root = win.root ?: continue
                    val pkg = root.packageName?.toString().orEmpty()
                    if (!isIgnoredSystemOrSelfPackage(pkg)) {
                        candidateRoots.add(root)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading accessibility windows", e)
            }
        }

        val collectedLines = LinkedHashSet<String>()
        var detectedPkg = _currentForegroundPackage.value.orEmpty()
        var totalChars = 0

        for (root in candidateRoots) {
            val rootPkg = root.packageName?.toString()?.trim().orEmpty()
            if (rootPkg.isNotEmpty() && !isIgnoredSystemOrSelfPackage(rootPkg)) {
                detectedPkg = rootPkg
            }
            collectNodeText(root, collectedLines, totalChars) { addedLen ->
                totalChars += addedLen
            }
        }

        CapturedScreenText(
            sourcePackage = detectedPkg,
            text = collectedLines.joinToString("\n").trim(),
        )
    }

    private fun collectNodeText(
        node: AccessibilityNodeInfo?,
        output: LinkedHashSet<String>,
        currentLength: Int,
        onAdded: (Int) -> Unit,
    ) {
        if (node == null || currentLength >= MAX_CAPTURED_TEXT_CHARS) return
        if (!node.isVisibleToUser) return

        val pkg = node.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && isIgnoredSystemOrSelfPackage(pkg)) return

        var added = 0
        val rawText = node.text?.toString()?.trim().orEmpty()
        if (rawText.isNotBlank() && currentLength + added < MAX_CAPTURED_TEXT_CHARS) {
            if (output.add(rawText)) {
                added += rawText.length + 1
                onAdded(rawText.length + 1)
            }
        }

        val rawDesc = node.contentDescription?.toString()?.trim().orEmpty()
        if (rawDesc.isNotBlank() && rawDesc != rawText && currentLength + added < MAX_CAPTURED_TEXT_CHARS) {
            if (output.add(rawDesc)) {
                added += rawDesc.length + 1
                onAdded(rawDesc.length + 1)
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            if (currentLength + added >= MAX_CAPTURED_TEXT_CHARS) break
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                collectNodeText(child, output, currentLength + added) { childAdded ->
                    added += childAdded
                    onAdded(childAdded)
                }
            }
        }
    }

    suspend fun captureScreenBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            val hwBuffer = screenshot.hardwareBuffer
                            val bitmap = try {
                                val wrapped = Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                                val software = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                wrapped?.recycle()
                                software?.let { downscaleIfNeeded(it) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed converting screenshot hardwareBuffer", e)
                                null
                            } finally {
                                try {
                                    hwBuffer.close()
                                } catch (_: Exception) {
                                }
                            }
                            if (continuation.isActive) {
                                continuation.resume(bitmap)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed with errorCode=$errorCode")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "takeScreenshot threw exception", e)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun downscaleIfNeeded(source: Bitmap): Bitmap {
        val maxDim = maxOf(source.width, source.height)
        if (maxDim <= MAX_SCREENSHOT_DIMENSION_PX) return source
        val scale = MAX_SCREENSHOT_DIMENSION_PX.toFloat() / maxDim.toFloat()
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (scaled !== source) {
            source.recycle()
        }
        return scaled
    }

    suspend fun dispatchTapAtPercent(
        xPercent: Float,
        yPercent: Float,
    ): Boolean {
        val (screenW, screenH) = getRealScreenSize(this)
        val clampedX = (xPercent.coerceIn(0.01f, 0.99f)) * screenW
        val clampedY = (yPercent.coerceIn(0.01f, 0.99f)) * screenH
        return dispatchTapAtPixel(clampedX, clampedY)
    }

    suspend fun dispatchTapAtPixel(xPx: Float, yPx: Float): Boolean {
        val path = Path().apply {
            moveTo(xPx, yPx)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null
            )
            if (!accepted && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    suspend fun dispatchDoubleTapAtPercent(
        xPercent: Float = 0.5f,
        yPercent: Float = 0.48f,
    ): Boolean {
        val first = dispatchTapAtPercent(xPercent, yPercent)
        if (!first) return false
        delay(80L)
        return dispatchTapAtPercent(xPercent, yPercent)
    }

    suspend fun dispatchDoubleTabAtPercentSafe(): Boolean {
        return dispatchDoubleTapAtPercent(0.5f, 0.52f)
    }

    /**
     * Dispatches a realistic touch fling/scroll via AccessibilityService.dispatchGesture.
     * NEXT_UP swipes finger bottom -> top (advances TikTok / Reels / Shorts to next video).
     * PREV_DOWN swipes finger top -> bottom (returns to previous video / scrolls up).
     */
    suspend fun dispatchSwipe(direction: SwipeDirection): Boolean = withContext(Dispatchers.Main) {
        val (screenW, screenH) = getRealScreenSize(this@JarvisAccessibilityService)
        val w = screenW.toFloat()
        val h = screenH.toFloat()

        val coords = when (direction) {
            SwipeDirection.NEXT_UP -> floatArrayOf(
                w * 0.50f, h * 0.78f,
                w * 0.51f, h * 0.49f,
                w * 0.50f, h * 0.21f,
                300f,
            )
            SwipeDirection.PREV_DOWN -> floatArrayOf(
                w * 0.50f, h * 0.23f,
                w * 0.49f, h * 0.52f,
                w * 0.50f, h * 0.80f,
                320f,
            )
            SwipeDirection.LEFT -> floatArrayOf(
                w * 0.84f, h * 0.50f,
                w * 0.50f, h * 0.49f,
                w * 0.16f, h * 0.50f,
                280f,
            )
            SwipeDirection.RIGHT -> floatArrayOf(
                w * 0.16f, h * 0.50f,
                w * 0.50f, h * 0.51f,
                w * 0.84f, h * 0.50f,
                280f,
            )
        }
        val startX = coords[0]
        val startY = coords[1]
        val midX = coords[2]
        val midY = coords[3]
        val endX = coords[4]
        val endY = coords[5]
        val durationMs = coords[6].toLong()

        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(midX, midY, endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        suspendCancellableCoroutine { continuation ->
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    fun dispatchBackAction(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Waits for the opened media app (Spotify, YouTube, TikTok, etc.) to settle,
     * scans the window for the first playable item / card / play button, and clicks it.
     */
    suspend fun autoPlayFirstResult(targetPackage: String): Boolean = withContext(Dispatchers.Main) {
        delay(1_700L)

        val (screenW, screenH) = getRealScreenSize(this@JarvisAccessibilityService)
        val candidateRoots = mutableListOf<AccessibilityNodeInfo>()
        try {
            rootInActiveWindow?.let { candidateRoots.add(it) }
        } catch (_: Exception) {}

        if (candidateRoots.isEmpty()) {
            windows?.forEach { win ->
                win.root?.let { candidateRoots.add(it) }
            }
        }

        if (candidateRoots.isEmpty()) return@withContext false

        val playKeywords = listOf("play", "oynat", "dinle", "listen", "çal", "cal", "izle", "watch")
        for (root in candidateRoots) {
            val playNode = findNodeMatching(root) { node ->
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
                val text = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
                val isPlay = playKeywords.any { kw -> desc.contains(kw) || text.contains(kw) }
                isPlay && (node.isClickable || node.parent?.isClickable == true)
            }
            if (playNode != null) {
                val targetToClick = if (playNode.isClickable) playNode else playNode.parent
                val clicked = targetToClick?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                if (clicked) {
                    Log.d(TAG, "autoPlayFirstResult: clicked playNode in $targetPackage")
                    return@withContext true
                }
            }
        }

        val rect = Rect()
        for (root in candidateRoots) {
            val contentNode = findNodeMatching(root) { node ->
                if (!node.isClickable) return@findNodeMatching false
                node.getBoundsInScreen(rect)
                val isReasonableSize = rect.width() > screenW * 0.35f && rect.height() > 80
                val isInContentArea = rect.top >= (screenH * 0.16f) && rect.bottom <= (screenH * 0.72f)
                isReasonableSize && isInContentArea
            }
            if (contentNode != null) {
                val clicked = contentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.d(TAG, "autoPlayFirstResult: clicked content card in $targetPackage")
                    return@withContext true
                }
            }
        }

        return@withContext dispatchTapAtPercent(0.50f, 0.32f)
    }

    private fun findNodeMatching(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeMatching(child, predicate)
            if (found != null) return found
        }
        return null
    }

    suspend fun executeScheduledMessageAutomation(
        targetPackage: String,
        targetAppName: String,
        recipients: List<String>,
        messageText: String,
    ): ScheduledAutomationReport = withContext(Dispatchers.Main) {
        if (recipients.isEmpty()) {
            return@withContext ScheduledAutomationReport(
                success = false,
                summary = "Gönderim yapılacak alıcı listesi boş.",
                sentCount = 0,
                totalCount = 0,
                failedRecipients = emptyList(),
            )
        }

        val resolvedPackage = resolveInstalledMessagingPackage(targetPackage)
        val launchIntent = packageManager.getLaunchIntentForPackage(resolvedPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return@withContext ScheduledAutomationReport(
            success = false,
            summary = "$targetAppName ($targetPackage) cihazda yüklü değil veya başlatılamadı.",
            sentCount = 0,
            totalCount = recipients.size,
            failedRecipients = recipients,
        )

        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            return@withContext ScheduledAutomationReport(
                success = false,
                summary = "$targetAppName açılamadı: ${e.localizedMessage ?: "yetki hatası"}",
                sentCount = 0,
                totalCount = recipients.size,
                failedRecipients = recipients,
            )
        }

        val windowReady = waitForTargetWindow(resolvedPackage, timeoutMs = 4_800L)
        if (!windowReady) {
            return@withContext ScheduledAutomationReport(
                success = false,
                summary = "$targetAppName ön plana getirilemedi.",
                sentCount = 0,
                totalCount = recipients.size,
                failedRecipients = recipients,
            )
        }

        delay(1_100L)

        // If app opened inside an existing chat (bottom message EditText visible), back out to Chats/Inbox
        repeat(2) {
            val currentRoot = rootInActiveWindow
            if (currentRoot != null && findBottomMessageInput(currentRoot) != null) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(650L)
            }
        }

        // Open Inbox / Chats tab if present (e.g. TikTok Inbox, Instagram DM, WhatsApp Chats)
        rootInActiveWindow?.let { root ->
            if (resolvedPackage.contains("musically") || resolvedPackage.contains("trill")) {
                clickNodeByLabels(root, listOf("gelen kutusu", "inbox", "mesajlar", "messages"))
                delay(1_200L)
            } else if (resolvedPackage.contains("instagram")) {
                clickNodeByLabels(root, listOf("mesajlar", "messages", "direct"))
                delay(1_100L)
            } else if (resolvedPackage.contains("whatsapp")) {
                clickNodeByLabels(root, listOf("sohbetler", "chats"))
                delay(450L)
            }
        }

        var sentCount = 0
        val failed = mutableListOf<String>()

        for ((idx, recipient) in recipients.withIndex()) {
            val ok = sendSingleRecipientViaUi(
                targetPackage = resolvedPackage,
                recipient = recipient,
                messageText = messageText,
            )
            if (ok) {
                sentCount++
            } else {
                failed.add(recipient)
            }
            if (idx < recipients.lastIndex) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(700L)
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(700L)
            }
        }

        val allSent = sentCount == recipients.size && failed.isEmpty()
        val summary = when {
            allSent -> "$targetAppName üzerinden ${recipients.joinToString(", ")} kişilerine \"$messageText\" gönderildi."
            sentCount > 0 -> "$targetAppName kısmi gönderim ($sentCount/${recipients.size}). Başarısız: ${failed.joinToString(", ")}"
            else -> "$targetAppName içinde hedef sohbet/gönder butonu bulunamadı (${failed.joinToString(", ")})."
        }

        ScheduledAutomationReport(
            success = allSent,
            summary = summary,
            sentCount = sentCount,
            totalCount = recipients.size,
            failedRecipients = failed,
        )
    }

    private fun resolveInstalledMessagingPackage(preferred: String): String {
        if (packageManager.getLaunchIntentForPackage(preferred) != null) return preferred
        if (preferred == "com.zhiliaoapp.musically" &&
            packageManager.getLaunchIntentForPackage("com.ss.android.ugc.trill") != null
        ) {
            return "com.ss.android.ugc.trill"
        }
        if (preferred == "com.whatsapp" &&
            packageManager.getLaunchIntentForPackage("com.whatsapp.w4b") != null
        ) {
            return "com.whatsapp.w4b"
        }
        return preferred
    }

    private suspend fun waitForTargetWindow(targetPackage: String, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (activePkg == targetPackage || _currentForegroundPackage.value == targetPackage) {
                return true
            }
            delay(250L)
        }
        return false
    }

    private suspend fun sendSingleRecipientViaUi(
        targetPackage: String,
        recipient: String,
        messageText: String,
    ): Boolean {
        val normRecipient = normalizeUiText(recipient)
        if (normRecipient.isBlank()) return false

        val (_, screenH) = getRealScreenSize(this)
        var root = rootInActiveWindow ?: return false

        // Step 1: Check if recipient chat row is already visible on Inbox/Chats screen (excluding headers/search)
        var openedChat = clickChatRowContaining(root, normRecipient, minTopPx = (screenH * 0.14f).toInt())

        // Step 2: Otherwise open New Chat / Search and type recipient into a TOP-HALF search EditText only
        if (!openedChat) {
            val topSearchEdit = findTopSearchEditNode(root, screenH)
            if (topSearchEdit != null) {
                performReliableClick(topSearchEdit)
                delay(280L)
                setNodeText(topSearchEdit, recipient)
                delay(1_300L)
            } else {
                val navLabels = if (targetPackage.contains("whatsapp")) {
                    listOf("yeni sohbet", "new chat", "ara", "search")
                } else {
                    listOf("ara", "search", "yeni mesaj", "new message", "yeni sohbet", "new chat")
                }
                clickNodeByLabels(root, navLabels)
                delay(850L)
                root = rootInActiveWindow ?: return false
                // On WhatsApp "Yeni sohbet", sometimes you need to tap the top Search icon inside Contacts
                var activeSearchEdit = findTopSearchEditNode(root, screenH)
                if (activeSearchEdit == null) {
                    clickNodeByLabels(root, listOf("ara", "search"))
                    delay(650L)
                    root = rootInActiveWindow ?: return false
                    activeSearchEdit = findTopSearchEditNode(root, screenH)
                }
                if (activeSearchEdit != null) {
                    performReliableClick(activeSearchEdit)
                    delay(240L)
                    setNodeText(activeSearchEdit, recipient)
                    delay(1_350L)
                }
            }
            root = rootInActiveWindow ?: return false
            val activeSearchTop = findTopSearchEditNode(root, screenH)?.let { node ->
                Rect().also { node.getBoundsInScreen(it) }.bottom + 12
            } ?: (screenH * 0.18f).toInt()
            openedChat = clickChatRowContaining(root, normRecipient, minTopPx = activeSearchTop)
        }

        if (!openedChat) return false
        delay(1_150L)

        // Step 3: Locate bottom message EditText inside conversation screen
        root = rootInActiveWindow ?: return false
        val messageInput = findMessageInputNode(root, screenH) ?: return false
        performReliableClick(messageInput)
        delay(220L)
        val typed = setNodeText(messageInput, messageText)
        if (!typed) return false
        delay(550L)

        // Step 4: Locate and tap Send button
        root = rootInActiveWindow ?: return false
        return clickSendButton(root, messageInput)
    }

    private fun collectAllNodes(root: AccessibilityNodeInfo?, maxNodes: Int = 340): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val list = ArrayList<AccessibilityNodeInfo>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && list.size < maxNodes) {
            val node = queue.removeFirst()
            list.add(node)
            val count = node.childCount
            for (i in 0 until count) {
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                }
                if (child != null) queue.add(child)
            }
        }
        return list
    }

    private suspend fun clickNodeByLabels(
        root: AccessibilityNodeInfo,
        labels: List<String>,
    ): Boolean {
        val nodes = collectAllNodes(root)
        val target = nodes.firstOrNull { node ->
            if (!node.isVisibleToUser) return@firstOrNull false
            val text = normalizeUiText(node.text?.toString().orEmpty())
            val desc = normalizeUiText(node.contentDescription?.toString().orEmpty())
            labels.any { lbl -> text == lbl || desc == lbl || text.contains(lbl) || desc.contains(lbl) }
        } ?: return false
        return performReliableClick(target)
    }

    private suspend fun clickChatRowContaining(
        root: AccessibilityNodeInfo,
        normRecipient: String,
        minTopPx: Int = 0,
    ): Boolean {
        val nodes = collectAllNodes(root)
        val match = nodes.firstOrNull { node ->
            if (!node.isVisibleToUser || node.isEditable) return@firstOrNull false
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.top < minTopPx) return@firstOrNull false
            val text = normalizeUiText(node.text?.toString().orEmpty())
            val desc = normalizeUiText(node.contentDescription?.toString().orEmpty())
            if (text.contains("meta ai") || desc.contains("meta ai")) return@firstOrNull false
            text.contains(normRecipient) || desc.contains(normRecipient)
        } ?: return false
        return performReliableClick(match)
    }

    private fun findEditableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        return collectAllNodes(root)
            .filter { node ->
                node.isVisibleToUser &&
                    (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true)
            }
            .sortedBy { node ->
                val rect = Rect()
                node.getBoundsInScreen(rect)
                rect.top
            }
    }

    private fun findTopSearchEditNode(root: AccessibilityNodeInfo, screenH: Int): AccessibilityNodeInfo? {
        return findEditableNodes(root).firstOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val hint = normalizeUiText((node.hintText ?: node.contentDescription ?: node.text)?.toString().orEmpty())
            val looksLikeSearch = hint.contains("ara") || hint.contains("search") || hint.contains("bul")
            rect.top < (screenH * 0.42f) && (looksLikeSearch || !hint.contains("mesaj") && !hint.contains("message"))
        }
    }

    private fun findBottomMessageInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val (_, screenH) = getRealScreenSize(this)
        return findEditableNodes(root).lastOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.bottom >= (screenH * 0.50f)
        }
    }

    private fun findMessageInputNode(root: AccessibilityNodeInfo, screenH: Int): AccessibilityNodeInfo? {
        val edits = findEditableNodes(root)
        if (edits.isEmpty()) return null
        val labeled = edits.lastOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val hint = normalizeUiText((node.hintText ?: node.text ?: node.contentDescription)?.toString().orEmpty())
            rect.bottom >= (screenH * 0.42f) &&
                (hint.contains("mesaj") || hint.contains("message") || hint.contains("yaz") || hint.contains("send"))
        }
        return labeled ?: edits.lastOrNull { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.bottom >= (screenH * 0.45f)
        } ?: edits.lastOrNull()
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private suspend fun clickSendButton(
        root: AccessibilityNodeInfo,
        inputNode: AccessibilityNodeInfo,
    ): Boolean {
        val inputRect = Rect()
        inputNode.getBoundsInScreen(inputRect)
        val nodes = collectAllNodes(root)

        val labeledSend = nodes.firstOrNull { node ->
            if (!node.isVisibleToUser || node.isEditable) return@firstOrNull false
            val text = normalizeUiText(node.text?.toString().orEmpty())
            val desc = normalizeUiText(node.contentDescription?.toString().orEmpty())
            text == "gonder" || text == "send" || text == "ilet" ||
                desc.contains("gonder") || desc.contains("send")
        }
        if (labeledSend != null && performReliableClick(labeledSend)) return true

        // Spatial fallback: clickable button/icon to the right of the message input on roughly the same row
        val adjacentRight = nodes
            .filter { node ->
                if (!node.isVisibleToUser || node.isEditable || !node.isClickable) return@filter false
                val r = Rect()
                node.getBoundsInScreen(r)
                r.left >= inputRect.right - 20 &&
                    kotlin.math.abs(r.centerY() - inputRect.centerY()) <= (inputRect.height().coerceAtLeast(80) * 1.4f)
            }
            .minByOrNull { node ->
                val r = Rect()
                node.getBoundsInScreen(r)
                r.left
            }

        return adjacentRight != null && performReliableClick(adjacentRight)
    }

    private suspend fun performReliableClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            if (current != null && current.isClickable) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            current = try {
                current?.parent
            } catch (_: Exception) {
                null
            }
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            return dispatchTapAtPixel(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        return false
    }

    private fun normalizeUiText(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .trim()
    }

    companion object {
        @Volatile
        private var instance: JarvisAccessibilityService? = null

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _currentForegroundPackage = MutableStateFlow<String?>(null)
        val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

        fun getInstance(): JarvisAccessibilityService? = instance

        fun isAccessibilityEnabled(context: Context): Boolean {
            if (_isServiceConnected.value && instance != null) return true
            val expectedComponent = ComponentName(context, JarvisAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices
                .split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expectedComponent }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun isVideoFeedPackage(packageName: String?): Boolean {
            val pkg = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (pkg.isBlank()) return false
            if (pkg in VIDEO_FEED_PACKAGES) return true
            return pkg.contains("tiktok") ||
                pkg.contains("musically") ||
                pkg.contains("youtube") ||
                pkg.contains("instagram")
        }

        private val VIDEO_FEED_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.trill",
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.snapchat.android",
        )

        fun getRealScreenSize(context: Context): Pair<Int, Int> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
            }
        }
    }
}
