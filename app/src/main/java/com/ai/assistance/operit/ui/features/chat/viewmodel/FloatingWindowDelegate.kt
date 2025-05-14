package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.services.FloatingChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责管理悬浮窗交互 */
class FloatingWindowDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onMessageReceived: (String) -> Unit,
        private val onAttachmentRequested: (String) -> Unit,
        private val onAttachmentRemoveRequested: (String) -> Unit
) {
    companion object {
        private const val TAG = "FloatingWindowDelegate"
    }

    // 悬浮窗状态
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()

    // 悬浮窗服务
    private var floatingService: FloatingChatService? = null

    // 服务连接
    private val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    floatingService = (service as? FloatingChatService.LocalBinder)?.getService()
                    // 设置消息收集
                    setupMessageCollection()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    floatingService = null
                }
            }

    // 广播接收器，用于接收悬浮窗关闭的广播
    private val floatingWindowReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == "com.ai.assistance.operit.FLOATING_WINDOW_CLOSED") {
                        // 更新悬浮窗状态
                        _isFloatingMode.value = false
                    }
                }
            }

    init {
        // 注册悬浮窗关闭的广播接收器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.registerReceiver(
                    floatingWindowReceiver,
                    IntentFilter("com.ai.assistance.operit.FLOATING_WINDOW_CLOSED"),
                    Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                    floatingWindowReceiver,
                    IntentFilter("com.ai.assistance.operit.FLOATING_WINDOW_CLOSED")
            )
        }
    }

    /** 切换悬浮窗模式 */
    fun toggleFloatingMode() {
        val newMode = !_isFloatingMode.value
        _isFloatingMode.value = newMode

        if (newMode) {
            // 绑定服务
            val intent = Intent(context, FloatingChatService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            // 解绑服务
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "解绑服务失败", e)
            }
            floatingService = null
        }
    }

    /** 设置消息收集 */
    private fun setupMessageCollection() {
        floatingService?.let { service ->
            // 收集消息
            viewModelScope.launch {
                try {
                    service.messageToSend.collect { message ->
                        Log.d(TAG, "从悬浮窗接收到消息: $message")
                        // 处理消息
                        onMessageReceived(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集消息时出错", e)
                }
            }

            // 收集附件请求
            viewModelScope.launch {
                try {
                    service.attachmentRequest.collect { request ->
                        Log.d(TAG, "从悬浮窗接收到附件请求: $request")
                        // 处理附件请求
                        onAttachmentRequested(request)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集附件请求时出错", e)
                }
            }

            // 收集附件删除请求
            viewModelScope.launch {
                try {
                    service.attachmentRemoveRequest.collect { filePath ->
                        Log.d(TAG, "从悬浮窗接收到附件删除请求: $filePath")
                        // 处理附件删除请求
                        onAttachmentRemoveRequested(filePath)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集附件删除请求时出错", e)
                }
            }
        }
    }

    /** 更新悬浮窗消息 */
    fun updateFloatingWindowMessages(messages: List<ChatMessage>) {
        floatingService?.updateChatMessages(messages)
    }

    /** 更新悬浮窗附件 */
    fun updateFloatingWindowAttachments(attachments: List<AttachmentInfo>) {
        floatingService?.let { service ->
            Log.d(TAG, "更新悬浮窗附件: ${attachments.size}项")
            service.updateAttachments(attachments)
        }
    }

    /** 清理资源 */
    fun cleanup() {
        // 解绑服务
        if (_isFloatingMode.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "在清理时解绑服务失败", e)
            }
        }

        // 取消注册广播接收器
        try {
            context.unregisterReceiver(floatingWindowReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "取消注册接收器失败", e)
        }
    }
}
