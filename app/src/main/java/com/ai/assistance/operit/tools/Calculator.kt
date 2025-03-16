package com.ai.assistance.operit.tools

/**
 * Calculator class that handles mathematical expression evaluation
 * Extracted from AIToolHandler to separate calculation functionality
 */
class Calculator {
    companion object {
        /**
         * Evaluates a mathematical expression
         * @param expression The expression to evaluate
         * @return The calculated result
         */
        fun evalExpression(expression: String): Double {
            // Using Android's built-in expressions evaluator
            // This is a simplified implementation for basic expressions
            try {
                // Clean and prepare the expression
                val preparedExpression = expression.trim()
                    .replace("\\s+".toRegex(), "")  // Remove all whitespace
                
                // Handle basic mathematical functions
                return when {
                    preparedExpression.contains("sqrt") -> {
                        val value = preparedExpression.substringAfter("sqrt").trim('(', ')')
                        Math.sqrt(evalExpression(value))
                    }
                    preparedExpression.contains("sin") -> {
                        val value = preparedExpression.substringAfter("sin").trim('(', ')')
                        Math.sin(Math.toRadians(evalExpression(value)))
                    }
                    preparedExpression.contains("cos") -> {
                        val value = preparedExpression.substringAfter("cos").trim('(', ')')
                        Math.cos(Math.toRadians(evalExpression(value)))
                    }
                    preparedExpression.contains("tan") -> {
                        val value = preparedExpression.substringAfter("tan").trim('(', ')')
                        Math.tan(Math.toRadians(evalExpression(value)))
                    }
                    preparedExpression.contains("log") -> {
                        val value = preparedExpression.substringAfter("log").trim('(', ')')
                        Math.log10(evalExpression(value))
                    }
                    preparedExpression.contains("ln") -> {
                        val value = preparedExpression.substringAfter("ln").trim('(', ')')
                        Math.log(evalExpression(value))
                    }
                    preparedExpression.equals("pi", ignoreCase = true) -> Math.PI
                    preparedExpression.equals("e", ignoreCase = true) -> Math.E
                    "+" in preparedExpression -> {
                        // Find the rightmost addition that's not inside parentheses
                        var parenthesesCount = 0
                        var splitIndex = -1
                        
                        for (i in preparedExpression.indices.reversed()) {
                            val char = preparedExpression[i]
                            if (char == ')') parenthesesCount++
                            else if (char == '(') parenthesesCount--
                            else if (char == '+' && parenthesesCount == 0) {
                                splitIndex = i
                                break
                            }
                        }
                        
                        if (splitIndex == -1) {
                            // No suitable '+' found, try to evaluate as is
                            preparedExpression.toDouble()
                        } else {
                            val left = preparedExpression.substring(0, splitIndex)
                            val right = preparedExpression.substring(splitIndex + 1)
                            evalExpression(left) + evalExpression(right)
                        }
                    }
                    "-" in preparedExpression && !preparedExpression.startsWith("-") -> {
                        // Find the rightmost subtraction that's not inside parentheses
                        var parenthesesCount = 0
                        var splitIndex = -1
                        
                        for (i in preparedExpression.indices.reversed()) {
                            val char = preparedExpression[i]
                            if (char == ')') parenthesesCount++
                            else if (char == '(') parenthesesCount--
                            else if (char == '-' && parenthesesCount == 0 && i > 0) {
                                splitIndex = i
                                break
                            }
                        }
                        
                        if (splitIndex == -1) {
                            // No suitable '-' found, try to evaluate as is
                            preparedExpression.toDouble()
                        } else {
                            val left = preparedExpression.substring(0, splitIndex)
                            val right = preparedExpression.substring(splitIndex + 1)
                            evalExpression(left) - evalExpression(right)
                        }
                    }
                    "*" in preparedExpression -> {
                        // Find the rightmost multiplication that's not inside parentheses
                        var parenthesesCount = 0
                        var splitIndex = -1
                        
                        for (i in preparedExpression.indices.reversed()) {
                            val char = preparedExpression[i]
                            if (char == ')') parenthesesCount++
                            else if (char == '(') parenthesesCount--
                            else if (char == '*' && parenthesesCount == 0) {
                                splitIndex = i
                                break
                            }
                        }
                        
                        if (splitIndex == -1) {
                            // No suitable '*' found, try to evaluate as is
                            preparedExpression.toDouble()
                        } else {
                            val left = preparedExpression.substring(0, splitIndex)
                            val right = preparedExpression.substring(splitIndex + 1)
                            evalExpression(left) * evalExpression(right)
                        }
                    }
                    "/" in preparedExpression -> {
                        // Find the rightmost division that's not inside parentheses
                        var parenthesesCount = 0
                        var splitIndex = -1
                        
                        for (i in preparedExpression.indices.reversed()) {
                            val char = preparedExpression[i]
                            if (char == ')') parenthesesCount++
                            else if (char == '(') parenthesesCount--
                            else if (char == '/' && parenthesesCount == 0) {
                                splitIndex = i
                                break
                            }
                        }
                        
                        if (splitIndex == -1) {
                            // No suitable '/' found, try to evaluate as is
                            preparedExpression.toDouble()
                        } else {
                            val left = preparedExpression.substring(0, splitIndex)
                            val right = preparedExpression.substring(splitIndex + 1)
                            evalExpression(left) / evalExpression(right)
                        }
                    }
                    preparedExpression.startsWith("(") && preparedExpression.endsWith(")") -> {
                        // Remove outer parentheses and evaluate inner expression
                        evalExpression(preparedExpression.substring(1, preparedExpression.length - 1))
                    }
                    else -> preparedExpression.toDouble() // Simple number
                }
            } catch (e: Exception) {
                // If parsing fails, fall back to basic arithmetic
                return when {
                    "+" in expression -> {
                        val parts = expression.split("+")
                        parts[0].trim().toDouble() + parts[1].trim().toDouble()
                    }
                    "-" in expression && !expression.startsWith("-") -> {
                        val parts = expression.split("-")
                        parts[0].trim().toDouble() - parts[1].trim().toDouble()
                    }
                    "*" in expression -> {
                        val parts = expression.split("*")
                        parts[0].trim().toDouble() * parts[1].trim().toDouble()
                    }
                    "/" in expression -> {
                        val parts = expression.split("/")
                        parts[0].trim().toDouble() / parts[1].trim().toDouble()
                    }
                    else -> expression.trim().toDouble()
                }
            }
        }
    }
} 