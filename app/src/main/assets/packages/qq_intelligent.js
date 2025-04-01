/*
METADATA
{
    // QQ Intelligent Assistant Package
    name: qq_intelligent
    description: 智能QQ助手：提供QQ消息回复、消息获取、用户查找等功能，通过UI自动化操作实现

    // Tools in this package
    tools: [
        {
            name: reply
            description: 在当前聊天窗口输入消息并发送。一般情况下，用户想要ai帮忙发送消息时，需要ai自己去生成回复的消息，如果不确定发送的内容，请不要调用工具。
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
async function close_keyboard() {
    await Tools.UI.pressKey("KEYCODE_BACK");
}
async function is_in_group() {
    let page = await UINode.getCurrentPage();
    return page.findById('com.tencent.mobileqq:id/xcf') === undefined;
}
async function get_history(params) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m;
    const message_num = params.message_num || 10;
    let page = await UINode.getCurrentPage();
    //获取群名称
    const chat_title = (_b = (_a = page.findById('com.tencent.mobileqq:id/ywf')) === null || _a === void 0 ? void 0 : _a.text) !== null && _b !== void 0 ? _b : "";
    const is_group = await is_in_group();
    //先滑动到最底部
    let tryMax = 0;
    while (tryMax < 1) {
        tryMax++;
        await Tools.UI.swipe(100, 1000, 100, 200);
        await Tools.System.sleep(500);
    }
    let messageList = [];
    let allMessages = [];
    //获取历史消息
    tryMax = 0;
    while (tryMax < 5) {
        tryMax++;
        page = await UINode.getCurrentPage();
        const list_view = page.findByClass('RecyclerView');
        if (!list_view) {
            return undefined;
        }
        // 清空临时消息列表
        messageList = [];
        // 获取当前可见的消息列表
        for (const message of list_view.children) {
            let message_text = (_d = (_c = message.findById('com.tencent.mobileqq:id/kad')) === null || _c === void 0 ? void 0 : _c.text) !== null && _d !== void 0 ? _d : "";
            if (message_text == "") {
                message_text = message.findById('com.tencent.mobileqq:id/kqn') ? '图片' : '';
            }
            let sender = (_f = (_e = message.findById('com.tencent.mobileqq:id/kap')) === null || _e === void 0 ? void 0 : _e.text) !== null && _f !== void 0 ? _f : "";
            if (!is_group) {
                //判断头像再左边还是右边
                const avatar = message.findById('com.tencent.mobileqq:id/b0x');
                if (avatar) {
                    if (((_h = (_g = avatar.centerPoint) === null || _g === void 0 ? void 0 : _g.x) !== null && _h !== void 0 ? _h : 0) < 300) {
                        sender = chat_title;
                        console.log("opps", (_j = avatar.centerPoint) === null || _j === void 0 ? void 0 : _j.x);
                    }
                    else {
                        sender = "self";
                        console.log("self", (_k = avatar.centerPoint) === null || _k === void 0 ? void 0 : _k.x);
                    }
                }
                else {
                    sender = "self";
                    console.log("self");
                }
            }
            const time = (_m = (_l = message.findById('com.tencent.mobileqq:id/f24')) === null || _l === void 0 ? void 0 : _l.allTexts().join('')) !== null && _m !== void 0 ? _m : "";
            messageList.push({ message: message_text, sender: sender, time: time });
        }
        // 将当前视图中的消息添加到总集合中，保持顺序
        // 从下往上滑动时，将新获取的消息放在前面（保持旧消息在前，新消息在后的顺序）
        allMessages = [...messageList, ...allMessages];
        // 如果已经获取了足够多的消息，停止滚动
        if (allMessages.length >= message_num) {
            break;
        }
        await Tools.UI.swipe(100, 300, 100, 1000);
        await Tools.System.sleep(500);
    }
    // 删除重复的消息，但保持原始顺序
    const uniqueMessages = [];
    const seen = new Set();
    for (const msg of allMessages) {
        const key = `${msg.message}-${msg.sender}-${msg.time}`;
        if (!seen.has(key)) {
            seen.add(key);
            uniqueMessages.push(msg);
        }
    }
    // 填充空白时间
    let lastTime = "";
    for (const msg of uniqueMessages) {
        if (msg.time == "") {
            msg.time = lastTime;
        }
        lastTime = msg.time;
    }
    // 返回符合数量要求的消息列表（保持原始顺序）
    return uniqueMessages.slice(0, message_num);
}
async function reply(params) {
    // 提取参数
    const message = params.message || "";
    const click_send = params.click_send || false;
    await Tools.UI.setText(message);
    await Tools.System.sleep(500);
    if (click_send) {
        await Tools.UI.clickElement({
            resourceId: "com.tencent.mobileqq:id/send_btn",
            index: "0"
        });
    }
    return true;
}
async function find_and_reply(params) {
    // 提取参数
    const message = params.message || "";
    const user_name = params.user_name || "";
    const user_type = params.user_type || "contacts";
    const click_send = params.click_send || false;
    let result = await find_user({ user_name: user_name, user_type: user_type });
    if (!result) {
        return false;
    }
    await Tools.System.sleep(1000);
    return await reply({ message: message, click_send: click_send });
}
async function find_user(params) {
    // 提取参数
    const user_name = params.user_name || "";
    const user_type = params.user_type || "contacts";
    // 检查是否已在QQ中
    let pageInfo = await Tools.UI.getPageInfo();
    // 如果不在QQ中，启动QQ
    if (!pageInfo.packageName.includes("com.tencent.mobileqq")) {
        await Tools.System.startApp("com.tencent.mobileqq", "com.tencent.mobileqq.activity.SplashActivity");
        await Tools.System.sleep(1000);
        pageInfo = await Tools.UI.getPageInfo();
    }
    // 如果不在SplashActivity中，返回主界面
    if (!pageInfo.activityName.includes("com.tencent.mobileqq.activity.SplashActivity")) {
        for (let i = 0; i < 2; i++) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(500);
        }
        pageInfo = await Tools.UI.getPageInfo();
    }
    // 如果还是不在SplashActivity中，重启应用，重新调用函数
    if (!pageInfo.activityName.includes("com.tencent.mobileqq.activity.SplashActivity")) {
        await Tools.System.stopApp("com.tencent.mobileqq");
        await Tools.System.sleep(1000);
        return await find_user({ user_name: user_name, user_type: user_type });
    }
    //向上滑动
    if ((await Tools.UI.findElement({ resourceId: "com.tencent.mobileqq:id/wsg" }))
        .uiElements.children.length == 0) {
        await Tools.System.stopApp("com.tencent.mobileqq");
        await Tools.System.sleep(1000);
        return await find_user({ user_name: user_name, user_type: user_type });
    }
    await Tools.System.sleep(500);
    // 点击搜索框
    await Tools.UI.clickElement({
        resourceId: "com.tencent.mobileqq:id/wsg",
        index: "0"
    });
    await Tools.System.sleep(500);
    let firstTarget = undefined;
    let tryMax = 0;
    while (tryMax < 2) {
        tryMax++;
        // 输入搜索内容
        await Tools.UI.setText(user_name);
        await Tools.System.sleep(2000 * tryMax);
        await close_keyboard();
        const currentPage = await UINode.getCurrentPage();
        const searchResult = currentPage.findById('com.tencent.mobileqq:id/eap');
        let isNeedToCatch = false;
        for (const child of searchResult.children) {
            if (isNeedToCatch) {
                firstTarget = child.findById('com.tencent.mobileqq:id/image');
                break;
            }
            const title = child.findById('com.tencent.mobileqq:id/title');
            if (title) {
                if (user_type == "contacts" && title.text == "联系人") {
                    isNeedToCatch = true;
                }
                else if (user_type == "groups" && title.text == "群聊") {
                    isNeedToCatch = true;
                }
            }
        }
        if (firstTarget) {
            await firstTarget.click();
            return true;
        }
    }
    return false;
}
async function find_and_get_history(params) {
    const user_name = params.user_name || "";
    const user_type = params.user_type || "contacts";
    const message_num = params.message_num || 10;
    let result = await find_user({ user_name: user_name, user_type: user_type });
    if (!result) {
        return undefined;
    }
    await Tools.System.sleep(1000);
    return await get_history({ message_num: message_num });
}
async function main() {
    // let result = await find_and_reply({ message: "你好你好！我是OPERIT，很高兴认识你！", user_name: "韩韩韩", user_type: "contacts", click_send: true });
    await find_user({ user_name: "Wind", user_type: "contacts" });
    // await find_user({ user_name: "Dec", user_type: "groups" });
    await Tools.System.sleep(1000);
    let result = await get_history({ message_num: 10 });
    console.log(result);
    complete({
        success: result
    });
}
async function wrap_bool(func, params, successMessage, failMessage, additionalMessage = "") {
    if (await func(params)) {
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
}
async function wrap_data(func, params, successMessage, failMessage, additionalMessage = "") {
    const result = await func(params);
    complete({
        success: true,
        message: successMessage,
        additionalMessage: additionalMessage,
        data: result
    });
}
exports.main = main;
exports.reply = async (params) => await wrap_bool(reply, params, "发送成功", "发送失败");
exports.find_user = async (params) => await wrap_bool(find_user, params, "查找成功", "查找失败，停留在查找页面", (await UINode.getCurrentPage()).toFormattedString());
exports.find_and_reply = async (params) => await wrap_bool(find_and_reply, params, "发送成功", "发送失败，停留在查找页面", (await UINode.getCurrentPage()).toFormattedString());
exports.get_history = async (params) => await wrap_data(get_history, params, "获取历史消息成功", "获取历史消息失败");
exports.find_and_get_history = async (params) => await wrap_data(find_and_get_history, params, "获取历史消息成功", "获取历史消息失败");
