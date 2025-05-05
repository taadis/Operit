/*
METADATA
{
    "name": "daily_life",
    "description": "日常生活工具集合，提供丰富的日常功能接口，包括日期时间查询、设备状态监测、天气搜索、提醒闹钟设置、短信电话通讯等。通过系统Intent实现各类日常任务，支持用户便捷地完成日常交互需求。",
    "tools": [
        {
            "name": "get_current_date",
            "description": "获取当前日期和时间，支持多种格式展示",
            "parameters": [
                {
                    "name": "format",
                    "description": "日期格式（'short'简短格式, 'medium'中等格式, 'long'完整格式，或自定义格式）",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "device_status",
            "description": "获取设备状态信息，包括电池和内存使用情况",
            "parameters": []
        },
        {
            "name": "set_reminder",
            "description": "创建提醒或待办事项。",
            "parameters": [
                {
                    "name": "title",
                    "description": "提醒或待办事项的标题",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "description",
                    "description": "提醒的附加详细信息",
                    "type": "string",
                    "required": false
                },
                {
                    "name": "due_date",
                    "description": "提醒的到期日期（ISO字符串格式）",
                    "type": "string",
                    "required": false
                }
            ]
        },
        {
            "name": "set_alarm",
            "description": "在设备上设置闹钟。",
            "parameters": [
                {
                    "name": "hour",
                    "description": "闹钟小时（0-23）",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "minute",
                    "description": "闹钟分钟（0-59）",
                    "type": "number",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "闹钟标签",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "days",
                    "description": "重复闹钟的天数（数字数组，1=周日，7=周六）",
                    "type": "array",
                    "required": false
                }
            ]
        },
        {
            "name": "send_message",
            "description": "发送短信",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "接收者电话号码",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "message",
                    "description": "短信内容",
                    "type": "string",
                    "required": true
                }
            ]
        },
        {
            "name": "make_phone_call",
            "description": "拨打电话",
            "parameters": [
                {
                    "name": "phone_number",
                    "description": "要拨打的电话号码",
                    "type": "string",
                    "required": true
                },
                {
                    "name": "emergency",
                    "description": "是否为紧急呼叫",
                    "type": "boolean",
                    "required": false
                }
            ]
        },
        {
            "name": "search_weather",
            "description": "搜索当前天气信息",
            "parameters": [
                {
                    "name": "location",
                    "description": "要查询天气的位置（城市名称或'current'表示当前位置）",
                    "type": "string",
                    "required": false
                }
            ]
        }
    ],
    "category": "SYSTEM_OPERATION"
}
*/
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var dailyLife = (function () {
    var _this = this;
    /**
     * Get the current date and time in various formats
     * @param params - Optional parameters including format
     */
    function get_current_date(params) {
        return __awaiter(this, void 0, void 0, function () {
            var format, now, formattedDate;
            return __generator(this, function (_a) {
                try {
                    format = params.format || 'medium';
                    now = new Date();
                    formattedDate = void 0;
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
                    return [2 /*return*/, {
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
                        }];
                }
                catch (error) {
                    console.error("[get_current_date] \u9519\u8BEF: ".concat(error.message));
                    console.error(error.stack);
                    throw error;
                }
                return [2 /*return*/];
            });
        });
    }
    /**
     * Get device status information
     */
    function device_status() {
        return __awaiter(this, void 0, void 0, function () {
            var deviceInfo, error_1;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        return [4 /*yield*/, Tools.System.getDeviceInfo()];
                    case 1:
                        deviceInfo = _a.sent();
                        return [2 /*return*/, {
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
                            }];
                    case 2:
                        error_1 = _a.sent();
                        throw new Error("Failed to get device status: ".concat(error_1.message));
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * Search for current weather information
     * @param params - Parameters with optional location
     */
    function search_weather(params) {
        return __awaiter(this, void 0, void 0, function () {
            var location_1, query, result, error_2;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        location_1 = params.location || "current";
                        query = location_1 === "current" ?
                            "当前天气" :
                            "".concat(location_1, " \u5929\u6C14");
                        console.log("\u641C\u7D22\u5929\u6C14\u4FE1\u606F: ".concat(query));
                        return [4 /*yield*/, Tools.Net.search(query)];
                    case 1:
                        result = _a.sent();
                        return [2 /*return*/, {
                                success: true,
                                query: query,
                                location: location_1,
                                timestamp: new Date().toISOString(),
                                weather_results: result.results.map(function (item) { return ({
                                    title: item.title,
                                    url: item.url,
                                    snippet: item.snippet
                                }); }),
                                note: "天气数据来自网络搜索结果，仅供参考。"
                            }];
                    case 2:
                        error_2 = _a.sent();
                        console.error("[search_weather] \u9519\u8BEF: ".concat(error_2.message));
                        console.error(error_2.stack);
                        throw new Error("\u83B7\u53D6\u5929\u6C14\u4FE1\u606F\u5931\u8D25: ".concat(error_2.message));
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * Set a reminder or to-do item
     * @param params - Parameters with reminder details
     */
    function set_reminder(params) {
        return __awaiter(this, void 0, void 0, function () {
            var intent, dueDate, beginTime, endTime, now, beginTime, endTime, result, error_3;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        if (!params.title) {
                            throw new Error("Reminder title is required");
                        }
                        console.log("创建提醒...");
                        console.log("尝试使用隐式Intent创建日历事件...");
                        intent = new Intent("android.intent.action.INSERT" /* IntentAction.ACTION_INSERT */);
                        // 设置日历事件的URI
                        intent.setData("content://com.android.calendar/events");
                        // 设置事件详情
                        intent.putExtra("title", params.title);
                        if (params.description) {
                            intent.putExtra("description", params.description);
                        }
                        // 处理日期
                        if (params.due_date) {
                            dueDate = new Date(params.due_date);
                            beginTime = dueDate.getTime();
                            endTime = beginTime + 3600000;
                            intent.putExtra("beginTime", beginTime);
                            intent.putExtra("endTime", endTime);
                            // 添加单独的日期和时间组件，增加兼容性
                            intent.putExtra("eventTimezone", "UTC");
                            intent.putExtra("allDay", false);
                        }
                        else {
                            now = new Date();
                            beginTime = now.getTime();
                            endTime = beginTime + 3600000;
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
                        return [4 /*yield*/, intent.start()];
                    case 1:
                        result = _a.sent();
                        // 返回结果
                        return [2 /*return*/, {
                                success: true,
                                message: "提醒创建成功",
                                title: params.title,
                                description: params.description || null,
                                due_date: params.due_date || null,
                                method: "implicit_intent",
                                raw_result: result
                            }];
                    case 2:
                        error_3 = _a.sent();
                        console.error("[set_reminder] \u9519\u8BEF: ".concat(error_3.message));
                        console.error(error_3.stack);
                        return [2 /*return*/, {
                                success: false,
                                message: "\u521B\u5EFA\u63D0\u9192\u5931\u8D25: ".concat(error_3.message),
                                title: params.title,
                                description: params.description || null,
                                due_date: params.due_date || null,
                                error: error_3.message
                            }];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * Set an alarm on the device
     * @param params - Parameters with alarm details
     */
    function set_alarm(params) {
        return __awaiter(this, void 0, void 0, function () {
            var intent, result, error_4;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        if (params.hour === undefined || params.minute === undefined) {
                            throw new Error("Hour and minute are required for setting an alarm");
                        }
                        if (typeof params.hour === 'string') {
                            params.hour = Number(params.hour);
                        }
                        if (typeof params.minute === 'string') {
                            params.minute = Number(params.minute);
                        }
                        if (params.days) {
                            if (typeof params.days === 'string') {
                                params.days = JSON.parse(params.days);
                            }
                            if (Array.isArray(params.days)) {
                                params.days = params.days.map(function (day) {
                                    if (typeof day === 'string') {
                                        return Number(day);
                                    }
                                    return day;
                                });
                            }
                            else {
                                console.error("Invalid days format");
                                params.days = [];
                            }
                        }
                        if (params.hour < 0 || params.hour > 23) {
                            throw new Error("Hour must be between 0 and 23");
                        }
                        if (params.minute < 0 || params.minute > 59) {
                            throw new Error("Minute must be between 0 and 59");
                        }
                        console.log("设置闹钟..." + params.hour + ":" + params.minute);
                        console.log("尝试使用隐式Intent设置闹钟...");
                        intent = new Intent("android.intent.action.SET_ALARM");
                        // 或者也可以使用常量: new Intent(IntentAction.ACTION_MAIN);
                        // 设置闹钟详情 - 使用正确的参数名称
                        intent.putExtra("android.intent.extra.alarm.HOUR", params.hour);
                        intent.putExtra("android.intent.extra.alarm.MINUTES", params.minute);
                        // 添加标签
                        intent.putExtra("android.intent.extra.alarm.MESSAGE", params.message);
                        // 设置重复日期（如果提供）
                        if (params.days && params.days.length > 0) {
                            intent.putExtra("android.intent.extra.alarm.DAYS", params.days);
                        }
                        // 不跳过UI确认 - 这可能是问题所在，某些设备需要用户确认
                        // intent.putExtra("android.intent.extra.alarm.SKIP_UI", true);
                        // 设置闹钟应该立即响铃
                        intent.putExtra("android.intent.extra.alarm.VIBRATE", true);
                        // 添加标志 - 必须使用NEW_TASK标志来启动Activity
                        intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
                        // 确保Intent被视为Activity启动而不是广播
                        // 手动添加DEFAULT类别以确保Intent可以被正确处理
                        intent.addCategory("android.intent.category.DEFAULT");
                        return [4 /*yield*/, intent.start()];
                    case 1:
                        result = _a.sent();
                        // 返回结果
                        return [2 /*return*/, {
                                success: true,
                                message: "闹钟设置成功",
                                alarm_time: "".concat(params.hour.toString().padStart(2, '0'), ":").concat(params.minute.toString().padStart(2, '0')),
                                label: params.message || null,
                                repeat_days: params.days || null,
                                method: "implicit_intent",
                                raw_result: result
                            }];
                    case 2:
                        error_4 = _a.sent();
                        console.error("[set_alarm] \u9519\u8BEF: ".concat(error_4.message));
                        console.error(error_4.stack);
                        return [2 /*return*/, {
                                success: false,
                                message: "\u8BBE\u7F6E\u95F9\u949F\u5931\u8D25: ".concat(error_4.message),
                                alarm_time: "".concat(params.hour.toString().padStart(2, '0'), ":").concat(params.minute.toString().padStart(2, '0')),
                                label: params.message || null,
                                repeat_days: params.days || null,
                                error: error_4.message
                            }];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * Send a text message
     * @param params - Parameters with message details
     */
    function send_message(params) {
        return __awaiter(this, void 0, void 0, function () {
            var intent, smsUri, result, error_5;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        if (!params.phone_number) {
                            throw new Error("Phone number is required");
                        }
                        if (!params.message) {
                            throw new Error("Message content is required");
                        }
                        console.log("\u53D1\u9001\u77ED\u4FE1: ".concat(params.phone_number));
                        intent = new Intent("android.intent.action.SENDTO" /* IntentAction.ACTION_SENDTO */);
                        smsUri = "smsto:".concat(params.phone_number);
                        intent.setData(smsUri);
                        // 添加短信内容
                        intent.putExtra("sms_body", params.message);
                        // 添加必要的标志
                        intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
                        return [4 /*yield*/, intent.start()];
                    case 1:
                        result = _a.sent();
                        return [2 /*return*/, {
                                success: true,
                                message: "短信编辑界面已打开",
                                phone_number: params.phone_number,
                                content_preview: params.message.length > 30 ? params.message.substring(0, 30) + "..." : params.message,
                                raw_result: result
                            }];
                    case 2:
                        error_5 = _a.sent();
                        console.error("\u53D1\u9001\u77ED\u4FE1\u5931\u8D25: ".concat(error_5.message));
                        return [2 /*return*/, {
                                success: false,
                                message: "\u53D1\u9001\u77ED\u4FE1\u5931\u8D25: ".concat(error_5.message),
                                phone_number: params.phone_number
                            }];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * Make a phone call
     * @param params - Parameters with call details
     */
    function make_phone_call(params) {
        return __awaiter(this, void 0, void 0, function () {
            var action, intent, phoneUri, result, error_6;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        if (!params.phone_number) {
                            throw new Error("Phone number is required");
                        }
                        console.log("\u62E8\u6253\u7535\u8BDD: ".concat(params.phone_number));
                        // 选择合适的Intent Action
                        // 如果是紧急电话，使用ACTION_CALL_EMERGENCY，否则使用ACTION_DIAL
                        if (typeof params.emergency === 'string') {
                            params.emergency = params.emergency === 'true';
                        }
                        action = params.emergency ? "android.intent.action.CALL_EMERGENCY" /* IntentAction.ACTION_CALL_EMERGENCY */ : "android.intent.action.DIAL" /* IntentAction.ACTION_DIAL */;
                        intent = new Intent(action);
                        phoneUri = "tel:".concat(params.phone_number);
                        intent.setData(phoneUri);
                        // 添加必要的标志
                        intent.addFlag(268435456 /* IntentFlag.ACTIVITY_NEW_TASK */);
                        return [4 /*yield*/, intent.start()];
                    case 1:
                        result = _a.sent();
                        return [2 /*return*/, {
                                success: true,
                                message: params.emergency ? "紧急电话已拨打" : "拨号界面已打开",
                                phone_number: params.phone_number,
                                is_emergency: params.emergency || false,
                                raw_result: result
                            }];
                    case 2:
                        error_6 = _a.sent();
                        console.error("\u62E8\u6253\u7535\u8BDD\u5931\u8D25: ".concat(error_6.message));
                        return [2 /*return*/, {
                                success: false,
                                message: "\u62E8\u6253\u7535\u8BDD\u5931\u8D25: ".concat(error_6.message),
                                phone_number: params.phone_number,
                                is_emergency: params.emergency || false
                            }];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 等待指定的毫秒数
     * @param ms 等待的毫秒数
     */
    function sleep(ms) {
        return __awaiter(this, void 0, void 0, function () {
            var sleepTime;
            return __generator(this, function (_a) {
                sleepTime = Number(ms);
                if (isNaN(sleepTime)) {
                    throw new Error("Invalid sleep time");
                }
                return [2 /*return*/, new Promise(function (resolve) { return setTimeout(resolve, sleepTime); })];
            });
        });
    }
    /**
     * Test and demonstrate all daily life functions
     * This function shows examples of all available daily life functions
     */
    function main() {
        return __awaiter(this, void 0, void 0, function () {
            var results, dateResult, error_7, deviceResult, error_8, weatherResult, error_9, now, hour, minute, alarmResult, error_10, testPhoneNumber, testMessage, smsResult, error_11, testPhoneNumber, dialResult, error_12, error_13;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 27, , 28]);
                        results = {};
                        // 1. 测试当前日期时间函数
                        console.log("测试获取当前日期时间...");
                        _a.label = 1;
                    case 1:
                        _a.trys.push([1, 3, , 4]);
                        return [4 /*yield*/, get_current_date({})];
                    case 2:
                        dateResult = _a.sent();
                        results.date = dateResult;
                        console.log("✓ 日期时间获取成功");
                        return [3 /*break*/, 4];
                    case 3:
                        error_7 = _a.sent();
                        results.date = { error: "\u83B7\u53D6\u65E5\u671F\u65F6\u95F4\u5931\u8D25: ".concat(error_7.message) };
                        console.log("✗ 日期时间获取失败");
                        return [3 /*break*/, 4];
                    case 4:
                        // 2. 测试设备状态函数
                        console.log("测试获取设备状态...");
                        _a.label = 5;
                    case 5:
                        _a.trys.push([5, 7, , 8]);
                        return [4 /*yield*/, device_status()];
                    case 6:
                        deviceResult = _a.sent();
                        results.device = deviceResult;
                        console.log("✓ 设备状态获取成功");
                        return [3 /*break*/, 8];
                    case 7:
                        error_8 = _a.sent();
                        results.device = { error: "\u83B7\u53D6\u8BBE\u5907\u72B6\u6001\u5931\u8D25: ".concat(error_8.message) };
                        console.log("✗ 设备状态获取失败");
                        return [3 /*break*/, 8];
                    case 8:
                        // 3. 测试天气搜索功能
                        console.log("测试天气搜索...");
                        _a.label = 9;
                    case 9:
                        _a.trys.push([9, 11, , 12]);
                        return [4 /*yield*/, search_weather({ location: "current" })];
                    case 10:
                        weatherResult = _a.sent();
                        results.weather = weatherResult;
                        console.log("✓ 天气搜索成功");
                        return [3 /*break*/, 12];
                    case 11:
                        error_9 = _a.sent();
                        results.weather = { error: "\u5929\u6C14\u641C\u7D22\u5931\u8D25: ".concat(error_9.message) };
                        console.log("✗ 天气搜索失败");
                        return [3 /*break*/, 12];
                    case 12:
                        // 4. 测试设置提醒功能
                        // console.log("测试设置提醒...");
                        // try {
                        //     const reminderResult = await set_reminder({
                        //         title: "测试提醒",
                        //         description: "这是一个测试提醒",
                        //         // 可以设置未来时间
                        //         due_date: new Date(Date.now() + 3600000).toISOString() // 1小时后
                        //     });
                        //     results.reminder = reminderResult;
                        //     console.log("✓ 设置提醒成功");
                        // } catch (error) {
                        //     results.reminder = { error: `设置提醒失败: ${error.message}` };
                        //     console.log("✗ 设置提醒失败");
                        // }
                        // 5. 测试设置闹钟功能
                        console.log("测试设置闹钟...");
                        _a.label = 13;
                    case 13:
                        _a.trys.push([13, 15, , 16]);
                        now = new Date();
                        hour = now.getHours();
                        minute = (now.getMinutes() + 5) % 60;
                        return [4 /*yield*/, set_alarm({
                                hour: hour.toString(),
                                minute: minute.toString(),
                                message: "测试闹钟",
                                // 可以添加重复日期测试，例如每周一和周五
                                // days: [2, 6]  // 2表示周一，6表示周五
                            })];
                    case 14:
                        alarmResult = _a.sent();
                        results.alarm = alarmResult;
                        console.log("✓ 设置闹钟成功");
                        return [3 /*break*/, 16];
                    case 15:
                        error_10 = _a.sent();
                        results.alarm = { error: "\u8BBE\u7F6E\u95F9\u949F\u5931\u8D25: ".concat(error_10.message) };
                        console.log("✗ 设置闹钟失败");
                        return [3 /*break*/, 16];
                    case 16:
                        // 6. 测试发送消息功能
                        console.log("测试发送短信功能...");
                        _a.label = 17;
                    case 17:
                        _a.trys.push([17, 20, , 21]);
                        testPhoneNumber = "10086";
                        testMessage = "这是一条测试短信，不会实际发送";
                        return [4 /*yield*/, send_message({
                                phone_number: testPhoneNumber,
                                message: testMessage
                            })];
                    case 18:
                        smsResult = _a.sent();
                        results.message = smsResult;
                        console.log("✓ 短信测试界面打开成功");
                        // 等待用户查看短信界面
                        return [4 /*yield*/, sleep(5000)];
                    case 19:
                        // 等待用户查看短信界面
                        _a.sent();
                        return [3 /*break*/, 21];
                    case 20:
                        error_11 = _a.sent();
                        results.message = { error: "\u77ED\u4FE1\u6D4B\u8BD5\u5931\u8D25: ".concat(error_11.message) };
                        console.log("✗ 短信测试失败");
                        return [3 /*break*/, 21];
                    case 21:
                        // 7. 测试拨打电话功能
                        console.log("测试拨号功能...");
                        _a.label = 22;
                    case 22:
                        _a.trys.push([22, 25, , 26]);
                        testPhoneNumber = "10086";
                        return [4 /*yield*/, make_phone_call({
                                phone_number: testPhoneNumber,
                                emergency: false
                            })];
                    case 23:
                        dialResult = _a.sent();
                        results.call = dialResult;
                        console.log("✓ 拨号界面打开成功");
                        // 等待用户查看拨号界面
                        return [4 /*yield*/, sleep(5000)];
                    case 24:
                        // 等待用户查看拨号界面
                        _a.sent();
                        return [3 /*break*/, 26];
                    case 25:
                        error_12 = _a.sent();
                        results.call = { error: "\u62E8\u53F7\u6D4B\u8BD5\u5931\u8D25: ".concat(error_12.message) };
                        console.log("✗ 拨号测试失败");
                        return [3 /*break*/, 26];
                    case 26: 
                    // 返回所有测试结果
                    return [2 /*return*/, {
                            message: "日常生活功能测试完成",
                            test_results: results,
                            timestamp: new Date().toISOString(),
                            summary: "测试了各种日常生活功能，包括天气搜索、拨号和短信测试。请查看各功能的测试结果。"
                        }];
                    case 27:
                        error_13 = _a.sent();
                        return [2 /*return*/, {
                                success: false,
                                message: "\u6D4B\u8BD5\u8FC7\u7A0B\u4E2D\u53D1\u751F\u9519\u8BEF: ".concat(error_13.message)
                            }];
                    case 28: return [2 /*return*/];
                }
            });
        });
    }
    /**
     * 包装函数 - 统一处理所有daily_life函数的返回结果
     * @param func 原始函数
     * @param params 函数参数
     * @param successMessage 成功消息
     * @param failMessage 失败消息
     * @param additionalInfo 附加信息(可选)
     */
    function daily_wrap(func_1, params_1, successMessage_1, failMessage_1) {
        return __awaiter(this, arguments, void 0, function (func, params, successMessage, failMessage, additionalInfo) {
            var result, error_14;
            if (additionalInfo === void 0) { additionalInfo = ""; }
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        console.log("\u5F00\u59CB\u6267\u884C\u51FD\u6570: ".concat(func.name || '匿名函数'));
                        console.log("\u53C2\u6570:", JSON.stringify(params, null, 2));
                        return [4 /*yield*/, func(params)];
                    case 1:
                        result = _a.sent();
                        console.log("\u51FD\u6570 ".concat(func.name || '匿名函数', " \u6267\u884C\u7ED3\u679C:"), JSON.stringify(result, null, 2));
                        // 如果原始函数已经调用了complete，就不需要再次调用
                        if (result === undefined)
                            return [2 /*return*/];
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
                        return [3 /*break*/, 3];
                    case 2:
                        error_14 = _a.sent();
                        // 详细记录错误信息
                        console.error("\u51FD\u6570 ".concat(func.name || '匿名函数', " \u6267\u884C\u5931\u8D25!"));
                        console.error("\u9519\u8BEF\u4FE1\u606F: ".concat(error_14.message));
                        console.error("\u9519\u8BEF\u5806\u6808: ".concat(error_14.stack));
                        // 处理错误
                        complete({
                            success: false,
                            message: "".concat(failMessage, ": ").concat(error_14.message),
                            additionalInfo: additionalInfo,
                            error_stack: error_14.stack
                        });
                        return [3 /*break*/, 3];
                    case 3: return [2 /*return*/];
                }
            });
        });
    }
    return {
        get_current_date: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(get_current_date, params, "获取日期时间成功", "获取日期时间失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        device_status: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(device_status, params, "获取设备状态成功", "获取设备状态失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        search_weather: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(search_weather, params, "获取天气信息成功", "获取天气信息失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        set_reminder: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(set_reminder, params, "设置提醒成功", "设置提醒失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        set_alarm: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(set_alarm, params, "设置闹钟成功", "设置闹钟失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        send_message: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(send_message, params, "发送消息成功", "发送消息失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        make_phone_call: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(make_phone_call, params, "拨打电话成功", "拨打电话失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); },
        main: function (params) { return __awaiter(_this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, daily_wrap(main, params, "测试完成", "测试失败")];
                    case 1: return [2 /*return*/, _a.sent()];
                }
            });
        }); }
    };
})();
//逐个导出
exports.get_current_date = dailyLife.get_current_date;
exports.device_status = dailyLife.device_status;
exports.search_weather = dailyLife.search_weather;
exports.set_reminder = dailyLife.set_reminder;
exports.set_alarm = dailyLife.set_alarm;
exports.send_message = dailyLife.send_message;
exports.make_phone_call = dailyLife.make_phone_call;
exports.main = dailyLife.main;
