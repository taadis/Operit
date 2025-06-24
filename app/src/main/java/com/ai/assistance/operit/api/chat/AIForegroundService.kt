package com.ai.assistance.operit.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/** 前台服务，用于在AI进行长时间处理时保持应用活跃，防止被系统杀死。 该服务不执行实际工作，仅通过显示一个持久通知来提升应用的进程优先级。 */
class AIForegroundService : Service() {

    companion object {
        private const val TAG = "AIForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AI_SERVICE_CHANNEL"
        private const val CHANNEL_NAME = "AI Service"

        // 静态标志，用于从外部检查服务是否正在运行
        val isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        Log.d(TAG, "AI 前台服务创建。")
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "AI 前台服务已启动。")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 返回 START_NOT_STICKY 表示如果服务被杀死，系统不需要尝试重启它。
        // 因为服务的生命周期由 EnhancedAIService 精确控制。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        Log.d(TAG, "AI 前台服务已销毁。")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 该服务是启动服务，不提供绑定功能。
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_LOW // 低重要性，避免打扰用户
                            )
                            .apply {
                                description = "Keeps the AI assistant running in the background."
                            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        // 为了简单起见，使用一个安卓内置图标。
        // 在实际项目中，应替换为应用的自定义图标。
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI 助手")
                .setContentText("正在处理您的请求...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // 使通知不可被用户清除
                .build()
    }
}
