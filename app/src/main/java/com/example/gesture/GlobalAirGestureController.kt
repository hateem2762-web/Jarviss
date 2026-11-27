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
}

data class GlobalAirGestureHudState(
    val isEnabled: Boolean = false,
    val isCameraActive: Boolean = false,
    val isWaitingForVideoApp: Boolean = false,
    val isHandTracked: Boolean = false,
    val statusLabel: String = "EL KONTROLÜ // KAPALI",
    val lastActionLabel: String = "",
    val lastActionTimestampMs: Long = 0L,
    val triggerCount: Long = 0L,
)

/**
 * Pure Kotlin 4-direction hand swipe tracker with cooldown & hand presence hysteresis.
 * Image-space coordinates:
 * - userSpaceX: 0f (user's left) .. 1f (user's right)
 * - y: 0f (top) .. 1f (bottom)
 * Therefore moving hand UP produces negative dy, moving hand DOWN produces positive dy.
 */
class GlobalAirGestureTracker(
    private val cooldownMs: Long = 1_100L,
    private val minDisplacementY: Float = 0.16f,
    private val minDisplacementX: Float = 0.20f,
    private val maxWindowMs: Long = 550L,
    private val maxCrossAxisRatio: Float = 0.78f,
    private val handHoldMs: Long = 700L,
) {
    private data class Sample(
        val timestampMs: Long,
        val x: Float,
        val y: Float,
    )

    data class FrameOutput(
        val gesture: GlobalAirGestureDirection?,
        val isHandTracked: Boolean,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastTriggerMs: Long = -cooldownMs
    private var lastHandSeenMs: Long = -handHoldMs
    private var handTracked: Boolean = false

    fun onFrame(
        hasHand: Boolean,
        userSpaceX: Float,
        y: Float,
        timestampMs: Long,
    ): FrameOutput {
        if (!hasHand) {
            if (samples.isNotEmpty() && timestampMs - lastHandSeenMs > 280L) {
                samples.clear()
            }
            if (handTracked && timestampMs - lastHandSeenMs >= handHoldMs) {
                handTracked = false
            }
            return FrameOutput(gesture = null, isHandTracked = handTracked)
        }

        lastHandSeenMs = timestampMs
        handTracked = true

        val last = samples.lastOrNull()
        if (last != null && timestampMs - last.timestampMs > 280L) {
            samples.clear()
        }
        samples.addLast(Sample(timestampMs, userSpaceX, y))
        while (samples.isNotEmpty() && timestampMs - samples.first().timestampMs > maxWindowMs) {
            samples.removeFirst()
        }

        if (samples.size < 3 || timestampMs - lastTriggerMs < cooldownMs) {
            return FrameOutput(gesture = null, isHandTracked = true)
        }

        val oldest = samples.first()
        val dx = userSpaceX - oldest.x
        val dy = y - oldest.y
        val absDx = abs(dx)
        val absDy = abs(dy)

        val gesture = when {
            absDy >= minDisplacementY && absDy >= absDx && absDx <= absDy * maxCrossAxisRatio -> {
                if (dy < 0f) GlobalAirGestureDirection.SWIPE_UP else GlobalAirGestureDirection.SWIPE_DOWN
            }
            absDx >= minDisplacementX && absDx > absDy && absDy <= absDx * maxCrossAxisRatio -> {
                if (dx > 0f) GlobalAirGestureDirection.SWIPE_RIGHT else GlobalAirGestureDirection.SWIPE_LEFT
            }
            else -> null
        }

        if (gesture != null) {
            lastTriggerMs = timestampMs
            samples.clear()
        }
        return FrameOutput(gesture = gesture, isHandTracked = true)
    }

    fun checkPresenceExpired(timestampMs: Long): Boolean {
        if (handTracked && timestampMs - lastHandSeenMs >= handHoldMs) {
            handTracked = false
            samples.clear()
            return true
        }
        return false
    }

    fun isHandCurrentlyTracked(): Boolean = handTracked

    fun reset() {
        samples.clear()
        handTracked = false
        lastHandSeenMs = -handHoldMs
    }
}

class GlobalAirGestureController(
    private val context: Context,
    private val onGesture: (GlobalAirGestureDirection) -> Unit,
    private val onTrackingChanged: (cameraActive: Boolean, handTracked: Boolean, errorStatus: String?) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracker = GlobalAirGestureTracker(cooldownMs = 1_100L)

    @Volatile
    private var isStarted = false
    private var startJob: Job? = null
    private var presenceTickerJob: Job? = null
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var lastReportedHandTracked = false

    fun isRunning(): Boolean = isStarted

    fun start(lifecycleOwner: LifecycleOwner) {
        if (isStarted) return
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onTrackingChanged(false, false, "Kamera izni gerekli")
            return
        }

        isStarted = true
        tracker.reset()
        lastReportedHandTracked = false
        startJob?.cancel()
        presenceTickerJob?.cancel()
        onTrackingChanged(false, false, "EL SENSÖRÜ // HAZIRLANIYOR")

        startJob = scope.launch {
            try {
                val modelBuffer = withContext(Dispatchers.IO) { loadOrFetchModelBuffer() }
                if (!isStarted) return@launch
                if (modelBuffer == null) {
                    isStarted = false
                    onTrackingChanged(false, false, "El modeli indirilemedi")
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
                onTrackingChanged(false, false, "Kamera erişimi kısıtlı")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start GlobalAirGestureController", e)
                stop(silent = true)
                onTrackingChanged(false, false, "Kamera başlatılamadı")
            }
        }
    }

    fun stop(silent: Boolean = false) {
        if (!isStarted && cameraProvider == null && handLandmarker == null) {
            if (!silent) onTrackingChanged(false, false, null)
            return
        }
        isStarted = false
        startJob?.cancel()
        startJob = null
        presenceTickerJob?.cancel()
        presenceTickerJob = null
        tracker.reset()
        lastReportedHandTracked = false

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
            onTrackingChanged(false, false, null)
        }
    }

    private fun startPresenceTicker() {
        presenceTickerJob?.cancel()
        presenceTickerJob = scope.launch {
            while (isActive && isStarted) {
                delay(260L)
                val expired = synchronized(tracker) {
                    tracker.checkPresenceExpired(SystemClock.uptimeMillis())
                }
                if (expired && lastReportedHandTracked) {
                    lastReportedHandTracked = false
                    onTrackingChanged(true, false, null)
                }
            }
        }
    }

    private fun createHandLandmarker(modelBuffer: ByteBuffer): HandLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetBuffer(modelBuffer)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.50f)
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
                onTrackingChanged(true, false, null)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bindToLifecycle failed", e)
                stop(silent = true)
                onTrackingChanged(false, false, "Ön kamera kullanılamıyor")
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

        try {
            val rawBitmap = imageProxy.toBitmap()
            val uprightBitmap = rotateBitmapIfNeeded(rawBitmap, imageProxy.imageInfo.rotationDegrees)
            if (uprightBitmap !== rawBitmap) {
                rawBitmap.recycle()
            }
            val mpImage = BitmapImageBuilder(uprightBitmap).build()
            val frameTimeMs = SystemClock.uptimeMillis()
            marker.detectAsync(mpImage, frameTimeMs)
        } catch (e: Exception) {
            Log.w(TAG, "Global gesture frame analysis skipped: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun handleLandmarksResult(result: HandLandmarkerResult?) {
        if (!isStarted) return
        val landmarks = result?.landmarks()?.firstOrNull()
        val nowMs = SystemClock.uptimeMillis()

        val output = synchronized(tracker) {
            if (landmarks == null || landmarks.size <= 9) {
                tracker.onFrame(hasHand = false, userSpaceX = 0f, y = 0f, timestampMs = nowMs)
            } else {
                val palmRawX = (landmarks[0].x() + landmarks[9].x()) * 0.5f
                val palmY = (landmarks[0].y() + landmarks[9].y()) * 0.5f
                val userSpaceX = (1f - palmRawX).coerceIn(0f, 1f)
                tracker.onFrame(hasHand = true, userSpaceX = userSpaceX, y = palmY, timestampMs = nowMs)
            }
        }

        if (output.isHandTracked != lastReportedHandTracked) {
            lastReportedHandTracked = output.isHandTracked
            mainHandler.post {
                if (isStarted) {
                    onTrackingChanged(true, output.isHandTracked, null)
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
