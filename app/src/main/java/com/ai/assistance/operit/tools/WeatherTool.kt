package com.ai.assistance.operit.tools

import android.content.Context
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolValidationResult
import java.util.*
import kotlin.random.Random

/**
 * Tool for retrieving weather information
 * 使用本地模拟数据，无需API密钥和网络请求
 */
class WeatherTool(private val context: Context) : ToolExecutor {
    
    companion object {
        private const val TAG = "WeatherTool"
    }
    
    override fun invoke(tool: AITool): ToolResult {
        // 从工具参数中获取位置信息
        val location = tool.parameters.find { it.name == "location" }?.value
        
        if (location.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Location parameter is required"
            )
        }
        
        // 尝试获取天气数据
        return try {
            // 使用扩展的本地模拟数据
            val weatherData = getLocalWeatherData(location)
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = formatWeatherResponse(location, weatherData)
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error fetching weather data: ${e.message}"
            )
        }
    }
    
    override fun validateParameters(tool: AITool): ToolValidationResult {
        val location = tool.parameters.find { it.name == "location" }?.value
        
        return if (location.isNullOrBlank()) {
            ToolValidationResult(
                valid = false,
                errorMessage = "Location parameter is required"
            )
        } else {
            ToolValidationResult(valid = true)
        }
    }
    
    /**
     * 获取本地天气数据
     * 包含大量预定义城市和动态生成的数据
     */
    private fun getLocalWeatherData(location: String): Map<String, Any> {
        // 预定义的城市天气数据
        val cities = mapOf(
            // 中国城市
            "beijing" to mapOf(
                "temp" to 25,
                "humidity" to 65,
                "condition" to "晴天",
                "wind" to 5.3,
                "aqi" to 48,
                "forecast" to listOf("晴天", "多云", "多云", "晴天", "晴天")
            ),
            "shanghai" to mapOf(
                "temp" to 28,
                "humidity" to 70,
                "condition" to "多云",
                "wind" to 4.2,
                "aqi" to 62,
                "forecast" to listOf("多云", "小雨", "多云", "晴天", "晴天")
            ),
            "guangzhou" to mapOf(
                "temp" to 30,
                "humidity" to 75,
                "condition" to "多云",
                "wind" to 3.7,
                "aqi" to 55,
                "forecast" to listOf("多云", "雷阵雨", "雷阵雨", "多云", "多云")
            ),
            "shenzhen" to mapOf(
                "temp" to 31,
                "humidity" to 78,
                "condition" to "多云",
                "wind" to 3.5,
                "aqi" to 50,
                "forecast" to listOf("多云", "雷阵雨", "小雨", "多云", "晴天")
            ),
            "chengdu" to mapOf(
                "temp" to 24,
                "humidity" to 82,
                "condition" to "小雨",
                "wind" to 2.8,
                "aqi" to 58,
                "forecast" to listOf("小雨", "小雨", "多云", "多云", "小雨")
            ),
            "hangzhou" to mapOf(
                "temp" to 27,
                "humidity" to 68,
                "condition" to "晴天",
                "wind" to 3.9,
                "aqi" to 52,
                "forecast" to listOf("晴天", "多云", "小雨", "多云", "晴天")
            ),
            "nanjing" to mapOf(
                "temp" to 26,
                "humidity" to 66,
                "condition" to "晴天",
                "wind" to 4.1,
                "aqi" to 59,
                "forecast" to listOf("晴天", "晴天", "多云", "小雨", "多云")
            ),
            "wuhan" to mapOf(
                "temp" to 29,
                "humidity" to 72,
                "condition" to "多云",
                "wind" to 3.6,
                "aqi" to 64,
                "forecast" to listOf("多云", "小雨", "小雨", "多云", "晴天")
            ),
            "tianjin" to mapOf(
                "temp" to 24,
                "humidity" to 63,
                "condition" to "晴天",
                "wind" to 5.5,
                "aqi" to 56,
                "forecast" to listOf("晴天", "晴天", "多云", "多云", "晴天")
            ),
            "chongqing" to mapOf(
                "temp" to 28,
                "humidity" to 76,
                "condition" to "多云",
                "wind" to 3.2,
                "aqi" to 61,
                "forecast" to listOf("多云", "小雨", "小雨", "多云", "多云")
            ),
            "xian" to mapOf(
                "temp" to 23,
                "humidity" to 60,
                "condition" to "晴天",
                "wind" to 4.3,
                "aqi" to 53,
                "forecast" to listOf("晴天", "晴天", "多云", "多云", "晴天")
            ),
            
            // 国际城市
            "new york" to mapOf(
                "temp" to 18,
                "humidity" to 50,
                "condition" to "Partly Cloudy",
                "wind" to 7.4,
                "aqi" to 32,
                "forecast" to listOf("Partly Cloudy", "Sunny", "Sunny", "Cloudy", "Rainy")
            ),
            "london" to mapOf(
                "temp" to 15,
                "humidity" to 80,
                "condition" to "Rainy",
                "wind" to 6.1,
                "aqi" to 38,
                "forecast" to listOf("Rainy", "Cloudy", "Partly Cloudy", "Cloudy", "Rainy")
            ),
            "tokyo" to mapOf(
                "temp" to 22,
                "humidity" to 60,
                "condition" to "Clear",
                "wind" to 3.5,
                "aqi" to 41,
                "forecast" to listOf("Clear", "Sunny", "Cloudy", "Rainy", "Cloudy")
            ),
            "paris" to mapOf(
                "temp" to 17,
                "humidity" to 65,
                "condition" to "Cloudy",
                "wind" to 5.2,
                "aqi" to 36,
                "forecast" to listOf("Cloudy", "Partly Cloudy", "Sunny", "Sunny", "Partly Cloudy")
            ),
            "berlin" to mapOf(
                "temp" to 16,
                "humidity" to 70,
                "condition" to "Partly Cloudy",
                "wind" to 5.8,
                "aqi" to 39,
                "forecast" to listOf("Partly Cloudy", "Cloudy", "Rainy", "Cloudy", "Partly Cloudy")
            ),
            "sydney" to mapOf(
                "temp" to 25,
                "humidity" to 55,
                "condition" to "Sunny",
                "wind" to 4.9,
                "aqi" to 28,
                "forecast" to listOf("Sunny", "Sunny", "Partly Cloudy", "Sunny", "Sunny")
            ),
            "singapore" to mapOf(
                "temp" to 32,
                "humidity" to 85,
                "condition" to "Thunderstorm",
                "wind" to 3.1,
                "aqi" to 45,
                "forecast" to listOf("Thunderstorm", "Rainy", "Cloudy", "Thunderstorm", "Cloudy")
            ),
            "dubai" to mapOf(
                "temp" to 36,
                "humidity" to 40,
                "condition" to "Sunny",
                "wind" to 4.8,
                "aqi" to 62,
                "forecast" to listOf("Sunny", "Sunny", "Sunny", "Sunny", "Sunny")
            ),
            "rome" to mapOf(
                "temp" to 23,
                "humidity" to 63,
                "condition" to "Sunny",
                "wind" to 3.9,
                "aqi" to 43,
                "forecast" to listOf("Sunny", "Sunny", "Partly Cloudy", "Sunny", "Sunny")
            ),
            "moscow" to mapOf(
                "temp" to 5,
                "humidity" to 75,
                "condition" to "Cloudy",
                "wind" to 6.7,
                "aqi" to 49,
                "forecast" to listOf("Cloudy", "Snowy", "Cloudy", "Partly Cloudy", "Snowy")
            )
        )
        
        // 尝试匹配城市，如果找不到则生成动态天气数据
        val normalizedLocation = location.toLowerCase(Locale.getDefault()).trim()
        
        // 检查是否有预定义数据
        if (cities.containsKey(normalizedLocation)) {
            return cities[normalizedLocation]!!
        }

        // 如果没找到，基于城市名生成一个固定的随机种子，确保同一城市每次得到相同结果
        val seed = normalizedLocation.hashCode()
        val random = Random(seed)
        
        // 根据季节调整温度范围（使用当前月份简单模拟季节）
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-12
        
        // 根据月份估算季节和对应温度范围
        val (minTemp, maxTemp) = when {
            normalizedLocation.contains("north") || normalizedLocation.endsWith("北") -> {
                // 北半球: 冬季(12-2), 春季(3-5), 夏季(6-8), 秋季(9-11)
                when {
                    currentMonth in 12..12 || currentMonth in 1..2 -> -5 to 10 // 冬季
                    currentMonth in 3..5 -> 10 to 25 // 春季
                    currentMonth in 6..8 -> 20 to 35 // 夏季
                    else -> 10 to 25 // 秋季
                }
            }
            normalizedLocation.contains("south") || normalizedLocation.endsWith("南") -> {
                // 南半球: 夏季(12-2), 秋季(3-5), 冬季(6-8), 春季(9-11)
                when {
                    currentMonth in 12..12 || currentMonth in 1..2 -> 20 to 35 // 夏季
                    currentMonth in 3..5 -> 10 to 25 // 秋季
                    currentMonth in 6..8 -> -5 to 10 // 冬季
                    else -> 10 to 25 // 春季
                }
            }
            else -> {
                // 默认温度范围
                when {
                    currentMonth in 12..12 || currentMonth in 1..2 -> 0 to 15
                    currentMonth in 3..5 -> 10 to 25
                    currentMonth in 6..8 -> 15 to 30
                    else -> 10 to 20
                }
            }
        }
        
        // 根据位置名称特性调整湿度和天气状况
        val isDesert = normalizedLocation.contains("desert") || 
                       normalizedLocation.contains("沙漠")
        val isCoastal = normalizedLocation.contains("sea") || 
                        normalizedLocation.contains("coast") || 
                        normalizedLocation.contains("海") || 
                        normalizedLocation.contains("岸") ||
                        normalizedLocation.contains("岛")
        val isMountain = normalizedLocation.contains("mountain") || 
                         normalizedLocation.contains("mount") || 
                         normalizedLocation.contains("山")
        
        // 调整湿度范围
        val humidityRange = when {
            isDesert -> 10..30
            isCoastal -> 60..90
            isMountain -> 40..70
            else -> 30..70
        }
        
        // 根据地理特征和月份选择天气状况
        val conditions = when {
            isDesert -> listOf("晴天", "晴天", "晴天", "多云", "沙尘暴")
            isCoastal && (currentMonth in 6..9) -> listOf("多云", "小雨", "阵雨", "雷阵雨", "晴天")
            isCoastal -> listOf("多云", "小雨", "雾", "晴天", "阵雨")
            isMountain && (currentMonth in 11..12 || currentMonth in 1..3) -> 
                listOf("雪", "小雪", "多云", "晴天", "大雪")
            isMountain -> listOf("多云", "雾", "小雨", "晴天", "阵雨")
            currentMonth in 11..12 || currentMonth in 1..3 -> 
                listOf("多云", "阴天", "小雪", "晴天", "雾")
            currentMonth in 6..8 -> 
                listOf("晴天", "多云", "雷阵雨", "阵雨", "大雨")
            else -> listOf("晴天", "多云", "小雨", "阴天", "阵雨")
        }
        
        // 生成五天预报
        val forecast = List(5) { 
            conditions.random(random)
        }
        
        // 生成天气数据
        return mapOf(
            "temp" to random.nextInt(minTemp, maxTemp + 1),
            "humidity" to random.nextInt(humidityRange.first, humidityRange.last + 1),
            "condition" to conditions.random(random),
            "wind" to (random.nextInt(10) + random.nextDouble()),
            "aqi" to random.nextInt(20, 100),
            "forecast" to forecast
        )
    }
    
    /**
     * 格式化天气响应信息
     */
    private fun formatWeatherResponse(location: String, weatherData: Map<String, Any>): String {
        val forecast = weatherData["forecast"] as? List<*> ?: emptyList<String>()
        val forecastStr = if (forecast.isNotEmpty()) {
            "\n未来五天预报: ${forecast.joinToString(", ")}"
        } else {
            ""
        }
        
        return """
            ${location}天气:
            温度: ${weatherData["temp"]}°C
            天气状况: ${weatherData["condition"]}
            湿度: ${weatherData["humidity"]}%
            风速: ${weatherData["wind"]} m/s
            空气质量指数: ${weatherData["aqi"]}$forecastStr
        """.trimIndent()
    }
} 