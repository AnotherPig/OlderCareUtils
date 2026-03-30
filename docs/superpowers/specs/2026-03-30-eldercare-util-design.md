# 老年人实用工具 App 设计文档

## 概述

一款面向老年用户的 Android 实用工具，核心原则是**大、清晰、易用**。提供日期时间显示（桌面小组件 + app 全屏）、电池电量图文显示、语音播报功能。

## 需求总结

### 功能清单

1. **桌面小组件**（Glance）：日期时间农历显示，4x2 / 4x3 两种尺寸
2. **App 主界面**：大字时钟 + 底部快捷按钮（电池/播报/设置）
3. **电池详情页**：大电池图标 + 电量百分比 + 充电状态
4. **语音播报**：TTS 语音播报时间和电量，支持手动和定时触发
5. **设置页**：字体大小、显示内容、播报计划、深色模式

### 非功能需求

- 字体超大加粗，清晰可读
- 暖色浅底为默认配色（不刺眼），支持深色模式切换
- 操作简单直接，无复杂导航
- 适配 minSdk 23
- 禁用 Material You 动态颜色，使用固定暖色/深色调色板确保一致性

## 技术方案

**方案 A：Glance + Compose 全家桶**

| 层 | 技术 |
|---|---|
| 桌面小组件 | Jetpack Glance（Compose 风格 API） |
| App 界面 | Jetpack Compose + Material 3 |
| 语音播报 | Android TTS（TextToSpeech） |
| 定时任务 | WorkManager |
| 设置存储 | DataStore (Preferences) |
| 农历 | 自行实现农历算法 |

选择理由：项目已是纯 Compose 架构，Glance 保持代码风格一致；WorkManager 省电；DataStore 适配协程。

### 新增依赖

需要在 `gradle/libs.versions.toml` 中添加：

```toml
[versions]
glance = "1.1.0"
workRuntime = "2.10.0"
datastore = "1.1.0"

[libraries]
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workRuntime" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

## 项目结构

```
app/src/main/java/com/spanzy/oldercare/
├── OlderCareApplication.kt         # Application 类，TTS 初始化
├── MainActivity.kt                 # 入口，导航控制
├── MainScreen.kt                   # 主界面（大字时钟 + 底部按钮）
├── widget/
│   ├── ClockWidget.kt              # Glance 桌面小组件定义
│   └── ClockWidgetReceiver.kt      # Widget 更新广播接收器
├── service/
│   ├── VoiceService.kt             # TTS 语音播报封装
│   ├── ScheduleWorker.kt           # WorkManager 定时播报
│   └── BatteryMonitorService.kt    # 电池状态监听（前台服务）
├── screen/
│   ├── BatteryScreen.kt            # 电池详情页
│   └── SettingsScreen.kt           # 设置页
├── model/
│   ├── ClockConfig.kt              # 显示配置（哪些内容可见）
│   ├── AnnouncementConfig.kt       # 播报计划配置（含低电量、充电完成）
│   ├── ThemeConfig.kt              # 主题配置
│   └── BatteryState.kt             # 电池状态
├── util/
│   └── LunarCalendar.kt            # 农历算法
├── data/
│   └── SettingsRepository.kt       # DataStore 读写
└── ui/theme/
    ├── Color.kt                    # 暖色/深色两套配色
    ├── Theme.kt
    └── Type.kt                     # 4档字体大小预设
