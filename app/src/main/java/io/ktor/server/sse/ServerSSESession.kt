package io.ktor.server.sse

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Server-side SSE session
 */
public class ServerSSESession(
    private val call: ApplicationCall,
    private val responseChannel: ByteWriteChannel,
    public val coroutineContext: CoroutineContext
) {
    /**
     * Send an SSE event with the specified event type and data
     */
    public suspend fun send(
        event: String? = null,
        data: String? = null,
        id: String? = null,
        retry: Long? = null
    ) {
        val builder = StringBuilder()

        if (id != null) {
            builder.append("id: ").append(id).append("\n")
        }

        if (event != null) {
            builder.append("event: ").append(event).append("\n")
        }

        if (data != null) {
            for (line in data.lines()) {
                builder.append("data: ").append(line).append("\n")
            }
        }

        if (retry != null) {
            builder.append("retry: ").append(retry).append("\n")
        }

        builder.append("\n")
        responseChannel.writeStringUtf8(builder.toString())
        responseChannel.flush()
    }

    /**
     * Close the SSE session
     */
    public suspend fun close() {
        responseChannel.close()
    }
}

/**
 * SSE plugin for Ktor
 */
public class SSE {
    public companion object Plugin : io.ktor.server.application.Plugin<io.ktor.server.application.Application, Unit, SSE> {
        override val key: io.ktor.util.AttributeKey<SSE> = io.ktor.util.AttributeKey("SSE")

        override fun install(pipeline: io.ktor.server.application.Application, configure: Unit.() -> Unit): SSE {
            return SSE()
        }
    }
}

/**
 * Define an SSE route handler
 */
public fun Route.sse(
    path: String, 
    body: suspend ServerSSESession.() -> Unit
) {
    // Go back to using get, which is more straightforward
    get(path) {
        // Set cache control header
        call.response.cacheControl(CacheControl.NoCache(null))
        
        // Create a response writer that returns the ByteWriteChannel
        val channel = ByteChannel()
        
        // Launch the SSE response in a separate coroutine
        launch(coroutineContext) {
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                // Create SSE session with the channel
                val session = ServerSSESession(call, channel, coroutineContext)
                // Call the body function
                session.body()
                // Close the channel when done
                channel.close()
            }
        }
    }
} 