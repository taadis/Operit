/**
 * TypeScript definitions for Assistance Package Tools
 * 
 * This file provides type definitions for the JavaScript environment
 * available in package tools execution.
 */

/**
 * Tool call parameters object
 */
interface ToolParams {
    [key: string]: string | number | boolean | object;
}

/**
 * Tool configuration for object-style calls
 */
interface ToolConfig {
    type?: string;
    name: string;
    params?: ToolParams;
}

/**
 * Global function to call a tool and get a result
 * @returns A Promise with the tool result
 */
declare function toolCall(toolType: string, toolName: string, toolParams?: ToolParams): Promise<any>;
declare function toolCall(toolName: string, toolParams?: ToolParams): Promise<any>;
declare function toolCall(config: ToolConfig): Promise<any>;
declare function toolCall(toolName: string): Promise<any>;

/**
 * Global function to complete tool execution with a result
 * @param result - The result to return
 */
declare function complete(result: any): void;

/**
 * Global variable containing various useful utility functions
 */
declare namespace Tools {
    /**
     * File operations
     */
    namespace Files {
        /**
         * List files in a directory
         * @param path - Path to directory
         */
        function list(path: string): Promise<string>;
        
        /**
         * Read file contents
         * @param path - Path to file
         */
        function read(path: string): Promise<string>;
        
        /**
         * Write content to file
         * @param path - Path to file
         * @param content - Content to write
         */
        function write(path: string, content: string): Promise<string>;
        
        /**
         * Delete a file or directory
         * @param path - Path to file or directory
         */
        function deleteFile(path: string): Promise<string>;
        
        /**
         * Check if file exists
         * @param path - Path to check
         */
        function exists(path: string): Promise<string>;
        
        /**
         * Move file from source to target
         * @param source - Source path
         * @param target - Target path
         */
        function move(source: string, target: string): Promise<string>;
        
        /**
         * Copy file from source to target
         * @param source - Source path
         * @param target - Target path
         */
        function copy(source: string, target: string): Promise<string>;
        
        /**
         * Create a directory
         * @param path - Directory path
         */
        function mkdir(path: string): Promise<string>;
        
        /**
         * Find files matching a pattern
         * @param path - Base directory
         * @param pattern - Search pattern
         */
        function find(path: string, pattern: string): Promise<string>;
        
        /**
         * Get information about a file
         * @param path - File path
         */
        function info(path: string): Promise<string>;
        
        /**
         * Zip files/directories
         * @param source - Source path
         * @param target - Target path
         */
        function zip(source: string, target: string): Promise<string>;
        
        /**
         * Unzip an archive
         * @param source - Source archive
         * @param target - Target directory
         */
        function unzip(source: string, target: string): Promise<string>;
        
        /**
         * Open a file with system handler
         * @param path - File path
         */
        function open(path: string): Promise<string>;
    }
    
    /**
     * Network operations
     */
    namespace Net {
        /**
         * Perform HTTP GET request
         * @param url - URL to request
         */
        function httpGet(url: string): Promise<string>;
        
        /**
         * Perform HTTP POST request
         * @param url - URL to request
         * @param data - Data to post
         */
        function httpPost(url: string, data: string | object): Promise<string>;
        
        /**
         * Perform web search
         * @param query - Search query
         */
        function search(query: string): Promise<string>;
    }
    
    /**
     * System operations
     */
    namespace System {
        /**
         * Execute a system command
         * @param command - Command to execute
         */
        function exec(command: string): Promise<string>;
        
        /**
         * Sleep for specified seconds
         * @param seconds - Seconds to sleep
         */
        function sleep(seconds: string | number): Promise<string>;
    }
    
    /**
     * Calculate mathematical expression
     * @param expression - Expression to calculate
     */
    function calc(expression: string): Promise<string>;
}

/**
 * Lodash-like utility library
 */
declare const _: {
    isEmpty(value: any): boolean;
    isString(value: any): boolean;
    isNumber(value: any): boolean;
    isBoolean(value: any): boolean;
    isObject(value: any): boolean;
    isArray(value: any): boolean;
    forEach<T>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => void): any;
    map<T, R>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => R): R[];
};

/**
 * Data utilities
 */
declare const dataUtils: {
    /**
     * Parse JSON string to object
     * @param jsonString - JSON string to parse
     */
    parseJson(jsonString: string): any;
    
    /**
     * Convert object to JSON string
     * @param obj - Object to stringify
     */
    stringifyJson(obj: any): string;
    
    /**
     * Format date to string
     * @param date - Date to format
     */
    formatDate(date?: Date | string): string;
};

/**
 * Native interface for direct calls to Android
 */
declare namespace NativeInterface {
    /**
     * Call a tool synchronously (legacy method)
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     */
    function callTool(toolType: string, toolName: string, paramsJson: string): string;
    
    /**
     * Call a tool asynchronously
     * @param callbackId - Unique callback ID
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     */
    function callToolAsync(callbackId: string, toolType: string, toolName: string, paramsJson: string): void;
    
    /**
     * Set the result of script execution
     * @param result - Result string
     */
    function setResult(result: string): void;
    
    /**
     * Set an error for script execution
     * @param error - Error message
     */
    function setError(error: string): void;
}

/**
 * Module exports object for CommonJS-style exports
 */
declare var exports: {
    [key: string]: any;
}; 