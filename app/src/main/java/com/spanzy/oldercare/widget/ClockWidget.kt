package com.spanzy.oldercare.widget

import android.app.AlarmManager
import android.os.Build
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.spanzy.oldercare.MainActivity
import com.spanzy.oldercare.R
import com.spanzy.oldercare.data.settingsDataStore
import com.spanzy.oldercare.util.LunarCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 时钟小组件 - 使用 RemoteViews 直接渲染
 * 配置变更时即时刷新
 */
abstract class BaseClockWidgetReceiver(private val widgetSize: String) : AppWidgetProvider() {

    companion object {
        private const val TAG = "ClockWidget"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val CLOCK_SHOW_TIME = booleanPreferencesKey("clock_show_time")
        private val CLOCK_SHOW_DATE = booleanPreferencesKey("clock_show_date")
        private val CLOCK_SHOW_LUNAR = booleanPreferencesKey("clock_show_lunar")
        private val CLOCK_SHOW_WEEKDAY = booleanPreferencesKey("clock_show_weekday")
        private val CLOCK_USE_24HOUR = booleanPreferencesKey("clock_use_24_hour")
        private val CLOCK_FONT_SIZE_LEVEL = intPreferencesKey("clock_font_size_level")
        private val THEME_DARK_MODE = booleanPreferencesKey("theme_dark_mode")
        private val THEME_WIDGET_CLICK_ANNOUNCE = booleanPreferencesKey("theme_widget_click_announce")

        /**
         * 主动刷新所有时钟小组件
         */
        suspend fun refreshAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            listOf(
                ClockWidgetReceiver4x1::class.java,
                ClockWidgetReceiver4x2::class.java,
                ClockWidgetReceiver4x3::class.java,
                ClockWidgetReceiver4x4::class.java
            ).forEach { receiverClass ->
                try {
                    val componentName = ComponentName(context, receiverClass)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        // 获取实例的 widgetSize
                        val size = when (receiverClass) {
                            ClockWidgetReceiver4x1::class.java -> "4x1"
                            ClockWidgetReceiver4x2::class.java -> "4x2"
                            ClockWidgetReceiver4x3::class.java -> "4x3"
                            else -> "4x4"
                        }
                        updateWidgetInstances(context, appWidgetManager, ids, size)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "刷新 ${receiverClass.simpleName} 失败", e)
                }
            }
        }

        internal suspend fun updateWidgetInstances(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            widgetSize: String
        ) {
            val prefs = context.settingsDataStore.data.firstOrNull()

            val showTime = prefs?.get(CLOCK_SHOW_TIME) ?: true
            val showDate = prefs?.get(CLOCK_SHOW_DATE) ?: true
            val showLunar = prefs?.get(CLOCK_SHOW_LUNAR) ?: true
            val showWeekday = prefs?.get(CLOCK_SHOW_WEEKDAY) ?: true
            val use24Hour = prefs?.get(CLOCK_USE_24HOUR) ?: true
            val fontSizeLevel = prefs?.get(CLOCK_FONT_SIZE_LEVEL) ?: 4
            val darkMode = prefs?.get(THEME_DARK_MODE) ?: false
            val widgetClickAnnounce = prefs?.get(THEME_WIDGET_CLICK_ANNOUNCE) ?: false

            Log.d(TAG, "小组件[$widgetSize]配置: dark=$darkMode, fontSize=$fontSizeLevel")

            // 时间数据
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val weekday = calendar.get(Calendar.DAY_OF_WEEK)
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")

            val (displayHour, period) = if (use24Hour) {
                Pair(hour, "")
            } else {
                val displayH = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                Pair(displayH, if (hour < 12) "上午" else "下午")
            }
            val timeText = String.format("%02d:%02d", displayHour, minute)
            val dateText = String.format("%d年%d月%d日", year, month, day)
            val weekdayText = "星期${weekdays[weekday - 1]}"
            val lunarText = LunarCalendar.toLunar(year, month, day)

            // 颜色
            val bgColor = if (darkMode) "#0A0A1A" else "#F5F0E8"
            val primaryColor = if (darkMode) "#E0E0FF" else "#1A1A1A"
            val secondaryColor = if (darkMode) "#A0A0CC" else "#555555"
            val tertiaryColor = if (darkMode) "#8080AA" else "#777777"

            // 字体大小 (sp)
            val (timeSp, dateSp, lunarSp, paddingDp) = when (widgetSize) {
                "4x1" -> {
                    val base = when (fontSizeLevel) {
                        1 -> 28f; 2 -> 32f; 3 -> 36f; else -> 40f
                    }
                    Tuple4(base, base * 0.45f, base * 0.35f, 6f)
                }
                "4x2" -> {
                    val base = when (fontSizeLevel) {
                        1 -> 48f; 2 -> 56f; 3 -> 64f; else -> 72f
                    }
                    Tuple4(base, base * 0.38f, base * 0.30f, 12f)
                }
                "4x3" -> {
                    val base = when (fontSizeLevel) {
                        1 -> 56f; 2 -> 64f; 3 -> 76f; else -> 88f
                    }
                    Tuple4(base, base * 0.32f, base * 0.25f, 16f)
                }
                "4x4" -> {
                    val base = when (fontSizeLevel) {
                        1 -> 72f; 2 -> 84f; 3 -> 96f; else -> 108f
                    }
                    Tuple4(base, base * 0.26f, base * 0.20f, 20f)
                }
                else -> Tuple4(72f, 27f, 22f, 12f)
            }

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.clock_widget_layout)

                // 点击打开主应用
                val clickIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (widgetClickAnnounce) putExtra("widget_click_announce", true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                // 背景色
                views.setInt(R.id.widget_root, "setBackgroundColor", android.graphics.Color.parseColor(bgColor))

                // padding
                val paddingPx = (paddingDp * context.resources.displayMetrics.density).toInt()
                views.setViewPadding(R.id.widget_root, paddingPx, paddingPx, paddingPx, paddingPx)

                // 时间
                if (showTime) {
                    views.setTextViewText(R.id.widget_time, timeText)
                    views.setTextColor(R.id.widget_time, android.graphics.Color.parseColor(primaryColor))
                    views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, timeSp)
                    views.setViewVisibility(R.id.widget_time, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_time, View.GONE)
                }

                // AM/PM
                if (showTime && !use24Hour && period.isNotEmpty()) {
                    views.setTextViewText(R.id.widget_period, period)
                    views.setTextColor(R.id.widget_period, android.graphics.Color.parseColor(secondaryColor))
                    views.setTextViewTextSize(R.id.widget_period, TypedValue.COMPLEX_UNIT_SP, dateSp)
                    views.setViewVisibility(R.id.widget_period, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_period, View.GONE)
                }

                // 4x1 只显示时间
                if (widgetSize == "4x1") {
                    views.setViewVisibility(R.id.widget_date_row, View.GONE)
                    views.setViewVisibility(R.id.widget_lunar, View.GONE)
                } else {
                    // 日期 + 星期
                    if (showDate || showWeekday) {
                        views.setViewVisibility(R.id.widget_date_row, View.VISIBLE)
                        if (showDate) {
                            views.setTextViewText(R.id.widget_date, dateText)
                            views.setTextColor(R.id.widget_date, android.graphics.Color.parseColor(secondaryColor))
                            views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, dateSp)
                            views.setViewVisibility(R.id.widget_date, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_date, View.GONE)
                        }

                        views.setViewVisibility(
                            R.id.widget_date_space,
                            if (showDate && showWeekday) View.VISIBLE else View.GONE
                        )

                        if (showWeekday) {
                            views.setTextViewText(R.id.widget_weekday, weekdayText)
                            views.setTextColor(R.id.widget_weekday, android.graphics.Color.parseColor(secondaryColor))
                            views.setTextViewTextSize(R.id.widget_weekday, TypedValue.COMPLEX_UNIT_SP, dateSp)
                            views.setViewVisibility(R.id.widget_weekday, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_weekday, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_date_row, View.GONE)
                    }

                    // 农历
                    if (showLunar) {
                        views.setTextViewText(R.id.widget_lunar, "农历$lunarText")
                        views.setTextColor(R.id.widget_lunar, android.graphics.Color.parseColor(tertiaryColor))
                        views.setTextViewTextSize(R.id.widget_lunar, TypedValue.COMPLEX_UNIT_SP, lunarSp)
                        views.setViewVisibility(R.id.widget_lunar, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_lunar, View.GONE)
                    }
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetInstances(context, appWidgetManager, appWidgetIds, widgetSize)
            } catch (e: Exception) {
                Log.e(TAG, "onUpdate 失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ClockTickReceiver.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 检查是否所有尺寸的时钟小组件都已移除
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val hasActive = listOf(
            ClockWidgetReceiver4x1::class.java,
            ClockWidgetReceiver4x2::class.java,
            ClockWidgetReceiver4x3::class.java,
            ClockWidgetReceiver4x4::class.java
        ).any { receiverClass ->
            appWidgetManager.getAppWidgetIds(ComponentName(context, receiverClass)).isNotEmpty()
        }
        if (!hasActive) {
            ClockTickReceiver.cancel(context)
        }
    }
}

