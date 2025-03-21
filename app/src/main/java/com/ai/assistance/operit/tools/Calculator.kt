package com.ai.assistance.operit.tools

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Enhanced Calculator class that handles mathematical expression evaluation and date calculations
 * Provides a powerful alternative to eval() for safe expression evaluation
 */
class Calculator {
    companion object {
        private val DATE_FORMATS = arrayOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss"
        )

        // Variables map for storing temporary values
        private val variables = mutableMapOf<String, Double>()

        /**
         * Evaluates a mathematical or date expression
         * @param expression The expression to evaluate
         * @return The calculated result
         */
        fun evalExpression(expression: String): Double {
            try {
                // Clean and prepare the expression
                val preparedExpression = expression.trim()
                    .replace("\\s+".toRegex(), "")  // Remove all whitespace
                
                // Check for date calculation
                if (preparedExpression.contains("date") || 
                    preparedExpression.contains("days") || 
                    preparedExpression.contains("weekday") ||
                    preparedExpression.matches(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*".toRegex())) {
                    return handleDateCalculation(preparedExpression)
                }
                
                // Check for unit conversion
                if (preparedExpression.contains("convert")) {
                    return handleUnitConversion(preparedExpression)
                }
                
                // Check for statistical functions
                if (preparedExpression.contains("stats.")) {
                    return handleStatisticalFunction(preparedExpression)
                }
                
                // Check for variable assignment (x=5)
                if (preparedExpression.matches("[a-zA-Z][a-zA-Z0-9]*=.*".toRegex())) {
                    return handleVariableAssignment(preparedExpression)
                }
                
                // Check for variable usage
                if (preparedExpression.matches("[a-zA-Z][a-zA-Z0-9]*".toRegex())) {
                    return variables[preparedExpression] ?: throw IllegalArgumentException("Variable $preparedExpression not defined")
                }
                
                // Handle if/then/else conditional
                if (preparedExpression.contains("if(") && preparedExpression.contains("then") && preparedExpression.contains("else")) {
                    return handleConditional(preparedExpression)
                }
                
                // Handle exponential notation
                if (preparedExpression.contains("^")) {
                    val parts = preparedExpression.split("^")
                    return evalExpression(parts[0]).pow(evalExpression(parts[1]))
                }
                
                // Handle percentage calculation
                if (preparedExpression.contains("%")) {
                    // Find the rightmost percentage that's not inside parentheses
                    var parenthesesCount = 0
                    var splitIndex = -1
                    
                    for (i in preparedExpression.indices.reversed()) {
                        val char = preparedExpression[i]
                        if (char == ')') parenthesesCount++
                        else if (char == '(') parenthesesCount--
                        else if (char == '%' && parenthesesCount == 0) {
                            splitIndex = i
                            break
                        }
                    }
                    
                    if (splitIndex != -1) {
                        val value = evalExpression(preparedExpression.substring(0, splitIndex))
                        return value / 100.0
                    }
                }

                // Handle mathematical functions
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
                    preparedExpression.contains("abs") -> {
                        val value = preparedExpression.substringAfter("abs").trim('(', ')')
                        Math.abs(evalExpression(value))
                    }
                    preparedExpression.contains("round") -> {
                        val value = preparedExpression.substringAfter("round").trim('(', ')')
                        Math.round(evalExpression(value)).toDouble()
                    }
                    preparedExpression.contains("floor") -> {
                        val value = preparedExpression.substringAfter("floor").trim('(', ')')
                        Math.floor(evalExpression(value))
                    }
                    preparedExpression.contains("ceil") -> {
                        val value = preparedExpression.substringAfter("ceil").trim('(', ')')
                        Math.ceil(evalExpression(value))
                    }
                    preparedExpression.contains("max") -> {
                        val params = preparedExpression.substringAfter("max").trim('(', ')').split(",")
                        if (params.size != 2) throw IllegalArgumentException("max requires exactly 2 parameters")
                        Math.max(evalExpression(params[0]), evalExpression(params[1]))
                    }
                    preparedExpression.contains("min") -> {
                        val params = preparedExpression.substringAfter("min").trim('(', ')').split(",")
                        if (params.size != 2) throw IllegalArgumentException("min requires exactly 2 parameters")
                        Math.min(evalExpression(params[0]), evalExpression(params[1]))
                    }
                    preparedExpression.equals("pi", ignoreCase = true) -> Math.PI
                    preparedExpression.equals("e", ignoreCase = true) -> Math.E
                    preparedExpression.contains("rand") -> {
                        Math.random()
                    }
                    preparedExpression.contains("fact") -> {
                        val value = preparedExpression.substringAfter("fact").trim('(', ')')
                        factorial(evalExpression(value).toInt()).toDouble()
                    }
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
                try {
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
                } catch (fallbackException: Exception) {
                    throw IllegalArgumentException("Cannot evaluate expression: $expression", e)
                }
            }
        }

        /**
         * Handle date calculations
         * Supports operations like:
         * - today() - Returns current date as timestamp
         * - now() - Returns current timestamp in milliseconds
         * - date(2023-01-01) - Parses a date string
         * - date_diff(date1, date2) - Calculate days between dates
         * - date_add(date, days) - Add days to a date
         * - weekday(date) - Get day of week (1-7, where 1 is Sunday)
         * - month(date) - Get month (1-12)
         * - year(date) - Get year
         * - day(date) - Get day of month
         */
        private fun handleDateCalculation(expression: String): Double {
            try {
                // Case 1: today() - returns current timestamp in days (from epoch)
                if (expression.contains("today()")) {
                    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).toDouble()
                }
                
                // Case 2: now() - returns current timestamp in milliseconds
                if (expression.contains("now()")) {
                    return System.currentTimeMillis().toDouble()
                }

                // Case 3: date(dateString) - parse a date string to timestamp in days
                if (expression.contains("date(")) {
                    val dateString = expression.substringAfter("date(").substringBefore(")")
                    val dateMillis = parseDate(dateString)?.time ?: 
                        throw IllegalArgumentException("Cannot parse date: $dateString")
                    return TimeUnit.MILLISECONDS.toDays(dateMillis).toDouble()
                }
                
                // Case 4: date_diff(date1, date2) - days between dates
                if (expression.contains("date_diff(")) {
                    val params = expression.substringAfter("date_diff(").substringBefore(")").split(",")
                    if (params.size != 2) throw IllegalArgumentException("date_diff requires exactly 2 parameters")
                    
                    val date1 = if (params[0].trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(params[0].trim()) ?: throw IllegalArgumentException("Cannot parse date: ${params[0]}")
                    }
                    
                    val date2 = if (params[1].trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(params[1].trim()) ?: throw IllegalArgumentException("Cannot parse date: ${params[1]}")
                    }
                    
                    val diffInMillis = Math.abs(date1.time - date2.time)
                    return TimeUnit.MILLISECONDS.toDays(diffInMillis).toDouble()
                }
                
                // Case 5: date_add(date, days) - add days to date
                if (expression.contains("date_add(")) {
                    val params = expression.substringAfter("date_add(").substringBefore(")").split(",")
                    if (params.size != 2) throw IllegalArgumentException("date_add requires exactly 2 parameters")
                    
                    val date = if (params[0].trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(params[0].trim()) ?: throw IllegalArgumentException("Cannot parse date: ${params[0]}")
                    }
                    
                    val daysToAdd = params[1].trim().toInt()
                    
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                    
                    return TimeUnit.MILLISECONDS.toDays(calendar.timeInMillis).toDouble()
                }
                
                // Case 6: weekday(date) - get day of week (1-7, where 1 is Sunday)
                if (expression.contains("weekday(")) {
                    val dateParam = expression.substringAfter("weekday(").substringBefore(")")
                    
                    val date = if (dateParam.trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(dateParam.trim()) ?: throw IllegalArgumentException("Cannot parse date: $dateParam")
                    }
                    
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    return calendar.get(Calendar.DAY_OF_WEEK).toDouble()
                }
                
                // Case 7: month(date) - get month (1-12)
                if (expression.contains("month(")) {
                    val dateParam = expression.substringAfter("month(").substringBefore(")")
                    
                    val date = if (dateParam.trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(dateParam.trim()) ?: throw IllegalArgumentException("Cannot parse date: $dateParam")
                    }
                    
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    return (calendar.get(Calendar.MONTH) + 1).toDouble() // Calendar.MONTH is 0-based
                }
                
                // Case 8: year(date) - get year
                if (expression.contains("year(")) {
                    val dateParam = expression.substringAfter("year(").substringBefore(")")
                    
                    val date = if (dateParam.trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(dateParam.trim()) ?: throw IllegalArgumentException("Cannot parse date: $dateParam")
                    }
                    
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    return calendar.get(Calendar.YEAR).toDouble()
                }
                
                // Case 9: day(date) - get day of month
                if (expression.contains("day(")) {
                    val dateParam = expression.substringAfter("day(").substringBefore(")")
                    
                    val date = if (dateParam.trim() == "today()") {
                        Date(System.currentTimeMillis())
                    } else {
                        parseDate(dateParam.trim()) ?: throw IllegalArgumentException("Cannot parse date: $dateParam")
                    }
                    
                    val calendar = Calendar.getInstance()
                    calendar.time = date
                    return calendar.get(Calendar.DAY_OF_MONTH).toDouble()
                }
                
                // Case 10: date_format(date, format)
                // This doesn't return a number but we can't have mixed return types, so throws exception for this case
                if (expression.contains("date_format(")) {
                    throw IllegalArgumentException("date_format returns a string, not a number. Use direct API instead.")
                }
                
                throw IllegalArgumentException("Unrecognized date operation: $expression")
            } catch (e: Exception) {
                throw IllegalArgumentException("Error in date calculation: ${e.message}")
            }
        }
        
