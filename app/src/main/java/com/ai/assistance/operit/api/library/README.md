# 问题库模块 (ProblemLibrary)

## 概述

问题库模块是Operit应用中负责存储、分析和检索问题记录的组件。它捕获用户查询、AI生成的解决方案，并进行分析提取关键信息，用于未来参考和用户偏好学习。

> **注意:** 已完全重构为单一简化的API。

## 特点

- **问题分析**: 分析用户查询和AI解决方案，提取摘要和关键点
- **用户偏好学习**: 从对话历史中提取用户偏好
- **问题记录**: 存储问题记录，包括查询、解决方案和使用的工具
- **内存优化**: 智能压缩长问题/解决方案记录

## 使用方法

### 初始化

在应用程序生命周期早期初始化模块:

```kotlin
// 在Application类或主Activity中
ProblemLibrary.initialize(applicationContext)
```

### 保存问题到库

保存问题到库中:

```kotlin
ProblemLibrary.saveProblemAsync(
    context,
    toolHandler,
    conversationHistory,
    solutionContent,
    aiService
)
```

### 分析查询

分析查询和响应内容:

```kotlin
ProblemLibrary.analyzeQueryAsync(
    context, 
    query, 
    response,
    aiService
) { results ->
    // 处理结果
    textView.text = results.problemSummary
}
```

## 与EnhancedAIService集成

EnhancedAIService使用问题库模块自动保存已完成的对话，提取有用信息供未来参考。

## 数据流

1. 用户与AI助手交互
2. 对话完成
3. 问题库接收对话数据
4. 分析生成摘要和偏好数据
5. 问题记录保存供将来参考
6. 根据分析更新用户偏好 