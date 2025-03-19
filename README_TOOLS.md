# AI Assistant Tool Capabilities

This document explains how to use and extend the AI tools functionality in the application.

## Overview

The AI assistant now has the ability to use tools to provide enhanced responses. When the AI detects that a tool would be helpful to answer your question, it can automatically invoke the tool and incorporate the results into its response.

## Available Tools

The following tools are currently available:

### 1. Basic Tools
Basic utility tools for simple operations.

#### 1.1 Calculator
Perform basic mathematical calculations.
```
<tool name="calculate">
<param name="expression">2+2</param>
</tool>
```

#### 1.2 Sleep
Pause execution for a specified duration in milliseconds.
```
<tool name="sleep">
<param name="duration_ms">2000</param>
</tool>
```

#### 1.3 Device Info
Obtain basic device information for the current app session.
```
<tool name="device_info">
</tool>
```

### 2. File System Tools
The application provides several tools for file system operations:

#### 2.1 List Files
List contents of a directory.
```
<tool name="list_files">
<param name="path">/sdcard/Download</param>
</tool>
```

#### 2.2 Read File
Read the content of a file.
```
<tool name="read_file">
<param name="path">/sdcard/Download/example.txt</param>
</tool>
```

#### 2.3 Write File
Write content to a file.
```
<tool name="write_file">
<param name="path">/sdcard/Download/example.txt</param>
<param name="content">Hello, world!</param>
<param name="append">false</param>
</tool>
```

#### 2.4 Delete File
Delete a file or directory.
```
<tool name="delete_file">
<param name="path">/sdcard/Download/example.txt</param>
<param name="recursive">false</param>
</tool>
```

#### 2.5 File Exists
Check if a file or directory exists.
```
<tool name="file_exists">
<param name="path">/sdcard/Download/example.txt</param>
</tool>
```

#### 2.6 Move File
Move or rename a file or directory.
```
<tool name="move_file">
<param name="source">/sdcard/Download/example.txt</param>
<param name="destination">/sdcard/Download/new_name.txt</param>
</tool>
```

#### 2.7 Copy File
Copy a file or directory.
```
<tool name="copy_file">
<param name="source">/sdcard/Download/example.txt</param>
<param name="destination">/sdcard/Documents/example_copy.txt</param>
<param name="recursive">false</param>
</tool>
```

#### 2.8 Make Directory
Create a directory.
```
<tool name="make_directory">
<param name="path">/sdcard/MyNewFolder</param>
<param name="create_parents">false</param>
</tool>
```

#### 2.9 Find Files
Search for files matching a pattern.
```
<tool name="find_files">
<param name="path">/sdcard/Download</param>
<param name="pattern">*.jpg</param>
<param name="max_depth">2</param>
<param name="use_path_pattern">false</param>
<param name="case_insensitive">false</param>
</tool>
```

**Important:** The `path` parameter MUST start with `/sdcard/` to avoid system issues. Searching from root directory is not supported and may result in errors.

The `max_depth` parameter controls how deep the search will go in subdirectories:
- If not specified, the search is fully recursive (unlimited depth)
- Set to `0` to search only in the specified path without subdirectories
- Set to a positive number to limit search depth (e.g., `1` for immediate subdirectories only)

The `use_path_pattern` parameter (default: false) determines whether to match against the file name or the entire path.
The `case_insensitive` parameter (default: false) controls whether the pattern matching is case-sensitive.

#### 2.10 File Info
Get information about a file or directory.
```
<tool name="file_info">
<param name="path">/sdcard/Download/example.txt</param>
</tool>
```

#### 2.11 Zip Files
Compress files or directories.
```
<tool name="zip_files">
<param name="source">/sdcard/Download/MyFolder</param>
<param name="destination">/sdcard/Download/MyFolder.zip</param>
</tool>
```

#### 2.12 Unzip Files
Extract a zip file.
```
<tool name="unzip_files">
<param name="source">/sdcard/Download/archive.zip</param>
<param name="destination">/sdcard/Download/extracted</param>
</tool>
```

#### 2.13 Open File
Open a file with the system's default application.
```
<tool name="open_file">
<param name="path">/sdcard/Download/document.pdf</param>
</tool>
```

#### 2.14 Share File
Share a file using the system's share interface.
```
<tool name="share_file">
<param name="path">/sdcard/Download/image.jpg</param>
<param name="title">Check out this image!</param>
</tool>
```

