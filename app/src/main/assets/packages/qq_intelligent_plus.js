/*
METADATA
{
  // QQ Intelligent Assistant Package
  name: qq_intelligent_plus
  description: 智能QQ助手：提供QQ消息回复、消息获取、用户查找等功能，通过UI自动化操作实现

  // Tools in this package
  tools: [
    {
      name: reply
      description: 打开应用，基于搜索用户，点进聊天输入消息发送的自动化操作
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
    }
  ],
  "category": "UI_AUTOMATION"
}
*/
async function close_keyboard() {
    await Tools.UI.pressKey("KEYCODE_BACK");
}
async function reply(params) {
    // 提取参数
    const message = params.message || "";
    const user_name = params.user_name || "";
    const user_type = params.user_type || "contacts";
    const click_send = params.click_send || false;
    await find_user({ user_name: user_name, user_type: user_type });
    await Tools.UI.setText(message);
    await Tools.System.sleep(500);
    if (click_send) {
        await Tools.UI.clickElement({
            resourceId: "com.tencent.mobileqq:id/send_btn",
            index: "0"
        });
    }
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
        for (let i = 0; i < 3; i++) {
            await Tools.UI.pressKey("KEYCODE_BACK");
            await Tools.System.sleep(100);
        }
        pageInfo = await Tools.UI.getPageInfo();
    }
    // 如果还是不在SplashActivity中，重启应用，重新调用函数
    if (!pageInfo.activityName.includes("com.tencent.mobileqq.activity.SplashActivity")) {
        await Tools.System.stopApp("com.tencent.mobileqq");
        await Tools.System.sleep(1000);
        await find_user({ user_name: user_name, user_type: user_type });
        return;
    }
    //向上滑动
    if ((await Tools.UI.findElement({ resourceId: "com.tencent.mobileqq:id/wsg" }))
        .uiElements.children.length == 0) {
        await Tools.System.stopApp("com.tencent.mobileqq");
        await Tools.System.sleep(1000);
        await find_user({ user_name: user_name, user_type: user_type });
        return;
    }
    await Tools.System.sleep(500);
    // 点击搜索框
    await Tools.UI.clickElement({
        resourceId: "com.tencent.mobileqq:id/wsg",
        index: "0"
    });
    await Tools.System.sleep(500);
    // 输入搜索内容
    await Tools.UI.setText(user_name);
    await Tools.System.sleep(2000);
    await close_keyboard();
    const currentPage = await UINode.getCurrentPage();
    const searchResult = currentPage.findById('com.tencent.mobileqq:id/eap');
    let firstTarget = undefined;
    let isNeedToCatch = false;
    for (const child of searchResult.children) {
        if (isNeedToCatch) {
            firstTarget = child.findById('com.tencent.mobileqq:id/bgt');
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
        complete({
            success: true,
            message: "搜索完成"
        });
    }
    else {
        complete({
            success: false,
            message: "未找到目标"
        });
    }
}
async function main() {
    await reply({ message: "你好你好！我是OPERIT，很高兴认识你！", user_name: "韩韩韩", user_type: "contacts", click_send: true });
    // await find_user({ user_name: "Wind", user_type: "contacts" });
}
exports.main = main;
exports.reply = reply;
exports.find_user = find_user;
