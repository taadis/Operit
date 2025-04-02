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

/**
 * File conversion result data
 */
interface FileConversionResultData {
    sourcePath: string;
    targetPath: string;
    sourceFormat: string;
    targetFormat: string;
    conversionType: string;  // "document", "image", "audio", "video", "archive"
    quality?: string;
    fileSize: number;
    duration: number;
    metadata: Record<string, string>;
    toString(): string;
}

/**
 * File format conversion support data
 */
interface FileFormatConversionsResultData {
    formatType?: string;
    conversions: Record<string, string[]>;
    fileTypes: Record<string, string[]>;
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
    shouldKeepNode?(): boolean;
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

interface FileConversionResult extends BaseResult {
    data: FileConversionResultData;
}

interface FileFormatConversionsResult extends BaseResult {
    data: FileFormatConversionsResultData;
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
    FileConversionResult | FileFormatConversionsResult |
    (BaseResult & { data: any });

// ============================================================================
// Tool Name Types
// ============================================================================

/**
 * File tool names
 */
type FileToolName = 'list_files' | 'read_file' | 'write_file' | 'delete_file' | 'file_exists' |
    'move_file' | 'copy_file' | 'make_directory' | 'find_files' | 'file_info' |
    'zip_files' | 'unzip_files' | 'open_file' | 'share_file' | 'download_file' |
    'convert_file' | 'get_supported_conversions';

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
    'swipe' | 'combined_operation' | 'find_element';

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

// ============================================================================
// FFmpeg Types
// ============================================================================

/**
 * FFmpeg codec types
 */
type FFmpegVideoCodec =
    | 'libx264'    // H.264/AVC
    | 'libx265'    // H.265/HEVC
    | 'libvpx'     // VP8/VP9
    | 'libaom'     // AV1
    | 'mpeg4'      // MPEG-4
    | 'mjpeg'      // Motion JPEG
    | 'prores'     // ProRes
    | 'h264'       // H.264 (alternative)
    | 'hevc'       // HEVC (alternative)
    | 'vp8'        // VP8 (alternative)
    | 'vp9'        // VP9 (alternative)
    | 'av1';       // AV1 (alternative)

type FFmpegAudioCodec =
    | 'aac'        // Advanced Audio Coding
    | 'mp3'        // MPEG Audio Layer III
    | 'opus'       // Opus
    | 'vorbis'     // Vorbis
    | 'flac'       // Free Lossless Audio Codec
    | 'pcm'        // Pulse Code Modulation
    | 'wav'        // Waveform Audio File Format
    | 'ac3'        // Dolby Digital
    | 'eac3';      // Enhanced AC-3

type FFmpegResolution =
    | '1280x720'   // HD
    | '1920x1080'  // Full HD
    | '3840x2160'  // 4K
    | '7680x4320'  // 8K
    | `${number}x${number}`;  // Custom resolution

type FFmpegBitrate =
    | '500k'       // 500 kbps
    | '1000k'      // 1000 kbps
    | '2000k'      // 2000 kbps
    | '4000k'      // 4000 kbps
    | '8000k'      // 8000 kbps
    | `${number}k` // Custom kbps
    | `${number}M`; // Custom Mbps

/**
 * FFmpeg stream information
 * Represents detailed information about a video or audio stream in a media file
 */
interface FFmpegStreamInfo {
    /** Stream index in the media file (0-based) */
    index: number;

    /** Stream type: "video" or "audio" */
    type: 'video' | 'audio';

    /** Codec name used for this stream */
    codec: FFmpegVideoCodec | FFmpegAudioCodec;

    /** Frame rate for video streams (e.g., "30/1", "29.97") */
    frameRate?: `${number}/${number}` | `${number}`;

    /** Sample rate for audio streams in Hz (e.g., "44100") */
    sampleRate?: `${number}`;

    /** Number of audio channels (e.g., 2 for stereo) */
    channels?: 1 | 2 | 4 | 6 | 8;

    /** Returns a formatted string representation of the stream info */
    toString(): string;
}

/**
 * FFmpeg result data
 * Contains comprehensive information about the FFmpeg operation execution
 */
interface FFmpegResultData {
    /** The complete FFmpeg command that was executed */
    command: string;

    /** FFmpeg return code (0 indicates success) */
    returnCode: number;

    /** Complete output from the FFmpeg command execution */
    output: string;