#### 2.15 Download File
Download a file from a URL to local storage.
```
<tool name="download_file">
<param name="url">https://example.com/file.pdf</param>
<param name="destination">/sdcard/Download/file.pdf</param>
</tool>
```

### 3. HTTP Tools
These tools provide direct access to HTTP resources, allowing the AI to directly fetch web pages and send HTTP requests.

#### 3.1 Fetch Web Page
Retrieve content from a web page.
```
<tool name="fetch_web_page">
<param name="url">https://example.com</param>
<param name="format">text</param>
</tool>
```
Parameter `format` is optional and can be either "text" (default, returns plain text) or "html" (returns the raw HTML).

#### 3.2 HTTP Request
Send an HTTP request with full control over method, headers, and body.
```
<tool name="http_request">
<param name="url">https://api.example.com/data</param>
<param name="method">POST</param>
<param name="headers">{"Content-Type": "application/json", "Authorization": "Bearer token123"}</param>
<param name="body">{"name": "Test", "value": 123}</param>
<param name="body_type">json</param>
</tool>
```
- `method` is optional, defaults to GET (supports GET, POST, PUT, DELETE, HEAD, OPTIONS)
- `headers` is optional, should be a JSON object
- `body` is optional, used for POST/PUT/DELETE requests
- `body_type` is optional, can be "json" (default), "form", or "text"

#### 3.3 Web Search
Returns pre-defined simulated search results (no actual web access).
```
<tool name="web_search">
<param name="query">Latest Android features</param>
</tool>
```

### 4. System Operation Tools
These tools provide access to system-level operations. Note that these operations require user authorization.

#### 4.1 Get System Setting
Retrieve the value of a system setting.
```
<tool name="get_system_setting">
<param name="setting">screen_brightness</param>
<param name="namespace">system</param>
</tool>
```

#### 4.2 Modify System Setting
Change the value of a system setting.
```
<tool name="modify_system_setting">
<param name="setting">screen_brightness</param>
<param name="value">100</param>
<param name="namespace">system</param>
</tool>
```

#### 4.3 Install App
Install an application from an APK file.
```
<tool name="install_app">
<param name="apk_path">/sdcard/Download/app.apk</param>
</tool>
```

#### 4.4 Uninstall App
Uninstall an application.
```
<tool name="uninstall_app">
<param name="package_name">com.example.app</param>
<param name="keep_data">false</param>
</tool>
```

#### 4.5 List Installed Apps
Get a list of installed applications.
```
<tool name="list_installed_apps">
<param name="include_system_apps">false</param>
</tool>
```

#### 4.6 Start App
Launch an application.
```
<tool name="start_app">
<param name="package_name">com.example.app</param>
</tool>
```

#### 4.7 Stop App
Force stop a running application.
```
<tool name="stop_app">
<param name="package_name">com.example.app</param>
</tool>
```

### 5. UI Automation Tools
These tools provide UI automation capabilities through ADB, allowing the AI to interact with the device interface.

#### 5.1 Get Page Info
Retrieve information about the current UI page/window, including the full UI hierarchy.
```
<tool name="get_page_info">
<param name="format">xml</param>
<param name="detail">summary</param>
</tool>
```
- `format` is optional, can be "xml" (default) or "json"
- `detail` is optional, can be "minimal", "summary" (default), or "full"
  - `minimal`: Returns basic information about the current window and a simplified UI structure
  - `summary`: Returns basic information plus a comprehensive list of interactive elements
  - `full`: Returns complete information including window details and the full UI hierarchy

#### 5.2 Tap
Simulate a tap/click at specific coordinates.
```
<tool name="tap">
<param name="x">500</param>
<param name="y">800</param>
</tool>
```

#### 5.3 Click Element
Simulate a click on an element identified by resource ID, text, content description, or class name.
```
<tool name="click_element">
<param name="resourceId">com.example.app:id/button1</param>
</tool>
```
Alternative ways to identify elements:
```
<tool name="click_element">
<param name="text">Submit</param>
</tool>
```
```
<tool name="click_element">
<param name="contentDesc">Submit button</param>
</tool>
```
```
<tool name="click_element">
<param name="className">Button</param>
</tool>
```

Optional parameters for greater precision (especially useful for lists where multiple elements share the same identifiers):
```
<tool name="click_element">
<param name="resourceId">com.example.app:id/list_item</param>
<param name="index">2</param>
</tool>
```
```
<tool name="click_element">
<param name="text">View Details</param>
<param name="partialMatch">true</param>
</tool>
```

