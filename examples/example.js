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
            searchData: searchResult.toString().substring(0, 200) + "...", // Truncate for brevity
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
exports.chain_tools = chain_tools;
exports.main = async () => {
    let result = await hello_world({ name: "TypeScript" });
    console.log(result);
    result = await chain_tools({ query: "TypeScript" });
    console.log(result);
    complete("All operations completed successfully");
};
