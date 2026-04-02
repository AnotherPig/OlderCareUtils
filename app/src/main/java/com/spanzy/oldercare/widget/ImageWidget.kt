package com.spanzy.oldercare.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.spanzy.oldercare.MainActivity
import com.spanzy.oldercare.R
import com.spanzy.oldercare.data.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min

/**
 * 图片显示小组件 - 使用 RemoteViews 直接渲染
 * 图片更换时即时刷新
 */
abstract class BaseImageWidgetReceiver : AppWidgetProvider() {

    companion object {
        private const val TAG = "ImageWidget"
        private const val TARGET_SIZE = 512
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /**
         * 主动刷新所有图片小组件（供外部调用）
         */
        suspend fun refreshAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            listOf(
                ImageWidgetReceiver2x2::class.java,
                ImageWidgetReceiver3x3::class.java,
                ImageWidgetReceiver4x4::class.java
            ).forEach { receiverClass ->
                try {
                    val componentName = ComponentName(context, receiverClass)
                    val ids = appWidgetManager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        // 直接调用更新，不经过广播（更快）
                        updateWidgetInstances(context, appWidgetManager, ids)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "刷新 ${receiverClass.simpleName} 失败", e)
                }
            }
        }

        /**
         * 更新指定 ID 的图片小组件
         */
        internal suspend fun updateWidgetInstances(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            // 从 DataStore 读取配置
            val prefs = context.settingsDataStore.data.firstOrNull()

            val imageUri = try {
                prefs?.get(stringPreferencesKey("image_uri"))
            } catch (e: Exception) {
                Log.e(TAG, "读取图片 URI 失败", e)
                null
            }
            val widgetClickAnnounce = prefs?.get(booleanPreferencesKey("theme_widget_click_announce")) ?: false

            Log.d(TAG, "图片小组件更新: uri=$imageUri")

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.image_widget_layout)

                // 点击打开主应用，无图片时直接跳转图片选择器
                val clickIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (widgetClickAnnounce) putExtra("widget_click_announce", true)
                    if (imageUri == null) putExtra("widget_open_image_picker", true)
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    widgetId,
                    clickIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                if (imageUri != null) {
                    val bitmap = loadBitmapSafely(imageUri)
                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, bitmap)
                        views.setViewVisibility(R.id.widget_image, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_placeholder, View.GONE)
                    } else {
                        showPlaceholder(views, "加载失败")
                    }
                } else {
                    showPlaceholder(views, "点击设置\n选择图片")
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun showPlaceholder(views: RemoteViews, text: String) {
            views.setViewVisibility(R.id.widget_image, View.GONE)
            views.setViewVisibility(R.id.widget_placeholder, View.VISIBLE)
            views.setTextViewText(R.id.widget_placeholder, text)
        }

        /**
         * 安全加载 Bitmap
         */
        private fun loadBitmapSafely(imagePath: String): Bitmap? {
            var originalBitmap: Bitmap? = null
            try {
                val imageFile = File(imagePath)
                if (!imageFile.exists() || !imageFile.canRead()) {
                    Log.e(TAG, "图片文件不存在或不可读: $imagePath")
                    return null
                }

                // 获取图片尺寸
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                imageFile.inputStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                if (options.outWidth <= 0 || options.outHeight <= 0) return null

                // 计算采样率
                options.inSampleSize = calculateInSampleSize(
                    options.outWidth, options.outHeight, TARGET_SIZE
                )
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565

                originalBitmap = imageFile.inputStream().use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                } ?: return null

                val result = applyCenterCropWithPadding(originalBitmap!!)
                originalBitmap?.recycle()
                originalBitmap = null
                return result

            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "内存不足", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "图片加载失败", e)
                return null
            } finally {
                originalBitmap?.recycle()
            }
        }

        private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
            var inSampleSize = 1
            if (width > reqSize || height > reqSize) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while (halfWidth / inSampleSize >= reqSize && halfHeight / inSampleSize >= reqSize) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun applyCenterCropWithPadding(source: Bitmap): Bitmap {
            val originalWidth = source.width
            val originalHeight = source.height
            val cropSize = min(originalWidth, originalHeight)
            val x = (originalWidth - cropSize) / 2
            val y = (originalHeight - cropSize) / 2

            var croppedBitmap: Bitmap? = null
            var scaledBitmap: Bitmap? = null

            try {
                croppedBitmap = Bitmap.createBitmap(source, x, y, cropSize, cropSize)
                val paddingPercent = 0.92f
                val paddedSize = (cropSize * paddingPercent).toInt()
                scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap!!, paddedSize, paddedSize, true)

                val finalBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.RGB_565)
                val canvas = Canvas(finalBitmap)
                canvas.drawColor(Color.parseColor("#F5F0E8"))
                val offset = ((cropSize - paddedSize) / 2).toInt()
                canvas.drawBitmap(scaledBitmap!!, offset.toFloat(), offset.toFloat(), null)

                return finalBitmap
            } finally {
                croppedBitmap?.recycle()
                scaledBitmap?.recycle()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 使用 goAsync() 在后台线程加载图片，避免 ANR
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetInstances(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                Log.e(TAG, "onUpdate 失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}

// ========== 三种尺寸的图片小组件 ==========

class ImageWidgetReceiver2x2 : BaseImageWidgetReceiver()
class ImageWidgetReceiver3x3 : BaseImageWidgetReceiver()
class ImageWidgetReceiver4x4 : BaseImageWidgetReceiver()