private data class Tuple4<T1, T2, T3, T4>(
    val first: T1, val second: T2, val third: T3, val fourth: T4
)

// ========== 4 种尺寸的时钟小组件 ==========

class ClockWidgetReceiver4x1 : BaseClockWidgetReceiver("4x1")
class ClockWidgetReceiver4x2 : BaseClockWidgetReceiver("4x2")
class ClockWidgetReceiver4x3 : BaseClockWidgetReceiver("4x3")
class ClockWidgetReceiver4x4 : BaseClockWidgetReceiver("4x4")

/**
 * 每分钟时钟更新广播接收器
 * 通过 AlarmManager 精确触发，不依赖 app 进程存活
 */
class ClockTickReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ClockTickReceiver"
        private const val ACTION_TICK = "com.spanzy.oldercare.ACTION_CLOCK_TICK"
        private const val REQUEST_CODE = 2001

        fun schedule(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ClockTickReceiver::class.java).apply {
                    action = ACTION_TICK
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 精确对齐下一个整分钟，加 50ms 确保已跨过分钟边界
                val now = System.currentTimeMillis()
                val nextMinute = (now / 60000 + 1) * 60000 + 50

                // Android 12+ 检查精确闹钟权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC, nextMinute, pendingIntent
                        )
                    } else {
                        // 无精确闹钟权限，使用非精确闹钟兜底
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC, nextMinute, pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC, nextMinute, pendingIntent
                    )
                }
                Log.d(TAG, "时钟闹钟已调度，${nextMinute - now}ms 后触发")
            } catch (e: Exception) {
                Log.e(TAG, "调度时钟闹钟失败", e)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ClockTickReceiver::class.java).apply {
                action = ACTION_TICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
            Log.d(TAG, "时钟闹钟已取消")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // 开机后重新调度闹钟并立即刷新
                schedule(context)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        BaseClockWidgetReceiver.refreshAll(context)
                    } catch (_: Exception) { }
                    finally { pendingResult.finish() }
                }
            }
            ACTION_TICK -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        BaseClockWidgetReceiver.refreshAll(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "时钟更新失败", e)
                    } finally {
                        // 调度下一次精确闹钟
                        schedule(context)
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
