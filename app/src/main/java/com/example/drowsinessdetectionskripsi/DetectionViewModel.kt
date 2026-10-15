package com.example.drowsinessdetectionskripsi

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import com.example.drowsinessdetectionskripsi.detection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel managing the entire detection pipeline:
 * CameraX frames → Preprocessing → MediaPipe → EAR/MAR → Drowsiness Rules
 */
class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    // Detection engine
    private val detector = DrowsinessDetector()
    private var faceMeshHelper: FaceMeshHelper? = null

    // State
    private val _uiState = MutableStateFlow(DetectionUiState())
    val uiState: StateFlow<DetectionUiState> = _uiState.asStateFlow()

    // ── Camera/analyzer-thread state ─────────────────────────────────────────
    private var frameCounter = 0
    private var fpsCounter = 0
    private var fpsTimestamp = System.nanoTime()
    private var currentFps = 0f
    private var lastPreprocMs = 0f
    private var lastMemMb = 0f
    private var lastCpuPct = 0f
    private var prevCpuTime = 0L
    private var prevWallTime = 0L
    private var isProcessing = false

    // ── Detection-thread state ───────────────────────────────────────────────
    // MediaPipe + drowsiness logic run on their own single thread so the
    // preview is never blocked by detection. Frames arriving while detection
    // is busy skip detection (preview still updates at camera rate).
    private val detectionExecutor = Executors.newSingleThreadExecutor()
    private val detectionBusy = AtomicBoolean(false)
    private var detectBitmap: Bitmap? = null
    private val detectCopyCanvas = Canvas()
    private var detectionCounter = 0
    private var detFpsCounter = 0
    private var detFpsTimestamp = System.nanoTime()
    private var currentDetFps = 0f
    private var lastEar = 0f
    private var lastMar = 0f
    private var lastStatus = "No Face"
    private var lastCompMs = 0f
    private var lastFaceDetected = false
    private var lastLandmarks: List<FloatArray>? = null
    private var lastLatencyMs: Float? = null

    // Monotonic timestamp for MediaPipe VIDEO mode (must strictly increase)
    private var lastDetectTimestampMs = 0L

    // Test mode state (testStartTime written by analyzer, read by detection)
    @Volatile private var testStartTime: Long? = null
    private var testSampleCount = 0
    private val testLogs = mutableListOf<CsvExporter.TestLogEntry>()
    @Volatile private var testDetectionCount = 0

    // Reusable rotate/mirror buffers — double-buffered so Compose never
    // displays the bitmap currently being drawn into
    private var rotateBufA: Bitmap? = null
    private var rotateBufB: Bitmap? = null
    private var rotateFlip = false
    private val rotateMatrix = Matrix()
    private val rotateCanvas = Canvas()
    private val rotatePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Capped test-log preview lines (full data goes to testLogs → CSV)
    private val logPreviewLines = kotlin.collections.ArrayDeque<String>()


    // Cumulative population std dev accumulators (thesis Eq 2.8)
    private var testEarSum = 0.0
    private var testEarSumSq = 0.0
    private var testEarCount = 0
    private var testMarSum = 0.0
    private var testMarSumSq = 0.0
    private var testMarCount = 0

    // Alarm and vibration
    private var isAlarmActive = false
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun initFaceMesh() {
        if (faceMeshHelper == null) {
            try {
                faceMeshHelper = FaceMeshHelper(getApplication())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load face model: ${e.message}")
            }
        }
    }

    fun setPreprocessing(type: PreprocessingType) {
        _uiState.value = _uiState.value.copy(preprocessing = type)
        detector.reset()
        resetFrameState()
    }

    fun startRealTimeMode() {
        detector.reset()
        resetFrameState()
        primeCpuMeasurement()
        _uiState.value = _uiState.value.copy(
            mode = DetectionMode.REALTIME,
            isRunning = true,
            testComplete = false
        )
    }

    fun startTestMode() {
        detector.reset()
        resetFrameState()
        primeCpuMeasurement()
        testStartTime = null
        testSampleCount = 0
        testLogs.clear()
        logPreviewLines.clear()
        testEarSum = 0.0; testEarSumSq = 0.0; testEarCount = 0
        testMarSum = 0.0; testMarSumSq = 0.0; testMarCount = 0
        testDetectionCount = 0
        _uiState.value = _uiState.value.copy(
            mode = DetectionMode.TESTING,
            isRunning = true,
            testComplete = false,
            testElapsed = 0f,
            testSamples = 0,
            testLogPreview = "",
            groundTruthDisplay = "Get ready \u2014 look at camera",
            groundTruthPhase = "normal",
            summary = null
        )
    }

    fun stopDetection() {
        _uiState.value = _uiState.value.copy(isRunning = false)
        resetFrameState()
        stopAlarm()
    }

    fun toggleLandmarks() {
        _uiState.value = _uiState.value.copy(showLandmarks = !_uiState.value.showLandmarks)
    }

    /**
     * Called from CameraX ImageAnalysis analyzer for each frame.
     *
     * The camera thread only converts, preprocesses, and publishes the display
     * frame — so the preview runs at camera rate (~30 fps) with minimal delay.
     * MediaPipe detection runs on its own thread (runDetection) on the most
     * recent frame; frames arriving while detection is busy skip detection but
     * still update the preview. Drowsiness threshold logic runs every
     * SAMPLE_INTERVAL-th detection (same cadence as before).
     */
    fun processFrame(imageProxy: ImageProxy) {
        if (!_uiState.value.isRunning || isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true

        try {
            // Convert and release camera frame immediately
            val bitmap = imageProxyToBitmap(imageProxy)
            imageProxy.close()

            if (bitmap == null) return

            frameCounter++
            fpsCounter++

            // Preview FPS (camera rate)
            val now = System.nanoTime()
            val elapsed = (now - fpsTimestamp) / 1_000_000_000.0
            if (elapsed >= 1.0) {
                currentFps = (fpsCounter / elapsed).toFloat()
                fpsCounter = 0
                fpsTimestamp = now
            }

            val state = _uiState.value

            // t_input: start of the full pipeline (before preprocessing)
            val tInput = System.nanoTime()

            val preprocessed = if (state.preprocessing == PreprocessingType.NONE) {
                lastPreprocMs = 0f
                bitmap
            } else {
                val tPre = System.nanoTime()
                val result = ImagePreprocessor.process(bitmap, state.preprocessing)
                lastPreprocMs = (System.nanoTime() - tPre) / 1_000_000f
                result
            }

            // Sample resource usage every 30 frames
            if (frameCounter % 30 == 0) {
                val rt = Runtime.getRuntime()
                val used = (rt.totalMemory() - rt.freeMemory()) + Debug.getNativeHeapAllocatedSize()
                lastMemMb = used / (1024f * 1024f)

                // CPU usage from /proc/self/stat
                try {
                    val stat = java.io.File("/proc/self/stat").readText()
                    val fields = stat.split(" ")
                    val utime = fields[13].toLong()
                    val stime = fields[14].toLong()
                    val cpuTime = utime + stime
                    val wallTime = System.nanoTime() / 1_000_000L

                    if (prevWallTime > 0) {
                        val cpuDelta = (cpuTime - prevCpuTime) * 10 // jiffies to ms
                        val wallDelta = wallTime - prevWallTime
                        if (wallDelta > 0) {
                            val cores = rt.availableProcessors()
                            lastCpuPct = (cpuDelta.toFloat() / wallDelta / cores * 100f).coerceIn(0f, 100f)
                        }
                    }
                    prevCpuTime = cpuTime
                    prevWallTime = wallTime
                } catch (_: Exception) { }
            }

            // Testing mode: elapsed time, completion check, and ground-truth
            // instruction are time-based — handled on the camera thread so the
            // countdown/instructions stay smooth even if detection is slow
            var testElapsed = -1f
            var gtDisplay = ""
            var gtPhase = ""
            if (state.mode == DetectionMode.TESTING) {
                if (testStartTime == null) testStartTime = System.nanoTime()
                testElapsed = (System.nanoTime() - testStartTime!!) / 1_000_000_000f
                if (testElapsed >= Constants.TEST_DURATION_S) {
                    completeTest()
                    return
                }
                val gt = ActionSchedule.getGroundTruth(testElapsed)
                gtDisplay = gt.displayText
                gtPhase = gt.phase
            }

            // Publish the preview frame immediately — display never waits for detection
            when (state.mode) {
                DetectionMode.REALTIME -> _uiState.update {
                    it.copy(
                        displayBitmap = preprocessed,
                        currentFps = currentFps,
                        currentPreprocMs = lastPreprocMs,
                        currentMemMb = lastMemMb,
                        currentCpuPct = lastCpuPct
                    )
                }
                DetectionMode.TESTING -> _uiState.update {
                    it.copy(
                        displayBitmap = preprocessed,
                        currentFps = currentFps,
                        currentPreprocMs = lastPreprocMs,
                        currentMemMb = lastMemMb,
                        currentCpuPct = lastCpuPct,
                        testElapsed = testElapsed,
                        groundTruthDisplay = gtDisplay,
                        groundTruthPhase = gtPhase
                    )
                }
                else -> {}
            }

            // Hand the latest frame to the detection thread if it's idle.
            // Copy into a detection-owned bitmap: the rotate/preprocess buffers
            // get overwritten by upcoming frames while detection is running.
            if (detectionBusy.compareAndSet(false, true)) {
                val det = getDetectBitmap(preprocessed.width, preprocessed.height)
                detectCopyCanvas.setBitmap(det)
                detectCopyCanvas.drawBitmap(preprocessed, 0f, 0f, null)
                detectCopyCanvas.setBitmap(null)
                val mode = state.mode
                try {
                    detectionExecutor.execute { runDetection(det, tInput, mode) }
                } catch (e: Exception) {
                    detectionBusy.set(false) // executor shut down
                }
            }

        } catch (e: Exception) {
            // Silently handle frame processing errors
        } finally {
            imageProxy.close() // Safe to call again (idempotent)
            isProcessing = false
        }
    }

    /**
     * Runs on the dedicated detection thread: MediaPipe landmarks, EAR/MAR,
     * drowsiness sampling, and (in testing mode) CSV log entries.
     * Publishes detection results into the UI state when done.
     */
    private fun runDetection(bitmap: Bitmap, tInputNs: Long, mode: DetectionMode) {
        try {
            // VIDEO mode requires strictly increasing timestamps
            val detectTs = maxOf(SystemClock.uptimeMillis(), lastDetectTimestampMs + 1)
            lastDetectTimestampMs = detectTs

            val t0 = System.nanoTime()
            val landmarks = faceMeshHelper?.detectLandmarks(bitmap, detectTs)
            val compMs = (System.nanoTime() - t0) / 1_000_000f
            lastCompMs = compMs

            if (landmarks != null) {
                lastFaceDetected = true
                lastLandmarks = landmarks
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()
                val earL = detector.calculateEar(landmarks, Constants.LEFT_EYE, w, h)
                val earR = detector.calculateEar(landmarks, Constants.RIGHT_EYE, w, h)
                lastEar = (earL + earR) / 2f
                lastMar = detector.calculateMar(landmarks, Constants.MOUTH, w, h)
            } else {
                lastFaceDetected = false
                lastLandmarks = null
                detector.resetCounters()
            }

            detectionCounter++
            detFpsCounter++
            val now = System.nanoTime()
            val elapsed = (now - detFpsTimestamp) / 1_000_000_000.0
            if (elapsed >= 1.0) {
                currentDetFps = (detFpsCounter / elapsed).toFloat()
                detFpsCounter = 0
                detFpsTimestamp = now
            }
            if (mode == DetectionMode.TESTING && testStartTime != null) testDetectionCount++

            // Drowsiness threshold logic runs only every SAMPLE_INTERVAL-th
            // detection (same cadence as the previous every-5th-frame gating)
            val isSampleFrame = detectionCounter % Constants.SAMPLE_INTERVAL == 0
            var previewStr: String? = null

            if (mode == DetectionMode.REALTIME) {
                if (isSampleFrame && lastFaceDetected) {
                    val sample = detector.processSample(lastEar, lastMar, compMs)
                    lastStatus = sample.systemStatus
                    if (sample.classificationLatencyS != null) {
                        lastLatencyMs = sample.classificationLatencyS * 1000f
                    }
                } else if (!lastFaceDetected) {
                    lastStatus = "No Face"
                }
            } else if (mode == DetectionMode.TESTING) {
                if (!lastFaceDetected) lastStatus = "No Face"
                if (isSampleFrame) previewStr = logTestSample(compMs, tInputNs)
            }

            // Don't publish after the session was stopped/completed
            if (!_uiState.value.isRunning) return

            _uiState.update { s ->
                s.copy(
                    currentEar = lastEar,
                    currentMar = lastMar,
                    currentCompMs = lastCompMs,
                    currentDetFps = currentDetFps,
                    currentLandmarks = lastLandmarks,
                    lastClassificationLatencyMs = lastLatencyMs,
                    earBreached = lastFaceDetected && lastEar < Constants.EAR_THRESHOLD,
                    marBreached = lastFaceDetected && lastMar > Constants.MAR_THRESHOLD,
                    currentStatus = lastStatus,
                    faceDetected = lastFaceDetected,
                    testSamples = testSampleCount,
                    testLogPreview = previewStr ?: s.testLogPreview
                )
            }
            updateAlertState()
        } catch (e: Exception) {
            // Silently handle detection errors
        } finally {
            detectionBusy.set(false)
        }
    }

    /**
     * Builds one test-mode log entry (called every SAMPLE_INTERVAL-th detection
     * on the detection thread). Returns the capped log-preview string, or null
     * if nothing was logged.
     */
    private fun logTestSample(compMs: Float, tInputNs: Long): String? {
        val startNs = testStartTime ?: return null
        val elapsed = (System.nanoTime() - startNs) / 1_000_000_000f
        if (elapsed >= Constants.TEST_DURATION_S) return null

        // Ground truth — time-based, computed from Constants.EXPECTED_FPS (fixed, no gaps)
        val gt = ActionSchedule.getGroundTruth(elapsed)

        testSampleCount++
        val (_, ramMb) = getResourceUsage()
        var logPreviewLine = ""

        val entry = if (lastFaceDetected) {
            val sample = detector.processSample(lastEar, lastMar, compMs)
            // t_output: immediately after classification decision is produced
            val latencyMs = (System.nanoTime() - tInputNs) / 1_000_000f
            lastStatus = sample.systemStatus

            // Cumulative population std dev (thesis Eq 2.8)
            testEarSum += sample.avgEar.toDouble()
            testEarSumSq += (sample.avgEar.toDouble()) * sample.avgEar.toDouble()
            testEarCount++
            val earCumStd = if (testEarCount > 1) {
                val mean = testEarSum / testEarCount
                kotlin.math.sqrt(maxOf(0.0, testEarSumSq / testEarCount - mean * mean))
            } else 0.0

            testMarSum += sample.mar.toDouble()
            testMarSumSq += (sample.mar.toDouble()) * sample.mar.toDouble()
            testMarCount++
            val marCumStd = if (testMarCount > 1) {
                val mean = testMarSum / testMarCount
                kotlin.math.sqrt(maxOf(0.0, testMarSumSq / testMarCount - mean * mean))
            } else 0.0

            val e = CsvExporter.TestLogEntry(
                sample = testSampleCount,
                timestamp = elapsed,
                avgEar = String.format(Locale.US, "%.4f", sample.avgEar),
                mar = String.format(Locale.US, "%.4f", sample.mar),
                earStdDev = String.format(Locale.US, "%.4f", earCumStd),
                marStdDev = String.format(Locale.US, "%.4f", marCumStd),
                computationTimeMs = lastPreprocMs,
                classificationLatencyMs = String.format(Locale.US, "%.3f", latencyMs),
                cpuUsagePct = lastCpuPct,
                ramUsageMb = ramMb,
                groundTruth = gt.groundTruth,
                systemStatus = sample.systemStatus,
                ruleTriggered = sample.ruleTriggered ?: "None"
            )
            val rs = when {
                "Rule 1" in (sample.ruleTriggered ?: "") -> "R1"
                "Rule 2" in (sample.ruleTriggered ?: "") -> "R2"
                "Rule 3" in (sample.ruleTriggered ?: "") -> "R3"
                else -> ""
            }
            logPreviewLine = " %3d |%.4f|%.4f|%5.0fms|%-8s|%-8s|%s".format(
                testSampleCount, sample.avgEar, sample.mar, sample.computationTimeMs,
                gt.groundTruth, sample.systemStatus, rs
            )
            e
        } else {
            lastStatus = "No Face"
            val e = CsvExporter.TestLogEntry(
                sample = testSampleCount,
                timestamp = elapsed,
                avgEar = "", mar = "", earStdDev = "", marStdDev = "",
                computationTimeMs = compMs,
                classificationLatencyMs = "",
                cpuUsagePct = lastCpuPct, ramUsageMb = ramMb,
                groundTruth = gt.groundTruth,
                systemStatus = "No Face",
                ruleTriggered = ""
            )
            logPreviewLine = " %3d |  --  |  --  |%5.0fms|%-8s|NoFace  |".format(
                testSampleCount, compMs, gt.groundTruth
            )
            e
        }

        synchronized(testLogs) {
            // completeTest() snapshots this list after setting isRunning=false;
            // never add entries past that point
            if (!_uiState.value.isRunning) return null
            testLogs.add(entry)
        }

        logPreviewLines.addLast(logPreviewLine)
        while (logPreviewLines.size > Constants.LOG_PREVIEW_MAX_LINES) {
            logPreviewLines.removeFirst()
        }
        return logPreviewLines.joinToString("\n")
    }

    private fun getDetectBitmap(w: Int, h: Int): Bitmap {
        val cur = detectBitmap
        if (cur != null && cur.width == w && cur.height == h && !cur.isRecycled) return cur
        val fresh = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        detectBitmap = fresh
        return fresh
    }

    private fun completeTest() {
        _uiState.update { it.copy(isRunning = false) }
        stopAlarm()

        val logsSnapshot = synchronized(testLogs) { testLogs.toList() }
        if (logsSnapshot.isEmpty()) return

        // Avg FPS = detection throughput (frames actually processed by
        // MediaPipe), not the decoupled preview/camera rate
        val avgFps = testDetectionCount / Constants.TEST_DURATION_S
        val summary = CsvExporter.buildSummary(logsSnapshot, avgFps)

        _uiState.update {
            it.copy(
                testComplete = true,
                summary = summary,
                testElapsed = Constants.TEST_DURATION_S
            )
        }
    }

    /**
     * Write CSV to the user-chosen URI (via SAF file picker).
     */
    fun saveCsvToUri(contentResolver: ContentResolver, uri: Uri) {
        val state = _uiState.value
        val summary = state.summary
        if (summary == null) {
            _uiState.value = _uiState.value.copy(saveResult = "Error: No summary available")
            return
        }
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                CsvExporter.writeCsv(outputStream, state.preprocessing.displayName, testLogs, summary)
            } ?: run {
                _uiState.value = _uiState.value.copy(saveResult = "Error: Could not open file")
                return
            }
            val path = uri.lastPathSegment ?: uri.toString()
            _uiState.value = _uiState.value.copy(saveResult = "Saved to: $path")
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(saveResult = "Error: ${e.message}")
        }
    }

    fun getDefaultFilename(): String {
        val prep = _uiState.value.preprocessing.displayName.replace(" ", "_").lowercase()
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "test_${prep}_$date"
    }

    // ── Alarm & vibration ─────────────────────────────────────────────────────

    /**
     * Starts alarm + vibration when drowsy, stops them when no longer drowsy.
     * Called from the detection thread; stopAlarm is also called from the
     * main/camera threads on stop/completion — hence @Synchronized.
     */
    @Synchronized
    private fun updateAlertState() {
        if (!_uiState.value.isRunning) return
        val isDrowsy = lastStatus.contains("drowsy", ignoreCase = true)
        if (isDrowsy && !isAlarmActive) startAlarm()
        else if (!isDrowsy && isAlarmActive) stopAlarm()
    }

    @Synchronized
    private fun startAlarm() {
        isAlarmActive = true
        // MediaPlayer — create once, loop the 1-second clip indefinitely
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(getApplication(), R.raw.alarm_beep)?.apply {
                isLooping = true
            }
        }
        mediaPlayer?.start()
        // Vibration — 700 ms on, 300 ms off, repeat from index 0
        ensureVibrator()
        val effect = VibrationEffect.createWaveform(longArrayOf(0L, 700L, 300L), 0)
        vibrator?.vibrate(effect)
    }

    @Synchronized
    private fun stopAlarm() {
        if (!isAlarmActive) return
        isAlarmActive = false
        mediaPlayer?.let { mp -> if (mp.isPlaying) { mp.pause(); mp.seekTo(0) } }
        vibrator?.cancel()
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun ensureVibrator() {
        if (vibrator != null) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getApplication<Application>()
                .getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            getApplication<Application>().getSystemService(Vibrator::class.java)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun primeCpuMeasurement() {
        try {
            val stat = java.io.File("/proc/self/stat").readText()
            val fields = stat.split(" ")
            prevCpuTime = fields[13].toLong() + fields[14].toLong()
            prevWallTime = System.nanoTime() / 1_000_000L
        } catch (_: Exception) { }
    }

    private fun resetFrameState() {
        frameCounter = 0
        fpsCounter = 0
        fpsTimestamp = System.nanoTime()
        currentFps = 0f
        detectionCounter = 0
        detFpsCounter = 0
        detFpsTimestamp = System.nanoTime()
        currentDetFps = 0f
        lastEar = 0f
        lastMar = 0f
        lastStatus = "No Face"
        lastCompMs = 0f
        lastFaceDetected = false
        lastLandmarks = null
        lastLatencyMs = null
    }

    private fun getResourceUsage(): Pair<Float, Float> {
        return try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            val ramMb = memInfo.totalPss / 1024f
            val cpuPct = 0f // Accurate CPU% requires root; omit for mobile
            Pair(cpuPct, ramMb)
        } catch (e: Exception) {
            Pair(0f, 0f)
        }
    }

    /**
     * Converts ImageProxy to a rotation-corrected, mirror-corrected Bitmap.
     * Uses CameraX's native toBitmap() — much faster than the manual
     * NV21→JPEG→Bitmap roundtrip (avoids JPEG compression/decompression).
     *
     * The rotated/mirrored result is drawn into a reused double-buffered
     * bitmap (no per-frame allocation). Callers must NOT recycle the result.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val rawBitmap = imageProxy.toBitmap()

            val rotation = imageProxy.imageInfo.rotationDegrees
            val rawW = rawBitmap.width
            val rawH = rawBitmap.height
            val outW = if (rotation == 90 || rotation == 270) rawH else rawW
            val outH = if (rotation == 90 || rotation == 270) rawW else rawH

            rotateMatrix.reset()
            if (rotation != 0) {
                // Rotate around source center, then shift so the rotated
                // bounding box fills the output exactly
                rotateMatrix.postRotate(rotation.toFloat(), rawW / 2f, rawH / 2f)
                rotateMatrix.postTranslate((outW - rawW) / 2f, (outH - rawH) / 2f)
            }
            if (Constants.USE_FRONT_CAMERA) {
                rotateMatrix.postScale(-1f, 1f, outW / 2f, outH / 2f)
            }

            if (rotateMatrix.isIdentity) {
                rawBitmap
            } else {
                val out = getRotateBuffer(outW, outH)
                rotateCanvas.setBitmap(out)
                rotateCanvas.drawBitmap(rawBitmap, rotateMatrix, rotatePaint)
                rotateCanvas.setBitmap(null)
                rawBitmap.recycle()
                out
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getRotateBuffer(w: Int, h: Int): Bitmap {
        rotateFlip = !rotateFlip
        val cur = if (rotateFlip) rotateBufA else rotateBufB
        if (cur != null && cur.width == w && cur.height == h && !cur.isRecycled) return cur
        val fresh = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (rotateFlip) rotateBufA = fresh else rotateBufB = fresh
        return fresh
    }

    override fun onCleared() {
        super.onCleared()
        // Drain the detection thread before closing the landmarker it uses
        detectionExecutor.shutdown()
        try {
            detectionExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) { }
        faceMeshHelper?.close()
        stopAlarm()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

enum class DetectionMode {
    NONE, REALTIME, TESTING
}

data class DetectionUiState(
    val mode: DetectionMode = DetectionMode.NONE,
    val isRunning: Boolean = false,
    val preprocessing: PreprocessingType = PreprocessingType.NONE,

    // Display
    val displayBitmap: Bitmap? = null,
    val currentEar: Float = 0f,
    val currentMar: Float = 0f,
    val currentFps: Float = 0f,
    val currentDetFps: Float = 0f,
    val currentCompMs: Float = 0f,
    val currentPreprocMs: Float = 0f,
    val currentMemMb: Float = 0f,
    val currentCpuPct: Float = 0f,
    val currentLandmarks: List<FloatArray>? = null,
    val showLandmarks: Boolean = false,
    val lastClassificationLatencyMs: Float? = null,
    val earBreached: Boolean = false,
    val marBreached: Boolean = false,
    val currentStatus: String = "No Face",
    val faceDetected: Boolean = false,

    // Test mode
    val testElapsed: Float = 0f,
    val testSamples: Int = 0,
    val testComplete: Boolean = false,
    val groundTruthDisplay: String = "",
    val groundTruthPhase: String = "normal",
    val testLogPreview: String = "",
    val summary: CsvExporter.TestSummary? = null,

    // Save
    val saveResult: String? = null,

    // Error
    val errorMessage: String? = null
)
