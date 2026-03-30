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

## 项目架构

### 技术栈

- **语言**: Kotlin 2.2.10
- **UI 框架**: Jetpack Compose + Material 3
- **构建系统**: Gradle 9.3.1 + Kotlin DSL（`.kts`）
- **AGP**: 9.1.0
- **最低 SDK**: 23，目标/编译 SDK: 36
- **Java 兼容**: Java 11

### 项目结构

单模块 Android 应用，包名 `com.spanzy.oldercare`。

```
app/src/main/java/com/spanzy/oldercare/
├── MainActivity.kt          # 唯一 Activity，使用 Compose setContent
└── ui/theme/                # Compose 主题定义
    ├── Color.kt             # 颜色常量（Purple/Pink 系列）
    ├── Theme.kt             # MyOlderCareUtilTheme，支持动态颜色（Android 12+）
    └── Type.kt              # 排版定义
```

### 关键约定

- **Compose-only UI**: 没有 XML 布局文件，全部使用 Jetpack Compose
- **Kotlin DSL**: 所有 Gradle 脚本使用 `.kts` 格式。声明 Maven 仓库时必须使用 `maven { url = uri("...") }` 语法，不能用 Groovy 的 `maven { url '...' }`
- **版本目录**: 依赖版本统一管理在 `gradle/libs.versions.toml`
- **Compose BOM**: Compose 库版本通过 BOM（`compose-bom:2026.02.01`）统一管理，单个 Compose 库不需指定版本

### 已知问题

- `app/build.gradle.kts` 第 8-12 行的 `compileSdk` 配置使用了非标准写法 `compileSdk { version = release(36) { ... } }`，这是 AGP 9.x 新增的 incubating API，可能产生编译警告
- `settings.gradle.kts` 中 `dependencyResolutionManagement` 的相关 API 被 `@Incubating` 标注，会产生 IDE 警告
