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
 * Common result interfaces for structured data
 */
interface BaseResult {
    success: boolean;
    error?: string;
}

/**
 * Basic result data types
 */
interface StringResult extends BaseResult {
    data: string;
    toString(): string;
}

interface BooleanResult extends BaseResult {
    data: boolean;
    toString(): string;
}

interface NumberResult extends BaseResult {
    data: number;
    toString(): string;
}

// ============================================================================
// Calculation and Date Result Types
// ============================================================================

/**
 * Calculation result data
 */
interface CalculationResultData {
    expression: string;
    result: number;
    formattedResult: string;
    variables: Record<string, number>;
    toString(): string;
}

// ============================================================================
// Connection Result Types
// ============================================================================

/**
 * Connection result data
 */
interface ConnectionResultData {
    connectionId: string;
    isActive: boolean;
    timestamp: number;
    toString(): string;
}

// ============================================================================
// File Operation Types
// ============================================================================

/**
 * File entry in directory listing
 */
interface FileEntry {
    name: string;
    isDirectory: boolean;
    size: number;
    permissions: string;
    lastModified: string;
    toString(): string;
}

interface FileExistsData {
    path: string;
    exists: boolean;
    isDirectory?: boolean;
    size?: number;
}

/**
 * Detailed file information data
 */
interface FileInfoData {
    path: string;
    exists: boolean;
    fileType: string;  // "file", "directory", or "other"
    size: number;
    permissions: string;
    owner: string;
    group: string;
    lastModified: string;
    rawStatOutput: string;
}

/**
 * Directory listing data
 */
interface DirectoryListingData {
    path: string;
    entries: FileEntry[];
    toString(): string;
}

/**
 * File content data
 */
interface FileContentData {
    path: string;
    content: string;
    size: number;
    toString(): string;
}

/**
 * File operation data
 */
interface FileOperationData {
    operation: string;
    path: string;
    successful: boolean;
    details: string;
    toString(): string;
}

/**
 * Find files result data
 */
interface FindFilesResultData {
    path: string;
    pattern: string;
    files: string[];
    toString(): string;
}

// ============================================================================
// HTTP and Network Types
// ============================================================================

/**
 * HTTP response data
 */
interface HttpResponseData {
    url: string;
    statusCode: number;
    statusMessage: string;
    headers: Record<string, string>;
    contentType: string;
    content: string;
    contentSummary: string;
    size: number;
    toString(): string;
}

/**
 * Web page link
 */
interface Link {
    text: string;
    url: string;
    toString(): string;
}

/**
 * Web page data
 */
interface WebPageData {
    url: string;
    title: string;
    contentType: string;
    content: string;
    textContent: string;
    size: number;
    links: Link[];
    toString(): string;
}

/**
 * Web search result
 */
interface SearchResult {
    title: string;
    url: string;
    snippet: string;
    toString(): string;
}

/**
 * Device information result data
 */
interface DeviceInfoResultData {
    deviceId: string;
    model: string;
    manufacturer: string;
    androidVersion: string;
    sdkVersion: number;
    screenResolution: string;
    screenDensity: number;
    totalMemory: string;
    availableMemory: string;
    totalStorage: string;
    availableStorage: string;
    batteryLevel: number;
    batteryCharging: boolean;
    cpuInfo: string;
    networkType: string;
    additionalInfo: Record<string, string>;
    toString(): string;
}

/**
 * Web search result data
 */
interface WebSearchResultData {
    query: string;
    results: SearchResult[];
    toString(): string;
}

// ============================================================================
// System Settings and App Types
// ============================================================================

/**
 * Sleep result data
 */
interface SleepResultData {
    sleptMs: number;
    requestedMs: number;
    toString(): string;
}

/**
 * System setting data
 */
interface SystemSettingData {
    namespace: string;
    setting: string;
    value: string;
    toString(): string;
}

/**
 * App operation data
 */
interface AppOperationData {
    operationType: string;
    packageName: string;
    success: boolean;
    details: string;
    toString(): string;
}

/**
 * App list data
 */
interface AppListData {
    includesSystemApps: boolean;
    packages: string[];
    toString(): string;
}

// ============================================================================
// UI Types
// ============================================================================

/**
 * UI node structure for hierarchical display
 */
