package com.ai.assistance.operit.tools.packTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.javascript.JsToolManager

/**
 * Interpreter for the OperLang scripting language that defines custom tools
 * This class now uses the new JavaScript tool system instead of OperScript
 */
class OperLangInterpreter private constructor(
    private val context: Context,
    private val packageManager: PackageManager
) {
    companion object {
        private const val TAG = "OperLangInterpreter"
        
        @Volatile
        private var INSTANCE: OperLangInterpreter? = null
        
        fun getInstance(context: Context, packageManager: PackageManager): OperLangInterpreter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OperLangInterpreter(context.applicationContext, packageManager).also { INSTANCE = it }
            }
        }
    }
    
    // Get the JavaScript tool manager instance
    private val jsToolManager = JsToolManager.getInstance(context, packageManager)
    
    /**
     * Executes an OperLang script with the given tool parameters
     * Now delegates to JavaScript execution
     */
    fun executeScript(script: String, tool: AITool): ToolResult {
        Log.d(TAG, "OperLangInterpreter using JavaScript execution: ${tool.name}")
        
        // 检查脚本是否包含 OperScript 语法，如果是则进行转换
        val jsScript = if (isOperScriptSyntax(script)) {
            convertOperScriptToJs(script)
        } else {
            script
        }
        
        // 使用 JavaScript 执行脚本
        return jsToolManager.executeScript(jsScript, tool)
    }
    
    /**
     * 检查脚本是否使用 OperScript 语法
     */
    private fun isOperScriptSyntax(script: String): Boolean {
        val operScriptKeywords = listOf("CALL", "LET", "IF", "ELSE", "FOREACH", "IN", "WHILE", "FUNCTION", "CALC", "TRY", "CATCH", "OUT")
        
        // 简单检查是否包含 OperScript 关键字
        return operScriptKeywords.any { keyword -> 
            script.contains(Regex("\\b$keyword\\b"))
        }
    }
    
    /**
     * 将 OperScript 语法转换为 JavaScript 语法
     * 这是一个简单的实现，实际应用可能需要更复杂的语法分析
     */
    private fun convertOperScriptToJs(script: String): String {
        var jsScript = script
        
        // 替换 CALL 关键字为 toolCall 函数
        jsScript = jsScript.replace(
            Regex("CALL\\s+(\\w+)\\s+\"([^\"]+)\"\\s+\\{([^}]*)\\}"),
            "toolCall(\"$1\", \"$2\", {$3})"
        )
        
        // 替换 LET 关键字为 const
        jsScript = jsScript.replace(
            Regex("LET\\s+(\\w+)\\s+=\\s+"),
            "const $1 = "
        )
        
        // 替换 CALC 关键字（简单移除）
        jsScript = jsScript.replace(
            Regex("CALC\\s+"),
            ""
        )
        
        // 替换 OUT 关键字为 console.log
        jsScript = jsScript.replace(
            Regex("OUT\\s+(.*)"),
            "console.log($1)"
        )
        
        // 添加完成回调
        jsScript += "\n\n// 自动添加的完成回调\ncomplete(\"Script execution completed\");"
        
        return jsScript
    }
} 