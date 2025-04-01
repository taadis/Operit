package com.ai.assistance.operit.tools.javascript

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.*
import com.ai.assistance.operit.tools.packTool.PackageManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * JavaScript 引擎 - 通过 WebView 执行 JavaScript 脚本
 * 提供与 Android 原生代码的交互机制
 * 
 * 主要功能：
 * 1. 执行 JavaScript 脚本
 * 2. 为脚本提供工具调用能力
 * 3. 集成常用的第三方 JavaScript 库
 * 
 * 工具调用使用方式:
 * - 标准模式: toolCall("toolType", "toolName", { param1: "value1" })
 * - 简化模式: toolCall("toolName", { param1: "value1" })
 * - 对象模式: toolCall({ type: "toolType", name: "toolName", params: { param1: "value1" } })
 * - 直接模式: toolCall("toolName")
 * 
 * 便捷工具调用:
 * - 文件操作: Tools.Files.read("/path/to/file")
 * - 网络操作: Tools.Net.httpGet("https://example.com")
 * - 系统操作: Tools.System.sleep("1")
 * - 计算功能: Tools.calc("2 + 2 * 3")
 * 
 * 完成脚本执行:
 * - complete(result) 函数传递最终结果返回给调用者
 */
class JsEngine(private val context: Context) {
    companion object {
        private const val TAG = "JsEngine"
        private const val TIMEOUT_SECONDS = 60L  // 增加超时时间到60秒
        private const val PRE_TIMEOUT_SECONDS = 55L  // 提前5秒触发JavaScript端的超时保护
    }
    
