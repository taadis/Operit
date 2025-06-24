package com.ai.assistance.operit.api.voice

import android.content.Context

/** 语音服务工厂，用于创建不同类型的语音服务实例 */
object VoiceServiceFactory {
    /** 语音服务类型枚举 */
    enum class VoiceServiceType {
        /** 基于Android系统TTS的简单语音实现 */
        SIMPLE_TTS,
    }

    /**
     * 创建语音服务实例
     *
     * @param context 应用上下文
     * @param type 语音服务类型
     * @return 对应类型的VoiceService实例
     */
    fun createVoiceService(
            context: Context,
            type: VoiceServiceType = VoiceServiceType.SIMPLE_TTS
    ): VoiceService {
        return when (type) {
            VoiceServiceType.SIMPLE_TTS -> SimpleVoiceProvider(context)
        }
    }

    // 单例实例缓存
    private var instance: VoiceService? = null

    /**
     * 获取语音服务单例实例
     *
     * @param context 应用上下文
     * @return VoiceService实例
     */
    fun getInstance(context: Context): VoiceService {
        if (instance == null) {
            instance = createVoiceService(context)
        }
        return instance!!
    }
}
