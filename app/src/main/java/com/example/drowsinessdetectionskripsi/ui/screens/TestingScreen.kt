package com.example.drowsinessdetectionskripsi.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drowsinessdetectionskripsi.Constants
import com.example.drowsinessdetectionskripsi.DetectionUiState
import com.example.drowsinessdetectionskripsi.detection.CsvExporter
import com.example.drowsinessdetectionskripsi.ui.components.MetricBox
import com.example.drowsinessdetectionskripsi.ui.theme.*

@Composable
fun TestingScreen(
    state: DetectionUiState,
    defaultFilename: String,
    onCancel: () -> Unit,
    onSaveCsv: (String) -> Unit,
    onToggleLandmarks: () -> Unit
) {
    var filename by remember { mutableStateOf(defaultFilename) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // Top bar — Box extends ColorCard behind the status bar/notch;
        // statusBarsPadding() pushes the Row content below it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorCard)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            TextButton(onClick = onCancel) {
                Text("\u2190 Cancel", color = ColorDanger, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Testing", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorText)
            Spacer(modifier = Modifier.weight(1f))
            // Landmark toggle button
            TextButton(onClick = onToggleLandmarks) {
                Text(
                    text = if (state.showLandmarks) "Hide LM" else "Show LM",
                    color = if (state.showLandmarks) ColorAccent else ColorTextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .background(ColorAccent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(state.preprocessing.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorBg)
            }
            Spacer(modifier = Modifier.width(6.dp))
            } // end Row
        } // end top bar Box

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Instruction banner
            val bannerColor = when (state.groundTruthPhase) {
                "action" -> ColorDanger
                "countdown" -> ColorWarning
                else -> ColorGreenDark
            }
            val bannerTextColor = when (state.groundTruthPhase) {
                "countdown" -> ColorBg
                "action" -> ColorText
                else -> ColorSuccess
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .background(bannerColor, RoundedCornerShape(12.dp))
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.testComplete) "Test Complete \u2014 See results below"
                           else state.groundTruthDisplay.ifEmpty { "Get ready \u2014 look at camera" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.testComplete) ColorText else bannerTextColor,
                    textAlign = TextAlign.Center
                )
            }

            // Camera feed with optional landmark overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = state.displayBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Camera feed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (state.showLandmarks) {
                        val landmarks = state.currentLandmarks
                        if (landmarks != null) {
                            TestingLandmarkOverlay(
                                landmarks = landmarks,
                                bitmapWidth = bitmap.width,
                                bitmapHeight = bitmap.height,
                                earBreached = state.earBreached,
                                marBreached = state.marBreached,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    Text("Preparing...", color = ColorTextDim)
                }
            }

            // Timer + progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .background(ColorCard, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val m = (state.testElapsed.toInt()) / 60
                    val s = (state.testElapsed.toInt()) % 60
                    Text(
                        text = "%02d:%02d / 01:00".format(m, s),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.testComplete) ColorSuccess else ColorText
                    )
                    Text(
                        text = "Samples: ${state.testSamples}",
                        fontSize = 11.sp,
                        color = ColorTextDim
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { (state.testElapsed / 60f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = ColorAccent,
                    trackColor = ColorBg,
                )
            }

            // Live metrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .background(ColorCard, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text("Live Metrics", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorTextDim,
                    modifier = Modifier.padding(bottom = 2.dp).align(Alignment.CenterHorizontally))

                Row(modifier = Modifier.fillMaxWidth()) {
                    val earVal = if (state.faceDetected) "%.3f".format(state.currentEar) else "\u2014"
                    val marVal = if (state.faceDetected) "%.3f".format(state.currentMar) else "\u2014"
                    val statusColor = when (state.currentStatus) {
                        "Drowsy" -> ColorDanger
                        "Awake" -> ColorSuccess
                        else -> ColorWarning
                    }
                    MetricBox("Avg EAR", earVal,
                        if (state.earBreached) ColorDanger else if (state.faceDetected) ColorAccent else ColorTextDim,
                        Modifier.weight(1f))
                    Spacer(Modifier.width(3.dp))
                    MetricBox("MAR", marVal,
                        if (state.marBreached) ColorDanger else if (state.faceDetected) ColorAccent else ColorTextDim,
                        Modifier.weight(1f))
                    Spacer(Modifier.width(3.dp))
                    MetricBox("Status", state.currentStatus, statusColor, Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox("FPS", "%.0f".format(state.currentFps), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(3.dp))
                    MetricBox("Comp.T", "%.0fms".format(state.currentCompMs), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(3.dp))
                    MetricBox(
                        "Pre.T",
                        if (state.preprocessing.name == "NONE") "--"
                        else "%.0fms".format(state.currentPreprocMs),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(
                        "RAM",
                        if (state.currentMemMb > 0) "%.0f MB".format(state.currentMemMb) else "--",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(3.dp))
                    MetricBox(
                        "CPU",
                        if (state.currentCpuPct > 0) "%.0f%%".format(state.currentCpuPct) else "--",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(3.dp))
                    MetricBox(
                        "Det.FPS",
                        "%.0f".format(state.currentDetFps),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Log preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .background(ColorCard, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text("Log Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorTextDim,
                    modifier = Modifier.padding(bottom = 2.dp))

                val header = "  #  | AvgEAR|  MAR  | Comp.T| GT     | Status | Rule\n" +
                             " ----+-------+-------+-------+--------+--------+------"
                val logText = if (state.testLogPreview.isNotEmpty()) {
                    "$header\n${state.testLogPreview}"
                } else header

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 120.dp)
                        .background(ColorBg, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ColorTextDim,
                        lineHeight = 10.sp
                    )
                }
            }

            // Summary (shown after test complete)
            if (state.testComplete && state.summary != null) {
                SummarySection(state.summary)
            }

            // Save controls (shown after test complete)
            if (state.testComplete) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Save Results", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorText,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = filename,
                            onValueChange = { filename = it },
                            modifier = Modifier.weight(1f).height(56.dp),
                            placeholder = { Text("Enter filename...", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = ColorText),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorAccent,
                                unfocusedBorderColor = ColorBorder,
                                cursorColor = ColorAccent
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onSaveCsv(filename) },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccess),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Save CSV", color = ColorBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (state.saveResult != null) {
                        Text(
                            text = state.saveResult,
                            fontSize = 10.sp,
                            color = if (state.saveResult.startsWith("Error")) ColorDanger else ColorSuccess,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TestingLandmarkOverlay(
    landmarks: List<FloatArray>,
    bitmapWidth: Int,
    bitmapHeight: Int,
    earBreached: Boolean,
    marBreached: Boolean,
    modifier: Modifier = Modifier
) {
    val eyeNormal = Color(0xFF00D9FF)
    val mouthNormal = Color(0xFFFFB300)
    val breachColor = Color(0xFFFF5252)

    val eyeColor = if (earBreached) breachColor else eyeNormal
    val mouthColor = if (marBreached) breachColor else mouthNormal

    Canvas(modifier = modifier) {
        val bW = bitmapWidth.toFloat()
        val bH = bitmapHeight.toFloat()
        val scale = minOf(size.width / bW, size.height / bH)
        val renderedW = bW * scale
        val renderedH = bH * scale
        val offsetX = (size.width - renderedW) / 2f
        val offsetY = (size.height - renderedH) / 2f

        fun lmOffset(idx: Int): Offset {
            val lm = landmarks[idx]
            return Offset(offsetX + lm[0] * renderedW, offsetY + lm[1] * renderedH)
        }

        for (eyeIdx in listOf(Constants.RIGHT_EYE, Constants.LEFT_EYE)) {
            val pts = eyeIdx.map { lmOffset(it) }
            for (i in pts.indices) {
                drawLine(eyeColor, pts[i], pts[(i + 1) % pts.size], strokeWidth = 2f)
            }
            pts.forEach { drawCircle(eyeColor, radius = 4f, center = it) }
        }

        // Use outer-lip contour for clean polygon
        val mPts = Constants.MOUTH_DRAW.map { lmOffset(it) }
        for (i in mPts.indices) {
            drawLine(mouthColor, mPts[i], mPts[(i + 1) % mPts.size], strokeWidth = 2f)
        }
        mPts.forEach { drawCircle(mouthColor, radius = 4f, center = it) }
    }
}

@Composable
private fun SummarySection(summary: CsvExporter.TestSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .background(ColorCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("Summary", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorAccent,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))

        SummaryRow("Total Samples", "${summary.total}")
        SummaryRow("Awake / Drowsy / NoFace", "${summary.awakeN} / ${summary.drowsyN} / ${summary.nofaceN}")
        Spacer(Modifier.height(4.dp))
        SummaryRow("TP / TN / FP / FN", "${summary.tp} / ${summary.tn} / ${summary.fp} / ${summary.fn}")
        SummaryRow("Accuracy", "%.4f".format(summary.accuracy))
        SummaryRow("Precision", "%.4f".format(summary.precision))
        SummaryRow("Recall", "%.4f".format(summary.recall))
        SummaryRow("F1-Score", "%.4f".format(summary.f1))
        Spacer(Modifier.height(4.dp))
        SummaryRow("Avg EAR", "%.4f (\u03C3: %.4f)".format(summary.avgEar, summary.earStd))
        SummaryRow("Avg MAR", "%.4f (\u03C3: %.4f)".format(summary.avgMar, summary.marStd))
        SummaryRow("Avg Comp.T", "%.1f ms".format(summary.avgCompMs))
        SummaryRow("Avg Cls.Latency", "%.1f ms (%d events)".format(summary.avgClsLatMs, summary.clsLatEvents))
        SummaryRow("Avg FPS", "%.1f".format(summary.avgFps))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, color = ColorTextDim)
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorText)
    }
}