```

### 导航架构

使用简单的 Compose 状态驱动导航（无 Jetpack Navigation），单 Activity 内通过枚举切换屏幕：

```kotlin
enum class Screen { Main, Battery, Settings }
```

- 默认显示 MainScreen
- 底部按钮切换到 BatteryScreen / SettingsScreen
- 返回按钮回到 MainScreen
- 无复杂导航栈

### DataStore 键命名

使用单一 Preferences DataStore 实例，键前缀分组：
- `clock_*`：ClockConfig 相关
- `announce_*`：AnnouncementConfig 相关
- `theme_*`：ThemeConfig 相关

Repository 暴露三个独立的 `Flow`：
```kotlin
val clockConfig: Flow<ClockConfig>
val announcementConfig: Flow<AnnouncementConfig>
val themeConfig: Flow<ThemeConfig>
```

### 小组件数据流

```
DataStore -> SettingsRepository -> provideGlance { ... } -> Glance composable
```

小组件在 `provideGlance` 中读取 DataStore，通过 `currentState` 获取配置。

## 详细设计

### 1. 主界面（MainScreen）

**布局**：时钟为主 + 底部 3 个快捷按钮

```
┌─────────────────────────┐
│                         │
│        14:30            │  ← 超大加粗，每秒刷新
│  2026年3月30日 星期一    │
│     农历三月初二         │
│                         │
│  ┌──────┐┌──────┐┌─────┐│
│  │ 🔋75%││ 🔊   ││ ⚙️  ││  ← 底部三个大按钮
│  │ 电池 ││ 播报 ││设置 ││
│  └──────┘└──────┘└─────┘│
└─────────────────────────┘
```

**交互**：
- 🔋 按钮 → 进入电池详情页
- 🔊 按钮 → 立即语音播报时间和电量
- ⚙️ 按钮 → 进入设置页
- 电池按钮上实时显示当前电量百分比

**显示内容**：时间、日期、农历、星期各自可配置开关，默认全部显示。

**时间格式**：默认 12 小时制（带上午/下午），跟随系统设置。可在设置中切换 12/24 小时制。

### 2. 桌面小组件（ClockWidget）

**Glance 实现**，两种尺寸：

**4x2 紧凑型**：
```
┌─────────────────────┐
│ 14:30     星期一     │
│ 2026年3月30日        │
│ 农历三月初二          │
└─────────────────────┘
```

**4x3 大型**（含电量条）：
```
┌─────────────────────┐
│      14:30          │
│ 2026年3月30日 星期一  │
│    农历三月初二       │
│ 🔋 ████████░░ 75%   │
└─────────────────────┘
```

**规则**：
- 点击小组件 → 通过 `actionStartActivity<MainActivity>()` 打开 app 主界面
- 更新策略：WorkManager 周期性任务（最小 15 分钟）确保后台更新 + app 运行时 Glance `updateAll` 即时刷新
- 显示内容和配色跟随 app 内设置（`fontSizeLevel` 不影响小组件，小组件使用独立适配的固定尺寸）
- 使用系统字体（Glance 限制）
- 小组件尺寸由用户从启动器选择，无需存储尺寸偏好
- 无敏感数据，锁屏显示相同内容

### 3. 电池详情页（BatteryScreen）

**布局**：大电池图标 + 超大百分比 + 充电状态 + 播报按钮

```
┌─────────────────────────┐
│ ← 电池电量              │
│                         │
│      ┌────────┐         │
│      │  ━━━━  │ ← 电池头│
│      │ ██████ │         │
│      │ ██████ │ ← 填充 │
│      │ ██████ │         │
│      └────────┘         │
│                         │
│        75%              │  ← 超大数字
│     🔋 未充电            │
│                         │
│  ┌───────────────────┐  │
│  │ 🔊 语音播报电量    │  │  ← 全宽大按钮
│  └───────────────────┘  │
└─────────────────────────┘
```

**充电状态显示**：
- 未充电：绿色电池 + "未充电"
- 充电中：蓝色电池 + ⚡ + "充电中"
- 充满：绿色电池 + "已充满"

### 4. 语音播报（VoiceService + ScheduleWorker）

#### TTS 初始化与容错

- 在 `OlderCareApplication.kt` 中懒加载初始化 TTS 引擎
- 强制使用 `Locale.SIMPLIFIED_CHINESE`
- 检查 `TextToSpeech.SUCCESS` 初始化结果
- 处理中文语言包不可用的情况：显示 Toast 提示 "语音播报不可用"
- TTS 不可用时，播报按钮变为灰色不可点击状态

#### 播报格式

| 类型 | 播报内容 |
|------|---------|
| 时间 | "现在是 下午 2点30分，2026年3月30日，星期一，农历三月初二" |
| 电量 | "当前电池电量 百分之七十五，未充电" |
| 低电量 | "电池电量低，仅剩百分之二十，请及时充电" |
| 充电完成 | "充电已完成，电池电量百分之百" |

#### 触发方式

**手动触发**：
- 主屏播报按钮 → 播报时间+电量（可配置内容）
- 电池页播报按钮 → 播报电量
- 不受免打扰限制

**定时播报**（ScheduleWorker / WorkManager）：
- 用户可配置间隔：每整点 / 每半小时 / 每15分钟
- 可选播报内容：时间 / 日期 / 农历 / 电量，自由组合
- 免打扰时段：如 22:00-07:00，时段内定时播报直接跳过（不排队），主屏显示"免打扰中"
- 免打扰结束时不补播

**低电量播报**：
- 使用 WorkManager 周期性检查电池状态（最小间隔 15 分钟）
- 电量低于阈值（默认 20%）时自动播报
- 可配置阈值（10% / 15% / 20% / 30%）
- 重复提醒间隔：30分钟 / 1小时 / 不重复
- 受免打扰限制

**充电完成播报**：
- 使用 WorkManager 周期性检查
- 电量达到 100% 且充电中时播报提醒
- 受免打扰限制

### 5. 设置页（SettingsScreen）

**字体大小**：
- Compose Slider 控件，`steps = 3`（4 档离散）
- 4 个预设节点：标准 / 大 / 较大 / 超大
- 默认值：超大（第 4 档）
- 具体映射表：

| 档位 | 时间 | 日期 | 农历/星期 |
|------|------|------|-----------|
| 标准 | 48sp | 18sp | 16sp |
| 大 | 56sp | 20sp | 18sp |
| 较大 | 64sp | 22sp | 20sp |
| 超大 | 72sp | 24sp | 22sp |

- 该设置仅影响 app 内显示，小组件使用独立适配的固定尺寸

**显示内容开关**：
- 时间：默认开
- 日期：默认开
- 农历：默认开
- 星期：默认开

**时间格式**：
- 12小时制 / 24小时制，默认 12小时制

**播报计划**：
- 定时播报开关：默认关
- 播报间隔：整点 / 半小时 / 15分钟
- 免打扰时段：默认 22:00-07:00
- 播报内容选择：时间 / 日期 / 农历 / 电量，多选
- 低电量播报开关：默认开
- 低电量阈值：默认 20%
- 重复提醒间隔：默认 30分钟
- 充电完成播报开关：默认开

**深色模式**：
- 开关切换，默认关（暖色浅底）
- 覆盖系统 `isSystemInDarkTheme()`，用户手动控制
- 切换后 app 和小组件同步更新

### 6. 配色方案

**暖色模式（默认）**：

| 元素 | 颜色 |
|------|------|
| 背景 | `#f5f0e8`（暖米白） |
| 主文字 | `#1a1a1a` |
| 副文字 | `#555555` |
| 辅助文字 | `#777777` |
| 按钮背景 | `#e8e0d0` |
| 电池绿 | `#16a34a` |
| 充电蓝 | `#3b82f6` |
| 低电红 | `#ef4444` |

