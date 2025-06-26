package com.ai.assistance.operit.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.ai.assistance.operit.data.model.Live2DConfig
import com.ai.assistance.operit.data.model.Live2DModel
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Live2D数据仓库 负责管理Live2D模型数据和配置的读取与保存 */
class Live2DRepository(private val context: Context) {

    companion object {
        private const val TAG = "Live2DRepository"
        private const val PREFS_NAME = "live2d_preferences"
        private const val KEY_CONFIG = "live2d_config"
        private const val KEY_MODELS = "live2d_models"
        private const val KEY_LAST_MODEL_ID = "live2d_last_model_id"

        // 内置模型位于assets/live2d目录
        private const val ASSETS_MODEL_DIR = "live2d"
        // 用户模型位于Android/data/包名/files/live2d目录
        private const val USER_MODEL_DIR = "live2d"

        // 单例实例
        @Volatile private var INSTANCE: Live2DRepository? = null

        fun getInstance(context: Context): Live2DRepository {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: Live2DRepository(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }
    }

    // SharedPreferences对象
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Gson实例用于JSON序列化
    private val gson = Gson()

    // 模型列表和当前配置的状态流
    private val _models = MutableStateFlow<List<Live2DModel>>(emptyList())
    val models: StateFlow<List<Live2DModel>> = _models

    private val _currentConfig = MutableStateFlow<Live2DConfig?>(null)
    val currentConfig: StateFlow<Live2DConfig?> = _currentConfig

    // 用户模型文件夹路径
    val userModelDir: String by lazy {
        context.getExternalFilesDir(null)?.absolutePath + File.separator + USER_MODEL_DIR
    }

    init {
        // 确保用户模型目录存在
        createUserModelDirIfNeeded()
        // 同步 Assets 资源到用户目录
        synchronizeAssets()
        // 加载所有模型和配置
        loadModelsAndConfig()
    }

    /** 创建用户模型目录（如果不存在） */
    private fun createUserModelDirIfNeeded() {
        val modelDir = File(userModelDir)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
    }

    /** 同步Assets中的资源和模型到用户数据目录。 这会作为预制步骤，将内置模型复制出来，如果它们尚不存在的话。 */
    private fun synchronizeAssets() {
        val modelDir = File(userModelDir)
        try {
            val assetManager = context.assets
            val assetModelsPath = "$ASSETS_MODEL_DIR/Live2DModels"
            val modelFolders = assetManager.list(assetModelsPath) ?: return

            // 1. 复制模型文件夹
            for (folder in modelFolders) {
                val destFile = File(modelDir, folder)
                if (!destFile.exists()) {
                    Log.d(TAG, "Prepulating model '$folder' from assets.")
                    copyAssetDirectory("$assetModelsPath/$folder", destFile.absolutePath)
                }
            }

            // 2. 复制Live2D库需要的基础资源文件
            val baseResourceFiles =
                    assetManager.list(ASSETS_MODEL_DIR)?.filter {
                        !it.equals("Live2DModels", ignoreCase = true)
                    }

            baseResourceFiles?.forEach { fileName ->
                val destFile = File(modelDir.parent, fileName) // 存放在 live2d 目录的父目录，即 files
                if (!destFile.exists()) {
                    val srcPath = "$ASSETS_MODEL_DIR/$fileName"
                    assetManager.open(srcPath).use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }
            }

            // 3. 设置C++层需要的基础资源路径
            val externalFilesDirPath = context.getExternalFilesDir(null)?.absolutePath
            System.setProperty("LIVE2D_BASE_RESOURCES_PATH", externalFilesDirPath)
            Log.d(TAG, "Base resources path set to: $externalFilesDirPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing assets: ${e.message}", e)
        }
    }

    /** 从用户目录加载所有模型和当前配置 */
    private fun loadModelsAndConfig() {
        // 1. 扫描用户目录加载所有模型
        val userDir = File(userModelDir)
        val allModels = mutableListOf<Live2DModel>()
        if (userDir.exists() && userDir.isDirectory) {
            val modelFolders = userDir.listFiles { file -> file.isDirectory } ?: emptyArray()

            for (folder in modelFolders) {
                val jsonFile =
                        folder
                                .listFiles { file ->
                                    file.isFile && file.name.endsWith(".model3.json")
                                }
                                ?.firstOrNull()
                                ?: continue

                // 获取表情列表
                val expressionDir = File(folder, "expressions")
                val expressions =
                        if (expressionDir.exists() && expressionDir.isDirectory) {
                            expressionDir
                                    .listFiles { file ->
                                        file.isFile && file.name.endsWith(".json")
                                    }
                                    ?.map { it.nameWithoutExtension }
                                    ?: emptyList()
                        } else {
                            emptyList()
                        }

                // 所有模型都视为用户模型
                val model =
                        Live2DModel(
                                id = "user_${folder.name}",
                                name = folder.name,
                                folderPath = folder.absolutePath,
                                jsonFileName = jsonFile.name,
                                isBuiltIn = false, // 所有模型都视为非内置
                                expressions = expressions,
                                thumbnailPath = null
                        )
                allModels.add(model)
            }
        }
        _models.value = allModels
        saveModelsToPrefs(allModels) // 保存以备后用

        // 2. 加载配置
        loadConfigFromPrefs()

        // 3. 验证当前配置的模型是否存在，如果不存在，则重置为第一个可用模型
        val currentModelExists = _models.value.any { it.id == _currentConfig.value?.modelId }
        if (!currentModelExists && _models.value.isNotEmpty()) {
            Log.w(TAG, "Current model not found, resetting to the first available model.")
            val firstModel = _models.value.first()
            switchModel(firstModel.id)
        }
    }

    /** 从SharedPreferences加载配置 */
    private fun loadConfigFromPrefs() {
        val lastModelId = prefs.getString(KEY_LAST_MODEL_ID, null)
        val configJson = prefs.getString(KEY_CONFIG, null)

        if (configJson != null) {
            try {
                val config = gson.fromJson(configJson, Live2DConfig::class.java)
                _currentConfig.value = config
            } catch (e: Exception) {
                Log.e(TAG, "Error loading config from prefs: ${e.message}")
                // 如果有模型但配置加载失败，使用第一个模型创建新配置
                if (_models.value.isNotEmpty()) {
                    val defaultModel =
                            if (lastModelId != null) {
                                _models.value.find { it.id == lastModelId } ?: _models.value.first()
                            } else {
                                _models.value.first()
                            }
                    val defaultConfig = Live2DConfig(modelId = defaultModel.id)
                    _currentConfig.value = defaultConfig
                    saveConfigToPrefs(defaultConfig)
                }
            }
        } else {
            // 如果有模型但没有配置，使用第一个模型创建新配置
            if (_models.value.isNotEmpty()) {
                val defaultModel =
                        if (lastModelId != null) {
                            _models.value.find { it.id == lastModelId } ?: _models.value.first()
                        } else {
                            _models.value.first()
                        }
                val defaultConfig = Live2DConfig(modelId = defaultModel.id)
                _currentConfig.value = defaultConfig
                saveConfigToPrefs(defaultConfig)
            }
        }
    }

    /** 保存模型列表到SharedPreferences */
    private fun saveModelsToPrefs(models: List<Live2DModel>) {
        val modelsJson = gson.toJson(models)
        prefs.edit { putString(KEY_MODELS, modelsJson) }
    }

    /** 保存配置到SharedPreferences */
    private fun saveConfigToPrefs(config: Live2DConfig) {
        val configJson = gson.toJson(config)
        prefs.edit { putString(KEY_CONFIG, configJson) }
    }

    /** 保存最后使用的模型ID到SharedPreferences */
    private fun saveLastModelId(modelId: String) {
        prefs.edit { putString(KEY_LAST_MODEL_ID, modelId) }
    }

    /** 更新当前配置 */
    fun updateConfig(config: Live2DConfig) {
        _currentConfig.value = config
        saveConfigToPrefs(config)
        saveLastModelId(config.modelId)
    }

    /** 获取当前模型 */
    fun getCurrentModel(): Live2DModel? {
        val currentConfig = _currentConfig.value ?: return null
        return _models.value.find { it.id == currentConfig.modelId }
    }

    /** 切换模型 */
    fun switchModel(modelId: String) {
        val currentConfig = _currentConfig.value ?: return
        val newConfig = currentConfig.copy(modelId = modelId)

        // JNI调用已移除，视图层将负责加载

        _currentConfig.value = newConfig
        saveConfigToPrefs(newConfig)
        saveLastModelId(modelId)
    }

    /** 复制assets文件夹到设备存储 */
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
                // 使用try-with-resources来判断是文件还是目录
                try {
                    assetManager.open(srcFilePath).close() // 如果是目录，会抛出异常
                    // 是文件 -> 复制
                    assetManager.open(srcFilePath).use { input ->
                        FileOutputStream(dstFilePath).use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    // 是目录 -> 递归
                    copyAssetDirectory(srcFilePath, dstFilePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset directory: ${e.message}", e)
            throw e
        }
    }

    /** 扫描用户目录下的模型 */
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

    /** 删除用户模型 */
    suspend fun deleteUserModel(modelId: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val model = _models.value.find { it.id == modelId } ?: return@withContext false
                    if (model.isBuiltIn) return@withContext false // 不能删除内置模型

                    // 删除文件
                    val modelDir = File(model.folderPath)
                    val deleted = modelDir.deleteRecursively()

                    if (deleted) {
                        // 更新模型列表
                        val updatedModels = _models.value.filter { it.id != modelId }
                        _models.value = updatedModels
                        saveModelsToPrefs(updatedModels)

                        // 如果删除的是当前使用的模型，切换到第一个可用的模型
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
}
