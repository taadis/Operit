package org.jetbrains.kotlinx.mcp.shared

import org.jetbrains.kotlinx.mcp.JSONRPCMessage
import java.nio.charset.StandardCharsets
import kotlinx.serialization.encodeToString

/**
 * Buffers a continuous stdio stream into discrete JSON-RPC messages.
 */
public class ReadBuffer {
    private val buffer = StringBuilder()

    public fun append(chunk: ByteArray) {
        buffer.append(String(chunk, StandardCharsets.UTF_8))
    }

    public fun readMessage(): JSONRPCMessage? {
        if (buffer.isEmpty()) return null
        
        val lfIndex = buffer.indexOf('\n')
        val line = when {
            lfIndex == -1 -> return null
            lfIndex == 0 -> {
                buffer.delete(0, 1)
                ""
            }
            else -> {
                var endIndex = lfIndex
                var skipChars = 1
                
                if (lfIndex > 0 && buffer[lfIndex - 1] == '\r') {
                    endIndex = lfIndex - 1
                    skipChars = 2
                }
                
                val string = buffer.substring(0, endIndex)
                buffer.delete(0, lfIndex + 1)
                string
            }
        }
        
        return deserializeMessage(line)
    }

    public fun clear() {
        buffer.clear()
    }
}

internal fun deserializeMessage(line: String): JSONRPCMessage {
    return McpJson.decodeFromString(line)
}

internal fun serializeMessage(message: JSONRPCMessage): String {
    return McpJson.encodeToString(message) + "\n"
}

