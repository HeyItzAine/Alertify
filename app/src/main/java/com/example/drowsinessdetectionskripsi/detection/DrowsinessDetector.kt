package com.example.drowsinessdetectionskripsi.detection

import com.example.drowsinessdetectionskripsi.Constants
import java.util.ArrayDeque
import kotlin.math.sqrt

/**
 * Core drowsiness detection engine ported from main.py DrowsinessDetector class.
 *
 * Processes EAR/MAR values with temporal smoothing and applies 2-rule drowsiness logic:
 *   Rule 1: EAR breach for consecutive samples → Drowsy (Eyes)
 *   Rule 2: MAR breach for consecutive samples → Drowsy (Yawn)
 */
class DrowsinessDetector {

    private val earWindow = ArrayDeque<Float>(Constants.EAR_WINDOW_SIZE)
    private val marWindow = ArrayDeque<Float>(Constants.MAR_WINDOW_SIZE)

    var earConsec = 0; private set
    var marConsec = 0; private set

    var isDrowsy = false; private set
    var lastRule: String? = null; private set

    // Latency tracking
    private var earBreachStart: Long? = null
    private var marBreachStart: Long? = null
    private var wasDrowsy = false
    val latencyEvents = mutableListOf<Float>() // seconds

    /**
     * Calculate Eye Aspect Ratio from 6 landmark points.
     * Points: p1(outer), p2(upper-outer), p3(upper-inner), p4(inner), p5(lower-inner), p6(lower-outer)
     */
    fun calculateEar(
        landmarks: List<FloatArray>, // each is [x, y]
        eyeIndices: IntArray,
        width: Float,
        height: Float
    ): Float {
        val pts = eyeIndices.map { idx ->
            floatArrayOf(landmarks[idx][0] * width, landmarks[idx][1] * height)
        }
        val (p1, p2, p3, p4, p5, p6) = pts
        val v1 = distance(p2, p6)
        val v2 = distance(p3, p5)
        val horiz = distance(p1, p4)
        return if (horiz != 0f) (v1 + v2) / (2f * horiz) else 0f
    }

    /**
     * Calculate Mouth Aspect Ratio from 8 landmark points.
     */
    fun calculateMar(
        landmarks: List<FloatArray>,
        mouthIndices: IntArray,
        width: Float,
        height: Float
    ): Float {
        val pts = mouthIndices.map { idx ->
            floatArrayOf(landmarks[idx][0] * width, landmarks[idx][1] * height)
        }
        val (p1, p2, p3, p4, p5, p6, p7, p8) = pts
        val v1 = distance(p2, p8)
        val v2 = distance(p3, p7)
        val v3 = distance(p4, p6)
        val horiz = distance(p1, p5)
        return if (horiz != 0f) (v1 + v2 + v3) / (2f * horiz) else 0f
    }

    /**
     * Called every sample interval. Applies temporal smoothing and drowsiness rules.
     * Returns a SampleResult with smoothed values and classification.
     */
    fun processSample(avgEar: Float, mar: Float, computationTimeMs: Float): SampleResult {
        val now = System.nanoTime()

        // Add to smoothing windows
        if (earWindow.size >= Constants.EAR_WINDOW_SIZE) earWindow.pollFirst()
        earWindow.addLast(avgEar)

        if (marWindow.size >= Constants.MAR_WINDOW_SIZE) marWindow.pollFirst()
        marWindow.addLast(mar)

        val smEar = earWindow.average().toFloat()
        val smMar = marWindow.average().toFloat()
        val earStd = stdDev(earWindow)
        val marStd = stdDev(marWindow)

        val earBreached = smEar < Constants.EAR_THRESHOLD
        val marBreached = smMar > Constants.MAR_THRESHOLD

        // Track breach start times
        if (earBreached) {
            if (earBreachStart == null) earBreachStart = now
            earConsec++
        } else {
            earBreachStart = null
            earConsec = 0
        }

        if (marBreached) {
            if (marBreachStart == null) marBreachStart = now
            marConsec++
        } else {
            marBreachStart = null
            marConsec = 0
        }

        // Drowsiness rules
        var rule: String? = null
        var breachStart: Long? = null
        if (earConsec >= Constants.EAR_CONSECUTIVE_LIMIT) {
            rule = "Rule 1 (EAR)"
            breachStart = earBreachStart
        } else if (marConsec >= Constants.MAR_CONSECUTIVE_LIMIT) {
            rule = "Rule 2 (MAR)"
            breachStart = marBreachStart
        }

        isDrowsy = rule != null
        lastRule = rule

        // Classification latency on Awake → Drowsy edge
        var classificationLatencyS: Float? = null
        if (isDrowsy && !wasDrowsy && breachStart != null) {
            classificationLatencyS = (now - breachStart) / 1_000_000_000f
            latencyEvents.add(classificationLatencyS)
        }
        wasDrowsy = isDrowsy

        return SampleResult(
            avgEar = smEar,
            mar = smMar,
            earStd = earStd,
            marStd = marStd,
            earBreached = earBreached,
            marBreached = marBreached,
            systemStatus = if (isDrowsy) "Drowsy" else "Awake",
            ruleTriggered = rule,
            computationTimeMs = computationTimeMs,
            classificationLatencyS = classificationLatencyS
        )
    }

    /**
     * Reset all state (called when switching modes or preprocessing).
     */
    fun reset() {
        earWindow.clear()
        marWindow.clear()
        earConsec = 0
        marConsec = 0
        isDrowsy = false
        lastRule = null
        earBreachStart = null
        marBreachStart = null
        wasDrowsy = false
        latencyEvents.clear()
    }

    /**
     * Reset only the consecutive counters (when no face is detected).
     */
    fun resetCounters() {
        earConsec = 0
        marConsec = 0
        isDrowsy = false
        lastRule = null
        earBreachStart = null
        marBreachStart = null
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        return sqrt(dx * dx + dy * dy)
    }

    private fun stdDev(deque: ArrayDeque<Float>): Float {
        if (deque.size < 2) return 0f
        val mean = deque.average().toFloat()
        val variance = deque.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    /**
     * Destructuring support for list of 6 floatArrays
     */
    private operator fun <T> List<T>.component6(): T = this[5]
    private operator fun <T> List<T>.component7(): T = this[6]
    private operator fun <T> List<T>.component8(): T = this[7]
}

data class SampleResult(
    val avgEar: Float,
    val mar: Float,
    val earStd: Float,
    val marStd: Float,
    val earBreached: Boolean,
    val marBreached: Boolean,
    val systemStatus: String,
    val ruleTriggered: String?,
    val computationTimeMs: Float,
    val classificationLatencyS: Float?
)
