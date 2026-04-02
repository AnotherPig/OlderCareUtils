package com.spanzy.oldercare

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.spanzy.oldercare.service.VoiceService
import com.spanzy.oldercare.util.WorkManagerScheduler
import com.spanzy.oldercare.widget.BaseClockWidgetReceiver
import com.spanzy.oldercare.widget.ClockTickReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 类
 * 负责全局初始化和小组件更新
 */
class OlderCareApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timeTickReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()

        // 预初始化 TTS（非阻塞）
        VoiceService.getInstance(this).initialize()

        // 启动电池检查任务（低电量提醒、充电完成提醒）
        WorkManagerScheduler.scheduleBatteryCheck(this)

        // 立即更新一次小组件
        applicationScope.launch {
            try {
                BaseClockWidgetReceiver.refreshAll(this@OlderCareApplication)
            } catch (_: Exception) {
                // 小组件可能未添加，忽略错误
            }
        }

        // 确保时钟每分钟更新闹钟已调度（小组件可能已添加但闹钟未设置）
        ClockTickReceiver.schedule(this)

        // 注册系统 TIME_TICK 广播，每分钟整点精确触发（进程存活时最可靠）
        registerTimeTickReceiver()
    }

    /**
     * 注册 ACTION_TIME_TICK 广播接收器
     * 系统在每分钟整点发送此广播，对齐系统时钟，无需任何权限
     */
    private fun registerTimeTickReceiver() {
        if (timeTickReceiver != null) return
        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                applicationScope.launch {
                    try {
                        BaseClockWidgetReceiver.refreshAll(this@OlderCareApplication)
                    } catch (_: Exception) { }
                }
            }
        }
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            timeTickReceiver?.let { unregisterReceiver(it) }
            timeTickReceiver = null
        } catch (_: Exception) { }
        try {
            VoiceService.getInstance(this).release()
        } catch (_: Exception) { }
    }
}
