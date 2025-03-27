/*
METADATA
{
    "name": "typescript_example",
    "description": "A TypeScript example package with Promise-based tools",
    "tools": [
        {
            "name": "hello_world",
            "description": "A simple hello world tool that demonstrates TypeScript and Promise-based API",
            "parameters": [
                {
                    "name": "name",
                    "description": "Name to greet",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "file_explorer",
            "description": "Lists files in a directory and reads text files using async/await",
            "parameters": [
                {
                    "name": "path",
                    "description": "Path to explore",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "chain_tools",
            "description": "Demonstrates chaining multiple tool calls using Promises",
            "parameters": [
                {
                    "name": "query",
                    "description": "Search query",
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
 * Simple hello world function that demonstrates TypeScript and Promise-based API
 * @param params Tool parameters with name property
 */
async function hello_world(params) {
    try {
        // Access parameters with type checking
        const name = params.name || "World";
        // Sleep for a moment to demonstrate async behavior
        await Tools.System.sleep(1);
        // Format message with simple template
        const message = `Hello, ${name}! This message was generated using TypeScript.
Current time: ${dataUtils.formatDate(new Date())}
This is running in a WebView-based JavaScript environment with Promise support.`;
        // Complete the function with a result
        complete({ message });
    }
    catch (error) {
        // Handle errors properly
        complete(`Error in hello_world: ${error.message}`);
    }
}
/**
 * List files in a directory and read text files
 * @param params Tool parameters with path property
 */
async function file_explorer(params) {
    try {
        const path = params.path || "/";
        // Get file list using await syntax
        const fileListResult = await Tools.Files.list(path);
        // Parse the result as JSON
        const fileList = JSON.parse(fileListResult);
        // Build a formatted response
        let response = `# Files in ${path}\n\n`;
        if (Array.isArray(fileList)) {
            // Add file information
            for (const file of fileList) {
                if (typeof file === 'object' && file !== null) {
                    const isDirectory = file.isDirectory || false;
                    const name = file.name || "Unknown";
                    const size = file.size || 0;
                    response += `- ${isDirectory ? "📁" : "📄"} **${name}**`;
                    if (!isDirectory) {
                        response += ` (${formatFileSize(size)})`;
                    }
                    response += "\n";
                }
            }
            // Try to read the first text file
            const textFiles = fileList.filter(file => !file.isDirectory &&
                (file.name.endsWith(".txt") ||
                    file.name.endsWith(".md") ||
                    file.name.endsWith(".json") ||
                    file.name.endsWith(".js") ||
                    file.name.endsWith(".ts")));
            if (textFiles.length > 0) {
                const firstFile = textFiles[0];
                response += "\n## Preview of " + firstFile.name + "\n\n";
                try {
                    const filePath = path + "/" + firstFile.name;
                    const content = await Tools.Files.read(filePath);
                    // Truncate long content
                    const preview = content.length > 500
                        ? content.substring(0, 500) + "...\n[Content truncated]"
                        : content;
                    response += "```\n" + preview + "\n```";
                }
                catch (readError) {
                    response += "Error reading file: " + readError.message;
                }
            }
        }
        else {
            response += "Error: Could not parse file list result.";
        }
        complete(response);
    }
    catch (error) {
        complete(`Error exploring files: ${error.message}`);
    }
}
/**
 * Format file size in human-readable format
 */
function formatFileSize(bytes) {
    if (bytes < 1024)
        return bytes + " B";
    if (bytes < 1024 * 1024)
        return (bytes / 1024).toFixed(2) + " KB";
    if (bytes < 1024 * 1024 * 1024)
        return (bytes / (1024 * 1024)).toFixed(2) + " MB";
    return (bytes / (1024 * 1024 * 1024)).toFixed(2) + " GB";
}
/**
 * Chain multiple tool calls with Promises
 * @param params Tool parameters with query property
 */
async function chain_tools(params) {
    try {
        const query = params.query || "JavaScript";
        // Start with a simple calculation
        const calcResult = await Tools.calc("42 * 3");
        // Chain to a web search
        const searchResult = await Tools.Net.search(query);
        // Chain to a sleep operation
        await Tools.System.sleep(1);
        // Prepare final result
        const result = {
            calculation: calcResult,
            searchSummary: `Found results for "${query}"`,
            searchData: searchResult.substring(0, 200) + "...", // Truncate for brevity
            timestamp: Date.now()
        };
        complete(result);
    }
    catch (error) {
        complete(`Error in chain_tools: ${error.message}`);
    }
}
// Export functions for direct access
exports.hello_world = hello_world;
exports.file_explorer = file_explorer;
exports.chain_tools = chain_tools;