    /** Execution duration in milliseconds */
    duration: number;

    /** Array of video stream information */
    videoStreams: FFmpegStreamInfo[];

    /** Array of audio stream information */
    audioStreams: FFmpegStreamInfo[];

    /** Returns a formatted string representation of the result */
    toString(): string;
}

/**
 * FFmpeg tool names
 * Available FFmpeg-related tools in the system
 */
type FFmpegToolName =
    /** Execute custom FFmpeg commands */
    | 'ffmpeg_execute'
    /** Get FFmpeg system information */
    | 'ffmpeg_info'
    /** Convert video files with simplified parameters */
    | 'ffmpeg_convert';

/**
 * All tool names
 */
type ToolName = FileToolName | NetToolName | SystemToolName | UiToolName |
    CalculatorToolName | ConnectionToolName | PackageToolName | FFmpegToolName | string;

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
    'convert_file': FileConversionResultData;
    'get_supported_conversions': FileFormatConversionsResultData;

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
    'find_element': UIPageResultData;

    // Calculator operations
    'calculate': CalculationResultData;

    // Package operations
    'use_package': string;
    'query_problem_library': string;

    // FFmpeg operations
    'ffmpeg_execute': FFmpegResultData;
    'ffmpeg_info': FFmpegResultData;
    'ffmpeg_convert': FFmpegResultData;
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

        /**
         * Convert a file
         * @param sourcePath - Source file path
         * @param targetPath - Target file path
         * @param options - Conversion options
         */
        function convert(sourcePath: string, targetPath: string, options?: {
            quality?: 'low' | 'medium' | 'high' | 'lossless';
            video_codec?: FFmpegVideoCodec;
            audio_codec?: FFmpegAudioCodec;
            resolution?: FFmpegResolution;
            bitrate?: FFmpegBitrate;
            extra_params?: string;
        }): Promise<FileConversionResultData>;

        /**
         * Get supported conversions for a file
         * @param formatType - Optional file type filter (e.g., "image", "audio", "video", "document", "archive")
         */
        function getSupportedConversions(formatType?: string): Promise<FileFormatConversionsResultData>;
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
         * Sleep for specified milliseconds
         * @param milliseconds - Milliseconds to sleep
         */
        function sleep(milliseconds: string | number): Promise<SleepResultData>;

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
         * Multiple call patterns supported:
         * - clickElement(resourceId: string): Click by resource ID
         * - clickElement(bounds: string): Click by bounds "[x1,y1][x2,y2]"
         * - clickElement(params: object): Click using parameters object
         * - clickElement(type: "resourceId"|"className"|"bounds", value: string): Click by type
         * - clickElement(resourceId: string, index: number): Click by resource ID and index
         * @param param1 - Resource ID, bounds, or parameter object
         * @param param2 - Optional index or value
         * @param param3 - Optional index when using type+value
         */
        function clickElement(param1: string | { [key: string]: any }, param2?: string | number, param3?: number): Promise<UIActionResultData>;

        /**
         * Click on an element (detailed overloads matching implementation)
         * @param params - Parameters object with resourceId, className, text, contentDesc, bounds, etc.
         */
        function clickElement(params: {
            resourceId?: string,
            className?: string,
            text?: string,
            contentDesc?: string,
            bounds?: string,
            index?: number,
            partialMatch?: boolean,
            isClickable?: boolean
        }): Promise<UIActionResultData>;

        /**
         * Click on an element by resource ID
         * @param resourceId - Element resource ID to click
         */
        function clickElement(resourceId: string): Promise<UIActionResultData>;

        /**
         * Click on an element by bounds
         * @param bounds - Element bounds in format "[x1,y1][x2,y2]"
         */
        function clickElement(bounds: string): Promise<UIActionResultData>;

        /**
         * Click on an element by resource ID with index
         * @param resourceId - Element resource ID to click
         * @param index - Index of the element when multiple match (0-based)
         */
        function clickElement(resourceId: string, index: number): Promise<UIActionResultData>;

        /**
         * Click on an element by type and value
         * @param type - Type of identifier ("resourceId", "className", or "bounds")
         * @param value - Value for the specified type
         */
        function clickElement(type: "resourceId" | "className" | "bounds", value: string): Promise<UIActionResultData>;

