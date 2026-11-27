package com.example.news

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "AirGestureController"
private const val MODEL_FILE_NAME = "hand_landmarker.task"
private const val MODEL_DOWNLOAD_URL =
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"

enum class HandSwipeGesture {
    SWIPE_RIGHT,
    SWIPE_LEFT,
}

/**
 * Pure Kotlin horizontal air-swipe recognizer with 1.2s cooldown to avoid accidental double skips.
 * Expects normalized user-space coordinates where x=0 is user's left and x=1 is user's right.
 */
class SwipeGestureTracker(
    private val cooldownMs: Long = 1_200L,
    private val minDisplacementX: Float = 0.21f,
    private val maxWindowMs: Long = 500L,
    private val maxVerticalRatio: Float = 0.90f,
) {
    private data class Sample(
        val timestampMs: Long,
        val x: Float,
        val y: Float,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastTriggerMs: Long = -cooldownMs

    fun onHandPosition(userSpaceX: Float, userSpaceY: Float, timestampMs: Long): HandSwipeGesture? {
        // Evict stale points or large gaps (hand exited and re-entered)
        val last = samples.lastOrNull()
        if (last != null && timestampMs - last.timestampMs > 280L) {
            samples.clear()
        }
        samples.addLast(Sample(timestampMs, userSpaceX, userSpaceY))
        while (samples.isNotEmpty() && timestampMs - samples.first().timestampMs > maxWindowMs) {
            samples.removeFirst()
        }

        if (samples.size < 3) return null
        if (timestampMs - lastTriggerMs < cooldownMs) return null

        val oldest = samples.first()
        val dx = userSpaceX - oldest.x
        val dy = abs(userSpaceY - oldest.y)

        if (abs(dx) >= minDisplacementX && dy <= abs(dx) * maxVerticalRatio) {
            lastTriggerMs = timestampMs
            samples.clear()
            return if (dx > 0f) {
                HandSwipeGesture.SWIPE_RIGHT
            } else {
                HandSwipeGesture.SWIPE_LEFT
            }
        }
        return null
    }

    fun reset() {
        samples.clear()
    }
}

class AirGestureController(
    private val context: Context,
    private val onSwipe: (HandSwipeGesture) -> Unit,
    private val onStatusChanged: (active: Boolean, statusText: String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val swipeTracker = SwipeGestureTracker(cooldownMs = 1_200L)

    @Volatile
    private var isStarted = false
    private var startJob: Job? = null
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null

    fun start(lifecycleOwner: LifecycleOwner) {
        if (isStarted) return
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onStatusChanged(false, "Kamera izni gerekli")
            return
        }

        isStarted = true
        swipeTracker.reset()
        startJob?.cancel()
        onStatusChanged(false, "El sensörü hazırlanıyor…")

        startJob = scope.launch {
            try {
                val modelBuffer = withContext(Dispatchers.IO) { loadOrFetchModelBuffer() }
                if (!isStarted) return@launch
                if (modelBuffer == null) {
                    isStarted = false
                    onStatusChanged(false, "El algılama modeli yüklenemedi")
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.w(TAG, "Camera access blocked in current context", e)
                stop()
                onStatusChanged(false, "Kamera bu pencerede kısıtlı")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AirGestureController", e)
                stop()
                onStatusChanged(false, "El sensörü başlatılamadı")
            }
        }
    }

    fun stop() {
        isStarted = false
        startJob?.cancel()
        startJob = null
        swipeTracker.reset()

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
        onStatusChanged(false, "")
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
                Log.w(TAG, "MediaPipe HandLandmarker runtime error: ${error.message}")
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
                onStatusChanged(true, "EL KONTROLÜ // AKTİF")
            } catch (e: Exception) {
                Log.e(TAG, "CameraX bindToLifecycle failed", e)
                stop()
                onStatusChanged(false, "Ön kamera kullanılamıyor")
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
            Log.w(TAG, "Frame analysis skipped: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun handleLandmarksResult(result: HandLandmarkerResult?) {
        if (!isStarted || result == null) return
        val landmarks = result.landmarks().firstOrNull()
        if (landmarks == null || landmarks.size <= 9) return

        // Use average of wrist (0) and middle finger MCP (9) for stable palm center tracking
        val palmRawX = (landmarks[0].x() + landmarks[9].x()) * 0.5f
        val palmY = (landmarks[0].y() + landmarks[9].y()) * 0.5f
        // Front camera is mirrored relative to user's perspective: user right = (1f - rawX)
        val userSpaceX = (1f - palmRawX).coerceIn(0f, 1f)
        val nowMs = SystemClock.uptimeMillis()

        val gesture = swipeTracker.onHandPosition(userSpaceX, palmY, nowMs) ?: return
        mainHandler.post {
            if (isStarted) {
                onSwipe(gesture)
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
        // 1. Check bundled asset first
        try {
            context.assets.open(MODEL_FILE_NAME).use { input ->
                val bytes = input.readBytes()
                if (bytes.isNotEmpty()) {
                    return toDirectByteBuffer(bytes)
                }
            }
        } catch (_: Exception) {
        }

        // 2. Check cached file in app-private storage
        val cachedFile = File(context.filesDir, MODEL_FILE_NAME)
        if (cachedFile.exists() && cachedFile.length() > 100_000L) {
            val bytes = runCatching { cachedFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return toDirectByteBuffer(bytes)
            }
        }

        // 3. Download official MediaPipe Hand Landmarker model once and cache privately
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
