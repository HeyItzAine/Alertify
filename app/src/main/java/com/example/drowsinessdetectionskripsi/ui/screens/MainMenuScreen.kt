package com.example.drowsinessdetectionskripsi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drowsinessdetectionskripsi.Constants
import com.example.drowsinessdetectionskripsi.ui.theme.*

@Composable
fun MainMenuScreen(
    onRealTimeClick: () -> Unit,
    onTestingClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Icon
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(ColorPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83D\uDC41", fontSize = 38.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "DrowsyGuard",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = ColorText
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Real-Time Drowsiness Detection",
            fontSize = 13.sp,
            color = ColorTextDim
        )
        Text(
            text = "EAR + MAR  \u00B7  Mediapipe Face Mesh",
            fontSize = 11.sp,
            color = ColorTextDim
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Real-time card
        MenuCard(
            icon = "\uD83D\uDCF9",
            title = "Real-Time Detection",
            desc = "Live camera with drowsiness classification",
            accentColor = ColorPrimary,
            onClick = onRealTimeClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Testing card
        MenuCard(
            icon = "\uD83D\uDCCA",
            title = "Testing Mode",
            desc = "1-min guided test with CSV export & metrics",
            accentColor = ColorAccent,
            onClick = onTestingClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Config info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorCard, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Detection Parameters",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextDim
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "EAR: ${Constants.EAR_THRESHOLD}  \u00B7  MAR: ${Constants.MAR_THRESHOLD}\n" +
                            "Rules: EAR>=${Constants.EAR_CONSECUTIVE_LIMIT}f \u00B7 MAR>=${Constants.MAR_CONSECUTIVE_LIMIT}f\n" +
                            "Camera: ${if (Constants.USE_FRONT_CAMERA) "Front" else "Back"}",
                    fontSize = 11.sp,
                    color = ColorTextDim,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "On-device drowsiness detection",
            fontSize = 10.sp,
            color = ColorTextDim,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MenuCard(
    icon: String,
    title: String,
    desc: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(ColorCard, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = ColorTextDim
            )
        }
        Text(
            text = "\u203A",
            fontSize = 26.sp,
            color = ColorTextDim
        )
    }
}