        /**
         * Click on an element by type, value and index
         * @param type - Type of identifier ("resourceId" or "className")
         * @param value - Value for the specified type
         * @param index - Index of the element when multiple match (0-based)
         */
        function clickElement(type: "resourceId" | "className", value: string, index: number): Promise<UIActionResultData>;

        /**
         * Find UI elements matching specific criteria without clicking them
         * @param params - Search parameters (resourceId, className, text, etc.)
         */
        function findElement(params: {
            resourceId?: string,
            className?: string,
            text?: string,
            partialMatch?: boolean,
            limit?: number
        }): Promise<UIPageResultData>;

        /**
         * Set text in input field
         * @param text - Text to input
         */
        function setText(text: string): Promise<UIActionResultData>;

        /**
         * Press a key
         * @param keyCode - Key code to press
         */
        function pressKey(keyCode: string): Promise<UIActionResultData>;

        /**
         * Swipe from one position to another
         * @param startX - Start X coordinate
         * @param startY - Start Y coordinate
         * @param endX - End X coordinate
         * @param endY - End Y coordinate
         */
        function swipe(startX: number, startY: number, endX: number, endY: number): Promise<UIActionResultData>;

        /**
         * Execute a UI operation, wait, then return the new UI state
         * @param operation - Operation to execute
         * @param delayMs - Delay in milliseconds before getting new UI state
         */
        function combinedOperation(operation: string, delayMs?: number): Promise<CombinedOperationResultData>;
    }

    /**
     * Calculate mathematical expression
     * @param expression - Expression to calculate
     */
    function calc(expression: string): Promise<CalculationResultData>;

    /**
     * FFmpeg operations
     * Provides tools for video and audio processing using FFmpeg
     */
    namespace FFmpeg {
        /**
         * Execute a custom FFmpeg command
         * @param command - The FFmpeg command to execute
         * @returns Promise resolving to FFmpegResultData containing execution details
         * @throws Error if the command execution fails
         */
        function execute(command: string): Promise<FFmpegResultData>;

        /**
         * Get FFmpeg system information
         * @returns Promise resolving to FFmpegResultData containing system information
         */
        function info(): Promise<FFmpegResultData>;

        /**
         * Convert video file with simplified parameters
         * @param inputPath - Source video file path
         * @param outputPath - Destination video file path
         * @param options - Optional conversion parameters
         */
        function convert(
            inputPath: string,
            outputPath: string,
            options?: {
                video_codec?: FFmpegVideoCodec;
                audio_codec?: FFmpegAudioCodec;
                resolution?: FFmpegResolution;
                bitrate?: FFmpegBitrate;
            }
        ): Promise<FFmpegResultData>;
    }
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
 * UINode - A powerful wrapper for Android UI elements with DOM-like operations
 * 
 * This class provides a convenient way to navigate, search, and interact with
 * Android UI elements. It wraps SimplifiedUINode objects and provides methods
 * similar to web DOM manipulation.
 */
declare class UINode {
    /**
     * Create a new UINode instance
     * @param node - The SimplifiedUINode object to wrap
     */
    constructor(node: SimplifiedUINode);

    // Core Properties

    /**
     * The class name of the node
     */
    readonly className: string | undefined;

    /**
     * The text content of the node
     */
    readonly text: string | undefined;

    /**
     * The content description of the node
     */
    readonly contentDesc: string | undefined;

    /**
     * The resource ID of the node
     */
    readonly resourceId: string | undefined;

    /**
     * The bounds of the node in format "[x1,y1][x2,y2]"
     */
    readonly bounds: string | undefined;

    /**
     * Whether the node is clickable
     */
    readonly isClickable: boolean;

    /**
     * The underlying wrapped SimplifiedUINode object
     */
    readonly rawNode: SimplifiedUINode;

    /**
     * The center point coordinates based on bounds
     */
    readonly centerPoint: { x: number, y: number } | undefined;

    /**
     * All children nodes
     */
    readonly children: UINode[];

    /**
     * The number of children
     */
    readonly childCount: number;

    // Text Extraction

    /**
     * Get all text content from this node and its descendants
     * @param trim - Whether to trim whitespace from text
     * @param skipEmpty - Whether to skip empty text values
     */
    allTexts(trim?: boolean, skipEmpty?: boolean): string[];

    /**
     * Get all text content as a single string
     * @param separator - String to join text values with
     */
    textContent(separator?: string): string;

