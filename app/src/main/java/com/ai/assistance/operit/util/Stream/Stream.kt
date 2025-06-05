package com.ai.assistance.operit.util.Stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch

/** Stream接口，类似于Kotlin Flow，用于表示异步计算的数据流 */
interface Stream<T> {
    /** 收集Stream发出的值 */
    suspend fun collect(collector: StreamCollector<T>)

    /** Stream的标准收集方法，简化使用 */
    suspend fun collect(onEach: suspend (T) -> Unit) {
        collect(
                object : StreamCollector<T> {
                    override suspend fun emit(value: T) {
                        onEach(value)
                    }
                }
        )
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
        flow.collect { value -> collector.emit(value) }
    }
}

/** Stream到Flow的适配器，允许将Stream转换为Kotlin Flow */
class StreamAsFlow<T>(private val stream: Stream<T>) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        stream.collect { value -> collector.emit(value) }
    }
}

/** 将Flow转换为Stream */
fun <T> Flow<T>.asStream(): Stream<T> = FlowAsStream(this)

/** 将Stream转换为Flow */
fun <T> Stream<T>.asFlow(): Flow<T> = StreamAsFlow(this)

/** 在指定的协程作用域中启动Stream收集 */
fun <T> Stream<T>.launchIn(scope: CoroutineScope, onEach: suspend (T) -> Unit = {}): Job {
    return scope.launch { collect { value -> onEach(value) } }
}
