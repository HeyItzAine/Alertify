package com.example.drowsinessdetectionskripsi

/**
 * All detection constants ported from main.py
 */
object Constants {
    // Camera
    const val USE_FRONT_CAMERA = true

    // Thresholds
    const val EAR_THRESHOLD = 0.25f
    const val MAR_THRESHOLD = 0.50f

    // Sampling
    const val SAMPLE_INTERVAL = 5          // Every 5th frame
    const val EXPECTED_FPS = 16f           // Used for ground truth window computation only

    // Smoothing windows
    const val EAR_WINDOW_SIZE = 6
    const val MAR_WINDOW_SIZE = 18

    // Consecutive sample limits for drowsiness rules
    const val EAR_CONSECUTIVE_LIMIT = 6    // Rule 1: ~1s at 6 sps
    const val MAR_CONSECUTIVE_LIMIT = 24   // Rule 2: ~4s at 6 sps

    // Detection delays in seconds
    const val EAR_DELAY_S = EAR_CONSECUTIVE_LIMIT / 6.0f
    const val MAR_DELAY_S = MAR_CONSECUTIVE_LIMIT / 6.0f

    // MediaPipe landmark indices
    val RIGHT_EYE = intArrayOf(33, 160, 158, 133, 153, 144)
    val LEFT_EYE = intArrayOf(362, 385, 387, 263, 373, 380)

    // Inner-lip landmarks for MAR calculation (8 points)
    // Corners: p1=78 (right), p5=308 (left)
    // Upper inner lip (3 pts): p2=81, p3=13, p4=311
    // Lower inner lip (3 pts): p6=318, p7=14, p8=88
    val MOUTH = intArrayOf(78, 81, 13, 311, 308, 318, 14, 88)

    // Inner-lip contour for drawing (matches MAR calculation landmarks)
    val MOUTH_DRAW = intArrayOf(78, 81, 13, 311, 308, 318, 14, 88)

    // Test duration
    const val TEST_DURATION_S = 60.0f

    // Max lines kept in the on-screen test log preview (CSV keeps all samples)
    const val LOG_PREVIEW_MAX_LINES = 12
}
