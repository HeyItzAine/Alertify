package com.example.drowsinessdetectionskripsi.detection

import com.example.drowsinessdetectionskripsi.Constants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Guided ground-truth action schedule for the 60s test mode.
 *
 * Ground truth for action phases is time-based, computed from actual FPS:
 *
 *   sps  = avgFps / SAMPLE_INTERVAL          (actual samples per second)
 *   drowsyStart = actionEntry.start + consecutiveLimit / sps
 *   drowsyEnd   = actionEntry.end   + windowSize / sps
 *
 *   drowsyStart — when the system first *can* detect the rule (consecutive limit met).
 *   drowsyEnd   — action end + smoothing window drain time (time for smoothed value
 *                 to return below threshold after subject stops the action).
 *
 * This means:
 *   - If consecutive limit > samples available in the window, drowsyStart falls
 *     AFTER the action phase ends — the window extends into the next phase.
 *   - The smoothTail ensures labels continue while the smoothed metric drains.
 *   - Ground truth is FPS-adaptive: it automatically scales with the real sample rate.
 *
 * Example at 16fps (sps = 3.2):
 *   EYES  (10–15s, limit=6):  drowsy 11.9s – 16.9s
 *   YAWN  (23–29s, limit=24): drowsy 30.5s – 34.6s  ← extends past 29s
 *   COMBO (37–42s, limit=6):  drowsy 38.9s – 43.9s
 *   EYES  (50–55s, limit=6):  drowsy 51.9s – 56.9s
 */
enum class ActionType { NORMAL, COUNTDOWN, EYES, YAWN, COMBO }

data class ScheduleEntry(
    val start: Float,
    val end: Float,
    val actionType: ActionType,
    val instruction: String,
    val actionEnd: Float? = null   // end of the full block, for countdown display
)

object ActionSchedule {

    val entries = listOf(
        // Baseline
        ScheduleEntry(0f,  7f,  ActionType.NORMAL,    "Look at camera normally"),

        // Action 1: Eyes (Rule 1 — EAR)
        ScheduleEntry(7f,  10f, ActionType.COUNTDOWN, "Close/squint eyes for 5s",       15f),
        ScheduleEntry(10f, 15f, ActionType.EYES,      "CLOSE EYES \u2014 Hold!",         15f),

        // Baseline
        ScheduleEntry(15f, 20f, ActionType.NORMAL,    "Look at camera normally"),

        // Action 2: Yawn (Rule 2 — MAR)
        ScheduleEntry(20f, 23f, ActionType.COUNTDOWN, "Yawn (open mouth wide) for 6s",  29f),
        ScheduleEntry(23f, 29f, ActionType.YAWN,      "YAWN \u2014 Keep mouth open!",    29f),

        // Baseline
        ScheduleEntry(29f, 34f, ActionType.NORMAL,    "Look at camera normally"),

        // Action 3: Combo (use EAR limit — eyes trigger first)
        ScheduleEntry(34f, 37f, ActionType.COUNTDOWN, "Close eyes + Yawn for 5s",       42f),
        ScheduleEntry(37f, 42f, ActionType.COMBO,     "CLOSE EYES + YAWN \u2014 Hold!", 42f),

        // Baseline
        ScheduleEntry(42f, 47f, ActionType.NORMAL,    "Look at camera normally"),

        // Action 4: Eyes again (Rule 1 — EAR)
        ScheduleEntry(47f, 50f, ActionType.COUNTDOWN, "Close/squint eyes for 5s",       55f),
        ScheduleEntry(50f, 55f, ActionType.EYES,      "CLOSE EYES \u2014 Hold!",         55f),

        // Final baseline
        ScheduleEntry(55f, 60f, ActionType.NORMAL,    "Look at camera normally"),
    )

    data class GroundTruthResult(
        val groundTruth: String,
        val phase: String,
        val displayText: String,
        val actionType: ActionType
    )

    /** Returns the schedule entry active at [elapsed] seconds, or null after 60s. */
    fun entryAt(elapsed: Float): ScheduleEntry? =
        entries.find { elapsed >= it.start && elapsed < it.end }

    /**
     * Returns true if [elapsed] falls within ANY action's computed Drowsy window.
     *
     * Drowsy window (computed once from Constants.EXPECTED_FPS so boundaries are
     * fixed for the whole test — no flickering from live FPS fluctuations):
     *   start = actionPhaseStart + consecutiveLimit / sps
     *   end   = actionPhaseEnd   + windowSize / sps
     */
    private val SPS = Constants.EXPECTED_FPS / Constants.SAMPLE_INTERVAL

    private fun isDrowsyAtTime(elapsed: Float): Boolean {
        for (entry in entries) {
            val (limit, window) = when (entry.actionType) {
                ActionType.EYES, ActionType.COMBO ->
                    Constants.EAR_CONSECUTIVE_LIMIT to Constants.EAR_WINDOW_SIZE
                ActionType.YAWN ->
                    Constants.MAR_CONSECUTIVE_LIMIT to Constants.MAR_WINDOW_SIZE
                else -> continue
            }
            val drowsyStart = entry.start + limit / SPS
            val drowsyEnd   = entry.end   + window / SPS
            if (elapsed >= drowsyStart && elapsed < drowsyEnd) return true
        }
        return false
    }

    /**
     * Compute ground truth for [elapsed] seconds into the test.
     * Windows are derived from Constants.EXPECTED_FPS — fixed and gap-free.
     */
    fun getGroundTruth(elapsed: Float): GroundTruthResult {
        val entry = entryAt(elapsed)
            ?: return GroundTruthResult("Awake", "normal", "Test complete", ActionType.NORMAL)

        val groundTruth = if (isDrowsyAtTime(elapsed)) "Drowsy" else "Awake"

        return when (entry.actionType) {
            ActionType.NORMAL -> GroundTruthResult(
                groundTruth, "normal", entry.instruction, ActionType.NORMAL
            )
            ActionType.COUNTDOWN -> {
                val cd = max(1, min(3, ceil(entry.end - elapsed).toInt()))
                GroundTruthResult(
                    groundTruth, "countdown",
                    "${entry.instruction}  in  $cd...",
                    ActionType.COUNTDOWN
                )
            }
            else -> {
                val remaining = ceil((entry.actionEnd ?: entry.end) - elapsed).toInt()
                GroundTruthResult(
                    groundTruth, "action",
                    "${entry.instruction}  (${remaining}s left)",
                    entry.actionType
                )
            }
        }
    }
}
