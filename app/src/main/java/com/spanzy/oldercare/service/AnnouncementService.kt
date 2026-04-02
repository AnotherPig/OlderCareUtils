package com.spanzy.oldercare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.spanzy.oldercare.MainActivity
import com.spanzy.oldercare.R
import com.spanzy.oldercare.data.settingsDataStore
import com.spanzy.oldercare.util.BatteryHelper
import com.spanzy.oldercare.util.LunarCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 定时播报前台服务
 * 使用协程定时器替代 WorkManager，支持任意间隔（含 1 分钟）
 * 前台服务确保后台持续运行
 */
class AnnouncementService : Service() {

    companion object {
        private const val TAG = "AnnouncementService"
        private const val CHANNEL_ID = "announcement_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AnnouncementService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AnnouncementService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val voiceService by lazy { VoiceService.getInstance(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("定时播报运行中"))
        Log.d(TAG, "定时播报服务已启动")

        serviceScope.launch {
            runAnnouncementLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "定时播报服务已停止")
    }

    /**
     * 主播报循环
     */
    private suspend fun runAnnouncementLoop() {
        while (true) {
            try {
                val prefs = applicationContext.settingsDataStore.data.first()

                val enabled = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("announce_scheduled_enabled")] ?: false
                val intervalMinutes = prefs[androidx.datastore.preferences.core.intPreferencesKey("announce_interval_minutes")] ?: 60

                if (!enabled) {
                    Log.d(TAG, "定时播报已关闭，停止服务")
                    stopSelf()
                    return
                }

                // 检查免打扰
                val quietStart = prefs[androidx.datastore.preferences.core.intPreferencesKey("announce_quiet_start_hour")] ?: 22
                val quietEnd = prefs[androidx.datastore.preferences.core.intPreferencesKey("announce_quiet_end_hour")] ?: 7

                if (!BatteryHelper.isInQuietHours(quietStart, quietEnd)) {
                    performAnnouncement()
                } else {
                    Log.d(TAG, "免打扰时段，跳过播报")
                }

                // 等待指定间隔
                delay(intervalMinutes * 60 * 1000L)
            } catch (e: Exception) {
                Log.e(TAG, "播报循环异常", e)
                delay(60_000L) // 出错时等1分钟再重试
            }
        }
    }

    /**
     * 构建并执行播报
     */
    private suspend fun performAnnouncement() {
        val prefs = applicationContext.settingsDataStore.data.first()

        val announceTime = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("announce_time")] ?: true
        val announceDate = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("announce_date")] ?: true
        val announceLunar = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("announce_lunar")] ?: true
        val announceBattery = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("announce_battery")] ?: true
        val use24Hour = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("clock_use_24_hour")] ?: false

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val parts = mutableListOf<String>()

        if (announceTime) {
            val period = if (use24Hour) "" else if (hour < 12) "上午" else "下午"
            val displayHour = if (use24Hour) hour else if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val timeText = "${displayHour}点${if (minute > 0) "${minute}分" else ""}"
            if (period.isNotEmpty()) parts.add("现在${period} ${timeText}") else parts.add("现在${timeText}")
        }

        if (announceDate) {
            parts.add("${year}年${month}月${day}日")
        }

        if (announceLunar) {
            parts.add("农历${LunarCalendar.toLunar(year, month, day)}")
        }

        if (announceBattery) {
            val batteryState = BatteryHelper.getBatteryState(applicationContext)
            val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
            val statusText = when {
                batteryState.isFull -> "已充满"
                batteryState.isCharging -> "充电中"
                else -> ""
            }
            if (statusText.isNotEmpty()) {
                parts.add("电池电量百分之${batteryText}，${statusText}")
            } else {
                parts.add("电池电量百分之${batteryText}")
            }
        }

        val message = parts.joinToString("，")
        if (message.isNotEmpty()) {
            Log.d(TAG, "定时播报: $message")
            // 确保初始化 TTS
            voiceService.initialize()
            voiceService.speak(message)

            // 更新通知
            updateNotification("上次播报: ${String.format("%02d:%02d", hour, minute)}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定时播报",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "定时语音播报服务"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("长者助手")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) { }
    }
}