    // WebView 实例用于执行 JavaScript
    private var webView: WebView? = null
    
    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)
    
    // 工具调用接口
    private val toolCallInterface = JsToolCallInterface()
    
    // 结果回调
    private var resultCallback: CompletableFuture<Any?>? = null
    
    // 用于生成唯一ID的计数器
    private var callbackCounter = 0
    
    // 用于存储工具调用的回调
    private val toolCallbacks = mutableMapOf<String, CompletableFuture<String>>()
    
    // 标记 JS 环境是否已初始化
    private var jsEnvironmentInitialized = false
    
    // 初始化 WebView
    private fun initWebView() {
        if (webView == null) {
            // 需要在主线程创建 WebView
            val latch = CountDownLatch(1)
            ContextCompat.getMainExecutor(context).execute {
                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(toolCallInterface, "NativeInterface")
                    }
                    latch.countDown()
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing WebView: ${e.message}", e)
                    latch.countDown()
                }
            }
            latch.await(5, TimeUnit.SECONDS)
        }
    }

    /**
     * 初始化 JavaScript 环境
     * 加载核心功能、工具库和辅助函数
     * 这些代码只需要执行一次
     */
    private fun initJavaScriptEnvironment() {
        if (jsEnvironmentInitialized) {
            return  // 如果已经初始化，直接返回
        }
        
        val initScript = """
            // 添加全局错误处理器，捕获所有未处理的错误
            window.onerror = function(message, source, lineno, colno, error) {
                try {
                    // 构建完整的错误信息
                    var errorInfo = {
                        message: message,
                        source: source,
                        line: lineno,
                        column: colno,
                        stack: error && error.stack ? error.stack : "No stack trace available"
                    };
                    
                    // 记录详细的错误信息到控制台
                    console.error("GLOBAL ERROR CAUGHT:", JSON.stringify(errorInfo));
                    
                    // 如果尚未完成执行，报告错误
                    if (!window._hasCompleted) {
                        NativeInterface.setError("JavaScript Error: " + message + " at line " + lineno + 
                                               ", column " + colno + " in " + (source || "unknown") + 
                                               "\nStack: " + (error && error.stack ? error.stack : "No stack trace"));
                        window._hasCompleted = true;
                    }
                    
                    // 返回true表示我们已经处理了错误
                    return true;
                } catch(e) {
                    // 确保错误处理器本身不会抛出错误
                    console.error("Error in error handler:", e);
                    return false;
                }
            };
            
            // 增强console功能，将所有控制台输出发送到Android
            (function() {
                var originalConsole = {
                    log: console.log,
                    error: console.error,
                    warn: console.warn,
                    info: console.info
                };
                
                // 重写控制台方法
                console.log = function() {
                    try {
                        var args = Array.prototype.slice.call(arguments);
                        var message = args.map(function(arg) {
                            return typeof arg === 'object' ? JSON.stringify(arg) : String(arg);
                        }).join(' ');
                        
                        // 调用原始方法
                        originalConsole.log.apply(console, arguments);
                        
                        // 向Android报告日志
                        if (typeof NativeInterface !== 'undefined' && NativeInterface.logInfo) {
                            NativeInterface.logInfo("LOG: " + message);
                        }
                    } catch(e) {
                        originalConsole.error("Error in console.log:", e);
                    }
                };
                
                console.error = function() {
                    try {
                        var args = Array.prototype.slice.call(arguments);
                        var message = args.map(function(arg) {
                            return typeof arg === 'object' ? JSON.stringify(arg) : String(arg);
                        }).join(' ');
                        
                        // 调用原始方法
                        originalConsole.error.apply(console, arguments);
                        
                        // 向Android报告错误
                        if (typeof NativeInterface !== 'undefined' && NativeInterface.logError) {
                            NativeInterface.logError("ERROR: " + message);
                        }
                    } catch(e) {
                        originalConsole.error("Error in console.error:", e);
                    }
                };
            })();
            
            // 定义 toolCall 函数 - 支持多种参数传递方式，并且返回Promise
            function toolCall(toolType, toolName, toolParams) {
                // Create a Promise wrapping the tool call
                return new Promise((resolve, reject) => {
                    try {
                        // 生成唯一回调ID
                        const callbackId = '_tc_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
                        
                        // 处理不同的参数调用模式
                        let type, name, params;
                        
                        if (arguments.length === 1 && typeof toolType === 'object') {
                            // 对象模式: toolCall({type: "...", name: "...", params: {...}})
                            const config = toolType;
                            type = config.type || "default";
                            name = config.name || "";
                            
                            // 安全地序列化参数对象
                            try {
                                let paramsObj = {};
                                if (config.params && typeof config.params === 'object') {
                                    paramsObj = Object.assign({}, config.params);
                                }
                                params = JSON.stringify(paramsObj);
                            } catch (e) {
                                console.error("Error serializing object mode params:", e);
                                params = "{}";
                            }
                        } else if (arguments.length === 1 && typeof toolType === 'string') {
                            // 字符串模式: toolCall("toolName")
                            type = "default";
                            name = toolType;
                            params = "{}";
                        } else if (arguments.length === 2 && typeof toolName === 'object') {
                            // 工具名+参数模式: toolCall("toolName", {param1: "value1"})
                            type = "default";
                            name = toolType;
                            
                            // 安全地序列化参数对象
                            try {
                                // 使用深拷贝而非直接引用，避免修改原始对象
                                const paramsCopy = Object.assign({}, toolName || {});
                                params = JSON.stringify(paramsCopy);
                            } catch (e) {
                                console.error("Error serializing params:", e);
                                params = "{}";
                            }
                        } else {
                            // 标准模式: toolCall("toolType", "toolName", {param1: "value1"})
                            type = toolType || "default";
                            name = toolName || "";
                            
                            // 安全地序列化参数对象
                            try {
                                const paramsCopy = Object.assign({}, toolParams || {});
                                params = JSON.stringify(paramsCopy);
                            } catch (e) {
                                console.error("Error serializing params:", e);
                                params = "{}";
                            }
                        }
                        
                        // 调用本地方法，并传递回调ID
                        NativeInterface.callToolAsync(callbackId, type, name, params);
                        
                        // 注册回调处理
                        window[callbackId] = function(result, isError) {
                            // 清理回调
                            delete window[callbackId];
                            
                            // Handle the structured result
                            if (isError) {
                                // Error results are now structured JSON objects
                                if (typeof result === 'object' && result.success === false) {
                                    reject(new Error(result.error || "Unknown error"));
                                } else {
                                    reject(new Error(typeof result === 'string' ? result : JSON.stringify(result)));
                                }
                            } else {
                                try {
                                    // Process structured result
                                    let processedResult;
                                    
                                    // If it's already a JS object (from JSON in sendToolResult)
                                    if (typeof result === 'object') {
                                        if (result.success) {
                                            // Return just the data part of successful results
                                            processedResult = result.data;
                                        } else {
                                            // For error objects, reject the promise
                                            reject(new Error(result.error || "Unknown error"));
                                            return;
                                        }
                                    } 
                                    // If it's a JSON string
                                    else if (typeof result === 'string' && (result.startsWith('{') || result.startsWith('['))) {
                                        const parsedResult = JSON.parse(result);
                                        
                                        // Check if it's our ToolResult format
                                        if (parsedResult && typeof parsedResult === 'object' && 'success' in parsedResult) {
                                            if (parsedResult.success) {
                                                // Return just the data for successful results
                                                processedResult = parsedResult.data;
                                            } else {
                                                // For error results, reject the promise
                                                reject(new Error(parsedResult.error || "Unknown error"));
                                                return;
                                            }
                                        } else {
                                            // Regular JSON result (legacy or other format)
                                            processedResult = parsedResult;
                                        }
                                    } else {
                                        // Plain string or other primitive
                                        processedResult = result;
                                    }
                                    
                                    // Resolve the promise with the final processed result
                                    resolve(processedResult);
                                } catch (e) {
                                    // If any parsing error occurs, return the original result
                                    console.error("Error processing tool result:", e);
                                    resolve(result);
                                }
                            }
                        };
                    } catch (error) {
                        reject(error);
                    }
                });
            }
            
            // 工具调用的便捷方法
            var Tools = {
                // 文件系统操作
                Files: {
                    list: (path) => toolCall("list_files", { path }),
                    read: (path) => toolCall("read_file", { path }),
                    write: (path, content) => toolCall("write_file", { path, content }),
                    deleteFile: (path) => toolCall("delete_file", { path }),
                    exists: (path) => toolCall("file_exists", { path }),
                    move: (source, destination) => toolCall("move_file", { source, destination }),
                    copy: (source, destination) => toolCall("copy_file", { source, destination }),
                    mkdir: (path) => toolCall("make_directory", { path }),
                    find: (path, pattern) => toolCall("find_files", { path, pattern }),
                    info: (path) => toolCall("file_info", { path }),
                    zip: (source, destination) => toolCall("zip_files", { source, destination }),
                    unzip: (source, destination) => toolCall("unzip_files", { source, destination }),
                    open: (path) => toolCall("open_file", { path }),
                    share: (path) => toolCall("share_file", { path }),
                    download: (url, destination) => toolCall("download_file", { url, destination })
                },
                // 网络操作
                Net: {
                    httpGet: (url) => toolCall("http_request", { url, method: "GET" }),
                    httpPost: (url, data) => toolCall("http_request", { url, method: "POST", data }),
                    search: (query) => toolCall("web_search", { query }),
                    fetchPage: (url) => toolCall("fetch_web_page", { url })
                },
                // 系统操作
                System: {
                    exec: (command) => toolCall("execute_comma nd", { command }),
                    sleep: (milliseconds) => toolCall("sleep", { duration_ms: parseInt(milliseconds) }),
                    getSetting: (setting, namespace) => toolCall("get_system_setting", { key: setting, namespace }),
                    setSetting: (setting, value, namespace) => toolCall("modify_system_setting", { key: setting, value, namespace }),
                    getDeviceInfo: () => toolCall("device_info"),
                    startApp: (packageName, activity) => toolCall("start_app", { package_name: packageName, activity: activity }),
                    stopApp: (packageName) => toolCall("stop_app", { package_name: packageName }),
                    listApps: (includeSystem) => toolCall("list_installed_apps", { include_system: !!includeSystem })
                },
                // UI操作
                UI: {
                    getPageInfo: () => toolCall("get_page_info"),
                    tap: (x, y) => toolCall("tap", { x, y }),
                    // 增强的clickElement方法，支持多种参数类型
                    clickElement: function(param1, param2, param3) {
                        // 根据参数类型和数量判断调用方式
                        if (typeof param1 === 'object') {
                            // 如果第一个参数是对象，直接传递参数对象
                            return toolCall("click_element", param1);
                        } else if (arguments.length === 1) {
                            // 单参数，假定为resourceId
                            if (param1.startsWith('[') && param1.includes('][')) {
                                // 参数看起来像bounds格式 [x,y][x,y]
                                return toolCall("click_element", { bounds: param1 });
                            }
                            return toolCall("click_element", { resourceId: param1 });
                        } else if (arguments.length === 2) {
                            // 两个参数，假定为(resourceId, index)或(className, index)
                            if (param1 === 'resourceId') {
                                return toolCall("click_element", { resourceId: param2 });
                            } else if (param1 === 'className') {
                                return toolCall("click_element", { className: param2 });
                            } else if (param1 === 'bounds') {
                                return toolCall("click_element", { bounds: param2 });
                            } else {
                                return toolCall("click_element", { resourceId: param1, index: param2 });
                            }
                        } else if (arguments.length === 3) {
                            // 三个参数，假定为(type, value, index)
                            if (param1 === 'resourceId') {
                                return toolCall("click_element", { resourceId: param2, index: param3 });
                            } else if (param1 === 'className') {
                                return toolCall("click_element", { className: param2, index: param3 });
                            } else {
                                return toolCall("click_element", { resourceId: param1, className: param2, index: param3 });
                            }
                        }
                        // 默认情况
                        return toolCall("click_element", { resourceId: param1 });
                    },
                    // 查找UI元素方法
                    findElement: function(params) {
                        return toolCall("find_element", params);
                    },
                    setText: (text) => toolCall("set_input_text", { text }),
                    swipe: (startX, startY, endX, endY) => toolCall("swipe", { start_x: startX, start_y: startY, end_x: endX, end_y: endY }),
                    pressKey: (keyCode) => toolCall("press_key", { key_code: keyCode }),
                    // 组合操作
                    combinedOperation: (operation, delayMs) => toolCall("combined_operation", { operation, delay_ms: delayMs || 1000 })
                },
                // 计算功能
                calc: (expression) => toolCall("calculate", { expression })
            };
            
            // 定义完成回调
            function complete(result) {
                try {
                    console.log("complete() called with result type: " + typeof result);
                    
                    if (!window._hasCompleted) {
                        window._hasCompleted = true;
                        clearTimeout(window._safetyTimeout);
                        
                        // 确保结果是可以序列化的
                        let serializedResult;
                        try {
                            serializedResult = JSON.stringify(result);
                            console.log("Result serialized successfully, length: " + serializedResult.length);
                        } catch (serializeError) {
                            console.error("Failed to serialize result:", serializeError);
                            serializedResult = JSON.stringify({
                                error: "Failed to serialize result",
                                message: String(serializeError),
                                result: String(result).substring(0, 1000)
                            });
                        }
                        
                        // 通过JavaScript接口发送结果
                        try {
                            console.log("Calling NativeInterface.setResult...");
                            NativeInterface.setResult(serializedResult);
                            console.log("NativeInterface.setResult call completed");
                        } catch (nativeError) {
                            console.error("Error calling NativeInterface:", nativeError);
                            // 尝试再次调用，以防是暂时性错误
                            setTimeout(() => {
                                try {
                                    console.log("Retrying NativeInterface.setResult...");
                                    NativeInterface.setResult(serializedResult);
                                } catch (retryError) {
                                    console.error("Retry also failed:", retryError);
                                }
                            }, 100);
                        }
                    } else {
                        console.warn("complete() called but execution was already completed");
                    }
                } catch (completeError) {
                    console.error("Error in complete function:", completeError);
                    try {
                        NativeInterface.setError("Error in complete function: " + completeError.message);
                    } catch (e) {
                        console.error("Failed to report complete error:", e);
                    }
                }
            }
            
            // 加载第三方库支持
            $THIRD_PARTY_LIBS
            
            // 加载 UINode 库
            ${loadUINodeJs()}
            
            // 函数处理异步Promise的辅助函数
            function __handleAsync(possiblePromise) {
                if (possiblePromise instanceof Promise) {
                    // 更强大的异步处理
                    console.log("Detected async Promise, waiting for resolution...");
                    
                    // 创建一个超时保护，确保非常长时间运行的Promise最终会被处理
                    const asyncTimeout = setTimeout(() => {
                        if (!window._hasCompleted) {
                            console.log("Async Promise timeout reached after 45 seconds");
                            // 尝试安全地完成执行
                            try {
                                window._hasCompleted = true;
                                // 创建超时结果
                                const timeoutResult = {
                                    warning: "Operation timeout", 
                                    result: "Promise did not resolve within the time limit"
                                };
                                
                                NativeInterface.setResult(JSON.stringify(timeoutResult));
                                console.log("Timeout result set from Promise handler");
                            } catch (e) {
                                console.error("Error during timeout handling:", e);
                            }
                        }
                    }, 45000); // 45秒后超时，比主超时稍短
                    
                    possiblePromise
                        .then(result => {
                            clearTimeout(asyncTimeout);
                            console.log("Async Promise resolved successfully");
                            if (!window._hasCompleted) {
                                try {
                                    window._hasCompleted = true;
                                    // 安全地序列化结果
                                    let serializedResult;
                                    try {
                                        serializedResult = JSON.stringify(result);
                                    } catch (serializeError) {
                                        console.error("Failed to serialize Promise result:", serializeError);
                                        // 创建一个简单的可以序列化的对象
                                        serializedResult = JSON.stringify({
                                            error: "Failed to serialize result",
                                            message: String(serializeError),
                                            result: String(result).substring(0, 1000)
                                        });
                                    }
                                    NativeInterface.setResult(serializedResult);
                                    console.log("Result set from Promise resolution");
                                } catch (completionError) {
                                    console.error("Error during async completion:", completionError);
                                    NativeInterface.setError("Async completion error: " + completionError.message);
                                }
                            } else {
                                console.log("Promise resolved, but execution was already completed");
                            }
                        })
                        .catch(error => {
                            clearTimeout(asyncTimeout);
                            console.error("Async Promise rejected:", error);
                            if (!window._hasCompleted) {
                                try {
                                    window._hasCompleted = true;
                                    // 安全地序列化结果
                                    let serializedResult;
                                    try {
                                        serializedResult = JSON.stringify(error);
                                    } catch (serializeError) {
                                        console.error("Failed to serialize Promise error:", serializeError);
                                        serializedResult = JSON.stringify({
                                            error: "Failed to serialize error",
                                            message: String(error),
                                            result: String(error).substring(0, 1000)
                                        });
                                    }
                                    NativeInterface.setError(serializedResult);
                                    console.log("Error set from Promise rejection");
                                } catch (errorHandlingError) {
                                    console.error("Error during async error handling:", errorHandlingError);
                                }
                            }
                        });
                    return true; // Signal that we're handling it asynchronously
                }
                return false; // Not a promise
            }
        """.trimIndent()
        
        // 在 WebView 中执行初始化脚本
        ContextCompat.getMainExecutor(context).execute {
            try {
                webView?.evaluateJavascript(initScript) { result ->
                    Log.d(TAG, "JS environment initialization completed: $result")
                    jsEnvironmentInitialized = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize JS environment: ${e.message}", e)
            }
        }
        
        // 等待初始化完成
        var retries = 0
        while (!jsEnvironmentInitialized && retries < 10) {
            Thread.sleep(100)
            retries++
        }
    }

    /**
     * 执行 JavaScript 脚本并调用其中的特定函数
     * @param script 完整的JavaScript脚本内容
     * @param functionName 要调用的函数名称
     * @param params 要传递给函数的参数
     * @return 函数执行结果
     */
    fun executeScriptFunction(script: String, functionName: String, params: Map<String, String>): Any? {
        // Reset any previous state
        resetState()
        
        initWebView()
        
        // 确保JavaScript环境已初始化
        if (!jsEnvironmentInitialized) {
            initJavaScriptEnvironment()
        }
        
        val future = CompletableFuture<Any?>()
        resultCallback = future
        
        // 将参数转换为 JSON 对象
        val paramsJson = JSONObject(params).toString()
        
        // 优化后的脚本执行代码，只包含必要的执行逻辑
        val executionScript = """
            // 清理定时器以准备新的执行
            (function() {
                try {
                    var highestTimeoutId = setTimeout(";");
                    for (var i = 0 ; i < highestTimeoutId ; i++) {
                        clearTimeout(i);
                        clearInterval(i);
                    }
                } catch(e) {}
            })();
            
            // 设置参数和执行状态
            var params = $paramsJson;
            window._hasCompleted = false;
            
            // 设置安全超时机制
            window._safetyTimeout = setTimeout(function() {
                if (!window._hasCompleted) {
                    console.log("Safety timeout warning at " + (${TIMEOUT_SECONDS-5}) + " seconds");
                    // 不立即结束，而是添加另一个最终超时
                    setTimeout(function() {
                        if (!window._hasCompleted) {
                            window._hasCompleted = true;
                            NativeInterface.setResult("Script execution timed out after ${TIMEOUT_SECONDS} seconds");
                        }
                    }, 5000); // 再等5秒
                }
            }, ${(TIMEOUT_SECONDS-5) * 1000});
            
            // 执行用户脚本
            try {
                // 创建模块执行环境 - 使用一个闭包来避免重复声明变量
                let moduleResult = (function() {
                    // 创建一个自包含的模块环境
                    const module = {exports: {}};
                    const exports = module.exports;
                    
                    // 模拟requireJS
                    const require = function(moduleName) {
                        console.log('Attempted to require: ' + moduleName);
                        // 这里可以扩展，添加对常用模块的模拟
                        if (moduleName === 'lodash') return _;
                        // 对其他常用模块的支持可以在这里添加
                        if (moduleName === 'uuid') {
                            return {
                                v4: function() {
                                    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
                                        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
                                        return v.toString(16);
                                    });
                                }
                            };
                        }
                        if (moduleName === 'axios') {
                            return {
                                get: (url, config) => {
                                    const params = config ? Object.assign({}, { url }, config) : { url };
                                    return toolCall("http_request", params);
                                },
                                post: (url, data, config) => {
                                    const params = config ? Object.assign({}, { url, data }, config) : { url, data };
                                    return toolCall("http_request", params);
                                }
                            };
                        }
                        return {};
                    };
                    
                    // 执行用户脚本，定义所有函数
                    ${script}
                    
                    // 返回模块环境
                    return {
                        module: module,
                        exports: exports,
                        foundFunction: null
                    };
                })();
                
                // 从模块环境中获取结果
                const module = moduleResult.module;
                const exports = moduleResult.exports;
                
                // 确保指定的函数存在 - 先查找exports，再查找module.exports，最后查找全局
                let functionResult = null;
                let functionFound = false;
                
                if (typeof exports['${functionName}'] === 'function') {
                    // 如果函数是作为exports导出的
                    functionFound = true;
                    functionResult = exports['${functionName}'](params);
                } else if (typeof module.exports['${functionName}'] === 'function') {
                    // 尝试替代的导出模式
                    functionFound = true;
                    functionResult = module.exports['${functionName}'](params);
                } else if (typeof window['${functionName}'] === 'function') {
                    // 全局函数
                    functionFound = true;
                    functionResult = window['${functionName}'](params);
                }
                
                if (functionFound) {
                    // 处理函数返回结果，特别是异步Promise
                    if (!__handleAsync(functionResult)) {
                        // 如果是同步结果且没有调用complete()，使用该结果
                        if (!window._hasCompleted) {
                            NativeInterface.setResult(JSON.stringify(functionResult));
                        }
                    }
                } else {
                    // 如果没有找到函数，记录所有可用的函数
                    var availableFunctions = [];
                    for (var key in exports) {
                        if (typeof exports[key] === 'function') {
                            availableFunctions.push(key);
                        }
                    }
                    for (var key in module.exports) {
                        if (typeof module.exports[key] === 'function' && !availableFunctions.includes(key)) {
                            availableFunctions.push(key);
                        }
                    }
                    for (var key in window) {
                        if (typeof window[key] === 'function' && !key.startsWith('_') && !availableFunctions.includes(key)) {
                            availableFunctions.push(key);
                        }
                    }
                    
                    var errorMsg = "Function '${functionName}' not found in script. Available functions: " + 
                                  (availableFunctions.length > 0 ? availableFunctions.join(", ") : "none");
                    NativeInterface.setError(errorMsg);
                }
            } catch (error) {
                NativeInterface.setError("Script error: " + error.message);
            }
        """.trimIndent()
        
        // 在主线程中执行脚本
        ContextCompat.getMainExecutor(context).execute {
            webView?.evaluateJavascript(executionScript) { result ->
                Log.d(TAG, "Script function execution completed with: $result")
            }
        }
        
        // 等待结果或超时
        return try {
            // 创建一个定时器，在超时前提醒JavaScript
            val preTimeoutTimer = java.util.Timer()
            
            // 只在较长的脚本执行中使用超时预警
            preTimeoutTimer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    try {
                        // 如果还没完成，尝试提前触发完成
                        if (!future.isDone) {
                            Log.d(TAG, "Pre-timeout warning triggered")
                            ContextCompat.getMainExecutor(context).execute {
                                webView?.evaluateJavascript("""
                                    if (typeof complete === 'function' && !window._hasCompleted) {
                                        console.log("Script execution approaching timeout");
                                        // 不强制完成，只记录警告
                                    }
                                """, null)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in pre-timeout handler: ${e.message}", e)
                    }
                }
            }, PRE_TIMEOUT_SECONDS * 1000)
            
            try {
                val result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                preTimeoutTimer.cancel()
                result
            } catch (e: Exception) {
                preTimeoutTimer.cancel()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Script execution timed out or failed: ${e.message}", e)
            // 确保WebView的JavaScript不再继续执行
            ContextCompat.getMainExecutor(context).execute {
                webView?.evaluateJavascript("window._hasCompleted = true; clearTimeout(window._safetyTimeout);", null)
            }
            "Error: ${e.message}"
        }
    }
    
    /**
     * 重置引擎状态，避免多次调用时的状态干扰
     */
    private fun resetState() {
        // 只有当之前的回调存在时才需要完成它
        if (resultCallback != null && !resultCallback!!.isDone) {
            try {
                resultCallback?.complete("Execution canceled: new execution started")
            } catch (e: Exception) {
                Log.e(TAG, "Error completing previous callback: ${e.message}", e)
            }
            resultCallback = null
        }
        
        // 清理所有待处理的工具调用回调
        toolCallbacks.forEach { (_, future) ->
            if (!future.isDone) {
                future.complete("Operation canceled: engine reset")
            }
        }
        toolCallbacks.clear()
        
        // 如果WebView已经存在，执行轻量级清理
        if (webView != null) {
            ContextCompat.getMainExecutor(context).execute {
                try {
                    // 使用更简单、更安全的清理代码
                    webView?.evaluateJavascript("""
                        // 清理所有定时器
                        (function() {
                            var highestTimeoutId = setTimeout(";");
                            for (var i = 0 ; i < highestTimeoutId ; i++) {
                                clearTimeout(i);
                                clearInterval(i);
                            }
                        })();
                    """, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in WebView cleanup: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * 加载 UINode.js 文件
     * 从 assets 目录读取并返回 JS 代码
     */
    private fun loadUINodeJs(): String {
        return try {
            val inputStream = context.assets.open("js/UINode.js")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading UINode.js: ${e.message}", e)
            // 如果加载失败，返回空字符串
            ""
        }
    }
    
    /**
     * 加载常用的第三方 JavaScript 库
     * 可以根据需要添加更多库
     */
    private val THIRD_PARTY_LIBS = """
        // Lodash 核心功能
        // 轻量级版本，包含最常用的工具函数
        var _ = (function() {
            // 简单的 Lodash 核心实现
            return {
                isEmpty: function(value) {
                    return value === null || value === undefined || 
                           (Array.isArray(value) && value.length === 0) ||
                           (typeof value === 'object' && Object.keys(value).length === 0);
                },
                isString: function(value) {
                    return typeof value === 'string';
                },
                isNumber: function(value) {
                    return typeof value === 'number' && !isNaN(value);
                },
                isBoolean: function(value) {
                    return typeof value === 'boolean';
                },
                isObject: function(value) {
                    return typeof value === 'object' && value !== null && !Array.isArray(value);
                },
                isArray: function(value) {
                    return Array.isArray(value);
                },
                forEach: function(collection, iteratee) {
                    if (Array.isArray(collection)) {
                        for (let i = 0; i < collection.length; i++) {
                            iteratee(collection[i], i, collection);
                        }
                    } else if (typeof collection === 'object' && collection !== null) {
                        for (let key in collection) {
                            if (collection.hasOwnProperty(key)) {
                                iteratee(collection[key], key, collection);
                            }
                        }
                    }
                    return collection;
                },
                map: function(collection, iteratee) {
                    const result = [];
                    if (Array.isArray(collection)) {
                        for (let i = 0; i < collection.length; i++) {
                            result.push(iteratee(collection[i], i, collection));
                        }
                    } else if (typeof collection === 'object' && collection !== null) {
                        for (let key in collection) {
                            if (collection.hasOwnProperty(key)) {
                                result.push(iteratee(collection[key], key, collection));
                            }
                        }
                    }
                    return result;
                }
            };
        })();
        
        // 简单的数据处理库
        var dataUtils = {
            parseJson: function(jsonString) {
                try {
                    return JSON.parse(jsonString);
                } catch (e) {
                    return null;
                }
            },
            stringifyJson: function(obj) {
                try {
                    return JSON.stringify(obj);
                } catch (e) {
                    return "{}";
                }
            },
            formatDate: function(date) {
                if (!date) date = new Date();
                if (typeof date === 'string') date = new Date(date);
                
                return date.getFullYear() + '-' + 
                       String(date.getMonth() + 1).padStart(2, '0') + '-' + 
                       String(date.getDate()).padStart(2, '0') + ' ' + 
                       String(date.getHours()).padStart(2, '0') + ':' + 
                       String(date.getMinutes()).padStart(2, '0') + ':' + 
                       String(date.getSeconds()).padStart(2, '0');
            }
        };
    """.trimIndent()
    
    /**
     * JavaScript 接口，提供 Native 调用方法
     */
    @Keep
    inner class JsToolCallInterface {
        
        /**
         * 同步工具调用（旧版本，保留兼容性）
         */
        @JavascriptInterface
        fun callTool(toolType: String, toolName: String, paramsJson: String): String {
            try {
                // 解析参数
                val params = mutableMapOf<String, String>()
                val jsonObject = JSONObject(paramsJson)
                jsonObject.keys().forEach { key ->
                    params[key] = jsonObject.opt(key)?.toString() ?: ""
                }
                
                // 调用工具
                Log.d(TAG, "[Sync] JavaScript tool call: $toolType:$toolName with params: $params")
                
                // 参数验证
                if (toolName.isEmpty()) {
                    Log.e(TAG, "Tool name cannot be empty")
                    return "Error: Tool name cannot be empty"
                }
                
                // 构建工具参数
                val toolParameters = params.map { (name, value) ->
                    ToolParameter(name = name, value = value)
                }
                
                // 构建完整工具名称 (如果有类型则使用类型:名称格式，否则直接使用名称)
                val fullToolName = if (toolType.isNotEmpty() && toolType != "default") {
                    "$toolType:$toolName"
                } else {
                    toolName
                }
                
                // 创建工具调用对象
                val aiTool = AITool(
                    name = fullToolName,
                    parameters = toolParameters
                )
                
                Log.d(TAG, "Executing tool (sync): $fullToolName")
                
                // 使用 AIToolHandler 执行工具
                val result = toolHandler.executeTool(aiTool)
                
                // 记录执行结果
                if (result.success) {
                    val resultString = result.result.toString()
                    Log.d(TAG, "[Sync] Tool execution succeeded: ${resultString.take(10000)}${if (resultString.length > 10000) "..." else ""}")
                } else {
                    Log.e(TAG, "[Sync] Tool execution failed: ${result.error}")
                }
                
                // 返回结果
                return if (result.success) {
                    // Convert tool result to JSON for proper handling of structured data
                    val resultJson = Json.encodeToString(
                        JsonElement.serializer(),
                        buildJsonObject {
                            put("success", JsonPrimitive(true))
                            
                            // Handle different result data types
                            when (val resultData = result.result) {
                                is StringResultData -> put("data", JsonPrimitive(resultData.value))
                                is BooleanResultData -> put("data", JsonPrimitive(resultData.value))
                                is IntResultData -> put("data", JsonPrimitive(resultData.value))
                                else -> {
                                    // 使用 toJson 方法获取 JSON 字符串
                                    val jsonString = resultData.toJson()
                                    // 确保获取的是有效的 JSON
                                    try {
                                        put("data", Json.parseToJsonElement(jsonString))
                                    } catch (e: Exception) {
                                        put("data", JsonPrimitive(jsonString))
                                    }
                                }
                            }
                        }
                    )
                    resultJson
                } else {
                    // Return error as JSON
                    Json.encodeToString(
                        JsonElement.serializer(),
                        buildJsonObject {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive(result.error ?: "Unknown error"))
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Sync] Error in tool call: ${e.message}", e)
                return Json.encodeToString(
                    JsonElement.serializer(),
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Error: ${e.message}"))
                    }
                )
            }
        }
        
        /**
         * 异步工具调用（新版本，使用Promise）
         */
        @JavascriptInterface
        fun callToolAsync(callbackId: String, toolType: String, toolName: String, paramsJson: String) {
            try {
                // 解析参数
                val params = mutableMapOf<String, String>()
                val jsonObject = JSONObject(paramsJson)
                jsonObject.keys().forEach { key ->
                    params[key] = jsonObject.opt(key)?.toString() ?: ""
                }
                
                // 调用工具
                Log.d(TAG, "[Async] JavaScript tool call: $toolType:$toolName with params: $params, callbackId: $callbackId")
                
                // 参数验证
                if (toolName.isEmpty()) {
                    Log.e(TAG, "Tool name cannot be empty")
                    val errorJson = Json.encodeToString(
                        JsonElement.serializer(),
                        buildJsonObject {
                            put("success", JsonPrimitive(false))
                            put("error", JsonPrimitive("Tool name cannot be empty"))
                        }
                    )
                    sendToolResult(callbackId, errorJson, true)
                    return
                }
                
                // 构建工具参数
                val toolParameters = params.map { (name, value) ->
                    ToolParameter(name = name, value = value)
                }
                
                // 构建完整工具名称 (如果有类型则使用类型:名称格式，否则直接使用名称)
                val fullToolName = if (toolType.isNotEmpty() && toolType != "default") {
                    "$toolType:$toolName"
                } else {
                    toolName
                }
                
                // 创建工具调用对象
                val aiTool = AITool(
                    name = fullToolName,
                    parameters = toolParameters
                )
                
                Log.d(TAG, "Executing tool (async): $fullToolName")
                
                // 在后台线程中执行工具调用
                Thread {
                    try {
                        // 使用 AIToolHandler 执行工具
                        val result = toolHandler.executeTool(aiTool)
                        
                        // 记录执行结果
                        if (result.success) {
                            val resultString = result.result.toString()
                            Log.d(TAG, "[Async] Tool execution succeeded: ${resultString.take(10000)}${if (resultString.length > 10000) "..." else ""}")
                            // 发送成功结果回调
                            val resultJson = Json.encodeToString(
                                JsonElement.serializer(),
                                buildJsonObject {
                                    put("success", JsonPrimitive(true))
                                    
                                    // Handle different result data types
                                    when (val resultData = result.result) {
                                        is StringResultData -> put("data", JsonPrimitive(resultData.value))
                                        is BooleanResultData -> put("data", JsonPrimitive(resultData.value))
                                        is IntResultData -> put("data", JsonPrimitive(resultData.value))
                                        else -> {
                                            // 使用 toJson 方法获取 JSON 字符串
                                            val jsonString = resultData.toJson()
                                            // 确保获取的是有效的 JSON
                                            try {
                                                put("data", Json.parseToJsonElement(jsonString))
                                            } catch (e: Exception) {
                                                put("data", JsonPrimitive(jsonString))
                                            }
                                        }
                                    }
                                }
                            )
                            sendToolResult(callbackId, resultJson, false)
                        } else {
                            Log.e(TAG, "[Async] Tool execution failed: ${result.error}")
                            // 发送错误结果回调
                            val errorJson = Json.encodeToString(
                                JsonElement.serializer(),
                                buildJsonObject {
                                    put("success", JsonPrimitive(false))
                                    put("error", JsonPrimitive(result.error ?: "Unknown error"))
                                }
                            )
                            sendToolResult(callbackId, errorJson, true)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[Async] Error in async tool execution: ${e.message}", e)
                        // 发送异常结果回调
                        val errorJson = Json.encodeToString(
                            JsonElement.serializer(),
                            buildJsonObject {
                                put("success", JsonPrimitive(false))
                                put("error", JsonPrimitive("Error: ${e.message}"))
                            }
                        )
                        sendToolResult(callbackId, errorJson, true)
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "[Async] Error setting up async tool call: ${e.message}", e)
                val errorJson = Json.encodeToString(
                    JsonElement.serializer(),
                    buildJsonObject {
                        put("success", JsonPrimitive(false))
                        put("error", JsonPrimitive("Error: ${e.message}"))
                    }
                )
                sendToolResult(callbackId, errorJson, true)
            }
        }
        
        /**
         * 向JavaScript发送工具调用结果
         */
        private fun sendToolResult(callbackId: String, result: String, isError: Boolean) {
            ContextCompat.getMainExecutor(context).execute {
                try {
                    // If the result is already a JSON string, don't escape it further
                    val jsCode = if (result.trim().startsWith("{") || result.trim().startsWith("[")) {
                        """
                            if (typeof window['$callbackId'] === 'function') {
                                window['$callbackId'](${result}, $isError);
                            } else {
                                console.error("Callback not found: $callbackId");
                            }
                        """.trimIndent()
                    } else {
                        val escapedResult = result.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                        """
                            if (typeof window['$callbackId'] === 'function') {
                                window['$callbackId']("$escapedResult", $isError);
                            } else {
                                console.error("Callback not found: $callbackId");
                            }
                        """.trimIndent()
                    }
                    webView?.evaluateJavascript(jsCode, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending tool result to JavaScript: ${e.message}", e)
                }
            }
        }
        
        @JavascriptInterface
        fun setResult(result: String) {
            try {
                // 加入更详细的日志，帮助排查异步问题
                Log.d(TAG, "Setting result from JavaScript: length=${result.length}, callback=${resultCallback != null}, isDone=${resultCallback?.isDone}")
                
                // 确保回调仍然有效
                if (resultCallback == null) {
                    Log.e(TAG, "Result callback is null when trying to complete")
                    return
                }
                
                if (resultCallback!!.isDone) {
                    Log.w(TAG, "Result callback is already completed when trying to set result")
                    return
                }
                
                // 使用主线程执行complete操作，避免可能的线程问题
                ContextCompat.getMainExecutor(context).execute {
                    try {
                        // 返回成功结果
                        if (!resultCallback!!.isDone) {
                            Log.d(TAG, "Actually completing the result callback")
                            resultCallback!!.complete(result)
                        } else {
                            Log.w(TAG, "Callback became complete between check and execution")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error completing result on main thread: ${e.message}", e)
                        if (!resultCallback!!.isDone) {
                            resultCallback!!.completeExceptionally(e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting result: ${e.message}", e)
                resultCallback?.completeExceptionally(e)
            }
        }
        
        @JavascriptInterface
        fun setError(error: String) {
            try {
                // 加入更详细的日志
                Log.d(TAG, "Setting error from JavaScript: $error, callback=${resultCallback != null}, isDone=${resultCallback?.isDone}")
                
                // 确保回调仍然有效
                if (resultCallback == null) {
                    Log.e(TAG, "Result callback is null when trying to complete with error")
                    return
                }
                
                if (resultCallback!!.isDone) {
                    Log.w(TAG, "Result callback is already completed when trying to set error")
                    return
                }
                
                // 使用主线程执行complete操作
                ContextCompat.getMainExecutor(context).execute {
                    // 返回错误结果
                    if (!resultCallback!!.isDone) {
                        resultCallback!!.complete("Error: $error")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting error result: ${e.message}", e)
                resultCallback?.completeExceptionally(e)
            }
        }
        
        @JavascriptInterface
        fun logInfo(message: String) {
            Log.i(TAG, "JS: $message")
        }
        
        @JavascriptInterface
        fun logError(message: String) {
            Log.e(TAG, "JS ERROR: $message")
        }
        
        @JavascriptInterface
        fun logDebug(message: String, data: String) {
            Log.d(TAG, "JS DEBUG: $message | $data")
        }
        
        @JavascriptInterface
        fun reportError(errorType: String, errorMessage: String, errorLine: Int, errorStack: String) {
            Log.e(TAG, "DETAILED JS ERROR: \nType: $errorType\nMessage: $errorMessage\nLine: $errorLine\nStack: $errorStack")
        }
    }
    
    /**
     * 销毁引擎资源
     */
    fun destroy() {
        try {
            // 确保任何挂起的回调被完成
            resultCallback?.complete("Engine destroyed")
            resultCallback = null
            
            // 清理所有待处理的工具调用回调
            toolCallbacks.forEach { (_, future) ->
                if (!future.isDone) {
                    future.complete("Engine destroyed")
                }
            }
            toolCallbacks.clear()
            
            // 在主线程中销毁 WebView
            ContextCompat.getMainExecutor(context).execute {
                try {
                    webView?.apply {
                        removeJavascriptInterface("NativeInterface")
                        loadUrl("about:blank")
                        clearHistory()
                        clearCache(true)
                        destroy()
                    }
                    webView = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error destroying WebView: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during JsEngine destruction: ${e.message}", e)
        }
    }
    
    /**
     * 处理引擎异常
     */
    private fun handleException(e: Exception): String {
        Log.e(TAG, "JsEngine exception: ${e.message}", e)
        
        // 尝试重置当前状态
        try {
            resetState()
        } catch (resetEx: Exception) {
            Log.e(TAG, "Failed to reset state after exception: ${resetEx.message}", resetEx)
        }
        
        return "Error: ${e.message}"
    }
    
    /**
     * 诊断引擎状态
     * 用于调试目的，记录当前状态信息
     */
    fun diagnose() {
        try {
            Log.d(TAG, "=== JsEngine Diagnostics ===")
            Log.d(TAG, "WebView initialized: ${webView != null}")
            Log.d(TAG, "Result callback: ${resultCallback?.isDone ?: "null"}")
            Log.d(TAG, "Tool callbacks pending: ${toolCallbacks.size}")
            
            // 检查WebView状态
            if (webView != null) {
                ContextCompat.getMainExecutor(context).execute {
                    webView?.evaluateJavascript("""
                        (function() {
                            var result = {
                                memory: (window.performance && window.performance.memory) 
                                    ? {
                                        totalJSHeapSize: window.performance.memory.totalJSHeapSize,
                                        usedJSHeapSize: window.performance.memory.usedJSHeapSize,
                                        jsHeapSizeLimit: window.performance.memory.jsHeapSizeLimit
                                      } 
                                    : "Not available",
                                timers: "Unable to count"
                            };
                            
                            // 尝试估计定时器数量
                            try {
                                var count = 0;
                                var id = setTimeout(function(){}, 0);
                                clearTimeout(id);
                                result.timers = id;
                            } catch(e) {}
                            
                            return JSON.stringify(result);
                        })();
                    """) { diagResult ->
                        Log.d(TAG, "WebView diagnostics: $diagResult")
                    }
                }
            }
            
            Log.d(TAG, "=========================")
        } catch (e: Exception) {
            Log.e(TAG, "Error during diagnostics: ${e.message}", e)
        }
    }
} 