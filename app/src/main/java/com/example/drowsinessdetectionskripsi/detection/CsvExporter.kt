package com.example.drowsinessdetectionskripsi.detection

import com.example.drowsinessdetectionskripsi.Constants
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports test results to CSV with summary statistics.
 * Ported from main.py _save_csv and _test_complete methods.
 */
object CsvExporter {

    data class TestLogEntry(
        val sample: Int,
        val timestamp: Float,
        val avgEar: String,     // empty string if no face
        val mar: String,
        val earStdDev: String,
        val marStdDev: String,
        val computationTimeMs: Float,
        val classificationLatencyMs: String,
        val cpuUsagePct: Float,
        val ramUsageMb: Float,
        val groundTruth: String,
        val systemStatus: String,
        val ruleTriggered: String
    )

    data class TestSummary(
        val total: Int,
        val tp: Int, val tn: Int, val fp: Int, val fn: Int,
        val accuracy: Float, val precision: Float, val recall: Float, val f1: Float,
        val avgEar: Float, val earStd: Float,
        val avgMar: Float, val marStd: Float,
        val avgCompMs: Float,
        val avgClsLatMs: Float, val clsLatEvents: Int,
        val avgFps: Float,
        val avgCpu: Float, val avgRam: Float,
        val drowsyN: Int, val awakeN: Int, val nofaceN: Int
    )

    fun computeConfusion(logs: List<TestLogEntry>): IntArray {
        var tp = 0; var tn = 0; var fp = 0; var fn = 0
        for (e in logs) {
            val gt = e.groundTruth
            val ss = e.systemStatus
            if (gt.isEmpty() || ss.isEmpty() || ss == "No Face") continue
            when {
                gt == "Drowsy" && ss == "Drowsy" -> tp++
                gt == "Awake" && ss == "Awake" -> tn++
                gt == "Awake" && ss == "Drowsy" -> fp++
                gt == "Drowsy" && ss == "Awake" -> fn++
            }
        }
        return intArrayOf(tp, tn, fp, fn)
    }

    fun computeMetrics(tp: Int, tn: Int, fp: Int, fn: Int): FloatArray {
        val total = (tp + tn + fp + fn).toFloat()
        val acc = if (total > 0) (tp + tn) / total else 0f
        val prec = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
        val rec = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
        val f1 = if (prec + rec > 0) 2 * prec * rec / (prec + rec) else 0f
        return floatArrayOf(acc, prec, rec, f1)
    }

    fun buildSummary(logs: List<TestLogEntry>, avgFps: Float): TestSummary {
        val valid = logs.filter { it.systemStatus != "No Face" }
        val ears = valid.mapNotNull { it.avgEar.toFloatOrNull() }
        val mars = valid.mapNotNull { it.mar.toFloatOrNull() }
        val compTs = logs.map { it.computationTimeMs }
        val cpus = logs.map { it.cpuUsagePct }.filter { it > 0 }
        val rams = logs.map { it.ramUsageMb }.filter { it > 0 }
        val clsLats = logs.mapNotNull { it.classificationLatencyMs.toFloatOrNull() }.filter { it > 0 }

        val (tp, tn, fp, fn) = computeConfusion(logs)
        val (acc, prec, rec, f1) = computeMetrics(tp, tn, fp, fn)

        val avgComp = if (compTs.isNotEmpty()) compTs.average().toFloat() else 0f
        val avgClsLatMs = if (clsLats.isNotEmpty()) clsLats.average().toFloat() else 0f
        val avgCpu = if (cpus.isNotEmpty()) cpus.average().toFloat() else 0f
        val avgRam = if (rams.isNotEmpty()) rams.average().toFloat() else 0f

        return TestSummary(
            total = logs.size,
            tp = tp, tn = tn, fp = fp, fn = fn,
            accuracy = acc, precision = prec, recall = rec, f1 = f1,
            avgEar = if (ears.isNotEmpty()) ears.average().toFloat() else 0f,
            earStd = if (ears.size > 1) stdDev(ears) else 0f,
            avgMar = if (mars.isNotEmpty()) mars.average().toFloat() else 0f,
            marStd = if (mars.size > 1) stdDev(mars) else 0f,
            avgCompMs = avgComp,
            avgClsLatMs = avgClsLatMs, clsLatEvents = clsLats.size,
            avgFps = avgFps,
            avgCpu = avgCpu, avgRam = avgRam,
            drowsyN = logs.count { it.systemStatus == "Drowsy" },
            awakeN = logs.count { it.systemStatus == "Awake" },
            nofaceN = logs.count { it.systemStatus == "No Face" }
        )
    }

