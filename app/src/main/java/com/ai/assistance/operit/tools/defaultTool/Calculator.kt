package com.ai.assistance.operit.tools

import com.ai.assistance.operit.tools.calculator.Calculator as CalcImpl
import java.util.Date

/**
 * 增强的计算器类，支持数学表达式计算、日期计算和JavaScript语法特性
 * 提供安全的表达式计算，替代eval()
 */
class Calculator {
    companion object {
        /**
         * 计算表达式
         */
        fun evalExpression(expression: String): Double {
            return CalcImpl.evalExpression(expression)
        }
        
        /**
         * 获取变量值
         */
        fun getVariable(name: String): Double? {
            return CalcImpl.getVariable(name)
        }
        
        /**
         * 设置变量值
         */
        fun setVariable(name: String, value: Double) {
            CalcImpl.setVariable(name, value)
        }
        
        /**
         * 清除所有变量
         */
        fun clearVariables() {
            CalcImpl.clearVariables()
        }
        
        /**
         * 格式化日期
         */
        fun formatDate(date: Date, format: String): String {
            return CalcImpl.formatDate(date, format)
        }
        
        /**
         * 格式化结果
         */
        fun formatResult(result: Double): String {
            return CalcImpl.formatResult(result)
        }
        
        /**
         * 获取支持的单位列表
         */
        fun getSupportedUnits(): Map<String, List<String>> {
            return CalcImpl.getSupportedUnits()
        }
        
        /**
         * 获取支持的日期函数
         */
        fun getSupportedDateFunctions(): List<String> {
            return CalcImpl.getSupportedDateFunctions()
        }
        
        /**
         * 获取支持的统计函数
         */
        fun getSupportedStatFunctions(): List<String> {
            return CalcImpl.getSupportedStatFunctions()
        }
        
        /**
         * 获取支持的JavaScript特性
         */
        fun getSupportedJsFeatures(): List<String> {
            return CalcImpl.getSupportedJsFeatures()
        }
    }
} 