package io.ktor.client.plugins.sse

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.CancellationException

/**
 * SSE event representation
 */
public data class SSEEvent(
    val id: String? = null,
    val event: String? = null,
    val data: String? = null,
    val retry: Long? = null
)

/**
 * Client-side SSE session
 */
public class ClientSSESession(
    public val call: HttpResponse,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val events = Channel<SSEEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var job: Job? = null

    /**
     * Flow of incoming SSE events
     */
    public val incoming: Flow<SSEEvent> = events.receiveAsFlow()
    
    /**
     * Start processing SSE events
     */
    public fun start() {
        job = scope.launch {
            processEvents()
        }
    }

    private suspend fun processEvents() {
        try {
            val channel = call.bodyAsChannel()
            val buffer = StringBuilder()
            
            var id: String? = null
            var event: String? = null
            var data = StringBuilder()
            var retry: Long? = null
            
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                
                if (line.isEmpty()) {
                    // Empty line means dispatch the event
                    if (data.isNotEmpty()) {
                        events.send(SSEEvent(id, event, data.toString(), retry))
                    }
                    
                    // Reset for next event
                    event = null
                    data = StringBuilder()
                    retry = null
                    continue
                }
                
                when {
                    line.startsWith("id:") -> {
                        id = line.substring(3).trim()
                    }
                    line.startsWith("event:") -> {
                        event = line.substring(6).trim()
                    }
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) {
                            data.append("\n")
                        }
                        data.append(line.substring(5).trim())
                    }
                    line.startsWith("retry:") -> {
                        retry = line.substring(6).trim().toLongOrNull()
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                events.close(e)
            }
        } finally {
            events.close()
        }
    }

    /**
     * Cancel the SSE session
     */
    public fun cancel() {
        job?.cancel()
        events.close()
    }

    /**
     * Close the SSE session
     */
    public suspend fun close() {
        cancel()
        job?.join()
    }
}

/**
 * Create an SSE session with the specified URL
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    reconnectionTime: Duration? = 3.seconds,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession {
    val response = this.request {
        url(urlString)
        method = io.ktor.http.HttpMethod.Get
        accept(io.ktor.http.ContentType.Text.EventStream)
        
        if (reconnectionTime != null) {
            headers.append("Connection", "keep-alive")
            headers.append("Cache-Control", "no-cache")
        }
        
        block()
    }
    
    val session = ClientSSESession(response)
    session.start()
    return session
}

/**
 * Create an SSE session with the specified request builder
 */
public suspend fun HttpClient.sseSession(
    reconnectionTime: Duration? = 3.seconds,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession {
    val response = this.request {
        method = io.ktor.http.HttpMethod.Get
        accept(io.ktor.http.ContentType.Text.EventStream)
        
        if (reconnectionTime != null) {
            headers.append("Connection", "keep-alive")
            headers.append("Cache-Control", "no-cache")
        }
        
        block()
    }
    
    val session = ClientSSESession(response)
    session.start()
    return session
} 