    /**
     * Check if this node or any descendant contains the specified text
     * @param text - Text to search for
     * @param caseSensitive - Whether the search is case-sensitive
     */
    hasText(text: string, caseSensitive?: boolean): boolean;

    // Search Methods

    /**
     * Find the first descendant node matching the criteria
     * @param criteria - Search criteria or predicate function
     * @param deep - Whether to search recursively
     */
    find(criteria: object | ((node: UINode) => boolean), deep?: boolean): UINode | undefined;

    /**
     * Find all descendant nodes matching the criteria
     * @param criteria - Search criteria or predicate function
     * @param deep - Whether to search recursively
     */
    findAll(criteria: object | ((node: UINode) => boolean), deep?: boolean): UINode[];

    // Convenience Search Methods

    /**
     * Find a node by text content
     * @param text - Text to search for
     * @param options - Search options
     */
    findByText(text: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode | undefined;

    /**
     * Find nodes by text content
     * @param text - Text to search for
     * @param options - Search options
     */
    findAllByText(text: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode[];

    /**
     * Find a node by resource ID
     * @param id - Resource ID to search for
     * @param options - Search options
     */
    findById(id: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode | undefined;

    /**
     * Find nodes by resource ID
     * @param id - Resource ID to search for
     * @param options - Search options
     */
    findAllById(id: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode[];

    /**
     * Find a node by class name
     * @param className - Class name to search for
     * @param options - Search options
     */
    findByClass(className: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode | undefined;

    /**
     * Find nodes by class name
     * @param className - Class name to search for
     * @param options - Search options
     */
    findAllByClass(className: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode[];

    /**
     * Find a node by content description
     * @param description - Content description to search for
     * @param options - Search options
     */
    findByContentDesc(description: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode | undefined;

    /**
     * Find nodes by content description
     * @param description - Content description to search for
     * @param options - Search options
     */
    findAllByContentDesc(description: string, options?: { exact?: boolean, caseSensitive?: boolean }): UINode[];

    /**
     * Find all clickable nodes
     */
    findClickable(): UINode[];

    // Actions

    /**
     * Click on this node
     */
    click(): Promise<UIActionResultData>;

    /**
     * Set text in this node (typically an input field)
     * @param text - Text to enter
     */
    setText(text: string): Promise<UIActionResultData>;

    /**
     * Wait for a specified time, then return an updated UI state
     * @param ms - Milliseconds to wait
     */
    wait(ms?: number): Promise<UINode>;

    /**
     * Click this node and wait for the UI to update
     * @param ms - Milliseconds to wait after clicking
     */
    clickAndWait(ms?: number): Promise<UINode>;

    // Utility Methods

    /**
     * Convert to string representation
     */
    toString(): string;

    /**
     * Get a tree representation of this node and its descendants
     * @param indent - Indentation string for formatting
     */
    toTree(indent?: string): string;

    /**
     * Get a tree representation of this node and its descendants in Kotlin format
     * with filtering for relevant nodes only
     * @param indent - Indentation string for formatting
     */
    toTreeString(indent?: string): string;

    /**
     * Get a formatted string representation of the page info including
     * application package name, activity name, and UI hierarchy in tree format
     * (Only available on nodes created via fromPageInfo())
     */
    toFormattedString?(): string;

    /**
     * Check if this node and another are the same
     * @param other - Node to compare with
     */
    equals(other: UINode): boolean;

    // Static Methods

    /**
     * Create a UINode from a page info result
     * @param pageInfo - Page info from UI.getPageInfo()
     */
    static fromPageInfo(pageInfo: UIPageResultData): UINode;

    /**
     * Get the current page UI
     */
    static getCurrentPage(): Promise<UINode>;

    /**
     * Perform a search, wait, and return updated UI state
     * @param query - Search parameters
     * @param delayMs - Milliseconds to wait
     */
    static findAndWait(query: object, delayMs?: number): Promise<UINode>;

    /**
     * Click an element, wait, and return updated UI state
     * @param query - Element to click (search parameters)
     * @param delayMs - Milliseconds to wait
     */
    static clickAndWait(query: object, delayMs?: number): Promise<UINode>;
}

/**
 * Module exports object for CommonJS-style exports
 */
declare var exports: {
    [key: string]: any;
}; 