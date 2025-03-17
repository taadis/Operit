# UI 代码重组指南

## 重组目标

本次重组旨在按照功能模块和组件复用性将 UI 代码划分为更清晰的结构，主要目标是：

1. 增强代码的模块化程度
2. 提高组件复用性
3. 减少文件间依赖
4. 使项目结构更加直观和有条理

## 新的目录结构

```
ui/
 ├── common/               # 通用组件目录
 │    ├── animations/      # 动画相关组件
 │    ├── inputs/          # 输入控件
 │    └── displays/        # 显示组件
 │
 ├── features/             # 功能模块目录
 │    ├── chat/            # 聊天功能模块
 │    │    ├── components/ # 聊天特有组件
 │    │    └── screens/    # 聊天屏幕
 │    │
 │    ├── settings/        # 设置功能模块
 │    │    ├── components/ # 设置特有组件
 │    │    └── screens/    # 设置屏幕
 │    │
 │    └── demo/            # 演示功能模块
 │         ├── components/ # 演示特有组件
 │         └── screens/    # 演示屏幕
 │
 └── theme/                # 主题相关代码
```

## 如何使用新结构

### 导入组件

在新的结构中，导入组件时需要注意包路径的变化：

1. 通用组件的导入:
   ```kotlin
   // 动画组件
   import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility
   
   // 显示组件
   import com.ai.assistance.operit.ui.common.displays.CodeBlockComposable
   
   // 其他通用组件
   import com.ai.assistance.operit.ui.common.SimpleLinearProgressIndicator
   ```

2. 功能特定组件的导入:
   ```kotlin
   // 聊天组件
   import com.ai.assistance.operit.ui.features.chat.components.ChatHeader
   
   // 聊天屏幕
   import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
   ```

### 添加新组件

1. **通用组件**: 如果您的组件可以被多个功能使用，请将其添加到 `common` 目录下的适当子目录中。

2. **功能特定组件**: 如果组件仅用于特定功能，请将其添加到相应功能模块的 `components` 目录中。

3. **屏幕组件**: 完整的页面组件应放在相应功能模块的 `screens` 目录中。

## 迁移状态

目前迁移工作正在进行中，有关详细的迁移计划和状态，请参阅 `MIGRATION_PLAN.md` 文件。

## 注意事项

1. 移动文件后，需要更新文件的包声明和导入语句。
2. 由于包路径的变化，可能会导致一些编译错误，需要检查并修复这些问题。
3. 在导入时，应优先考虑使用通用组件，以提高代码复用性。

## 最佳实践

1. 在开发新功能时，请遵循新的目录结构。
2. 评估组件的复用性：如果一个组件可能被多个功能使用，考虑将其放在 `common` 目录中。
3. 保持组件的内聚性：相关的组件应放在一起。
4. 减少组件间的耦合：组件应尽可能独立，减少对其他组件的依赖。 