        /**
         * Handle common unit conversions
         * Supports conversions like:
         * - convert(value, from_unit, to_unit)
         * - Units include: f (Fahrenheit), c (Celsius), km, mi (miles), m (meters), 
         *   ft (feet), kg (kilograms), lb (pounds)
         */
        private fun handleUnitConversion(expression: String): Double {
            try {
                if (expression.contains("convert(")) {
                    val params = expression.substringAfter("convert(").substringBefore(")").split(",")
                    if (params.size != 3) throw IllegalArgumentException("convert requires exactly 3 parameters: value, from_unit, to_unit")
                    
                    val value = evalExpression(params[0].trim())
                    val fromUnit = params[1].trim().lowercase()
                    val toUnit = params[2].trim().lowercase()
                    
                    return when {
                        // Temperature conversions
                        fromUnit == "f" && toUnit == "c" -> (value - 32) * 5 / 9 // F to C
                        fromUnit == "c" && toUnit == "f" -> value * 9 / 5 + 32 // C to F
                        fromUnit == "c" && toUnit == "k" -> value + 273.15 // C to K
                        fromUnit == "k" && toUnit == "c" -> value - 273.15 // K to C
                        fromUnit == "f" && toUnit == "k" -> (value - 32) * 5 / 9 + 273.15 // F to K
                        fromUnit == "k" && toUnit == "f" -> (value - 273.15) * 9 / 5 + 32 // K to F
                        
                        // Length conversions
                        fromUnit == "km" && toUnit == "mi" -> value * 0.621371 // km to miles
                        fromUnit == "mi" && toUnit == "km" -> value * 1.60934 // miles to km
                        fromUnit == "m" && toUnit == "ft" -> value * 3.28084 // meters to feet
                        fromUnit == "ft" && toUnit == "m" -> value * 0.3048 // feet to meters
                        fromUnit == "cm" && toUnit == "in" -> value * 0.393701 // cm to inches
                        fromUnit == "in" && toUnit == "cm" -> value * 2.54 // inches to cm
                        
                        // Weight conversions
                        fromUnit == "kg" && toUnit == "lb" -> value * 2.20462 // kg to pounds
                        fromUnit == "lb" && toUnit == "kg" -> value * 0.453592 // pounds to kg
                        fromUnit == "g" && toUnit == "oz" -> value * 0.035274 // grams to ounces
                        fromUnit == "oz" && toUnit == "g" -> value * 28.3495 // ounces to grams
                        
                        // Volume conversions
                        fromUnit == "l" && toUnit == "gal" -> value * 0.264172 // liters to gallons
                        fromUnit == "gal" && toUnit == "l" -> value * 3.78541 // gallons to liters
                        fromUnit == "ml" && toUnit == "oz" -> value * 0.033814 // milliliters to fluid ounces
                        fromUnit == "oz" && toUnit == "ml" -> value * 29.5735 // fluid ounces to milliliters
                        
                        // Speed conversions
                        fromUnit == "kph" && toUnit == "mph" -> value * 0.621371 // km/h to miles/h
                        fromUnit == "mph" && toUnit == "kph" -> value * 1.60934 // miles/h to km/h
                        
                        // Same unit (no conversion needed)
                        fromUnit == toUnit -> value
                        
                        else -> throw IllegalArgumentException("Unsupported conversion: $fromUnit to $toUnit")
                    }
                }
                
                throw IllegalArgumentException("Invalid unit conversion format. Use: convert(value, from_unit, to_unit)")
            } catch (e: Exception) {
                throw IllegalArgumentException("Error in unit conversion: ${e.message}")
            }
        }
        
