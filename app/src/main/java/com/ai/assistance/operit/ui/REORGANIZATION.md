# UI 重组方案

基于对当前项目结构的分析，建议将 UI 代码按照功能模块进行重组，从而提高代码的可维护性和可读性。

## 新的目录结构

```
ui/
 ├── common/           # 通用组件目录
 │    ├── animations/  # 动画相关组件
 │    ├── inputs/      # 输入控件
 │    └── displays/    # 显示组件
 │
 ├── features/         # 功能模块目录
 │    ├── chat/        # 聊天功能模块
 │    │    ├── components/ # 聊天特有组件
 │    │    └── screens/    # 聊天屏幕
 │    │
 │    ├── settings/    # 设置功能模块
 │    │    ├── components/ # 设置特有组件
 │    │    └── screens/    # 设置屏幕
 │    │
 │    └── demo/        # 演示功能模块
 │         ├── components/ # 演示特有组件
 │         └── screens/    # 演示屏幕
 │
 └── theme/            # 主题相关代码
```

## 需要移动的文件

### 通用组件（app/src/main/java/com/ai/assistance/operit/ui/common/）

以下组件应移动到通用组件目录：

- SimpleLinearProgressIndicator.kt
- SimpleAnimation.kt
- CodeBlockComposable.kt
- TextWithCodeBlocksComposable.kt
- MessageContentParser.kt

### 聊天功能（app/src/main/java/com/ai/assistance/operit/ui/features/chat/）

#### 组件（components/）

- ChatArea.kt
- ChatHeader.kt
- ChatInputSection.kt
- ChatHistorySelector.kt
- AiMessageComposable.kt
- UserMessageComposable.kt
- SystemMessageComposable.kt
- ThinkingMessageComposable.kt
- CursorStyleChatMessage.kt
- ToolExecutionBox.kt
- ToolProgressBar.kt
- ReferencesDisplay.kt

#### 屏幕（screens/）

- AIChatScreen.kt (已移动)
- ConfigurationScreen.kt

### 设置功能（app/src/main/java/com/ai/assistance/operit/ui/features/settings/）

#### 屏幕（screens/）

- SettingsScreen.kt

### 演示功能（app/src/main/java/com/ai/assistance/operit/ui/features/demo/）

#### 屏幕（screens/）

- ShizukuDemoScreen.kt

## 后续步骤

1. 移动所有文件到新的位置
2. 更新所有文件中的包声明（package 语句）
3. 更新导入语句（import 语句）
4. 更新任何引用这些组件的其他文件

## 注意事项

- 移动文件时需要注意更新其依赖关系
- 应保持功能组件的内聚性，将相关代码放在一起
- 通用组件应当与特定功能解耦，使它们可以在多个功能之间共享

重组完成后，代码库将更加模块化，便于维护和扩展。 