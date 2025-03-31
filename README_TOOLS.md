# Operit AI Assistant Tool Capabilities

This document provides comprehensive documentation for all the tools available to the Operit AI assistant. These tools enable the AI to perform a wide range of operations from simple calculations to complex system interactions.

## Table of Contents

- [Overview](#overview)
- [Tool Usage](#tool-usage)
- [Basic Tools](#basic-tools)
  - [Calculator](#calculator)
  - [Sleep](#sleep)
  - [Device Info](#device-info)
- [File System Tools](#file-system-tools)
- [HTTP Tools](#http-tools)
- [System Operation Tools](#system-operation-tools)
- [UI Automation Tools](#ui-automation-tools)
- [Extending the Tool Framework](#extending-the-tool-framework)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)

## Overview

The Operit AI assistant can invoke specialized tools to complete tasks that require system capabilities or complex computations. When you ask a question that could benefit from a tool, the AI will automatically use the appropriate tool and incorporate the results into its response.

### How to Get the Most from the Calculator Tool

To encourage the AI to use the Calculator's advanced features, try these approaches:

1. **Be specific about calculations**: Instead of asking "What's the temperature in Celsius if it's 98.6°F?", try "Convert 98.6 Fahrenheit to Celsius."

2. **Mention key terms**: Including terms like "calculate", "convert", "days between", or "statistics" helps the AI recognize when to use the calculator.

3. **For date calculations**: Specify your dates clearly, for example: "How many days between January 1, 2023 and today?" or "What day of the week is December 25, 2023?"

4. **For unit conversions**: Clearly mention both units, like "Convert 150 kilometers to miles" or "What's 32°F in Celsius?"

5. **For statistical functions**: Provide the data set directly, such as "Calculate the average of 10, 15, 20, 25, and 30" or "What's the standard deviation of 5, 7, 9, 11, 13?"

The calculator is particularly useful for financial calculations, date manipulations, unit conversions, and any scenario where you need precise numerical results rather than explanations.

## Tool Usage

Tools are invoked using a standardized XML-like syntax. The AI handles this formatting automatically. The general structure is:

```
<tool name="tool_name">
<param name="parameter_name">parameter_value</param>
</tool>
```

## Basic Tools

### Calculator

The Calculator tool provides an enhanced mathematical evaluation engine that supports various operations from basic arithmetic to advanced calculations. It serves as a safe and powerful alternative to `eval()` with extensive capabilities for date operations, unit conversions, statistical functions, and more.

#### When to Use the Calculator

The calculator is ideal for:
- **Mathematical calculations**: From basic arithmetic to complex formulas
- **Date and time operations**: Finding differences between dates, adding days, determining weekdays
- **Unit conversions**: Converting between different systems of measurement
- **Statistical analysis**: Computing means, medians, standard deviations
- **Financial calculations**: Interest, discount, compound growth formulas
- **Conditional processing**: Using if-then-else logic for decision-making

When users ask questions involving any of these elements, the Calculator tool can provide precise numerical answers rather than just explaining the process.

#### Basic Usage

```
<tool name="calculate">
<param name="expression">2+2</param>
</tool>
```

#### Supported Features

##### 1. Arithmetic Operations

- **Basic Math**: `2+3*4/2`
- **Parentheses**: `(2+3)*4`
- **Exponentiation**: `2^3` (2 raised to power 3)
- **Percentage**: `50%` (returns 0.5)

##### 2. Mathematical Functions

- **Square Root**: `sqrt(16)`
- **Trigonometric**: `sin(30)`, `cos(45)`, `tan(60)` (angles in degrees)
- **Logarithmic**: `log(100)` (base 10), `ln(10)` (natural log)
- **Rounding**: `round(3.7)`, `floor(3.7)`, `ceil(3.6)`
- **Comparison**: `max(5,10)`, `min(5,10)`
- **Absolute Value**: `abs(-5)`
- **Factorial**: `fact(5)` (5! = 120)
- **Random Number**: `rand()` (returns a random value between 0 and 1)

##### 3. Constants

- **Pi**: `pi` (3.14159...)
- **Euler's Number**: `e` (2.71828...)

##### 4. Variables

You can store and retrieve values using variables:

```
<tool name="calculate">
<param name="expression">x=10</param>
</tool>

<tool name="calculate">
<param name="expression">y=20</param>
</tool>

<tool name="calculate">
<param name="expression">x+y</param>
</tool>
```

Variables persist throughout the conversation until explicitly cleared.

##### 5. Date Calculations

The Calculator supports the following date-related operations and functions:

- **Current Date**: `today()` - Returns the number of days since epoch (1970-01-01)
- **Current Timestamp**: `now()` - Returns the current time in milliseconds
- **Parse Date**: `date("2023-01-01")` - Parses a date string into days since epoch
- **Date Difference**: `date_diff(date1, date2)` - Calculates days between two dates
- **Date Addition**: `date_add(date, days)` - Adds or subtracts days from a date
- **Get Weekday**: `weekday(date)` - Gets the day of week (1-7, where 1 is Sunday)
- **Get Month**: `month(date)` - Gets the month of the date (1-12)
- **Get Year**: `year(date)` - Gets the year of the date
- **Get Day**: `day(date)` - Gets the day of month

All functions accept dates as parameters, which can be the result of `today()`, `date("2023-01-01")`, or days since epoch.
The calculator supports multiple common date formats, including:
- yyyy-MM-dd (e.g., 2023-01-01)
- yyyy/MM/dd (e.g., 2023/01/01)
- MM/dd/yyyy (e.g., 01/01/2023)
- dd/MM/yyyy (e.g., 01/01/2023)
- yyyy-MM-dd HH:mm:ss (e.g., 2023-01-01 12:30:45)
- yyyy/MM/dd HH:mm:ss (e.g., 2023/01/01 12:30:45)

**Note**: All date calculation operations return numeric results (days or timestamps), not formatted date strings.

**Practical Examples**:

```
# Calculate days between two dates
<tool name="calculate">
<param name="expression">date_diff(date("2023-01-01"), today())</param>
</tool>

# Calculate date 30 days from now (returns days since epoch)
<tool name="calculate">
<param name="expression">date_add(today(), 30)</param>
</tool>

# Check what day of week today is (1=Sunday, 2=Monday, etc.)
<tool name="calculate">
<param name="expression">weekday(today())</param>
</tool>

# Get the month of a specific date
<tool name="calculate">
<param name="expression">month(date("2023-12-25"))</param>
</tool>

# Calculate how many days are left in the current year
<tool name="calculate">
<param name="expression">date_diff(today(), date_add(date(year(today()) + "-12-31"), 1))</param>
</tool>

# Check if a date is in the future
<tool name="calculate">
<param name="expression">if(date_diff(today(), date("2024-01-01"))>0)then(1)else(0)</param>
</tool>
```

##### 6. Unit Conversions

Convert between different units of measurement:

```
<tool name="calculate">
<param name="expression">convert(32, f, c)</param>
</tool>
```

Supported unit conversions:

- **Temperature**: c (Celsius), f (Fahrenheit), k (Kelvin)
- **Length**: km (kilometers), mi (miles), m (meters), ft (feet), cm (centimeters), in (inches)
- **Weight**: kg (kilograms), lb (pounds), g (grams), oz (ounces)
- **Volume**: l (liters), gal (gallons), ml (milliliters), oz (fluid ounces)
- **Speed**: kph (kilometers per hour), mph (miles per hour)

##### 7. Statistical Functions

Perform statistical operations on sets of values:

```
<tool name="calculate">
<param name="expression">stats.mean(1,2,3,4,5)</param>
</tool>
```

Available statistical functions:

- **Mean**: `stats.mean(values...)` - Calculate average
- **Median**: `stats.median(values...)` - Find middle value
- **Minimum**: `stats.min(values...)` - Find minimum value
- **Maximum**: `stats.max(values...)` - Find maximum value
- **Sum**: `stats.sum(values...)` - Sum values
- **Standard Deviation**: `stats.stdev(values...)` - Calculate standard deviation

##### 8. Conditional Logic

The calculator supports conditional expressions with an if-then-else syntax:

```
<tool name="calculate">
<param name="expression">if(x>5)then(10)else(20)</param>
</tool>
```

**Comparison Operators**:
- Equality: `==` (e.g., `x == 10`)
- Inequality: `!=` (e.g., `x != 0`)
- Greater than: `>` (e.g., `x > 5`)
- Less than: `<` (e.g., `x < 10`)
- Greater than or equal: `>=` (e.g., `x >= 100`)
- Less than or equal: `<=` (e.g., `x <= 50`)

**Logical Operators**:
- AND: `&&` (e.g., `x > 5 && y < 10`)
- OR: `||` (e.g., `x == 0 || y == 0`)

**Examples**:

```
# Basic if-then-else with a comparison
<tool name="calculate">
<param name="expression">if(10>5)then(1)else(0)</param>
</tool>

# Using variables in conditions
<tool name="calculate">
<param name="expression">x=15; if(x>10)then(x*2)else(x/2)</param>
</tool>

# Compound conditions with logical operators
<tool name="calculate">
<param name="expression">x=5; y=10; if(x<10 && y>5)then(x+y)else(x-y)</param>
</tool>

# Nested expressions in conditions
<tool name="calculate">
<param name="expression">if(sqrt(16)==4)then(1)else(0)</param>
</tool>

# Conditional based on date comparison
<tool name="calculate">
<param name="expression">if(year(today())==2023)then("Current year")else("Different year")</param>
</tool>
```

The conditional expressions are evaluated strictly as numeric results: any non-zero result in a condition is considered `true`, while zero is considered `false`.

#### Overall Examples

Here are some comprehensive examples combining multiple features of the calculator:

```
# Temperature conversion with rounding
<tool name="calculate">
<param name="expression">round(convert(98.6, f, c))</param>
</tool>

# Calculating discount with variables and percentages
<tool name="calculate">
<param name="expression">price=100; discount=15%; price*(1-discount)</param>
</tool>

# Calculating average speed for a trip
<tool name="calculate">
<param name="expression">distance=150; time=2.5; convert(distance/time, kph, mph)</param>
</tool>

# Financial calculation: compound interest
<tool name="calculate">
<param name="expression">principal=1000; rate=0.05; years=5; principal*(1+rate)^years</param>
</tool>

# Using today's date with conditional logic
<tool name="calculate">
<param name="expression">day_of_week=weekday(today()); if(day_of_week>5)then("Weekend")else("Weekday")</param>
</tool>
```

### Sleep

The Sleep tool pauses execution for a specified amount of time. This is primarily useful for demonstration purposes or when timing operations.

```
<tool name="sleep">
<param name="duration_ms">2000</param>
</tool>
```

The `duration_ms` parameter specifies the sleep duration in milliseconds (default: 1000, maximum: 10000).

### Device Info

The Device Info tool returns basic information about the current device session.

```
<tool name="device_info">
</tool>
```

This tool doesn't require any parameters and returns information like device ID, OS version, and app session identifier.

## File System Tools

These tools provide access to the device's file system for reading, writing, and managing files.

> **Security Note**: All file paths must begin with `/sdcard/` for security reasons. Access to other parts of the file system is restricted.

### List Files

List files and directories in a specified directory.

```
<tool name="list_files">
<param name="path">/sdcard/Download</param>
</tool>
```

### Read File

Read the content of a text file.

```
<tool name="read_file">
<param name="path">/sdcard/Download/example.txt</param>
</tool>
```

### Write File

Write or append text to a file.

```
<tool name="write_file">
<param name="path">/sdcard/Download/example.txt</param>
<param name="content">Hello, world!</param>
<param name="append">false</param>
</tool>
```

The `append` parameter (default: false) controls whether to overwrite the file or append to it.

### Delete File

Delete a file or directory.

```
<tool name="delete_file">
<param name="path">/sdcard/Download/example.txt</param>
<param name="recursive">false</param>
</tool>
```

Set `recursive` to true to delete directories and their contents.

### File Exists

Check if a file or directory exists.

```
<tool name="file_exists">
<param name="path">/sdcard/Download/example.txt</param>
</tool>
```

### Move File

Move or rename a file or directory.

```
<tool name="move_file">
<param name="source">/sdcard/Download/example.txt</param>
<param name="destination">/sdcard/Download/new_name.txt</param>
</tool>
```

### Copy File

Copy a file or directory.

```
<tool name="copy_file">
<param name="source">/sdcard/Download/example.txt</param>
<param name="destination">/sdcard/Documents/example_copy.txt</param>
<param name="recursive">false</param>
</tool>
```

Set `recursive` to true to copy directories and their contents.

### Make Directory

Create a new directory.

```
<tool name="make_directory">
<param name="path">/sdcard/MyNewFolder</param>
<param name="create_parents">false</param>
</tool>
```

Set `create_parents` to true to create any missing parent directories.

### Find Files

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

Parameters:
- `pattern`: A glob pattern to match files (e.g., `*.jpg`, `document*.pdf`)
- `max_depth`: How deep to search in subdirectories (default: -1 for unlimited)
- `use_path_pattern`: Whether to match against full path or just filename (default: false)
- `case_insensitive`: Whether to ignore case when matching (default: false)

### File Info

Get detailed information about a file or directory.

```
<tool name="file_info">
<param name="path">/sdcard/Download/example.txt</param>
</tool>
```

Returns information like size, modification date, and file type.

### Zip Files

Compress files or directories into a ZIP archive.

```
<tool name="zip_files">
<param name="source">/sdcard/Download/MyFolder</param>
<param name="destination">/sdcard/Download/MyFolder.zip</param>
</tool>
```

### Unzip Files

Extract a ZIP archive.

```
<tool name="unzip_files">
<param name="source">/sdcard/Download/archive.zip</param>
<param name="destination">/sdcard/Download/extracted</param>
</tool>
```

### Open File

Open a file with the system's default application.

```
<tool name="open_file">
<param name="path">/sdcard/Download/document.pdf</param>
</tool>
```

### Share File

Share a file using the system's share interface.

```
<tool name="share_file">
<param name="path">/sdcard/Download/image.jpg</param>
<param name="title">Check out this image!</param>
</tool>
```

The `title` parameter (default: "Share File") sets the title for the share dialog.

### Download File

Download a file from a URL to local storage.

```
<tool name="download_file">
<param name="url">https://example.com/file.pdf</param>
<param name="destination">/sdcard/Download/file.pdf</param>
</tool>
```

## HTTP Tools

These tools enable the AI to interact with web resources.

### Fetch Web Page

Retrieve content from a web page.

```
<tool name="fetch_web_page">
<param name="url">https://example.com</param>
<param name="format">text</param>
</tool>
```

The `format` parameter (default: "text") can be:
- `text`: Returns the page content as plain text
- `html`: Returns the raw HTML content

### HTTP Request

Send custom HTTP requests with full control over method, headers, and body.

```
<tool name="http_request">
<param name="url">https://api.example.com/data</param>
<param name="method">POST</param>
<param name="headers">{"Content-Type": "application/json", "Authorization": "Bearer token123"}</param>
<param name="body">{"name": "Test", "value": 123}</param>
<param name="body_type">json</param>
</tool>
```

Parameters:
- `method`: HTTP method (default: GET, supports GET, POST, PUT, DELETE, HEAD, OPTIONS)
- `headers`: HTTP headers as a JSON object (optional)
- `body`: Request body (optional, for POST/PUT/DELETE)
- `body_type`: Body content type (default: "json", supports "json", "form", "text")

### Web Search

Perform web searches and get results.

```
<tool name="web_search">
<param name="query">Latest Android features</param>
</tool>
```

Returns search results including titles, snippets, and URLs.

## System Operation Tools

These tools provide access to system-level operations. They require user authorization.

### Get System Setting

Retrieve the value of a system setting.

```
<tool name="get_system_setting">
<param name="setting">screen_brightness</param>
<param name="namespace">system</param>
</tool>
```

The `namespace` parameter (default: "system") can be "system", "secure", or "global".

### Modify System Setting

Change the value of a system setting.

```
<tool name="modify_system_setting">
<param name="setting">screen_brightness</param>
<param name="value">100</param>
<param name="namespace">system</param>
</tool>
```

### Install App

Install an application from an APK file.

```
<tool name="install_app">
<param name="apk_path">/sdcard/Download/app.apk</param>
</tool>
```

### Uninstall App

Uninstall an application.

```
<tool name="uninstall_app">
<param name="package_name">com.example.app</param>
<param name="keep_data">false</param>
</tool>
```

The `keep_data` parameter (default: false) controls whether to preserve app data.

### List Installed Apps

Get a list of installed applications.

```
<tool name="list_installed_apps">
<param name="include_system_apps">false</param>
</tool>
```

Set `include_system_apps` to true to include system applications.

### Start App

Launch an application.

```
<tool name="start_app">
<param name="package_name">com.example.app</param>
<param name="activity">com.example.app.MainActivity</param>
</tool>
```

The `activity` parameter (optional) specifies a specific activity to launch.

### Stop App

Stop a running application.

```
<tool name="stop_app">
<param name="package_name">com.example.app</param>
</tool>
```

## UI Automation Tools

These tools enable the AI to interact with the device's user interface through accessibility services.

### Get Page Info

Get information about the current UI screen.

```
<tool name="get_page_info">
<param name="format">xml</param>
<param name="detail">summary</param>
</tool>
```

Parameters:
- `format`: Output format (default: "xml", supports "xml", "json")
- `detail`: Level of detail (default: "summary", supports "minimal", "summary", "full")

### Tap

Simulate a tap at specific coordinates.

```
<tool name="tap">
<param name="x">500</param>
<param name="y">800</param>
</tool>
```

### Click Element

Click an element identified by resource ID or class name.

```
<tool name="click_element">
<param name="resourceId">com.example.app:id/button</param>
<param name="className">android.widget.Button</param>
<param name="index">0</param>
<param name="partialMatch">false</param>
</tool>
```

Parameters:
- `resourceId`: Element's resource ID (optional if className is provided)
- `className`: Element's class name (optional if resourceId is provided)
- `index`: Which matching element to click (default: 0, for multiple matches)
- `partialMatch`: Whether to allow partial ID matching (default: false)

### Set Input Text

Set text in an input field.

```
<tool name="set_input_text">
<param name="text">Hello, world!</param>
</tool>
```

### Press Key

Simulate a key press.

```
<tool name="press_key">
<param name="keyCode">KEYCODE_BACK</param>
</tool>
```

Common key codes: KEYCODE_BACK, KEYCODE_HOME, KEYCODE_MENU, KEYCODE_SEARCH.

### Swipe

Simulate a swipe gesture.

```
<tool name="swipe">
<param name="startX">500</param>
<param name="startY">1000</param>
<param name="endX">500</param>
<param name="endY">200</param>
<param name="duration">300</param>
</tool>
```

The `duration` parameter (default: 300) specifies the gesture duration in milliseconds.


### Combined Operation

Execute a UI operation, wait, and return the new UI state.

```
<tool name="combined_operation">
<param name="operation">tap 500 800</param>
<param name="delayMs">1000</param>
</tool>
```

Parameters:
- `operation`: The UI operation to perform (e.g., "tap 500 800", "click_element resourceId buttonId")
- `delayMs`: The delay before capturing the new UI state (default: 1000)

### Best Practices for UI Automation

1. **First Check UI State**: Always get the current UI state with `get_page_info` before attempting interactions.

2. **Use Combined Operation**: Whenever possible, use `combined_operation` instead of individual operations, as it handles waiting and returns the new UI state automatically.

3. **Handle Various UI Elements**:
   - For lists: Use the `index` parameter to click specific items
   - For ambiguous elements: Use more specific identifiers or coordinates
   - For complex flows: Chain operations with appropriate delays

4. **Fallback Approaches**:
   - When elements can't be identified by ID or class, use coordinate-based interactions
   - When elements are dynamically generated, use partial matching

5. **App Launch Best Practice**: When launching apps, use `combined_operation` to immediately capture the UI state after launch.

## Extending the Tool Framework

### Adding Custom Tools

To add a custom tool to the framework:

1. Implement your tool executor in `AIToolHandler`:

```kotlin
registerTool("my_custom_tool") { tool ->
    val param1 = tool.parameters.find { it.name == "param1" }?.value ?: ""
    
    // Your tool implementation
    val result = performCustomOperation(param1)
    
    ToolResult(
        toolName = tool.name,
        success = true,
        result = result
    )
}
```

2. Update the system prompt in `EnhancedAIService` to document your tool:

```kotlin
private val SYSTEM_PROMPT = """
    ...existing prompt...
    
    Available tools:
    ...existing tools...
    - my_custom_tool: Description of your custom tool. Parameters: param1 (description)
    
    ...rest of prompt...
""".trimIndent()
```

## Architecture

The tool functionality is implemented through several key components:

### Core Components

- **AITool.kt**: Data models for tools, parameters, invocations, and results
- **AIToolHandler.kt**: Core class for tool extraction and execution
- **EnhancedAIService.kt**: Main service integrating AI capabilities with tools

### UI Components

- **ToolProgressBar.kt**: Shows tool execution progress
- **ReferencesDisplay.kt**: Displays references from AI responses

### Specialized Tool Implementations

- **Calculator.kt**: Mathematical and date calculation engine
- **FileSystemTools.kt**: File operations
- **HttpTools.kt**: Web interactions
- **SystemOperationTools.kt**: System-level operations

### Execution Flow

1. User sends a request to the AI
2. AI identifies a need for a tool and invokes it
3. Tool handler extracts and validates the tool invocation
4. Tool executor performs the operation
5. Result is returned to the AI
6. AI incorporates the result into its response

## Troubleshooting

### Common Issues

1. **Tool Not Found**: Ensure the tool name is correct and registered in `AIToolHandler`.

2. **Parameter Errors**: Check that all required parameters are provided with correct values.

3. **Permission Issues**: Many tools require specific permissions. Check that the app has the necessary permissions and the user has granted them.

4. **File Path Issues**: All file paths must begin with `/sdcard/`. Trying to access other paths will fail.

5. **UI Automation Failures**: UI elements may change between app versions. Verify element IDs and try using more generic approaches like coordinate-based tapping.

### Debugging

For detailed logging, check logcat with these tags:
- `AIToolHandler`: Tool extraction and execution
- `EnhancedAIService`: Overall service operation
- Specific tool tags like `Calculator`, `FileSystemTools`, etc.

---

For further assistance or to report issues, please contact the development team.

## Appendix: Example Interactions

### Calculator Tool Usage Examples

Here are some example user queries that effectively trigger the calculator tool:

#### Mathematical Calculations
**User**: "What is the square root of 169 multiplied by 3?"  
**AI uses**: `calculate` with expression `sqrt(169)*3`

#### Date Calculations
**User**: "How many days are there between March 15, 2023 and November 28, 2023?"  
**AI uses**: `calculate` with expression `date_diff(date("2023-03-15"), date("2023-11-28"))`

**User**: "What day of the week will Christmas be this year?"  
**AI uses**: `calculate` with expression `weekday(date("2023-12-25"))`

#### Unit Conversions
**User**: "I need to convert 26.2 miles to kilometers for my marathon training."  
**AI uses**: `calculate` with expression `convert(26.2, mi, km)`

**User**: "What's 350 degrees Fahrenheit in Celsius for my baking recipe?"  
**AI uses**: `calculate` with expression `convert(350, f, c)`

#### Statistical Analysis
**User**: "Calculate the average and standard deviation of these test scores: 85, 92, 78, 90, 88."  
**AI uses**:  
- `calculate` with expression `stats.mean(85,92,78,90,88)`
- `calculate` with expression `stats.stdev(85,92,78,90,88)`

#### Financial Calculations
**User**: "If I invest $10,000 at 7% annual interest compounded annually, how much will I have after 10 years?"  
**AI uses**: `calculate` with expression `10000*(1+0.07)^10`

**User**: "Calculate a 15% discount on a $120 purchase."  
**AI uses**: `calculate` with expression `120*(1-0.15)` or `120-120*0.15`

#### Conditional Logic
**User**: "Is 42.5 closer to 40 or to 50?"  
**AI uses**: `calculate` with expression `if(abs(42.5-40)<abs(42.5-50))then("40")else("50")`

These examples demonstrate how specific, clear questions about calculations naturally lead the AI to use the calculator tool rather than explaining steps or using approximate answers. 