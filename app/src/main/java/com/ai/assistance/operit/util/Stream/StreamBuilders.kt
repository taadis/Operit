package com.ai.assistance.operit.util.Stream

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * 创建一个空的Stream
 */
fun <T> emptyStream(): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        // 不发射任何值
    }
}

/**
 * 从单个值创建Stream
 */
fun <T> streamOf(value: T): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        collector.emit(value)
    }
}

/**
 * 从多个值创建Stream
 */
fun <T> streamOf(vararg values: T): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        for (value in values) {
            collector.emit(value)
        }
    }
}

/**
 * 从集合创建Stream
 */
fun <T> Collection<T>.asStream(): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        for (item in this@asStream) {
            collector.emit(item)
        }
    }
}

/**
 * 从序列创建Stream
 */
fun <T> Sequence<T>.asStream(): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        for (item in this@asStream) {
            collector.emit(item)
        }
    }
}

/**
 * 通过调用构建器创建Stream
 */
fun <T> stream(block: suspend StreamCollector<T>.() -> Unit): Stream<T> = object : Stream<T> {
    override suspend fun collect(collector: StreamCollector<T>) {
        block(collector)
    }
}

/**
 * 创建固定间隔发射整数的Stream
 */
fun intervalStream(period: Duration, initialDelay: Duration = Duration.ZERO): Stream<Long> = stream {
    var count = 0L
    delay(initialDelay)
    while (true) {
        emit(count++)
        delay(period)
    }
}

/**
 * 创建固定次数的Stream
 */
fun rangeStream(start: Int, count: Int): Stream<Int> = stream {
    for (i in start until start + count) {
        emit(i)
    }
}

/**
 * 从异常创建Stream
 */
fun <T> streamError(exception: Throwable): Stream<T> = stream {
    throw exception
} 