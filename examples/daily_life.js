/*
METADATA
{
    "name": "daily_life",
    "description": "Tools for daily life activities like getting date, device info, and UI interactions",
    "tools": [
        {
            "name": "get_current_date",
            "description": "Get the current date and time in various formats",
            "parameters": [
                {
                    "name": "format",
                    "description": "Date format ('short', 'medium', 'long', or custom pattern)",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "device_status",
            "description": "Get device status information including battery and memory",
            "parameters": []
        },
        {
            "name": "set_reminder",
            "description": "Create a reminder or to-do item",
            "parameters": [
                {
                    "name": "title",
                    "description": "Title of the reminder or to-do",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "description",
                    "description": "Additional details for the reminder",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "due_date",
                    "description": "Due date for the reminder (ISO string format)",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "set_alarm",
            "description": "Set an alarm on the device",
            "parameters": [
                {
                    "name": "hour",
                    "description": "Hour for the alarm (0-23)",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "minute",
                    "description": "Minute for the alarm (0-59)",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "Label for the alarm",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "days",
                    "description": "Days to repeat the alarm (array of numbers, 1=Sunday, 7=Saturday)",
                    "type": "array",
                    "required": false
                }
            ]
        },
        {
            "name": "send_message",
            "description": "Send a text message",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "Recipient phone number",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "Message content",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "make_phone_call",
            "description": "Make a phone call",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "Phone number to call",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "emergency",
                    "description": "Whether this is an emergency call",
                    "type": "boolean",
                    "required": false
                }
            ]
        }
    ],
    "category": "SYSTEM"
}
*/
const dailyLife = (function () {
    /**
     * Get the current date and time in various formats
     * @param params - Optional parameters including format
     */
    async function get_current_date(params) {
        try {
            const format = params.format || 'medium';
            const now = new Date();
            let formattedDate;
            switch (format) {
                case 'short':
                    formattedDate = now.toLocaleDateString();
                    break;
                case 'long':
                    formattedDate = now.toLocaleDateString(undefined, {
                        weekday: 'long',
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit'
                    });
                    break;
                case 'medium':
                default:
                    formattedDate = now.toLocaleDateString(undefined, {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit'
                    });
                    break;
            }
            return {
                timestamp: now.getTime(),
                iso: now.toISOString(),
                formatted: formattedDate,
                date: {
                    year: now.getFullYear(),
                    month: now.getMonth() + 1,
                    day: now.getDate(),
                    weekday: now.toLocaleDateString(undefined, { weekday: 'long' })
                },
                time: {
                    hours: now.getHours(),
                    minutes: now.getMinutes(),
                    seconds: now.getSeconds()
                }
            };
        }
        catch (error) {
            console.error(`[get_current_date] 错误: ${error.message}`);
            console.error(error.stack);
            throw error;
        }
    }
    /**
     * Get device status information
     */
    async function device_status() {
        try {
            // Get device information using the System tool
            const deviceInfo = await Tools.System.getDeviceInfo();
            return {
                battery: {
                    level: deviceInfo.batteryLevel,
                    charging: deviceInfo.batteryCharging
                },
                memory: {
                    total: deviceInfo.totalMemory,
                    available: deviceInfo.availableMemory
                },
                storage: {
                    total: deviceInfo.totalStorage,
                    available: deviceInfo.availableStorage
                },
                device: {
                    model: deviceInfo.model,
                    manufacturer: deviceInfo.manufacturer,
                    androidVersion: deviceInfo.androidVersion
                },
                network: deviceInfo.networkType
            };
        }
        catch (error) {
            throw new Error(`Failed to get device status: ${error.message}`);
        }
    }
    /**
     * Search the web for information
     * @param params - Parameters with search query
     */
    async function search_web(params) {
        try {
            if (!params.query) {
                throw new Error("Search query is required");
            }
            const result = await Tools.Net.search(params.query);
            return {
                success: true,
                query: params.query,
                results: result.results.map(item => ({
                    title: item.title,
                    url: item.url,
                    snippet: item.snippet
                }))
            };
        }
        catch (error) {
            throw new Error(`Failed to search web: ${error.message}`);
        }
    }
    /**
     * Set a reminder or to-do item
     * @param params - Parameters with reminder details
     */
    async function set_reminder(params) {
        try {
            if (!params.title) {
                throw new Error("Reminder title is required");
            }
            console.log("创建提醒...");
            console.log("尝试使用隐式Intent创建日历事件...");
            // 创建Intent
            const intent = new Intent("android.intent.action.INSERT" /* IntentAction.ACTION_INSERT */);
            // 设置日历事件的URI
            intent.setData("content://com.android.calendar/events");
            // 设置事件详情
            intent.putExtra("title", params.title);
            if (params.description) {
                intent.putExtra("description", params.description);
            }
            // 处理日期
            if (params.due_date) {
                const dueDate = new Date(params.due_date);
                // 使用毫秒级时间戳
                const beginTime = dueDate.getTime();
                const endTime = beginTime + 3600000; // 默认1小时后
                intent.putExtra("beginTime", beginTime);
                intent.putExtra("endTime", endTime);
                // 添加单独的日期和时间组件，增加兼容性
                intent.putExtra("eventTimezone", "UTC");
                intent.putExtra("allDay", false);
            }
            else {
                // 默认使用当前时间后1小时
                const now = new Date();
                const beginTime = now.getTime();
                const endTime = beginTime + 3600000;
                intent.putExtra("beginTime", beginTime);
                intent.putExtra("endTime", endTime);
                // 添加单独的日期和时间组件
                intent.putExtra("eventTimezone", "UTC");
                intent.putExtra("allDay", false);
            }
            // 设置提醒
            intent.putExtra("hasAlarm", 1);
            // 添加标志
            intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
            // 启动Intent
            const result = await intent.start();
            // 返回结果
            return {
                success: true,
                message: "提醒创建成功",
                title: params.title,
                description: params.description || null,
                due_date: params.due_date || null,
                method: "implicit_intent",
                raw_result: result
            };
        }
        catch (error) {
            console.error(`[set_reminder] 错误: ${error.message}`);
            console.error(error.stack);
            return {
                success: false,
                message: `创建提醒失败: ${error.message}`,
                title: params.title,
                description: params.description || null,
                due_date: params.due_date || null,
                error: error.message
            };
        }
    }
    /**
     * Set an alarm on the device
     * @param params - Parameters with alarm details
     */
    async function set_alarm(params) {
        try {
            if (params.hour === undefined || params.minute === undefined) {
                throw new Error("Hour and minute are required for setting an alarm");
            }
            if (params.hour < 0 || params.hour > 23) {
                throw new Error("Hour must be between 0 and 23");
            }
            if (params.minute < 0 || params.minute > 59) {
                throw new Error("Minute must be between 0 and 59");
            }
            console.log("设置闹钟...");
            console.log("尝试使用隐式Intent设置闹钟...");
            // 创建Intent
            const intent = new Intent("android.intent.action.SET_ALARM");
            // 设置闹钟详情
            intent.putExtra("android.intent.extra.alarm.HOUR", params.hour);
            intent.putExtra("android.intent.extra.alarm.MINUTES", params.minute);
            // 添加标签（如果提供）
            if (params.message) {
                intent.putExtra("android.intent.extra.alarm.MESSAGE", params.message);
            }
            // 设置重复日期（如果提供）
            if (params.days && params.days.length > 0) {
                intent.putExtra("android.intent.extra.alarm.DAYS", params.days);
            }
            // 跳过UI确认
            intent.putExtra("android.intent.extra.alarm.SKIP_UI", true);
            // 添加标志
            intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
            // 启动Intent
            const result = await intent.start();
            // 返回结果
            return {
                success: true,
                message: "闹钟设置成功",
                alarm_time: `${params.hour.toString().padStart(2, '0')}:${params.minute.toString().padStart(2, '0')}`,
                label: params.message || null,
                repeat_days: params.days || null,
                method: "implicit_intent",
                raw_result: result
            };
        }
        catch (error) {
            console.error(`[set_alarm] 错误: ${error.message}`);
            console.error(error.stack);
            return {
                success: false,
                message: `设置闹钟失败: ${error.message}`,
                alarm_time: `${params.hour.toString().padStart(2, '0')}:${params.minute.toString().padStart(2, '0')}`,
                label: params.message || null,
                repeat_days: params.days || null,
                error: error.message
            };
        }
    }
    /**
     * Send a text message
     * @param params - Parameters with message details
     */
    async function send_message(params) {
        try {
            if (!params.phone_number) {
                throw new Error("Phone number is required");
            }
            if (!params.message) {
                throw new Error("Message content is required");
            }
            // Create an intent to send a message
            const intent = new Intent("android.intent.action.SENDTO" /* IntentAction.ACTION_SENDTO */);
            // Format the phone number URI
            const phoneUri = `smsto:${params.phone_number}`;
            intent.putExtra("address", phoneUri);
            // Add message content
            intent.putExtra("sms_body", params.message);
            // Add necessary flags
            intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
            // Start the intent
            const result = await intent.start();
            return {
                success: true,
                message: "Message sending intent launched",
                recipient: params.phone_number,
                content_preview: params.message.length > 30 ? params.message.substring(0, 30) + "..." : params.message,
                raw_result: result
            };
        }
        catch (error) {
            throw new Error(`Failed to send message: ${error.message}`);
        }
    }
    /**
     * Make a phone call
     * @param params - Parameters with call details
     */
    async function make_phone_call(params) {
        try {
            if (!params.phone_number) {
                throw new Error("Phone number is required");
            }
            // Select the appropriate intent action based on whether it's an emergency
            const action = params.emergency ? "android.intent.action.CALL_EMERGENCY" /* IntentAction.ACTION_CALL_EMERGENCY */ : "android.intent.action.DIAL" /* IntentAction.ACTION_DIAL */;
            // Create an intent to make a call
            const intent = new Intent(action);
            // Format the phone URI
            const phoneUri = `tel:${params.phone_number}`;
            intent.putExtra("android.intent.extra.PHONE_NUMBER", params.phone_number);
            // Add necessary flags
            intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
            // Start the intent
            const result = await intent.start();
            return {
                success: true,
                message: `Phone call intent launched (${params.emergency ? 'emergency' : 'normal'})`,
                phone_number: params.phone_number,
                is_emergency: params.emergency || false,
                raw_result: result
            };
        }
        catch (error) {
            throw new Error(`Failed to make phone call: ${error.message}`);
        }
    }
    /**
     * Test and demonstrate all daily life functions
     * This function shows examples of all available daily life functions
     */
    async function main() {
        try {
            // 创建测试结果对象 - 添加接口定义以解决类型错误
            const results = {};
            // 1. 测试当前日期时间函数
            console.log("测试获取当前日期时间...");
            try {
                const dateResult = await get_current_date({});
                results.date = dateResult;
                console.log("✓ 日期时间获取成功");
            }
            catch (error) {
                results.date = { error: `获取日期时间失败: ${error.message}` };
                console.log("✗ 日期时间获取失败");
            }
            // 2. 测试设备状态函数
            console.log("测试获取设备状态...");
            try {
                const deviceResult = await device_status();
                results.device = deviceResult;
                console.log("✓ 设备状态获取成功");
            }
            catch (error) {
                results.device = { error: `获取设备状态失败: ${error.message}` };
                console.log("✗ 设备状态获取失败");
            }
            // 3. 测试网络搜索功能
            console.log("测试网络搜索...");
            try {
                const searchResult = await search_web({ query: "今日天气" });
                results.search = searchResult;
                console.log("✓ 网络搜索成功");
            }
            catch (error) {
                results.search = { error: `网络搜索失败: ${error.message}` };
                console.log("✗ 网络搜索失败");
            }
            // 4. 测试设置提醒功能
            console.log("测试设置提醒...");
            try {
                const reminderResult = await set_reminder({
                    title: "测试提醒",
                    description: "这是一个测试提醒"
                });
                results.reminder = reminderResult;
                console.log("✓ 设置提醒成功");
            }
            catch (error) {
                results.reminder = { error: `设置提醒失败: ${error.message}` };
                console.log("✗ 设置提醒失败");
            }
            // 5. 测试设置闹钟功能
            console.log("测试设置闹钟...");
            try {
                // 获取当前时间，设置为5分钟后的闹钟
                const now = new Date();
                const hour = now.getHours();
                const minute = (now.getMinutes() + 5) % 60;
                const alarmResult = await set_alarm({
                    hour: hour,
                    minute: minute,
                    message: "测试闹钟"
                });
                results.alarm = alarmResult;
                console.log("✓ 设置闹钟成功");
            }
            catch (error) {
                results.alarm = { error: `设置闹钟失败: ${error.message}` };
                console.log("✗ 设置闹钟失败");
            }
            // 6. 测试发送消息功能 (不实际发送，避免干扰)
            console.log("模拟测试发送消息...");
            results.message = {
                success: true,
                message: "消息发送功能已模拟测试",
                note: "为避免实际发送消息，此功能仅作演示。实际使用时请使用send_message函数。"
            };
            console.log("✓ 发送消息测试完成");
            // 7. 测试拨打电话功能 (不实际拨打，避免干扰)
            console.log("模拟测试拨打电话...");
            results.call = {
                success: true,
                message: "拨打电话功能已模拟测试",
                note: "为避免实际拨打电话，此功能仅作演示。实际使用时请使用make_phone_call函数。"
            };
            console.log("✓ 拨打电话测试完成");
            // 返回所有测试结果
            return {
                message: "日常生活功能测试完成",
                test_results: results,
                timestamp: new Date().toISOString(),
                summary: "测试了7个日常生活功能，请查看各功能的测试结果。"
            };
        }
        catch (error) {
            return {
                success: false,
                message: `测试过程中发生错误: ${error.message}`
            };
        }
    }
    /**
     * 包装函数 - 统一处理所有daily_life函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     * @param additionalInfo 附加信息(可选)
     */
    async function daily_wrap(func, params, successMessage, failMessage, additionalInfo = "") {
        try {
            console.log(`开始执行函数: ${func.name || '匿名函数'}`);
            console.log(`参数:`, JSON.stringify(params, null, 2));
            // 执行原始函数
            const result = await func(params);
            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2));
            // 如果原始函数已经调用了complete，就不需要再次调用
            if (result === undefined)
                return;
            // 根据结果类型处理
            if (typeof result === "boolean") {
                // 布尔类型结果
                complete({
                    success: result,
                    message: result ? successMessage : failMessage,
                    additionalInfo: additionalInfo
                });
            }
            else {
                // 数据类型结果
                complete({
                    success: true,
                    message: successMessage,
                    additionalInfo: additionalInfo,
                    data: result
                });
            }
        }
        catch (error) {
            // 详细记录错误信息
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`);
            console.error(`错误信息: ${error.message}`);
            console.error(`错误堆栈: ${error.stack}`);
            // 处理错误
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalInfo: additionalInfo,
                error_stack: error.stack
            });
        }
    }
    return {
        get_current_date: async (params) => await daily_wrap(get_current_date, params, "获取日期时间成功", "获取日期时间失败"),
        device_status: async (params) => await daily_wrap(device_status, params, "获取设备状态成功", "获取设备状态失败"),
        search_web: async (params) => await daily_wrap(search_web, params, "网络搜索成功", "网络搜索失败"),
        set_reminder: async (params) => await daily_wrap(set_reminder, params, "设置提醒成功", "设置提醒失败"),
        set_alarm: async (params) => await daily_wrap(set_alarm, params, "设置闹钟成功", "设置闹钟失败"),
        send_message: async (params) => await daily_wrap(send_message, params, "发送消息成功", "发送消息失败"),
        make_phone_call: async (params) => await daily_wrap(make_phone_call, params, "拨打电话成功", "拨打电话失败"),
        main: async (params) => await daily_wrap(main, params, "测试完成", "测试失败")
    };
})();
//逐个导出
exports.get_current_date = dailyLife.get_current_date;
exports.device_status = dailyLife.device_status;
exports.search_web = dailyLife.search_web;
exports.set_reminder = dailyLife.set_reminder;
exports.set_alarm = dailyLife.set_alarm;
exports.send_message = dailyLife.send_message;
exports.make_phone_call = dailyLife.make_phone_call;
exports.main = dailyLife.main;
