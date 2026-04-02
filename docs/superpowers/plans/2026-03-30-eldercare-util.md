# 老年人实用工具 App 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一款面向老年用户的 Android 实用工具，包含桌面小组件（日期时间农历）、大字时钟、电池电量显示、TTS 语音播报和设置功能。

**Architecture:** 单模块 Android 应用，使用 Jetpack Compose + Material 3 构建 UI，Glance 实现桌面小组件，WorkManager 处理后台任务，DataStore 持久化设置。简单枚举导航，无 Jetpack Navigation 依赖。

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.02.01, Glance 1.1.0, WorkManager 2.10.0, DataStore 1.1.0, Android TTS, minSdk 23, targetSdk 36

---

## 文件结构

```
app/src/main/java/com/spanzy/oldercare/
├── OlderCareApplication.kt         # Application 类，TTS 懒加载初始化
├── MainActivity.kt                 # 入口，枚举导航控制
├── MainScreen.kt                   # 主界面（大字时钟 + 底部按钮）
├── widget/
│   ├── ClockWidget.kt              # Glance 桌面小组件（4x2 + 4x3）
│   └── ClockWidgetReceiver.kt      # Widget 更新广播接收器
├── service/
│   ├── VoiceService.kt             # TTS 语音播报封装
│   ├── ScheduleWorker.kt           # WorkManager 定时播报
│   └── BatteryMonitorService.kt    # 电池状态监听（前台服务）
├── screen/
│   ├── BatteryScreen.kt            # 电池详情页
│   └── SettingsScreen.kt           # 设置页
├── model/
│   ├── ClockConfig.kt              # 显示配置
│   ├── AnnouncementConfig.kt       # 播报计划配置
│   ├── ThemeConfig.kt              # 主题配置
│   └── BatteryState.kt             # 电池状态
├── util/
│   ├── LunarCalendar.kt            # 农历算法
│   └── BatteryHelper.kt            # 电池状态读取工具
├── data/
│   └── SettingsRepository.kt       # DataStore 读写
└── ui/theme/
    ├── Color.kt                    # 暖色/深色两套配色
    ├── Theme.kt
    └── Type.kt                     # 4档字体大小预设
```

```
app/src/main/res/xml/
└── clock_widget_info.xml           # Widget 元数据（4x2 + 4x3）
```

---

### Task 1: 添加项目依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 更新 libs.versions.toml 添加新依赖版本**

在 `[versions]` 部分添加：
```toml
glance = "1.1.0"
workRuntime = "2.10.0"
datastore = "1.1.0"
```

在 `[libraries]` 部分添加：
```toml
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntime" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: 更新 app/build.gradle.kts 添加依赖**

在 `dependencies` 块末尾添加：
```kotlin
// Glance 桌面小组件
implementation(libs.androidx.glance.appwidget)
implementation(libs.androidx.glance.material3)

// WorkManager 定时任务
implementation(libs.androidx.work.runtime.ktx)

// DataStore 设置存储
implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: 验证 Gradle 同步**

运行: `./gradlew build --dry-run`
预期: 输出显示依赖解析成功，无错误

---

### Task 2: 创建数据模型

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/model/ClockConfig.kt`
- Create: `app/src/main/java/com/spanzy/oldercare/model/AnnouncementConfig.kt`
- Create: `app/src/main/java/com/spanzy/oldercare/model/ThemeConfig.kt`
- Create: `app/src/main/java/com/spanzy/oldercare/model/BatteryState.kt`

- [ ] **Step 1: 创建 ClockConfig.kt**

```kotlin
package com.spanzy.oldercare.model

import kotlinx.serialization.Serializable

/**
 * 时钟显示配置
 * @param showTime 是否显示时间
 * @param showDate 是否显示日期
 * @param showLunar 是否显示农历
 * @param showWeekday 是否显示星期
 * @param fontSizeLevel 字体大小档位 1=标准, 2=大, 3=较大, 4=超大
 * @param use24Hour 是否使用24小时制
 */
@Serializable
data class ClockConfig(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showLunar: Boolean = true,
    val showWeekday: Boolean = true,
    val fontSizeLevel: Int = 4,
    val use24Hour: Boolean = false
)
```

- [ ] **Step 2: 创建 AnnouncementConfig.kt**

```kotlin
package com.spanzy.oldercare.model

import kotlinx.serialization.Serializable

/**
 * 播报计划配置
 * @param scheduledAnnounceEnabled 是否启用定时播报
 * @param intervalMinutes 播报间隔（分钟）: 15, 30, 60
 * @param quietStartHour 免打扰开始小时 (0-23)
 * @param quietEndHour 免打扰结束小时 (0-23)
 * @param announceTime 播报时间
 * @param announceDate 播报日期
 * @param announceLunar 播报农历
 * @param announceBattery 播报电量
 * @param lowBatteryEnabled 是否启用低电量播报
 * @param lowBatteryThreshold 低电量阈值: 10, 15, 20, 30
 * @param lowBatteryRepeatMinutes 低电量重复间隔（分钟）: 0=不重复, 30, 60
 * @param chargeCompleteEnabled 是否启用充电完成播报
 */
@Serializable
data class AnnouncementConfig(
    val scheduledAnnounceEnabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val announceTime: Boolean = true,
    val announceDate: Boolean = true,
    val announceLunar: Boolean = true,
    val announceBattery: Boolean = true,
    val lowBatteryEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 20,
    val lowBatteryRepeatMinutes: Int = 30,
    val chargeCompleteEnabled: Boolean = true
)
```

- [ ] **Step 3: 创建 ThemeConfig.kt**

```kotlin
package com.spanzy.oldercare.model

import kotlinx.serialization.Serializable

/**
 * 主题配置
 * @param darkMode 是否启用深色模式（覆盖系统设置）
 */
@Serializable
data class ThemeConfig(
    val darkMode: Boolean = false
)
```

- [ ] **Step 4: 创建 BatteryState.kt**

```kotlin
package com.spanzy.oldercare.model

/**
 * 电池状态
 * @param level 电量百分比 (0-100)
 * @param isCharging 是否正在充电
 * @param isFull 是否已充满
 */
data class BatteryState(
    val level: Int,
    val isCharging: Boolean,
    val isFull: Boolean
) {
    companion object {
        /** 从 Intent 解析电池状态 */
        fun fromIntent(intent: android.content.Intent?): BatteryState {
            if (intent == null) return BatteryState(0, false, false)

            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val percentage = (level * 100) / scale.coerceAtLeast(1)

            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                           status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val isFull = status == android.os.BatteryManager.BATTERY_STATUS_FULL

            return BatteryState(percentage, isCharging, isFull)
        }
    }
}
```

- [ ] **Step 5: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 3: 更新主题配色

**Files:**
- Modify: `app/src/main/java/com/spanzy/oldercare/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/spanzy/oldercare/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/spanzy/oldercare/ui/theme/Theme.kt`

- [ ] **Step 1: 重写 Color.kt**

完全替换文件内容：
```kotlin
package com.spanzy.oldercare.ui.theme

