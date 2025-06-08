package com.ai.assistance.operit.util.Stream

import android.util.Log
import com.ai.assistance.operit.util.Stream.plugins.StreamPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch

/** 日志工具类，统一Stream相关日志 */
object StreamLogger {
    private const val TAG = "StreamFramework"
    private var enabled = true
    private var verboseEnabled = false

    /** 启用或禁用日志 */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** 启用或禁用详细日志 */
    fun setVerboseEnabled(enabled: Boolean) {
        this.verboseEnabled = enabled
    }

    /** 记录调试信息 */
    fun d(component: String, message: String) {
        if (enabled) {
            Log.d(TAG, "[$component] $message")
        }
    }

    /** 记录信息 */
    fun i(component: String, message: String) {
        if (enabled) {
            Log.i(TAG, "[$component] $message")
        }
    }

    /** 记录详细信息 */
    fun v(component: String, message: String) {
        if (enabled && verboseEnabled) {
            Log.v(TAG, "[$component] $message")
        }
    }

    /** 记录警告 */
    fun w(component: String, message: String) {
        if (enabled) {
            Log.w(TAG, "[$component] $message")
        }
    }

    /** 记录错误 */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) {
                Log.e(TAG, "[$component] $message", throwable)
            } else {
                Log.e(TAG, "[$component] $message")
            }
        }
    }
}

/** Stream接口，类似于Kotlin Flow，用于表示异步计算的数据流 */
interface Stream<T> {
    /** 收集Stream发出的值 */
    suspend fun collect(collector: StreamCollector<T>)

    /** Stream的标准收集方法，简化使用 */
    suspend fun collect(onEach: suspend (T) -> Unit) {
        StreamLogger.d("Stream", "开始收集Stream元素")
        collect(
                object : StreamCollector<T> {
                    override suspend fun emit(value: T) {
                        StreamLogger.v("Stream", "收集到元素: $value")
                        onEach(value)
                    }
                }
        )
        StreamLogger.d("Stream", "完成Stream收集")
    }
}

/** Stream收集器接口，类似于FlowCollector */
interface StreamCollector<in T> {
    /** 发射一个值到Stream */
    suspend fun emit(value: T)
}

/** Flow到Stream的适配器，允许将Kotlin Flow转换为Stream */
class FlowAsStream<T>(private val flow: Flow<T>) : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        StreamLogger.d("FlowAsStream", "开始从Flow收集元素")
        flow.collect { value ->
            StreamLogger.v("FlowAsStream", "从Flow收集到元素: $value")
            collector.emit(value)
        }
        StreamLogger.d("FlowAsStream", "完成从Flow收集元素")
    }
}

/** Stream到Flow的适配器，允许将Stream转换为Kotlin Flow */
class StreamAsFlow<T>(private val stream: Stream<T>) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        StreamLogger.d("StreamAsFlow", "开始转换Stream到Flow")
        stream.collect { value ->
            StreamLogger.v("StreamAsFlow", "转换元素到Flow: $value")
            collector.emit(value)
        }
        StreamLogger.d("StreamAsFlow", "完成转换Stream到Flow")
    }
}

/** 将Flow转换为Stream */
fun <T> Flow<T>.asStream(): Stream<T> = FlowAsStream(this)

/** 将Stream转换为Flow */
fun <T> Stream<T>.asFlow(): Flow<T> = StreamAsFlow(this)

/** 在指定的协程作用域中启动Stream收集 */
fun <T> Stream<T>.launchIn(scope: CoroutineScope, onEach: suspend (T) -> Unit = {}): Job {
    StreamLogger.d("Stream.launchIn", "在协程作用域中启动Stream收集")
    return scope.launch {
        collect { value ->
            StreamLogger.v("Stream.launchIn", "收集到元素: $value")
            onEach(value)
        }
    }
}

/**
 * 使用一组插件将字符流分割成不同的组。
 *
 * 该函数会根据插件的匹配状态将字符流划分为不同的组：
 * 1. 匹配到插件的字符会分到对应插件组
 * 2. 未匹配到任何插件的字符会归为默认文本组（tag为null）
 *
 * @param plugins 用于分割流的插件列表
 * @return 返回一个包含分组后结果的Stream
 */
