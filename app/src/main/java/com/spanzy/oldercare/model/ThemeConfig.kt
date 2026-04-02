package com.spanzy.oldercare.model

import kotlinx.serialization.Serializable

/**
 * 主题配置
 * @param darkMode 是否启用深色模式（覆盖系统设置）
 * @param widgetUpdateInterval 小组件更新间隔（秒）
 * @param widgetClickAnnounce 点击小组件时是否语音播报
 */
@Serializable
data class ThemeConfig(
    val darkMode: Boolean = false,
    val widgetUpdateInterval: Int = 60,  // 默认60秒
    val widgetClickAnnounce: Boolean = false  // 默认不播报
) {
    companion object {
        /** 可选的更新间隔（秒） */
        val UPDATE_INTERVALS = listOf(30, 60, 300)  // 30秒、1分钟、5分钟
    }
}
