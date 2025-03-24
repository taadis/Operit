package com.ai.assistance.operit.tools.packTool

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.ai.assistance.operit.data.ToolCategory
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.PackageTool
import com.ai.assistance.operit.tools.PackageToolExecutor
import com.ai.assistance.operit.tools.PackageToolParameter
import com.ai.assistance.operit.tools.ToolPackage
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.hjson.JsonValue
import java.io.File
import java.io.IOException

/**
 * Manages the loading, registration, and handling of tool packages
 * 
 * Package Lifecycle:
 * 1. Available Packages: All packages in assets (only HJSON format)
 * 2. Imported Packages: Packages that user has imported (but not necessarily using)
 * 3. Used Packages: Packages that are loaded and registered with AI in current session
 */
class PackageManager private constructor(
    private val context: Context,
    private val aiToolHandler: AIToolHandler
) {
    companion object {
        private const val TAG = "PackageManager"
        private const val PACKAGES_DIR = "packages" // Directory in app storage for packages
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
    
    init {
        // Create packages directory if it doesn't exist
        val packagesDir = File(context.filesDir, PACKAGES_DIR)
        if (!packagesDir.exists()) {
            packagesDir.mkdirs()
        }
        
        // Load available packages info (metadata only) from assets
        loadAvailablePackages()
    }
    
    /**
     * Loads all available packages metadata (from assets, only HJSON format)
     */
    private fun loadAvailablePackages() {
        // Load packages from assets (HJSON only)
        val assetManager = context.assets
        val packageFiles = assetManager.list(ASSETS_PACKAGES_DIR) ?: emptyArray()
        
        for (fileName in packageFiles) {
            if (fileName.endsWith(".hjson")) {
                val packageMetadata = loadPackageFromAsset("$ASSETS_PACKAGES_DIR/$fileName")
                if (packageMetadata != null) {
                    availablePackages[packageMetadata.name] = packageMetadata
                    Log.d(TAG, "Loaded package: ${packageMetadata.name} with description: ${packageMetadata.description}, tools: ${packageMetadata.tools.size}")
                }
            }
        }
    }
    
    
    /**
     * Loads a complete ToolPackage from an HJSON file in assets
     */
    private fun loadPackageFromAsset(assetPath: String): ToolPackage? {
        try {
            val assetManager = context.assets
            val hjsonContent = assetManager.open(assetPath).bufferedReader().use { it.readText() }
            val jsonString = JsonValue.readHjson(hjsonContent).toString()
            
            // 创建配置了ignoreUnknownKeys的JSON解析器
            val jsonConfig = Json { }
            return jsonConfig.decodeFromString<ToolPackage>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading package from asset: $assetPath", e)
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
     * Executes a tool from any loaded package
     */
    fun executeTool(tool: AITool): ToolResult {
        // Check if this is a package tool (with format packageName:toolName)
        val parts = tool.name.split(":")
        if (parts.size != 2) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Invalid tool name format. Expected 'packageName:toolName'"
            )
        }
        
        val packageName = parts[0]
        
        
        // Execute the tool using AIToolHandler
        return aiToolHandler.executeTool(tool)
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
    
}