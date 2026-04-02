package com.spanzy.oldercare.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.spanzy.oldercare.worker.ScheduleWorker
import java.util.concurrent.TimeUnit

/**
 * WorkManager 调度工具
 */
object WorkManagerScheduler {

    private const val SCHEDULE_WORK_NAME = "schedule_announcement_work"
    private const val BATTERY_CHECK_WORK_NAME = "battery_check_work"

    /**
     * 初始化定时播报任务
     * @param intervalMinutes 间隔分钟数 (15, 30, 60)
     */
    fun scheduleAnnouncement(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)

        // WorkManager 最小间隔为 15 分钟
        val repeatInterval = maxOf(intervalMinutes, 15).toLong()

        val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
            repeatInterval, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            SCHEDULE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * 取消定时播报任务
     */
    fun cancelAnnouncement(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SCHEDULE_WORK_NAME)
    }

    /**
     * 初始化电池检查任务
     * 用于低电量提醒和充电完成提醒
     */
    fun scheduleBatteryCheck(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
            15, TimeUnit.MINUTES // 最小间隔
        ).build()

        workManager.enqueueUniquePeriodicWork(
            BATTERY_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
