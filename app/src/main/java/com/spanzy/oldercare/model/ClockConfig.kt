package com.spanzy.oldercare.model

import kotlinx.serialization.Serializable

/**
 * 时钟显示配置
 * @param showTime 是否显示时间
 * @param showDate 是否显示日期
 * @param showLunar 是否显示农历
 * @param showWeekday 是否显示星期
 * @param fontSizeLevel 字体大小档位 1=标准, 2=大, 3=较大, 4=超大
 * @param use24Hour 是否使用24小时制
 */
@Serializable
data class ClockConfig(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showLunar: Boolean = true,
    val showWeekday: Boolean = true,
    val fontSizeLevel: Int = 4,
    val use24Hour: Boolean = false
)
