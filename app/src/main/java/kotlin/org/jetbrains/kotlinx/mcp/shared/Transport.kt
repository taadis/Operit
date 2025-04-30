package org.jetbrains.kotlinx.mcp.shared

import org.jetbrains.kotlinx.mcp.JSONRPCMessage

/**
 * Interface for MCP transports, which handle the sending and receiving of JSON-RPC messages.
 */
public interface Transport {
    /**
     * Callback to invoke when the transport is closed.
     */
    public var onClose: (() -> Unit)?
    
    /**
     * Callback to invoke when an error occurs.
     */
    public var onError: ((Throwable) -> Unit)?
    
    /**
     * Callback to invoke when a message is received.
     */
    public var onMessage: (suspend (JSONRPCMessage) -> Unit)?
    
    /**
     * Start the transport. This should be called before any other method.
     */
    public suspend fun start()
    
    /**
     * Send a message through the transport.
     */
    public suspend fun send(message: JSONRPCMessage)
    
    /**
     * Close the transport.
     */
    public suspend fun close()
}
