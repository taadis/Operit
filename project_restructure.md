# Project Restructuring Plan

## Current Issues
- Mixed concerns within packages
- Unclear module boundaries
- Scattered related functionality
- Difficult to navigate for newcomers

## New Structure Overview

```
app/src/main/java/com/ai/assistance/operit/
├─ core/
│  ├─ OperitApplication.kt
│  ├─ di/                         # Dependency injection
│  └─ config/                     # App configuration
├─ data/
│  ├─ model/                      # Data models
│  ├─ repository/                 # Data repositories
│  ├─ preferences/                # Preferences management
│  └─ local/                      # Local storage
├─ domain/
│  ├─ usecase/                    # Business logic use cases
│  └─ manager/                    # Domain managers
├─ ui/
│  ├─ main/                       # Main screen
│  │  ├─ MainActivity.kt
│  │  └─ MainViewModel.kt
│  ├─ chat/                       # Chat UI
│  │  ├─ components/
│  │  └─ viewmodel/
│  ├─ floating/                   # Floating window UI
│  ├─ common/                     # Shared UI components
│  │  ├─ composables/
│  │  ├─ animations/
│  │  └─ displays/
│  ├─ theme/                      # Theming
│  └─ permissions/                # Permission handling
├─ tools/
│  ├─ core/                       # Tool core functionality
│  │  ├─ AIToolHandler.kt
│  │  ├─ ToolRegistration.kt
│  │  └─ ToolPackage.kt
│  ├─ system/                     # System tools
│  ├─ javascript/                 # JavaScript tools
│  ├─ utilities/                  # Utility tools
│  ├─ file/                       # File operation tools
│  ├─ network/                    # Network tools
│  └─ calculator/                 # Calculator tools
├─ api/
│  ├─ service/                    # API service interfaces
│  ├─ implementation/             # API implementations
│  └─ enhanced/                   # Enhanced API features
├─ service/                       # Android services
│  ├─ accessibility/
│  ├─ floating/
│  └─ background/
└─ util/                          # Common utilities
   ├─ extensions/                 # Kotlin extensions
   ├─ serialization/              # Serialization helpers
   └─ network/                    # Network utilities
```

## File Moving Instructions

### Core Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/core/di
mkdir -p app/src/main/java/com/ai/assistance/operit/core/config
```
- Move `core/OperitApplication.kt` → `core/OperitApplication.kt` (stays in place)
- Move `api/enhanced/config/SystemPromptConfig.kt` → `core/config/SystemPromptConfig.kt`

### Data Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/data/model
mkdir -p app/src/main/java/com/ai/assistance/operit/data/repository
mkdir -p app/src/main/java/com/ai/assistance/operit/data/preferences
mkdir -p app/src/main/java/com/ai/assistance/operit/data/local
```
- Move `core/model/AiReference.kt` → `data/model/AiReference.kt`
- Move `core/model/AITool.kt` → `data/model/AITool.kt`
- Move `core/model/ChatHistory.kt` → `data/model/ChatHistory.kt`
- Move `core/model/ChatMessage.kt` → `data/model/ChatMessage.kt`
- Move `core/model/InputProcessingState.kt` → `data/model/InputProcessingState.kt`
- Move `core/model/UserPreferences.kt` → `data/model/UserPreferences.kt`
- Move `core/data/ApiPreferences.kt` → `data/preferences/ApiPreferences.kt`
- Move `core/data/ChatHistoryManager.kt` → `data/repository/ChatHistoryManager.kt`
- Move `core/data/UIHierarchyManager.kt` → `data/repository/UIHierarchyManager.kt`
- Move `core/data/UserPreferencesManager.kt` → `data/preferences/UserPreferencesManager.kt`
- Move `tools/ToolResultDataClasses.kt` → `data/model/ToolResult.kt`

### Domain Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/domain/usecase
mkdir -p app/src/main/java/com/ai/assistance/operit/domain/manager
```
- Move `api/enhanced/utils/InputProcessor.kt` → `domain/manager/InputProcessor.kt`
- Move `api/enhanced/utils/ReferenceManager.kt` → `domain/manager/ReferenceManager.kt`
- Move `api/enhanced/utils/ToolExecutionManager.kt` → `domain/manager/ToolExecutionManager.kt`
- Move `api/enhanced/models/ConversationMarkupManager.kt` → `domain/manager/ConversationMarkupManager.kt`
- Move `api/enhanced/models/ConversationRoundManager.kt` → `domain/manager/ConversationRoundManager.kt`

### UI Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/main
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/chat/components
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/chat/viewmodel
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/floating
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/common/composables
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/common/animations
mkdir -p app/src/main/java/com/ai/assistance/operit/ui/common/displays
```
- Move `core/MainActivity.kt` → `ui/main/MainActivity.kt`
- Move `core/viewmodel/ChatViewModel.kt` → `ui/chat/viewmodel/ChatViewModel.kt`
- Move `ui/OperitApp.kt` → `ui/main/OperitApp.kt`
- Move `ui/theme/` → `ui/theme/` (keep directory structure)
- Move `ui/permissions/` → `ui/permissions/` (keep directory structure)
- Move `ui/navigation/NavItem.kt` → `ui/common/NavItem.kt`
- Move `ui/display/UIOperationOverlay.kt` → `ui/common/displays/UIOperationOverlay.kt`
- Move `ui/common/common/animations/SimpleAnimation.kt` → `ui/common/animations/SimpleAnimation.kt`
- Move `ui/common/common/displays/CodeBlockComposable.kt` → `ui/common/displays/CodeBlockComposable.kt`
- Move `ui/common/common/displays/MessageContentParser.kt` → `ui/common/displays/MessageContentParser.kt`
- Move `ui/common/common/displays/TextWithCodeBlocksComposable.kt` → `ui/common/displays/TextWithCodeBlocksComposable.kt`
- Move `services/ui/FloatingChatWindow.kt` → `ui/floating/FloatingChatWindow.kt`
- Move `services/ui/FloatingWindowTheme.kt` → `ui/floating/FloatingWindowTheme.kt`

