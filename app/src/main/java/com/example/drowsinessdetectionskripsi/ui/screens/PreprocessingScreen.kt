package com.example.drowsinessdetectionskripsi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drowsinessdetectionskripsi.detection.PreprocessingType
import com.example.drowsinessdetectionskripsi.ui.theme.*

@Composable
fun PreprocessingScreen(
    mode: String, // "realtime" or "testing"
    onBack: () -> Unit,
    onSelect: (PreprocessingType) -> Unit
) {
    val accentColor = if (mode == "realtime") ColorPrimary else ColorAccent
    val modeTitle = if (mode == "realtime") "Real-Time Detection" else "Testing Mode"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // Header — Box extends ColorCard behind the status bar/notch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorCard)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text(
                        text = "\u2190 Back",
                        color = ColorAccent,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Select Preprocessing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorText
                )
            }
        }

        // Mode badge
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.CenterHorizontally)
                .background(accentColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = modeTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText
            )
        }

        Text(
            text = "Choose a light enhancement algorithm\napplied before face detection",
            fontSize = 12.sp,
            color = ColorTextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 14.dp)
        )

        // Options
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            for (type in PreprocessingType.entries) {
                PreprocessingCard(
                    icon = type.icon,
                    name = type.displayName,
                    desc = type.description,
                    onClick = { onSelect(type) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreprocessingCard(
    icon: String,
    name: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(ColorCard, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorText
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                color = ColorTextDim
            )
        }
        Text(
            text = "\u203A",
            fontSize = 24.sp,
            color = ColorTextDim
        )
    }
}
