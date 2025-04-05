package com.ai.assistance.operit.core.model

data class UserPreferences(
    val preferences: String = "",  // 用户偏好描述，限制100字
    val gender: String = "",       // 性别
    val occupation: String = "",   // 从业方向
    val age: Int = 0,             // 年龄
    val isInitialized: Boolean = false  // 是否已初始化
) 