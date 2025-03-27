package com.ai.assistance.operit.tools.packTool

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.util.Log
import com.ai.assistance.operit.permissions.ToolCategory
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.PackageTool
import com.ai.assistance.operit.tools.PackageToolExecutor
import com.ai.assistance.operit.tools.PackageToolParameter
import com.ai.assistance.operit.tools.ToolPackage
import com.ai.assistance.operit.tools.javascript.JsEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.hjson.JsonValue
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * Manages the loading, registration, and handling of tool packages
 * 
 * Package Lifecycle:
 * 1. Available Packages: All packages in assets (both JS and HJSON format)
 * 2. Imported Packages: Packages that user has imported (but not necessarily using)
 * 3. Used Packages: Packages that are loaded and registered with AI in current session
 */
class PackageManager private constructor(
    private val context: Context,
    private val aiToolHandler: AIToolHandler
) {
    companion object {
        private const val TAG = "PackageManager"
        private const val PACKAGES_DIR = "packages" // Directory for packages
        private const val ASSETS_PACKAGES_DIR = "packages" // Directory in assets for packages
        private const val PACKAGE_PREFS = "com.ai.assistance.operit.tools.PackageManager"
        private const val IMPORTED_PACKAGES_KEY = "imported_packages"
        private const val ACTIVE_PACKAGES_KEY = "active_packages"
        
        @Volatile
        private var INSTANCE: PackageManager? = null
        
        fun getInstance(context: Context, aiToolHandler: AIToolHandler): PackageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PackageManager(context.applicationContext, aiToolHandler).also { INSTANCE = it }
            }
        }
    }
    
    // Map of package name to package description (all available packages in market)
    private val availablePackages = mutableMapOf<String, ToolPackage>()
    
    // JavaScript engine for executing JS package code
    private val jsEngine by lazy { JsEngine(context) }
    
    // Get the external packages directory
    private val externalPackagesDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            Log.d(TAG, "External packages directory: ${dir.absolutePath}")
            return dir
        }
    
    init {
        // Create packages directory if it doesn't exist
        externalPackagesDir // This will create the directory if it doesn't exist
        
        // Load available packages info (metadata only) from assets and external storage
        loadAvailablePackages()
    }
    
    /**
     * Loads all available packages metadata (from assets and external storage, both JS and HJSON format)
     */
    private fun loadAvailablePackages() {
        // Load packages from assets (JS only, skip TS files)
        val assetManager = context.assets
        val packageFiles = assetManager.list(ASSETS_PACKAGES_DIR) ?: emptyArray()
        
        for (fileName in packageFiles) {
            if (fileName.endsWith(".hjson")) {
                val packageMetadata = loadPackageFromHjsonAsset("$ASSETS_PACKAGES_DIR/$fileName")
                if (packageMetadata != null) {
                    availablePackages[packageMetadata.name] = packageMetadata
                    Log.d(TAG, "Loaded HJSON package from assets: ${packageMetadata.name} with description: ${packageMetadata.description}, tools: ${packageMetadata.tools.size}")
                }
            } else if (fileName.endsWith(".js")) {
                // Only load JavaScript files, skip TypeScript files which require compilation
                val packageMetadata = loadPackageFromJsAsset("$ASSETS_PACKAGES_DIR/$fileName")
                if (packageMetadata != null) {
                    availablePackages[packageMetadata.name] = packageMetadata
                    Log.d(TAG, "Loaded JavaScript package from assets: ${packageMetadata.name} with description: ${packageMetadata.description}, tools: ${packageMetadata.tools.size}")
                }
            }
        }
        
        // Also load packages from external storage (imported from external sources)
        val externalFiles = externalPackagesDir.listFiles() ?: emptyArray()
        
        for (file in externalFiles) {
            if (file.name.endsWith(".hjson")) {
                try {
                    val hjsonContent = file.readText()
                    val jsonString = JsonValue.readHjson(hjsonContent).toString()
                    
                    val jsonConfig = Json { ignoreUnknownKeys = true }
                    val packageMetadata = jsonConfig.decodeFromString<ToolPackage>(jsonString)
                    
                    availablePackages[packageMetadata.name] = packageMetadata
                    Log.d(TAG, "Loaded imported HJSON package from external storage: ${packageMetadata.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading imported HJSON package from: ${file.path}", e)
                }
            } else if (file.name.endsWith(".js")) {
                // Only load JavaScript files from external storage too
                try {
                    val packageMetadata = loadPackageFromJsFile(file)
                    if (packageMetadata != null) {
                        availablePackages[packageMetadata.name] = packageMetadata
                        Log.d(TAG, "Loaded imported JavaScript package from external storage: ${packageMetadata.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading imported JavaScript package from: ${file.path}", e)
                }
            }
        }
    }
    
    /**
     * Loads a complete ToolPackage from a JavaScript file
     */
    private fun loadPackageFromJsFile(file: File): ToolPackage? {
        try {
            val jsContent = file.readText()
            return parseJsPackage(jsContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading package from JS file: ${file.path}", e)
            return null
        }
    }

    /**
     * Loads a complete ToolPackage from a JavaScript file in assets
     */
    private fun loadPackageFromJsAsset(assetPath: String): ToolPackage? {
        try {
            val assetManager = context.assets
            val jsContent = assetManager.open(assetPath).bufferedReader().use { it.readText() }
            return parseJsPackage(jsContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading package from JS asset: $assetPath", e)
            return null
        }
    }

    /**
     * Parses a JavaScript package file into a ToolPackage object
     * Uses the metadata in the file header and extracts function definitions using JsEngine
     */
    private fun parseJsPackage(jsContent: String): ToolPackage? {
        try {
            // Extract metadata from comments at the top of the file
            val metadataString = extractMetadataFromJs(jsContent)
            
            // 先将元数据解析为 JSONObject 以便修改 tools 数组中的每个元素
            val metadataJson = org.json.JSONObject(JsonValue.readHjson(metadataString).toString())
            
            // 检查并修复 tools 数组中的元素，确保每个工具都有 script 字段
            if (metadataJson.has("tools") && metadataJson.get("tools") is org.json.JSONArray) {
                val toolsArray = metadataJson.getJSONArray("tools")
                for (i in 0 until toolsArray.length()) {
                    val tool = toolsArray.getJSONObject(i)
                    if (!tool.has("script")) {
                        // 添加一个临时的空 script 字段
                        tool.put("script", "")
                    }
                }
            }
            
            // 使用修改后的 JSON 字符串进行反序列化
            val jsonString = metadataJson.toString()
            
            val jsonConfig = Json { ignoreUnknownKeys = true }
            val packageMetadata = jsonConfig.decodeFromString<ToolPackage>(jsonString)
            
            // 更新所有工具，使用相同的完整脚本内容，但记录每个工具的函数名
            val tools = packageMetadata.tools.map { tool ->
                // 检查函数是否存在于脚本中
                validateToolFunctionExists(jsContent, tool.name)
                
                // 使用整个脚本，并记录函数名，而不是提取单个函数
                tool.copy(script = jsContent)
            }
            
            return packageMetadata.copy(tools = tools)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JS package: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 验证JavaScript文件中是否存在指定的函数
     * 这确保了我们可以在运行时调用该函数
     */
    private fun validateToolFunctionExists(jsContent: String, toolName: String): Boolean {
        // 各种函数声明模式
        val patterns = listOf(
            """async\s+function\s+$toolName\s*\(""",
            """function\s+$toolName\s*\(""",
            """exports\.$toolName\s*=\s*(?:async\s+)?function""",
            """(?:const|let|var)\s+$toolName\s*=\s*(?:async\s+)?\(""",
            """exports\.$toolName\s*=\s*(?:async\s+)?\(?"""
        )
        
        for (pattern in patterns) {
            if (pattern.toRegex().find(jsContent) != null) {
                return true
            }
        }
        
        Log.w(TAG, "Could not find function '$toolName' in JavaScript file")
        return false
    }
    
    /**
     * Extracts the metadata from JS comments at the top of the file
     */
    private fun extractMetadataFromJs(jsContent: String): String {
        val metadataPattern = """/\*\s*METADATA\s*([\s\S]*?)\*/""".toRegex()
        val match = metadataPattern.find(jsContent)
        
        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // If no metadata block is found, return empty metadata
            "{}"
        }
    }
    
    /**
     * Returns the path to the external packages directory
     * This can be used to show the user where the packages are stored for manual editing
     */
    fun getExternalPackagesPath(): String {
        val path = externalPackagesDir.absolutePath
        // 为了更易读，改成Android/data/包名/files/packages的形式
        return "Android/data/${context.packageName}/files/packages"
    }
    
    /**
     * Imports a package from external storage path
     * @param filePath The file path to the JS or HJSON package file in external storage
     * @return Success message with package details or error message
     */
    fun importPackageFromExternalStorage(filePath: String): String {
        try {
            // Check if the file exists and is readable
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return "Cannot access file at path: $filePath"
            }
            
            // Check if it's a supported file type
            if (!filePath.endsWith(".hjson") && !filePath.endsWith(".js") && !filePath.endsWith(".ts")) {
                return "Only HJSON, JavaScript (.js) and TypeScript (.ts) package files are supported"
            }
            
            // Parse the file to get package metadata
            val packageMetadata = if (filePath.endsWith(".hjson")) {
            val hjsonContent = file.readText()
            val jsonString = JsonValue.readHjson(hjsonContent).toString()
            
            val jsonConfig = Json { ignoreUnknownKeys = true }
                jsonConfig.decodeFromString<ToolPackage>(jsonString)
            } else {
                // Treat both .js and .ts files as JavaScript packages
                loadPackageFromJsFile(file) ?: return "Failed to parse ${if (filePath.endsWith(".ts")) "TypeScript" else "JavaScript"} package file"
            }
            
            // Check if package with same name already exists
            if (availablePackages.containsKey(packageMetadata.name)) {
                return "A package with name '${packageMetadata.name}' already exists in available packages"
            }
            
            // Copy the file to app's external storage
            val destinationFile = File(externalPackagesDir, file.name)
            file.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Add to available packages
            availablePackages[packageMetadata.name] = packageMetadata
            
            Log.d(TAG, "Successfully imported external package to: ${destinationFile.absolutePath}")
            return "Successfully imported package: ${packageMetadata.name}\nStored at: ${destinationFile.absolutePath}"
        } catch (e: Exception) {
            Log.e(TAG, "Error importing package from external storage", e)
            return "Error importing package: ${e.message}"
        }
    }
    
    /**
     * Loads a complete ToolPackage from an HJSON file in assets
     */
    private fun loadPackageFromHjsonAsset(assetPath: String): ToolPackage? {
        try {
            val assetManager = context.assets
            val hjsonContent = assetManager.open(assetPath).bufferedReader().use { it.readText() }
            val jsonString = JsonValue.readHjson(hjsonContent).toString()
            
            // 创建配置了ignoreUnknownKeys的JSON解析器
            val jsonConfig = Json { ignoreUnknownKeys = true }
            return jsonConfig.decodeFromString<ToolPackage>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading package from HJSON asset: $assetPath - ${e.message}", e)
            return null
        }
    }
    
    /**
     * Import a package by name, adding it to the user's imported packages list
     * This does NOT register the package with the AIToolHandler - it just adds it to imported list
     * @param packageName The name of the package to import
     * @return Success/failure message
     */
    fun importPackage(packageName: String): String {
        // Check if the package is available
        if (!availablePackages.containsKey(packageName)) {
            return "Package not found in available packages: $packageName"
        }
        
        // Check if already imported
        val importedPackages = getImportedPackages()
        if (importedPackages.contains(packageName)) {
            return "Package '$packageName' is already imported"
        }
        
        // Add to imported packages
        val updatedPackages = importedPackages.toMutableList()
        updatedPackages.add(packageName)
        
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(updatedPackages)
        prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()
        
        Log.d(TAG, "Successfully imported package: $packageName")
        return "Successfully imported package: $packageName"
    }
    
    /**
     * Activates and loads a package for use in the current AI session
     * This loads the full package data and registers its tools with AIToolHandler
     * @param packageName The name of the imported package to use
     * @return Package description and tools for AI prompt enhancement, or error message
     */
    fun usePackage(packageName: String): String {
        // Check if package is imported
        val importedPackages = getImportedPackages()
        if (!importedPackages.contains(packageName)) {
            return "Package not imported. Import it first with 'import package $packageName'"
        }
        
        // Load the full package data
        val toolPackage = getPackageTools(packageName)
            ?: return "Failed to load package data for: $packageName"
        
        // Register the package tools with AIToolHandler
        registerPackageTools(toolPackage)
            
        Log.d(TAG, "Successfully loaded and activated package: $packageName")
            
            // Generate and return the system prompt enhancement
            return generatePackageSystemPrompt(toolPackage)
    }
    
    /**
     * Registers all tools in a package with the AIToolHandler
     */
    private fun registerPackageTools(toolPackage: ToolPackage) {
        val packageToolExecutor = PackageToolExecutor(toolPackage, context, this)
        
        // Register each tool with the format packageName:toolName
        toolPackage.tools.forEach { packageTool ->
            val toolName = "${toolPackage.name}:${packageTool.name}"
            aiToolHandler.registerTool(toolName, toolPackage.category) { tool ->
                packageToolExecutor.invoke(tool)
            }
        }
    }
    
    
    /**
     * Generates a system prompt enhancement for the imported package
     */
    private fun generatePackageSystemPrompt(toolPackage: ToolPackage): String {
        val sb = StringBuilder()
        
        sb.appendLine("Using package: ${toolPackage.name}")
        sb.appendLine("Description: ${toolPackage.description}")
        sb.appendLine()
        sb.appendLine("Available tools in this package:")
        
        toolPackage.tools.forEach { tool ->
            sb.appendLine("- ${toolPackage.name}:${tool.name}: ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(required)" else "(optional)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description}")
                }
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }

    
    /**
     * Gets a list of all available packages for discovery (the "market")
     * @return A map of package name to description
     */
    fun getAvailablePackages(): Map<String, ToolPackage> {
        // Refresh the list to ensure it's up to date
        loadAvailablePackages()
        return availablePackages
    }
    
    /**
     * Get a list of all imported packages
     * @return A list of imported package names
     */
    fun getImportedPackages(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(IMPORTED_PACKAGES_KEY, "[]")
        return try {
            // 创建配置了ignoreUnknownKeys的JSON解析器
            val jsonConfig = Json { ignoreUnknownKeys = true }
            jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding imported packages", e)
            emptyList()
        }
    }
    
    
    /**
     * Get the tools for a loaded package
     * @param packageName The name of the loaded package
     * @return The ToolPackage object or null if the package is not loaded
     */
    fun getPackageTools(packageName: String): ToolPackage? {
        return availablePackages[packageName]
    }
    
    /**
     * Checks if a package is imported
     */
    fun isPackageImported(packageName: String): Boolean {
        return getImportedPackages().contains(packageName)
    }

    
    /**
     * Remove an imported package
     * @param packageName The name of the package to remove from imported list
     * @return Success/failure message
     */
    fun removePackage(packageName: String): String {
        // Then remove from imported packages
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val currentPackages = getImportedPackages().toMutableList()
        
        if (currentPackages.remove(packageName)) {
            val updatedJson = Json.encodeToString(currentPackages)
            prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()
            Log.d(TAG, "Removed package from imported list: $packageName")
            return "Successfully removed package: $packageName"
        } else {
            Log.d(TAG, "Package not found in imported list: $packageName")
            return "Package not found in imported list: $packageName"
        }
    }

    /**
     * Get the script content for a package by name
     * @param packageName The name of the package
     * @return The full JavaScript content of the package or null if not found
     */
    fun getPackageScript(packageName: String): String? {
        val toolPackage = availablePackages[packageName] ?: return null
        
        // All tools in a package share the same script, so we can get it from any tool
        return if (toolPackage.tools.isNotEmpty()) {
            toolPackage.tools[0].script
        } else {
            null
        }
    }

    /**
     * Clean up resources when the manager is no longer needed
     */
    fun destroy() {
        jsEngine.destroy()
    }
    
}