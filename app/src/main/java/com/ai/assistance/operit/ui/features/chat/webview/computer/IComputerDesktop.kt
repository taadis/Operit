package com.ai.assistance.operit.ui.features.chat.webview.computer

interface IComputerDesktop {
    /** 获取当前所有打开的标签页信息 */
    fun getTabs(): List<ComputerTab>

    /** 切换到指定的标签页 */
    fun switchToTab(tabId: String)
    fun switchToTab(index: Int)

    /** 打开一个新的桌面主页标签 */
    fun openDesktop()

    /** 打开一个新的浏览器标签，可以指定URL */
    fun openBrowser(url: String? = null)

    /** 获取当前激活标签页的完整HTML内容 */
    suspend fun getCurrentPageHtml(): String?

    /** 在当前激活的标签页中，根据CSS选择器点击一个元素 */
    suspend fun clickElement(selector: String): Pair<Boolean, String>

    /** 在当前激活的标签页中，滚动页面 */
    fun scrollBy(x: Int, y: Int)

    /** 在当前激活的标签页中，向指定选择器的输入框输入文本 */
    suspend fun inputText(selector: String, text: String): Pair<Boolean, String>

    /** 关闭指定的标签页 */
    fun closeTab(tabId: String)
    fun closeTab(index: Int)

    /** 等待当前页面加载完成 */
    suspend fun awaitPageLoaded(timeoutMillis: Long): Boolean

    /** 返回到前一个页面 */
    fun goBack()
} 