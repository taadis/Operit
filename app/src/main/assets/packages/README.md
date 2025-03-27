# Assistance Tool Packages

This directory contains tool packages that can be loaded by the AI assistant. These packages provide additional functionality to the assistant, allowing it to perform specialized tasks.

## Package Formats

Packages can be defined in two formats:

1. **JavaScript (.js)** - Preferred format with full support for Promise-based async execution
2. **HJSON (.hjson)** - Legacy format with limited functionality (maintained for backward compatibility)

## JavaScript Package Structure

A JavaScript package consists of a single JavaScript file with:

1. A **metadata block** at the top of the file
2. **Tool function definitions** that implement the functionality
3. Optional **exports** statements to make the functions available

### Example JavaScript Package:

```javascript
/*
METADATA
{
    "name": "example_package",
    "description": "An example package showing how to create tools",
    "tools": [
        {
            "name": "hello_world",
            "description": "A simple hello world tool",
            "parameters": [
                {
                    "name": "name",
                    "description": "Name to greet",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ],
    "category": "FILE_READ"
}
*/

// Tool implementation
async function hello_world(params) {
    // Access parameters
    const name = params.name || "World";
    
    // Use async/await with other tools
    await Tools.System.sleep(1);
    
    // Return a result
    complete(`Hello, ${name}!`);
}

// Export the function
exports.hello_world = hello_world;
```

## TypeScript Support

You can also write your packages in TypeScript (.ts) with full type checking for all APIs. TypeScript packages are automatically compiled to JavaScript at runtime.

### Example TypeScript Package:

```typescript
/*
METADATA
{
    "name": "typescript_example",
    "description": "A TypeScript example package",
    "tools": [
        {
            "name": "hello_world",
            "description": "A simple hello world tool with TypeScript",
            "parameters": [
                {
                    "name": "name",
                    "description": "Name to greet",
                    "type": "string",
                    "required": true
                }
            ]
        }
    ],
    "category": "FILE_READ"
}
*/

/**
 * Simple hello world function with TypeScript
 * @param params Tool parameters with name property
 */
async function hello_world(params: { name: string }): Promise<void> {
    // Access parameters with type checking
    const name = params.name || "World";
    
    // Use async/await with proper typing
    await Tools.System.sleep(1);
    
    // Return a result
    complete(`Hello, ${name}!`);
}

// Export the function
exports.hello_world = hello_world;
```

## Promise-Based Async API

All tool calls now return Promises, allowing for async/await pattern usage:

```javascript
async function chain_example(params) {
    try {
        // Chain multiple tool calls with await
        const fileList = await Tools.Files.list("/some/path");
        const fileContents = await Tools.Files.read("/some/path/file.txt");
        
        // Do work with the results
        const result = `Found ${fileList.length} files. First file contents: ${fileContents}`;
        
        complete(result);
    } catch (error) {
        complete(`Error: ${error.message}`);
    }
}
```

## Available Tools API

The following utility namespaces are available in all package scripts:

### Tools.Files
- `list(path)` - List files in a directory
- `read(path)` - Read file contents
- `write(path, content)` - Write content to file
- `deleteFile(path)` - Delete a file or directory
- `exists(path)` - Check if file exists
- `move(source, target)` - Move file from source to target
- `copy(source, target)` - Copy file from source to target
- `mkdir(path)` - Create a directory
- `find(path, pattern)` - Find files matching a pattern
- `info(path)` - Get information about a file
- `zip(source, target)` - Zip files/directories
- `unzip(source, target)` - Unzip an archive
- `open(path)` - Open a file with system handler

### Tools.Net
- `httpGet(url)` - Perform HTTP GET request
- `httpPost(url, data)` - Perform HTTP POST request
- `search(query)` - Perform web search

### Tools.System
- `exec(command)` - Execute a system command
- `sleep(seconds)` - Sleep for specified seconds

### Other Utilities
- `Tools.calc(expression)` - Calculate mathematical expression
- `_` - Lodash-like utility library
- `dataUtils` - Data processing utilities

## Creating a New Package

1. Create a new `.js` or `.ts` file in this directory
2. Add a METADATA block at the top with package details
3. Define your tool functions
4. Export the functions using the CommonJS pattern
5. Use the Promise-based API for any asynchronous operations

## Testing Your Package

You can test your package by using the `use_package` tool in the assistant chat:

```
<tool name="use_package">
  <param name="package_name">your_package_name</param>
</tool>
```

After the package is loaded, you can use any of its tools:

```
<tool name="your_package_name:your_tool_name">
  <param name="param1">value1</param>
</tool>
``` 