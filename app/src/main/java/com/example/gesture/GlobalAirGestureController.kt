package com.example.gesture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.AirGestureSensitivity
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.File
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "GlobalAirGestureCtrl"
private const val MODEL_FILE_NAME = "hand_landmarker.task"
private const val MODEL_DOWNLOAD_URL =
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"

enum class GlobalAirGestureDirection {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    PINCH,
    TWO_FINGER_VICTORY,
}

enum class HandPosture {
    NONE,
    OPEN_OR_POINTER,
    PINCH,
    TWO_FINGER_VICTORY,
    FIST,
}

data class HandLandmarkPoint(
    val x: Float,
    val y: Float,
)

data class GlobalAirGestureHudState(
    val isEnabled: Boolean = false,
    val isCameraActive: Boolean = false,
    val isWaitingForVideoApp: Boolean = false,
    val isHandTracked: Boolean = false,
    val statusLabel: String = "EL KONTROLÜ // KAPALI",
    val activePostureBadge: String = "",
    val lastActionLabel: String = "",
    val lastActionTimestampMs: Long = 0L,
    val triggerCount: Long = 0L,
)

/**
 * Rotation- and palm/back-invariant 21-landmark posture classifier.
 * Uses Euclidean ratios from the wrist (0) to PIPs and fingertips so the user
 * does NOT have to awkwardly force a flat open palm straight toward the lens.
 */
object HandPostureClassifier {

    fun classify(landmarks: List<HandLandmarkPoint>): Triple<HandPosture, Float, HandLandmarkPoint> {
        if (landmarks.size < 21) {
            return Triple(HandPosture.NONE, 0.16f, HandLandmarkPoint(0.5f, 0.5f))
        }
        val wrist = landmarks[0]
        val middleMcp = landmarks[9]
        val handScale = hypot(middleMcp.x - wrist.x, middleMcp.y - wrist.y)
            .coerceIn(0.06f, 0.45f)

        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val middleTip = landmarks[12]

        fun dist(a: HandLandmarkPoint, b: HandLandmarkPoint): Float = hypot(a.x - b.x, a.y - b.y)

        fun isExtended(tipIdx: Int, pipIdx: Int): Boolean {
            return dist(wrist, landmarks[tipIdx]) > dist(wrist, landmarks[pipIdx]) * 1.12f
        }

        fun isFolded(tipIdx: Int, pipIdx: Int): Boolean {
            return dist(wrist, landmarks[tipIdx]) < dist(wrist, landmarks[pipIdx]) * 1.05f
        }

        val indexExtended = isExtended(8, 6)
        val middleExtended = isExtended(12, 10)
        val ringFolded = isFolded(16, 14)
        val pinkyFolded = isFolded(20, 18)

        val indexFolded = isFolded(8, 6)
        val middleFolded = isFolded(12, 10)

        val thumbIndexDist = dist(thumbTip, indexTip)
        val thumbMiddleDist = dist(thumbTip, middleTip)

        val isFist = indexFolded && middleFolded && ringFolded && pinkyFolded
        val isPinch = !isFist &&
            thumbIndexDist < handScale * 0.36f &&
            thumbMiddleDist > handScale * 0.42f
        val isTwoFinger = !isPinch &&
            indexExtended &&
            middleExtended &&
            ringFolded &&
            pinkyFolded &&
            thumbIndexDist > handScale * 0.36f

        val posture = when {
            isFist -> HandPosture.FIST
            isPinch -> HandPosture.PINCH
            isTwoFinger -> HandPosture.TWO_FINGER_VICTORY
            else -> HandPosture.OPEN_OR_POINTER
        }

        // Track weighted blend of index fingertip and middle MCP for effortless wrist/finger flicks
        val anchor = HandLandmarkPoint(
            x = (1f - (indexTip.x * 0.45f + middleMcp.x * 0.55f)).coerceIn(0f, 1f),
            y = (indexTip.y * 0.45f + middleMcp.y * 0.55f).coerceIn(0f, 1f),
        )

        return Triple(posture, handScale, anchor)
    }
}

/**
 * Pure Kotlin 6-gesture tracker (4-direction scale-normalized micro-flicks + Pinch + Two-Finger Victory)
 * with Fist Pause/Reset, opposite-direction return-stroke suppression, and hand presence hysteresis.
 */
