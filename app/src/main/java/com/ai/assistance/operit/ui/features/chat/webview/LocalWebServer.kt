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
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "LocalWebServer"

        // 公共常量
        const val DEFAULT_PORT = 8080

        private const val DEFAULT_INDEX_HTML_CONTENT = """
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
            <p>欢迎使用 Operit Web 工作空间！这是当前对话的专属网页环境。</p>
            
            <div class="tip">
                <p>目前还没有任何网页内容。请要求 AI 创建一个网站或 Web 应用，AI 将会：</p>
                <ol>
                    <li>创建 HTML、CSS 和 JavaScript 文件</li>
                    <li>生成 <code>index.html</code> 作为主页</li>
                    <li>您可以随时按下 Web 按钮查看实时结果</li>
                </ol>
            </div>
            
            <p>这个页面会在您请求AI生成内容后自动更新。</p>
        </body>
        </html>
        """

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
        @Deprecated("Use specific workspace paths instead of deriving from chatId")
        fun getWorkspacePath(chatId: String): String {
            val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return "$downloadDir/Operit/workspace/$chatId"
        }

        // 确保工作空间目录存在
        fun ensureWorkspaceDirExists(path: String): String {
            val workspaceDir = File(path)
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs()
            }
            return path
        }

        /** 如果需要，创建默认的index.html文件 */
        fun createDefaultIndexHtmlIfNeeded(workspaceDir: File) {
            val indexHtmlFile = File(workspaceDir, "index.html")
            if (!indexHtmlFile.exists()) {
                try {
                    indexHtmlFile.writeText(DEFAULT_INDEX_HTML_CONTENT.trimIndent())
                    Log.d(TAG, "已在 ${indexHtmlFile.absolutePath} 创建默认的 index.html")
                } catch (e: IOException) {
                    Log.e(TAG, "创建默认的 index.html 失败", e)
                }
            }
        }
    }

    private var workspacePath: String = "" // Default to empty

    private val isServerRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private var currentChatId: String? = null

    // 在启动前确保工作区目录存在
    @Throws(IOException::class)
    override fun start() {
        if (workspacePath.isNotEmpty()) {
            ensureWorkspaceDirExists(workspacePath)
        }
        super.start()
        isServerRunning.set(true)
        Log.d(TAG, "本地服务器已启动，端口: $port，工作空间: $workspacePath")
    }

    override fun stop() {
        super.stop()
        isServerRunning.set(false)
        Log.d(TAG, "本地服务器已停止")
    }

    fun updateChatWorkspace(chatId: String, newWorkspacePath: String) {
        this.workspacePath = newWorkspacePath
        ensureWorkspaceDirExists(newWorkspacePath)
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
            return createDefaultIndexHtml().addCorsHeaders()
        }

        try {
            if (!requestedFile.exists()) {
                Log.w(TAG, "文件不存在: $requestedFile")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "文件不存在")
                        .addCorsHeaders()
            }

            if (!isInWorkspace(requestedFile)) {
                Log.w(TAG, "试图访问工作空间外的文件: $requestedFile")
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "禁止访问")
                        .addCorsHeaders()
            }

            // 确定MIME类型
            val mimeType = getMimeTypeForFile(uri)

            // 如果是HTML文件，注入eruda
            if (mimeType == "text/html") {
                try {
                    val fileContent = requestedFile.readText(Charsets.UTF_8)
                    val injectedContent = injectErudaIntoHtml(fileContent)
                    return newFixedLengthResponse(Response.Status.OK, mimeType, injectedContent)
                            .addCorsHeaders()
                } catch (e: Exception) {
                    Log.e(TAG, "读取或注入HTML时出错: ${e.message}")
                    // 出错则回退到直接提供文件
                }
            }

            // 返回文件内容
            val fileInputStream = FileInputStream(requestedFile)
            return newChunkedResponse(Response.Status.OK, mimeType, fileInputStream)
                    .addCorsHeaders()
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "文件未找到: ${e.message}")
            return newFixedLengthResponse(
                            Response.Status.NOT_FOUND,
                            MIME_PLAINTEXT,
                            "文件不存在: ${e.message}"
                    )
                    .addCorsHeaders()
        } catch (e: Exception) {
            Log.e(TAG, "服务器错误: ${e.message}")
            return newFixedLengthResponse(
                            Response.Status.INTERNAL_ERROR,
                            MIME_PLAINTEXT,
                            "服务器错误: ${e.message}"
                    )
                    .addCorsHeaders()
        }
    }

    // 注入eruda脚本到HTML内容中
    private fun injectErudaIntoHtml(htmlContent: String): String {
        val erudaScript =
                """
        <script>
        (function() {
            if (window.erudaInjected) { return; }
            window.erudaInjected = true;
            localStorage.removeItem('eruda-entry-btn');
            var script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/eruda';
            document.body.appendChild(script);
            script.onload = function() {
                if (!window.eruda) return;
                eruda.init();
                var entryBtn = eruda.get('entry');
                if (entryBtn) {
                    entryBtn.position({
                        x: 10,
                        y: window.innerHeight - 70
                    });
                }
            }
        })();
        </script>
        """
        // 将脚本插入到</body>之前
        return htmlContent.replace("</body>", "$erudaScript</body>", ignoreCase = true)
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
        // 为默认页面也注入eruda
        val injectedHtml = injectErudaIntoHtml(DEFAULT_INDEX_HTML_CONTENT)
        return newFixedLengthResponse(Response.Status.OK, "text/html", injectedHtml)
    }

    // 添加CORS响应头的扩展函数
    private fun Response.addCorsHeaders(): Response {
        // 允许所有来源的跨域请求
        this.addHeader("Access-Control-Allow-Origin", "*")
        // 允许的请求方法
        this.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        // 允许的请求头
        this.addHeader(
                "Access-Control-Allow-Headers",
                "X-Requested-With, Content-Type, Authorization, Origin, Accept"
        )
        // 预检请求有效期（秒）
        this.addHeader("Access-Control-Max-Age", "3600")
        // 是否允许发送Cookie
        this.addHeader("Access-Control-Allow-Credentials", "true")
        // 如果是OPTIONS预检请求，快速返回
        return this
    }

    // 处理OPTIONS预检请求
    private fun handleOptionsRequest(): Response {
        val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        return response.addCorsHeaders()
    }
}
