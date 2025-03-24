package com.ai.assistance.operit.tools.javascript

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.packTool.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * JavaScript Tool Manager - 管理 JavaScript 脚本执行和工具调用
 * 此类替代了原来的 OperScript 实现，提供更简洁的 JavaScript 集成
 */
class JsToolManager private constructor(
    private val context: Context,
    private val packageManager: PackageManager
) {
    companion object {
        private const val TAG = "JsToolManager"
        
        @Volatile
        private var INSTANCE: JsToolManager? = null
        
        fun getInstance(context: Context, packageManager: PackageManager): JsToolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JsToolManager(context.applicationContext, packageManager).also { INSTANCE = it }
            }
        }
    }
    
    // 缓存已加载的 JavaScript 引擎
    private val scriptEngineCache = ConcurrentHashMap<String, JsEngine>()
    
    // 最大缓存大小
    private val MAX_CACHE_SIZE = 5
    
    // 最后一次使用时间记录
    private val lastUsedTime = ConcurrentHashMap<String, Long>()
    
    /**
     * 执行 JavaScript 工具调用
     * @param script JavaScript 脚本内容
     * @param tool 要执行的工具
     * @return 工具执行结果
     */
    suspend fun executeScript(script: String, tool: AITool): ToolResult {
        // Execute script on background thread using IO dispatcher
        return withContext(Dispatchers.IO) {
            executeScriptInternal(script, tool)
        }
    }
    
    /**
     * 在后台线程上执行 JavaScript
     */
    private fun executeScriptInternal(script: String, tool: AITool): ToolResult {
        try {
            Log.d(TAG, "Executing JavaScript: ${tool.name}")
            
            // 获取或创建 JavaScript 引擎 - 使用可靠的缓存机制
            val jsEngine = scriptEngineCache.getOrPut(tool.name) {
                Log.d(TAG, "Creating new JavaScript engine for: ${tool.name}")
                JsEngine(context)
            }
            
            // 设置工具参数
            val params = mutableMapOf<String, String>()
            tool.parameters.forEach { param ->
                params[param.name] = param.value
            }
            
            // 执行脚本
            val result = jsEngine.executeScript(script, params)
            
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = result.toString(),
                error = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing JavaScript: ${e.message}", e)
            
            // 如果执行失败，清理这个工具的引擎，下次会创建新的
            try {
                cleanupTool(tool.name)
            } catch (cleanupEx: Exception) {
                Log.e(TAG, "Failed to cleanup engine after error: ${cleanupEx.message}")
            }
            
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Script execution error: ${e.message}"
            )
        }
    }
    
    /**
     * 清理过多的缓存条目
     */
    private fun cleanupCache() {
        if (scriptEngineCache.size > MAX_CACHE_SIZE) {
            // 按最后使用时间排序，保留最近使用的
            val sortedEntries = lastUsedTime.entries.sortedBy { it.value }
            
            // 移除最老的条目，直到缓存大小达到限制
            val entriesToRemove = sortedEntries.take(scriptEngineCache.size - MAX_CACHE_SIZE)
            entriesToRemove.forEach { (key, _) ->
                scriptEngineCache.remove(key)
                lastUsedTime.remove(key)
            }
        }
    }
    
    /**
     * 调用 JavaScript 工具
     * @param toolType 工具类型
     * @param toolName 工具名称
     * @param params 参数
     * @return 工具调用结果
     */
    fun callTool(toolType: String, toolName: String, params: Map<String, String>): Any? {
        try {
            Log.d(TAG, "Calling tool: $toolType:$toolName with params: $params")
            
            // 这里可以实现具体的工具调用逻辑
            // 例如通过 packageManager 查找对应的工具并执行
            
            // 实际调用工具的逻辑
            // 此处应该集成到现有的工具执行系统
            
            return "Result from $toolType:$toolName tool call"
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool: ${e.message}", e)
            return "Error: ${e.message}"
        }
    }
    
    /**
     * 释放所有资源
     * 在应用退出或者不再需要时调用
     */
    fun destroy() {
        try {
            // 清理所有缓存的引擎
            scriptEngineCache.values.forEach { engine ->
                try {
                    engine.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error destroying engine: ${e.message}", e)
                }
            }
            
            // 清空集合
            scriptEngineCache.clear()
            lastUsedTime.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error during JsToolManager destruction: ${e.message}", e)
        }
    }
    
    /**
     * 清理特定工具的引擎
     * 在特定工具出现问题时调用
     */
    fun cleanupTool(toolName: String) {
        val keysToRemove = mutableListOf<String>()
        
        // 查找包含工具名的所有键
        scriptEngineCache.keys.forEach { key ->
            if (key.startsWith(toolName)) {
                keysToRemove.add(key)
            }
        }
        
        // 移除并销毁对应的引擎
        keysToRemove.forEach { key ->
            scriptEngineCache[key]?.destroy()
            scriptEngineCache.remove(key)
            lastUsedTime.remove(key)
        }
        
        Log.d(TAG, "Cleaned up ${keysToRemove.size} engines for tool: $toolName")
    }
} 