package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import android.os.Environment
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** LocalWebServer - 基于NanoHTTPD的本地Web服务器 用于显示工作空间目录中的文件 */
class LocalWebServer
private constructor(
        private val port: Int,
        private val context: Context,
        private var chatId: String = "default"
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "LocalWebServer"

        // 公共常量
        const val DEFAULT_PORT = 8080

        // 单例实例
        @Volatile private var INSTANCE: LocalWebServer? = null

        // 获取单例实例的方法
        fun getInstance(context: Context, port: Int = DEFAULT_PORT): LocalWebServer {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: LocalWebServer(port, context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }

        // 工作空间的基础路径
        fun getWorkspacePath(chatId: String): String {
            val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return "$downloadDir/Operit/workspace/$chatId"
        }

        // 确保工作空间目录存在
        fun ensureWorkspaceDirExists(chatId: String): String {
            val workspacePath = getWorkspacePath(chatId)
            val workspaceDir = File(workspacePath)
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs()
            }
            return workspacePath
        }
    }

    private val workspacePath: String
        get() = getWorkspacePath(chatId)

    private val isServerRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var currentChatId: String? = null

    // 在启动前确保工作区目录存在
    @Throws(IOException::class)
    override fun start() {
        ensureWorkspaceDirExists(chatId)
        super.start()
        isServerRunning.set(true)
        Log.d(TAG, "本地服务器已启动，端口: $port，工作空间: $workspacePath")
    }

    override fun stop() {
        super.stop()
        isServerRunning.set(false)
        Log.d(TAG, "本地服务器已停止")
    }

    fun updateChatId(newChatId: String) {
        chatId = newChatId
        ensureWorkspaceDirExists(chatId)
        Log.d(TAG, "工作空间已更新: $workspacePath")
    }

    fun isRunning(): Boolean {
        return isServerRunning.get()
    }

    private fun handleRequest(socket: Socket) {
        // 请求处理逻辑...
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "收到请求: ${session.uri}")

        var uri = session.uri
        // 处理根路径，默认显示index.html
        if (uri == "/" || uri.isEmpty()) {
            uri = "/index.html"
        }

        // 检查请求的文件是否在工作空间内
        val requestedFile = File(workspacePath, uri.substring(1))

        // 如果index.html不存在，创建一个默认的欢迎页面
        if (uri == "/index.html" && !requestedFile.exists()) {
            return createDefaultIndexHtml()
        }

        try {
            if (!requestedFile.exists()) {
                Log.w(TAG, "文件不存在: $requestedFile")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "文件不存在")
            }

            if (!isInWorkspace(requestedFile)) {
                Log.w(TAG, "试图访问工作空间外的文件: $requestedFile")
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "禁止访问")
            }

            // 确定MIME类型
            val mimeType = getMimeTypeForFile(uri)

            // 返回文件内容
            val fileInputStream = FileInputStream(requestedFile)
            return newChunkedResponse(Response.Status.OK, mimeType, fileInputStream)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "文件未找到: ${e.message}")
            return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "文件不存在: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "服务器错误: ${e.message}")
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "服务器错误: ${e.message}"
            )
        }
    }

    // 检查文件是否在工作空间内
    private fun isInWorkspace(file: File): Boolean {
        return try {
            val workspaceDir = File(workspacePath)
            file.canonicalPath.startsWith(workspaceDir.canonicalPath)
        } catch (e: IOException) {
            Log.e(TAG, "检查文件路径时出错: ${e.message}")
            false
        }
    }

    // 创建默认的欢迎页面
    private fun createDefaultIndexHtml(): Response {
        val html =
                """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Operit Web 工作空间</title>
            <style>
                body {
                    font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
                    line-height: 1.6;
                    color: #333;
                    max-width: 800px;
                    margin: 0 auto;
                    padding: 20px;
                }
                h1 {
                    color: #2c3e50;
                    border-bottom: 2px solid #eaecef;
                    padding-bottom: 10px;
                }
                code {
                    background-color: #f8f8f8;
                    padding: 3px 5px;
                    border-radius: 3px;
                    font-family: Consolas, Monaco, 'Andale Mono', monospace;
                }
                .tip {
                    background-color: #f0f7ff;
                    border-left: 4px solid #42b983;
                    padding: 12px 16px;
                    margin: 20px 0;
                    border-radius: 0 4px 4px 0;
                }
            </style>
        </head>
        <body>
            <h1>Operit Web 工作空间</h1>
            <p>欢迎使用 Operit Web 工作空间！这是聊天 ID <strong>$chatId</strong> 的专属网页环境。</p>
            
            <div class="tip">
                <p>目前还没有任何网页内容。请要求 AI 创建一个网站或 Web 应用，AI 将会：</p>
                <ol>
                    <li>创建 HTML、CSS 和 JavaScript 文件</li>
                    <li>生成 <code>index.html</code> 作为主页</li>
                    <li>您可以随时按下 Web 按钮查看实时结果</li>
                </ol>
            </div>
            
            <p>这个页面会自动刷新，当有新内容时将会显示。</p>
            <script>
                // 每5秒自动刷新一次
                setTimeout(() => {
                    window.location.reload();
                }, 5000);
            </script>
        </body>
        </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