### Tools Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/tools/core
mkdir -p app/src/main/java/com/ai/assistance/operit/tools/system
mkdir -p app/src/main/java/com/ai/assistance/operit/tools/javascript
mkdir -p app/src/main/java/com/ai/assistance/operit/tools/utilities
mkdir -p app/src/main/java/com/ai/assistance/operit/tools/file
mkdir -p app/src/main/java/com/ai/assistance/operit/tools/network
```
- Move `tools/AIToolHandler.kt` → `tools/core/AIToolHandler.kt`
- Move `tools/ToolPackage.kt` → `tools/core/ToolPackage.kt`
- Move `tools/ToolRegistration.kt` → `tools/core/ToolRegistration.kt`
- Move `tools/system/` → `tools/system/` (keep directory structure)
- Move `tools/javascript/` → `tools/javascript/` (keep directory structure)
- Move `tools/calculator/` → `tools/calculator/` (keep directory structure)
- Move `tools/defaultTool/Calculator.kt` → `tools/calculator/CalculatorTool.kt` (rename to avoid duplication)
- Move `tools/defaultTool/DeviceInfoToolExecutor.kt` → `tools/utilities/DeviceInfoTool.kt`
- Move `tools/defaultTool/FFmpegTool.kt` → `tools/utilities/FFmpegTool.kt`
- Move `tools/defaultTool/FileConverterTool.kt` → `tools/file/FileConverterTool.kt`
- Move `tools/defaultTool/FileSystemTools.kt` → `tools/file/FileSystemTools.kt`
- Move `tools/defaultTool/HttpTools.kt` → `tools/network/HttpTools.kt`
- Move `tools/defaultTool/SystemOperationTools.kt` → `tools/system/SystemOperationTools.kt`
- Move `tools/defaultTool/UITools.kt` → `tools/utilities/UITools.kt`
- Move `tools/defaultTool/WebSearchTool.kt` → `tools/network/WebSearchTool.kt`
- Move `tools/packTool/PackageManager.kt` → `tools/utilities/PackageManager.kt`

### API Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/api/service
mkdir -p app/src/main/java/com/ai/assistance/operit/api/implementation
mkdir -p app/src/main/java/com/ai/assistance/operit/api/library
```
- Move `api/AIService.kt` → `api/service/AIService.kt`
- Move `api/EnhancedAIService.kt` → `api/implementation/EnhancedAIService.kt`
- Move `api/library/` → `api/library/` (keep directory structure)

### Service Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/service/accessibility
mkdir -p app/src/main/java/com/ai/assistance/operit/service/floating
mkdir -p app/src/main/java/com/ai/assistance/operit/service/background
```
- Move `services/FloatingChatService.kt` → `service/floating/FloatingChatService.kt`
- Move `services/ServiceLifecycleOwner.kt` → `service/ServiceLifecycleOwner.kt`
- Move `services/TermuxCommandResultService.kt` → `service/background/TermuxCommandResultService.kt`
- Move `services/UIAccessibilityService.kt` → `service/accessibility/UIAccessibilityService.kt`

### Util Module
```
mkdir -p app/src/main/java/com/ai/assistance/operit/util/extensions
mkdir -p app/src/main/java/com/ai/assistance/operit/util/serialization
mkdir -p app/src/main/java/com/ai/assistance/operit/util/network
```
- Move `util/ChatUtils.kt` → `util/extensions/ChatUtils.kt`
- Move `util/IntRangeSerializer.kt` → `util/serialization/IntRangeSerializer.kt`
- Move `util/LocalDateTimeSerializer.kt` → `util/serialization/LocalDateTimeSerializer.kt`
- Move `util/NetworkUtils.kt` → `util/network/NetworkUtils.kt`
- Move `util/SerializationSetup.kt` → `util/serialization/SerializationSetup.kt`

## Implementation Steps

1. **Create directory structure first**
   ```bash
   # Run all the mkdir commands listed above
   ```

2. **Move files systematically**
   - Move files one module at a time
   - Update import references in each file after moving

3. **Update references**
   - After moving all files, do a project-wide search for old package references
   - Systematically update all imports to reference the new locations

4. **Refactoring recommendations**
   - Rename files to better match their purpose
   - Merge similar functionalities
   - Split overly complex files

5. **Testing**
   - After restructuring, ensure the application builds correctly
   - Run unit tests to verify functionality
   - Perform manual testing of key features

## Benefits of New Structure

1. **Clear separation of concerns**
   - Each module has a specific responsibility
   - Files are organized by feature and function

2. **Improved maintainability**
   - New developers can easily understand the codebase structure
   - Related files are grouped together

3. **Better scalability**
   - New features can be added to the appropriate modules
   - Easier to extend without disrupting existing code

4. **Clearer dependencies**
   - Module dependencies are more explicit
   - Reduced circular dependencies 