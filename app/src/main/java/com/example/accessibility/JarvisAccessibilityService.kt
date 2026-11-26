package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedHashSet
import kotlinx.coroutines.Dispatchers
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
