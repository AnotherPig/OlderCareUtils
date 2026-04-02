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
