# UI 组件迁移计划

## 概述

本文档跟踪 UI 组件从旧的平面结构迁移到新的特性导向架构的进度。

## 文件迁移进度

### 阶段一：通用组件迁移
- [x] 已完成

### 阶段二：Chat 功能组件迁移
- [x] MessageHistoryView.kt
- [x] ChatScreen.kt
- [x] ChatArea.kt
- [x] UserMessageComposable.kt
- [x] AiMessageComposable.kt
- [x] SystemMessageComposable.kt
- [x] ThinkingMessageComposable.kt
- [x] MessageContentParser.kt
- [x] CursorStyleChatMessage.kt
- [x] ToolExecutionBox.kt
- [x] SimpleLinearProgressIndicator.kt
- [x] ToolProgressBar.kt
- [x] ReferencesDisplay.kt
- [x] ChatInputSection.kt

### 阶段三：Settings 和 Demo 功能迁移
- [x] 已完成
  - [x] 创建了 SettingsViewModel 类，解决了 SettingsScreen 的引用问题
  - [x] 修复了 ShizukuDemoScreen 与 AdbCommandExecutor 的集成

### 阶段四：更新包声明和导入语句
- [x] 已完成
  - [x] 已更新 ShizukuDemoScreen.kt 文件中的 AdbCommandExecutor 用法，修复了 requestPermission 错误
  - [x] 已修复 AiMessageComposable.kt 中的 `ToolExecution` 引用问题，创建了适配器方法
  - [x] 已更新 ToolExecutionBox.kt 和 ToolProgressBar.kt 中的 SimpleLinearProgressIndicator 导入路径
  - [x] 已创建并修复 ChatInputSection.kt，更新了其对 InputProcessingState 的使用
  - [x] 已验证 ChatArea.kt 和其他组件的导入引用正确
  - [x] 已更新 Settings 相关引用，创建了专用的 SettingsViewModel
  - [x] 已更新 OperitApp.kt 中的导入路径，指向新的特性目录结构中的屏幕组件

### 阶段五：测试和验证
- [ ] 待进行
  - [x] 已创建测试计划文档：`app/src/main/java/com/ai/assistance/operit/ui/features/chat/TEST_MIGRATION.md`
  - [ ] 执行功能测试
  - [ ] 执行回归测试
  - [ ] 修复测试过程中发现的问题

### 阶段六：清理旧文件
- [x] 已完成
  - [x] 已删除 ui/screens 目录下的所有旧文件
    - [x] AIChatScreen.kt
    - [x] SettingsScreen.kt
    - [x] ShizukuDemoScreen.kt
  - [x] 已删除 ui/components 目录下的所有旧文件
    - [x] ChatArea.kt
    - [x] ChatHeader.kt
    - [x] ChatInputSection.kt
    - [x] ConfigurationScreen.kt
    - [x] SimpleLinearProgressIndicator.kt
    - [x] AiMessageComposable.kt
    - [x] ToolExecutionBox.kt
    - [x] ThinkingMessageComposable.kt
    - [x] UserMessageComposable.kt
    - [x] SystemMessageComposable.kt
    - [x] MessageContentParser.kt
    - [x] CursorStyleChatMessage.kt
    - [x] ReferencesDisplay.kt
    - [x] ToolProgressBar.kt
    - [x] ChatHistorySelector.kt
    - [x] CodeBlockComposable.kt
    - [x] TextWithCodeBlocksComposable.kt
    - [x] SimpleAnimation.kt

## 说明
- 所有文件已按照新的架构重组，并已解决引用和导入问题
- 已准备测试计划，接下来需要实际测试以确认迁移后的功能正常工作
- 所有旧文件已删除，只保留了迁移后的新文件结构 