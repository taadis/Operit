# AI Assistant Tool Capabilities

This document explains how to use and extend the AI tools functionality in the application.

## Overview

The AI assistant now has the ability to use tools to provide enhanced responses. When the AI detects that a tool would be helpful to answer your question, it can automatically invoke the tool and incorporate the results into its response.

## Available Tools

The following tools are currently available:

### 1. Weather Tool
Get current weather information.
```
<tool name="weather">
</tool>
```

### 2. Calculator
Perform basic mathematical calculations.
```
<tool name="calculate">
<param name="expression">2+2</param>
</tool>
```

### 3. Web Search
Search the web for information.
```
<tool name="web_search">
<param name="query">Latest Android features</param>
</tool>
```

## How It Works

1. When you send a message to the AI, the enhanced AI service processes your request.
2. If the AI decides a tool would be helpful, it will include a tool invocation in its response.
3. The application detects the tool syntax, extracts the tool name and parameters.
4. The appropriate tool executor is invoked with the provided parameters.
5. The tool result is inserted back into the AI's response.
6. Progress is displayed to the user during this process.

## Architecture

The tool functionality is implemented with the following components:

- `AITool.kt`: Contains data models for tools, parameters, invocations, and results.
- `AIToolHandler.kt`: Core class that extracts and processes tool invocations.
- `EnhancedAIService.kt`: Extension of the base AIService that incorporates tool handling.
- `ToolProgressBar.kt`: UI component for displaying tool execution progress.
- `ReferencesDisplay.kt`: UI component for displaying reference links found in AI responses.

## Adding Custom Tools

To add a custom tool, follow these steps:

1. Register your tool executor in the `EnhancedAIService`:

```kotlin
// Example of registering a custom tool
enhancedAiService.registerTool("my_custom_tool") { tool ->
    val param1 = tool.parameters.find { it.name == "param_name" }?.value ?: ""
    
    // Process the tool parameters
    val result = processCustomTool(param1)
    
    // Return a tool result
    ToolResult(
        toolName = tool.name,
        success = true,
        result = "Custom tool result: $result"
    )
}
```

2. Update the system prompt to inform the AI about your new tool:

```kotlin
private val SYSTEM_PROMPT_TOOLS = """
    You have access to the following tools. When you want to use a tool, output it in this specific format:
    
    <tool name="tool_name">
    <param name="parameter_name">parameter_value</param>
    </tool>
    
    Available tools:
    - weather: Get the current weather. No parameters needed.
    - calculate: Calculate a mathematical expression. Parameters: expression (e.g. "2+2")
    - web_search: Search the web for information. Parameters: query (the search term)
    - my_custom_tool: Description of your custom tool. Parameters: param_name (description)
    
    Only use these tools when necessary. Use the exact format specified above.
""".trimIndent()
```

## Tool Execution Flow

1. User sends a message.
2. Input is preprocessed (optional).
3. Message is sent to the AI model with tool instructions.
4. AI response is received and monitored for tool invocations.
5. When a tool invocation is detected, execution is paused to process the tool.
6. Tool results are incorporated into the response.
7. Processing continues until complete.
8. References are extracted from the final response.

## UI Components

- **Tool Progress Bar**: Shows the current state and progress of tool execution.
- **References Display**: Shows clickable references extracted from AI responses.
- **Input Processing Indicator**: Shows when user input is being preprocessed.

## Customizing UI

You can customize the appearance of the tool components by modifying the following files:
- `ToolProgressBar.kt`: Customize the progress bar appearance.
- `ReferencesDisplay.kt`: Customize how references are displayed.

## Troubleshooting

If you encounter issues with the tool functionality:

1. Check that the tool syntax is correctly formatted.
2. Verify that the tool name is registered with an executor.
3. Look for log messages with the tag "AIToolHandler" or "EnhancedAIService".
4. Ensure the AI system prompt includes instructions for the tool.

---

For more information, please refer to the source code or contact the development team. 