package com.ai.assistance.operit.api

import com.ai.assistance.operit.data.model.ModelParameter
import okhttp3.*

/** AI服务接口，定义与不同AI提供商进行交互的标准方法 */
interface AIService {
    /** 输入token计数 */
    val inputTokenCount: Int

    /** 输出token计数 */
    val outputTokenCount: Int

    /** 重置token计数器 */
    fun resetTokenCounts()

    /** 取消当前流式传输 */
    fun cancelStreaming()

    /**
     * 发送消息到AI服务
     *
     * @param message 用户消息内容
     * @param onPartialResponse 接收部分响应的回调函数(content: String, thinking: String?)
     * @param chatHistory 聊天历史记录，(角色, 内容)对的列表
     * @param onComplete 完成响应的回调函数
     * @param onConnectionStatus 连接状态变化的回调函数
     * @param modelParameters 模型参数列表
     */
    suspend fun sendMessage(
            message: String,
            onPartialResponse: (content: String, thinking: String?) -> Unit,
            chatHistory: List<Pair<String, String>> = emptyList(),
            onComplete: () -> Unit = {},
            onConnectionStatus: ((status: String) -> Unit)? = null,
            modelParameters: List<ModelParameter<*>> = emptyList()
    )
}
