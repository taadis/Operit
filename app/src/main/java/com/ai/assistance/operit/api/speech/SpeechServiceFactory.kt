package com.ai.assistance.operit.api.speech

import android.content.Context

/** 语音识别服务工厂类 用于创建和管理不同类型的语音识别服务 */
object SpeechServiceFactory {
    /** 语音识别服务类型 */
    enum class SpeechServiceType {
        /** 基于Sherpa-ncnn的本地识别实现 */
        SHERPA_NCNN
    }

    /**
     * 创建语音识别服务实例
     *
     * @param context 应用上下文
     * @param type 语音识别服务类型
     * @return 对应类型的语音识别服务实例
     */
    fun createSpeechService(
            context: Context,
            type: SpeechServiceType = SpeechServiceType.SHERPA_NCNN
    ): SpeechService {
        return when (type) {
            SpeechServiceType.SHERPA_NCNN -> SherpaSpeechProvider(context)
        }
    }

    // 单例实例缓存
    private var instance: SpeechService? = null
    private var currentType: SpeechServiceType = SpeechServiceType.SHERPA_NCNN

    /**
     * 获取语音识别服务单例实例
     *
     * @param context 应用上下文
     * @param type 语音识别服务类型
     * @return 语音识别服务实例
     */
    fun getInstance(
            context: Context,
            type: SpeechServiceType = SpeechServiceType.SHERPA_NCNN
    ): SpeechService {
        val needNewInstance = instance == null || type != currentType
        
        if (needNewInstance) {
            instance?.shutdown()
            instance = createSpeechService(context, type)
            currentType = type
        }
        return instance!!
    }

    /** 重置单例实例 在需要更改语音识别服务类型或释放资源时调用 */
    fun resetInstance() {
        instance?.shutdown()
        instance = null
    }
}
