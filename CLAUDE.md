# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# 构建 debug APK
./gradlew assembleDebug

# 构建 release APK
./gradlew assembleRelease

# 清理构建产物
./gradlew clean

# 运行单元测试（本地 JVM）
./gradlew test

# 运行单个单元测试类
./gradlew test --tests "com.spanzy.oldercare.ExampleUnitTest"

# 运行 Android Instrumented 测试（需连接设备/模拟器）
./gradlew connectedAndroidTest

# 查看依赖树
./gradlew :app:dependencies
```

注意：本项目 Gradle wrapper 使用阿里云镜像源，构建依赖通过 `settings.gradle.kts` 中配置的阿里云 Maven 仓库下载。

## 技术栈

- **语言**: Kotlin 2.2.10
- **UI 框架**: Jetpack Compose + Material 3
- **构建系统**: Gradle 9.3.1 + Kotlin DSL（`.kts`）
- **AGP**: 9.1.0
- **最低 SDK**: 23，目标/编译 SDK: 36
- **Java 兼容**: Java 11
- **Compose BOM**: `2026.02.01`，单个 Compose 库不需指定版本
- **持久化**: DataStore Preferences
- **后台任务**: WorkManager + AlarmManager + 前台服务
- **图片裁剪**: uCrop (com.github.Yalantis:ucrop:2.2.8)

## 项目架构

单模块 Android 应用，包名 `com.spanzy.oldercare`，面向老年用户的实用工具（时钟、电池监控、语音播报）。

### 导航

单 Activity 架构。`MainActivity` 通过 Compose state 切换三个页面：`MainScreen`（主页时钟）、`BatteryScreen`（电池详情）、`SettingsScreen`（设置）。无 Navigation 框架，使用简单的 `Screen` 枚举 + `BackHandler`。

### 数据层

`data/SettingsRepository.kt` 是唯一的配置中心，使用 `Context.settingsDataStore`（顶层扩展属性，单一 Preferences DataStore 实例）。管理三类配置 Flow：

- `clockConfig: Flow<ClockConfig>` — 时钟显示选项（时间/日期/农历/星期、字号级别、12/24小时）
- `announcementConfig: Flow<AnnouncementConfig>` — 语音播报设置（间隔、免打扰时段、播报内容、低电量提醒）
- `themeConfig: Flow<ThemeConfig>` — 深色模式、小组件点击播报开关

每个 `updateXxxConfig(transform)` 方法：写入 DataStore → 调用 `notifyWidgetUpdate()` 刷新所有小组件。DataStore 键使用前缀分组（`clock_*`、`announce_*`、`theme_*`、`image_uri`）。

### 服务层

| 服务 | 类型 | 用途 |
|------|------|------|
| `VoiceService` | 单例 | TTS 语音合成，异步初始化，处理小米设备兼容性 |
| `AnnouncementService` | 前台服务（specialUse） | 协程循环定时播报，支持1分钟间隔，需前台通知 |
| `BatteryMonitorService` | 前台服务 | 电池状态监控，低电量/充电完成提醒 |

`AnnouncementService` 使用协程循环而非 WorkManager（后者最小间隔15分钟）。`MainActivity.onCreate()` 在定时播报开关开启时自动启动此服务。

### 小组件层（RemoteViews）

**不使用 Glance**。所有小组件使用 `AppWidgetProvider` + `RemoteViews` 直接渲染。

**时钟小组件** (`widget/ClockWidget.kt`)：
- `BaseClockWidgetReceiver` 抽象基类，4个尺寸子类（`4x1`~`4x4`）
- `ClockTickReceiver` 负责每分钟更新：AlarmManager `setExactAndAllowWhileIdle` 自调度 + `ACTION_TIME_TICK` 系统广播双保险
- `onUpdate()` 使用 `goAsync()` 避免ANR（DataStore 读取是IO操作）
- 需要权限：`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`

**图片小组件** (`widget/ImageWidget.kt`)：
- `BaseImageWidgetReceiver` 基类，3个尺寸子类（`2x2`、`3x3`、`4x4`）
- 安全加载 Bitmap（采样压缩、CenterCrop、RGB_565）
- 图片通过 uCrop 裁剪后保存到 `getExternalFilesDir(null)/images/`

**刷新机制**：
- `OlderCareApplication` 注册 `ACTION_TIME_TICK` 实现进程存活时的精确分钟更新
- `ClockTickReceiver` 处理 `BOOT_COMPLETED` 在重启后重新调度
- `SettingsChangeReceiver` 接收设置变更广播立即刷新
- `SettingsRepository.notifyWidgetUpdate()` 直接调用 `refreshAll()` 同步更新

### UI 主题

`ui/theme/Theme.kt` 定义 `MyOlderCareUtilTheme`，支持深色/浅色模式。颜色方案为暖色调（非默认 Material），大字号针对老年用户优化。`FontSizeLevel` 枚举（1-4）控制小组件字号。

### 关键约定

- **Compose-only 应用 UI**：应用内全部使用 Jetpack Compose，无 XML 布局
- **XML 布局仅用于小组件**：`clock_widget_layout.xml` 和 `image_widget_layout.xml` 是 RemoteViews 所需的 XML 布局
- **Kotlin DSL**：所有 Gradle 脚本使用 `.kts` 格式，声明 Maven 仓库必须用 `maven { url = uri("...") }` 语法
- **版本目录**：依赖版本统一管理在 `gradle/libs.versions.toml`
- **不可变配置**：所有 Config 类为 `data class`，通过 `transform` 函数更新
- **Widget 进程隔离**：小组件运行在独立进程，通过 DataStore（文件）共享配置，不能依赖内存状态
- **所有代码注释使用中文**

### 已知问题

- `app/build.gradle.kts` 的 `compileSdk` 使用 AGP 9.x incubating API，会产生编译警告
- `settings.gradle.kts` 中 `dependencyResolutionManagement` 的 API 被 `@Incubating` 标注
- `ScheduleWorker.kt` 是旧 WorkManager 播报的遗留代码，已被 `AnnouncementService` 替代
