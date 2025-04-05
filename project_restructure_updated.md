# Updated Project Restructuring Plan

## Current Issues Observed
- Duplicate data directories (`data/` and `core/data/`)
- Duplicated ChatHistoryManager in two locations
- Inconsistent organization of viewmodels
- Misaligned package structure

## Fixing the Structure

The new structure should maintain the good parts of the current reorganization while fixing the inconsistencies:

```
app/src/main/java/com/ai/assistance/operit/
├─ core/                          # Core application functionality
│  ├─ application/                # Application initialization
│  │  └─ OperitApplication.kt
│  └─ config/                     # Core configuration
│     └─ SystemPromptConfig.kt
├─ data/                          # All data-related components
│  ├─ model/                      # Data models
│  │  ├─ AiReference.kt
│  │  ├─ AITool.kt
│  │  ├─ ChatHistory.kt
│  │  ├─ ChatMessage.kt
│  │  ├─ InputProcessingState.kt
│  │  ├─ UserPreferences.kt
│  │  └─ ToolResult.kt            # Renamed from ToolResultDataClasses.kt
│  ├─ repository/                 # Data repositories
│  │  ├─ ChatHistoryManager.kt
│  │  └─ UIHierarchyManager.kt
│  └─ preferences/                # Preferences management
│     ├─ ApiPreferences.kt
│     └─ UserPreferencesManager.kt
├─ domain/                        # Business logic
│  └─ manager/                    # Domain managers
│     ├─ ConversationMarkupManager.kt
│     ├─ ConversationRoundManager.kt
│     ├─ InputProcessor.kt
│     ├─ ReferenceManager.kt
│     └─ ToolExecutionManager.kt
├─ ui/                            # UI components
│  ├─ main/                       # Main UI components
│  │  ├─ MainActivity.kt
│  │  └─ OperitApp.kt
│  ├─ theme/                      # Theming
│  │  ├─ Color.kt
│  │  ├─ Theme.kt
│  │  └─ Type.kt
│  ├─ permissions/                # Permission handling
│  │  ├─ PermissionRequestOverlay.kt
│  │  ├─ ToolPermissionDialog.kt
│  │  └─ ToolPermissionSystem.kt
│  ├─ floating/                   # Floating UI
│  │  ├─ FloatingChatWindow.kt
│  │  └─ FloatingWindowTheme.kt
│  ├─ common/                     # Common UI components
│  │  ├─ NavItem.kt
│  │  ├─ displays/
│  │  │  ├─ CodeBlockComposable.kt
│  │  │  ├─ MessageContentParser.kt
│  │  │  ├─ TextWithCodeBlocksComposable.kt
│  │  │  └─ UIOperationOverlay.kt
│  │  └─ animations/
│  │     └─ SimpleAnimation.kt
│  └─ chat/                       # Chat UI components
│     └─ viewmodel/
│        └─ ChatViewModel.kt
├─ tools/                         # Tools (keep as is per request)
│  ├─ AIToolHandler.kt
│  ├─ ToolPackage.kt
│  ├─ ToolRegistration.kt
│  ├─ ToolResultDataClasses.kt
│  ├─ system/
│  │  ├─ AdbCommandExecutor.kt
│  │  ├─ ShizukuInstaller.kt
│  │  ├─ TermuxAuthorizer.kt
│  │  ├─ TermuxCommandExecutor.kt
│  │  └─ TermuxInstaller.kt
│  ├─ packTool/
│  │  └─ PackageManager.kt
│  ├─ javascript/
│  │  ├─ JsEngine.kt
│  │  ├─ JsToolManager.kt
│  │  ├─ README.md
│  │  └─ ScriptExecutionReceiver.kt
│  ├─ defaultTool/
│  │  ├─ Calculator.kt
│  │  ├─ DeviceInfoToolExecutor.kt
│  │  ├─ FFmpegTool.kt
│  │  ├─ FileConverterTool.kt
│  │  ├─ FileSystemTools.kt
│  │  ├─ HttpTools.kt
│  │  ├─ SystemOperationTools.kt
│  │  ├─ UITools.kt
│  │  └─ WebSearchTool.kt
│  └─ calculator/
│     ├─ Calculator.kt
│     ├─ CalculatorTest.kt
│     └─ JsCalculator.kt
├─ services/                      # Android services
│  ├─ FloatingChatService.kt
│  ├─ ServiceLifecycleOwner.kt
│  ├─ TermuxCommandResultService.kt
│  └─ UIAccessibilityService.kt
├─ api/                           # API integrations
│  ├─ AIService.kt
│  ├─ EnhancedAIService.kt
│  └─ library/
│     ├─ ProblemLibrary.kt
│     └─ README.md
└─ util/                          # Utilities
   ├─ ChatUtils.kt
   ├─ IntRangeSerializer.kt
   ├─ LocalDateTimeSerializer.kt
   ├─ NetworkUtils.kt
   └─ SerializationSetup.kt
```

## Files to Move/Resolve

### Removing Duplications
1. **Remove the duplicate ChatHistoryManager.kt**:
   - Keep `data/repository/ChatHistoryManager.kt`
   - Delete `core/data/ChatHistoryManager.kt`

### Other Adjustments
1. **Consider moving ChatViewModel.kt to a better location**:
   - Current: `ui/chat/viewmodel/ChatViewModel.kt`
   - This is fine as is, but ensure all UI-related viewmodels follow this pattern

2. **Consider creating a tool model package**:
   - Long term, consider moving `tools/ToolResultDataClasses.kt` to `data/model/ToolResult.kt`
   - Keep as is for now since you mentioned not to change tools

## Implementation Instructions

1. **Fix the duplicate ChatHistoryManager issue first**
```bash
# Remove the duplicate file
rm app/src/main/java/com/ai/assistance/operit/core/data/ChatHistoryManager.kt
```

2. **Ensure all viewmodels follow consistent pattern**
   - If there are other viewmodels, organize them under respective feature packages with a `viewmodel` subfolder (like chat/viewmodel)

3. **Update any import references**
   - Since files have been moved, make sure all imports point to the correct locations
   - Run a project-wide search for imports referencing old locations and update them

4. **Update build files if necessary**
   - Make sure any build files (like build.gradle) reference the correct packages

## Benefits of This Structure

1. **Clean organization**
   - Clear separation between data, domain, UI, and utilities
   - No duplicate files or functionality

2. **Better developer experience**
   - Easier to find files based on their purpose
   - Logical grouping of related functionality

3. **Easier maintenance**
   - When adding new features, there's a clear pattern to follow
   - Reduces risk of creating inconsistent structures

4. **Future extensibility**
   - The structure allows for adding new modules and components without major reorganization

## Next Steps

After implementing these changes, consider:

1. **Adding dependency injection** to help manage dependencies between components
2. **Creating a module structure** if the app continues to grow
3. **Adding module-level README files** to explain component purposes
4. **Implementing tests** that verify the proper interactions between components 