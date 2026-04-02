package com.spanzy.oldercare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spanzy.oldercare.model.AnnouncementConfig
import com.spanzy.oldercare.model.ClockConfig
import com.spanzy.oldercare.model.ThemeConfig
import com.spanzy.oldercare.widget.BaseClockWidgetReceiver
import com.spanzy.oldercare.widget.BaseImageWidgetReceiver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore 设置存储仓库
 * 使用单一 Preferences DataStore 实例，键前缀分组管理
 */

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {

        /** 通知所有尺寸小组件刷新 - 直接调用 RemoteViews 更新 */
        suspend fun notifyWidgetUpdate(context: Context) {
            try {
                // 等待 DataStore 写入完成
                kotlinx.coroutines.delay(50)

                // 刷新时钟小组件
                BaseClockWidgetReceiver.refreshAll(context)

                // 刷新图片小组件
                BaseImageWidgetReceiver.refreshAll(context)

                android.util.Log.d("SettingsRepository", "All widgets refreshed")
            } catch (e: Exception) {
                android.util.Log.w("SettingsRepository", "Widget update failed", e)
            }
        }

        // ========== ClockConfig 键 ==========
        private val CLOCK_SHOW_TIME = booleanPreferencesKey("clock_show_time")
        private val CLOCK_SHOW_DATE = booleanPreferencesKey("clock_show_date")
        private val CLOCK_SHOW_LUNAR = booleanPreferencesKey("clock_show_lunar")
        private val CLOCK_SHOW_WEEKDAY = booleanPreferencesKey("clock_show_weekday")
        private val CLOCK_FONT_SIZE_LEVEL = intPreferencesKey("clock_font_size_level")
        private val CLOCK_USE_24_HOUR = booleanPreferencesKey("clock_use_24_hour")

        // ========== AnnouncementConfig 键 ==========
        private val ANNOUNCE_SCHEDULED_ENABLED = booleanPreferencesKey("announce_scheduled_enabled")
        private val ANNOUNCE_INTERVAL_MINUTES = intPreferencesKey("announce_interval_minutes")
        private val ANNOUNCE_QUIET_START_HOUR = intPreferencesKey("announce_quiet_start_hour")
        private val ANNOUNCE_QUIET_END_HOUR = intPreferencesKey("announce_quiet_end_hour")
        private val ANNOUNCE_TIME = booleanPreferencesKey("announce_time")
        private val ANNOUNCE_DATE = booleanPreferencesKey("announce_date")
        private val ANNOUNCE_LUNAR = booleanPreferencesKey("announce_lunar")
        private val ANNOUNCE_BATTERY = booleanPreferencesKey("announce_battery")
        private val ANNOUNCE_LOW_BATTERY_ENABLED = booleanPreferencesKey("announce_low_battery_enabled")
        private val ANNOUNCE_LOW_BATTERY_THRESHOLD = intPreferencesKey("announce_low_battery_threshold")
        private val ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES = intPreferencesKey("announce_low_battery_repeat_minutes")
        private val ANNOUNCE_CHARGE_COMPLETE_ENABLED = booleanPreferencesKey("announce_charge_complete_enabled")

        // ========== ThemeConfig 键 ==========
        private val THEME_DARK_MODE = booleanPreferencesKey("theme_dark_mode")
        private val THEME_WIDGET_UPDATE_INTERVAL = intPreferencesKey("theme_widget_update_interval")
        private val THEME_WIDGET_CLICK_ANNOUNCE = booleanPreferencesKey("theme_widget_click_announce")

        // ========== 图片显示小组件 ==========
        private val IMAGE_URI = stringPreferencesKey("image_uri")
    }

    // ========== ClockConfig ==========
    val clockConfig: Flow<ClockConfig> = context.settingsDataStore.data.map { preferences ->
        ClockConfig(
            showTime = preferences[CLOCK_SHOW_TIME] ?: true,
            showDate = preferences[CLOCK_SHOW_DATE] ?: true,
            showLunar = preferences[CLOCK_SHOW_LUNAR] ?: true,
            showWeekday = preferences[CLOCK_SHOW_WEEKDAY] ?: true,
            fontSizeLevel = preferences[CLOCK_FONT_SIZE_LEVEL] ?: 4,
            use24Hour = preferences[CLOCK_USE_24_HOUR] ?: false
        )
    }

    suspend fun updateClockConfig(transform: (ClockConfig) -> ClockConfig) {
        var newConfig: ClockConfig? = null
        context.settingsDataStore.edit { preferences ->
            val current = ClockConfig(
                showTime = preferences[CLOCK_SHOW_TIME] ?: true,
                showDate = preferences[CLOCK_SHOW_DATE] ?: true,
                showLunar = preferences[CLOCK_SHOW_LUNAR] ?: true,
                showWeekday = preferences[CLOCK_SHOW_WEEKDAY] ?: true,
                fontSizeLevel = preferences[CLOCK_FONT_SIZE_LEVEL] ?: 4,
                use24Hour = preferences[CLOCK_USE_24_HOUR] ?: false
            )
            newConfig = transform(current)
            preferences[CLOCK_SHOW_TIME] = newConfig!!.showTime
            preferences[CLOCK_SHOW_DATE] = newConfig!!.showDate
            preferences[CLOCK_SHOW_LUNAR] = newConfig!!.showLunar
            preferences[CLOCK_SHOW_WEEKDAY] = newConfig!!.showWeekday
            preferences[CLOCK_FONT_SIZE_LEVEL] = newConfig!!.fontSizeLevel
            preferences[CLOCK_USE_24_HOUR] = newConfig!!.use24Hour
        }
        notifyWidgetUpdate(context)
    }

    // ========== AnnouncementConfig ==========
    val announcementConfig: Flow<AnnouncementConfig> = context.settingsDataStore.data.map { preferences ->
        AnnouncementConfig(
            scheduledAnnounceEnabled = preferences[ANNOUNCE_SCHEDULED_ENABLED] ?: false,
            intervalMinutes = preferences[ANNOUNCE_INTERVAL_MINUTES] ?: 60,
            quietStartHour = preferences[ANNOUNCE_QUIET_START_HOUR] ?: 22,
            quietEndHour = preferences[ANNOUNCE_QUIET_END_HOUR] ?: 7,
            announceTime = preferences[ANNOUNCE_TIME] ?: true,
            announceDate = preferences[ANNOUNCE_DATE] ?: true,
            announceLunar = preferences[ANNOUNCE_LUNAR] ?: true,
            announceBattery = preferences[ANNOUNCE_BATTERY] ?: true,
            lowBatteryEnabled = preferences[ANNOUNCE_LOW_BATTERY_ENABLED] ?: true,
            lowBatteryThreshold = preferences[ANNOUNCE_LOW_BATTERY_THRESHOLD] ?: 20,
            lowBatteryRepeatMinutes = preferences[ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES] ?: 30,
            chargeCompleteEnabled = preferences[ANNOUNCE_CHARGE_COMPLETE_ENABLED] ?: true
        )
    }

    suspend fun updateAnnouncementConfig(transform: (AnnouncementConfig) -> AnnouncementConfig) {
        var newConfig: AnnouncementConfig? = null
        context.settingsDataStore.edit { preferences ->
            val current = AnnouncementConfig(
                scheduledAnnounceEnabled = preferences[ANNOUNCE_SCHEDULED_ENABLED] ?: false,
                intervalMinutes = preferences[ANNOUNCE_INTERVAL_MINUTES] ?: 60,
                quietStartHour = preferences[ANNOUNCE_QUIET_START_HOUR] ?: 22,
                quietEndHour = preferences[ANNOUNCE_QUIET_END_HOUR] ?: 7,
                announceTime = preferences[ANNOUNCE_TIME] ?: true,
                announceDate = preferences[ANNOUNCE_DATE] ?: true,
                announceLunar = preferences[ANNOUNCE_LUNAR] ?: true,
                announceBattery = preferences[ANNOUNCE_BATTERY] ?: true,
                lowBatteryEnabled = preferences[ANNOUNCE_LOW_BATTERY_ENABLED] ?: true,
                lowBatteryThreshold = preferences[ANNOUNCE_LOW_BATTERY_THRESHOLD] ?: 20,
                lowBatteryRepeatMinutes = preferences[ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES] ?: 30,
                chargeCompleteEnabled = preferences[ANNOUNCE_CHARGE_COMPLETE_ENABLED] ?: true
            )
            newConfig = transform(current)
            preferences[ANNOUNCE_SCHEDULED_ENABLED] = newConfig!!.scheduledAnnounceEnabled
            preferences[ANNOUNCE_INTERVAL_MINUTES] = newConfig!!.intervalMinutes
            preferences[ANNOUNCE_QUIET_START_HOUR] = newConfig!!.quietStartHour
            preferences[ANNOUNCE_QUIET_END_HOUR] = newConfig!!.quietEndHour
            preferences[ANNOUNCE_TIME] = newConfig!!.announceTime
            preferences[ANNOUNCE_DATE] = newConfig!!.announceDate
            preferences[ANNOUNCE_LUNAR] = newConfig!!.announceLunar
            preferences[ANNOUNCE_BATTERY] = newConfig!!.announceBattery
            preferences[ANNOUNCE_LOW_BATTERY_ENABLED] = newConfig!!.lowBatteryEnabled
            preferences[ANNOUNCE_LOW_BATTERY_THRESHOLD] = newConfig!!.lowBatteryThreshold
            preferences[ANNOUNCE_LOW_BATTERY_REPEAT_MINUTES] = newConfig!!.lowBatteryRepeatMinutes
            preferences[ANNOUNCE_CHARGE_COMPLETE_ENABLED] = newConfig!!.chargeCompleteEnabled
        }
        notifyWidgetUpdate(context)
    }

    // ========== ThemeConfig ==========
    val themeConfig: Flow<ThemeConfig> = context.settingsDataStore.data.map { preferences ->
        ThemeConfig(
            darkMode = preferences[THEME_DARK_MODE] ?: false,
            widgetUpdateInterval = preferences[THEME_WIDGET_UPDATE_INTERVAL] ?: 60,
            widgetClickAnnounce = preferences[THEME_WIDGET_CLICK_ANNOUNCE] ?: false
        )
    }

    suspend fun updateThemeConfig(transform: (ThemeConfig) -> ThemeConfig) {
        var newConfig: ThemeConfig? = null
        context.settingsDataStore.edit { preferences ->
            val current = ThemeConfig(
                darkMode = preferences[THEME_DARK_MODE] ?: false,
                widgetUpdateInterval = preferences[THEME_WIDGET_UPDATE_INTERVAL] ?: 60,
                widgetClickAnnounce = preferences[THEME_WIDGET_CLICK_ANNOUNCE] ?: false
            )
            newConfig = transform(current)
            preferences[THEME_DARK_MODE] = newConfig!!.darkMode
            preferences[THEME_WIDGET_UPDATE_INTERVAL] = newConfig!!.widgetUpdateInterval
            preferences[THEME_WIDGET_CLICK_ANNOUNCE] = newConfig!!.widgetClickAnnounce
        }
        notifyWidgetUpdate(context)
    }

    // ========== 图片显示小组件 ==========
    val imageUri: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[IMAGE_URI]
    }

    suspend fun updateImageUri(uri: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uri != null) {
                preferences[IMAGE_URI] = uri
            } else {
                preferences.remove(IMAGE_URI)
            }
        }
        kotlinx.coroutines.delay(50)
        BaseImageWidgetReceiver.refreshAll(context)
    }
}

/**
 * 获取图片 URI 的扩展函数
 */
fun Context.getImageUri() = settingsDataStore.data.map { preferences ->
    preferences[stringPreferencesKey("image_uri")]
}