class GlobalAirGestureTracker(
    private var sensitivity: AirGestureSensitivity = AirGestureSensitivity.EASY,
    private val sameDirectionCooldownMs: Long = 680L,
    private val oppositeDirectionGuardMs: Long = 1_280L,
    private val postureCooldownMs: Long = 1_100L,
    private val pinchHoldMs: Long = 110L,
    private val twoFingerHoldMs: Long = 220L,
    private val maxWindowMs: Long = 480L,
    private val maxCrossAxisRatio: Float = 0.85f,
    private val handHoldMs: Long = 750L,
) {
    private data class Sample(
        val timestampMs: Long,
        val x: Float,
        val y: Float,
    )

    data class FrameOutput(
        val gesture: GlobalAirGestureDirection?,
        val isHandTracked: Boolean,
        val posture: HandPosture = HandPosture.NONE,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastSwipeTriggerMs: Long = -oppositeDirectionGuardMs
    private var lastSwipeDirection: GlobalAirGestureDirection? = null
    private var lastPostureTriggerMs: Long = -postureCooldownMs
    private var lastHandSeenMs: Long = -handHoldMs
    private var handTracked: Boolean = false

    private var smoothedX: Float? = null
    private var smoothedY: Float? = null

    private var pinchStartMs: Long = -1L
    private var twoFingerStartMs: Long = -1L

    fun setSensitivity(newSensitivity: AirGestureSensitivity) {
        sensitivity = newSensitivity
    }

    fun onLandmarks(
        landmarks: List<HandLandmarkPoint>?,
        timestampMs: Long,
    ): FrameOutput {
        if (landmarks == null || landmarks.size < 21) {
            return onFrame(
                hasHand = false,
                userSpaceX = 0f,
                y = 0f,
                timestampMs = timestampMs,
                posture = HandPosture.NONE,
                handScale = 0.16f,
            )
        }
        val (posture, handScale, anchor) = HandPostureClassifier.classify(landmarks)
        return onFrame(
            hasHand = true,
            userSpaceX = anchor.x,
            y = anchor.y,
            timestampMs = timestampMs,
            posture = posture,
            handScale = handScale,
        )
    }

    fun onFrame(
        hasHand: Boolean,
        userSpaceX: Float,
        y: Float,
        timestampMs: Long,
        posture: HandPosture = HandPosture.OPEN_OR_POINTER,
        handScale: Float = 0.16f,
    ): FrameOutput {
        if (!hasHand) {
            pinchStartMs = -1L
            twoFingerStartMs = -1L
            if (samples.isNotEmpty() && timestampMs - lastHandSeenMs > 280L) {
                samples.clear()
                smoothedX = null
                smoothedY = null
            }
            if (handTracked && timestampMs - lastHandSeenMs >= handHoldMs) {
                handTracked = false
            }
            return FrameOutput(gesture = null, isHandTracked = handTracked, posture = HandPosture.NONE)
        }

        lastHandSeenMs = timestampMs
        handTracked = true

        // 1. Fist pauses swiping so user can safely lower or reposition their hand
        if (posture == HandPosture.FIST) {
            samples.clear()
            smoothedX = null
            smoothedY = null
            pinchStartMs = -1L
            twoFingerStartMs = -1L
            return FrameOutput(gesture = null, isHandTracked = true, posture = HandPosture.FIST)
        }

        // 2. Pinch posture detection (Play / Pause)
        if (posture == HandPosture.PINCH) {
            twoFingerStartMs = -1L
            samples.clear()
            if (pinchStartMs < 0L) {
                pinchStartMs = timestampMs
            } else if (
                timestampMs - pinchStartMs >= pinchHoldMs &&
                timestampMs - lastPostureTriggerMs >= postureCooldownMs
            ) {
                lastPostureTriggerMs = timestampMs
                lastSwipeTriggerMs = timestampMs
                pinchStartMs = timestampMs + postureCooldownMs
                return FrameOutput(
                    gesture = GlobalAirGestureDirection.PINCH,
                    isHandTracked = true,
                    posture = HandPosture.PINCH,
                )
            }
            return FrameOutput(gesture = null, isHandTracked = true, posture = HandPosture.PINCH)
        } else {
            pinchStartMs = -1L
        }

        // 3. Two-finger Victory posture detection (Like / Double-Tap or configurable)
        if (posture == HandPosture.TWO_FINGER_VICTORY) {
            if (twoFingerStartMs < 0L) {
                twoFingerStartMs = timestampMs
            } else if (
                timestampMs - twoFingerStartMs >= twoFingerHoldMs &&
                timestampMs - lastPostureTriggerMs >= postureCooldownMs
            ) {
                lastPostureTriggerMs = timestampMs
                lastSwipeTriggerMs = timestampMs
                twoFingerStartMs = timestampMs + postureCooldownMs
                samples.clear()
                return FrameOutput(
                    gesture = GlobalAirGestureDirection.TWO_FINGER_VICTORY,
                    isHandTracked = true,
                    posture = HandPosture.TWO_FINGER_VICTORY,
                )
            }
        } else {
            twoFingerStartMs = -1L
        }

        // 4. EMA smoothing + scale-normalized micro-flick swipe tracking
        val sx = smoothedX?.let { it * 0.35f + userSpaceX * 0.65f } ?: userSpaceX
        val sy = smoothedY?.let { it * 0.35f + y * 0.65f } ?: y
        smoothedX = sx
        smoothedY = sy

        val last = samples.lastOrNull()
        if (last != null && timestampMs - last.timestampMs > 280L) {
            samples.clear()
        }
        samples.addLast(Sample(timestampMs, sx, sy))
        while (samples.isNotEmpty() && timestampMs - samples.first().timestampMs > maxWindowMs) {
            samples.removeFirst()
        }

        if (samples.size < 2 || timestampMs - lastSwipeTriggerMs < sameDirectionCooldownMs) {
            return FrameOutput(gesture = null, isHandTracked = true, posture = posture)
        }

        val (minDy, minDx) = thresholdsFor(handScale)
        val oldest = samples.first()
        val dx = sx - oldest.x
        val dy = sy - oldest.y
        val absDx = abs(dx)
        val absDy = abs(dy)

        val candidate = when {
            absDy >= minDy && absDy >= absDx && absDx <= absDy * maxCrossAxisRatio -> {
                if (dy < 0f) GlobalAirGestureDirection.SWIPE_UP else GlobalAirGestureDirection.SWIPE_DOWN
            }
            absDx >= minDx && absDx > absDy && absDy <= absDx * maxCrossAxisRatio -> {
                if (dx > 0f) GlobalAirGestureDirection.SWIPE_RIGHT else GlobalAirGestureDirection.SWIPE_LEFT
            }
            else -> null
        } ?: return FrameOutput(gesture = null, isHandTracked = true, posture = posture)

        // Return-stroke suppression: avoid accidental reverse scroll when relaxing hand back down
        val isOpposite = lastSwipeDirection != null && areOpposite(lastSwipeDirection!!, candidate)
        if (isOpposite && timestampMs - lastSwipeTriggerMs < oppositeDirectionGuardMs) {
            val primaryDisp = if (candidate == GlobalAirGestureDirection.SWIPE_UP || candidate == GlobalAirGestureDirection.SWIPE_DOWN) absDy else absDx
            val primaryThresh = if (candidate == GlobalAirGestureDirection.SWIPE_UP || candidate == GlobalAirGestureDirection.SWIPE_DOWN) minDy else minDx
            if (primaryDisp < primaryThresh * 1.9f) {
                return FrameOutput(gesture = null, isHandTracked = true, posture = posture)
            }
        }

        lastSwipeTriggerMs = timestampMs
        lastSwipeDirection = candidate
        samples.clear()
        return FrameOutput(gesture = candidate, isHandTracked = true, posture = posture)
    }

    private fun thresholdsFor(handScale: Float): Pair<Float, Float> {
        val scale = handScale.coerceIn(0.06f, 0.42f)
        return when (sensitivity) {
            AirGestureSensitivity.EASY -> {
                val dy = (scale * 0.54f).coerceIn(0.065f, 0.14f)
                val dx = (scale * 0.68f).coerceIn(0.085f, 0.175f)
                dy to dx
            }
            AirGestureSensitivity.BALANCED -> {
                val dy = (scale * 0.78f).coerceIn(0.09f, 0.175f)
                val dx = (scale * 0.94f).coerceIn(0.11f, 0.205f)
                dy to dx
            }
            AirGestureSensitivity.PRECISE -> {
                val dy = (scale * 1.05f).coerceIn(0.12f, 0.22f)
                val dx = (scale * 1.22f).coerceIn(0.145f, 0.25f)
                dy to dx
            }
        }
    }

    private fun areOpposite(a: GlobalAirGestureDirection, b: GlobalAirGestureDirection): Boolean {
        return (a == GlobalAirGestureDirection.SWIPE_UP && b == GlobalAirGestureDirection.SWIPE_DOWN) ||
            (a == GlobalAirGestureDirection.SWIPE_DOWN && b == GlobalAirGestureDirection.SWIPE_UP) ||
            (a == GlobalAirGestureDirection.SWIPE_LEFT && b == GlobalAirGestureDirection.SWIPE_RIGHT) ||
            (a == GlobalAirGestureDirection.SWIPE_RIGHT && b == GlobalAirGestureDirection.SWIPE_LEFT)
    }

    fun checkPresenceExpired(timestampMs: Long): Boolean {
        if (handTracked && timestampMs - lastHandSeenMs >= handHoldMs) {
            handTracked = false
            samples.clear()
            smoothedX = null
            smoothedY = null
            pinchStartMs = -1L
            twoFingerStartMs = -1L
            return true
        }
        return false
    }

    fun reset() {
        samples.clear()
        smoothedX = null
        smoothedY = null
        pinchStartMs = -1L
        twoFingerStartMs = -1L
        handTracked = false
        lastHandSeenMs = -handHoldMs
        lastSwipeDirection = null
    }
}

class GlobalAirGestureController(
    private val context: Context,
    private val onGesture: (GlobalAirGestureDirection) -> Unit,
    private val onTrackingChanged: (cameraActive: Boolean, handTracked: Boolean, posture: HandPosture, errorStatus: String?) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracker = GlobalAirGestureTracker()

    @Volatile
    private var isStarted = false
    @Volatile
    private var lastFrameAnalyzedMs = 0L
    private var startJob: Job? = null
    private var presenceTickerJob: Job? = null
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var lastReportedHandTracked = false
    private var lastReportedPosture = HandPosture.NONE

    fun isRunning(): Boolean = isStarted

    fun updateSensitivity(sensitivity: AirGestureSensitivity) {
        synchronized(tracker) {
            tracker.setSensitivity(sensitivity)
        }
    }

    fun start(lifecycleOwner: LifecycleOwner) {
        if (isStarted) return
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onTrackingChanged(false, false, HandPosture.NONE, "Kamera izni gerekli")
            return
        }

        isStarted = true
        synchronized(tracker) { tracker.reset() }
        lastReportedHandTracked = false
        lastReportedPosture = HandPosture.NONE
        lastFrameAnalyzedMs = 0L
        startJob?.cancel()
        presenceTickerJob?.cancel()
        onTrackingChanged(false, false, HandPosture.NONE, "EL SENSÖRÜ // HAZIRLANIYOR")

        startJob = scope.launch {
            try {
                val modelBuffer = withContext(Dispatchers.IO) { loadOrFetchModelBuffer() }
                if (!isStarted) return@launch
                if (modelBuffer == null) {
                    isStarted = false
                    onTrackingChanged(false, false, HandPosture.NONE, "El modeli indirilemedi")
                    return@launch
                }

                val landmarker = withContext(Dispatchers.IO) {
                    createHandLandmarker(modelBuffer)
                }
                if (!isStarted) {
                    runCatching { landmarker.close() }
                    return@launch
                }
                handLandmarker = landmarker
                bindFrontCamera(lifecycleOwner)
                startPresenceTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.w(TAG, "Camera access blocked in service context", e)
                stop(silent = true)
                onTrackingChanged(false, false, HandPosture.NONE, "Kamera erişimi kısıtlı")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start GlobalAirGestureController", e)
                stop(silent = true)
                onTrackingChanged(false, false, HandPosture.NONE, "Kamera başlatılamadı")
            }
        }
    }

    fun stop(silent: Boolean = false) {
        if (!isStarted && cameraProvider == null && handLandmarker == null) {
            if (!silent) onTrackingChanged(false, false, HandPosture.NONE, null)
            return
        }
        isStarted = false
        startJob?.cancel()
        startJob = null
        presenceTickerJob?.cancel()
        presenceTickerJob = null
        synchronized(tracker) { tracker.reset() }
        lastReportedHandTracked = false
        lastReportedPosture = HandPosture.NONE

        val provider = cameraProvider
        cameraProvider = null
        if (provider != null) {
            val unbindRunnable = Runnable {
                runCatching { provider.unbindAll() }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                unbindRunnable.run()
            } else {
                mainHandler.post(unbindRunnable)
            }
        }

        val executor = analysisExecutor
        analysisExecutor = null
        val marker = handLandmarker
        handLandmarker = null

        if (marker != null || executor != null) {
            Thread {
                runCatching { marker?.close() }
                runCatching { executor?.shutdown() }
            }.start()
        }

        if (!silent) {
            onTrackingChanged(false, false, HandPosture.NONE, null)
        }
    }

    private fun startPresenceTicker() {
        presenceTickerJob?.cancel()
        presenceTickerJob = scope.launch {
            while (isActive && isStarted) {
                delay(240L)
                val expired = synchronized(tracker) {
                    tracker.checkPresenceExpired(SystemClock.uptimeMillis())
                }
                if (expired && (lastReportedHandTracked || lastReportedPosture != HandPosture.NONE)) {
                    lastReportedHandTracked = false
                    lastReportedPosture = HandPosture.NONE
                    onTrackingChanged(true, false, HandPosture.NONE, null)
                }
            }
        }
    }

    private fun createHandLandmarker(modelBuffer: ByteBuffer): HandLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetBuffer(modelBuffer)
            .build()

        // Relaxed confidence thresholds so angled hands, finger-only gestures, or back-of-hand track smoothly
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.36f)
            .setMinHandPresenceConfidence(0.36f)
            .setMinTrackingConfidence(0.32f)
            .setResultListener { result, _ ->
                handleLandmarksResult(result)
            }
            .setErrorListener { error ->
                Log.w(TAG, "MediaPipe HandLandmarker error: ${error.message}")
            }
            .build()

        return HandLandmarker.createFromOptions(context, options)
    }

    private fun bindFrontCamera(lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!isStarted) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val executor = Executors.newSingleThreadExecutor()
                analysisExecutor = executor

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(480, 360),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(resolutionSelector)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    analyzeFrame(imageProxy)
                }

                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, imageAnalysis)
                onTrackingChanged(true, false, HandPosture.NONE, null)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bindToLifecycle failed", e)
                stop(silent = true)
                onTrackingChanged(false, false, HandPosture.NONE, "Ön kamera kullanılamıyor")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (!isStarted) {
            imageProxy.close()
            return
        }
        val marker = handLandmarker
        if (marker == null) {
            imageProxy.close()
            return
        }

        // Adaptive FPS throttling: ~7 FPS idle (battery/thermal friendly), ~24 FPS while hand tracked
        val nowMs = SystemClock.uptimeMillis()
        val minIntervalMs = if (lastReportedHandTracked) 42L else 135L
        if (nowMs - lastFrameAnalyzedMs < minIntervalMs) {
            imageProxy.close()
            return
        }
        lastFrameAnalyzedMs = nowMs

        try {
            val rawBitmap = imageProxy.toBitmap()
            val uprightBitmap = rotateBitmapIfNeeded(rawBitmap, imageProxy.imageInfo.rotationDegrees)
            if (uprightBitmap !== rawBitmap) {
                rawBitmap.recycle()
            }
            val mpImage = BitmapImageBuilder(uprightBitmap).build()
            marker.detectAsync(mpImage, nowMs)
        } catch (e: Exception) {
            Log.w(TAG, "Global gesture frame analysis skipped: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun handleLandmarksResult(result: HandLandmarkerResult?) {
        if (!isStarted) return
        val rawLandmarks = result?.landmarks()?.firstOrNull()
        val points = rawLandmarks?.map { lm -> HandLandmarkPoint(lm.x(), lm.y()) }
        val nowMs = SystemClock.uptimeMillis()

        val output = synchronized(tracker) {
            tracker.onLandmarks(points, nowMs)
        }

        if (output.isHandTracked != lastReportedHandTracked || output.posture != lastReportedPosture) {
            lastReportedHandTracked = output.isHandTracked
            lastReportedPosture = output.posture
            mainHandler.post {
                if (isStarted) {
                    onTrackingChanged(true, output.isHandTracked, output.posture, null)
                }
            }
        }

        val detectedGesture = output.gesture ?: return
        mainHandler.post {
            if (isStarted) {
                onGesture(detectedGesture)
            }
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun loadOrFetchModelBuffer(): ByteBuffer? {
        try {
            context.assets.open(MODEL_FILE_NAME).use { input ->
                val bytes = input.readBytes()
                if (bytes.isNotEmpty()) {
                    return toDirectByteBuffer(bytes)
                }
            }
        } catch (_: Exception) {
        }

        val cachedFile = File(context.filesDir, MODEL_FILE_NAME)
        if (cachedFile.exists() && cachedFile.length() > 100_000L) {
            val bytes = runCatching { cachedFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return toDirectByteBuffer(bytes)
            }
        }

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(MODEL_DOWNLOAD_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Model download HTTP ${resp.code}")
                    return null
                }
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size < 100_000) return null
                val tmp = File(context.filesDir, "$MODEL_FILE_NAME.tmp")
                tmp.writeBytes(bytes)
                if (cachedFile.exists()) cachedFile.delete()
                tmp.renameTo(cachedFile)
                toDirectByteBuffer(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading hand_landmarker.task", e)
            null
        }
    }

    private fun toDirectByteBuffer(bytes: ByteArray): ByteBuffer {
        return ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }
    }
}