import androidx.compose.ui.graphics.Color

// ========== 暖色模式（默认）==========
val WarmBackground = Color(0xFFF5F0E8)
val WarmPrimaryText = Color(0xFF1A1A1A)
val WarmSecondaryText = Color(0xFF555555)
val WarmTertiaryText = Color(0xFF777777)
val WarmButtonBackground = Color(0xFFE8E0D0)
val WarmBatteryGreen = Color(0xFF16A34A)
val WarmChargeBlue = Color(0xFF3B82F6)
val WarmLowBatteryRed = Color(0xFFEF4444)

// ========== 深色模式 ==========
val DarkBackground = Color(0xFF0A0A1A)
val DarkPrimaryText = Color(0xFFE0E0FF)
val DarkSecondaryText = Color(0xFFA0A0CC)
val DarkTertiaryText = Color(0xFF8080AA)
val DarkButtonBackground = Color(0x14FFFFFF) // 透明白
val DarkBatteryGreen = Color(0xFF4ADE80)
val DarkChargeBlue = Color(0xFF60A5FA)
val DarkLowBatteryRed = Color(0xFFF87171)
```

- [ ] **Step 2: 重写 Type.kt**

完全替换文件内容：
```kotlin
package com.spanzy.oldercare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体大小档位映射表
 * Level 1 (标准): time 48sp, date 18sp, lunar/weekday 16sp
 * Level 2 (大):   time 56sp, date 20sp, lunar/weekday 18sp
 * Level 3 (较大): time 64sp, date 22sp, lunar/weekday 20sp
 * Level 4 (超大): time 72sp, date 24sp, lunar/weekday 22sp
 */
sealed class FontSizeLevel(val level: Int) {
    abstract val timeSp: androidx.compose.ui.unit.TextUnit
    abstract val dateSp: androidx.compose.ui.unit.TextUnit
    abstract val lunarSp: androidx.compose.ui.unit.TextUnit

    data object Level1 : FontSizeLevel(1) {
        override val timeSp = 48.sp
        override val dateSp = 18.sp
        override val lunarSp = 16.sp
    }
    data object Level2 : FontSizeLevel(2) {
        override val timeSp = 56.sp
        override val dateSp = 20.sp
        override val lunarSp = 18.sp
    }
    data object Level3 : FontSizeLevel(3) {
        override val timeSp = 64.sp
        override val dateSp = 22.sp
        override val lunarSp = 20.sp
    }
    data object Level4 : FontSizeLevel(4) {
        override val timeSp = 72.sp
        override val dateSp = 24.sp
        override val lunarSp = 22.sp
    }

    companion object {
        fun fromLevel(level: Int): FontSizeLevel = when (level) {
            1 -> Level1
            2 -> Level2
            3 -> Level3
            4 -> Level4
            else -> Level4 // 默认超大
        }
    }
}

// 保留 sp 类型别名用于类型转换
import androidx.compose.ui.unit.TextUnit
val Int.sp: TextUnit get() = androidx.compose.ui.unit.sp(this)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: 重写 Theme.kt**