interface SimplifiedUINode {
    className?: string;
    text?: string;
    contentDesc?: string;
    resourceId?: string;
    bounds?: string;
    isClickable: boolean;
    children: SimplifiedUINode[];
    toString(): string;
    toTreeString(indent?: string): string;
}

/**
 * UI page result data
 */
interface UIPageResultData {
    packageName: string;
    activityName: string;
    uiElements: SimplifiedUINode;
    toString(): string;
}

/**
 * UI action result data
 */
interface UIActionResultData {
    actionType: string;
    actionDescription: string;
    coordinates?: [number, number];
    elementId?: string;
    toString(): string;
}

/**
 * Combined operation result data
 */
interface CombinedOperationResultData {
    operationSummary: string;
    waitTime: number;
    pageInfo: UIPageResultData;
    toString(): string;
}

interface CombinedOperationResult extends BaseResult {
    data: CombinedOperationResultData;
}

interface DeviceInfoResult extends BaseResult {
    data: DeviceInfoResultData;
}

// ============================================================================
// Result Type Wrappers
// ============================================================================

interface CalculationResult extends BaseResult {
    data: CalculationResultData;
}

interface DateResult extends BaseResult {
    data: DateResultData;
}

interface ConnectionResult extends BaseResult {
    data: ConnectionResultData;
}

interface DirectoryListingResult extends BaseResult {
    data: DirectoryListingData;
}

interface FileContentResult extends BaseResult {
    data: FileContentData;
}

interface FileOperationResult extends BaseResult {
    data: FileOperationData;
}

interface HttpResponseResult extends BaseResult {
    data: HttpResponseData;
}

interface WebPageResult extends BaseResult {
    data: WebPageData;
}

interface WebSearchResult extends BaseResult {
    data: WebSearchResultData;
}

interface SystemSettingResult extends BaseResult {
    data: SystemSettingData;
}

interface AppOperationResult extends BaseResult {
    data: AppOperationData;
}

interface AppListResult extends BaseResult {
    data: AppListData;
}

interface UIPageResult extends BaseResult {
    data: UIPageResultData;
}

interface UIActionResult extends BaseResult {
    data: UIActionResultData;
}

/**
 * Generic tool result type
 */
type ToolResult = StringResult | BooleanResult | NumberResult |
    CalculationResult | DateResult | ConnectionResult |
    DirectoryListingResult | FileContentResult | FileOperationResult |
    HttpResponseResult | WebPageResult | WebSearchResult |
    SystemSettingResult | AppOperationResult | AppListResult |
    UIPageResult | UIActionResult | CombinedOperationResult | DeviceInfoResult |
    (BaseResult & { data: any });

// ============================================================================
// Tool Name Types
// ============================================================================

/**
 * File tool names
 */
type FileToolName = 'list_files' | 'read_file' | 'write_file' | 'delete_file' | 'file_exists' |
    'move_file' | 'copy_file' | 'make_directory' | 'find_files' | 'file_info' |
    'zip_files' | 'unzip_files' | 'open_file' | 'share_file' | 'download_file';

/**
 * Network tool names
 */
type NetToolName = 'http_request' | 'web_search' | 'fetch_web_page';

/**
 * System tool names
 */
type SystemToolName = 'sleep' | 'get_system_setting' | 'modify_system_setting' |
    'install_app' | 'uninstall_app' | 'list_installed_apps' | 'start_app' | 'stop_app' |
    'device_info';

/**
 * UI tool names
 */
type UiToolName = 'get_page_info' | 'click_element' | 'tap' | 'set_input_text' | 'press_key' |
    'swipe' | 'combined_operation';

/**
 * Calculator tool names
 */
type CalculatorToolName = 'calculate';

/**
 * Connection tool names
 */
type ConnectionToolName = 'establish_connection';

/**
 * Package tool names
 */
type PackageToolName = 'use_package' | 'query_problem_library';

/**
 * All tool names
 */
type ToolName = FileToolName | NetToolName | SystemToolName | UiToolName |
    CalculatorToolName | ConnectionToolName | PackageToolName | string;

/**
 * Maps tool names to their result data types
 */
