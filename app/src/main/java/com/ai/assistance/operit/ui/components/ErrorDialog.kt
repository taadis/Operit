package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** 简洁的错误弹窗组件 只显示错误消息内容，不显示堆栈跟踪 */
@Composable
fun ErrorDialog(
        errorMessage: String,
        onDismiss: () -> Unit,
        properties: DialogProperties =
                DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
) {
    // 处理错误消息：移除堆栈跟踪信息，只保留主要错误信息
    val cleanErrorMessage = errorMessage.cleanErrorMessage()

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.request_failed)) },
            text = {
                Box(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                            text = cleanErrorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) } },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            properties = properties
    )
}

/** 扩展函数：清理错误消息，只保留主要错误信息 增强版本能够处理更多特殊错误格式和嵌套错误 */
@Composable
private fun String.cleanErrorMessage(): String {
    if (this.isBlank()) return stringResource(R.string.unknown_error)

    // 处理常见网络错误格式
    if (this.contains("UnknownHostException") ||
                    this.contains("无法连接到服务器") ||
                    this.contains("无法解析主机名")
    )
            return stringResource(R.string.server_connection_failed)

    if (this.contains("SocketTimeoutException") || this.contains("连接超时") || this.contains("请求超时"))
            return stringResource(R.string.connection_timeout)

    if (this.contains("ConnectException") ||
                    this.contains("Connection refused") ||
                    this.contains("拒绝连接")
    )
            return stringResource(R.string.connection_refused)

    // 先尝试提取最有意义的错误部分
    val lines = this.split("\n")

    // 如果错误消息不包含换行符，检查其他特性
    if (lines.size <= 1) {
        // 处理错误码情况 (例如 "Error 404: Not Found")
        if (this.matches(Regex(".*Error \\d+:.*"))) {
            return this
        }

        // 处理JSON格式错误
        if (this.contains("{") && this.contains("}")) {
            val jsonErrorMatch = Regex("\\{\"error\":\"(.+?)\"\\}").find(this)
            if (jsonErrorMatch != null) {
                return jsonErrorMatch.groupValues[1]
            }
        }

        // 如果包含错误细节但被隐藏在括号中
        val parenthesisMatch = Regex(".*?\\((.+?)\\)").find(this)
        if (parenthesisMatch != null) {
            val content = parenthesisMatch.groupValues[1]
            // 确保括号内容不是堆栈跟踪
            if (!content.contains("at ") && content.length < 100) {
                return content
            }
        }

        return this
    }

    // 首先，尝试找到带有明确错误信息的行
    val errorLines =
            lines.filter { line ->
                line.contains("Error:") ||
                        line.contains("错误:") ||
                        line.contains("失败:") ||
                        (line.contains(":") && !line.matches(Regex(".*at .*\\(.+\\)")))
            }

    if (errorLines.isNotEmpty()) {
        return errorLines.first()
    }

    // 尝试找到最有意义的错误信息行
    val meaningfulLines =
            lines.filter { line ->
                !line.matches(Regex(".*at .*\\(.*\\).*")) && // 过滤堆栈跟踪行
                !line.matches(Regex(".*\\..*Exception:")) && // 过滤异常类型声明
                        !line.contains("Exception in thread") && // 过滤线程异常信息
                        !line.contains("Caused by:") && // 过滤因果关系行
                        line.isNotBlank() && // 过滤空行
                        !line.matches(Regex("^\\s+at\\s+.*")) && // 过滤缩进的堆栈行
                        !line.matches(Regex("^\\s+\\.\\.\\..*")) && // 过滤省略行
                        !line.matches(Regex("^\\s+\\d+ more.*")) // 过滤"更多行"提示
            }

    // 如果找不到有意义的行，尝试提取错误类型或消息
    if (meaningfulLines.isEmpty()) {
        // 尝试找出异常类名或错误描述
        val exceptionMatch = Regex("([A-Za-z0-9.]+Exception)").find(this)
        if (exceptionMatch != null) {
            val exceptionType = exceptionMatch.groupValues[1]
            val simpleName = exceptionType.substringAfterLast(".")

            // 特殊处理网络相关异常类型
            when {
                simpleName.contains("SocketTimeout") -> return stringResource(R.string.connection_check_network)
                simpleName.contains("UnknownHost") -> return stringResource(R.string.cannot_resolve_host)
                simpleName.contains("Connect") -> return stringResource(R.string.cannot_connect_to_server)
                simpleName.contains("SSL") || simpleName.contains("TLS") -> return stringResource(R.string.secure_connection_failed)
                simpleName.contains("Timeout") -> return stringResource(R.string.request_timeout)
                simpleName.contains("IO") -> return stringResource(R.string.network_transfer_failed)
            }

            // 尝试提取错误消息
            val messageMatch = Regex("$simpleName:\\s*(.+?)($|\\n)").find(this)
            return if (messageMatch != null) {
                "${simpleName}: ${messageMatch.groupValues[1]}"
            } else {
                stringResource(R.string.error_occurred, simpleName)
            }
        }

        // 如果没有匹配到异常类型，找第一个包含Error或Exception的行
        val firstErrorLine =
                lines.firstOrNull {
                    it.contains("Error") || it.contains("Exception") || it.contains("失败")
                }

        return firstErrorLine ?: stringResource(R.string.try_again_later)
    }

    // 如果找到了有意义的行，限制显示数量
    return if (meaningfulLines.size > 2) {
        meaningfulLines.take(2).joinToString("\n")
    } else {
        meaningfulLines.joinToString("\n")
    }
}