fun Stream<Char>.splitBy(plugins: List<StreamPlugin>): Stream<StreamGroup<StreamPlugin?>> {
    return stream {
        // 重置所有插件到初始状态
        plugins.forEach { it.initPlugin() }

        var defaultTextBuffer = mutableListOf<Char>()
        var activePlugin: StreamPlugin? = null
        var activeGroupBuffer = mutableListOf<Char>()
        var tryingPlugin: StreamPlugin? = null
        var tryingBuffer = mutableListOf<Char>()

        // 内部函数：将缓冲区中的字符转换为字符串并发送到下游
        suspend fun emitDefaultText() {
            if (defaultTextBuffer.isNotEmpty()) {
                val text = defaultTextBuffer.joinToString("")
                emit(StreamGroup(null, streamOf(text)))
                defaultTextBuffer.clear()
            }
        }

        // 内部函数：将活动插件的缓冲区作为一个组发送到下游
        suspend fun emitActiveGroup() {
            val plugin = activePlugin
            if (activeGroupBuffer.isNotEmpty() && plugin != null) {
                val text = activeGroupBuffer.joinToString("")
                emit(StreamGroup(plugin, streamOf(text)))
                activeGroupBuffer.clear()
            }
        }

        // 收集源字符流
        this@splitBy.collect { char ->
            val currentActivePlugin = activePlugin
            val currentTryingPlugin = tryingPlugin

            if (currentActivePlugin != null) {
                // --- 状态：处理中 ---
                // 我们在一个插件识别的块内（例如，在XML标签内）
                activeGroupBuffer.add(char)
                currentActivePlugin.processChar(char)

                if (!currentActivePlugin.isProcessing) {
                    // 插件已完成处理，刷新组并返回到空闲状态
                    emitActiveGroup()
                    activePlugin = null
                }
            } else if (currentTryingPlugin != null) {
                // --- 状态：尝试中 ---
                // 一个插件正在尝试匹配模式
                tryingBuffer.add(char)
                currentTryingPlugin.processChar(char)

                if (currentTryingPlugin.isProcessing) {
                    // --- 转换：尝试中 -> 处理中 ---
                    // 尝试中的插件确认了匹配
                    emitDefaultText() // 先完成之前的默认文本

                    activePlugin = currentTryingPlugin
                    activeGroupBuffer.addAll(tryingBuffer)

                    tryingPlugin = null
                    tryingBuffer.clear()
                } else if (!currentTryingPlugin.isTryingToStart) {
                    // --- 转换：尝试中 -> 空闲 ---
                    // 尝试中的插件未能匹配
                    // 将缓冲区作为默认文本处理
                    defaultTextBuffer.addAll(tryingBuffer)

                    val pluginThatFailed = currentTryingPlugin
                    tryingPlugin = null
                    tryingBuffer.clear()
                    pluginThatFailed.reset() // 重置失败的插件，为下次尝试做准备
                }
            } else {
                // --- 状态：空闲 ---
                // 没有活动或尝试中的插件，我们处于默认文本模式
                var foundTrying = false

                for (plugin in plugins) {
                    plugin.processChar(char)

                    if (plugin.isTryingToStart) {
                        // --- 转换：空闲 -> 尝试中 ---
                        // 插件开始匹配
                        tryingPlugin = plugin
                        tryingBuffer.add(char)
                        foundTrying = true
                        break // 锁定在第一个开始尝试的插件上
                    }
                }

                if (!foundTrying) {
                    // 没有插件开始尝试，所以这只是默认文本
                    defaultTextBuffer.add(char)
                }
            }
        }

        // 流结束后，刷新所有剩余文本
        emitDefaultText()
        emitActiveGroup()

        // 处理tryingBuffer中可能残留的内容
        if (tryingBuffer.isNotEmpty()) {
            defaultTextBuffer.addAll(tryingBuffer)
            emitDefaultText()
        }
    }
}
