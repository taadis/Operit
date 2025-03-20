package com.ai.assistance.operit.data

/**
 * 工具类别映射，用于确定每个工具属于哪个类别
 */
object ToolCategoryMapper {
    
    // 存储工具名称和类别的映射关系
    private val toolCategoryMap = mapOf(
        // 系统操作工具
        "get_system_setting" to ToolCategory.SYSTEM_OPERATION,
        "modify_system_setting" to ToolCategory.SYSTEM_OPERATION,
        "install_app" to ToolCategory.SYSTEM_OPERATION,
        "uninstall_app" to ToolCategory.SYSTEM_OPERATION,
        "list_installed_apps" to ToolCategory.SYSTEM_OPERATION,
        "start_app" to ToolCategory.SYSTEM_OPERATION,
        "stop_app" to ToolCategory.SYSTEM_OPERATION,
        
        // 网络工具
        "fetch_web_page" to ToolCategory.NETWORK,
        "http_request" to ToolCategory.NETWORK,
        "web_search" to ToolCategory.NETWORK,
        "download_file" to ToolCategory.NETWORK,
        
        // UI自动化工具
        "get_page_info" to ToolCategory.UI_AUTOMATION,
        "tap" to ToolCategory.UI_AUTOMATION,
        "click_element" to ToolCategory.UI_AUTOMATION,
        "set_input_text" to ToolCategory.UI_AUTOMATION,
        "press_key" to ToolCategory.UI_AUTOMATION,
        "swipe" to ToolCategory.UI_AUTOMATION,
        "launch_app" to ToolCategory.UI_AUTOMATION,
        "combined_operation" to ToolCategory.UI_AUTOMATION,
        
        // 文件读取工具
        "list_files" to ToolCategory.FILE_READ,
        "read_file" to ToolCategory.FILE_READ,
        "file_exists" to ToolCategory.FILE_READ,
        "find_files" to ToolCategory.FILE_READ,
        "file_info" to ToolCategory.FILE_READ,
        "open_file" to ToolCategory.FILE_READ,
        
        // 文件写入工具
        "write_file" to ToolCategory.FILE_WRITE,
        "delete_file" to ToolCategory.FILE_WRITE,
        "move_file" to ToolCategory.FILE_WRITE,
        "copy_file" to ToolCategory.FILE_WRITE,
        "make_directory" to ToolCategory.FILE_WRITE,
        "zip_files" to ToolCategory.FILE_WRITE,
        "unzip_files" to ToolCategory.FILE_WRITE,
        "share_file" to ToolCategory.FILE_WRITE,
        
        // 基本工具（这些工具较为安全，划分为文件读取类别）
        "calculate" to ToolCategory.FILE_READ,
        "sleep" to ToolCategory.FILE_READ,
        "device_info" to ToolCategory.FILE_READ
    )
    
    /**
     * 获取工具所属类别
     * @param toolName 工具名称
     * @return 工具类别，如果未找到则默认为UI自动化类别（最高安全等级）
     */
    fun getToolCategory(toolName: String): ToolCategory {
        return toolCategoryMap[toolName] ?: ToolCategory.UI_AUTOMATION
    }
    
