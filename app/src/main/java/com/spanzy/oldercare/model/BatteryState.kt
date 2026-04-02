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
