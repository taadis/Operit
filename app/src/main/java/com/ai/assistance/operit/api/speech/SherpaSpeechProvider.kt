package com.ai.assistance.operit.api.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.ncnn.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 基于sherpa-ncnn的本地语音识别实现 sherpa-ncnn是一个轻量级、高性能的语音识别引擎，比Whisper更适合移动端 参考:
 * https://github.com/k2-fsa/sherpa-ncnn
 */
@SuppressLint("MissingPermission")
class SherpaSpeechProvider(private val context: Context) : SpeechService {
    companion object {
        private const val TAG = "SherpaSpeechProvider"
    }

    private var recognizer: SherpaNcnn? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _recognitionState = MutableStateFlow(SpeechService.RecognitionState.UNINITIALIZED)
    override val currentState: SpeechService.RecognitionState
        get() = _recognitionState.value
    override val recognitionStateFlow: StateFlow<SpeechService.RecognitionState> =
            _recognitionState.asStateFlow()

    private val _recognitionResult = MutableStateFlow(SpeechService.RecognitionResult(""))
    override val recognitionResultFlow: StateFlow<SpeechService.RecognitionResult> =
            _recognitionResult.asStateFlow()

    private val _recognitionError = MutableStateFlow(SpeechService.RecognitionError(0, ""))
    override val recognitionErrorFlow: StateFlow<SpeechService.RecognitionError> =
            _recognitionError.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    override val isRecognizing: Boolean
        get() = currentState == SpeechService.RecognitionState.RECOGNIZING

