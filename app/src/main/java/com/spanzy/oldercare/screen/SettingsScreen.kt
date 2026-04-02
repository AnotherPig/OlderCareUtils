package com.spanzy.oldercare.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.spanzy.oldercare.model.AnnouncementConfig
import com.spanzy.oldercare.model.ClockConfig
import com.spanzy.oldercare.model.ThemeConfig
import com.spanzy.oldercare.ui.theme.DarkBackground
import com.spanzy.oldercare.ui.theme.DarkPrimaryText
import com.spanzy.oldercare.ui.theme.WarmBackground
import com.spanzy.oldercare.ui.theme.WarmButtonBackground
import com.spanzy.oldercare.ui.theme.WarmPrimaryText
import java.io.File

/**
 * 设置页
 */
@Composable
fun SettingsScreen(
    clockConfig: ClockConfig,
    announcementConfig: AnnouncementConfig,
    themeConfig: ThemeConfig,
    imageUri: String? = null,
    onClockConfigChange: (ClockConfig) -> Unit,
    onAnnouncementConfigChange: (AnnouncementConfig) -> Unit,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    onImageUriChange: (String?) -> Unit,
    onBack: () -> Unit
) {
    val backgroundColor = if (themeConfig.darkMode) DarkBackground else WarmBackground
    val primaryTextColor = if (themeConfig.darkMode) DarkPrimaryText else WarmPrimaryText
    val cardBackground = if (themeConfig.darkMode) Color(0xFF1A1A2E) else WarmButtonBackground

    // 滚动容器
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .statusBarsPadding()
    ) {
        // 标题栏 - 返回按钮更大更明显
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 更大的返回按钮
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(12.dp)
                    .size(52.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "←",
                    fontSize = 28.sp,
                    color = primaryTextColor
                )
            }
            Text(
                text = "设置",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 字体大小
        SettingsCard(
            title = "小组件字体大小",
            background = cardBackground
        ) {
            FontSizeSlider(
                currentLevel = clockConfig.fontSizeLevel,
                onLevelChange = { newLevel ->
                    onClockConfigChange(clockConfig.copy(fontSizeLevel = newLevel))
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 显示内容
        SettingsCard(
            title = "小组件显示内容",
            background = cardBackground
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(
                    label = "时间",
                    checked = clockConfig.showTime,
                    onCheckedChange = { onClockConfigChange(clockConfig.copy(showTime = it)) }
                )
                SwitchRow(
                    label = "日期",
                    checked = clockConfig.showDate,
                    onCheckedChange = { onClockConfigChange(clockConfig.copy(showDate = it)) }
                )
                SwitchRow(
                    label = "农历",
                    checked = clockConfig.showLunar,
                    onCheckedChange = { onClockConfigChange(clockConfig.copy(showLunar = it)) }
                )
                SwitchRow(
                    label = "星期",
                    checked = clockConfig.showWeekday,
                    onCheckedChange = { onClockConfigChange(clockConfig.copy(showWeekday = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 时间格式
        SettingsCard(
            title = "小组件时间格式",
            background = cardBackground
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "使用24小时制", fontSize = 18.sp, color = primaryTextColor)
                Switch(
                    checked = clockConfig.use24Hour,
                    onCheckedChange = { onClockConfigChange(clockConfig.copy(use24Hour = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 播报计划
        SettingsCard(
            title = "播报计划",
            background = cardBackground
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(
                    label = "定时播报",
                    checked = announcementConfig.scheduledAnnounceEnabled,
                    onCheckedChange = { onAnnouncementConfigChange(announcementConfig.copy(scheduledAnnounceEnabled = it)) }
                )
                if (announcementConfig.scheduledAnnounceEnabled) {
                    SelectorRow(
                        label = "播报间隔",
                        value = when (announcementConfig.intervalMinutes) {
                            1 -> "每1分钟"
                            15 -> "每15分钟"
                            30 -> "每半小时"
                            else -> "每整点"
                        },
                        onClick = {
                            val newInterval = cycleIntervalMinutes(announcementConfig.intervalMinutes)
                            onAnnouncementConfigChange(announcementConfig.copy(intervalMinutes = newInterval))
                        },
                        darkMode = themeConfig.darkMode
                    )
                    SelectorRow(
                        label = "免打扰时段",
                        value = String.format(
                            "%02d:00-%02d:00",
                            announcementConfig.quietStartHour,
                            announcementConfig.quietEndHour
                        ),
                        onClick = { /* TODO: 弹出时间选择器 - 可选功能 */ },
                        darkMode = themeConfig.darkMode
                    )
                }
                Divider(color = primaryTextColor.copy(alpha = 0.1f))
                SwitchRow(
                    label = "低电量播报",
                    checked = announcementConfig.lowBatteryEnabled,
                    onCheckedChange = { onAnnouncementConfigChange(announcementConfig.copy(lowBatteryEnabled = it)) }
                )
                if (announcementConfig.lowBatteryEnabled) {
                    SelectorRow(
                        label = "电量阈值",
                        value = "${announcementConfig.lowBatteryThreshold}%",
                        onClick = {
                            val newThreshold = cycleLowBatteryThreshold(announcementConfig.lowBatteryThreshold)
                            onAnnouncementConfigChange(announcementConfig.copy(lowBatteryThreshold = newThreshold))
                        },
                        darkMode = themeConfig.darkMode
                    )
                }
                Divider(color = primaryTextColor.copy(alpha = 0.1f))
                SwitchRow(
                    label = "充电完成播报",
                    checked = announcementConfig.chargeCompleteEnabled,
                    onCheckedChange = { onAnnouncementConfigChange(announcementConfig.copy(chargeCompleteEnabled = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 桌面图片显示设置
        SettingsCard(
            title = "桌面图片显示",
            background = cardBackground
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 图片预览
                if (imageUri != null) {
                    val context = LocalContext.current
                    val imageFile = File(imageUri)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                if (themeConfig.darkMode) Color(0xFF2A2A2A) else Color(0xFFEEEEEE),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageFile.exists()) {
                            // 使用 key 强制在 imageUri 变化时重建 AsyncImage
                            key(imageUri) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageFile)
                                        .memoryCacheKey(imageUri)
                                        .size(Size.ORIGINAL)
                                        .build(),
                                    contentDescription = "图片预览",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Text(
                                text = "图片加载失败",
                                fontSize = 14.sp,
                                color = if (themeConfig.darkMode) Color.Gray else Color.DarkGray
                            )
                        }
                    }
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (imageUri != null) {
                        // 更换图片按钮
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = { onImageUriChange("pick") })
                                .background(
                                    if (themeConfig.darkMode) Color(0xFF505050) else Color(0xFFD0D0D0),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "更换",
                                fontSize = 16.sp,
                                color = if (themeConfig.darkMode) Color.White else Color.Black
                            )
                        }
                        // 清除图片按钮
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = { onImageUriChange(null) })
                                .background(
                                    if (themeConfig.darkMode) Color(0xFF505050) else Color(0xFFD0D0D0),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "清除",
                                fontSize = 16.sp,
                                color = if (themeConfig.darkMode) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 应用设置
        SettingsCard(
            title = "应用设置",
            background = cardBackground
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow(
                    label = "启用深色模式",
                    checked = themeConfig.darkMode,
                    onCheckedChange = { onThemeConfigChange(themeConfig.copy(darkMode = it)) }
                )
                SelectorRow(
                    label = "小组件更新频率",
                    value = formatUpdateInterval(themeConfig.widgetUpdateInterval),
                    onClick = {
                        val newInterval = cycleUpdateInterval(themeConfig.widgetUpdateInterval)
                        onThemeConfigChange(themeConfig.copy(widgetUpdateInterval = newInterval))
                    },
                    darkMode = themeConfig.darkMode
                )
                SwitchRow(
                    label = "点击小组件语音播报",
                    checked = themeConfig.widgetClickAnnounce,
                    onCheckedChange = { onThemeConfigChange(themeConfig.copy(widgetClickAnnounce = it)) }
                )
            }
        }
    }
}

/**
 * 格式化更新间隔
 */
private fun formatUpdateInterval(seconds: Int): String {
    return when (seconds) {
        30 -> "30秒"
        60 -> "1分钟"
        300 -> "5分钟"
        else -> "${seconds}秒"
    }
}

/**
 * 循环切换更新间隔
 */
private fun cycleUpdateInterval(current: Int): Int {
    val intervals = com.spanzy.oldercare.model.ThemeConfig.UPDATE_INTERVALS
    val currentIndex = intervals.indexOf(current)
    return intervals[(currentIndex + 1) % intervals.size]
}

@Composable
private fun SettingsCard(
    title: String,
    background: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SelectorRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    darkMode: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick
            )
            .background(
                if (isPressed) {
                    // 按下时使用更深的背景色，色差更明显
                    if (darkMode) {
                        Color(0xFF505050) // 深色模式：浅灰色背景
                    } else {
                        Color(0xFFD0D0D0) // 浅色模式：深灰色背景
                    }
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alpha(if (isPressed) 0.7f else 1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPressed) {
                    if (darkMode) {
                        Color(0xFFFFFFFF) // 深色模式：白色文字
                    } else {
                        Color(0xFF000000) // 浅色模式：黑色文字
                    }
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "▾",
                fontSize = 14.sp,
                color = if (isPressed) {
                    if (darkMode) {
                        Color(0xFFFFFFFF)
                    } else {
                        Color(0xFF000000)
                    }
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }
            )
        }
    }
}

/**
 * 循环切换播报间隔
 */
private fun cycleIntervalMinutes(current: Int): Int {
    val intervals = listOf(1, 15, 30, 60)
    val currentIndex = intervals.indexOf(current)
    return intervals[(currentIndex + 1) % intervals.size]
}

/**
 * 循环切换低电量阈值
 */
private fun cycleLowBatteryThreshold(current: Int): Int {
    val thresholds = listOf(10, 15, 20, 30)
    val currentIndex = thresholds.indexOf(current)
    return thresholds[(currentIndex + 1) % thresholds.size]
}

@Composable
private fun FontSizeSlider(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "小", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))

            Slider(
                value = currentLevel.toFloat(),
                onValueChange = { onLevelChange(it.toInt()) },
                valueRange = 1f..4f,
                steps = 2, // 3步 = 4档
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )

            Text(text = "大", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        }

        // 档位标签
        val levelNames = listOf("标准", "大", "较大", "超大")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            levelNames.forEachIndexed { index, name ->
                val isSelected = currentLevel == index + 1
                Text(
                    text = name,
                    fontSize = 13.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
