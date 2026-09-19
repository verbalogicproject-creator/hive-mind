package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ProjectMemoryApp
import com.example.R
import com.example.domain.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class McpForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "mcp_server_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_PORT = "extra_port"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_READ_ONLY = "extra_read_only"

        fun startService(
            context: Context,
            host: String = "127.0.0.1",
            port: Int = 8080,
            isReadOnly: Boolean = true
        ) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_READ_ONLY, isReadOnly)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, McpForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                val readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, true)

                val config = ServerConfig(
                    host = host,
                    port = port,
                    isReadOnly = readOnly
                )

                startForeground(NOTIFICATION_ID, buildNotification(host, port, readOnly))

                serviceScope.launch(Dispatchers.IO) {
                    val app = application as ProjectMemoryApp
                    app.mcpServerManager.start(config)
                }
            }
            ACTION_STOP -> {
                serviceScope.launch(Dispatchers.IO) {
                    val app = application as ProjectMemoryApp
                    app.mcpServerManager.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        val app = application as? ProjectMemoryApp
        app?.mcpServerManager?.stop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Project Memory MCP Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground status of the local Model Context Protocol server"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(host: String, port: Int, readOnly: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, McpForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val modeText = if (readOnly) "Read-Only" else "Read-Write"
        val subtitle = "Listening on $host:$port ($modeText)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Project Memory Server Active")
            .setContentText(subtitle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Server", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
