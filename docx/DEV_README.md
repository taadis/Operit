# 开发者说明文档

本文档提供项目源代码结构概览以及每个Kotlin文件的功能摘要。

## 目录

1. [API模块](#api模块)
2. [核心模块](#核心模块)
3. [数据模块](#数据模块)
4. [UI模块](#ui模块)
5. [服务模块](#服务模块)
6. [工具模块](#工具模块)

## API模块

### `AIService.kt`
提供核心AI服务功能，处理与AI模型的交互和响应处理。

### `EnhancedAIService.kt`
扩展基础AIService，增强了上下文管理和额外的工具响应处理能力。

### API/Library

#### `api/library/ProblemLibrary.kt`
实现维护和访问常见问题及其解决方案库的功能。在这里被当做记忆库使用。

### API/Enhance

#### `api/enhance/ConversationMarkupManager.kt`
管理对话标记，提升视觉呈现和交互体验。

#### `api/enhance/ConversationRoundManager.kt`
管理用户与AI之间的对话轮次，用于上下文管理。

#### `api/enhance/InputProcessor.kt`
处理用户输入，处理用户反馈。

#### `api/enhance/PlanItemParser.kt`
解析和处理来自AI响应的计划项目。

#### `api/enhance/ReferenceManager.kt`
管理对话中对文档、代码或其他资源的引用。

#### `api/enhance/ToolExecutionManager.kt`
根据对话上下文管理AI工具的执行。

## 核心模块

### Core/Application

#### `core/application/OperitApplication.kt`
应用程序的主类，初始化应用组件并管理应用程序生命周期。

### Core/Config

#### `core/config/SystemPromptConfig.kt`
定义应用程序中使用的AI模型的系统提示和配置。

### Core/Tools

#### `core/tools/javascript/JsEngine.kt`
提供在应用程序中执行JavaScript代码的接口。

#### `core/tools/javascript/JsTools.kt`
实现在Android环境中使用JavaScript的工具。

#### `core/tools/defaultTool/UITools.kt`
提供UI操作和交互的工具。

## 数据模块

### Data/Model

#### `data/model/AiReference.kt`
定义应用程序中使用的AI引用的数据结构。

#### `data/model/AITool.kt`
定义AI工具及其功能的数据结构。

#### `data/model/ChatHistory.kt`
管理聊天历史记录的数据结构。

#### `data/model/ChatMessage.kt`
定义单个聊天消息的数据结构。

#### `data/model/InputProcessingState.kt`
管理应用程序中输入处理的状态。

#### `data/model/PlanItem.kt`
定义AI辅助规划中使用的计划项的数据结构。

#### `data/model/PreferenceProfile.kt`
定义用于自定义AI行为的用户偏好配置文件。

### Data/MCP

#### `data/mcp/MCPRepository.kt`
提供MCP插件服务器市场的查看。目前还没接入。

### Data/Preferences

#### `data/preferences/AgreementPreferences.kt`
管理用户协议许可是否经过授权。

#### `data/preferences/ApiPreferences.kt`
管理API配置偏好，如终端节点和身份验证。

#### `data/preferences/UserPreferencesManager.kt`
管理并持久化用户在应用程序会话之间的偏好设置。ai也可以修改用户偏好。

### Data/Repository

#### `data/repository/ChatHistoryManager.kt`
管理聊天历史数据的存储和检索。

#### `data/repository/UIHierarchyManager.kt`
管理用于无障碍功能和UI自动化的UI层次结构数据。

### Data/Updates

#### `data/updates/UpdateManager.kt`
管理应用程序更新，包括检查更新和处理更新过程。

### Data/DB

#### `data/db/AppDatabase.kt`
使用Room定义应用程序的主数据库结构。这是一个sql的数据库。

#### `data/db/ProblemDao.kt`
提供与数据库中问题相关数据交互的数据访问对象(DAO)接口。

#### `data/db/ProblemEntity.kt`
定义用于存储问题信息的数据库实体。

## UI模块

### UI/Main

#### `ui/main/MainActivity.kt`
应用程序的主活动，作为入口点并托管主要UI组件。

#### `ui/main/OperitApp.kt`
实现整个应用程序的主要Composable UI结构。

### UI/Theme

#### `ui/theme/Color.kt`
定义整个应用程序使用的颜色调色板。

#### `ui/theme/Theme.kt`
实现应用程序的主题系统，包括明暗模式支持。

### UI/Navigation

#### `ui/navigation/NavItem.kt`
定义应用程序导航系统中使用的导航项。

### UI/Permissions

#### `ui/permissions/PermissionRequestOverlay.kt`
实现向用户请求权限的覆盖UI。在工具执行的时候，会弹出这个来请求用户允许。当然如果自动批准了，就不会弹出。

#### `ui/permissions/ToolPermissionDialog.kt`
定义请求特定工具权限的对话框。

#### `ui/permissions/ToolPermissionSystem.kt`
管理工具权限系统，处理权限请求并跟踪已授予的权限。

### UI/Floating

#### `ui/floating/FloatingChatWindow.kt`
实现浮动聊天窗口UI，可在其他应用程序之上覆盖显示。

#### `ui/floating/FloatingWindowTheme.kt`
定义浮动窗口组件的主题。

### UI/Features

#### UI/Features/About

##### `ui/features/about/screens/AboutScreen.kt`
实现"关于"屏幕，显示应用程序信息、版本和开发者信息。

#### UI/Features/Agreement

##### `ui/features/agreement/screens/AgreementScreen.kt`
实现协议屏幕，供用户查看并接受服务条款和隐私政策。

#### UI/Features/Chat

##### `ui/features/chat/viewmodel/ChatViewModel.kt`
实现聊天功能的ViewModel，管理聊天数据和用户交互。

##### `ui/features/chat/components/ChatArea.kt`
实现聊天区域UI组件，用于显示和与聊天消息交互。

#### UI/Features/Demo

##### `ui/features/demo/screens/ShizukuDemoScreen.kt`
这个界面命名有点问题，它的实质功能是一个界面，负责引导用户开始储存什么的权限。。

#### UI/Features/Help

##### `ui/features/help/screens/HelpScreen.kt`
实现帮助屏幕，提供用户援助和文档。

#### UI/Features/MCP

##### `ui/features/mcp/screens/MCPScreen.kt`
MCP市场查看界面。

#### UI/Features/Packages

##### `ui/features/packages/screens/PackageManagerScreen.kt`
实现包管理器屏幕，用于管理应用程序包和依赖项。这个是用户可以增加js拓展包来拓展ai的工具。

#### UI/Features/Settings

##### `ui/features/settings/screens/SettingsScreen.kt`
实现主设置屏幕UI及功能。

##### `ui/features/settings/screens/UserPreferencesSettingsScreen.kt`
实现用户偏好设置屏幕，用于自定义AI助手行为。主要就是那个用户偏好修改界面。

##### `ui/features/settings/screens/UserPreferencesGuideScreen.kt`
提供配置用户偏好的引导设置体验。

##### `ui/features/settings/screens/ToolPermissionSettingsScreen.kt`
管理AI工具和功能的权限设置。

#### UI/Features/Toolbox

##### `ui/features/toolbox/screens/ToolboxScreen.kt`
实现包含各种实用工具的主工具箱屏幕。

##### `ui/features/toolbox/screens/FormatConverterScreen.kt`
提供在不同文件格式之间转换的功能。

#### UI/Features/Problems

##### `ui/features/problems/screens/ProblemLibraryScreen.kt`
实现用于显示和管理问题库的UI。

##### `ui/features/problems/viewmodel/ProblemLibraryViewModel.kt`
管理问题库功能的数据和业务逻辑。

### UI/Common

#### `ui/common/NavItem.kt`
提供在整个UI中使用的通用导航项实现。

#### UI/Common/Displays

##### `ui/common/displays/FpsCounter.kt`
实现帧率计数器，用于监控UI性能。

##### `ui/common/displays/MarkdownTextComposable.kt`
实现用于在UI中渲染Markdown文本的Composable。

##### `ui/common/displays/MessageContentParser.kt`
解析和处理聊天及其他UI组件中显示的消息内容。

##### `ui/common/displays/UIOperationOverlay.kt`
实现用于显示进行中UI操作的覆盖层。

## 服务模块

### `services/FloatingChatService.kt`
实现浮动聊天服务，允许用户从任何屏幕与AI助手交互。

### `services/ServiceLifecycleOwner.kt`
管理应用程序中各种服务的生命周期。

### `services/TermuxCommandResultService.kt`
处理Termux命令的执行和处理，促进命令行交互。

### `services/UIAccessibilityService.kt`
实现无障碍服务，使AI助手能够与设备UI交互。

## 工具模块

### `util/ArchiveUtil.kt`
提供归档操作的工具，如压缩和提取。

### `util/ChatUtils.kt`
包含聊天相关操作的实用功能。

### `util/DocumentConversionUtil.kt`
提供在不同文档格式之间转换的工具。

### `util/FFmpegUtil.kt`
实现使用FFmpeg处理媒体操作的工具。

### `util/FileFormatUtil.kt`
包含处理不同文件格式和转换的工具。

### `util/IntRangeSerializer.kt`
实现整数范围的序列化。

### `util/LocalDateTimeSerializer.kt`
实现LocalDateTime对象的序列化。

### `util/MediaConversionUtil.kt`
提供在不同媒体格式之间转换的工具。

### `util/NetworkUtils.kt`
包含网络操作的实用功能。

### `util/SerializationSetup.kt`
设置应用程序的序列化配置。

### `util/TextSegmenter.kt`
提供用于处理的文本分段工具。

### `util/VectorDatabaseHelper.kt`
实现使用向量数据库的辅助功能。

---

收到Claude的mcp服务器，Autojs，OpenManus和Cline的启发，我们想到可以把ai同手机系统深度结合起来，从而自动化地完成一系列任务，以及挖掘手机在日常生活之中的潜力。

ai agent使用的token消耗高，豆包通义一类的ai更侧重多模态理解和对话以及图片生成忽略了实际问题的执行，手机自带的助手闭锁不智慧也不自由，Manus的高价邀请码和Claude的高价执行收费标准，Operit AI应运而生。

deepseek爆火，其chat模型能力强悍，反应迅速，并且有磁盘缓存的技术，我们意识到可以通过deepseek结合工具的调用来实现一个更加自由开放，工具和操作能力多样化的ai智能助手软件

项目的文件夹分级有点奇怪，所以我在这里把主要的功能都概述一下。
首先最基础的就是ai的对话能力。

1.ai对话和对话模式。
非计划模式
ai在正常聊天的时候采用flow的异步流式传输，在等待用户输入的时候会输出等待用户响应标签，在任务结束的时候会输出任务完成标签，在对话过程中将会进行工具执行和工具请求。

计划模式
ai会先制订一个计划，然后会在接下来的对话中在执行工具和等待用户响应的过程中推进计划进度，直到任务完成。相比toolgraph，计划模式对于ai有更强的自由度和选择性。

2.工具和拓展包
工具
工具是作为ai的普通输出模式，不同于ai call模式，其描述能力和连续对话能力都有显著的提高。工具在另外一个readme里面有详细的说明，主要是基于adb权限的文件管理、系统设置修改，基于okhttp和webview注入的网页请求，基于无障碍的自动化屏幕点击，基于intent的linux模拟器调用、各种日常工具跳转，基于各种库和ffmpeg的文件格式转换和音视频处理。

拓展包
ai在用户没有导入拓展包的时候，不会识别到它。在导入后，ai可以通过use package工具来获取拓展包的具体提示内容，然后基于提示内容进行进一步的工具执行。这样的操作明显减轻了ai对于工具选择的难度和减少了系统提示词长度。

3.问题库和记忆优化
ai在每次任务完成的时候，就会进行一次问题总结，然后把问题放入问题库中。在需要的时候，它会根据向量索引的问题库进行一次搜索，然后获取用户的相关信息以及历史的处理方案。ai每次执行工具的时候，都会返回一个很长的结果，在记忆优化模式下，一些比较长的执行结果会智能清理，来优化ai的反应性能。

4.ai工具权限管理
用户可以通过主界面下方的按钮选择是否自动批准。这个指的是总权限。
所有权限分为以下状态:禁止，询问，警惕，允许。
当总权限设置为允许的时候，所有的权限都会根据小类的权限设定走。禁止的时候，将会禁止ai使用工具，剩下两个，对于一切权限都会进行询问。

小类权限:设置为禁止的时候，该分类禁止使用。设置为询问，就会每次执行都要进行弹窗。警惕模式下，对于读取一类的不危险操作就会直接进行，对于写入一类的危险操作就会询问。允许模式下，就会一直同意工具执行。

5.用户偏好配置
用户可以根据引导页快速配置用户的偏好，也可以新建一个偏好组使用。每次任务执行完毕后，ai都会谨慎地修改用户的当前偏好组内容。如果用户不希望被修改，可以进行偏好锁定。

6.工具箱
有的时候，可以跳过询问ai，直接去ui界面的工具箱执行和ai同样的功能。工具箱里面提供了环境配置、文件管理、手机应用权限管理、终端、文件格式转换等快捷工具，可以直接使用。

7.token统计
对于所有的和ai的交互，我们都在最底层进行了拦截和处理，来准确统计token用量和计算价钱。


开发用到的技术栈:
withContext、Flow、Collect、SaveState、lifeCircle等Compose的好用的库，可以实现数据保存、线程切换、页面生命周期管理、数据监听、异步获取等操作。

页面设计上，我们把ai对话设计在第一栏，对于计划模式，我们加入了左侧的时间线来提现每一个计划执行的状态。对于工具的执行、完成、请求转化，我们进行了折叠，来美化页面效果。我们加入了自动跟随到底部的智能判断，来一定程度上减少软件操作的繁琐。



| 功能/特性 | VSCode Cline | Cursor | Lamda 项目 | Manus/OpenManus | 国内模型(百度/通义/豆包) | Operit AI |
|---|---|---|---|---|---|---|
| **核心能力** | 代码操作辅助 | 增强代码编辑 | 安卓自动化 | 多功能Agent | 对话/多模态理解 | 全能AI助手 |
| **文件读写** | ✅ | ✅ | ✅ | ✅ | ❌/部分支持 | ✅ |
| **代码编辑** | ✅ | ✅⭐ | ❌ | ✅ | ❌/部分支持 | ✅ |
| **命令执行** | ✅ | ✅ | ✅ | ✅ | ❌/部分支持 | ✅ |
| **图像理解** | ❌ | ✅ (Claude 3.7) | ❌ | ✅ | ✅ | ✅ |
| **系统集成** | 仅VSCode | 仅代码环境 | 模拟器环境 | 部分系统能力 | 有限 | ✅⭐ (深度安卓集成) |
| **权限管理** | 有限 | 基础 | 无 | 有限 | 有限 | ✅⭐ (多级权限体系) |
| **计划执行** | ❌ | 部分支持 | ❌ | ✅ | ❌ | ✅⭐ (计划模式+时间线) |
| **插件扩展** | ❌ | ❌ | ❌ | 有限 | ❌ | ✅ (MCP兼容+JS拓展包) |
| **记忆优化** | 有限 | 有限 | ❌ | 部分支持 | 有限 | ✅⭐ (问题库+向量检索) |
| **用户偏好** | 有限 | 有限 | ❌ | 有限 | 有限 | ✅⭐ (可编辑偏好组) |
| **屏幕操作** | ❌ | ❌ | ✅ | 部分支持 | ❌ | ✅ (无障碍服务) |
| **使用门槛** | 需编程基础 | 需编程基础 | 高 | 中高 | 低 | 低 (打开即用) |
| **多模态任务** | ❌ | 部分支持 | ❌ | 部分支持 | 部分支持 | ✅ (单一对话多任务) |
| **工具多样性** | 有限 | 代码为主 | 有限 | 较多 | 有限 | ✅⭐ (文件/系统/自动化等) |
| **Token统计** | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **上下文管理** | 有限 | 较好 | ❌ | 较好 | 有限 | ✅⭐ (记忆优化) |
| **UI体验** | 开发者导向 | 开发者导向 | 复杂 | 功能导向 | 简单 | ✅⭐ (用户友好) |
| **流式输出** | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ |
| **手机端支持** | ❌ | 有限 | ✅ | 有限 | ✅ | ✅⭐ (原生体验) |
| **基础模型** | 多种 | Claude 3.7等 | 多种 | Claude等 | 自研模型 | DeepSeek等 |
