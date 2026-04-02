package com.spanzy.oldercare.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.service.VoiceService
import com.spanzy.oldercare.ui.theme.WarmPrimaryText
import com.spanzy.oldercare.ui.theme.DarkPrimaryText

/**
 * 电池信息屏幕
 */
@Composable
fun BatteryScreen(
    batteryState: BatteryState,
    darkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val textColor = if (darkMode) DarkPrimaryText else WarmPrimaryText

    // 进入页面时播报电池电量
    LaunchedEffect(Unit) {
        val voiceService = VoiceService.getInstance(context)
        voiceService.initialize()

        val statusText = when {
            batteryState.isFull -> "已充满"
            batteryState.isCharging -> "充电中"
            else -> "未充电"
        }
        voiceService.speak("剩余电量${batteryState.level}%，$statusText")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 顶部返回按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(12.dp)
                .size(52.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 28.sp,
                color = textColor
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "电池信息",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "${batteryState.level}%",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                batteryState.isFull -> "已充满"
                batteryState.isCharging -> "充电中"
                else -> "未充电"
            },
            fontSize = 24.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "返回",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
