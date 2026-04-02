package com.spanzy.oldercare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.model.ClockConfig
import com.spanzy.oldercare.service.VoiceService
import com.spanzy.oldercare.ui.theme.FontSizeLevel
import com.spanzy.oldercare.ui.theme.WarmButtonBackground
import com.spanzy.oldercare.ui.theme.WarmChargeBlue
import com.spanzy.oldercare.ui.theme.WarmBatteryGreen
import com.spanzy.oldercare.ui.theme.WarmLowBatteryRed
import com.spanzy.oldercare.ui.theme.WarmPrimaryText
import com.spanzy.oldercare.ui.theme.WarmSecondaryText
import com.spanzy.oldercare.ui.theme.WarmTertiaryText
import com.spanzy.oldercare.ui.theme.DarkButtonBackground
import com.spanzy.oldercare.ui.theme.DarkPrimaryText
import com.spanzy.oldercare.ui.theme.DarkSecondaryText
import com.spanzy.oldercare.ui.theme.DarkBatteryGreen
import com.spanzy.oldercare.ui.theme.DarkChargeBlue
import com.spanzy.oldercare.ui.theme.DarkLowBatteryRed
import com.spanzy.oldercare.util.BatteryHelper
import com.spanzy.oldercare.util.LunarCalendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

enum class Screen {
    Main,
    Battery,
    Settings
}

@Composable
fun MainScreen(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    clockConfig: ClockConfig,
    batteryState: BatteryState,
    darkMode: Boolean
) {
    val context = LocalContext.current
    val voiceService = VoiceService.getInstance(context)
    val coroutineScope = rememberCoroutineScope()

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val calendar = Calendar.getInstance()
    calendar.timeInMillis = currentTime
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val weekday = calendar.get(Calendar.DAY_OF_WEEK)

    // 主页始终使用最大字体，不受设置影响
    val maxFontSizeLevel = FontSizeLevel.fromLevel(4) // Level 4 = 超大

    val backgroundColor = if (darkMode) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.background
    val primaryTextColor = if (darkMode) DarkPrimaryText else WarmPrimaryText
    val secondaryTextColor = if (darkMode) DarkSecondaryText else WarmSecondaryText
    val tertiaryTextColor = if (darkMode) MaterialTheme.colorScheme.tertiary else WarmTertiaryText
    val buttonBackground = if (darkMode) DarkButtonBackground else WarmButtonBackground
    val batteryColor = when {
        batteryState.isFull || (!batteryState.isCharging && batteryState.level > 50) ->
            if (darkMode) DarkBatteryGreen else WarmBatteryGreen
        batteryState.isCharging -> if (darkMode) DarkChargeBlue else WarmChargeBlue
        else -> if (darkMode) DarkLowBatteryRed else WarmLowBatteryRed
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 主页始终显示时间（24小时制）
        Text(
            text = String.format("%02d:%02d", hour, minute),
            fontSize = maxFontSizeLevel.timeSp,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 主页始终显示日期
        Text(
            text = "${year}年${month}月${day}日",
            fontSize = maxFontSizeLevel.dateSp,
            fontWeight = FontWeight.Medium,
            color = secondaryTextColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 主页始终显示星期
        val weekdayText = arrayOf("日", "一", "二", "三", "四", "五", "六")[weekday - 1]
        Text(
            text = "星期${weekdayText}",
            fontSize = maxFontSizeLevel.lunarSp,
            color = tertiaryTextColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 主页始终显示农历
        val lunar = LunarCalendar.toLunar(year, month, day)
        Text(
            text = "农历${lunar}",
            fontSize = maxFontSizeLevel.lunarSp,
            color = tertiaryTextColor
        )

        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuickButton(
                icon = "🔋",
                label = "${batteryState.level}%",
                subLabel = "电池",
                modifier = Modifier.weight(1f),
                backgroundColor = buttonBackground,
                textColor = primaryTextColor,
                onClick = { onNavigate(Screen.Battery) }
            )

            QuickButton(
                icon = "🔊",
                label = "播报",
                subLabel = "语音",
                modifier = Modifier.weight(1f),
                backgroundColor = buttonBackground,
                textColor = primaryTextColor,
                onClick = {
                    // 触发异步初始化（非阻塞，避免 ANR）
                    voiceService.initialize()

                    // 使用协程调用 suspend 函数
                    coroutineScope.launch {
                        val clickCalendar = Calendar.getInstance()
                        val clickYear = clickCalendar.get(Calendar.YEAR)
                        val clickMonth = clickCalendar.get(Calendar.MONTH) + 1
                        val clickDay = clickCalendar.get(Calendar.DAY_OF_MONTH)
                        val clickHour = clickCalendar.get(Calendar.HOUR_OF_DAY)
                        val clickMinute = clickCalendar.get(Calendar.MINUTE)

                        val period = if (clickHour < 12) "上午" else "下午"
                        val displayHour = if (clickHour == 0) 12 else if (clickHour > 12) clickHour - 12 else clickHour
                        val timeText = "${displayHour}点${if (clickMinute > 0) "${clickMinute}分" else ""}"

                        val parts = mutableListOf("现在${period} ${timeText}", "${clickYear}年${clickMonth}月${clickDay}日")
                        val lunar = LunarCalendar.toLunar(clickYear, clickMonth, clickDay)
                        parts.add("农历${lunar}")
                        val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
                        val statusText = when {
                            batteryState.isFull -> "已充满"
                            batteryState.isCharging -> "充电中"
                            else -> "未充电"
                        }
                        parts.add("电池电量百分之${batteryText}，${statusText}")

                        voiceService.speak(parts.joinToString("，"))
                    }
                }
            )

            QuickButton(
                icon = "⚙️",
                label = "设置",
                subLabel = "",
                modifier = Modifier.weight(1f),
                backgroundColor = buttonBackground,
                textColor = primaryTextColor,
                onClick = { onNavigate(Screen.Settings) }
            )
        }
    }
}

@Composable
private fun QuickButton(
    icon: String,
    label: String,
    subLabel: String,
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (subLabel.isNotEmpty()) {
                Text(
                    text = subLabel,
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
