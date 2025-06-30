package com.ai.assistance.operit.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.ai.assistance.operit.data.model.DragonBonesConfig
import com.ai.assistance.operit.data.model.DragonBonesModel
import com.ai.assistance.operit.data.model.ModelType
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** DragonBones数据仓库 负责管理DragonBones模型数据和配置的读取与保存 */
class DragonBonesRepository(private val context: Context) {

    companion object {
        private const val TAG = "DragonBonesRepository"
        private const val PREFS_NAME = "dragonbones_preferences"
        private const val KEY_CONFIG = "dragonbones_config"
        private const val KEY_MODELS = "dragonbones_models"
        private const val KEY_LAST_MODEL_ID = "dragonbones_last_model_id"

        // 内置模型位于assets/dragonbones/models目录
        private const val ASSETS_MODEL_DIR = "dragonbones/models"
        // 用户模型位于Android/data/包名/files/dragonbones目录
        private const val USER_MODEL_DIR = "dragonbones"

        // 单例实例
        @Volatile private var INSTANCE: DragonBonesRepository? = null

        fun getInstance(context: Context): DragonBonesRepository {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: DragonBonesRepository(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _models = MutableStateFlow<List<DragonBonesModel>>(emptyList())
    val models: StateFlow<List<DragonBonesModel>> = _models

    private val _currentConfig = MutableStateFlow<DragonBonesConfig?>(null)
    val currentConfig: StateFlow<DragonBonesConfig?> = _currentConfig

    private val userModelDir: String by lazy {
        context.getExternalFilesDir(null)?.absolutePath + File.separator + USER_MODEL_DIR
    }

    init {
        createUserModelDirIfNeeded()
        synchronizeAssets()
        loadModelsAndConfig()
    }

    private fun createUserModelDirIfNeeded() {
        val modelDir = File(userModelDir)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
    }

    private fun synchronizeAssets() {
        try {
            val assetManager = context.assets
            val modelFolders = assetManager.list(ASSETS_MODEL_DIR) ?: return

            for (folder in modelFolders) {
                val destDir = File(userModelDir, folder)
                if (!destDir.exists()) {
                    Log.d(TAG, "Populating model '$folder' from assets.")
                    copyAssetDirectory("$ASSETS_MODEL_DIR/$folder", destDir.absolutePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing assets: ${e.message}", e)
        }
    }

    private fun loadModelsAndConfig() {
        // First, load models from preferences to preserve metadata like modelType.
        val modelsFromPrefs = loadModelsFromPrefs()

        // Then, scan the filesystem for the current state of models.
        val modelsFromDisk = scanModelsInDirectory(File(userModelDir))

        // Merge the two lists. Use the metadata from prefs if the model exists,
        // otherwise use the newly scanned model data.
        val finalModels =
                modelsFromDisk.map { diskModel ->
                        modelsFromPrefs.find { it.id == diskModel.id } ?: diskModel
                }

        _models.value = finalModels
        saveModelsToPrefs(finalModels)

        loadConfigFromPrefs()

        val currentModelExists = _models.value.any { it.id == _currentConfig.value?.modelId }
        if (!currentModelExists && _models.value.isNotEmpty()) {
            Log.w(TAG, "Current model not found, resetting to the first available model.")
            val firstModel = _models.value.first()
            switchModel(firstModel.id)
        }
    }

    private fun scanModelsInDirectory(directory: File): List<DragonBonesModel> {
        val allModels = mutableListOf<DragonBonesModel>()
        if (directory.exists() && directory.isDirectory) {
            val modelFolders = directory.listFiles { file -> file.isDirectory } ?: emptyArray()

            for (folder in modelFolders) {
                val skeletonFile =
                        folder
                                .listFiles { file ->
                                    file.isFile &&
                                            file.extension == "json" &&
                                            !file.name.endsWith("_tex.json")
                                }
                                ?.firstOrNull()
                                ?: continue

                val modelName = skeletonFile.nameWithoutExtension
                val textureJsonFile = File(folder, "${modelName}_tex.json")
                val textureImageFile = File(folder, "${modelName}_tex.png")

                if (textureJsonFile.exists() && textureImageFile.exists()) {
                    val model =
                            DragonBonesModel(
                                    id = "user_${folder.name}",
                                    name = folder.name,
                                    folderPath = folder.absolutePath,
                                    skeletonFile = skeletonFile.name,
                                    textureJsonFile = textureJsonFile.name,
                                    textureImageFile = textureImageFile.name,
                                    isBuiltIn = false // All models in user dir are treated as such
                            )
                    allModels.add(model)
                }
            }
        }
        return allModels
    }

    private fun loadModelsFromPrefs(): List<DragonBonesModel> {
        val json = prefs.getString(KEY_MODELS, null)
        if (json.isNullOrEmpty()) return emptyList()

        return try {
            gson.fromJson(json, Array<DragonBonesModel>::class.java).toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing models from JSON", e)
            emptyList()
        }
    }

    private fun loadConfigFromPrefs() {
        val lastModelId = prefs.getString(KEY_LAST_MODEL_ID, null)
        val configJson = prefs.getString(KEY_CONFIG, null)

        if (configJson != null) {
            try {
                val config = gson.fromJson(configJson, DragonBonesConfig::class.java)
                _currentConfig.value = config
            } catch (e: Exception) {
                Log.e(TAG, "Error loading config from prefs: ${e.message}")
                if (_models.value.isNotEmpty()) {
                    val defaultModel =
                            _models.value.find { it.id == lastModelId } ?: _models.value.first()
                    val defaultConfig = DragonBonesConfig(modelId = defaultModel.id)
                    _currentConfig.value = defaultConfig
                    saveConfigToPrefs(defaultConfig)
                }
            }
        } else {
            if (_models.value.isNotEmpty()) {
                val defaultModel =
                        _models.value.find { it.id == lastModelId } ?: _models.value.first()
                val defaultConfig = DragonBonesConfig(modelId = defaultModel.id)
                _currentConfig.value = defaultConfig
                saveConfigToPrefs(defaultConfig)
            }
        }
    }

    private fun saveModelsToPrefs(models: List<DragonBonesModel>) {
        val modelsJson = gson.toJson(models)
        prefs.edit { putString(KEY_MODELS, modelsJson) }
    }

    private fun saveConfigToPrefs(config: DragonBonesConfig) {
        val configJson = gson.toJson(config)
        prefs.edit { putString(KEY_CONFIG, configJson) }
    }

    private fun saveLastModelId(modelId: String) {
        prefs.edit { putString(KEY_LAST_MODEL_ID, modelId) }
    }

    fun updateConfig(config: DragonBonesConfig) {
        _currentConfig.value = config
        saveConfigToPrefs(config)
        saveLastModelId(config.modelId)
    }

    fun getCurrentModel(): DragonBonesModel? {
        val currentConfig = _currentConfig.value ?: return null
        return _models.value.find { it.id == currentConfig.modelId }
    }

    fun switchModel(modelId: String) {
        val currentConfig = _currentConfig.value ?: return
        val newConfig = currentConfig.copy(modelId = modelId)
        _currentConfig.value = newConfig
        saveConfigToPrefs(newConfig)
        saveLastModelId(modelId)
    }

    private fun copyAssetDirectory(srcPath: String, dstPath: String) {
        try {
            val assetManager = context.assets
            val files = assetManager.list(srcPath) ?: return

            val dstDir = File(dstPath)
            if (!dstDir.exists()) {
                dstDir.mkdirs()
            }

            for (fileName in files) {
                val srcFilePath = "$srcPath/$fileName"
                val dstFilePath = "$dstPath/$fileName"
                try {
                    assetManager.open(srcFilePath).use { it.close() } // Throws on directory
                    assetManager.open(srcFilePath).use { input ->
                        FileOutputStream(dstFilePath).use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    copyAssetDirectory(srcFilePath, dstFilePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset directory: ${e.message}", e)
            throw e
        }
    }

    suspend fun scanUserModels(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    loadModelsAndConfig()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning user models: ${e.message}")
                    false
                }
            }

    suspend fun deleteUserModel(modelId: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val model = _models.value.find { it.id == modelId } ?: return@withContext false
                    if (model.isBuiltIn) return@withContext false

                    val modelDir = File(model.folderPath)
                    val deleted = modelDir.deleteRecursively()

                    if (deleted) {
                        val updatedModels = _models.value.filter { it.id != modelId }
                        _models.value = updatedModels
                        saveModelsToPrefs(updatedModels)

                        if (_currentConfig.value?.modelId == modelId && updatedModels.isNotEmpty()
                        ) {
                            switchModel(updatedModels.first().id)
                        }
                    }
                    deleted
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting user model: ${e.message}")
                    false
                }
            }

    suspend fun importModelFromZip(uri: Uri): Boolean =
            withContext(Dispatchers.IO) {
                var successfulImport = false
                val tempDir =
                        File(
                                "${context.cacheDir.absolutePath}/db_temp_extract_${System.nanoTime()}"
                        )
                try {
                    if (tempDir.exists()) tempDir.deleteRecursively()
                    tempDir.mkdirs()

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zipInputStream ->
                            var zipEntry = zipInputStream.nextEntry
                            while (zipEntry != null) {
                                val outputFile = File(tempDir, zipEntry.name)
                                if (!outputFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                                    throw SecurityException("Zip Path Traversal Vulnerability")
                                }
                                if (zipEntry.isDirectory) {
                                    outputFile.mkdirs()
                                } else {
                                    outputFile.parentFile?.mkdirs()
                                    outputFile.outputStream().use {
                                        it.write(zipInputStream.readBytes())
                                    }
                                }
                                zipInputStream.closeEntry()
                                zipEntry = zipInputStream.nextEntry
                            }
                        }
                    }
                            ?: return@withContext false

                    val foundModels = scanModelsInDirectory(tempDir)

                    if (foundModels.isEmpty()) {
                        Log.e(TAG, "ZIP file does not contain any valid DragonBones models.")
                        return@withContext false
                    }

                    Log.d(TAG, "Found ${foundModels.size} models in ZIP file.")

                    for (model in foundModels) {
                        var targetDir = File(userModelDir, model.name)
                        if (targetDir.exists()) {
                            val uniqueName = "${model.name}_${System.currentTimeMillis()}"
                            targetDir = File(userModelDir, uniqueName)
                            Log.w(
                                    TAG,
                                    "Model directory '${model.name}' already exists, renaming to '$uniqueName'"
                            )
                        }

                        try {
                            File(model.folderPath).copyRecursively(targetDir, overwrite = true)
                            Log.d(
                                    TAG,
                                    "Model '${model.name}' imported successfully to ${targetDir.absolutePath}"
                            )
                            successfulImport = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy model '${model.name}'", e)
                        }
                    }

                    if (successfulImport) {
                        loadModelsAndConfig()
                        Log.d(TAG, "Finished importing models, reloading.")
                    }

                    return@withContext successfulImport
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import model from ZIP: ${e.message}", e)
                    return@withContext false
                } finally {
                    if (tempDir.exists()) {
                        tempDir.deleteRecursively()
                    }
                }
            }

    fun updateModelType(modelId: String, modelType: ModelType) {
        val currentModels = _models.value.toMutableList()
        val modelIndex = currentModels.indexOfFirst { it.id == modelId }
        if (modelIndex != -1) {
            val updatedModel = currentModels[modelIndex].copy(modelType = modelType)
            currentModels[modelIndex] = updatedModel
            _models.value = currentModels
            saveModelsToPrefs(currentModels)
        }
    }
}