        /**
         * Handle statistical functions on arrays of values
         * Supports operations like:
         * - stats.mean(1,2,3,4,5) - Calculate the mean
         * - stats.median(1,2,3,4,5) - Calculate the median
         * - stats.min(1,2,3,4,5) - Find the minimum value
         * - stats.max(1,2,3,4,5) - Find the maximum value
         * - stats.sum(1,2,3,4,5) - Sum the values
         * - stats.stdev(1,2,3,4,5) - Calculate standard deviation
         */
        private fun handleStatisticalFunction(expression: String): Double {
            try {
                // Extract function name and parameters
                val functionName = expression.substringAfter("stats.").substringBefore("(")
                val paramsString = expression.substringAfter("(").substringBefore(")")
                val params = paramsString.split(",").map { evalExpression(it.trim()) }
                
                if (params.isEmpty()) {
                    throw IllegalArgumentException("Statistical functions require at least one value")
                }
                
                return when (functionName.trim().lowercase()) {
                    "mean" -> params.average()
                    "median" -> {
                        val sorted = params.sorted()
                        if (sorted.size % 2 == 0) {
                            // Even number of elements - average the middle two
                            (sorted[sorted.size / 2] + sorted[sorted.size / 2 - 1]) / 2
                        } else {
                            // Odd number of elements - return the middle one
                            sorted[sorted.size / 2]
                        }
                    }
                    "min" -> params.minOrNull() ?: 0.0
                    "max" -> params.maxOrNull() ?: 0.0
                    "sum" -> params.sum()
                    "stdev" -> {
                        val mean = params.average()
                        val variance = params.map { (it - mean).pow(2) }.average()
                        sqrt(variance)
                    }
                    else -> throw IllegalArgumentException("Unknown statistical function: $functionName")
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Error in statistical calculation: ${e.message}")
            }
        }
        
        /**
         * Handle conditional expressions of the form if(condition)then(value1)else(value2)
         */
        private fun handleConditional(expression: String): Double {
            try {
                // Parse the condition
                val conditionStr = expression
                    .substringAfter("if(")
                    .substringBefore(")then")
                
                // Parse the then value
                val thenValue = expression
                    .substringAfter(")then(")
                    .substringBefore(")else")
                
                // Parse the else value
                val elseValue = expression
                    .substringAfter(")else(")
                    .substringBefore(")")
                
                // Evaluate the condition
                val conditionResult = evaluateCondition(conditionStr)
                
                // Return the appropriate value based on the condition
                return if (conditionResult) {
                    evalExpression(thenValue)
                } else {
                    evalExpression(elseValue)
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Error in conditional expression: ${e.message}")
            }
        }
        
        /**
         * Evaluate a logical condition like "x > 5"
         */
        private fun evaluateCondition(condition: String): Boolean {
            // Handle logical operations
            if (condition.contains("&&")) {
                val parts = condition.split("&&")
                return evaluateCondition(parts[0].trim()) && evaluateCondition(parts[1].trim())
            }
            
            if (condition.contains("||")) {
                val parts = condition.split("||")
                return evaluateCondition(parts[0].trim()) || evaluateCondition(parts[1].trim())
            }
            
            // Handle comparisons
            when {
                condition.contains("==") -> {
                    val parts = condition.split("==")
                    return evalExpression(parts[0].trim()) == evalExpression(parts[1].trim())
                }
                condition.contains("!=") -> {
                    val parts = condition.split("!=")
                    return evalExpression(parts[0].trim()) != evalExpression(parts[1].trim())
                }
                condition.contains(">=") -> {
                    val parts = condition.split(">=")
                    return evalExpression(parts[0].trim()) >= evalExpression(parts[1].trim())
                }
                condition.contains("<=") -> {
                    val parts = condition.split("<=")
                    return evalExpression(parts[0].trim()) <= evalExpression(parts[1].trim())
                }
                condition.contains(">") -> {
                    val parts = condition.split(">")
                    return evalExpression(parts[0].trim()) > evalExpression(parts[1].trim())
                }
                condition.contains("<") -> {
                    val parts = condition.split("<")
                    return evalExpression(parts[0].trim()) < evalExpression(parts[1].trim())
                }
                else -> {
                    // If no comparison is found, evaluate the expression and check if it's non-zero
                    return evalExpression(condition) != 0.0
                }
            }
        }
        
        /**
         * Calculate factorial of a number
         */
        private fun factorial(n: Int): Long {
            if (n < 0) throw IllegalArgumentException("Factorial is not defined for negative numbers")
            if (n > 20) throw IllegalArgumentException("Factorial too large to calculate")
            
            var result = 1L
            for (i in 2..n) {
                result *= i
            }
            return result
        }
        
        /**
         * Attempts to parse a date string using multiple common formats
         */
        private fun parseDate(dateString: String): Date? {
            for (format in DATE_FORMATS) {
                try {
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    formatter.isLenient = false
                    return formatter.parse(dateString)
                } catch (e: Exception) {
                    // Try next format
                }
            }
            return null
        }
        
        /**
         * Format a date object using the specified format
         */
        fun formatDate(date: Date, format: String): String {
            val formatter = SimpleDateFormat(format, Locale.getDefault())
            return formatter.format(date)
        }
        
        /**
         * Handle variable assignment
         */
        private fun handleVariableAssignment(expression: String): Double {
            val parts = expression.split("=", limit = 2)
            val variableName = parts[0].trim()
            val variableValue = evalExpression(parts[1].trim())
            
            variables[variableName] = variableValue
            return variableValue
        }
        
        /**
         * Clear all stored variables
         */
        fun clearVariables() {
            variables.clear()
        }
        
        /**
         * Get a stored variable value
         */
        fun getVariable(name: String): Double? {
            return variables[name]
        }
        
        /**
         * Set a variable value
         */
        fun setVariable(name: String, value: Double) {
            variables[name] = value
        }
        
        /**
         * Format the result for display
         */
        fun formatResult(result: Double): String {
            // If it's a whole number, display without decimal
            if (result == Math.floor(result)) {
                return result.toInt().toString()
            }
            // Otherwise use decimal formatting
            return "%.6f".format(result).trimEnd('0').trimEnd('.')
        }
        
        /**
         * Get a list of supported units for conversion
         */
        fun getSupportedUnits(): Map<String, List<String>> {
            return mapOf(
                "Temperature" to listOf("c (Celsius)", "f (Fahrenheit)", "k (Kelvin)"),
                "Length" to listOf("km (kilometers)", "mi (miles)", "m (meters)", "ft (feet)", "cm (centimeters)", "in (inches)"),
                "Weight" to listOf("kg (kilograms)", "lb (pounds)", "g (grams)", "oz (ounces)"),
                "Volume" to listOf("l (liters)", "gal (gallons)", "ml (milliliters)", "oz (fluid ounces)"),
                "Speed" to listOf("kph (kilometers per hour)", "mph (miles per hour)")
            )
        }
        
        /**
         * Get a list of supported date functions
         */
        fun getSupportedDateFunctions(): List<String> {
            return listOf(
                "today() - Current date",
                "now() - Current timestamp in milliseconds",
                "date(2023-01-01) - Parse date string",
                "date_diff(date1, date2) - Days between dates",
                "date_add(date, days) - Add days to date",
                "weekday(date) - Get day of week (1-7)",
                "month(date) - Get month (1-12)",
                "year(date) - Get year",
                "day(date) - Get day of month"
            )
        }
        
        /**
         * Get a list of supported statistical functions
         */
        fun getSupportedStatFunctions(): List<String> {
            return listOf(
                "stats.mean(values...) - Calculate average",
                "stats.median(values...) - Find middle value",
                "stats.min(values...) - Find minimum value",
                "stats.max(values...) - Find maximum value",
                "stats.sum(values...) - Sum values",
                "stats.stdev(values...) - Calculate standard deviation"
            )
        }
    }
} 