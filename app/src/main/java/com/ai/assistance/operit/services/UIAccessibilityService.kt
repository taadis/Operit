package com.ai.assistance.operit.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistance.operit.core.data.UIHierarchyManager
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import android.util.Xml

/**
 * 无障碍服务，用于获取UI层次结构
 */
class UIAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "UIAccessibilityService"
        
        // 单例实例
        private var instance: UIAccessibilityService? = null
        
        // 获取服务实例
        fun getInstance(): UIAccessibilityService? {
            return instance
        }
        
        // 检查服务是否运行
        fun isRunning(): Boolean {
            return instance != null
        }
    }
    
    override fun onServiceConnected() {
        Log.d(TAG, "无障碍服务连接成功")
        
        // 设置服务配置
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        
        // 设置超时，确保服务不会被系统快速终止
        info.notificationTimeout = 100
        
        // 更新配置
        serviceInfo = info
        
        // 保存实例引用
        instance = this
        
        // 通知UIHierarchyManager服务已启动
        UIHierarchyManager.setAccessibilityServiceRunning(true)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 无需处理事件，我们只使用服务来获取UI层次结构
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "无障碍服务已解绑")
        instance = null
        UIHierarchyManager.setAccessibilityServiceRunning(false)
        return super.onUnbind(intent)
    }
    
    /**
     * 获取当前界面的UI层次结构
     * @return XML格式的UI层次结构字符串
     */
    fun getUIHierarchy(): String {
        val rootNode = rootInActiveWindow ?: return ""
        return convertNodeToXml(rootNode)
    }
    
    /**
     * 转换AccessibilityNodeInfo为XML字符串
     */
    private fun convertNodeToXml(rootNode: AccessibilityNodeInfo): String {
        val serializer = Xml.newSerializer()
        val writer = StringWriter()
        
        try {
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", true)
            
            // 开始根节点
            serializer.startTag("", "hierarchy")
            
            // 递归序列化节点
            serializeNode(serializer, rootNode)
            
            // 结束根节点
            serializer.endTag("", "hierarchy")
            serializer.endDocument()
            
            return writer.toString()
        } catch (e: Exception) {
            Log.e(TAG, "转换UI节点到XML出错", e)
            return ""
        } finally {
            rootNode.recycle()
        }
    }
    
    /**
     * 递归序列化节点
     */
    private fun serializeNode(serializer: XmlSerializer, node: AccessibilityNodeInfo) {
        try {
            serializer.startTag("", "node")
            
            // 添加节点属性
            serializer.attribute("", "class", sanitizeXmlString(node.className?.toString() ?: ""))
            serializer.attribute("", "package", sanitizeXmlString(node.packageName?.toString() ?: ""))
            serializer.attribute("", "content-desc", sanitizeXmlString(node.contentDescription?.toString() ?: ""))
            serializer.attribute("", "text", sanitizeXmlString(node.text?.toString() ?: ""))
            serializer.attribute("", "resource-id", sanitizeXmlString(node.viewIdResourceName ?: ""))
            serializer.attribute("", "clickable", node.isClickable.toString())
            serializer.attribute("", "enabled", node.isEnabled.toString())
            serializer.attribute("", "focused", node.isFocused.toString())
            serializer.attribute("", "selected", node.isSelected.toString())
            
            // 获取节点边界
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            serializer.attribute("", "bounds", "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]")
            
            // 处理子节点
            for (i in 0 until node.childCount) {
                val childNode = node.getChild(i) ?: continue
                serializeNode(serializer, childNode)
            }
            
            serializer.endTag("", "node")
        } catch (e: Exception) {
            Log.e(TAG, "序列化节点出错", e)
        }
    }
    
    /**
     * 清理字符串以确保XML兼容性
     * 移除或替换XML中不允许的字符
     */
    private fun sanitizeXmlString(input: String): String {
        if (input.isEmpty()) return input
        
        val sb = StringBuilder(input.length)
        for (c in input) {
            // XML 1.0 规范中允许的字符:
            // #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
            // 以下字符被排除：控制字符，不可见字符和某些特殊Unicode范围
            if (c == '\u0009' || c == '\n' || c == '\r' || 
                (c >= '\u0020' && c <= '\uD7FF') || 
                (c >= '\uE000' && c <= '\uFFFD')) {
                sb.append(c)
            } else {
                // 对于不合法字符，可以替换为空格或其他合法字符
                sb.append(' ')
                
                // 记录不合法字符的处理
                Log.d(TAG, "替换了不合法XML字符: U+${Integer.toHexString(c.code)}")
            }
        }
        return sb.toString()
    }
} 