interface ToolResultMap {
    // File operations
    'list_files': DirectoryListingData;
    'read_file': FileContentData;
    'write_file': FileOperationData;
    'delete_file': FileOperationData;
    'file_exists': FileExistsData;
    'move_file': FileOperationData;
    'copy_file': FileOperationData;
    'make_directory': FileOperationData;
    'find_files': FindFilesResultData;
    'file_info': FileInfoData;
    'zip_files': FileOperationData;
    'unzip_files': FileOperationData;
    'open_file': FileOperationData;
    'share_file': FileOperationData;
    'download_file': FileOperationData;

    // Network operations
    'http_request': HttpResponseData;
    'web_search': WebSearchResultData;
    'fetch_web_page': WebPageData;

    // System operations
    'sleep': SleepResultData;
    'get_system_setting': SystemSettingData;
    'modify_system_setting': SystemSettingData;
    'install_app': AppOperationData;
    'uninstall_app': AppOperationData;
    'list_installed_apps': AppListData;
    'start_app': AppOperationData;
    'stop_app': AppOperationData;
    'device_info': DeviceInfoResultData;

    // UI operations
    'get_page_info': UIPageResultData;
    'click_element': UIActionResultData;
    'tap': UIActionResultData;
    'set_input_text': UIActionResultData;
    'press_key': UIActionResultData;
    'swipe': UIActionResultData;
    'combined_operation': CombinedOperationResultData;

    // Calculator operations
    'calculate': CalculationResultData;

    // Package operations
    'use_package': string;
    'query_problem_library': string;
}

/**
 * Get return type for a specific tool name
 */
type ToolReturnType<T extends ToolName> = T extends keyof ToolResultMap
    ? ToolResultMap[T]
    : any;

// ============================================================================
// Tool Call Function Declarations
// ============================================================================

/**
 * Global function to call a tool and get a result
 * @returns A Promise with the tool result data of the appropriate type
 */