    override suspend fun initialize(): Boolean {
        if (isInitialized.value) return true
        Log.d(TAG, "Initializing sherpa-ncnn...")
        return try {
            withContext(Dispatchers.IO) {
                createRecognizer()
                if (recognizer != null) {
                    Log.d(TAG, "sherpa-ncnn initialized successfully")
                    _isInitialized.value = true
                    _recognitionState.value = SpeechService.RecognitionState.IDLE
                    true
                } else {
                    Log.e(TAG, "Failed to create sherpa-ncnn recognizer")
                    _recognitionState.value = SpeechService.RecognitionState.ERROR
                    _recognitionError.value =
                            SpeechService.RecognitionError(-1, "Failed to initialize recognizer")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize sherpa-ncnn", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value =
                    SpeechService.RecognitionError(-1, e.message ?: "Unknown error")
            false
        }
    }

    @Throws(IOException::class)
    private fun copyAssetDirToCache(assetDir: String, cacheDir: File): File {
        val targetDir = File(cacheDir, assetDir.substringAfterLast('/'))
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model files already exist in cache: ${targetDir.absolutePath}")
            return targetDir
        }
        Log.d(TAG, "Copying model files from assets '$assetDir' to ${targetDir.absolutePath}")
        targetDir.mkdirs()

        val assetManager = context.assets
        val fileList = assetManager.list(assetDir)
        if (fileList.isNullOrEmpty()) {
            throw IOException("Asset directory '$assetDir' is empty or does not exist.")
        }

        fileList.forEach { fileName ->
            val assetPath = "$assetDir/$fileName"
            val targetFile = File(targetDir, fileName)
            assetManager.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return targetDir
    }

    private fun createRecognizer() {
        val localModelDir: File
        try {
            val modelDirName = "sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13"
            val assetModelDir = "models/$modelDirName"
            localModelDir = copyAssetDirToCache(assetModelDir, context.filesDir)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy model assets.", e)
            _recognitionState.value = SpeechService.RecognitionState.ERROR
            _recognitionError.value =
                    SpeechService.RecognitionError(-1, "Failed to prepare model files.")
            return
        }

        val featConfig = getFeatureExtractorConfig(sampleRate = 16000.0f, featureDim = 80)

        val modelConfig =
                ModelConfig(
                        encoderParam =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        encoderBin =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        decoderParam =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        decoderBin =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        joinerParam =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        joinerBin =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.bin").absolutePath,
                        tokens = File(localModelDir, "tokens.txt").absolutePath,
                        numThreads = 2,
                        useGPU = false
                )

        val decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4)

        val recognizerConfig =
                RecognizerConfig(
                        featConfig = featConfig,
                        modelConfig = modelConfig,
                        decoderConfig = decoderConfig,
                        enableEndpoint = true,
                        rule1MinTrailingSilence = 2.4f,
                        rule2MinTrailingSilence = 1.2f,
                        rule3MinUtteranceLength = 20.0f,
                        hotwordsFile = "",
                        hotwordsScore = 1.5f
                )

        recognizer =
                SherpaNcnn(
                        config = recognizerConfig,
                        assetManager = null // Force using newFromFile
                )
    }

    override suspend fun startRecognition(
            languageCode: String,
            continuousMode: Boolean,
            partialResults: Boolean
    ): Boolean {
        if (!isInitialized.value) {
            if (!initialize()) return false
        }
        if (isRecognizing) return false

        _recognitionState.value = SpeechService.RecognitionState.PREPARING
        recognizer?.reset(false) // 使用SherpaNcnn中的reset方法，参数为false不重新创建识别器

        val sampleRateInHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

        audioRecord =
                AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRateInHz,
                        channelConfig,
                        audioFormat,
                        minBufferSize * 2
                )
        audioRecord?.startRecording()
        _recognitionState.value = SpeechService.RecognitionState.RECOGNIZING
        Log.d(TAG, "Started recording")

        recordingJob =
                scope.launch {
                    val bufferSize = minBufferSize
                    val audioBuffer = ShortArray(bufferSize)
                    var lastText = ""

                    while (isActive &&
                            _recognitionState.value == SpeechService.RecognitionState.RECOGNIZING) {
                        val ret = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                        if (ret > 0) {
                            val samples = FloatArray(ret) { i -> audioBuffer[i] / 32768.0f }
                            recognizer?.let {
                                it.acceptSamples(samples)
                                while (it.isReady()) {
                                    it.decode()
                                }
                                val isEndpoint = it.isEndpoint()
                                val text = it.text

                                if (text.isNotBlank() && lastText != text) {
                                    lastText = text
                                    _recognitionResult.value =
                                            SpeechService.RecognitionResult(
                                                    text = text,
                                                    isFinal = isEndpoint
                                            )
                                }

                                if (isEndpoint) {
                                    it.reset(false)
                                    // If not in continuous mode, stop after first endpoint
                                    if (!continuousMode) {
                                        _recognitionState.value =
                                                SpeechService.RecognitionState.IDLE
                                        return@launch
                                    }
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _recognitionState.value = SpeechService.RecognitionState.IDLE
                    }
                    Log.d(TAG, "Stopped recording.")
                }
        return true
    }

    override suspend fun stopRecognition(): Boolean {
        if (recordingJob?.isActive == true &&
                        _recognitionState.value == SpeechService.RecognitionState.RECOGNIZING
        ) {
            Log.d(TAG, "Stopping recognition...")
            recordingJob?.cancel()
            _recognitionState.value =
                    SpeechService.RecognitionState.PROCESSING // Indicate processing then idle

            // Finalize recognition
            recognizer?.inputFinished()
            val text = recognizer?.text ?: ""
            _recognitionResult.value = SpeechService.RecognitionResult(text = text, isFinal = true)

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _recognitionState.value = SpeechService.RecognitionState.IDLE
            return true
        }
        return false
    }

    override suspend fun cancelRecognition() {
        if (recordingJob?.isActive == true) {
            recordingJob?.cancel()
        }
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _recognitionState.value = SpeechService.RecognitionState.IDLE
    }

    override fun shutdown() {
        scope.launch {
            cancelRecognition()
            withContext(Dispatchers.IO) {
                // 不直接调用finalize方法，而是让GC自然处理
                recognizer = null
            }
            _isInitialized.value = false
            _recognitionState.value = SpeechService.RecognitionState.UNINITIALIZED
        }
    }

    override suspend fun getSupportedLanguages(): List<String> =
            withContext(Dispatchers.IO) {
                return@withContext listOf("zh", "en")
            }

    // This method is for non-streaming recognition, which we are not using with sherpa-ncnn's
    // streaming API.
    // We can leave it as a no-op or throw an exception if called.
    override suspend fun recognize(audioData: FloatArray) {
        // Not implemented for streaming recognizer
        withContext(Dispatchers.Main) {
            _recognitionError.value =
                    SpeechService.RecognitionError(
                            -10,
                            "Batch recognition not supported in this provider"
                    )
            _recognitionState.value = SpeechService.RecognitionState.ERROR
        }
    }
}
