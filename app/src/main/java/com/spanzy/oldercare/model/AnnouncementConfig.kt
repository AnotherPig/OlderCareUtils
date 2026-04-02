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