declare function toolCall<T extends ToolName>(toolType: string, toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
declare function toolCall<T extends ToolName>(toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
declare function toolCall<T extends ToolName>(config: ToolConfig & { name: T }): Promise<ToolReturnType<T>>;
declare function toolCall(toolName: string): Promise<any>;

/**
 * Global function to complete tool execution with a result
 * @param result - The result to return
 */
declare function complete<T>(result: T): void;

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
        function list(path: string): Promise<DirectoryListingData>;

        /**
         * Read file contents
         * @param path - Path to file
         */
        function read(path: string): Promise<FileContentData>;

        /**
         * Write content to file
         * @param path - Path to file
         * @param content - Content to write
         */
        function write(path: string, content: string): Promise<FileOperationData>;

        /**
         * Delete a file or directory
         * @param path - Path to file or directory
         */
        function deleteFile(path: string): Promise<FileOperationData>;

        /**
         * Check if file exists
         * @param path - Path to check
         */
        function exists(path: string): Promise<FileExistsData>;

        /**
         * Move file from source to destination
         * @param source - Source path
         * @param destination - Destination path
         */
        function move(source: string, destination: string): Promise<FileOperationData>;

        /**
         * Copy file from source to destination
         * @param source - Source path
         * @param destination - Destination path
         */
        function copy(source: string, destination: string): Promise<FileOperationData>;

        /**
         * Create a directory
         * @param path - Directory path
         */
        function mkdir(path: string): Promise<FileOperationData>;

        /**
         * Find files matching a pattern
         * @param path - Base directory
         * @param pattern - Search pattern
         */
        function find(path: string, pattern: string): Promise<FindFilesResultData>;

        /**
         * Get information about a file
         * @param path - File path
         */
        function info(path: string): Promise<FileOperationData>;

        /**
         * Zip files/directories
         * @param source - Source path
         * @param destination - Destination path
         */
        function zip(source: string, destination: string): Promise<FileOperationData>;

        /**
         * Unzip an archive
         * @param source - Source archive
         * @param destination - Target directory
         */
        function unzip(source: string, destination: string): Promise<FileOperationData>;

        /**
         * Open a file with system handler
         * @param path - File path
         */
        function open(path: string): Promise<FileOperationData>;

        /**
         * Share a file with other apps
         * @param path - File path
         */
        function share(path: string): Promise<FileOperationData>;

        /**
         * Download a file from URL
         * @param url - Source URL
         * @param destination - Destination path
         */
        function download(url: string, destination: string): Promise<FileOperationData>;
    }

    /**
     * Network operations
     */
    namespace Net {
        /**
         * Perform HTTP GET request
         * @param url - URL to request
         */
        function httpGet(url: string): Promise<HttpResponseData>;

        /**
         * Perform HTTP POST request
         * @param url - URL to request
         * @param data - Data to post
         */
        function httpPost(url: string, data: string | object): Promise<HttpResponseData>;

        /**
         * Perform web search
         * @param query - Search query
         */
        function search(query: string): Promise<WebSearchResultData>;

        /**
         * Fetch a web page
         * @param url - URL to fetch
         */
        function fetchPage(url: string): Promise<WebPageData>;
    }

    /**
     * System operations
     */
    namespace System {
        /**
         * Sleep for specified seconds
         * @param seconds - Seconds to sleep
         */
        function sleep(seconds: string | number): Promise<SleepResultData>;

        /**
         * Get a system setting
         * @param setting - Setting name
         * @param namespace - Setting namespace
         */
        function getSetting(setting: string, namespace?: string): Promise<SystemSettingData>;

        /**
         * Modify a system setting
         * @param setting - Setting name
         * @param value - New value
         * @param namespace - Setting namespace
         */
        function setSetting(setting: string, value: string, namespace?: string): Promise<SystemSettingData>;

        /**
         * Get device information
         */
        function getDeviceInfo(): Promise<DeviceInfoResultData>;

        /**
         * Launch an app by package name
         * @param packageName - Package name
         * @param activity - Optional specific activity to launch
         */
        function launchApp(packageName: string, activity?: string): Promise<AppOperationData>;

        /**
         * Stop a running app
         * @param packageName - Package name
         */
        function stopApp(packageName: string): Promise<AppOperationData>;

        /**
         * List installed apps
         * @param includeSystem - Whether to include system apps
         */
        function listApps(includeSystem?: boolean): Promise<AppListData>;

        /**
         * Start an app by package name
         * @param packageName - Package name
         * @param activity - Optional specific activity to launch
         */
        function startApp(packageName: string, activity?: string): Promise<AppOperationData>;
    }

    /**
     * UI operations
     */
    namespace UI {
        /**
         * Get current page information
         */
        function getPageInfo(): Promise<UIPageResultData>;

        /**
         * Tap at coordinates
         * @param x - X coordinate
         * @param y - Y coordinate
         */
        function tap(x: number, y: number): Promise<UIActionResultData>;

        /**
         * Click on an element
         * @param elementId - Element ID or selector
         */
        function clickElement(elementId: string): Promise<UIActionResultData>;

        /**
         * Set text in input field
         * @param elementId - Element ID or selector
         * @param text - Text to input
         */
        function setText(elementId: string, text: string): Promise<UIActionResultData>;

        /**
         * Swipe from one position to another
         * @param startX - Start X coordinate
         * @param startY - Start Y coordinate
         * @param endX - End X coordinate
         * @param endY - End Y coordinate
         */
        function swipe(startX: number, startY: number, endX: number, endY: number): Promise<UIActionResultData>;
    }

    /**
     * Calculate mathematical expression
     * @param expression - Expression to calculate
     */
    function calc(expression: string): Promise<CalculationResultData>;
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
     * @returns A JSON string representing a ToolResult object
     */
    function callTool(toolType: string, toolName: string, paramsJson: string): string;

    /**
     * Call a tool asynchronously
     * @param callbackId - Unique callback ID
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     * The callback will receive a ToolResult object
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

    /**
     * Log informational message
     * @param message - Message to log
     */
    function logInfo(message: string): void;

    /**
     * Log error message
     * @param message - Error message to log
     */
    function logError(message: string): void;

    /**
     * Log debug message with data
     * @param message - Debug message
     * @param data - Debug data
     */
    function logDebug(message: string, data: string): void;

    /**
     * Report detailed JavaScript error
     * @param errorType - Error type
     * @param errorMessage - Error message
     * @param errorLine - Line number where error occurred
     * @param errorStack - Error stack trace
     */
    function reportError(errorType: string, errorMessage: string, errorLine: number, errorStack: string): void;
}

/**
 * Module exports object for CommonJS-style exports
 */
declare var exports: {
    [key: string]: any;
}; 