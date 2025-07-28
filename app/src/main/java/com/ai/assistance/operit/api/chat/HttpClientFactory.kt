package com.ai.assistance.operit.api.chat

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * A factory for creating and managing a shared OkHttpClient instance.
 * Using a shared client allows for efficient reuse of connections and resources.
 */
object HttpClientFactory {

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Increase the connection timeout to handle slow networks better.
            .connectTimeout(60, TimeUnit.SECONDS)
            // Set long read/write timeouts for streaming responses.
            .readTimeout(1000, TimeUnit.SECONDS)
            .writeTimeout(1000, TimeUnit.SECONDS)
            // Use a connection pool to reuse connections, improving latency and reducing resource usage.
            // Increased idle connections to 10 from the default of 5.
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            // Explicitly enable HTTP/2, which is the default but good to have declared.
            // OkHttp will use HTTP/2 if the server supports it, falling back to HTTP/1.1.
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
} 