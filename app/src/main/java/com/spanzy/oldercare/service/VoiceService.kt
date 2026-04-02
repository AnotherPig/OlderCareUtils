package com.spanzy.oldercare.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TTS 语音播报服务
 * 使用异步初始化避免阻塞主线程导致 ANR
 */
class VoiceService private constructor(private val appContext: Context) :
    TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceService"
        @Volatile
        private var instance: VoiceService? = null
        private var initContext: Context? = null

        fun getInstance(context: Context): VoiceService {
            return instance ?: synchronized(this) {
                instance ?: VoiceService(context.applicationContext).also {
                    instance = it
                    initContext = context // 保存原始 Context（可能是 Activity）
                }
            }
        }
    }

    @Volatile
    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // 播报状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 初始化状态标记
    @Volatile
    private var isInitializing = false
    private var initError: String? = null

    // 音频焦点管理
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    /**
     * 异步初始化 TTS
     * 不阻塞主线程，避免 ANR
     */
    fun initialize() {
        if (tts != null || isInitializing) {
            Log.d(TAG, "TTS 已初始化或正在初始化，跳过")
            return
        }

        Log.d(TAG, "开始异步初始化 TTS...")
        isInitializing = true

        Handler(Looper.getMainLooper()).post {
            try {
                // 优先使用 initContext（可能是 Activity），小米设备可能需要 Activity Context
                val ctxToUse = initContext ?: appContext
                Log.d(TAG, "使用 Context 初始化 TTS: $ctxToUse (Activity=${ctxToUse is android.app.Activity})")

                // 直接使用系统默认 TTS，不指定具体引擎
                val engine = TextToSpeech(ctxToUse, this@VoiceService)
                tts = engine
                Log.d(TAG, "TTS 对象创建并保存: $engine")
            } catch (e: Exception) {
                Log.e(TAG, "TTS 初始化异常: ${e.message}", e)
                initError = "初始化异常: ${e.message}"
                isInitializing = false
                _isReady.value = false
            }
        }
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "TTS onInit 回调: status=$status, tts=$tts")

        // 如果 tts 为 null（构造函数还未返回），延迟处理
        if (tts == null) {
            Log.w(TAG, "onInit 时 tts 为 null，延迟 100ms 后处理")
            Handler(Looper.getMainLooper()).postDelayed({
                handleInit(status)
            }, 100)
            return
        }

        handleInit(status)
    }

    private fun handleInit(status: Int) {
        Log.d(TAG, "处理 TTS 初始化: status=$status, tts=$tts")
        isInitializing = false

        // 小米特殊处理：无论 status 是什么，都尝试使用
        tts?.let { engine ->
            Log.d(TAG, "开始小米设备 TTS 验证...")
            try {
                // 先检查 defaultEngine
                val defaultEngine = engine.defaultEngine
                Log.d(TAG, "默认 TTS 引擎: $defaultEngine")

                // 如果引擎为 null，可能需要等待服务启动
                if (defaultEngine == null) {
                    Log.w(TAG, "TTS 引擎为 null，等待服务启动...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        retryInitialization()
                    }, 500)
                    return
                }

                val testResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                Log.d(TAG, "小米设备验证：语言设置结果=$testResult")

                // 无论返回值是什么，都尝试初始化语言设置
                initializeLanguageSettings(engine)
                return
            } catch (e: Exception) {
                Log.e(TAG, "小米设备验证失败，异常: ${e.message}", e)
            }
        }

        // 如果 tts 为 null，记录错误
        if (tts == null) {
            Log.e(TAG, "TTS 对象仍为 null，无法初始化")
            initError = "TTS 对象为 null"
            _isReady.value = false
        }
    }

    private fun retryInitialization() {
        Log.d(TAG, "重试 TTS 初始化...")
        tts?.let { engine ->
            try {
                val defaultEngine = engine.defaultEngine
                Log.d(TAG, "重试 - 默认 TTS 引擎: $defaultEngine")

                if (defaultEngine != null) {
                    val testResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    Log.d(TAG, "重试 - 语言设置结果: $testResult")
                    initializeLanguageSettings(engine)
                } else {
                    Log.e(TAG, "重试失败 - TTS 引擎仍为 null")
                    // 标记为就绪，让用户尝试
                    _isReady.value = true
                    initError = "TTS 服务可能未启动，请在设置中检查语音服务"
                }
            } catch (e: Exception) {
                Log.e(TAG, "重试初始化异常: ${e.message}", e)
            }
        }
    }

    private fun initializeLanguageSettings(engine: TextToSpeech) {
        // 设置语言
        val langResult = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        Log.d(TAG, "语言设置结果: $langResult")

        // 检查 TTS 是否真的可用（通过调用 defaultEngine）
        try {
            val defaultEngine = engine.defaultEngine
            Log.d(TAG, "默认 TTS 引擎: $defaultEngine")
        } catch (e: Exception) {
            Log.w(TAG, "获取 TTS 引擎信息失败: ${e.message}")
        }

        // 尝试播放静音来测试 TTS 是否真的绑定成功
        try {
            val testResult = engine.playSilentUtterance(100, TextToSpeech.QUEUE_FLUSH, "test_binding")
            Log.d(TAG, "静音测试结果: $testResult")

            // 如果测试成功，标记为就绪
            if (testResult == TextToSpeech.SUCCESS) {
                _isReady.value = true
                initError = null
                Log.d(TAG, "TTS 测试成功，标记为就绪")
            } else {
                Log.w(TAG, "TTS 测试失败，可能未正确绑定")
                _isReady.value = true  // 仍然尝试使用
                initError = "TTS 可能未正确绑定"
            }
        } catch (e: Exception) {
            Log.w(TAG, "静音测试异常: ${e.message}，仍然尝试使用 TTS")
            _isReady.value = true
        }

        // 设置监听器
        try {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "开始播报: $utteranceId")
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "播报完成: $utteranceId")
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "播报错误: $utteranceId")
                    _isSpeaking.value = false
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    Log.d(TAG, "播报停止: $utteranceId")
                    _isSpeaking.value = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "设置监听器失败", e)
        }

        Log.d(TAG, "TTS 初始化完成，isReady=${_isReady.value}")
    }

    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus(): Boolean {
        try {
            if (hasAudioFocus) return true

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                hasAudioFocus = false
                                stop()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> hasAudioFocus = true
                        }
                    }
                    .build()

                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        } catch (e: Exception) {
            Log.e(TAG, "请求音频焦点失败", e)
            return false
        }
    }

    /**
     * 播报文本
     */
    suspend fun speak(text: String, utteranceId: String = "voice_${System.currentTimeMillis()}") {
        // 触发初始化（如果尚未初始化）
        if (tts == null && !isInitializing) {
            Log.d(TAG, "TTS 未初始化，触发异步初始化")
            initialize()
        }

        // 等待就绪（非阻塞）
        var waited = 0
        while (!_isReady.value && !isInitializing && waited < 100) {
            kotlinx.coroutines.delay(50)
            waited++
        }

        // 如果正在初始化，再等待一会儿
        if (isInitializing) {
            waited = 0
            while (isInitializing && waited < 100) {
                kotlinx.coroutines.delay(50)
                waited++
            }
        }

        if (!_isReady.value) {
            Log.w(TAG, "播报失败: ${initError ?: "TTS 未就绪，isReady=$_isReady, isInitializing=$isInitializing"}")
            return
        }

        // 请求音频焦点并播报
        requestAudioFocus()

        tts?.let { engine ->
            try {
                engine.stop()
                engine.setSpeechRate(1.0f)
                engine.setPitch(1.0f)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        engine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "设置音频属性失败", e)
                    }
                }

                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                Log.d(TAG, "播报结果: $result, text: $text")

                // 如果播报失败（未绑定），尝试重新初始化
                if (result == TextToSpeech.ERROR) {
                    Log.w(TAG, "播报失败，尝试重新初始化 TTS...")
                    _isReady.value = false
                    tts?.shutdown()
                    tts = null
                    initialize()

                    // 等待重新初始化后重试
                    waited = 0
                    while (!_isReady.value && waited < 50) {
                        kotlinx.coroutines.delay(100)
                        waited++
                    }

                    if (_isReady.value && tts != null) {
                        Log.d(TAG, "重新初始化成功，重试播报...")
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "${utteranceId}_retry")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "播报异常", e)
            }
        }
    }

    /**
     * 停止播报
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * 释放资源
     */
    fun release() {
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        _isReady.value = false
    }
}
