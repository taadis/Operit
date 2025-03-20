package com.ai.assistance.operit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 定义 DataStore
private val Context.toolPermissionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_permissions")

/**
 * 工具权限级别枚举
 */
enum class PermissionLevel {
    ALLOW,      // 允许 - 自动执行，不询问
    CAUTION,    // 警惕 - 对危险操作询问，其他允许
    ASK,        // 询问 - 总是询问
    FORBID;     // 禁止 - 不允许执行
    
    companion object {
        fun fromString(value: String?): PermissionLevel {
            return when (value) {
                "ALLOW" -> ALLOW
                "CAUTION" -> CAUTION
                "ASK" -> ASK
                "FORBID" -> FORBID
                else -> ASK  // 默认为询问
            }
        }
    }
}

/**
 * 工具类别枚举
 */
enum class ToolCategory {
    SYSTEM_OPERATION,    // 系统操作 (如修改设置)
    NETWORK,             // 网络 (如HTTP请求)
    UI_AUTOMATION,       // UI自动化 (如点击)
    FILE_READ,           // 文件读取 (如读文件)
    FILE_WRITE;          // 文件写入 (如写入/删除文件)
    
    companion object {
        fun getDefaultPermissionLevel(category: ToolCategory): PermissionLevel {
            return when (category) {
                SYSTEM_OPERATION -> PermissionLevel.ASK
                NETWORK -> PermissionLevel.ALLOW
                UI_AUTOMATION -> PermissionLevel.CAUTION
                FILE_READ -> PermissionLevel.ALLOW
                FILE_WRITE -> PermissionLevel.ASK
            }
        }
    }
}

/**
 * 工具权限管理类
 */
class ToolPermissionPreferences(private val context: Context) {
    
    companion object {
        // 全局权限开关
        val MASTER_SWITCH = stringPreferencesKey("master_switch")
        
        // 工具类别权限设置
        val SYSTEM_OPERATION_PERMISSION = stringPreferencesKey("system_operation_permission")
        val NETWORK_PERMISSION = stringPreferencesKey("network_permission")
        val UI_AUTOMATION_PERMISSION = stringPreferencesKey("ui_automation_permission")
        val FILE_READ_PERMISSION = stringPreferencesKey("file_read_permission")
        val FILE_WRITE_PERMISSION = stringPreferencesKey("file_write_permission")
        
        // 默认权限设置
        val DEFAULT_MASTER_SWITCH = PermissionLevel.ASK.name
    }
    
    // 获取全局权限开关设置
    val masterSwitchFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
    }
    
    // 获取系统操作权限设置
    val systemOperationPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[SYSTEM_OPERATION_PERMISSION] 
            ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.SYSTEM_OPERATION).name)
    }
    
    // 获取网络权限设置
    val networkPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[NETWORK_PERMISSION] 
            ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.NETWORK).name)
    }
    
    // 获取UI自动化权限设置
    val uiAutomationPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[UI_AUTOMATION_PERMISSION] 
            ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.UI_AUTOMATION).name)
    }
    
    // 获取文件读取权限设置
    val fileReadPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[FILE_READ_PERMISSION] 
            ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_READ).name)
    }
    
    // 获取文件写入权限设置
    val fileWritePermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[FILE_WRITE_PERMISSION] 
            ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_WRITE).name)
    }
    
    // 保存全局权限开关
    suspend fun saveMasterSwitch(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = level.name
        }
    }
    
    // 保存系统操作权限
    suspend fun saveSystemOperationPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[SYSTEM_OPERATION_PERMISSION] = level.name
        }
    }
    
    // 保存网络权限
    suspend fun saveNetworkPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[NETWORK_PERMISSION] = level.name
        }
    }
    
    // 保存UI自动化权限
    suspend fun saveUIAutomationPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[UI_AUTOMATION_PERMISSION] = level.name
        }
    }
    
    // 保存文件读取权限
    suspend fun saveFileReadPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[FILE_READ_PERMISSION] = level.name
        }
    }
    
    // 保存文件写入权限
    suspend fun saveFileWritePermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[FILE_WRITE_PERMISSION] = level.name
        }
    }
    
    // 保存所有权限设置
    suspend fun saveAllPermissions(
        masterSwitch: PermissionLevel,
        systemOperation: PermissionLevel,
        network: PermissionLevel,
        uiAutomation: PermissionLevel,
        fileRead: PermissionLevel,
        fileWrite: PermissionLevel
    ) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = masterSwitch.name
            preferences[SYSTEM_OPERATION_PERMISSION] = systemOperation.name
            preferences[NETWORK_PERMISSION] = network.name
            preferences[UI_AUTOMATION_PERMISSION] = uiAutomation.name
            preferences[FILE_READ_PERMISSION] = fileRead.name
            preferences[FILE_WRITE_PERMISSION] = fileWrite.name
        }
    }
} 