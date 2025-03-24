package com.ai.assistance.operit.tools.javascript

import android.content.Context
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.tools.packTool.PackageManager
import kotlinx.coroutines.runBlocking

/**
 * JavaScript 工具示例
 * 提供各种 JavaScript 脚本示例，用于演示 JS 工具调用功能
 */
object JsToolExamples {

    /**
     * 简单计算器示例
     */
    val calculatorExample = """
        // 基本计算
        const result1 = 2 + 2 * 3;
        console.log("计算结果:", result1);  // 输出: 8
        
        // 使用参数
        const x = 10;
        const y = 20;
        const result2 = x + y * 2;
        console.log("x + y * 2 =", result2);  // 输出: 50
        
        // 使用数学函数
        const trigResult = Math.sin(Math.PI/4) + Math.cos(Math.PI/3);
        console.log("sin(PI/4) + cos(PI/3) =", trigResult.toFixed(4));  // 约1.2071
        
        // 复杂计算
        const a = 5;
        const b = 7;
        const hypotenuse = Math.sqrt(a*a + b*b);
        console.log("斜边长度:", hypotenuse.toFixed(4));  // 8.6023
        
        // 混合运算
        const complexResult = (x + y) * 2 / (a + b);
        console.log("混合运算结果:", complexResult);  // 5
        
        // 返回最终结果
        complete({
            basic: result1,
            withVariables: result2,
            trigonometry: trigResult,
            hypotenuse: hypotenuse,
            complex: complexResult
        });
    """.trimIndent()
    
    /**
     * 变量和数据类型示例
     */
    val variablesExample = """
        // 数字类型
        const num = 42;
        const pi = 3.14159;
        
        // 字符串类型
        const name = "张三";
        const greeting = "你好，" + name + "！";
        
        // 布尔类型
        const active = true;
        const isComplete = false;
        
        // 数组
        const fruits = ["苹果", "香蕉", "橙子"];
        fruits.push("葡萄");
        
        // 对象
        const person = {
            name: "李四",
            age: 30,
            isStudent: false,
            hobbies: ["阅读", "游泳"]
        };
        
        // 对象和数组操作
        person.location = "北京";
        person.hobbies.push("骑行");
        
        // 使用 Lodash 辅助函数
        const isEmpty = _.isEmpty([]);  // true
        const isString = _.isString(name);  // true
        
        // 返回所有变量信息
        complete({
            numbers: { num, pi },
            strings: { name, greeting },
            booleans: { active, complete: isComplete },
            arrays: fruits,
            objects: person,
            lodash: { isEmpty, isString }
        });
    """.trimIndent()
    
    /**
     * 工具调用示例
     */
    val toolCallExample = """
        // 使用 toolCall 函数调用工具
        console.log("开始测试工具调用功能");
        
        // 1. 标准调用方式 - 完整参数
        const calcResult = toolCall("default", "calculate", {
            expression: "sqrt(16) + pow(2, 3)"
        });
        console.log("计算结果:", calcResult);
        
        // 2. 简化调用方式 - 直接传工具名和参数对象
        const fileInfo = toolCall("file_info", {
            path: "/sdcard/Download"
        });
        console.log("文件信息:", fileInfo);
        
        // 3. 对象配置调用方式
        const searchResult = toolCall({
            type: "default",
            name: "web_search",
            params: { query: "JavaScript 工具调用" }
        });
        console.log("搜索结果:", searchResult);
        
        // 4. 使用便捷方法 - 文件操作
        const filesList = Tools.Files.list("/sdcard/Download");
        console.log("文件列表:", filesList);
        
        // 5. 使用便捷方法 - 计算
        const mathResult = Tools.calc("2 * 3 + 4");
        console.log("计算结果:", mathResult);
        
        // 6. 使用便捷方法 - 系统操作
        Tools.System.sleep("1");
        console.log("暂停1秒后继续执行");
        
        // 处理返回结果
        const processedResult = "处理结果: " + calcResult + 
                                "，字符长度: " + String(calcResult).length;
        
        // 条件工具调用
        const fileExists = Tools.Files.exists("/sdcard/Download");
        
        if (fileExists === "true" || fileExists === true) {
            console.log("文件或目录存在");
        } else {
            console.log("文件或目录不存在");
        }
        
        // 调用设备信息工具
        const deviceInfo = toolCall("device_info");
        
        // 返回所有结果
        complete({
            standardCall: calcResult,
            simplifiedCall: fileInfo,
            objectCall: searchResult,
            toolsHelper: filesList,
            mathHelper: mathResult,
            fileExists: fileExists,
            deviceInfo: deviceInfo
        });
    """.trimIndent()
    
