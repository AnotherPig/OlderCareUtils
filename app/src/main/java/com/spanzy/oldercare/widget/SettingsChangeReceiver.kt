package com.spanzy.oldercare.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 设置变更广播接收器
 * 当设置发生变更时，强制刷新所有小组件
 */
class SettingsChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SettingsChangeReceiver"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.spanzy.oldercare.ACTION_SETTINGS_CHANGED") {
            Log.d(TAG, "Settings changed broadcast received, refreshing widgets")
            scope.launch {
                BaseClockWidgetReceiver.refreshAll(context)
                BaseImageWidgetReceiver.refreshAll(context)
            }
        }
    }
}
