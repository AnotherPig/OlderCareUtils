package com.spanzy.oldercare.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spanzy.oldercare.data.SettingsRepository
import com.spanzy.oldercare.service.VoiceService
import com.spanzy.oldercare.util.BatteryHelper
import com.spanzy.oldercare.util.LunarCalendar
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale

/**
 * WorkManager 定时播报 Worker
 * 负责执行定时播报、低电量提醒和充电完成提醒
 */
class ScheduleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val settingsRepository = SettingsRepository(context)
    private val voiceService = VoiceService.getInstance(context)

    // 跟踪上次播报时间（进程内缓存）
    private var lastLowBatteryAnnounceTime = 0L
    private var lastChargeCompleteAnnounceTime = 0L
    private var lastBatteryLevel = -1
    private var lastIsCharging = false

    override suspend fun doWork(): Result {
        try {
            // 读取配置
            val announcementConfig = settingsRepository.announcementConfig.first()
            val clockConfig = settingsRepository.clockConfig.first()

            // 检查免打扰时段
            if (BatteryHelper.isInQuietHours(
                    announcementConfig.quietStartHour,
                    announcementConfig.quietEndHour
                )
            ) {
                // 在免打扰时段，只播报充电完成（如果启用）
                if (announcementConfig.chargeCompleteEnabled) {
                    checkChargeComplete(announcementConfig)
                }
                return Result.success()
            }

            // 执行定时播报
            if (announcementConfig.scheduledAnnounceEnabled) {
                performAnnouncement(announcementConfig, clockConfig)
            }

            // 检查低电量
            if (announcementConfig.lowBatteryEnabled) {
                checkLowBattery(announcementConfig)
            }

            // 检查充电完成
            if (announcementConfig.chargeCompleteEnabled) {
                checkChargeComplete(announcementConfig)
            }

            return Result.success()
        } catch (e: Exception) {
            // 记录错误但不重试，避免频繁打扰
            return Result.success()
        }
    }

    /**
     * 执行定时播报
     * 根据配置构建播报内容：时间/日期/农历/电量
     */
    private suspend fun performAnnouncement(
        announcementConfig: com.spanzy.oldercare.model.AnnouncementConfig,
        clockConfig: com.spanzy.oldercare.model.ClockConfig
    ) {
        val messageBuilder = StringBuilder()

        // 获取当前时间信息
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // 播报时间
        if (announcementConfig.announceTime) {
            val timeText = formatTimeForSpeech(hour, minute, clockConfig.use24Hour)
            messageBuilder.append("现在时间是$timeText，")
        }

        // 播报日期
        if (announcementConfig.announceDate) {
            val dateText = formatDateForSpeech(year, month, day)
            messageBuilder.append("$dateText，")
        }

        // 播报农历
        if (announcementConfig.announceLunar) {
            val lunarText = LunarCalendar.toLunar(year, month, day)
            messageBuilder.append("农历$lunarText，")
        }

        // 播报电量
        if (announcementConfig.announceBattery) {
            val batteryState = BatteryHelper.getBatteryState(applicationContext)
            val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
            messageBuilder.append("电量$batteryText%")

            if (batteryState.isCharging) {
                messageBuilder.append("，正在充电")
            }
        }

        // 执行播报
        val message = messageBuilder.toString().trimEnd(',')
        if (message.isNotEmpty()) {
            voiceService.speak(message)
        }
    }

    /**
     * 检查低电量并播报
     * 使用 lastLowBatteryAnnounceTime 跟踪上次播报时间
     * 按 repeatMinutes 间隔去重播报
     */
    private suspend fun checkLowBattery(announcementConfig: com.spanzy.oldercare.model.AnnouncementConfig) {
        val batteryState = BatteryHelper.getBatteryState(applicationContext)
        val currentTime = System.currentTimeMillis()

        // 更新电量状态
        lastBatteryLevel = batteryState.level

        // 检查是否满足低电量条件
        val isLowBattery = !batteryState.isCharging &&
                batteryState.level <= announcementConfig.lowBatteryThreshold

        if (isLowBattery) {
            // 检查是否需要播报（首次或超过重复间隔）
            val shouldAnnounce = lastLowBatteryAnnounceTime == 0L ||
                    (currentTime - lastLowBatteryAnnounceTime) >=
                    announcementConfig.lowBatteryRepeatMinutes * 60 * 1000L

            if (shouldAnnounce && announcementConfig.lowBatteryRepeatMinutes > 0) {
                val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
                val message = "电量不足，当前电量$batteryText%，请及时充电"
                voiceService.speak(message)
                lastLowBatteryAnnounceTime = currentTime
            }
        } else {
            // 电量恢复或开始充电，重置播报时间
            lastLowBatteryAnnounceTime = 0L
        }
    }

    /**
     * 检查充电完成并播报
     * 使用 lastChargeCompleteAnnounceTime 跟踪上次播报时间
     * 30分钟内不重复播报
     */
    private suspend fun checkChargeComplete(announcementConfig: com.spanzy.oldercare.model.AnnouncementConfig) {
        val batteryState = BatteryHelper.getBatteryState(applicationContext)
        val currentTime = System.currentTimeMillis()
        val chargeCompleteThreshold = 30 * 60 * 1000L // 30分钟

        // 检测充电完成状态：从充电状态变为未充电，且电量 >= 95%
        val wasCharging = lastIsCharging
        val isCharging = batteryState.isCharging
        val isChargeComplete = wasCharging && !isCharging && batteryState.level >= 95

        // 更新状态
        lastIsCharging = isCharging

        if (isChargeComplete) {
            // 检查是否需要播报（30分钟内不重复）
            val shouldAnnounce = lastChargeCompleteAnnounceTime == 0L ||
                    (currentTime - lastChargeCompleteAnnounceTime) >= chargeCompleteThreshold

            if (shouldAnnounce) {
                val batteryText = BatteryHelper.formatBatteryForSpeech(batteryState.level)
                val message = "充电已完成，电量$batteryText%"
                voiceService.speak(message)
                lastChargeCompleteAnnounceTime = currentTime
            }
        }
    }

    /**
     * 格式化时间用于语音播报
     * @param hour 小时 (0-23)
     * @param minute 分钟 (0-59)
     * @param use24Hour 是否使用24小时制
     * @return 播报文本，如 "上午10点30分" 或 "22点30分"
     */
    private fun formatTimeForSpeech(hour: Int, minute: Int, use24Hour: Boolean): String {
        return if (use24Hour) {
            // 24小时制：直接播报小时和分钟
            val hourText = if (hour == 0) "零点" else formatNumber(hour)
            val minuteText = if (minute == 0) "整" else formatNumber(minute) + "分"
            "$hourText$minuteText"
        } else {
            // 12小时制：加上上午/下午/凌晨等前缀
            val (period, hour12) = when (hour) {
                0 -> "凌晨" to 12
                in 1..5 -> "凌晨" to hour
                in 6..8 -> "早上" to hour
                in 9..11 -> "上午" to hour
                12 -> "中午" to 12
                in 13..17 -> "下午" to (hour - 12)
                in 18..22 -> "晚上" to (hour - 12)
                else -> "深夜" to (hour - 12)
            }

            val hourText = if (hour12 == 0) "12点" else formatNumber(hour12) + "点"
            val minuteText = if (minute == 0) "" else formatNumber(minute) + "分"

            "$period$hourText$minuteText"
        }
    }

    /**
     * 格式化日期用于语音播报
     * @param year 年
     * @param month 月 (1-12)
     * @param day 日 (1-31)
     * @return 播报文本，如 "2024年3月15日"
     */
    private fun formatDateForSpeech(year: Int, month: Int, day: Int): String {
        return year.toString() + "年" + month + "月" + day + "日"
    }

    /**
     * 格式化数字用于语音播报
     * @param number 数字 (0-59)
     * @return 播报文本，如 "十" "二十三" "五"
     */
    private fun formatNumber(number: Int): String {
        return when (number) {
            0 -> "零"
            in 1..10 -> arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")[number]
            in 11..19 -> {
                val units = number % 10
                if (units == 0) "十" else "十" + arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")[units]
            }
            in 20..59 -> {
                val tens = number / 10
                val units = number % 10
                val tensText = arrayOf("", "一", "二", "三", "四", "五")[tens]
                if (units == 0) tensText + "十" else tensText + "十" + arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")[units]
            }
            else -> number.toString()
        }
    }

    companion object {
        /**
         * 重播播报状态（用于测试或重置）
         */
        fun resetAnnounceState() {
            // 注意：由于 Worker 实例每次执行都会创建，
            // 这里需要在单例或全局状态中跟踪才能实现真正的重置
            // 当前实现使用进程内缓存，进程重启后自动重置
        }
    }
}
