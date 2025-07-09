package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.getThemeForLanguage

/**
 * 代码编辑器组件，支持语法高亮、行号显示和代码补全
 * @param code 初始代码内容
 * @param language 代码语言，用于语法高亮
 * @param onCodeChange 代码变更回调
 * @param modifier 修饰符
 * @param readOnly 是否只读模式
 * @param showLineNumbers 是否显示行号
 * @param enableCompletion 是否启用代码补全
 */
@Composable
fun CodeEditor(
    code: String,
    language: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    enableCompletion: Boolean = true,
) {
    val theme = getThemeForLanguage(language)

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues).fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    NativeCodeEditor(context).apply {
                        this.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.setLanguage(language)
                        this.isEnabled = !readOnly
                        this.setText(code)
                        this.setOnTextChangedListener { newText ->
                            if (newText != code) {
                                onCodeChange(newText)
                    }
                }
                    }
                },
                update = { view ->
                    view.setLanguage(language)
                    view.isEnabled = !readOnly
                    if (view.getText() != code) {
                        view.setText(code)
                    }
                }
                    )
                }
            }
        }

/**
 * 原生代码编辑器视图
 */
class NativeCodeEditor : ViewGroup {
    private val codePane: CodePane
    
    private var onTextChangedListener: ((String) -> Unit)? = null
    
    constructor(context: Context) : super(context) {
        codePane = CodePane(context)
        init()
    }
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        codePane = CodePane(context, attrs)
        init()
                }
    
    private fun init() {
        addView(codePane)
        
        // 添加文本变化监听
        codePane.getCodeText().addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                onTextChangedListener?.invoke(s?.toString() ?: "")
            }
        })
    }
    
    /**
     * 设置文本变化监听器
     */
    fun setOnTextChangedListener(listener: (String) -> Unit) {
        onTextChangedListener = listener
    }
    
    /**
     * 设置语言
     */
    fun setLanguage(language: String) {
        codePane.setLanguage(language)
    }

    /**
     * 设置文本内容
     */
    fun setText(text: String) {
        codePane.getCodeText().setText(text)
    }
    
    /**
     * 获取文本内容
     */
    fun getText(): String {
        return codePane.getCodeText().text.toString()
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        
        codePane.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        
        setMeasuredDimension(width, height)
    }
    
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        codePane.layout(0, 0, r - l, b - t)
    }
}

