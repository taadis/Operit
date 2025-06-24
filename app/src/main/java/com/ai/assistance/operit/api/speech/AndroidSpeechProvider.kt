package com.ai.assistance.operit.api.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 基于Android系统SpeechRecognizer的语音识别服务实现 */
class AndroidSpeechProvider(private val context: Context) : SpeechService {
    companion object {
        private const val TAG = "AndroidSpeechProvider"
    }

    // 系统语音识别器实例
    private var recognizer: SpeechRecognizer? = null

    // 识别参数
    private var recognizerIntent: Intent? = null
    private var continuousRecognition = false

    // 状态流
    private val _recognitionState = MutableStateFlow(SpeechService.RecognitionState.UNINITIALIZED)
    override val currentState: SpeechService.RecognitionState
        get() = _recognitionState.value
    override val recognitionStateFlow: StateFlow<SpeechService.RecognitionState> =
            _recognitionState.asStateFlow()

    // 结果流
    private val _recognitionResult = MutableStateFlow(SpeechService.RecognitionResult(""))
    override val recognitionResultFlow: StateFlow<SpeechService.RecognitionResult> =
            _recognitionResult.asStateFlow()

    // 错误流
    private val _recognitionError = MutableStateFlow(SpeechService.RecognitionError(0, ""))
    override val recognitionErrorFlow: StateFlow<SpeechService.RecognitionError> =
            _recognitionError.asStateFlow()

    // 初始化标记
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // 识别中标记
    override val isRecognizing: Boolean
        get() =
                currentState == SpeechService.RecognitionState.RECOGNIZING ||
                        currentState == SpeechService.RecognitionState.PROCESSING

