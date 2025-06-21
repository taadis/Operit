package com.ai.assistance.operit.api

import com.ai.assistance.operit.data.model.ApiProviderType

/** AI服务工厂，根据提供商类型创建相应的AIService实例 */
object AIServiceFactory {
    /**
     * 创建AI服务实例
     *
     * @param apiProviderType API提供商类型
     * @param apiEndpoint API端点
     * @param apiKey API密钥
     * @param modelName 模型名称
     * @return 对应的AIService实现
     */
    fun createService(
            apiProviderType: ApiProviderType,
            apiEndpoint: String,
            apiKey: String,
            modelName: String
    ): AIService {
        return when (apiProviderType) {
            // OpenAI格式，支持原生和兼容OpenAI API的服务
            ApiProviderType.OPENAI -> OpenAIProvider(apiEndpoint, apiKey, modelName)

            // Claude格式，支持Anthropic Claude系列
            ApiProviderType.ANTHROPIC -> ClaudeProvider(apiEndpoint, apiKey, modelName)

            // Gemini格式，支持Google Gemini系列
            ApiProviderType.GOOGLE -> GeminiProvider(apiEndpoint, apiKey, modelName)

            // 其他中文服务商，当前使用OpenAI Provider (大多数兼容OpenAI格式)
            // 后续可根据需要实现专用Provider
            ApiProviderType.BAIDU -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.ALIYUN -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.XUNFEI -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.ZHIPU -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.BAICHUAN -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.MOONSHOT -> OpenAIProvider(apiEndpoint, apiKey, modelName)

            // 默认使用OpenAI格式（大多数服务商兼容）
            ApiProviderType.DEEPSEEK -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.SILICONFLOW -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.OPENROUTER -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.INFINIAI -> OpenAIProvider(apiEndpoint, apiKey, modelName)
            ApiProviderType.OTHER -> OpenAIProvider(apiEndpoint, apiKey, modelName)
        }
    }
}
