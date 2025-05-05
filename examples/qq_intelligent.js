/*
METADATA
{
    // QQ Intelligent Assistant Package
    name: qq_intelligent
    description: 高级QQ智能助手，通过UI自动化技术实现QQ应用交互，支持消息自动回复、历史记录读取、联系人搜索与通讯等功能，为AI赋予QQ社交能力。适用于智能客服、自动回复、社交辅助等场景。

    // Tools in this package
    tools: [
        {
            name: reply
            description: 在当前聊天窗口输入消息并发送。一般情况下，用户想要ai帮忙发送消息时，需要ai自己去生成回复的消息，如果不确定发送的内容，请不要调用工具。只要是停留在聊天界面，就可以直接调用这个。
            parameters: [
                {
                    name: message
                    description: 要发送的消息
                    type: string
                    required: true
                },
                {
                    name: click_send
                    description: 是否点击发送按钮
                    type: boolean
                    required: false
                }
            ]
        },
        {
            name: find_user
            description: 在QQ联系人或群成员中查找用户
            parameters: [
                {
                    name: user_name
                    description: 搜索用户名称
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: 搜索类型（contacts/groups）
                    type: string
                    required: true
                }
            ]
        },
        {
            name: find_and_reply
            description: 查找用户并发送消息
            parameters: [
                {
                    name: message
                    description: 要发送的消息
                    type: string
                    required: true
                },
                {
                    name: user_name
                    description: 发送目标用户名称
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: 用户类型（contacts/groups）
                    type: string
                    required: true
                },
                {
                    name: click_send
                    description: 是否点击发送按钮
                    type: boolean
                    required: false
                }
            ]
        },
        {
            name: get_history
            description: 获取当前聊天窗口的历史消息。如果用户要求读取聊天记录，那么可以用这个工具或者find_and_get_history。
            parameters: [
                {
                    name: message_num
                    description: 获取的消息数量
                    type: number
                    required: false
                }
            ]
        },
        {
            name: find_and_get_history
            description: 查找用户并获取聊天历史记录。一般情况下，用户想要让ai帮忙回消息的时候，需要先调用这个，得到历史记录后直接调用reply。
            parameters: [
                {
                    name: user_name
                    description: 搜索用户名称
                    type: string
                    required: true
                },
                {
                    name: user_type
                    description: 搜索类型（contacts/groups）
                    type: string
                    required: true
                },
                {
                    name: message_num
                    description: 获取的消息数量
                    type: number
                    required: false
                }
            ]
        }
    ],
    "category": "UI_AUTOMATION"
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
var __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
var QQIntelligent = (function () {
    var _this = this;
    function close_keyboard() {
        return __awaiter(this, void 0, void 0, function () {
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, Tools.UI.pressKey("KEYCODE_BACK")];
                    case 1:
                        _a.sent();
                        return [2 /*return*/];
                }
            });
        });
    }
    function is_in_group() {
        return __awaiter(this, void 0, void 0, function () {
            var page;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, UINode.getCurrentPage()];
                    case 1:
                        page = _a.sent();
                        return [2 /*return*/, page.findAllByContentDesc('语音').findIndex(function (e) { var _a, _b, _c; return ((_a = e.className) === null || _a === void 0 ? void 0 : _a.includes('ImageButton')) && ((_c = (_b = e.centerPoint) === null || _b === void 0 ? void 0 : _b.y) !== null && _c !== void 0 ? _c : 0) > 1000; }) === -1];
                }
            });
        });
    }
    Array.prototype.at = function (index) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    };
    function get_history(params) {
        return __awaiter(this, void 0, void 0, function () {
            var message_num, page, chat_title, tryMax, messageList, allMessages, list_view, _i, _a, message, message_text, sender, avatar, uniqueMessages, seen, _b, allMessages_1, msg, key;
            var _c, _d, _e, _f, _g, _h, _j;
            return __generator(this, function (_k) {
                switch (_k.label) {
                    case 0:
                        message_num = Number(params.message_num) || 10;
                        return [4 /*yield*/, UINode.getCurrentPage()];
                    case 1:
                        page = _k.sent();
                        chat_title = (_e = (_d = (_c = page.findById('com.tencent.mobileqq:id/ivTitleBtnLeft')) === null || _c === void 0 ? void 0 : _c.parent) === null || _d === void 0 ? void 0 : _d.allTexts()[0]) !== null && _e !== void 0 ? _e : "";
                        // const is_group = await is_in_group();
                        console.log("chat_title", chat_title);
                        tryMax = 0;
                        _k.label = 2;
                    case 2:
                        if (!(tryMax < 1)) return [3 /*break*/, 5];
                        tryMax++;
                        return [4 /*yield*/, Tools.UI.swipe(100, 1000, 100, 200)];
                    case 3:
                        _k.sent();
                        return [4 /*yield*/, Tools.System.sleep(500)];
                    case 4:
                        _k.sent();
                        return [3 /*break*/, 2];
                    case 5:
                        messageList = [];
                        allMessages = [];
                        //获取历史消息
                        tryMax = 0;
                        _k.label = 6;
                    case 6:
                        if (!(tryMax < 5)) return [3 /*break*/, 10];
                        tryMax++;
                        return [4 /*yield*/, UINode.getCurrentPage()];
                    case 7:
                        page = _k.sent();
                        console.log("page", page.toFormattedString());
                        list_view = page.findByClass('RecyclerView');
                        if (!list_view) {
                            return [2 /*return*/, undefined];
                        }
                        // 清空临时消息列表
                        messageList = [];
                        // 获取当前可见的消息列表
                        for (_i = 0, _a = list_view.children; _i < _a.length; _i++) {
                            message = _a[_i];
                            message_text = message.allTexts().join('/');
                            sender = "other";
                            avatar = message.findByClass('ImageView');
                            if (avatar) {
                                if (((_g = (_f = avatar.centerPoint) === null || _f === void 0 ? void 0 : _f.x) !== null && _g !== void 0 ? _g : 0) < 300) {
                                    sender = "other";
                                    console.log("other", (_h = avatar.centerPoint) === null || _h === void 0 ? void 0 : _h.x);
                                }
                                else {
                                    sender = "self";
                                    console.log("self", (_j = avatar.centerPoint) === null || _j === void 0 ? void 0 : _j.x);
                                }
                            }
                            else {
                                sender = "self";
                                console.log("self");
                            }
                            messageList.push({ message: message_text, sender: sender });
                        }
                        // 将当前视图中的消息添加到总集合中，保持顺序
                        // 从下往上滑动时，将新获取的消息放在前面（保持旧消息在前，新消息在后的顺序）
                        allMessages = __spreadArray(__spreadArray([], messageList, true), allMessages, true);
                        // 如果已经获取了足够多的消息，停止滚动
                        if (allMessages.length >= message_num) {
                            return [3 /*break*/, 10];
                        }
                        return [4 /*yield*/, Tools.UI.swipe(100, 300, 100, 1000)];
                    case 8:
                        _k.sent();
                        return [4 /*yield*/, Tools.System.sleep(500)];
                    case 9:
                        _k.sent();
                        return [3 /*break*/, 6];
                    case 10:
                        uniqueMessages = [];
                        seen = new Set();
                        for (_b = 0, allMessages_1 = allMessages; _b < allMessages_1.length; _b++) {
                            msg = allMessages_1[_b];
                            key = "".concat(msg.message, "-").concat(msg.sender);
                            if (!seen.has(key)) {
                                seen.add(key);
                                uniqueMessages.push(msg);
                            }
                        }
                        // 返回符合数量要求的消息列表（保持原始顺序）
                        return [2 /*return*/, { messages: uniqueMessages.slice(0, message_num), chat_title: chat_title }];
                }
            });
        });
    }
    function reply(params) {
        return __awaiter(this, void 0, void 0, function () {
            var message, click_send;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        message = params.message || "";
                        click_send = params.click_send || false;
                        return [4 /*yield*/, Tools.UI.setText(message)];
                    case 1:
                        _a.sent();
                        return [4 /*yield*/, Tools.System.sleep(500)];
                    case 2:
                        _a.sent();
                        if (!click_send) return [3 /*break*/, 4];
                        return [4 /*yield*/, Tools.UI.clickElement({
                                resourceId: "com.tencent.mobileqq:id/send_btn",
                                index: "0"
                            })];
                    case 3:
                        _a.sent();
                        _a.label = 4;
                    case 4: return [2 /*return*/, true];
                }
            });
        });
    }
    function find_and_reply(params) {
        return __awaiter(this, void 0, void 0, function () {
            var message, user_name, user_type, click_send, result;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        message = params.message || "";
                        user_name = params.user_name || "";
                        user_type = params.user_type || "contacts";
                        click_send = params.click_send || false;
                        return [4 /*yield*/, find_user({ user_name: user_name, user_type: user_type })];
                    case 1:
                        result = _a.sent();
                        if (!result) {
                            return [2 /*return*/, false];
                        }
                        return [4 /*yield*/, Tools.System.sleep(1000)];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, reply({ message: message, click_send: click_send })];
                    case 3: return [2 /*return*/, _a.sent()];
                }
            });
        });
    }
    function ensureActivity() {
        return __awaiter(this, arguments, void 0, function (activityName, packageName, enterActivity, tryMax) {
            var android, activity;
            var _this = this;
            var _a, _b;
            if (activityName === void 0) { activityName = ""; }
            if (packageName === void 0) { packageName = "com.tencent.mobileqq"; }
            if (enterActivity === void 0) { enterActivity = function () { return __awaiter(_this, void 0, void 0, function () { return __generator(this, function (_a) {
                return [2 /*return*/, true];
            }); }); }; }
            if (tryMax === void 0) { tryMax = 1; }
            return __generator(this, function (_c) {
                switch (_c.label) {
                    case 0:
                        android = new Android();
                        return [4 /*yield*/, Tools.UI.getPageInfo()];
                    case 1:
                        activity = _c.sent();
                        if ((_a = activity.activityName) === null || _a === void 0 ? void 0 : _a.includes(activityName)) {
                            return [2 /*return*/, true];
                        }
                        _c.label = 2;
                    case 2:
                        if (!(tryMax > 0)) return [3 /*break*/, 10];
                        tryMax--;
                        return [4 /*yield*/, Tools.System.stopApp(packageName)];
                    case 3:
                        _c.sent();
                        return [4 /*yield*/, Tools.System.sleep(2000)];
                    case 4:
                        _c.sent();
                        return [4 /*yield*/, Tools.System.startApp(packageName)];
                    case 5:
                        _c.sent();
                        // let intent = android.createIntent();
                        // intent.setComponent(packageName, activityName);
                        // await intent.start();
                        return [4 /*yield*/, Tools.System.sleep(3000)];
                    case 6:
                        // let intent = android.createIntent();
                        // intent.setComponent(packageName, activityName);
                        // await intent.start();
                        _c.sent(); // Give some time for app to launch
                        return [4 /*yield*/, enterActivity()];
                    case 7:
                        if (!_c.sent()) return [3 /*break*/, 9];
                        return [4 /*yield*/, Tools.UI.getPageInfo()];
                    case 8:
                        activity = _c.sent();
                        if ((_b = activity.activityName) === null || _b === void 0 ? void 0 : _b.includes(activityName)) {
                            return [2 /*return*/, true];
                        }
                        _c.label = 9;
                    case 9: return [3 /*break*/, 2];
                    case 10: return [2 /*return*/, false];
                }
            });
        });
    }
    function find_user(params) {
        return __awaiter(this, void 0, void 0, function () {
            var user_name, user_type, firstTarget, tryMax, search_btn, currentPage, searchResult, isNeedToCatch, _i, _a, child, title;
            var _this = this;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        user_name = params.user_name || "";
                        user_type = params.user_type || "contacts";
                        return [4 /*yield*/, ensureActivity("com.tencent.mobileqq.search.activity.UniteSearchActivity", "com.tencent.mobileqq", function () { return __awaiter(_this, void 0, void 0, function () {
                                var search_btn;
                                return __generator(this, function (_a) {
                                    switch (_a.label) {
                                        case 0: return [4 /*yield*/, UINode.getCurrentPage()];
                                        case 1:
                                            search_btn = (_a.sent()).findByText("搜索");
                                            if (!search_btn) return [3 /*break*/, 3];
                                            return [4 /*yield*/, search_btn.click()];
                                        case 2:
                                            _a.sent();
                                            return [2 /*return*/, true];
                                        case 3: return [2 /*return*/, false];
                                    }
                                });
                            }); })];
                    case 1:
                        if (!(_b.sent())) {
                            return [2 /*return*/, false];
                        }
                        firstTarget = undefined;
                        tryMax = 0;
                        _b.label = 2;
                    case 2:
                        if (!(tryMax < 2)) return [3 /*break*/, 13];
                        tryMax++;
                        return [4 /*yield*/, UINode.getCurrentPage()];
                    case 3:
                        search_btn = (_b.sent()).findByText("搜索");
                        if (!search_btn) return [3 /*break*/, 6];
                        return [4 /*yield*/, search_btn.click()];
                    case 4:
                        _b.sent();
                        return [4 /*yield*/, Tools.System.sleep(500)];
                    case 5:
                        _b.sent();
                        _b.label = 6;
                    case 6: 
                    // 输入搜索内容
                    return [4 /*yield*/, Tools.UI.setText(user_name)];
                    case 7:
                        // 输入搜索内容
                        _b.sent();
                        return [4 /*yield*/, Tools.System.sleep(3000 * tryMax)];
                    case 8:
                        _b.sent();
                        return [4 /*yield*/, close_keyboard()];
                    case 9:
                        _b.sent();
                        return [4 /*yield*/, UINode.getCurrentPage()];
                    case 10:
                        currentPage = _b.sent();
                        searchResult = currentPage.findAllById('com.tencent.mobileqq:id/title');
                        isNeedToCatch = false;
                        for (_i = 0, _a = searchResult; _i < _a.length; _i++) {
                            child = _a[_i];
                            if (isNeedToCatch) {
                                firstTarget = child;
                                break;
                            }
                            title = child;
                            if (title) {
                                if (user_type == "contacts" && title.text == "联系人") {
                                    isNeedToCatch = true;
                                }
                                else if (user_type == "groups" && title.text == "群聊") {
                                    isNeedToCatch = true;
                                }
                            }
                        }
                        if (!firstTarget) return [3 /*break*/, 12];
                        return [4 /*yield*/, firstTarget.click()];
                    case 11:
                        _b.sent();
                        return [2 /*return*/, true];
                    case 12: return [3 /*break*/, 2];
                    case 13: return [2 /*return*/, false];
                }
            });
        });
    }
    function find_and_get_history(params) {
        return __awaiter(this, void 0, void 0, function () {
            var user_name, user_type, message_num, result;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        user_name = params.user_name || "";
                        user_type = params.user_type || "contacts";
                        message_num = params.message_num || 10;
                        return [4 /*yield*/, find_user({ user_name: user_name, user_type: user_type })];
                    case 1:
                        result = _a.sent();
                        if (!result) {
                            return [2 /*return*/, undefined];
                        }
                        return [4 /*yield*/, Tools.System.sleep(1000)];
                    case 2:
                        _a.sent();
                        return [4 /*yield*/, get_history({ message_num: message_num })];
                    case 3: return [2 /*return*/, _a.sent()];
                }
            });
        });
    }
    function main() {
        return __awaiter(this, void 0, void 0, function () {
            var result;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, find_and_get_history({ user_name: "Dec", user_type: "groups", message_num: 20 })];
                    case 1:
                        result = _a.sent();
                        console.log(result);
                        complete({
                            success: result
                        });
                        return [2 /*return*/];
                }
            });
        });
    }
    function wrap_bool(func_1, params_1, successMessage_1, failMessage_1) {
        return __awaiter(this, arguments, void 0, function (func, params, successMessage, failMessage, additionalMessage) {
            if (additionalMessage === void 0) { additionalMessage = ""; }
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, func(params)];
                    case 1:
                        if (_a.sent()) {
                            complete({
                                success: true,
                                message: successMessage,
                                additionalMessage: additionalMessage
                            });
                        }
                        else {
                            complete({
                                success: false,
                                message: failMessage,
                                additionalMessage: additionalMessage
                            });
                        }
                        return [2 /*return*/];
                }
            });
        });
    }
    function wrap_data(func_1, params_1, successMessage_1, failMessage_1) {
        return __awaiter(this, arguments, void 0, function (func, params, successMessage, failMessage, additionalMessage) {
            var result;
            if (additionalMessage === void 0) { additionalMessage = ""; }
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0: return [4 /*yield*/, func(params)];
                    case 1:
                        result = _a.sent();
                        complete({
                            success: true,
                            message: successMessage,
                            additionalMessage: additionalMessage,
                            data: result
                        });
                        return [2 /*return*/];
                }
            });
        });
    }
    return {
        main: main,
        reply: function (params) { return __awaiter(_this, void 0, void 0, function () { return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, wrap_bool(reply, params, "发送成功", "发送失败")];
                case 1: return [2 /*return*/, _a.sent()];
            }
        }); }); },
        find_user: function (params) { return __awaiter(_this, void 0, void 0, function () { var _a, _b; return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    _a = wrap_bool;
                    _b = [find_user, params, "查找成功", "查找失败，停留在界面"];
                    return [4 /*yield*/, UINode.getCurrentPage()];
                case 1: return [4 /*yield*/, _a.apply(void 0, _b.concat([(_c.sent()).toFormattedString()]))];
                case 2: return [2 /*return*/, _c.sent()];
            }
        }); }); },
        find_and_reply: function (params) { return __awaiter(_this, void 0, void 0, function () { var _a, _b; return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    _a = wrap_bool;
                    _b = [find_and_reply, params, "发送成功", "发送失败，停留在界面"];
                    return [4 /*yield*/, UINode.getCurrentPage()];
                case 1: return [4 /*yield*/, _a.apply(void 0, _b.concat([(_c.sent()).toFormattedString()]))];
                case 2: return [2 /*return*/, _c.sent()];
            }
        }); }); },
        get_history: function (params) { return __awaiter(_this, void 0, void 0, function () { return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, wrap_data(get_history, params, "获取历史消息成功", "获取历史消息失败")];
                case 1: return [2 /*return*/, _a.sent()];
            }
        }); }); },
        find_and_get_history: function (params) { return __awaiter(_this, void 0, void 0, function () { return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, wrap_data(find_and_get_history, params, "获取历史消息成功", "获取历史消息失败")];
                case 1: return [2 /*return*/, _a.sent()];
            }
        }); }); }
    };
})();
//逐个导出
exports.reply = QQIntelligent.reply;
exports.find_user = QQIntelligent.find_user;
exports.find_and_reply = QQIntelligent.find_and_reply;
exports.get_history = QQIntelligent.get_history;
exports.find_and_get_history = QQIntelligent.find_and_get_history;
exports.main = QQIntelligent.main;
