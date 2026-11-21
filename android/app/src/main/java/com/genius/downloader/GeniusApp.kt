package com.genius.downloader

import android.app.Application
import android.util.Log
import com.genius.downloader.download.DownloadManager
import com.genius.downloader.engine.Accounts
import com.genius.downloader.engine.BrowserStore
import com.genius.downloader.engine.YtDlp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeniusApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        DownloadManager.init(this)
        Accounts.init(this)
        BrowserStore.init(filesDir)
        appScope.launch {
            // Décompresse python + yt-dlp + ffmpeg (quelques secondes au 1er lancement).
            YtDlp.init(this@GeniusApp)
            runCatching { Accounts.exportForYtDlp() }
            if (Prefs.autoUpdate && System.currentTimeMillis() - Prefs.lastUpdate > 24 * 3600_000L) {
                runCatching { YtDlp.update(this@GeniusApp) }
                    .onSuccess { Prefs.lastUpdate = System.currentTimeMillis(); Log.i("Genius", it) }
                    .onFailure { Log.w("Genius", "update yt-dlp", it) }
            }
        }
    }
}
