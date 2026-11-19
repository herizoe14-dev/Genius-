package com.genius.downloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.genius.downloader.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Garde les téléchargements actifs en arrière-plan et affiche la progression. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Téléchargements", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Terminés", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, ID, build(DownloadManager.tasks.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (!started) {
            started = true
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            scope.launch {
                DownloadManager.tasks.sample(500).collectLatest { tasks ->
                    if (tasks.none { it.active }) {
                        notifyDone(tasks)
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(ID, build(tasks))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun contentIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(tasks: List<DownloadTask>): Notification {
        val active = tasks.filter { it.active }
        val running = active.filter { it.status == Status.RUNNING }
        val p = if (running.isEmpty()) 0f else running.map { it.progress }.average().toFloat()
        val title = if (active.size <= 1) active.firstOrNull()?.title ?: "Genius" else "${active.size} téléchargements"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(running.firstOrNull()?.info ?: "En attente…")
            .setProgress(100, (p * 100).toInt(), running.isEmpty())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun notifyDone(tasks: List<DownloadTask>) {
        val recent = tasks.filter { System.currentTimeMillis() - it.createdAt < 6 * 3600_000 }
        val ok = recent.count { it.status == Status.DONE }
        val ko = recent.count { it.status == Status.FAILED }
        if (ok + ko == 0) return
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (ko == 0) "Téléchargement terminé" else "Téléchargements terminés")
            .setContentText(buildString {
                append("$ok réussi(s)")
                if (ko > 0) append(" · $ko échec(s)")
            })
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val CHANNEL_DONE = "downloads_done"
        private const val ID = 42
        private const val ID_DONE = 43
    }
}