**深色模式**：

| 元素 | 颜色 |
|------|------|
| 背景 | `#0a0a1a` |
| 主文字 | `#e0e0ff` |
| 副文字 | `#a0a0cc` |
| 辅助文字 | `#8080aa` |
| 按钮背景 | `#14FFFFFF`（透明白） |
| 电池绿 | `#4ade80` |
| 充电蓝 | `#60a5fa` |
| 低电红 | `#f87171` |

## 数据模型

### ClockConfig

```kotlin
data class ClockConfig(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showLunar: Boolean = true,
    val showWeekday: Boolean = true,
    val fontSizeLevel: Int = 4,   // 1=标准, 2=大, 3=较大, 4=超大
    val use24Hour: Boolean = false // 12小时制 / 24小时制
)
```

### AnnouncementConfig（原 ScheduleConfig，重命名以准确反映职责）

```kotlin
data class AnnouncementConfig(
    val scheduledAnnounceEnabled: Boolean = false,
    val intervalMinutes: Int = 60,           // 15, 30, 60
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val announceTime: Boolean = true,
    val announceDate: Boolean = true,
    val announceLunar: Boolean = true,
    val announceBattery: Boolean = true,
    val lowBatteryEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 20,       // 10, 15, 20, 30
    val lowBatteryRepeatMinutes: Int = 30,   // 0=不重复, 30, 60
    val chargeCompleteEnabled: Boolean = true
)
```

### ThemeConfig

```kotlin
data class ThemeConfig(
    val darkMode: Boolean = false  // 覆盖系统设置，用户手动控制
)
```

### BatteryState

```kotlin
data class BatteryState(
    val level: Int,         // 0-100
    val isCharging: Boolean,
    val isFull: Boolean
)
```

## 关键实现要点

1. **农历算法**：基于查找表的农历算法，覆盖 1900-2100 年，数据来源为标准天文年历。包含闰月处理，显示格式为"农历X月X日"。约 200-300 行查找表数据。
2. **小组件更新**：不依赖 `ACTION_TIME_TICK`（仅动态注册有效，app 被杀后失效）。使用 WorkManager PeriodicWorkRequest（最小间隔 15 分钟）保证后台更新；app 前台时通过 Glance `updateAll` 即时刷新。
3. **电池监听**：`ACTION_BATTERY_CHANGED` 是 sticky broadcast，无需权限，任何时刻可获取当前状态。后台低电量/充电完成检测通过 WorkManager 周期性轮询（15 分钟间隔），不依赖 manifest 注册的静态 Receiver。
4. **TTS 生命周期**：在 `OlderCareApplication` 中懒加载初始化 TTS 引擎，强制使用 `Locale.SIMPLIFIED_CHINESE`。检查初始化成功状态，不可用时禁用播报按钮并提示用户。按需 shutdown 避免内存泄漏。
5. **WorkManager**：使用 PeriodicWorkRequest 实现定时播报和电池状态检查。后台任务可能在某些 OEM 上被 Doze 限制，在设置页提供"忽略电池优化"引导。
6. **DataStore**：所有设置通过单一 Preferences DataStore 实例持久化，键前缀分组（`clock_*`, `announce_*`, `theme_*`）。变更时通知 UI 和小组件更新。
7. **字体大小**：Compose Slider 使用 `steps = 3` 实现离散 4 档选择。`fontSizeLevel` 存储 1-4 整数，映射到具体 sp 值（见设置页映射表）。仅影响 app 内显示，不影响小组件。
8. **主题集成**：禁用 Material You 动态颜色，使用固定暖色/深色调色板。`ThemeConfig.darkMode` 覆盖 `isSystemInDarkTheme()`，由用户手动控制。
