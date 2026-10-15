package com.example.drowsinessdetectionskripsi.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drowsinessdetectionskripsi.Constants
import com.example.drowsinessdetectionskripsi.DetectionUiState
import com.example.drowsinessdetectionskripsi.ui.components.MetricBox
import com.example.drowsinessdetectionskripsi.ui.theme.*

@Composable
fun RealTimeScreen(
    state: DetectionUiState,
    lightMode: Boolean,
    showCamera: Boolean,
    onToggleLightMode: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleRun: () -> Unit,
    onToggleLandmarks: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val showStatistics = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val showSettings = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top bar — Box extends ColorCard behind the status bar/notch;
        // statusBarsPadding() pushes the Row content below it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleRun) {
                    Text(if (state.isRunning) "Ⅱ" else "▶", color = if (state.isRunning) colors.error else colors.primary)
                }
                Text(
                    text = "Alertify",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleLightMode) {
                    Text(if (lightMode) "☾" else "☼", color = colors.onSurface, fontSize = 19.sp)
                }
                IconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier.semantics {
                        contentDescription = if (showCamera) "Hide camera preview" else "Show camera preview"
                    }
                ) {
                    CameraIcon(visible = showCamera, color = colors.onSurface)
                }
                IconButton(onClick = { showSettings.value = true }) {
                    Text("⋮", color = colors.onSurface, fontSize = 22.sp)
                }
            }
        }

        // Analysis remains active while the preview is hidden.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = state.displayBitmap
            if (showCamera && bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Camera feed",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (state.showLandmarks) {
                    val landmarks = state.currentLandmarks
                    if (landmarks != null) {
                        LandmarkOverlay(
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
                Text(text = if (state.isRunning) "Camera analysis active" else "Detection paused", color = colors.onSurface.copy(alpha = .6f))
            }
        }

        // Status + metrics card
        if (showStatistics.value) Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status banner
            val statusText: String
            val statusColor: Color
            when (state.currentStatus) {
                "Drowsy" -> {
                    statusText = "\u26A0 DROWSY"
                    statusColor = colors.error
                }
                "Awake" -> {
                    statusText = "\u25CF AWAKE"
                    statusColor = Color(0xFF20A36A)
                }
                else -> {
                    statusText = "\u25CF NO FACE"
                    statusColor = Color(0xFFB7791F)
                }
            }

            Text(
                text = statusText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 1: EAR, MAR, Latency
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricBox(
                    label = "EAR",
                    value = "%.3f".format(state.currentEar),
                    valueColor = if (state.earBreached) colors.error else colors.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                MetricBox(
                    label = "MAR",
                    value = "%.3f".format(state.currentMar),
                    valueColor = if (state.marBreached) colors.error else colors.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                MetricBox(
                    label = "Latency",
                    value = state.lastClassificationLatencyMs?.let { "%.0fms".format(it) } ?: "--",
                    valueColor = if (state.lastClassificationLatencyMs != null) Color(0xFFB7791F) else colors.onSurface.copy(alpha = .6f),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Row 2: FPS, Comp.T, Pre.T
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricBox(
                    label = "FPS",
                    value = "%.0f".format(state.currentFps),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                MetricBox(
                    label = "Comp.T",
                    value = "%.0fms".format(state.currentCompMs),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                MetricBox(
                    label = "Pre.T",
                    value = if (state.preprocessing.name == "NONE") "--"
                            else "%.0fms".format(state.currentPreprocMs),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Row 3: RAM, CPU
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricBox(
                    label = "RAM",
                    value = "%.0f MB".format(state.currentMemMb),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                MetricBox(
                    label = "CPU",
                    value = if (state.currentCpuPct > 0) "%.0f%%".format(state.currentCpuPct) else "--",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                MetricBox(
                    label = "Det.FPS",
                    value = "%.0f".format(state.currentDetFps),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
        if (showSettings.value) {
            AlertDialog(
                onDismissRequest = { showSettings.value = false },
                title = { Text("Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingToggleRow(
                            label = "Performance statistics",
                            checked = showStatistics.value,
                            onCheckedChange = { showStatistics.value = it }
                        )
                        SettingToggleRow(
                            label = "Face landmarks",
                            checked = state.showLandmarks,
                            onCheckedChange = { onToggleLandmarks() }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings.value = false }) { Text("Done") } }
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            maxLines = 2
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CameraIcon(visible: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(2.dp.toPx(), 7.dp.toPx()),
            size = Size(18.dp.toPx(), 13.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(7.dp.toPx(), 4.dp.toPx()),
            size = Size(6.dp.toPx(), 4.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = Offset(11.dp.toPx(), 13.dp.toPx()),
            style = Stroke(stroke)
        )
        if (!visible) {
            drawLine(
                color = color,
                start = Offset(4.dp.toPx(), 4.dp.toPx()),
                end = Offset(19.dp.toPx(), 19.dp.toPx()),
                strokeWidth = stroke
            )
        }
    }
}

@Composable
private fun LandmarkOverlay(
    landmarks: List<FloatArray>,
    bitmapWidth: Int,
    bitmapHeight: Int,
    earBreached: Boolean,
    marBreached: Boolean,
    modifier: Modifier = Modifier
) {
    val eyeNormal = Color(0xFF00D9FF)   // cyan
    val mouthNormal = Color(0xFFFFB300) // amber
    val breachColor = Color(0xFFFF5252) // red

    val eyeColor = if (earBreached) breachColor else eyeNormal
    val mouthColor = if (marBreached) breachColor else mouthNormal

    Canvas(modifier = modifier) {
        val bW = bitmapWidth.toFloat()
        val bH = bitmapHeight.toFloat()

        // Compute rendered image rect for ContentScale.Fit
        val scale = minOf(size.width / bW, size.height / bH)
        val renderedW = bW * scale
        val renderedH = bH * scale
        val offsetX = (size.width - renderedW) / 2f
        val offsetY = (size.height - renderedH) / 2f

        fun lmOffset(idx: Int): Offset {
            val lm = landmarks[idx]
            return Offset(offsetX + lm[0] * renderedW, offsetY + lm[1] * renderedH)
        }

        // Draw eye contours (right and left)
        for (eyeIdx in listOf(Constants.RIGHT_EYE, Constants.LEFT_EYE)) {
            val pts = eyeIdx.map { lmOffset(it) }
            for (i in pts.indices) {
                drawLine(eyeColor, pts[i], pts[(i + 1) % pts.size], strokeWidth = 2f)
            }
            pts.forEach { drawCircle(eyeColor, radius = 4f, center = it) }
        }

        // Draw mouth contour using outer-lip landmarks for clean polygon
        val mPts = Constants.MOUTH_DRAW.map { lmOffset(it) }
        for (i in mPts.indices) {
            drawLine(mouthColor, mPts[i], mPts[(i + 1) % mPts.size], strokeWidth = 2f)
        }
        mPts.forEach { drawCircle(mouthColor, radius = 4f, center = it) }
    }
}
