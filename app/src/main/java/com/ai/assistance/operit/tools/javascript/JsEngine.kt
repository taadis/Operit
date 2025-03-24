package com.ai.assistance.operit.tools.javascript

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.packTool.PackageManager
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
        private const val TIMEOUT_SECONDS = 30L
    }
    
    // WebView 实例用于执行 JavaScript
    private var webView: WebView? = null
    
    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)
    
    // 工具调用接口
    private val toolCallInterface = JsToolCallInterface()
    
    // 结果回调
    private var resultCallback: CompletableFuture<Any?>? = null
    
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
     * 执行 JavaScript 脚本
     * @param script JavaScript 脚本内容
     * @param params 脚本参数
     * @return 脚本执行结果
     */
    fun executeScript(script: String, params: Map<String, String>): Any? {
        initWebView()
        
        val future = CompletableFuture<Any?>()
        resultCallback = future
        
        // 将参数转换为 JSON 对象
        val paramsJson = JSONObject(params).toString()
        
        // 包装脚本，添加工具调用函数和参数
        val wrappedScript = """
            // 设置参数
            var params = $paramsJson;
            
            // 定义 toolCall 函数 - 支持多种参数传递方式
            function toolCall(toolType, toolName, toolParams) {
                // 处理不同的参数调用模式
                if (arguments.length === 1 && typeof toolType === 'object') {
                    // 对象模式: toolCall({type: "...", name: "...", params: {...}})
                    const config = toolType;
                    return NativeInterface.callTool(
                        config.type || "default", 
                        config.name || "", 
                        JSON.stringify(config.params || {})
                    );
                } else if (arguments.length === 1 && typeof toolType === 'string') {
                    // 字符串模式: toolCall("toolName")
                    return NativeInterface.callTool("default", toolType, "{}");
                } else if (arguments.length === 2 && typeof toolName === 'object') {
                    // 工具名+参数模式: toolCall("toolName", {param1: "value1"})
                    return NativeInterface.callTool("default", toolType, JSON.stringify(toolName || {}));
                } else {
                    // 标准模式: toolCall("toolType", "toolName", {param1: "value1"})
                    return NativeInterface.callTool(
                        toolType || "default", 
                        toolName || "", 
                        JSON.stringify(toolParams || {})
                    );
                }
            }
            
            // 工具调用的便捷方法
            const Tools = {
                // 文件系统操作
                Files: {
                    list: (path) => toolCall("list_files", { path }),
                    read: (path) => toolCall("read_file", { path }),
                    write: (path, content) => toolCall("write_file", { path, content }),
                    delete: (path) => toolCall("delete_file", { path }),
                    exists: (path) => toolCall("file_exists", { path }),
                    move: (source, target) => toolCall("move_file", { source, target }),
                    copy: (source, target) => toolCall("copy_file", { source, target }),
                    mkdir: (path) => toolCall("make_directory", { path }),
                    find: (path, pattern) => toolCall("find_files", { path, pattern }),
                    info: (path) => toolCall("file_info", { path }),
                    zip: (source, target) => toolCall("zip_files", { source, target }),
                    unzip: (source, target) => toolCall("unzip_files", { source, target }),
                    open: (path) => toolCall("open_file", { path })
                },
                // 网络操作
                Net: {
                    httpGet: (url) => toolCall("http_get", { url }),
                    httpPost: (url, data) => toolCall("http_post", { url, data }),
                    search: (query) => toolCall("web_search", { query })
                },
                // 系统操作
                System: {
                    exec: (command) => toolCall("execute_command", { command }),
                    sleep: (seconds) => toolCall("sleep", { seconds })
                },
                // 计算功能
                calc: (expression) => toolCall("calculate", { expression })
            };
            
            // 定义完成回调
            function complete(result) {
                NativeInterface.setResult(JSON.stringify(result));
            }
            
            // 添加第三方库支持
            $THIRD_PARTY_LIBS
            
            // 用户脚本
            try {
                $script
                
                // 如果脚本没有明确调用complete()，尝试返回最后一个表达式的值
                NativeInterface.setResult("Script executed without explicit result");
            } catch (error) {
                NativeInterface.setError("Script error: " + error.message);
            }
        """.trimIndent()
        
        // 在主线程中执行脚本
        ContextCompat.getMainExecutor(context).execute {
            webView?.evaluateJavascript(wrappedScript) { result ->
                Log.d(TAG, "Script evaluation completed with: $result")
            }
        }
        
        // 等待结果或超时
        return try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Script execution timed out or failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * 加载常用的第三方 JavaScript 库
     * 可以根据需要添加更多库
     */
    private val THIRD_PARTY_LIBS = """
        // Lodash 核心功能
        // 轻量级版本，包含最常用的工具函数
        const _ = (function() {
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
        const dataUtils = {
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
                Log.d(TAG, "JavaScript tool call: $toolType:$toolName with params: $params")
                
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
                
                Log.d(TAG, "Executing tool: $fullToolName")
                
                // 使用 AIToolHandler 执行工具
                val result = toolHandler.executeTool(aiTool)
                
                // 记录执行结果
                if (result.success) {
                    Log.d(TAG, "Tool execution succeeded: ${result.result.take(100)}${if (result.result.length > 100) "..." else ""}")
                } else {
                    Log.e(TAG, "Tool execution failed: ${result.error}")
                }
                
                // 返回结果
                return if (result.success) {
                    result.result
                } else {
                    "Error: ${result.error}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in tool call: ${e.message}", e)
                return "Error: ${e.message}"
            }
        }
        
        @JavascriptInterface
        fun setResult(result: String) {
            try {
                // 返回成功结果
                resultCallback?.complete(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting result: ${e.message}", e)
                resultCallback?.completeExceptionally(e)
            }
        }
        
        @JavascriptInterface
        fun setError(error: String) {
            // 返回错误结果
            resultCallback?.complete("Error: $error")
        }
    }
} 