    // 识别监听器
    private val recognitionListener =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                    _recognitionState.value = SpeechService.RecognitionState.RECOGNIZING
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // 音量变化，可用于UI显示音量大小
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // 接收语音数据缓冲区
                }

                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                    _recognitionState.value = SpeechService.RecognitionState.PROCESSING

                    if (continuousRecognition) {
                        // 在连续模式下，语音结束后自动重新开始识别
                        recognizer?.startListening(recognizerIntent)
                    }
                }

                override fun onError(error: Int) {
                    val errorMessage = getErrorMessage(error)
                    Log.e(TAG, "onError: $error - $errorMessage")

                    // 发送错误事件
                    _recognitionError.value = SpeechService.RecognitionError(error, errorMessage)

                    // 更新状态
                    _recognitionState.value = SpeechService.RecognitionState.ERROR

                    // 如果是网络错误或者其他临时错误，尝试重新启动（在连续模式下）
                    if (continuousRecognition &&
                                    (error == SpeechRecognizer.ERROR_NETWORK ||
                                            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                    ) {
                        recognizer?.startListening(recognizerIntent)
                    } else {
                        // 非临时性错误，或非连续模式，重置为空闲状态
                        _recognitionState.value = SpeechService.RecognitionState.IDLE
                    }
                }

                override fun onResults(results: Bundle?) {
                    Log.d(TAG, "onResults")
                    handleResults(results, true)

                    // 如果不是连续模式，那么识别完成后重置为空闲状态
                    if (!continuousRecognition) {
                        _recognitionState.value = SpeechService.RecognitionState.IDLE
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    Log.d(TAG, "onPartialResults")
                    handleResults(partialResults, false)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d(TAG, "onEvent: $eventType")
                }

                private fun handleResults(results: Bundle?, isFinal: Boolean) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches
                        ->
                        if (matches.isNotEmpty()) {
                            val text = matches[0]
                            Log.d(TAG, "Recognition result: $text (isFinal: $isFinal)")

                            // 发布识别结果
                            val confidence =
                                    results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                                            ?.firstOrNull()
                                            ?: 0f
                            _recognitionResult.value =
                                    SpeechService.RecognitionResult(text, isFinal, confidence)
                        }
                    }
                }

                private fun getErrorMessage(errorCode: Int): String {
                    return when (errorCode) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配的识别结果"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音输入"
                        else -> "未知错误: $errorCode"
                    }
                }
            }

    override suspend fun initialize(): Boolean =
            withContext(Dispatchers.Main) {
                if (isInitialized.value) return@withContext true

                return@withContext suspendCancellableCoroutine { continuation ->
                    try {
                        // 检查设备是否支持语音识别
                        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                            Log.e(TAG, "语音识别在此设备上不可用")
                            continuation.resume(false)
                            return@suspendCancellableCoroutine
                        }

                        // 创建语音识别器
                        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                        recognizer?.setRecognitionListener(recognitionListener)

                        // 创建默认的识别意图
                        recognizerIntent =
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE,
                                            Locale.getDefault().toString()
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                                    putExtra(
                                            RecognizerIntent.EXTRA_CALLING_PACKAGE,
                                            context.packageName
                                    )
                                }

                        _isInitialized.value = true
                        _recognitionState.value = SpeechService.RecognitionState.IDLE
                        continuation.resume(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "初始化语音识别器失败", e)
                        _recognitionState.value = SpeechService.RecognitionState.ERROR
                        continuation.resume(false)
                    }

                    // 如果协程被取消，释放资源
                    continuation.invokeOnCancellation { shutdown() }
                }
            }

    override suspend fun startRecognition(
            languageCode: String,
            continuousMode: Boolean,
            partialResults: Boolean
    ): Boolean =
            withContext(Dispatchers.Main) {
                // 检查初始化状态
                if (!isInitialized.value) {
                    val initResult = initialize()
                    if (!initResult) {
                        return@withContext false
                    }
                }

                // 检查当前状态
                if (isRecognizing) {
                    // 先停止当前的识别
                    stopRecognition()
                }

                return@withContext suspendCancellableCoroutine { continuation ->
                    try {
                        // 更新识别参数
                        continuousRecognition = continuousMode

                        // 更新识别意图
                        recognizerIntent =
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
                                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                                    putExtra(
                                            RecognizerIntent.EXTRA_CALLING_PACKAGE,
                                            context.packageName
                                    )

                                    // 设置不检测结束语音，在连续模式下防止识别器提前停止
                                    if (continuousMode) {
                                        putExtra(
                                                RecognizerIntent
                                                        .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                                                5000
                                        )
                                        putExtra(
                                                RecognizerIntent
                                                        .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                                                5000
                                        )
                                        putExtra(
                                                RecognizerIntent
                                                        .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                                                1000
                                        )
                                    }
                                }

                        _recognitionState.value = SpeechService.RecognitionState.PREPARING

                        // 开始识别
                        recognizer?.startListening(recognizerIntent)

                        continuation.resume(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "开始语音识别失败", e)
                        _recognitionState.value = SpeechService.RecognitionState.ERROR
                        continuation.resume(false)
                    }
                }
            }

    override suspend fun stopRecognition(): Boolean =
            withContext(Dispatchers.Main) {
                if (!isInitialized.value || !isRecognizing) {
                    return@withContext false
                }

                return@withContext suspendCancellableCoroutine { continuation ->
                    try {
                        // 停止识别
                        recognizer?.stopListening()
                        _recognitionState.value = SpeechService.RecognitionState.PROCESSING

                        continuation.resume(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "停止语音识别失败", e)
                        continuation.resume(false)
                    }
                }
            }

    override suspend fun cancelRecognition() =
            withContext(Dispatchers.Main) {
                if (isInitialized.value) {
                    recognizer?.cancel()
                    _recognitionState.value = SpeechService.RecognitionState.IDLE
                }
            }

    override fun shutdown() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null

            _isInitialized.value = false
            _recognitionState.value = SpeechService.RecognitionState.UNINITIALIZED
        } catch (e: Exception) {
            Log.e(TAG, "关闭语音识别器失败", e)
        }
    }

    override suspend fun getSupportedLanguages(): List<String> =
            withContext(Dispatchers.IO) {
                // Android 原生 SpeechRecognizer 不直接提供支持的语言列表
                // 这里返回最常用的几种语言代码
                return@withContext listOf(
                        "zh-CN", // 中文（简体）
                        "zh-TW", // 中文（繁体）
                        "en-US", // 英语（美国）
                        "en-GB", // 英语（英国）
                        "ja-JP", // 日语
                        "ko-KR", // 韩语
                        "fr-FR", // 法语
                        "de-DE", // 德语
                        "es-ES", // 西班牙语
                        "it-IT", // 意大利语
                        "ru-RU" // 俄语
                )
            }

    override suspend fun recognize(audioData: FloatArray) {
        // The native Android SpeechRecognizer works with a continuous stream
        // and doesn't support processing pre-recorded audio data directly.
        // This method is primarily for providers like Whisper.
        _recognitionError.value = SpeechService.RecognitionError(0, "Recognizing raw audio data is not supported by this provider.")
    }
}