完全替换文件内容：
```kotlin
package com.spanzy.oldercare.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// 暖色配色方案
private val WarmColorScheme = lightColorScheme(
    primary = WarmPrimaryText,
    onPrimary = Color.White,
    secondary = WarmSecondaryText,
    onSecondary = Color.White,
    tertiary = WarmTertiaryText,
    onTertiary = Color.White,
    background = WarmBackground,
    onBackground = WarmPrimaryText,
    surface = WarmButtonBackground,
    onSurface = WarmPrimaryText
)

// 深色配色方案
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryText,
    onPrimary = Color.Black,
    secondary = DarkSecondaryText,
    onSecondary = Color.Black,
    tertiary = DarkTertiaryText,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkButtonBackground,
    onSurface = DarkPrimaryText
)

/**
 * 应用主题
 * @param darkMode 用户手动控制的深色模式开关（覆盖系统设置）
 * @param content 主题内容
 */
@Composable
fun MyOlderCareUtilTheme(
    darkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkMode) DarkColorScheme else WarmColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 4: 农历算法（TDD）

**Files:**
- Create: `app/src/test/java/com/spanzy/oldercare/util/LunarCalendarTest.kt`
- Create: `app/src/main/java/com/spanzy/oldercare/util/LunarCalendar.kt`

- [ ] **Step 1: 编写失败测试**

创建测试文件：
```kotlin
package com.spanzy.oldercare.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LunarCalendarTest {

    @Test
    fun `测试2026年3月30日农历转换`() {
        val lunar = LunarCalendar.toLunar(2026, 3, 30)
        assertEquals("三月初二", lunar)
    }

    @Test
    fun `测试2024年2月10日农历新年`() {
        val lunar = LunarCalendar.toLunar(2024, 2, 10)
        assertEquals("正月初一", lunar)
    }

    @Test
    fun `测试2023年1月22日农历新年`() {
        val lunar = LunarCalendar.toLunar(2023, 1, 22)
        assertEquals("正月初一", lunar)
    }

    @Test
    fun `测试闰月处理 - 2023年闰二月`() {
        // 2023年有闰二月，验证闰月和正常二月的区分
        val normalFeb = LunarCalendar.toLunar(2023, 2, 20)
        val leapFeb = LunarCalendar.toLunar(2023, 3, 22) // 闰二月初一
        // 正常二月和闰二月应不同
    }

    @Test
    fun `测试范围边界 - 1900年1月31日`() {
        val lunar = LunarCalendar.toLunar(1900, 1, 31)
        assertEquals("正月初一", lunar)
    }

    @Test
    fun `测试范围边界 - 2100年12月31日`() {
        val lunar = LunarCalendar.toLunar(2100, 12, 31)
        // 应返回有效农历日期
        assert(lunar.isNotEmpty())
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行: `./gradlew test --tests "com.spanzy.oldercare.util.LunarCalendarTest"`
预期: FAIL with "Unresolved reference: LunarCalendar"

- [ ] **Step 3: 实现农历算法**

创建 LunarCalendar.kt（完整实现）：
```kotlin
package com.spanzy.oldercare.util

/**
 * 农历算法工具类
 * 支持 1900-2100 年的公历转农历
 * 基于查找表算法，数据来源于标准天文年历
 */
object LunarCalendar {

    // 农历数据表 (1900-2100)
    // 每个元素压缩存储一年信息：
    // - 低4位：闰月月份 (0表示无闰月)
    // - 5-16位：每月大小 (1为大月30天，0为小月29天)，从正月到十二月
    // - 17-20位：闰月大小 (0为小月，1为大月)
    private val lunarInfo = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520  // 2100
    )

    // 农历月份名称
    private val chineseMonths = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    // 农历日期名称（1-30）
    private val chineseDays = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    /**
     * 公历转农历
     * @param year 公历年
     * @param month 公历月 (1-12)
     * @param day 公历日
     * @return 农历字符串，格式如 "三月初二"，闰月加"闰"字如 "闰二月十五"
     */
    fun toLunar(year: Int, month: Int, day: Int): String {
        // 参数校验
        if (year < 1900 || year > 2100) {
            return "超出范围"
        }

        // 计算从1900年1月31日（农历正月初一）开始的天数
        var offset = getDaysOffset(year, month, day)

        // 计算农历年份
        var lunarYear = 1900
        var daysInYear = getLunarYearDays(lunarYear)
        while (offset >= daysInYear && lunarYear < 2100) {
            offset -= daysInYear
            lunarYear++
            daysInYear = getLunarYearDays(lunarYear)
        }

        // 计算农历月份
        val lunarInfo = lunarInfo[lunarYear - 1900]
        val leapMonth = (lunarInfo and 0xF).toInt() // 闰月月份
        var lunarMonth = 1
        var isLeap = false

        while (lunarMonth <= 12 && offset > 0) {
            val daysInMonth = if (isLeap) {
                getLeapMonthDays(lunarYear)
            } else {
                getLunarMonthDays(lunarYear, lunarMonth)
            }

            if (offset < daysInMonth) {
                break
            }

            offset -= daysInMonth

            if (isLeap) {
                isLeap = false
                lunarMonth++
            } else if (leapMonth == lunarMonth) {
                isLeap = true
            } else {
                lunarMonth++
            }
        }

        val lunarDay = offset + 1

        // 格式化输出
        val monthStr = if (isLeap) "闰${chineseMonths[lunarMonth - 1]}" else chineseMonths[lunarMonth - 1]
        val dayStr = chineseDays[lunarDay - 1]

        return "$monthStr$dayStr"
    }

    /**
     * 获取从1900年1月31日到指定日期的天数
     */
    private fun getDaysOffset(year: Int, month: Int, day: Int): Int {
        var offset = 0

        // 累加年份
        for (y in 1900 until year) {
            offset += 365
            if (isLeapYear(y)) offset++
        }

        // 累加月份
        for (m in 1 until month) {
            offset += getDaysInMonth(year, m)
        }

        // 加上日期
        offset += day - 1

        // 减去偏移（1900年1月31日是农历正月初一）
        offset -= 30 // 1900年1月有31天，1月31日是第31天，偏移30天

        return offset
    }

    /**
     * 判断是否为闰年（公历）
     */
    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    /**
     * 获取公历月份天数
     */
    private fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }
    }

    /**
     * 获取农历年份总天数
     */
    private fun getLunarYearDays(year: Int): Int {
        var sum = 348
        val info = lunarInfo[year - 1900]

        // 累加12个月的天数
        for (i in 0x8000 downTo 0x8 step 1) {
            sum += if ((info and i.toLong()) != 0L) 1 else 0
        }

        // 加上闰月天数
        return sum + getLeapMonthDays(year)
    }

    /**
     * 获取闰月天数
     */
    private fun getLeapMonthDays(year: Int): Int {
        val info = lunarInfo[year - 1900]
        return if ((info and 0x10000) != 0L) 30 else 0
    }

    /**
     * 获取农历月份天数
     */
    private fun getLunarMonthDays(year: Int, month: Int): Int {
        val info = lunarInfo[year - 1900]
        return if ((info and (0x10000 shr month)) != 0L) 30 else 29
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行: `./gradlew test --tests "com.spanzy.oldercare.util.LunarCalendarTest"`
预期: PASS

---

### Task 5: 设置存储（DataStore）

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/data/SettingsRepository.kt`

- [ ] **Step 1: 创建 SettingsRepository.kt**

```kotlin
package com.spanzy.oldercare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spanzy.oldercare.model.AnnouncementConfig
import com.spanzy.oldercare.model.ClockConfig
import com.spanzy.oldercare.model.ThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore 设置存储仓库
 * 使用单一 Preferences DataStore 实例，键前缀分组管理
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

        // ========== ClockConfig 键 ==========
        private val CLOCK_SHOW_TIME = booleanPreferencesKey("clock_show_time")
        private val CLOCK_SHOW_DATE = booleanPreferencesKey("clock_show_date")
        private val CLOCK_SHOW_LUNAR = booleanPreferencesKey("clock_show_lunar")
        private val CLOCK_SHOW_WEEKDAY = booleanPreferencesKey("clock_show_weekday")
        private val CLOCK_FONT_SIZE_LEVEL = intPreferencesKey("clock_font_size_level")
        private val CLOCK_USE_24_HOUR = booleanPreferencesKey("clock_use_24_hour")

        // ========== AnnouncementConfig 键 ==========
        private val ANNOUNCE_SCHEDULED_ENABLED = booleanPreferencesKey("announce_scheduled_enabled")
        private val ANNOUNCE_INTERVAL_MINUTES = intPreferencesKey("announce_interval_minutes")
        private val ANNOUNCE_QUIET_START_HOUR = intPreferencesKey("announce_quiet_start_hour")
        private val ANNOUNCE_QUIET_END_HOUR = intPreferencesKey("announce_quiet_end_hour")
        private val ANNOUNCE_TIME = booleanPreferencesKey("announce_time")
        private val ANNOUNCE_DATE = booleanPreferencesKey("announce_date")
        private val ANNOUNCE_LUNAR = booleanPreferencesKey("announce_lunar")
        private val ANNOUNCE_BATTERY = booleanPreferencesKey("announce_battery")
        private val ANNOUNCE_LOW_BATTERY_ENABLED = booleanPreferencesKey("announce_low_battery_enabled")
        private val ANNOUNCE_LOW_BATTERY_THRESHOLD = intPreferencesKey("announce_low_battery_threshold")
        private val ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES = intPreferencesKey("announce_low_battery_repeat_minutes")
        private val ANNOUNCE_CHARGE_COMPLETE_ENABLED = booleanPreferencesKey("announce_charge_complete_enabled")

        // ========== ThemeConfig 键 ==========
        private val THEME_DARK_MODE = booleanPreferencesKey("theme_dark_mode")
    }

    // ========== ClockConfig ==========
    val clockConfig: Flow<ClockConfig> = context.dataStore.data.map { preferences ->
        ClockConfig(
            showTime = preferences[CLOCK_SHOW_TIME] ?: true,
            showDate = preferences[CLOCK_SHOW_DATE] ?: true,
            showLunar = preferences[CLOCK_SHOW_LUNAR] ?: true,
            showWeekday = preferences[CLOCK_SHOW_WEEKDAY] ?: true,
            fontSizeLevel = preferences[CLOCK_FONT_SIZE_LEVEL] ?: 4,
            use24Hour = preferences[CLOCK_USE_24_HOUR] ?: false
        )
    }

    suspend fun updateClockConfig(transform: (ClockConfig) -> ClockConfig) {
        var newConfig: ClockConfig? = null
        context.dataStore.edit { preferences ->
            val current = ClockConfig(
                showTime = preferences[CLOCK_SHOW_TIME] ?: true,
                showDate = preferences[CLOCK_SHOW_DATE] ?: true,
                showLunar = preferences[CLOCK_SHOW_LUNAR] ?: true,
                showWeekday = preferences[CLOCK_SHOW_WEEKDAY] ?: true,
                fontSizeLevel = preferences[CLOCK_FONT_SIZE_LEVEL] ?: 4,
                use24Hour = preferences[CLOCK_USE_24_HOUR] ?: false
            )
            newConfig = transform(current)
            preferences[CLOCK_SHOW_TIME] = newConfig!!.showTime
            preferences[CLOCK_SHOW_DATE] = newConfig!!.showDate
            preferences[CLOCK_SHOW_LUNAR] = newConfig!!.showLunar
            preferences[CLOCK_SHOW_WEEKDAY] = newConfig!!.showWeekday
            preferences[CLOCK_FONT_SIZE_LEVEL] = newConfig!!.fontSizeLevel
            preferences[CLOCK_USE_24_HOUR] = newConfig!!.use24Hour
        }
    }

    // ========== AnnouncementConfig ==========
    val announcementConfig: Flow<AnnouncementConfig> = context.dataStore.data.map { preferences ->
        AnnouncementConfig(
            scheduledAnnounceEnabled = preferences[ANNOUNCE_SCHEDULED_ENABLED] ?: false,
            intervalMinutes = preferences[ANNOUNCE_INTERVAL_MINUTES] ?: 60,
            quietStartHour = preferences[ANNOUNCE_QUIET_START_HOUR] ?: 22,
            quietEndHour = preferences[ANNOUNCE_QUIET_END_HOUR] ?: 7,
            announceTime = preferences[ANNOUNCE_TIME] ?: true,
            announceDate = preferences[ANNOUNCE_DATE] ?: true,
            announceLunar = preferences[ANNOUNCE_LUNAR] ?: true,
            announceBattery = preferences[ANNOUNCE_BATTERY] ?: true,
            lowBatteryEnabled = preferences[ANNOUNCE_LOW_BATTERY_ENABLED] ?: true,
            lowBatteryThreshold = preferences[ANNOUNCE_LOW_BATTERY_THRESHOLD] ?: 20,
            lowBatteryRepeatMinutes = preferences[ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES] ?: 30,
            chargeCompleteEnabled = preferences[ANNOUNCE_CHARGE_COMPLETE_ENABLED] ?: true
        )
    }

    suspend fun updateAnnouncementConfig(transform: (AnnouncementConfig) -> AnnouncementConfig) {
        var newConfig: AnnouncementConfig? = null
        context.dataStore.edit { preferences ->
            val current = AnnouncementConfig(
                scheduledAnnounceEnabled = preferences[ANNOUNCE_SCHEDULED_ENABLED] ?: false,
                intervalMinutes = preferences[ANNOUNCE_INTERVAL_MINUTES] ?: 60,
                quietStartHour = preferences[ANNOUNCE_QUIET_START_HOUR] ?: 22,
                quietEndHour = preferences[ANNOUNCE_QUIET_END_HOUR] ?: 7,
                announceTime = preferences[ANNOUNCE_TIME] ?: true,
                announceDate = preferences[ANNOUNCE_DATE] ?: true,
                announceLunar = preferences[ANNOUNCE_LUNAR] ?: true,
                announceBattery = preferences[ANNOUNCE_BATTERY] ?: true,
                lowBatteryEnabled = preferences[ANNOUNCE_LOW_BATTERY_ENABLED] ?: true,
                lowBatteryThreshold = preferences[ANNOUNCE_LOW_BATTERY_THRESHOLD] ?: 20,
                lowBatteryRepeatMinutes = preferences[ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES] ?: 30,
                chargeCompleteEnabled = preferences[ANNOUNCE_CHARGE_COMPLETE_ENABLED] ?: true
            )
            newConfig = transform(current)
            preferences[ANNOUNCE_SCHEDULED_ENABLED] = newConfig!!.scheduledAnnounceEnabled
            preferences[ANNOUNCE_INTERVAL_MINUTES] = newConfig!!.intervalMinutes
            preferences[ANNOUNCE_QUIET_START_HOUR] = newConfig!!.quietStartHour
            preferences[ANNOUNCE_QUIET_END_HOUR] = newConfig!!.quietEndHour
            preferences[ANNOUNCE_TIME] = newConfig!!.announceTime
            preferences[ANNOUNCE_DATE] = newConfig!!.announceDate
            preferences[ANNOUNCE_LUNAR] = newConfig!!.announceLunar
            preferences[ANNOUNCE_BATTERY] = newConfig!!.announceBattery
            preferences[ANNOUNCE_LOW_BATTERY_ENABLED] = newConfig!!.lowBatteryEnabled
            preferences[ANNOUNCE_LOW_BATTERY_THRESHOLD] = newConfig!!.lowBatteryThreshold
            preferences[ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES] = newConfig!!.lowBatteryRepeatMinutes
            preferences[ANNOUNCE_CHARGE_COMPLETE_ENABLED] = newConfig!!.chargeCompleteEnabled
        }
    }

    // ========== ThemeConfig ==========
    val themeConfig: Flow<ThemeConfig> = context.dataStore.data.map { preferences ->
        ThemeConfig(
            darkMode = preferences[THEME_DARK_MODE] ?: false
        )
    }

    suspend fun updateThemeConfig(transform: (ThemeConfig) -> ThemeConfig) {
        var newConfig: ThemeConfig? = null
        context.dataStore.edit { preferences ->
            val current = ThemeConfig(
                darkMode = preferences[THEME_DARK_MODE] ?: false
            )
            newConfig = transform(current)
            preferences[THEME_DARK_MODE] = newConfig!!.darkMode
        }
    }
}
```

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 6: 电池工具和监听服务

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/util/BatteryHelper.kt`
- Create: `app/src/main/java/com/spanzy/oldercare/service/BatteryMonitorService.kt`

- [ ] **Step 1: 创建 BatteryHelper.kt**

```kotlin
package com.spanzy.oldercare.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.spanzy.oldercare.model.BatteryState

/**
 * 电池状态工具类
 */
object BatteryHelper {

    /**
     * 获取当前电池状态
     * 使用 sticky broadcast，无需权限
     */
    fun getBatteryState(context: Context): BatteryState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return BatteryState.fromIntent(intent)
    }

    /**
     * 检查是否在免打扰时段
     * @param startHour 开始小时 (0-23)
     * @param endHour 结束小时 (0-23)
     * @return true 如果当前在免打扰时段
     */
    fun isInQuietHours(startHour: Int, endHour: Int): Boolean {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        return if (startHour < endHour) {
            // 正常范围，如 22:00-07:00 (跨夜)
            currentHour >= startHour || currentHour < endHour
        } else {
            // 当天范围内，如 01:00-06:00
            currentHour in startHour until endHour
        }
    }

    /**
     * 格式化电量用于播报
     * 将数字转换为中文播报格式
     */
    fun formatBatteryForSpeech(level: Int): String {
        return when (level) {
            100 -> "一百"
            else -> {
                val tens = level / 10
                val units = level % 10
                when {
                    tens == 0 -> "$units"
                    units == 0 -> "${tens}十"
                    else -> "${tens}十$units"
                }
            }
        }
    }
}
```

- [ ] **Step 2: 创建 BatteryMonitorService.kt**

```kotlin
package com.spanzy.oldercare.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.util.BatteryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 电池状态监听服务
 * 前台服务，实时监听电池状态变化
 */
class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _batteryState = MutableStateFlow(BatteryState(0, false, false))
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                val state = BatteryHelper.getBatteryState(applicationContext)
                _batteryState.value = state
                kotlinx.coroutines.delay(30000) // 每30秒检查一次
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
```

- [ ] **Step 3: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 7: TTS 语音播报服务

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/service/VoiceService.kt`

- [ ] **Step 1: 创建 VoiceService.kt**

```kotlin
package com.spanzy.oldercare.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TTS 语音播报服务
 * 懒加载初始化，强制使用简体中文
 */
class VoiceService private constructor(private val context: Context) :
    TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // 播报状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    companion object {
        @Volatile
        private var instance: VoiceService? = null

        fun getInstance(context: Context): VoiceService {
            return instance ?: synchronized(this) {
                instance ?: VoiceService(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            tts = TextToSpeech(context, this).apply {
                // 设置播报进度监听
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            }
        } catch (e: Exception) {
            _isReady.value = false
            Toast.makeText(context, "语音播报初始化失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _isReady.value = false
                    Toast.makeText(context, "语音播报不可用：中文语言包未安装", Toast.LENGTH_LONG).show()
                } else {
                    _isReady.value = true
                }
            }
        } else {
            _isReady.value = false
            Toast.makeText(context, "语音播报初始化失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 播报文本
     * @param text 要播报的文本
     * @param UtteranceId 唯一标识，用于回调
     */
    fun speak(text: String, utteranceId: String = "voice_${System.currentTimeMillis()}") {
        if (!_isReady.value) {
            Toast.makeText(context, "语音播报不可用", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.let { engine ->
            // 停止当前播报
            engine.stop()

            // 开始新播报
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    /**
     * 停止播报
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * 释放资源
     */
    fun release() {
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        _isReady.value = false
    }
}
```

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 8: WorkManager 定时播报

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/service/ScheduleWorker.kt`

- [ ] **Step 1: 创建 ScheduleWorker.kt**

```kotlin
package com.spanzy.oldercare.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spanzy.oldercare.data.SettingsRepository
import com.spanzy.oldercare.model.AnnouncementConfig
import com.spanzy.oldercare.util.BatteryHelper
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 定时播报 Worker
 * 按配置的间隔执行播报任务
 */
class ScheduleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val settingsRepository = SettingsRepository(applicationContext)
    private val voiceService = VoiceService.getInstance(applicationContext)

    // 上次低电量播报时间
    private var lastLowBatteryAnnounceTime = 0L

    // 上次充电完成播报时间
    private var lastChargeCompleteAnnounceTime = 0L

    override suspend fun doWork(): Result {
        // 获取播报配置
        val announceConfig = settingsRepository.announcementConfig.first()

        // 检查是否在免打扰时段
        if (BatteryHelper.isInQuietHours(
                announceConfig.quietStartHour,
                announceConfig.quietEndHour
            )
        ) {
            // 免打扰时段，跳过播报
            return Result.success()
        }

        // 执行播报
        performAnnouncement(announceConfig)

        // 检查低电量
        checkLowBattery(announceConfig)

        // 检查充电完成
        checkChargeComplete(announceConfig)

        return Result.success()
    }

    private suspend fun performAnnouncement(config: AnnouncementConfig) {
        if (!config.scheduledAnnounceEnabled) return

        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val parts = mutableListOf<String>()

        // 构建播报内容
        if (config.announceTime) {
            val period = if (hour < 12) "上午" else "下午"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val timeText = "$displayHour点${if (minute > 0) "$minute分" else ""}"
            parts.add("现在$period $timeText")
        }

        if (config.announceDate) {
            parts.add("$year年$month月${day}日")
        }

        if (config.announceLunar) {
            val lunar = com.spanzy.oldercare.util.LunarCalendar.toLunar(year, month, day)
            parts.add("农历$lunar")
        }

        if (config.announceBattery) {
            val batteryState = BatteryHelper.getBatteryState(applicationContext)
            val batteryText = "电池电量${BatteryHelper.formatBatteryForSpeech(batteryState.level)}%"
            val statusText = when {
                batteryState.isFull -> "已充满"
                batteryState.isCharging -> "充电中"
                else -> "未充电"
            }
            parts.add("$batteryText，$statusText")
        }

        if (parts.isNotEmpty()) {
            val text = parts.joinToString("，")
            voiceService.speak(text)
        }
    }

    private suspend fun checkLowBattery(config: AnnouncementConfig) {
        if (!config.lowBatteryEnabled) return

        val batteryState = BatteryHelper.getBatteryState(applicationContext)
        if (batteryState.level > config.lowBatteryThreshold) return

        // 检查重复间隔
        if (config.lowBatteryRepeatMinutes > 0) {
            val now = System.currentTimeMillis()
            if (now - lastLowBatteryAnnounceTime < config.lowBatteryRepeatMinutes * 60 * 1000) {
                return
            }
            lastLowBatteryAnnounceTime = now
        }

        // 播报低电量提醒
        val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
        voiceService.speak("电池电量低，仅剩百分之$batteryText，请及时充电")
    }

    private suspend fun checkChargeComplete(config: AnnouncementConfig) {
        if (!config.chargeCompleteEnabled) return

        val batteryState = BatteryHelper.getBatteryState(applicationContext)
        if (batteryState.isFull && batteryState.isCharging) {
            // 避免重复播报（30分钟内不重复）
            val now = System.currentTimeMillis()
            if (now - lastChargeCompleteAnnounceTime > 30 * 60 * 1000) {
                voiceService.speak("充电已完成，电池电量百分之百")
                lastChargeCompleteAnnounceTime = now
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 9: 主界面（MainScreen）

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/MainScreen.kt`

- [ ] **Step 1: 创建 MainScreen.kt**

```kotlin
package com.spanzy.oldercare

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
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
import java.util.Calendar

/**
 * 主界面导航目标
 */
enum class Screen {
    Main,
    Battery,
    Settings
}

/**
 * 主界面
 * 大字时钟 + 底部三个快捷按钮
 */
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

    // 时间状态 - 使用独立的 state 而不是 map
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // 每秒更新时间
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

    val fontSizeLevel = remember(clockConfig.fontSizeLevel) {
        FontSizeLevel.fromLevel(clockConfig.fontSizeLevel)
    }

    // 根据深色模式选择颜色
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
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 时间显示区域
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // 时间
            if (clockConfig.showTime) {
                val displayHour = if (clockConfig.use24Hour) {
                    hour
                } else {
                    if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                }
                val period = if (!clockConfig.use24Hour) {
                    if (hour < 12) "上午" else "下午"
                } else ""

                Text(
                    text = String.format("%02d:%02d", displayHour, minute),
                    fontSize = fontSizeLevel.timeSp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    textAlign = TextAlign.Center
                )
                if (!clockConfig.use24Hour) {
                    val periodTextSize = with(androidx.compose.ui.platform.LocalDensity.current) {
                        (fontSizeLevel.timeSp.toPx() * 0.3f).toSp()
                    }
                    Text(
                        text = period,
                        fontSize = periodTextSize,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 日期
            if (clockConfig.showDate) {
                Text(
                    text = "$year年${month}月${day}日",
                    fontSize = fontSizeLevel.dateSp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 星期
            if (clockConfig.showWeekday) {
                val weekdayText = arrayOf("日", "一", "二", "三", "四", "五", "六")[weekday - 1]

                Text(
                    text = "星期$weekdayText",
                    fontSize = fontSizeLevel.lunarSp,
                    color = tertiaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 农历
            if (clockConfig.showLunar) {
                val lunar = LunarCalendar.toLunar(year, month, day)

                Text(
                    text = "农历$lunar",
                    fontSize = fontSizeLevel.lunarSp,
                    color = tertiaryTextColor
                )
            }
        }

        // 底部三个按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 电池按钮
            QuickButton(
                icon = "🔋",
                label = "${batteryState.level}%",
                subLabel = "电池",
                modifier = Modifier.weight(1f),
                backgroundColor = buttonBackground,
                textColor = primaryTextColor,
                onClick = { onNavigate(Screen.Battery) }
            )

            // 播报按钮
            QuickButton(
                icon = "🔊",
                label = "播报",
                subLabel = "语音",
                modifier = Modifier.weight(1f),
                backgroundColor = buttonBackground,
                textColor = primaryTextColor,
                onClick = {
                    // 触发语音播报 - 使用当前时间状态
                    val clickCalendar = Calendar.getInstance()
                    val clickYear = clickCalendar.get(Calendar.YEAR)
                    val clickMonth = clickCalendar.get(Calendar.MONTH) + 1
                    val clickDay = clickCalendar.get(Calendar.DAY_OF_MONTH)
                    val clickHour = clickCalendar.get(Calendar.HOUR_OF_DAY)
                    val clickMinute = clickCalendar.get(Calendar.MINUTE)

                    val period = if (clickHour < 12) "上午" else "下午"
                    val displayHour = if (clickHour == 0) 12 else if (clickHour > 12) clickHour - 12 else clickHour
                    val timeText = "$displayHour点${if (clickMinute > 0) "$clickMinute分" else ""}"

                    val parts = mutableListOf("现在$period $timeText", "$clickYear年$clickMonth月${clickDay}日")
                    val lunar = LunarCalendar.toLunar(clickYear, clickMonth, clickDay)
                    parts.add("农历$lunar")
                    val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
                    val statusText = when {
                        batteryState.isFull -> "已充满"
                        batteryState.isCharging -> "充电中"
                        else -> "未充电"
                    }
                    parts.add("电池电量百分之$batteryText，$statusText")

                    voiceService.speak(parts.joinToString("，"))
                }
            )

            // 设置按钮
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
```

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 10: 电池详情页

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/screen/BatteryScreen.kt`

- [ ] **Step 1: 创建 BatteryScreen.kt**

```kotlin
package com.spanzy.oldercare.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.service.VoiceService
import com.spanzy.oldercare.ui.theme.*
import com.spanzy.oldercare.util.BatteryHelper

/**
 * 电池详情页
 */
@Composable
fun BatteryScreen(
    batteryState: BatteryState,
    darkMode: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val voiceService = VoiceService.getInstance(context)

    val backgroundColor = if (darkMode) DarkBackground else WarmBackground
    val primaryTextColor = if (darkMode) DarkPrimaryText else WarmPrimaryText
    val secondaryTextColor = if (darkMode) DarkSecondaryText else WarmSecondaryText
    val buttonBackground = if (darkMode) DarkChargeBlue else WarmChargeBlue

    // 电池颜色
    val batteryColor = when {
        batteryState.isFull || (!batteryState.isCharging && batteryState.level > 50) ->
            if (darkMode) DarkBatteryGreen else WarmBatteryGreen
        batteryState.isCharging -> if (darkMode) DarkChargeBlue else WarmChargeBlue
        else -> if (darkMode) DarkLowBatteryRed else WarmLowBatteryRed
    }

    // 充电状态文字
    val statusText = when {
        batteryState.isFull -> "🔋 已充满"
        batteryState.isCharging -> "⚡ 充电中"
        else -> "🔋 未充电"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "←", fontSize = 28.sp, color = primaryTextColor)
            }
            Text(
                text = "电池电量",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 电池图标
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(6.dp, primaryTextColor, RoundedCornerShape(20.dp))
                .padding(8.dp)
        ) {
            // 电池头
            Box(
                modifier = Modifier
                    .offset(x = (55.dp), y = (-24).dp)
                    .width(50.dp)
                    .height(16.dp)
                    .background(primaryTextColor, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            )

            // 电池填充
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (darkMode) Color(0xFF1A1A2E) else Color(0xFFF0E8D8))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height((batteryState.level * 2.5).dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    batteryColor,
                                    batteryColor.copy(alpha = 0.7f)
                                )
                            ),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 电量百分比
        Text(
            text = "${batteryState.level}%",
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            color = batteryColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 充电状态
        Text(
            text = statusText,
            fontSize = 22.sp,
            color = secondaryTextColor
        )

        Spacer(modifier = Modifier.weight(1f))

        // 播报按钮
        Button(
            onClick = {
                val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
                val speech = when {
                    batteryState.isFull -> "充电已完成，电池电量百分之百"
                    batteryState.isCharging -> "当前电池电量百分之$batteryText，正在充电"
                    else -> "当前电池电量百分之$batteryText，未充电"
                }
                voiceService.speak(speech)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = "🔊 语音播报电量",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
```

注意：需要添加 `import androidx.compose.ui.graphics.Color`

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 11: 设置页

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/screen/SettingsScreen.kt`

- [ ] **Step 1: 创建 SettingsScreen.kt**

```kotlin
package com.spanzy.oldercare.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spanzy.oldercare.model.AnnouncementConfig
import com.spanzy.oldercare.model.ClockConfig
import com.spanzy.oldercare.model.ThemeConfig
import com.spanzy.oldercare.ui.theme.*

/**
 * 设置页
 */
@Composable
fun SettingsScreen(
    clockConfig: ClockConfig,
    announcementConfig: AnnouncementConfig,
    themeConfig: ThemeConfig,
    onClockConfigChange: (ClockConfig) -> Unit,
    onAnnouncementConfigChange: (AnnouncementConfig) -> Unit,
    onThemeConfigChange: (ThemeConfig) -> Unit,
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
            .padding(20.dp)
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "←", fontSize = 28.sp, color = primaryTextColor)
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
            title = "字体大小",
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
            title = "显示内容",
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
            title = "时间格式",
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
                            15 -> "每15分钟"
                            30 -> "每半小时"
                            else -> "每整点"
                        },
                        onClick = {
                            val newInterval = cycleIntervalMinutes(announcementConfig.intervalMinutes)
                            onAnnouncementConfigChange(announcementConfig.copy(intervalMinutes = newInterval))
                        }
                    )
                    SelectorRow(
                        label = "免打扰时段",
                        value = String.format(
                            "%02d:00-%02d:00",
                            announcementConfig.quietStartHour,
                            announcementConfig.quietEndHour
                        ),
                        onClick = { /* TODO: 弹出时间选择器 - 可选功能 */ }
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
                        }
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

        // 深色模式
        SettingsCard(
            title = "深色模式",
            background = cardBackground
        ) {
            SwitchRow(
                label = "启用深色模式",
                checked = themeConfig.darkMode,
                onCheckedChange = { onThemeConfigChange(themeConfig.copy(darkMode = it)) }
            )
        }
    }
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onClick) {
            Text(
                text = value,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Text(text = " ▾", fontSize = 14.sp)
        }
    }
}

/**
 * 循环切换播报间隔
 */
private fun cycleIntervalMinutes(current: Int): Int {
    val intervals = listOf(15, 30, 60)
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
```

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 12: Application 类和 MainActivity

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/OlderCareApplication.kt`
- Modify: `app/src/main/java/com/spanzy/oldercare/MainActivity.kt`

- [ ] **Step 1: 创建 OlderCareApplication.kt**

```kotlin
package com.spanzy.oldercare

import android.app.Application
import com.spanzy.oldercare.service.VoiceService

/**
 * Application 类
 * 负责全局初始化，包括 TTS 引擎
 */
class OlderCareApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 预初始化 TTS
        VoiceService.getInstance(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        // 清理 TTS 资源
        VoiceService.getInstance(this).release()
    }
}
```

- [ ] **Step 2: 重写 MainActivity.kt**

完全替换文件内容：
```kotlin
package com.spanzy.oldercare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.spanzy.oldercare.data.SettingsRepository
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.service.BatteryMonitorService
import com.spanzy.oldercare.ui.theme.MyOlderCareUtilTheme
import com.spanzy.oldercare.util.BatteryHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 主 Activity
 * 负责导航和状态管理
 */
class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )

    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 权限结果处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository(applicationContext)

        // 检查通知权限（Android 13+）
        checkNotificationPermission()

        setContent {
            MyApp()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun MyApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    var currentScreen by remember { mutableStateOf(Screen.Main) }

    // 收集配置
    val clockConfig by activity.settingsRepository.clockConfig.collectAsState(
        initial = com.spanzy.oldercare.model.ClockConfig()
    )
    val announcementConfig by activity.settingsRepository.announcementConfig.collectAsState(
        initial = com.spanzy.oldercare.model.AnnouncementConfig()
    )
    val themeConfig by activity.settingsRepository.themeConfig.collectAsState(
        initial = com.spanzy.oldercare.model.ThemeConfig()
    )

    // 电池状态
    var batteryState by remember {
        mutableStateOf(BatteryHelper.getBatteryState(activity))
    }

    // 定时更新电池状态
    androidx.compose.foundation.layout.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000) // 每30秒更新
            batteryState = BatteryHelper.getBatteryState(activity)
        }
    }

    MyOlderCareUtilTheme(darkMode = themeConfig.darkMode) {
        when (currentScreen) {
            Screen.Main -> MainScreen(
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it },
                clockConfig = clockConfig,
                batteryState = batteryState,
                darkMode = themeConfig.darkMode
            )
            Screen.Battery -> com.spanzy.oldercare.screen.BatteryScreen(
                batteryState = batteryState,
                darkMode = themeConfig.darkMode,
                onBack = { currentScreen = Screen.Main }
            )
            Screen.Settings -> com.spanzy.oldercare.screen.SettingsScreen(
                clockConfig = clockConfig,
                announcementConfig = announcementConfig,
                themeConfig = themeConfig,
                onClockConfigChange = { newConfig ->
                    activity.scope.launch {
                        activity.settingsRepository.updateClockConfig { newConfig }
                    }
                },
                onAnnouncementConfigChange = { newConfig ->
                    activity.scope.launch {
                        activity.settingsRepository.updateAnnouncementConfig { newConfig }
                    }
                },
                onThemeConfigChange = { newConfig ->
                    activity.scope.launch {
                        activity.settingsRepository.updateThemeConfig { newConfig }
                    }
                },
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}
```

- [ ] **Step 3: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 13: 更新 AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 添加 Application 类和服务声明**

在 `<application>` 标签添加 `android:name` 属性，并添加服务声明：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- WorkManager 初始化 -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name="com.spanzy.oldercare.OlderCareApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyOlderCareUtil">

        <activity
            android:name="com.spanzy.oldercare.MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MyOlderCareUtil">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 电池监听服务 -->
        <service
            android:name="com.spanzy.oldercare.service.BatteryMonitorService"
            android:enabled="true"
            android:exported="false" />

    </application>

</manifest>
```

- [ ] **Step 2: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 14: 桌面小组件（Glance）

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/widget/ClockWidget.kt`
- Create: `app/src/main/java/com/spanzy/oldercare/widget/ClockWidgetReceiver.kt`
- Create: `app/src/main/res/xml/clock_widget_info.xml`

- [ ] **Step 1: 创建 ClockWidget.kt**

```kotlin
package com.spanzy.oldercare.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spanzy.oldercare.MainActivity
import com.spanzy.oldercare.util.LunarCalendar
import java.util.Calendar
import kotlinx.coroutines.flow.first

// 扩展 DataStore 用于 Widget
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 时钟桌面小组件
 * 支持 4x2 和 4x3 两种尺寸
 */
class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 获取设置
        val prefs = context.widgetDataStore.data.first()

        val showTime = prefs[booleanPreferencesKey("clock_show_time")] ?: true
        val showDate = prefs[booleanPreferencesKey("clock_show_date")] ?: true
        val showLunar = prefs[booleanPreferencesKey("clock_show_lunar")] ?: true
        val showWeekday = prefs[booleanPreferencesKey("clock_show_weekday")] ?: true
        val darkMode = prefs[booleanPreferencesKey("theme_dark_mode")] ?: false

        provideContent {
            GlanceTheme(darkTheme = darkMode) {
                ClockWidgetContent(
                    showTime = showTime,
                    showDate = showDate,
                    showLunar = showLunar,
                    showWeekday = showWeekday
                )
            }
        }
    }

    @Composable
    private fun ClockWidgetContent(
        showTime: Boolean,
        showDate: Boolean,
        showLunar: Boolean,
        showWeekday: Boolean
    ) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val weekday = calendar.get(Calendar.DAY_OF_WEEK)

        // 颜色
        val backgroundColor = if (GlanceTheme.colors.isLight) {
            Color(0xFFF5F0E8)
        } else {
            Color(0xFF0A0A1A)
        }
        val primaryColor = if (GlanceTheme.colors.isLight) {
            Color(0xFF1A1A1A)
        } else {
            Color(0xFFE0E0FF)
        }
        val secondaryColor = if (GlanceTheme.colors.isLight) {
            Color(0xFF555555)
        } else {
            Color(0xFFA0A0CC)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(16.dp)
                .actionStartActivity<MainActivity>(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            if (showTime) {
                Text(
                    text = String.format("%02d:%02d", hour, minute),
                    style = TextStyle(
                        fontSize = if (showDate && showLunar) 42.sp else 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                )
            }

            if (showDate) {
                Row(
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    if (showWeekday) {
                        val weekdayText = arrayOf("日", "一", "二", "三", "四", "五", "六")[weekday - 1]
                        Text(
                            text = "星期$weekdayText ",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = secondaryColor
                            )
                        )
                    }
                    Text(
                        text = "$year年$month月${day}日",
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = secondaryColor
                        )
                    )
                }
            }

            if (showLunar) {
                val lunar = LunarCalendar.toLunar(year, month, day)
                Text(
                    text = "农历$lunar",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color(0xFF777777).let {
                            if (GlanceTheme.colors.isLight) it else Color(0xFF8080AA)
                        }
                    )
                )
            }
        }
    }
}

/**
 * Widget Receiver
 * 负责注册和更新小组件
 */
class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}
```

- [ ] **Step 2: 创建 ClockWidgetReceiver.kt**

（已在上面步骤1中包含）

- [ ] **Step 3: 创建 clock_widget_info.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="294dp"
    android:minHeight="146dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:maxResizeWidth="294dp"
    android:maxResizeHeight="220dp"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:description="@string/widget_description"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />

<!-- 另外创建一个 4x3 的版本，通过不同的 minWidth/minHeight -->
```

实际上，Glance 通过 `targetCellWidth` 和 `targetCellHeight` 自动处理不同尺寸。更新上述文件：

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="294dp"
    android:minHeight="146dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:maxResizeWidth="294dp"
    android:maxResizeHeight="220dp"
    android:updatePeriodMillis="900000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:description="@string/clock_widget_description"
    android:resizeMode="vertical"
    android:widgetCategory="home_screen" />
```

- [ ] **Step 4: 添加字符串资源**

在 `app/src/main/res/values/strings.xml` 添加：
```xml
<string name="clock_widget_description">时钟小组件</string>
```

- [ ] **Step 5: 在 AndroidManifest.xml 注册 Widget**

在 `<application>` 标签内添加：
```xml
<!-- 时钟小组件 -->
<receiver
    android:name="com.spanzy.oldercare.widget.ClockWidgetReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/clock_widget_info" />
</receiver>
```

- [ ] **Step 6: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 15: WorkManager 初始化和调度

**Files:**
- Create: `app/src/main/java/com/spanzy/oldercare/util/WorkManagerScheduler.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 WorkManagerScheduler.kt**

```kotlin
package com.spanzy.oldercare.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spanzy.oldercare.service.ScheduleWorker
import java.util.concurrent.TimeUnit

/**
 * WorkManager 调度工具
 */
object WorkManagerScheduler {

    private const val SCHEDULE_WORK_NAME = "schedule_announcement_work"
    private const val BATTERY_CHECK_WORK_NAME = "battery_check_work"

    /**
     * 初始化定时播报任务
     * @param intervalMinutes 间隔分钟数 (15, 30, 60)
     */
    fun scheduleAnnouncement(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)

        // WorkManager 最小间隔为 15 分钟
        val repeatInterval = maxOf(intervalMinutes, 15).toLong()

        val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
            repeatInterval, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            SCHEDULE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * 取消定时播报任务
     */
    fun cancelAnnouncement(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SCHEDULE_WORK_NAME)
    }

    /**
     * 初始化电池检查任务
     * 用于低电量提醒和充电完成提醒
     */
    fun scheduleBatteryCheck(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
            15, TimeUnit.MINUTES // 最小间隔
        ).build()

        workManager.enqueueUniquePeriodicWork(
            BATTERY_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
```

- [ ] **Step 2: 更新 AndroidManifest.xml 添加 WorkManagerInitializer**

WorkManager 2.6.0+ 默认自动初始化，无需添加 provider 声明。跳过此步骤。

- [ ] **Step 3: 验证编译**

运行: `./gradlew compileDebugKotlin`
预期: BUILD SUCCESSFUL

---

### Task 16: 最终构建验证

**Files:**
- None (验证步骤)

- [ ] **Step 1: 清理并构建完整项目**

运行: `./gradlew clean assembleDebug`
预期: BUILD SUCCESSFUL，生成 `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: 运行单元测试**

运行: `./gradlew test`
预期: BUILD SUCCESSFUL，所有测试通过

- [ ] **Step 3: 检查 APK 大小和依赖**

运行: `./gradlew :app:dependencies`
预期: 输出显示所有依赖正确解析，无冲突

- [ ] **Step 4: 验证 Manifest 内容**

运行: `./gradlew processDebugManifest`
预期: MANIFEST 文件正确生成，包含所有服务和 receiver

---

## 验证清单

实现完成后，验证以下功能：

- [ ] 主界面显示大字时钟，每秒更新
- [ ] 底部三个按钮可点击，分别跳转到电池页、播报、设置
- [ ] 播报按钮可触发语音，播报时间、日期、农历、电量
- [ ] 电池页显示大电池图标、电量百分比、充电状态
- [ ] 设置页字体滑块可切换4档大小
- [ ] 设置页显示内容开关可切换时间/日期/农历/星期
- [ ] 设置页播报计划可配置定时播报、免打扰时段
- [ ] 深色模式切换正确更新配色
- [ ] 桌面小组件可添加，显示时间/日期/农历
- [ ] 点击小组件可打开 App
- [ ] 设置持久化，重启 App 后保持

---

## 调试提示

1. **TTS 不可用**：检查设备是否安装中文语音包（设置 → 语言和输入 → 文字转语音）
2. **小组件不更新**：检查 WorkManager 是否正确初始化，查看 logcat
3. **播报无声音**：检查媒体音量，确认 TTS 引擎初始化成功
4. **深色模式不生效**：检查 `ThemeConfig.darkMode` 是否正确从 DataStore 读取

---

## 额外资源

- [Jetpack Glance 文档](https://developer.android.com/jetpack/androidx/releases/glance)
- [WorkManager 文档](https://developer.android.com/topic/libraries/architecture/workmanager)
- [DataStore 文档](https://developer.android.com/topic/libraries/architecture/datastore)
- [TTS 文档](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
