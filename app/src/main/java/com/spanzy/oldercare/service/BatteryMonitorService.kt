package com.spanzy.oldercare.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.spanzy.oldercare.model.BatteryState
import com.spanzy.oldercare.util.BatteryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 电池状态监听服务
 * 前台服务，实时监听电池状态变化
 */
class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _batteryState = MutableStateFlow(BatteryState(0, false, false))
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (true) {
                val state = BatteryHelper.getBatteryState(applicationContext)
                _batteryState.value = state
                delay(30000) // 每30秒检查一次
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