    /**
     * Write CSV content to any OutputStream (used by SAF file picker).
     */
    fun writeCsv(
        outputStream: java.io.OutputStream,
        preprocessing: String,
        logs: List<TestLogEntry>,
        summary: TestSummary
    ) {
        val writer = outputStream.bufferedWriter()

        // UTF-8 BOM so Excel recognises comma-separated columns
        writer.write("\uFEFF")

        // Header
        writer.write("Sample,Timestamp,Avg_EAR,MAR,EAR_Std_Dev,MAR_Std_Dev,")
        writer.write("Computation_Time_ms,Classification_Latency_ms,")
        writer.write("CPU_Usage_pct,RAM_Usage_MB,Ground_Truth,System_Status,Rule_Triggered\n")

        // Data rows
        for (e in logs) {
            writer.write("${e.sample},${e.timestamp},${e.avgEar},${e.mar},")
            writer.write("${e.earStdDev},${e.marStdDev},")
            writer.write("${String.format(Locale.US, "%.3f", e.computationTimeMs)},${e.classificationLatencyMs},")
            writer.write("${e.cpuUsagePct},${e.ramUsageMb},")
            writer.write("${e.groundTruth},${e.systemStatus},${e.ruleTriggered}\n")
        }

        val s = summary
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        writer.write("\n# === SUMMARY STATISTICS ===\n")
        writer.write("# Preprocessing: $preprocessing\n")
        writer.write("# Date: $dateStr\n")
        writer.write("# Duration: 60s | Sampling: Every ${Constants.SAMPLE_INTERVAL}th frame\n")
        writer.write("# EAR: ${Constants.EAR_THRESHOLD} | MAR: ${Constants.MAR_THRESHOLD}\n")
        writer.write("# EAR Rule: >=${Constants.EAR_CONSECUTIVE_LIMIT}f | MAR Rule: >=${Constants.MAR_CONSECUTIVE_LIMIT}f\n")
        writer.write("# EAR Window: ${Constants.EAR_WINDOW_SIZE} | MAR Window: ${Constants.MAR_WINDOW_SIZE}\n")
        writer.write("#\n")
        writer.write("# Total Samples: ${s.total}\n")
        writer.write("# Awake: ${s.awakeN} | Drowsy: ${s.drowsyN} | NoFace: ${s.nofaceN}\n")
        writer.write("# TP: ${s.tp} | TN: ${s.tn} | FP: ${s.fp} | FN: ${s.fn}\n")
        writer.write("# Accuracy: ${s.accuracy}\n")
        writer.write("# Precision: ${s.precision}\n")
        writer.write("# Recall: ${s.recall}\n")
        writer.write("# F1-Score: ${s.f1}\n")
        writer.write("#\n")
        writer.write("# Avg EAR: ${s.avgEar} | EAR Std: ${s.earStd}\n")
        writer.write("# Avg MAR: ${s.avgMar} | MAR Std: ${s.marStd}\n")
        writer.write("# Avg Computation Time (ms): ${s.avgCompMs}\n")
        writer.write("# Avg Classification Latency (ms): ${s.avgClsLatMs} (${s.clsLatEvents} events)\n")
        writer.write("# Avg FPS: ${s.avgFps}\n")
        writer.write("# Avg CPU (%): ${s.avgCpu}\n")
        writer.write("# Avg RAM (MB): ${s.avgRam}\n")

        writer.flush()
    }

    private fun stdDev(values: List<Float>): Float {
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return kotlin.math.sqrt(variance)
    }
}
