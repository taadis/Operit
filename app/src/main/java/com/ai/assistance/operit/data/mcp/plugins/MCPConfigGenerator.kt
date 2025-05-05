package com.ai.assistance.operit.data.mcp.plugins

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ai.assistance.operit.data.mcp.MCPVscodeConfig
import java.io.File

/**
 * MCP 配置生成器
 *
 * 负责生成MCP插件的配置文件
 */
class MCPConfigGenerator {

    companion object {
        private const val TAG = "MCPConfigGenerator"
    }
    
    /**
     * 生成MCP配置
     *
     * @param pluginId 插件ID
     * @param projectStructure 项目结构
     * @return MCP配置JSON
     */
    fun generateMcpConfig(pluginId: String, projectStructure: ProjectStructure): String {
        // 如果从README提取到了配置示例，优先使用
        if (projectStructure.configExample != null) {
            try {
                // 验证JSON是否有效
                val jsonObject = JsonParser.parseString(projectStructure.configExample).asJsonObject
                if (jsonObject.has("mcpServers")) {
                    return projectStructure.configExample
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析配置示例失败，将使用生成的配置", e)
            }
        }

        // 根据项目类型生成配置
        val configJson = JsonObject()
        val mcpServersJson = JsonObject()
        val serverJson = JsonObject()

        // 设置命令和参数
        when (projectStructure.type) {
            ProjectType.PYTHON -> {
                serverJson.addProperty("command", "python")

                val argsArray = com.google.gson.JsonArray()
                argsArray.add("-m")
                val moduleName = projectStructure.moduleNameFromConfig
                        ?: projectStructure.mainPythonModule
                        ?: pluginId.replace("-", "_").lowercase()
                argsArray.add(moduleName)

                serverJson.add("args", argsArray)
            }
            ProjectType.TYPESCRIPT -> {
                // 对于TypeScript项目，我们需要查找编译后的JS文件
                serverJson.addProperty("command", "node")

                val argsArray = com.google.gson.JsonArray()

                // 根据项目结构决定可能的输出路径
                val mainTsFile = projectStructure.mainTsFile
                if (mainTsFile != null) {
                    // 尝试确定编译输出位置
                    if (mainTsFile.startsWith("src/")) {
                        // src/ -> dist/ 是常见的TypeScript输出转换
                        val compiledPath = mainTsFile.replace("src/", "dist/").replace(".ts", ".js")
                        argsArray.add(compiledPath)
                    } else {
                        // 直接替换扩展名
                        val compiledPath = mainTsFile.replace(".ts", ".js")
                        argsArray.add(compiledPath)
                    }
                } else {
                    // 如果没有找到主TS文件，使用常见的输出位置
                    argsArray.add("dist/index.js")
                }

                serverJson.add("args", argsArray)
            }
            ProjectType.NODEJS -> {
                serverJson.addProperty("command", "node")

                val argsArray = com.google.gson.JsonArray()
                val mainFile = projectStructure.mainJsFile ?: "index.js"
                argsArray.add(mainFile)

                serverJson.add("args", argsArray)
            }
            else -> {
                // 使用默认配置
                serverJson.addProperty("command", "python")

                val argsArray = com.google.gson.JsonArray()
                argsArray.add("-m")
                argsArray.add(pluginId.replace("-", "_").lowercase())

                serverJson.add("args", argsArray)
            }
        }

        serverJson.addProperty("disabled", false)

        val autoApproveArray = com.google.gson.JsonArray()
        serverJson.add("autoApprove", autoApproveArray)

        // 构建完整配置
        val serverName = pluginId.split("/").last().lowercase()
        mcpServersJson.add(serverName, serverJson)
        configJson.add("mcpServers", mcpServersJson)

        return Gson().toJson(configJson)
    }
    
    /**
     * 保存MCP配置到文件
     *
     * @param pluginId 插件ID
     * @param config 配置内容
     * @param serverPath 服务器路径
     * @return 保存结果
     */
    fun saveMcpConfig(pluginId: String, config: String, serverPath: String): Boolean {
        try {
            val pluginDir = File(serverPath, "plugins/$pluginId")
            if (!pluginDir.exists()) {
                pluginDir.mkdirs()
            }

            val configFile = File(pluginDir, "config.json")
            configFile.writeText(config)
            
            Log.d(TAG, "已保存配置到: ${configFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "保存配置失败: $pluginId", e)
            return false
        }
    }
    
    /**
     * 从配置文件中提取服务器名称
     *
     * @param configJson 配置JSON
     * @return 服务器名称，如果解析失败则返回null
     */
    fun extractServerNameFromConfig(configJson: String): String? {
        return MCPVscodeConfig.extractServerName(configJson)
    }
} 