package com.ai.assistance.operit.data.mcp.plugins

import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * MCP 项目结构分析器
 *
 * 负责分析插件项目结构，识别项目类型和特征
 */
class MCPProjectAnalyzer {

    companion object {
        private const val TAG = "MCPProjectAnalyzer"

        // 用于匹配命令块的正则表达式
        private val CODE_BLOCK_REGEX = "```(?:bash|shell|cmd|sh|json)?([\\s\\S]*?)```".toRegex()

        // JSON配置块正则表达式
        private val JSON_CONFIG_REGEX = "\\{[\\s\\S]*?\"mcpServers\"[\\s\\S]*?\\}".toRegex()

        // 模块名正则表达式
        private val MODULE_NAME_REGEX =
                "\"args\"\\s*:\\s*\\[[^\\]]*\"([^\"]+)\"[^\\]]*\\]".toRegex()
    }

    /**
     * 分析项目结构
     *
     * @param pluginDir 插件目录
     * @param readmeContent README内容
     * @return 项目结构信息
     */
    fun analyzeProjectStructure(pluginDir: File, readmeContent: String): ProjectStructure {
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

        // 解析tsconfig.json并提取配置
        var tsConfigOutDir: String? = null
        var tsConfigRootDir: String? = null
        var tsConfigContent: String? = null

        if (hasTsConfig) {
            val tsConfigInfo = parseTsConfig(pluginDir)
            tsConfigOutDir = tsConfigInfo.first
            tsConfigRootDir = tsConfigInfo.second
            tsConfigContent = tsConfigInfo.third
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
                packageJsonContent = packageJsonContent,
                tsConfigOutDir = tsConfigOutDir,
                tsConfigRootDir = tsConfigRootDir,
                tsConfigContent = tsConfigContent
        )
    }

    /**
     * 解析tsconfig.json文件，提取编译相关配置
     *
     * @param pluginDir 插件目录
     * @return Triple(outDir, rootDir, tsConfigContent) - 输出目录、根目录和tsconfig内容
     */
    private fun parseTsConfig(pluginDir: File): Triple<String?, String?, String?> {
        val tsConfigFile = File(pluginDir, "tsconfig.json")
        if (!tsConfigFile.exists()) {
            return Triple(null, null, null)
        }

        try {
            val tsConfigContent = tsConfigFile.readText()
            val tsConfig = JSONObject(tsConfigContent)

            var outDir: String? = "dist" // 默认值
            var rootDir: String? = "src" // 默认值

            // 获取编译选项
            val compilerOptions = tsConfig.optJSONObject("compilerOptions")
            if (compilerOptions != null) {
                // 获取输出目录配置
                if (compilerOptions.has("outDir")) {
                    outDir = compilerOptions.getString("outDir").trim('/', '\\', '.', ' ')
                    Log.d(TAG, "从tsconfig.json提取到outDir: $outDir")
                }

                // 获取根目录配置
                if (compilerOptions.has("rootDir")) {
                    rootDir = compilerOptions.getString("rootDir").trim('/', '\\', '.', ' ')
                    Log.d(TAG, "从tsconfig.json提取到rootDir: $rootDir")
                }
            }

            return Triple(outDir, rootDir, tsConfigContent)
        } catch (e: Exception) {
            Log.e(TAG, "解析tsconfig.json失败", e)
            return Triple("dist", "src", null) // 返回默认值
        }
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

    /** 查找README文件 */
    fun findReadmeFile(pluginDir: File): File? {
        // 先查找根目录下的README文件
        var readmeFile = File(pluginDir, "README.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找小写readme文件
        readmeFile = File(pluginDir, "readme.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找INSTALL.md文件
        readmeFile = File(pluginDir, "INSTALL.md")
        if (readmeFile.exists() && readmeFile.isFile) {
            return readmeFile
        }

        // 查找docs目录下的README文件
        val docsDir = File(pluginDir, "docs")
        if (docsDir.exists() && docsDir.isDirectory) {
            readmeFile = File(docsDir, "README.md")
            if (readmeFile.exists() && readmeFile.isFile) {
                return readmeFile
            }

            // 查找小写readme文件
            readmeFile = File(docsDir, "readme.md")
            if (readmeFile.exists() && readmeFile.isFile) {
                return readmeFile
            }
        }

        // 查找任何可能的md文件
        val mdFiles = pluginDir.listFiles { file -> file.extension.equals("md", ignoreCase = true) }
        if (mdFiles != null && mdFiles.isNotEmpty()) {
            return mdFiles.first()
        }

        return null
    }
}
