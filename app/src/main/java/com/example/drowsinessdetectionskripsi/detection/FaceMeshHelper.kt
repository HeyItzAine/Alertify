package com.example.drowsinessdetectionskripsi.detection

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Wrapper around MediaPipe FaceLandmarker.
 * Initializes the model from assets and provides landmark detection.
 *
 * Runs in VIDEO mode so the face is tracked between frames; the full face
 * detector only re-runs when tracking confidence drops below the threshold.
 */
class FaceMeshHelper(context: Context) {

    private val faceLandmarker: FaceLandmarker

    // Double-buffered reusable landmark buffers (zero per-frame allocations).
    // We alternate which buffer we return each detection so the List reference
    // ACTUALLY CHANGES every frame — Compose compares the param by reference and
    // would otherwise skip recomposition (freezing the overlay until EAR/MAR
    // crossed a threshold). Double-buffering also keeps the buffer the UI is
    // currently drawing from being overwritten by the next detection.
    private val landmarkBufferA = Array(LANDMARK_COUNT) { FloatArray(2) }
    private val landmarkBufferB = Array(LANDMARK_COUNT) { FloatArray(2) }
    private val landmarkListA = landmarkBufferA.toList()
    private val landmarkListB = landmarkBufferB.toList()
    private var useBufferA = true

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.VIDEO)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    /**
     * @param timestampMs monotonically increasing frame timestamp (required by VIDEO mode)
     */
    fun detectLandmarks(bitmap: Bitmap, timestampMs: Long): List<FloatArray>? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result: FaceLandmarkerResult = faceLandmarker.detectForVideo(mpImage, timestampMs)

        if (result.faceLandmarks().isEmpty()) return null

        // Alternate buffers each detection so the returned reference changes
        useBufferA = !useBufferA
        val buffer = if (useBufferA) landmarkBufferA else landmarkBufferB
        val list = if (useBufferA) landmarkListA else landmarkListB

        val landmarks = result.faceLandmarks()[0]
        val n = minOf(landmarks.size, LANDMARK_COUNT)
        for (i in 0 until n) {
            buffer[i][0] = landmarks[i].x()
            buffer[i][1] = landmarks[i].y()
        }
        return list
    }

    fun close() {
        faceLandmarker.close()
    }

    companion object {
        private const val LANDMARK_COUNT = 478
    }
}
