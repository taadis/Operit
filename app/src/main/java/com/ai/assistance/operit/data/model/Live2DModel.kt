package com.ai.assistance.operit.data.model

/** Live2D模型配置数据类 */
data class Live2DModel(
        val id: String, // 模型唯一标识
        val name: String, // 模型名称
        val folderPath: String, // 模型所在文件夹路径
        val jsonFileName: String, // 模型配置文件名
        val isBuiltIn: Boolean, // 是否是内置模型
        val expressions: List<String> = listOf(), // 表情列表
        val thumbnailPath: String? = null // 缩略图路径
)

/** Live2D模型配置，包含所有可调整的参数 */
data class Live2DConfig(
        val modelId: String, // 当前选中的模型ID
        val scale: Float = 1.0f, // 缩放比例
        val translateX: Float = 0.0f, // X轴偏移
        val translateY: Float = 0.0f, // Y轴偏移
        val mouthForm: Float = 0.0f, // 嘴型
        val mouthOpenY: Float = 0.0f, // 嘴部开合
        val autoBlinkEnabled: Boolean = true, // 是否自动眨眼
        val renderBack: Boolean = false // 是否渲染背景
)