    /**
     * 错误处理示例
     */
    val errorHandlingExample = """
        // JavaScript 错误处理示例
        
        try {
            console.log("开始错误处理测试");
            
            // 尝试除以零
            const result = 10 / 0;
            console.log("10 / 0 =", result);  // 在 JS 中返回 Infinity，不会抛出异常
            
            // 尝试访问不存在的属性
            const obj = { name: "测试对象" };
            try {
                console.log("访问不存在的属性:", obj.nonExistentProperty);  // undefined
                console.log("访问更深层次的不存在属性");
                
                // 这会导致错误
                const value = obj.nonExistent.deepProperty;
            } catch (e) {
                console.log("捕获到内部错误:", e.message);
            }
            
            // 尝试工具调用错误
            try {
                const result = toolCall("non_existent", "invalid_tool", {});
                console.log("无效工具调用结果:", result);
            } catch (e) {
                console.log("工具调用错误:", e.message);
            }
            
            console.log("错误处理测试完成");
            
            complete({
                status: "成功",
                divisionByZero: 10 / 0,
                undefinedProperty: obj.nonExistentProperty,
                errorsCaught: true
            });
            
        } catch (e) {
            console.log("捕获到顶层错误:", e.message);
            complete({
                status: "失败",
                error: e.message
            });
        }
    """.trimIndent()
    
    /**
     * 数据处理示例
     */
    val dataProcessingExample = """
        // 使用 JavaScript 进行数据处理
        
        // 示例JSON数据
        const data = {
            users: [
                { id: 1, name: "张三", age: 28, active: true },
                { id: 2, name: "李四", age: 32, active: false },
                { id: 3, name: "王五", age: 45, active: true },
                { id: 4, name: "赵六", age: 19, active: true },
                { id: 5, name: "钱七", age: 26, active: false }
            ],
            settings: {
                showInactive: true,
                sortBy: "age",
                maxItems: 10
            }
        };
        
        // 数据筛选 - 只保留活跃用户
        const activeUsers = data.users.filter(user => user.active);
        console.log("活跃用户:", activeUsers.length);
        
        // 数据转换 - 提取用户名
        const userNames = data.users.map(user => user.name);
        console.log("用户名列表:", userNames.join(", "));
        
        // 数据排序 - 按年龄排序
        const sortedUsers = [...data.users].sort((a, b) => a.age - b.age);
        console.log("最年轻的用户:", sortedUsers[0].name);
        
        // 数据聚合 - 计算平均年龄
        const totalAge = data.users.reduce((sum, user) => sum + user.age, 0);
        const averageAge = totalAge / data.users.length;
        console.log("平均年龄:", averageAge.toFixed(1));
        
        // 数据分组 - 按活跃状态分组
        const userGroups = data.users.reduce((groups, user) => {
            const key = user.active ? "active" : "inactive";
            if (!groups[key]) groups[key] = [];
            groups[key].push(user);
            return groups;
        }, {});
        
        // 数据查找 - 查找特定用户
        const findUser = id => data.users.find(user => user.id === id);
        const user3 = findUser(3);
        console.log("ID为3的用户:", user3?.name);
        
        // 返回处理结果
        complete({
            originalCount: data.users.length,
            activeCount: activeUsers.length,
            names: userNames,
            youngest: sortedUsers[0],
            averageAge: averageAge,
            groupedUsers: userGroups,
            foundUser: user3
        });
    """.trimIndent()
    
    /**
     * 执行 JavaScript 工具示例
     */
    fun executeExampleScript(scriptContent: String, context: Context, packageManager: PackageManager, params: Map<String, String> = emptyMap()): String {
        // 使用 JsToolManager 运行脚本
        val jsToolManager = JsToolManager.getInstance(context, packageManager)
        
        // 创建工具参数
        val tool = AITool(
            name = "JsToolExample",
            parameters = params.map { entry ->
                ToolParameter(
                    name = entry.key,
                    value = entry.value
                )
            }
        )
        
        // 执行脚本
        val result = jsToolManager.executeScript(scriptContent, tool)
        
        // 处理结果
        return if (result.success) {
            "SUCCESS:\n${result.result}"
        } else {
            "ERROR:\n${result.error ?: "Unknown error"}"
        }
    }
    
    /**
     * 运行示例
     */
    fun runExample(exampleName: String, context: Context, packageManager: PackageManager): String {
        val scriptContent = when (exampleName.lowercase()) {
            "calculator" -> calculatorExample
            "variables" -> variablesExample
            "toolcall" -> toolCallExample
            "error" -> errorHandlingExample
            "data" -> dataProcessingExample
            else -> {
                return "ERROR: Unknown example '$exampleName'"
            }
        }
        
        return executeExampleScript(scriptContent, context, packageManager)
    }
} 