At least one of `resourceId`, `text`, `contentDesc`, or `className` must be provided.
- `index`: The zero-based index of the element when multiple matches are found (default: 0, meaning the first match)
- `partialMatch`: If true, allows partial text matching instead of exact matching (default: false)

#### 5.4 Set Input Text
Set text in an input field (the field must have focus).
```
<tool name="set_input_text">
<param name="text">Hello, world!</param>
</tool>
```

#### 5.5 Press Key
Simulate pressing a specific key.
```
<tool name="press_key">
<param name="keyCode">KEYCODE_BACK</param>
</tool>
```
Common key codes include:
- `KEYCODE_BACK` - Back button
- `KEYCODE_HOME` - Home button
- `KEYCODE_MENU` - Menu button
- `KEYCODE_ENTER` - Enter key
- `KEYCODE_TAB` - Tab key
- `KEYCODE_DPAD_UP` - D-pad up
- `KEYCODE_DPAD_DOWN` - D-pad down
- `KEYCODE_DPAD_LEFT` - D-pad left
- `KEYCODE_DPAD_RIGHT` - D-pad right

#### 5.6 Swipe
Perform a swipe gesture from one point to another.
```
<tool name="swipe">
<param name="startX">500</param>
<param name="startY">1000</param>
<param name="endX">500</param>
<param name="endY">500</param>
<param name="duration">300</param>
</tool>
```
- `duration` is optional, specifies the duration of the swipe in milliseconds (default: 300)

#### 5.7 Launch App
Launch an application by its package name.
```
<tool name="launch_app">
<param name="packageName">com.example.app</param>
</tool>
```

#### 5.8 Combined Operation
Perform a UI operation, wait for a specified time, and then return the new UI state. This tool is particularly useful for interactive flows that require waiting for UI changes after an action.
```
<tool name="combined_operation">
<param name="operation">tap 500 800</param>
<param name="delayMs">2000</param>
</tool>
```

The `operation` parameter uses a simple command syntax to specify which action to perform:

1. Tap at coordinates:
```
tap x y
```
Example: `tap 500 800`

2. Swipe from one point to another:
```
swipe startX startY endX endY [duration]
```
Example: `swipe 500 1000 500 200 300`

3. Click an element by identifier:
```
click_element type value [index] [partialMatch]
```
Example: `click_element resourceId com.example.app:id/button1`
Example: `click_element text Submit`
Example: `click_element contentDesc Submit button`
Example: `click_element className Button`
Example: `click_element resourceId com.example.app:id/list_item 2` (clicks the 3rd item with matching resource ID)
Example: `click_element text View 0 true` (clicks the first element containing "View" text)

4. Press a key:
```
press_key keyCode
```
Example: `press_key KEYCODE_BACK`

5. Set input text:
```
set_input_text value
```
Example: `set_input_text Hello, world!`

6. Launch an app:
```
launch_app packageName
```
Example: `launch_app com.example.app`

The `delayMs` parameter is optional and specifies how long to wait after performing the operation before retrieving the new UI state (default: 1000ms).

This tool makes it much easier to handle sequences like:
- Tap a button and see what happens
- Enter text and see the response
- Swipe to scroll and see new content

**Important:** This tool returns both the current window information and the simplified UI hierarchy after the operation, making it perfect for user interface exploration and interaction.

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
- `HttpTools.kt`: Implements HTTP tools for fetching web pages and making HTTP requests.
- `FileSystemTools.kt`: Implements file system operations.
- `SystemOperationTools.kt`: Implements system-level operations.

## Adding Custom Tools

To add a custom tool, follow these steps:

1. Register your tool executor in the `AIToolHandler`:

```kotlin
// Example of registering a custom tool
registerTool("my_custom_tool") { tool ->
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

2. Update the system prompt in `EnhancedAIService` to inform the AI about your new tool:

```kotlin
private val SYSTEM_PROMPT = """
    You are Operit, an all-capable AI assistant, aimed at solving any task presented by the user. You have various tools at your disposal that you can call upon to efficiently complete complex requests.
    
    Available tools:
    - calculate: Simple calculator that evaluates basic expressions locally. Parameters: expression (e.g. "2+2", "sqrt(16)")
    - sleep: Pause execution for a specified duration in milliseconds. Parameters: duration_ms (milliseconds, default 1000, max 10000)
    - device_info: Returns basic device identifier for the current app session only. No parameters needed.
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