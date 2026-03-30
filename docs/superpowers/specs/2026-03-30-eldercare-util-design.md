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

## 项目结构

```
app/src/main/java/com/spanzy/oldercare/
├── MainActivity.kt                 # 入口，导航控制
├── MainScreen.kt                   # 主界面（大字时钟 + 底部按钮）
├── widget/
│   ├── ClockWidget.kt              # Glance 桌面小组件定义
│   └── ClockWidgetReceiver.kt      # Widget 更新广播接收器
├── service/
│   ├── VoiceService.kt             # TTS 语音播报封装
│   └── ScheduleWorker.kt           # WorkManager 定时播报
├── screen/
│   ├── BatteryScreen.kt            # 电池详情页
│   └── SettingsScreen.kt           # 设置页
├── model/
│   ├── ClockConfig.kt              # 显示配置（哪些内容可见）
│   └── ScheduleConfig.kt           # 播报计划配置
├── util/
│   └── LunarCalendar.kt            # 农历算法
├── data/
│   └── SettingsRepository.kt       # DataStore 读写
└── ui/theme/
    ├── Color.kt                    # 暖色/深色两套配色
    ├── Theme.kt
    └── Type.kt                     # 4档字体大小预设
```

导航流：主屏 → 底部按钮分别进入电池详情页 / 触发播报 / 进入设置页。无复杂导航栈。

## 详细设计

### 1. 主界面（MainScreen）

**布局**：时钟为主 + 底部 3 个快捷按钮

```
┌─────────────────────────┐
│                         │
│        14:30            │  ← 72sp 超大加粗，每秒刷新
│  2026年3月30日 星期一    │  ← 24sp
│     农历三月初二         │  ← 22sp
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

### 2. 桌面小组件（ClockWidget）

**Glance 实现**，两种尺寸：

**4x2 紧凑型**：
```
┌─────────────────────┐
│ 14:30     星期一     │  ← 42sp 加粗
│ 2026年3月30日        │  ← 15sp
│ 农历三月初二          │  ← 14sp
└─────────────────────┘
```

**4x3 大型**（含电量条）：
```
┌─────────────────────┐
│      14:30          │  ← 56sp 超大加粗
│ 2026年3月30日 星期一  │  ← 18sp
│    农历三月初二       │  ← 16sp
│ 🔋 ████████░░ 75%   │  ← 电量进度条
└─────────────────────┘
```

**规则**：
- 点击小组件 → 打开 app 主界面
- 每分钟自动刷新时间（Glance 更新机制）
- 显示内容和配色跟随 app 内设置
- 使用系统字体（Glance 限制）

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
│        75%              │  ← 64sp 超大数字
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
- 免打扰时段：如 22:00-07:00，时段内静音

**低电量播报**：
- 监听电池变化广播（ACTION_BATTERY_LOW / 自定义阈值）
- 电量低于阈值（默认 20%）时自动播报
- 可配置阈值（10% / 15% / 20% / 30%）
- 重复提醒间隔：30分钟 / 1小时 / 不重复

**充电完成播报**：
- 监听充电状态变化
- 电量达到 100% 时播报提醒

### 5. 设置页（SettingsScreen）

**字体大小**：
- 滑动条控件，无级滑动
- 4 个预设节点：标准 / 大 / 较大 / 超大
- 默认值：超大

**显示内容开关**：
- 时间：默认开
- 日期：默认开
- 农历：默认开
- 星期：默认开

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
| 按钮背景 | `rgba(255,255,255,0.08)` |
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
    val fontSizeLevel: Int = 4 // 1=标准, 2=大, 3=较大, 4=超大
)
```

### ScheduleConfig

```kotlin
data class ScheduleConfig(
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
    val darkMode: Boolean = false
)
```

## 关键实现要点

1. **农历算法**：自行实现，覆盖 1900-2100 年范围，包含天干地支、生肖等信息
2. **小组件更新**：Glance 通过 `updateAll` 触发更新，配合 `BroadcastReceiver` 监听 `ACTION_TIME_TICK` 实现每分钟刷新
3. **电池监听**：注册 `BroadcastReceiver` 监听 `ACTION_BATTERY_CHANGED`，获取电量、充电状态
4. **TTS 生命周期**：在 Application 中初始化 TTS 引擎，按需 shutdown，避免内存泄漏
5. **WorkManager**：使用 PeriodicWorkRequest 实现定时播报，约束条件为设备空闲
6. **DataStore**：所有设置通过 Preferences DataStore 持久化，变更时通知 UI 和小组件更新
7. **字体大小映射**：4 档预设对应具体的 sp 值，滑动条连续调整时映射到最近的预设
