package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.graphics.Canvas
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

/**
 * 代码文本编辑器
 */
class CodeText : ColorsText {
    // 代码解析器
    private lateinit var codeParser: CodeParser
    
    // 代码补全接口
    interface CompletionMsg {
        fun change(input: String)
        fun close()
    }
    
    // 代码补全实现
    var completionHelper: CompletionMsg? = null
    
    // 父视图尺寸
    private var pWidth = 0
    private var pHeight = 0
    
    fun setPHeight(pHeight: Int) {
        this.pHeight = pHeight
    }
    
    fun getPHeight(): Int {
        return pHeight
    }
    
    fun setPWidth(pWidth: Int) {
        this.pWidth = pWidth
    }
    
    fun getPWidth(): Int {
        return pWidth
    }
    
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        
        // 处理代码补全
        completionHelper?.let { helper ->
            val ssb = text as? SpannableStringBuilder ?: return
            val now = selEnd
            val sb = StringBuilder()
            var choose = false
            
            var currentPos = now
            while (true) {
                if (currentPos - 1 < 0 || currentPos - 1 >= ssb.length) return
                val c = ssb[currentPos - 1]
                if (c == '"') {
                    choose = true
                }
                if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == ':' || c == '.') {
                    sb.insert(0, c)
                } else {
                    break
                }
                currentPos--
            }
            
            if (sb.isNotEmpty() || choose) {
                helper.change(sb.toString())
            } else {
                helper.close()
            }
        }
    }
    
    fun setLanguage(language: String) {
        codeParser.setLanguage(language)
    }

    /**
     * 设置语法检查
     */
    fun setCheckGrammar(c: GrammarCheck.Check) {
        check = c
    }
    
    override fun getParentWidth(): Int {
        return pWidth
    }
    
    override fun getParentHeight(): Int {
        return pHeight
    }
    
    constructor(context: Context) : super(context) {
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }
    
    private fun init() {
        // 初始化代码解析器
        codeParser = CodeParser(this)
        
        // 动态解析代码更新文字颜色
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // 解析代码
                codeParser.parse(start, before, count)
            }
            
            override fun afterTextChanged(s: Editable) {}
        })
    }
    
    // 记录绘制用时
    private var drawUseTimeCount: Long = 0
    // 记录最后一次回车时间
    private var lastEnterTime: Long = 0
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
    
    /**
     * 记录用户操作的键盘，避免一次按键，多次输入
     */
    private var defaultDeviceId = -1000
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // 处理设备ID
        val deviceId = event.deviceId
        if (defaultDeviceId == -1000) {
            defaultDeviceId = deviceId
        } else {
            if (defaultDeviceId == -1) {
                if (deviceId != defaultDeviceId) {
                    defaultDeviceId = deviceId
                    return true
                }
            }
        }
        
        if (deviceId != defaultDeviceId) {
            return true
        }
        
        // 处理Tab键
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            val start = selectionStart
            val end = selectionEnd
            if (start == end) {
                text?.insert(start, "  ")
            }
            return true
        }
        
        // 处理回车键
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            lastEnterTime = System.currentTimeMillis()
            val layout = layout
            if (layout == null) {
                return true
            }
            val start = selectionStart
            val end = selectionEnd
            if (start == end) {
                val line = layout.getLineForOffset(start)
                val startPos = layout.getLineStart(line)
                var space = ""
                for (i in startPos until (text?.length ?: 0)) {
                    if (text?.get(i) == ' ') {
                        space += " "
                    } else {
                        break
                    }
                }
                text?.insert(start, "\n$space")
                if (start > 0) {
                    val c = text?.get(start - 1)
                    if (c == '{' || c == '[' || c == '(') {
                        val newStart = selectionStart
                        text?.insert(newStart, "  ")
                        val afterIndentStart = selectionStart
                        text?.insert(afterIndentStart, "\n$space")
                        setSelection(afterIndentStart)
                    }
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
} 