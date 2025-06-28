package com.ai.assistance.operit.api.chat.enhance

import android.util.Log
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.PlanItemStatus
import java.util.UUID

/**
 * 专门处理计划项解析的工具类
 * 
 * 处理各种格式的plan_item和plan_update标签，提取计划项及其状态更新信息
 */
class PlanItemParser {
    companion object {
        private const val TAG = "PlanItemParser"
        
        // XML标签模式匹配 - 只保留两个主要的正则表达式，但确保它们足够健壮
        // 匹配plan_item标签，提取id、status和description
        private val planItemPattern = Regex("<plan_item\\s+(?:.*?)?id=\"([^\"]+)\"(?:.*?)status=\"([^\"]+)\"(?:.*?)>([^<]+)</plan_item>", RegexOption.DOT_MATCHES_ALL)
        
        // 匹配plan_update标签，提取id和status，支持任意格式（自闭合、空标签、普通标签）
        private val planUpdatePattern = Regex("<plan_update\\s+(?:.*?)?(?:id=\"([^\"]+)\"(?:.*?)status=\"([^\"]+)\"|status=\"([^\"]+)\"(?:.*?)id=\"([^\"]+)\")(?:.*?)(?:/>|></plan_update>|>(?:[^<]*)</plan_update>)", RegexOption.DOT_MATCHES_ALL)
        
        /**
         * 创建计划项标签
         * 
         * @param description 计划项描述
         * @return 格式化的计划项元素
         */
        fun createPlanItem(description: String): String {
            val id = UUID.randomUUID().toString()
            return "<plan_item id=\"$id\" status=\"todo\">$description</plan_item>"
        }
        
        /**
         * 更新计划项状态
         * 
         * @param id 计划项的ID
         * @param status 计划项的新状态
         * @param message 可选的状态更新消息
         * @return 格式化的计划状态更新元素
         */
        fun createPlanStatusUpdate(id: String, status: String, message: String? = null): String {
            return if (message != null) {
                "<plan_update id=\"$id\" status=\"$status\">$message</plan_update>"
            } else {
                "<plan_update id=\"$id\" status=\"$status\"></plan_update>"
            }
        }
        
        /**
         * 创建计划任务完成标记
         * 
         * @param id 计划项的ID
         * @param success 任务是否成功完成
         * @param message 关于完成的可选消息
         * @return 格式化的计划任务完成元素
         */
        fun createPlanTaskCompletion(id: String, success: Boolean, message: String? = null): String {
            val status = if (success) "completed" else "failed"
            return createPlanStatusUpdate(id, status, message)
        }
        
        /**
         * 从内容中提取所有计划项和状态更新
         * 
         * @param content 需要解析的文本内容
         * @return 提取的计划项列表
         */
        fun extractPlanItems(content: String): List<PlanItem> {
            val planItems = mutableListOf<PlanItem>()
            
            // 1. 首先提取计划项
            findPlanItems(content, planItems)
            
            // 2. 然后处理计划项更新
            processPlanUpdates(content, planItems)
            
            return planItems
        }
        
        /**
         * 从内容中提取所有计划项和状态更新，可以传入现有的计划项列表
         * 
         * @param content 需要解析的文本内容
         * @param existingItems 已有的计划项列表，用于更新状态
         * @return 提取和更新后的计划项列表
         */
        fun extractPlanItems(content: String, existingItems: List<PlanItem>): List<PlanItem> {
            val planItems = mutableListOf<PlanItem>()
            
            // 先添加所有现有的计划项
            planItems.addAll(existingItems)
            Log.d(TAG, "传入现有计划项: ${planItems.size}个")
            
            // 1. 从内容中提取新的计划项
            findPlanItems(content, planItems)
            
            // 2. 处理计划项更新
            processPlanUpdates(content, planItems)
            
            return planItems
        }
        
        /**
         * 提取计划项
         */
        private fun findPlanItems(content: String, planItems: MutableList<PlanItem>) {
            planItemPattern.findAll(content).forEach { match ->
                try {
                    val id = match.groupValues[1]
                    val statusStr = match.groupValues[2]
                    val description = match.groupValues[3].trim()
                    
                    if (id.isNotEmpty() && statusStr.isNotEmpty() && description.isNotEmpty()) {
                        // 转换状态字符串为枚举
                        val status = convertStatusString(statusStr)
                        
                        // 创建并添加计划项
                        planItems.add(
                            PlanItem(
                                id = id,
                                description = description,
                                status = status
                            )
                        )
                        
                        Log.d(TAG, "找到计划项: id=$id, status=$statusStr")
                    } else {
                        Log.w(TAG, "计划项缺少必要信息: id=$id")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析plan_item异常: ${e.message}")
                }
            }
        }
        
        /**
         * 处理计划项更新
         */
        private fun processPlanUpdates(content: String, planItems: MutableList<PlanItem>) {
            // 记录当前计划项的状态，用于调试
            if (planItems.isNotEmpty()) {
                Log.d(TAG, "处理更新前计划项状态: ${planItems.size}个")
                
                // 检查是否有多个正在进行的任务
                val inProgressItems = planItems.filter { it.status == PlanItemStatus.IN_PROGRESS }
                if (inProgressItems.size > 1) {
                    Log.w(TAG, "警告: 当前有多个进行中的计划项: ${inProgressItems.size}个")
                }
            }
            
            planUpdatePattern.findAll(content).forEach { match ->
                try {
                    // 提取ID - 可能在第1组或第4组
                    val id = match.groupValues[1].ifEmpty { match.groupValues[4] }
                    // 提取状态 - 可能在第2组或第3组
                    val statusStr = match.groupValues[2].ifEmpty { match.groupValues[3] }
                    
                    if (id.isNotEmpty() && statusStr.isNotEmpty()) {
                        Log.d(TAG, "找到计划更新: id=$id, status=$statusStr")
                        updatePlanItemStatus(planItems, id, statusStr)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析plan_update异常: ${e.message}")
                }
            }
        }
        
        /**
         * 更新计划项状态的通用方法
         */
        private fun updatePlanItemStatus(planItems: MutableList<PlanItem>, id: String, statusStr: String) {
            val existingItemIndex = planItems.indexOfFirst { it.id == id }
            if (existingItemIndex >= 0) {
                // 转换状态字符串为枚举
                val status = convertStatusString(statusStr)
                val existingItem = planItems[existingItemIndex]
                
                // 只有状态有变化时才更新
                if (existingItem.status != status) {
                    // 更新计划项
                    planItems[existingItemIndex] = existingItem.copy(
                        status = status,
                        completedAt = if (status == PlanItemStatus.COMPLETED || status == PlanItemStatus.FAILED) 
                            System.currentTimeMillis() else existingItem.completedAt
                    )
                }
            } else {
                Log.w(TAG, "找不到ID为 ${id} 的计划项进行状态更新，当前列表中有 ${planItems.size} 个项目")
            }
        }
        
        /**
         * 将状态字符串转换为枚举值
         */
        private fun convertStatusString(statusStr: String): PlanItemStatus {
            return when (statusStr.lowercase()) {
                "todo" -> PlanItemStatus.TODO
                "in_progress" -> PlanItemStatus.IN_PROGRESS
                "completed" -> PlanItemStatus.COMPLETED
                "failed" -> PlanItemStatus.FAILED
                "cancelled" -> PlanItemStatus.CANCELLED
                else -> PlanItemStatus.TODO
            }
        }
        
        /**
         * 检查内容是否包含任何计划相关的元素
         */
        fun containsPlanElements(content: String): Boolean {
            return content.contains("<plan_item") || content.contains("<plan_update")
        }
    }
} 