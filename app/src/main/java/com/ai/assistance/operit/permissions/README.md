# Tool Permission System

This directory contains the centralized permission system for AI tools in the Operit application.

## Overview

The permission system is designed to control which operations AI tools are allowed to perform based on user preferences and security considerations. The system is centered around the `ToolPermissionSystem` class, which manages both permission storage and checking.

## Key Components

### ToolPermissionSystem

The central class that manages all permission-related functionality:

- Permission level storage using DataStore
- Permission checking for tools
- Permission request handling via overlays
- Dangerous operation detection
- Operation description generation

### ToolCategory

An enum that categorizes tools based on their security implications:

- `SYSTEM_OPERATION` - Tools that modify system settings
- `NETWORK` - Tools that perform network operations
- `UI_AUTOMATION` - Tools that automate UI interactions
- `FILE_READ` - Tools that read files
- `FILE_WRITE` - Tools that write or delete files

### PermissionLevel

An enum that defines the possible permission levels for each category:

- `ALLOW` - Allow all operations without asking
- `CAUTION` - Ask for dangerous operations, allow others
- `ASK` - Always ask for permission
- `FORBID` - Never allow operations

## Integration with Tool Registration

When registering tools in `AIToolHandler`, each tool is associated with:

1. A category that determines its default permission level
2. Optional custom danger check logic
3. Optional custom description generator

This information is used at execution time to determine whether the tool should be allowed to run and what description to show to the user.

## Usage

```kotlin
// Get the tool permission system
val permissionSystem = ToolPermissionSystem.getInstance(context)

// Register a tool with custom danger check and description
toolHandler.registerTool(
    name = "my_tool",
    category = ToolCategory.SYSTEM_OPERATION,
    dangerCheck = { tool -> 
        // Custom logic to determine if this operation is dangerous
        val param = tool.parameters.find { it.name == "target" }?.value ?: ""
        param.contains("system")
    },
    descriptionGenerator = { tool ->
        // Custom description for user permission dialog
        val target = tool.parameters.find { it.name == "target" }?.value ?: ""
        "Perform operation on: $target"
    },
    executor = { tool ->
        // Tool implementation...
    }
)

// Check permission for a tool
val isAllowed = permissionSystem.checkToolPermission(myTool)
```

## Why This Design?

This centralized permission system improves on the previous design by:

1. Eliminating separate mapping logic between tools and categories
2. Registering permission-related information directly with tools
3. Making danger checks and descriptions customizable per tool
4. Providing a cleaner, more maintainable API 