package com.spanzy.oldercare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.spanzy.oldercare.data.SettingsRepository
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.service.AnnouncementService
import com.spanzy.oldercare.service.VoiceService
import com.spanzy.oldercare.screen.BatteryScreen
import com.spanzy.oldercare.screen.SettingsScreen
import com.spanzy.oldercare.ui.theme.MyOlderCareUtilTheme
import com.spanzy.oldercare.util.BatteryHelper
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID

/**
 * 主 Activity
 * 负责导航和状态管理
 */
class MainActivity : ComponentActivity() {

    internal lateinit var settingsRepository: SettingsRepository
    internal val scope = CoroutineScope(
        Dispatchers.Main + SupervisorJob()
    )

    // 通知权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 权限结果处理 */ }

    // 图片选择器（带 uCrop 裁剪）
    internal val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // 启动 uCrop 裁剪
            startUCrop(uri)
        }
    }

    // uCrop 裁剪结果处理
    private val uCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { intent ->
                // 获取裁剪后的图片 URI（来自自己的 FileProvider）
                val resultUri = UCrop.getOutput(intent)
                if (resultUri != null) {
                    scope.launch {
                        // 将图片复制到持久存储目录
                        val persistentUri = copyImageToPersistentStorage(resultUri)
                        if (persistentUri != null) {
                            // 保存 URI（Widget 进程可以访问自己的 FileProvider）
                            settingsRepository.updateImageUri(persistentUri)
                        }
                    }
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            result.data?.let { intent ->
                val cropError = UCrop.getError(intent)
                cropError?.printStackTrace()
            }
        }
    }

    /**
     * 启动 uCrop 裁剪
     */
    private fun startUCrop(sourceUri: android.net.Uri) {
        // 创建缓存文件用于保存裁剪结果
        val cacheDir = cacheDir
        val destFile = File(cacheDir, "cropped_${UUID.randomUUID()}.jpg")

        // 使用 FileProvider 获取输出 URI
        val destUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            destFile
        )

        // 配置 uCrop
        val intent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)  // 正方形裁剪
            .withMaxResultSize(1024, 1024)  // 输出最大尺寸
            .getIntent(this)
        uCropLauncher.launch(intent)
    }

    /**
     * 将裁剪后的图片复制到持久存储目录
     * @return 新文件绝对路径，失败返回 null
     */
    private fun copyImageToPersistentStorage(sourceUri: android.net.Uri): String? {
        return try {
            val imagesDir = File(getExternalFilesDir(null), "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            // 每次用时间戳生成新文件名
            val destFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")

            contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // 清理旧图片
            imagesDir.listFiles()
                ?.filter { it.name.startsWith("img_") && it.name.endsWith(".jpg") && it != destFile }
                ?.forEach { it.delete() }

            android.util.Log.d("MainActivity", "图片已保存: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "保存图片失败", e)
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository(applicationContext)

        // 检查通知权限（Android 13+）
        checkNotificationPermission()

        // 检查是否从小组件点击进入，如果是则触发语音播报
        val fromWidgetClick = intent.getBooleanExtra("widget_click_announce", false)
        if (fromWidgetClick) {
            // 延迟执行以确保 UI 已初始化
            scope.launch {
                kotlinx.coroutines.delay(500)
                triggerVoiceAnnouncement()
            }
            // 清除标记，避免旋转屏幕等场景重复触发
            intent.removeExtra("widget_click_announce")
        }

        // 如果定时播报已开启，自动启动前台服务
        scope.launch {
            val config = settingsRepository.announcementConfig.first()
            if (config.scheduledAnnounceEnabled) {
                AnnouncementService.start(this@MainActivity)
            }
        }

        setContent {
            MyApp()
        }
    }

    /**
     * 触发语音播报（与主页播报按钮相同的行为）
     */
    private fun triggerVoiceAnnouncement() {
        val voiceService = VoiceService.getInstance(applicationContext)
        voiceService.initialize()

        scope.launch {
            val batteryState = BatteryHelper.getBatteryState(applicationContext)

            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) + 1
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)

            val period = if (hour < 12) "上午" else "下午"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val timeText = "${displayHour}点${if (minute > 0) "${minute}分" else ""}"

            val parts = mutableListOf("现在${period} ${timeText}", "${year}年${month}月${day}日")
            val lunar = com.spanzy.oldercare.util.LunarCalendar.toLunar(year, month, day)
            parts.add("农历${lunar}")
            val batteryText = com.spanzy.oldercare.util.BatteryHelper.formatBatteryForSpeech(batteryState.level)
            val statusText = when {
                batteryState.isFull -> "已充满"
                batteryState.isCharging -> "充电中"
                else -> "未充电"
            }
            parts.add("电池电量百分之${batteryText}，${statusText}")

            voiceService.speak(parts.joinToString("，"))
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
    val imageUri by activity.settingsRepository.imageUri.collectAsState(initial = null)

    // 电池状态
    var batteryState by remember {
        mutableStateOf(BatteryHelper.getBatteryState(activity))
    }

    // 定时更新电池状态
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000) // 每30秒更新
            batteryState = BatteryHelper.getBatteryState(activity)
        }
    }

    // 返回键处理 - 非主页面返回主页，主页才退出应用
    BackHandler(enabled = currentScreen != Screen.Main) {
        currentScreen = Screen.Main
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
            Screen.Battery -> BatteryScreen(
                batteryState = batteryState,
                darkMode = themeConfig.darkMode,
                onBack = { currentScreen = Screen.Main }
            )
            Screen.Settings -> SettingsScreen(
                clockConfig = clockConfig,
                announcementConfig = announcementConfig,
                themeConfig = themeConfig,
                imageUri = imageUri,
                onClockConfigChange = { newConfig ->
                    activity.scope.launch {
                        activity.settingsRepository.updateClockConfig { newConfig }
                    }
                },
                onAnnouncementConfigChange = { newConfig ->
                    activity.scope.launch {
                        activity.settingsRepository.updateAnnouncementConfig { newConfig }
                        // 启动或停止前台播报服务
                        if (newConfig.scheduledAnnounceEnabled) {
                            com.spanzy.oldercare.service.AnnouncementService.start(activity)
                        } else {
                            com.spanzy.oldercare.service.AnnouncementService.stop(activity)
                        }
                    }
                },
                onThemeConfigChange = { newConfig ->
                    activity.scope.launch {
                        activity.settingsRepository.updateThemeConfig { newConfig }
                    }
                },
                onImageUriChange = { uri ->
                    if (uri == "pick") {
                        // 打开图片选择器，选择后自动进入裁剪界面
                        activity.pickImageLauncher.launch("image/*")
                    } else {
                        activity.scope.launch {
                            activity.settingsRepository.updateImageUri(uri)
                        }
                    }
                },
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}
