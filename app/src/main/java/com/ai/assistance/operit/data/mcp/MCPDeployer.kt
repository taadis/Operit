package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSession
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * MCP 插件部署工具类
 *
 * 负责分析插件项目结构，确定部署策略，并执行部署命令
 */
class MCPDeployer(private val context: Context) {

    // 创建一个协程作用域，用于处理异步操作
    private val deployerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MCPDeployer"
        private const val README_FILENAME = "README.md"
        private const val ALT_README_FILENAME = "readme.md"
        private const val INSTALL_MARKDOWN_FILENAME = "INSTALL.md"

        // 用于匹配安装命令的关键词
        private val INSTALL_KEYWORDS =
                listOf("安装", "部署", "启动", "运行", "执行", "install", "deploy", "start", "run", "execute")

        // 用于匹配命令块的正则表达式
        private val CODE_BLOCK_REGEX = "```(?:bash|shell|cmd|sh|json)?([\\s\\S]*?)```".toRegex()

        // 单行命令的正则表达式
        private val INLINE_CODE_REGEX = "`([^`]+)`".toRegex()

        // JSON配置块正则表达式
        private val JSON_CONFIG_REGEX = "\\{[\\s\\S]*?\"mcpServers\"[\\s\\S]*?\\}".toRegex()

        // 模块名正则表达式
        private val MODULE_NAME_REGEX =
                "\"args\"\\s*:\\s*\\[[^\\]]*\"([^\"]+)\"[^\\]]*\\]".toRegex()
    }

    // 项目类型
    enum class ProjectType {
        PYTHON,
        NODEJS,
        TYPESCRIPT,
        UNKNOWN
    }

    // 部署状态
    sealed class DeploymentStatus {
        object NotStarted : DeploymentStatus()
        data class InProgress(val message: String) : DeploymentStatus()
        data class Success(val message: String) : DeploymentStatus()
        data class Error(val message: String) : DeploymentStatus()
    }

    // 项目结构信息
    data class ProjectStructure(
            val type: ProjectType,
            val hasRequirementsTxt: Boolean = false,
            val hasPyprojectToml: Boolean = false,
            val hasSetupPy: Boolean = false,
            val hasPackageJson: Boolean = false,
            val hasTsConfig: Boolean = false,
            val mainPythonModule: String? = null,
            val mainJsFile: String? = null,
            val mainTsFile: String? = null,
            val hasTsFiles: Boolean = false,
            val configExample: String? = null,
            val moduleNameFromConfig: String? = null,
            val hasTypeScriptDependency: Boolean = false,
            val packageJsonScripts: JSONObject? = null,
            val packageJsonContent: String? = null
    )

    // 创建或获取终端会话
    private fun getTerminalSession(): TerminalSession {
        val sessionManager = TerminalSessionManager

        // 检查是否有现有会话，有则使用第一个，没有则创建新会话
        return if (sessionManager.getSessionCount() > 0) {
            sessionManager.getActiveSession() ?: sessionManager.createSession("MCP部署")
        } else {
            sessionManager.createSession("MCP部署")
        }
    }

    /**
     * 分析项目结构
     *
     * @param pluginDir 插件目录
     * @param readmeContent README内容
     * @return 项目结构信息
     */
    private fun analyzeProjectStructure(pluginDir: File, readmeContent: String): ProjectStructure {
        // 检查文件存在性
        val hasRequirementsTxt = File(pluginDir, "requirements.txt").exists()
        val hasPyprojectToml = File(pluginDir, "pyproject.toml").exists()
        val hasSetupPy = File(pluginDir, "setup.py").exists()
        val hasPackageJson = File(pluginDir, "package.json").exists()
        val hasTsConfig = File(pluginDir, "tsconfig.json").exists()

        // 查找入口文件
        val pythonFiles =
                pluginDir.listFiles { file -> file.extension.equals("py", ignoreCase = true) }
        val jsFiles = pluginDir.listFiles { file -> file.extension.equals("js", ignoreCase = true) }
        val tsFiles =
                pluginDir.listFiles { file ->
                    file.extension.equals("ts", ignoreCase = true) ||
                            file.extension.equals("tsx", ignoreCase = true)
                }
        val hasTsFiles = tsFiles != null && tsFiles.isNotEmpty()

        val mainPythonModule = findMainPythonModule(pluginDir, pythonFiles)
        val mainJsFile = findMainJsFile(pluginDir, jsFiles)
        val mainTsFile = findMainTsFile(pluginDir, tsFiles)

        // 检查package.json中是否有TypeScript依赖
        var hasTypeScriptDependency = false
        var packageJsonContent: String? = null
        var packageJsonScripts: JSONObject? = null

        if (hasPackageJson) {
            val packageJsonFile = File(pluginDir, "package.json")
            try {
                // 读取并保存package.json内容
                packageJsonContent = packageJsonFile.readText()
                val packageJson = JSONObject(packageJsonContent)

                // 检查dependencies和devDependencies中是否有TypeScript相关依赖
                val dependencies = packageJson.optJSONObject("dependencies")
                val devDependencies = packageJson.optJSONObject("devDependencies")

                if (dependencies != null &&
                                (dependencies.has("typescript") || dependencies.has("ts-node"))
                ) {
                    hasTypeScriptDependency = true
                } else if (devDependencies != null &&
                                (devDependencies.has("typescript") ||
                                        devDependencies.has("ts-node"))
                ) {
                    hasTypeScriptDependency = true
                }

                // 提取并保存scripts部分
                val scripts = packageJson.optJSONObject("scripts")
                if (scripts != null) {
                    packageJsonScripts = scripts
                    for (key in scripts.keys()) {
                        val value = scripts.optString(key)
                        if (value.contains("tsc") ||
                                        value.contains("ts-node") ||
                                        value.contains("typescript")
                        ) {
                            hasTypeScriptDependency = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析package.json失败", e)
            }
        }

        // 从README中提取配置示例
        val configExample = extractConfigExample(readmeContent)

        // 从配置示例中提取模块名
        val moduleNameFromConfig = extractModuleNameFromConfig(configExample)

        // 确定项目类型
        val projectType =
                when {
                    // TypeScript项目特征: 有tsconfig.json或.ts文件
                    hasTsConfig || hasTsFiles || hasTypeScriptDependency -> ProjectType.TYPESCRIPT
                    // Node.js项目特征
                    hasPackageJson || (jsFiles != null && jsFiles.isNotEmpty()) ->
                            ProjectType.NODEJS
                    // Python项目特征
                    hasRequirementsTxt ||
                            hasPyprojectToml ||
                            hasSetupPy ||
                            (pythonFiles != null && pythonFiles.isNotEmpty()) -> ProjectType.PYTHON
                    else -> ProjectType.UNKNOWN
                }

        return ProjectStructure(
                type = projectType,
                hasRequirementsTxt = hasRequirementsTxt,
                hasPyprojectToml = hasPyprojectToml,
                hasSetupPy = hasSetupPy,
                hasPackageJson = hasPackageJson,
                hasTsConfig = hasTsConfig,
                mainPythonModule = mainPythonModule,
                mainJsFile = mainJsFile,
                mainTsFile = mainTsFile,
                hasTsFiles = hasTsFiles,
                configExample = configExample,
                moduleNameFromConfig = moduleNameFromConfig,
                hasTypeScriptDependency = hasTypeScriptDependency,
                packageJsonScripts = packageJsonScripts,
                packageJsonContent = packageJsonContent
        )
    }

    /** 查找主Python模块 */
    private fun findMainPythonModule(pluginDir: File, pythonFiles: Array<File>?): String? {
        // 检查常见的Python入口文件
        val commonEntryFiles = listOf("main.py", "__main__.py", "app.py", "server.py")

        // 检查src目录下是否有与目录同名的Python包
        val srcDir = File(pluginDir, "src")
        if (srcDir.exists() && srcDir.isDirectory) {
            val packages = srcDir.listFiles { file -> file.isDirectory }
            packages?.forEach { pkg ->
                val initFile = File(pkg, "__init__.py")
                if (initFile.exists()) {
                    return pkg.name
                }
            }
        }

        // 检查常见入口文件
        commonEntryFiles.forEach { filename ->
            val file = File(pluginDir, filename)
            if (file.exists()) {
                // 从文件名推断模块名
                val moduleName = filename.removeSuffix(".py")
                if (moduleName != "__main__") {
                    return moduleName
                } else {
                    // 对于__main__.py，使用目录名作为模块名
                    return pluginDir.name.replace("-", "_").lowercase()
                }
            }
        }

        // 如果是典型的Python包结构
        val initFile = File(pluginDir, "__init__.py")
        if (initFile.exists()) {
            return pluginDir.name.replace("-", "_").lowercase()
        }

        // 最后尝试查找目录名对应的模块
        val dirNameModule = pluginDir.name.replace("-", "_").lowercase()
        val dirNameModuleFile = File(pluginDir, "${dirNameModule}.py")
        if (dirNameModuleFile.exists()) {
            return dirNameModule
        }

        return null
    }

    /** 查找主JS文件 */
    private fun findMainJsFile(pluginDir: File, jsFiles: Array<File>?): String? {
        // 如果有package.json，尝试从中提取入口点
        val packageJsonFile = File(pluginDir, "package.json")
        if (packageJsonFile.exists()) {
            try {
                val packageJson = JSONObject(packageJsonFile.readText())
                if (packageJson.has("main")) {
                    return packageJson.getString("main")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析package.json失败", e)
            }
        }

        // 检查常见的JS入口文件
        val commonEntryFiles = listOf("index.js", "server.js", "app.js", "main.js")
        commonEntryFiles.forEach { filename ->
            val file = File(pluginDir, filename)
            if (file.exists()) {
                return filename
            }
        }

        return null
    }

    /** 查找主TS文件 */
    private fun findMainTsFile(pluginDir: File, tsFiles: Array<File>?): String? {
        // 如果有package.json，尝试从中提取入口点
        val packageJsonFile = File(pluginDir, "package.json")
        if (packageJsonFile.exists()) {
            try {
                val packageJson = JSONObject(packageJsonFile.readText())

                // 检查main字段
                if (packageJson.has("main")) {
                    val mainField = packageJson.getString("main")
                    // 如果main字段是.ts文件或没有扩展名(可能是TypeScript模块)
                    if (mainField.endsWith(".ts") || !mainField.contains(".")) {
                        return mainField
                    }
                }

                // 检查特殊的源文件字段
                if (packageJson.has("source") && packageJson.getString("source").endsWith(".ts")) {
                    return packageJson.getString("source")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析package.json失败", e)
            }
        }

        // 检查src目录下的index.ts
        val srcDir = File(pluginDir, "src")
        if (srcDir.exists() && srcDir.isDirectory) {
            val srcIndexTs = File(srcDir, "index.ts")
            if (srcIndexTs.exists()) {
                return "src/index.ts"
            }

            val srcMainTs = File(srcDir, "main.ts")
            if (srcMainTs.exists()) {
                return "src/main.ts"
            }

            val srcAppTs = File(srcDir, "app.ts")
            if (srcAppTs.exists()) {
                return "src/app.ts"
            }

            val srcServerTs = File(srcDir, "server.ts")
            if (srcServerTs.exists()) {
                return "src/server.ts"
            }
        }

        // 检查常见的TS入口文件
        val commonEntryFiles = listOf("index.ts", "server.ts", "app.ts", "main.ts")
        commonEntryFiles.forEach { filename ->
            val file = File(pluginDir, filename)
            if (file.exists()) {
                return filename
            }
        }

        // 如果都没找到，返回第一个.ts文件
        if (tsFiles != null && tsFiles.isNotEmpty()) {
            return tsFiles[0].name
        }

        return null
    }

    /** 从README中提取配置示例 */
    private fun extractConfigExample(readmeContent: String): String? {
        // 尝试从代码块中找到JSON配置
        val codeBlocks = CODE_BLOCK_REGEX.findAll(readmeContent)
        for (match in codeBlocks) {
            val codeContent = match.groupValues[1].trim()
            if (codeContent.contains("\"mcpServers\"") ||
                            codeContent.contains("\"command\"") ||
                            codeContent.contains("\"args\"")
            ) {
                return codeContent
            }
        }

        // 如果代码块没找到，尝试用正则表达式匹配整个JSON配置
        val jsonMatches = JSON_CONFIG_REGEX.findAll(readmeContent)
        jsonMatches.forEach { match ->
            return match.value
        }

        return null
    }

    /** 从配置示例中提取模块名 */
    private fun extractModuleNameFromConfig(configExample: String?): String? {
        if (configExample == null) return null

        // 尝试从args数组中提取模块名
        val moduleMatches = MODULE_NAME_REGEX.find(configExample)
        moduleMatches?.let {
            return it.groupValues[1]
        }

        return null
    }

    /**
     * 生成部署命令
     *
     * @param projectStructure 项目结构
     * @param readmeContent README内容，用于查找特殊安装命令
     * @return 部署命令列表
     */
    private fun generateDeployCommands(
            projectStructure: ProjectStructure,
            readmeContent: String = ""
    ): List<String> {
        val commands = mutableListOf<String>()

        when (projectStructure.type) {
            ProjectType.PYTHON -> {
                // 首先检查README中是否有特定的pip安装命令
                val pipInstallCommand = findSpecificPipInstallCommand(readmeContent)

                if (pipInstallCommand != null) {
                    // 使用README中指定的pip安装命令
                    Log.d(TAG, "使用README中指定的pip安装命令: $pipInstallCommand")
                    commands.add(pipInstallCommand)
                } else if (projectStructure.hasRequirementsTxt) {
                    commands.add("pip install -r requirements.txt")
                } else if (projectStructure.hasPyprojectToml) {
                    commands.add("pip install .")
                } else if (projectStructure.hasSetupPy) {
                    commands.add("pip install .")
                } else {
                    // 尝试从插件名称生成安装命令
                    val packageName = extractPackageNameFromDirectory(projectStructure)
                    if (packageName != null) {
                        commands.add("pip install $packageName")
                    }
                }

                // 如果从配置中找到了模块名，优先使用
                val moduleName =
                        projectStructure.moduleNameFromConfig ?: projectStructure.mainPythonModule
                if (moduleName != null) {
                    commands.add("python -m $moduleName")
                }
            }
            ProjectType.TYPESCRIPT -> {
                Log.d(TAG, "开始配置TypeScript项目部署命令")
                // TypeScript项目部署命令
                if (projectStructure.hasPackageJson) {
                    Log.d(TAG, "检测到package.json文件存在")
                    // 添加npm换源命令 - 使用国内淘宝镜像提高安装速度和成功率
                    commands.add("npm config set registry https://registry.npmmirror.com")
                    Log.d(TAG, "添加命令: npm换源到淘宝镜像")
                    commands.add("# 如果换源失败，将继续使用默认源")

                    // 安装依赖
                    commands.add("npm install")
                    Log.d(TAG, "添加命令: npm install安装依赖")

                    // 检查是否有构建脚本
                    Log.d(TAG, "使用预先解析的package.json信息")

                    // 使用已解析的scripts信息
                    if (projectStructure.packageJsonScripts != null) {
                        val scripts = projectStructure.packageJsonScripts
                        Log.d(TAG, "找到scripts部分: ${scripts.toString()}")
                        // 优先使用build脚本
                        if (scripts.has("build")) {
                            Log.d(TAG, "找到build脚本，使用npm run build")
                            // commands.add("npm run build")
                            commands.add("node ./node_modules/typescript/bin/tsc")
                        } else if (scripts.has("compile")) {
                            Log.d(TAG, "找到compile脚本，使用npm run compile")
                            commands.add("npm run compile")
                        } else if (scripts.has("tsc")) {
                            Log.d(TAG, "找到tsc脚本，使用npm run tsc")
                            commands.add("npm run tsc")
                        } else {
                            Log.d(TAG, "未找到build/compile/tsc脚本")
                        }
                    } else if (projectStructure.hasTypeScriptDependency) {
                        Log.d(TAG, "未找到scripts部分但有TypeScript依赖，使用node调用tsc")
                        commands.add("node ./node_modules/typescript/bin/tsc")
                        Log.d(
                                TAG,
                                "添加命令: 直接调用TypeScript编译器: node ./node_modules/typescript/lib/tsc"
                        )
                    } else if (projectStructure.hasTsConfig) {
                        Log.d(TAG, "检测到tsconfig.json，尝试安装TypeScript并编译")
                        commands.add("npm install typescript --save-dev || true")
                        Log.d(TAG, "添加命令: 安装TypeScript开发依赖")
                        commands.add("node ./node_modules/typescript/bin/tsc")
                        Log.d(TAG, "添加命令: 使用node直接调用tsc编译器")
                    } else {
                        Log.d(TAG, "既没有scripts也没有TypeScript依赖，跳过TypeScript编译")
                    }

                    // 查找编译后的JS文件
                    val mainTsFile = projectStructure.mainTsFile
                    if (mainTsFile != null) {
                        Log.d(TAG, "找到主TS文件: $mainTsFile")
                        // 查找可能的编译输出目录
                        val possibleJsFile = mainTsFile.replace(".ts", ".js")
                        val possibleDistJs = "dist/" + possibleJsFile
                        val possibleBuildJs = "build/" + possibleJsFile
                        val possibleOutJs = "out/" + possibleJsFile

                        Log.d(TAG, "可能的编译后JS文件路径:")
                        Log.d(TAG, "- 直接替换: $possibleJsFile")
                        Log.d(TAG, "- dist目录: $possibleDistJs")
                        Log.d(TAG, "- build目录: $possibleBuildJs")
                        Log.d(TAG, "- out目录: $possibleOutJs")

                        // 查找哪个目录存在后再决定添加到命令中
                        // 由于这些目录只会在编译后存在，我们无法在此时检查
                        // 我们应该优先使用配置文件中可能指定的命令行参数

                        // 这里我们只添加运行命令，因为我们无法确定编译后确切的JS文件路径
                        if (projectStructure.configExample != null) {
                            // 如果有配置示例，我们将依赖它
                            Log.d(TAG, "TypeScript项目有配置示例: ${projectStructure.configExample}")
                        } else {
                            // 如果没有配置示例，添加一条注释命令
                            commands.add("# TypeScript编译后可能需要执行: node dist/index.js 或类似命令")
                            Log.d(TAG, "添加注释提示可能需要的运行命令")
                        }
                    } else {
                        Log.d(TAG, "未找到主TS文件，无法确定编译后JS文件路径")
                    }
                } else {
                    Log.d(TAG, "未找到package.json文件，无法配置TypeScript项目部署命令")
                }
                Log.d(TAG, "TypeScript项目部署命令配置完成")
            }
            ProjectType.NODEJS -> {
                // Node.js项目部署命令
                if (projectStructure.hasPackageJson) {
                    // 同样为Node.js项目添加换源设置
                    commands.add("npm config set registry https://registry.npmmirror.com")
                    commands.add("# 如果换源失败，将继续使用默认源")
                    commands.add("npm install")
                }

                val mainFile = projectStructure.mainJsFile
                if (mainFile != null) {
                    commands.add("node $mainFile")
                } else {
                    commands.add("npm start")
                }
            }
            ProjectType.UNKNOWN -> {
                // 无法确定项目类型，尝试通用命令
                Log.w(TAG, "无法确定项目类型，尝试通用命令")
            }
        }

        return commands
    }

    /**
     * 从README内容中查找特定的pip安装命令
     *
     * @param readmeContent README文件内容
     * @return 找到的特定pip安装命令，如果没找到则返回null
     */
    private fun findSpecificPipInstallCommand(readmeContent: String): String? {
        if (readmeContent.isBlank()) return null

        // 使用正则表达式查找pip install命令
        val pipInstallRegex =
                "(?:```[\\s\\S]*?)?pip install\\s+[\\w\\-_\\.]+(?:[\\s\\S]*?```)?".toRegex()
        val pipMatches = pipInstallRegex.findAll(readmeContent)

        for (match in pipMatches) {
            val command = match.value
            // 清理命令，去除可能的代码块标记
            val cleanCommand = command.replace("```", "").replace("`", "").trim()

            // 确保这是一个合法的pip install命令
            if (cleanCommand.startsWith("pip install") &&
                            !cleanCommand.contains("pip install .") &&
                            !cleanCommand.contains("pip install -r") &&
                            !cleanCommand.contains("pip install -e")
            ) {
                return cleanCommand
            }
        }

        // 没有找到特定的pip安装命令
        return null
    }

    /**
     * 从目录名提取可能的包名
     *
     * @param projectStructure 项目结构
     * @return 可能的包名，如果无法确定则返回null
     */
    private fun extractPackageNameFromDirectory(projectStructure: ProjectStructure): String? {
        // 获取插件目录名
        val pluginDir = File(projectStructure.mainPythonModule ?: "").parentFile?.parentFile
        val dirName = pluginDir?.name ?: return null

        // 常见的包名格式转换
        return when {
            dirName.startsWith("mcp-server-") -> dirName
            dirName.startsWith("mcp_server_") -> dirName
            dirName.contains("mcp") && dirName.contains("server") -> {
                // 尝试标准化包名格式
                "mcp-server-" +
                        dirName.replace("mcp-server-", "")
                                .replace("mcp_server_", "")
                                .replace("mcp-", "")
                                .replace("mcp_", "")
                                .replace("_server", "")
                                .replace("-server", "")
                                .lowercase()
            }
            else -> null
        }
    }

    /**
     * 生成MCP配置
     *
     * @param pluginId 插件ID
     * @param projectStructure 项目结构
     * @return MCP配置JSON
     */
    private fun generateMcpConfig(pluginId: String, projectStructure: ProjectStructure): String {
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
                val moduleName =
                        projectStructure.moduleNameFromConfig
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
                        if (File(compiledPath).exists()) {
                            argsArray.add(compiledPath)
                        } else {
                            // 尝试dist目录
                            argsArray.add("dist/" + compiledPath)
                        }
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
     * 部署MCP插件
     *
     * @param pluginId 插件ID
     * @param pluginPath 插件安装路径
     * @param statusCallback 部署状态回调
     */
    suspend fun deployPlugin(
            pluginId: String,
            pluginPath: String,
            statusCallback: (DeploymentStatus) -> Unit
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    statusCallback(DeploymentStatus.InProgress("开始部署插件: $pluginId"))
                    Log.d(TAG, "开始部署插件: $pluginId, 路径: $pluginPath")

                    // 验证插件路径
                    val pluginDir = File(pluginPath)
                    if (!pluginDir.exists() || !pluginDir.isDirectory) {
                        Log.e(TAG, "插件目录不存在: $pluginPath")
                        statusCallback(DeploymentStatus.Error("插件目录不存在: $pluginPath"))
                        return@withContext false
                    }

                    // 查找README文件
                    val readmeFile = findReadmeFile(pluginDir)
                    val readmeContent = readmeFile?.readText() ?: ""

                    // 分析项目结构
                    statusCallback(DeploymentStatus.InProgress("分析项目结构..."))
                    val projectStructure = analyzeProjectStructure(pluginDir, readmeContent)
                    Log.d(TAG, "项目类型: ${projectStructure.type}")

                    // 生成部署命令，传入README内容用于特殊命令检测
                    val deployCommands = generateDeployCommands(projectStructure, readmeContent)
                    if (deployCommands.isEmpty()) {
                        Log.e(TAG, "无法生成部署命令: $pluginId")
                        statusCallback(DeploymentStatus.Error("无法确定如何部署此插件，请查看README手动部署"))
                        return@withContext false
                    }

                    Log.d(TAG, "生成部署命令: $deployCommands")

                    // 生成MCP配置
                    val mcpConfig = generateMcpConfig(pluginId, projectStructure)
                    Log.d(TAG, "生成MCP配置: $mcpConfig")

                    // 保存MCP配置
                    statusCallback(DeploymentStatus.InProgress("保存MCP配置..."))
                    val mcpLocalServer = MCPLocalServer.getInstance(context)
                    val configSaveResult = mcpLocalServer.savePluginConfig(pluginId, mcpConfig)

                    if (!configSaveResult) {
                        Log.e(TAG, "保存MCP配置失败: $pluginId")
                        statusCallback(DeploymentStatus.Error("保存MCP配置失败"))
                        return@withContext false
                    }

                    // 获取终端会话
                    val session = getTerminalSession()

                    // 执行命令
                    val successFlag = AtomicBoolean(true)

                    // 定义Termux数据目录路径
                    val termuxDataDir =
                            "/data/data/com.termux/files/home/mcp_plugins/${pluginId.split("/").last()}"

                    // 首先创建Termux目标目录
                    statusCallback(DeploymentStatus.InProgress("创建Termux目录: $termuxDataDir"))

                    TerminalSessionManager.executeSessionCommand(
                            context = context,
                            session = session,
                            command = "mkdir -p $termuxDataDir",
                            onOutput = { output -> Log.d(TAG, "创建目录输出: $output") },
                            onInteractivePrompt = { prompt, executionId ->
                                Log.w(TAG, "创建目录出现交互提示: $prompt ($executionId)")
                            },
                            onComplete = { exitCode, success ->
                                if (!success) {
                                    Log.e(TAG, "创建Termux目录失败，退出码: $exitCode")
                                    successFlag.set(false)
                                }
                            }
                    )

                    if (!successFlag.get()) {
                        statusCallback(DeploymentStatus.Error("创建Termux目录失败"))
                        return@withContext false
                    }

                    // 复制插件文件到Termux目录
                    statusCallback(DeploymentStatus.InProgress("复制插件文件到Termux目录..."))

                    TerminalSessionManager.executeSessionCommand(
                            context = context,
                            session = session,
                            command = "cp -r $pluginPath/* $termuxDataDir/",
                            onOutput = { output -> Log.d(TAG, "复制文件输出: $output") },
                            onInteractivePrompt = { prompt, executionId ->
                                Log.w(TAG, "复制文件出现交互提示: $prompt ($executionId)")
                            },
                            onComplete = { exitCode, success ->
                                if (!success) {
                                    Log.e(TAG, "复制文件到Termux目录失败，退出码: $exitCode")
                                    successFlag.set(false)
                                }
                            }
                    )

                    if (!successFlag.get()) {
                        statusCallback(DeploymentStatus.Error("复制文件到Termux目录失败"))
                        return@withContext false
                    }

                    // 切换到Termux插件目录
                    statusCallback(DeploymentStatus.InProgress("切换到Termux插件目录"))

                    // 跟踪是否已经输出命令完成信息，避免重复输出
                    var hasOutputCompletionInfo = false

                    TerminalSessionManager.executeSessionCommand(
                            context = context,
                            session = session,
                            command = "cd $termuxDataDir",
                            onOutput = { output ->
                                // 减少不必要的日志输出
                                if (!output.contains("MCPInstaller") &&
                                                !output.contains("使用官方插件目录") &&
                                                !output.contains("读取到插件元数据") &&
                                                !output.contains("使用第一个目录作为插件路径")
                                ) {
                                    Log.d(TAG, "目录切换输出: $output")
                                }
                            },
                            onInteractivePrompt = { prompt, executionId ->
                                Log.w(TAG, "目录切换出现交互提示: $prompt ($executionId)")
                            },
                            onComplete = { exitCode, success ->
                                if (!success) {
                                    Log.e(TAG, "切换目录失败，退出码: $exitCode")
                                    successFlag.set(false)
                                }
                            }
                    )

                    if (!successFlag.get()) {
                        statusCallback(DeploymentStatus.Error("切换到Termux插件目录失败"))
                        return@withContext false
                    }

                    // 安装依赖并配置环境
                    for ((index, command) in deployCommands.withIndex()) {
                        // 跳过启动命令，只执行依赖安装命令
                        if (command.contains("python -m") ||
                                        (command.contains("node ") &&
                                                !command.contains(
                                                        "node ./node_modules/typescript"
                                                )) ||
                                        command.contains("npm start") ||
                                        command.startsWith("#")
                        ) {
                            continue
                        }

                        val cleanCommand = command.trim()
                        if (cleanCommand.isBlank()) continue

                        // 判断是否是非关键命令（如npm配置命令）
                        val isNonCriticalCommand =
                                cleanCommand.contains("npm config set") ||
                                        cleanCommand.contains("|| true") ||
                                        cleanCommand.contains("npm install -g") ||
                                        cleanCommand.startsWith("npm config")

                        statusCallback(
                                DeploymentStatus.InProgress(
                                        "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand"
                                )
                        )
                        Log.d(TAG, "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand")

                        val commandSuccess = AtomicBoolean(true)

                        // 交互式提示响应通道
                        val interactiveResponseChannel =
                                Channel<String>(capacity = Channel.CONFLATED)

                        // 命令执行状态，用于避免重复处理输出
                        val outputLines = mutableSetOf<String>()

                        TerminalSessionManager.executeSessionCommand(
                                context = context,
                                session = session,
                                command = cleanCommand,
                                onOutput = { output ->
                                    // 优化输出日志，避免重复和过多日志
                                    val trimmedOutput = output.trim()
                                    if (trimmedOutput.isNotEmpty() &&
                                                    !outputLines.contains(trimmedOutput) &&
                                                    !trimmedOutput.contains("MCPInstaller") &&
                                                    !trimmedOutput.contains("使用官方插件目录") &&
                                                    !trimmedOutput.contains("读取到插件元数据") &&
                                                    !trimmedOutput.contains("使用第一个目录作为插件路径")
                                    ) {

                                        outputLines.add(trimmedOutput)
                                        Log.d(TAG, "命令输出: $trimmedOutput")
                                        statusCallback(
                                                DeploymentStatus.InProgress("输出: $trimmedOutput")
                                        )

                                        // 命令完成标记处理
                                        if (trimmedOutput.contains("COMMAND_COMPLETE")) {
                                            if (!hasOutputCompletionInfo) {
                                                Log.d(TAG, "检测到命令完成标记")
                                                hasOutputCompletionInfo = true
                                            }
                                        }
                                    }
                                },
                                onInteractivePrompt = { prompt, executionId ->
                                    // 处理交互式提示...
                                    Log.d(TAG, "收到交互式提示: $prompt (执行ID: $executionId)")
                                    statusCallback(DeploymentStatus.InProgress("需要交互: $prompt"))

                                    // 使用协程发送自动响应
                                    deployerScope.launch {
                                        // 为各种常见提示提供默认响应
                                        val response =
                                                when {
                                                    // NPM提示
                                                    prompt.contains("proceed") &&
                                                            prompt.contains("[y/n]") -> "y"
                                                    // 常规[y/n]提示，默认yes
                                                    prompt.contains("[y/n]") ||
                                                            prompt.contains("[Y/n]") -> "y"
                                                    // 常规[y/N]提示，默认no但我们选yes
                                                    prompt.contains("[y/N]") -> "y"
                                                    // 输入文件名/路径提示
                                                    prompt.contains("filename") ||
                                                            prompt.contains("file name") ||
                                                            prompt.contains("path") -> "config.json"
                                                    // 确认提示
                                                    prompt.contains("continue") ||
                                                            prompt.contains("proceed") -> "y"
                                                    // 默认响应
                                                    else -> ""
                                                }

                                        // 如果确定了响应，发送到通道
                                        if (response.isNotEmpty()) {
                                            interactiveResponseChannel.send(response)
                                            Log.d(TAG, "发送自动响应: $response")
                                        } else {
                                            Log.w(TAG, "无法确定适当的自动响应")
                                        }
                                    }

                                    // 返回交互式响应通道，供终端会话管理器使用
                                    interactiveResponseChannel
                                },
                                onComplete = { exitCode, success ->
                                    Log.d(TAG, "命令'$cleanCommand'执行完成: 退出码=$exitCode, 成功=$success")
                                    commandSuccess.set(success)
                                }
                        )

                        // 如果命令失败
                        if (!commandSuccess.get()) {
                            if (isNonCriticalCommand) {
                                // 对于非关键命令，即使失败也继续
                                Log.w(TAG, "非关键命令执行失败，但将继续部署: $cleanCommand")
                                statusCallback(
                                        DeploymentStatus.InProgress(
                                                "非关键命令执行失败，继续后续步骤: $cleanCommand"
                                        )
                                )
                            } else {
                                // 关键命令失败，中止部署
                                Log.e(TAG, "命令执行失败: $cleanCommand")
                                statusCallback(DeploymentStatus.Error("命令执行失败: $cleanCommand"))
                                return@withContext false
                            }
                        }
                    }

                    // 构建部署成功消息
                    val successMessage = StringBuilder()
                    successMessage.append("插件部署成功: $pluginId\n")
                    successMessage.append("项目类型: ${projectStructure.type}\n")
                    successMessage.append("Termux部署路径: $termuxDataDir\n")

                    // 如果有MCP配置，添加服务器名称
                    val serverName =
                            if (projectStructure.configExample != null) {
                                extractServerNameFromConfig(projectStructure.configExample)
                            } else {
                                pluginId.split("/").last().lowercase()
                            }

                    if (serverName != null) {
                        successMessage.append("服务器名称: $serverName\n")
                    }

                    statusCallback(DeploymentStatus.Success(successMessage.toString()))
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "部署插件时出错", e)
                    statusCallback(DeploymentStatus.Error("部署出错: ${e.message}"))
                    return@withContext false
                }
            }

    /** 从配置中提取服务器名称 */
    private fun extractServerNameFromConfig(configExample: String): String? {
        try {
            val jsonObject = JsonParser.parseString(configExample).asJsonObject
            if (jsonObject.has("mcpServers")) {
                val mcpServers = jsonObject.getAsJsonObject("mcpServers")
                if (mcpServers.size() > 0) {
                    // 返回第一个服务器名称
                    return mcpServers.keySet().first()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从配置中提取服务器名称失败", e)
        }
        return null
    }

    /**
     * 查找README文件
     *
     * @param pluginDir 插件目录
     * @return README文件，如果不存在则返回null
     */
    private fun findReadmeFile(pluginDir: File): File? {
        // 先查找根目录下的README文件
        var readmeFile = File(pluginDir, README_FILENAME)
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找小写readme文件
        readmeFile = File(pluginDir, ALT_README_FILENAME)
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找INSTALL.md文件
        readmeFile = File(pluginDir, INSTALL_MARKDOWN_FILENAME)
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找docs目录下的README文件
        val docsDir = File(pluginDir, "docs")
        if (docsDir.exists() && docsDir.isDirectory) {
            readmeFile = File(docsDir, README_FILENAME)
            if (readmeFile.exists() && readmeFile.isFile) {
                return readmeFile
            }

            // 查找小写readme文件
            readmeFile = File(docsDir, ALT_README_FILENAME)
            if (readmeFile.exists() && readmeFile.isFile) {
                return readmeFile
            }
        }

        // 查找任何可能的md文件
        val mdFiles = pluginDir.listFiles { file -> file.extension.equals("md", ignoreCase = true) }
        if (!mdFiles.isNullOrEmpty()) {
            return mdFiles.first()
        }

        return null
    }

    /**
     * 解析README文件中的安装命令
     *
     * @param content README文件内容
     * @return 安装命令列表
     */
    private fun parseInstallCommands(content: String): List<String> {
        val commands = mutableListOf<String>()

        // 查找安装相关小节
        val sections = content.split("\n## ", "\n# ")

        // 首先查找安装相关小节
        val installSections =
                sections.filter { section ->
                    INSTALL_KEYWORDS.any { keyword ->
                        section.startsWith(keyword, ignoreCase = true) ||
                                section.split("\n")
                                        .firstOrNull()
                                        ?.contains(keyword, ignoreCase = true) == true
                    }
                }

        if (installSections.isNotEmpty()) {
            // 从安装小节中提取命令
            for (section in installSections) {
                // 查找代码块
                val codeBlocks = CODE_BLOCK_REGEX.findAll(section)
                for (match in codeBlocks) {
                    val codeContent = match.groupValues[1].trim()
                    if (codeContent.isNotEmpty()) {
                        // 将代码块拆分为单独的命令
                        commands.addAll(codeContent.split("\n").filter { it.trim().isNotEmpty() })
                    }
                }

                // 查找内联代码（使用反引号的单行命令）
                val inlineCodes = INLINE_CODE_REGEX.findAll(section)
                for (match in inlineCodes) {
                    val code = match.groupValues[1].trim()
                    if (code.isNotEmpty() && looksLikeCommand(code)) {
                        commands.add(code)
                    }
                }
            }
        }

        // 如果没找到安装小节，则搜索整个文档
        if (commands.isEmpty()) {
            // 查找代码块
            val codeBlocks = CODE_BLOCK_REGEX.findAll(content)
            for (match in codeBlocks) {
                val codeContent = match.groupValues[1].trim()
                if (codeContent.isNotEmpty()) {
                    // 检查代码块是否包含命令
                    val lines = codeContent.split("\n").filter { it.trim().isNotEmpty() }
                    if (lines.any { looksLikeCommand(it) }) {
                        commands.addAll(lines)
                    }
                }
            }

            // 查找内联代码
            val inlineCodes = INLINE_CODE_REGEX.findAll(content)
            for (match in inlineCodes) {
                val code = match.groupValues[1].trim()
                if (code.isNotEmpty() && looksLikeCommand(code)) {
                    commands.add(code)
                }
            }
        }

        return commands.distinct()
    }

    /**
     * 判断文本是否看起来像命令
     *
     * @param text 文本
     * @return 是否像命令
     */
    private fun looksLikeCommand(text: String): Boolean {
        val trimmedText = text.trim()
        // 常见命令前缀
        val commandPrefixes =
                listOf(
                        "npm",
                        "yarn",
                        "pip",
                        "python",
                        "java",
                        "javac",
                        "gradle",
                        "mvn",
                        "git",
                        "docker",
                        "node",
                        "cargo",
                        "make",
                        "cd",
                        "mkdir",
                        "cp",
                        "mv",
                        "rm",
                        "chmod",
                        "chown",
                        "tar",
                        "unzip",
                        "wget",
                        "curl"
                )

        return commandPrefixes.any { prefix ->
            trimmedText.startsWith("$prefix ") || trimmedText == prefix
        } ||
                trimmedText.startsWith("./") ||
                trimmedText.contains(".sh") ||
                trimmedText.contains(".bat") ||
                trimmedText.contains(".cmd")
    }

    /**
     * 为交互式提示生成响应
     *
     * @param prompt 交互式提示
     * @return 生成的响应，null表示无法自动处理
     */
    private fun generateResponseForPrompt(prompt: String): String? {
        val promptLower = prompt.lowercase()

        return when {
            // 确认类提示
            promptLower.contains("confirm") ||
                    promptLower.contains("continue") ||
                    promptLower.contains("proceed") ||
                    promptLower.contains("是否") ||
                    promptLower.contains("确认") ||
                    promptLower.contains("确定") -> "y"

            // 覆盖文件提示
            promptLower.contains("overwrite") ||
                    promptLower.contains("replace") ||
                    promptLower.contains("覆盖") -> "y"

            // 权限提示
            promptLower.contains("permission") ||
                    promptLower.contains("authorize") ||
                    promptLower.contains("allow") ||
                    promptLower.contains("权限") -> "y"

            // 安装确认
            promptLower.contains("install") &&
                    (promptLower.contains("?") || promptLower.contains("yes/no")) -> "y"

            // 默认选项提示，通常选第一个
            prompt.contains("[Y/n]") -> "y"
            prompt.contains("[y/N]") -> "y"

            // 密码提示通常无法自动处理
            promptLower.contains("password") || promptLower.contains("密码") -> null

            // 无法识别的提示
            else -> null
        }
    }
}