    /**
     * 判断工具是否为危险操作（用于UI警惕模式）
     * @param toolName 工具名称
     * @param parameters 工具参数
     * @return 是否是危险操作
     */
    fun isDangerousOperation(toolName: String, parameters: List<com.ai.assistance.operit.model.ToolParameter>): Boolean {
        return when (toolName) {
            "click_element" -> {
                // 检查是否点击敏感按钮
                val resourceId = parameters.find { it.name == "resourceId" }?.value ?: ""
                val className = parameters.find { it.name == "className" }?.value ?: ""
                val dangerousWords = listOf(
                    "send", "submit", "confirm", "pay", "purchase", "buy", "delete", "remove",
                    "发送", "提交", "确认", "支付", "购买", "删除", "移除"
                )
                
                dangerousWords.any { word ->
                    resourceId.contains(word, ignoreCase = true) || 
                    className.contains(word, ignoreCase = true)
                }
            }
            "combined_operation" -> {
                // 解析combined_operation中的操作
                val operation = parameters.find { it.name == "operation" }?.value ?: ""
                if (operation.startsWith("click_element")) {
                    val dangerousWords = listOf(
                        "send", "submit", "confirm", "pay", "purchase", "buy", "delete", "remove",
                        "发送", "提交", "确认", "支付", "购买", "删除", "移除"
                    )
                    dangerousWords.any { word -> operation.contains(word, ignoreCase = true) }
                } else {
                    false
                }
            }
            "delete_file", "write_file", "move_file", "modify_system_setting", 
            "install_app", "uninstall_app", "stop_app" -> true
            else -> false
        }
    }
    
    /**
     * 获取操作的可读描述
     * @param toolName 工具名称
     * @param parameters 工具参数
     * @return 操作描述
     */
    fun getOperationDescription(toolName: String, parameters: List<com.ai.assistance.operit.model.ToolParameter>): String {
        return when (toolName) {
            "tap" -> {
                val x = parameters.find { it.name == "x" }?.value ?: "?"
                val y = parameters.find { it.name == "y" }?.value ?: "?"
                "点击屏幕坐标 ($x, $y)"
            }
            "click_element" -> {
                val resourceId = parameters.find { it.name == "resourceId" }?.value
                val className = parameters.find { it.name == "className" }?.value
                val index = parameters.find { it.name == "index" }?.value ?: "0"
                
                when {
                    resourceId != null -> "点击元素 [资源ID: $resourceId" + (if (index != "0") ", 索引: $index" else "") + "]"
                    className != null -> "点击元素 [类名: $className" + (if (index != "0") ", 索引: $index" else "") + "]"
                    else -> "点击元素"
                }
            }
            "set_input_text" -> {
                val text = parameters.find { it.name == "text" }?.value ?: ""
                "设置输入文本: \"$text\""
            }
            "press_key" -> {
                val keyCode = parameters.find { it.name == "keyCode" }?.value ?: ""
                "按键: $keyCode"
            }
            "swipe" -> {
                val startX = parameters.find { it.name == "startX" }?.value ?: "?"
                val startY = parameters.find { it.name == "startY" }?.value ?: "?"
                val endX = parameters.find { it.name == "endX" }?.value ?: "?"
                val endY = parameters.find { it.name == "endY" }?.value ?: "?"
                "滑动屏幕 从 ($startX, $startY) 到 ($endX, $endY)"
            }
            "combined_operation" -> {
                val operation = parameters.find { it.name == "operation" }?.value ?: ""
                "执行组合操作: $operation"
            }
            "write_file" -> {
                val path = parameters.find { it.name == "path" }?.value ?: ""
                val append = parameters.find { it.name == "append" }?.value == "true"
                if (append) "追加内容到文件: $path" else "写入内容到文件: $path"
            }
            "delete_file" -> {
                val path = parameters.find { it.name == "path" }?.value ?: ""
                val recursive = parameters.find { it.name == "recursive" }?.value == "true"
                if (recursive) "递归删除: $path" else "删除文件: $path"
            }
            "read_file" -> {
                val path = parameters.find { it.name == "path" }?.value ?: ""
                "读取文件: $path"
            }
            "list_files" -> {
                val path = parameters.find { it.name == "path" }?.value ?: ""
                "列出目录内容: $path"
            }
            "launch_app" -> {
                val packageName = parameters.find { it.name == "packageName" }?.value ?: ""
                "启动应用: $packageName"
            }
            "http_request" -> {
                val url = parameters.find { it.name == "url" }?.value ?: ""
                val method = parameters.find { it.name == "method" }?.value ?: "GET"
                "$method 请求: $url"
            }
            else -> "$toolName 操作"
        }
    }
} 