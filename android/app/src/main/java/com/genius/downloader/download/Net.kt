package com.genius.downloader.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

object Net {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Attend le retour d'Internet (modem qui redémarre…), au plus [maxMs]. */
    suspend fun awaitOnline(context: Context, maxMs: Long = 15 * 60_000L) {
        var waited = 0L
        while (!isOnline(context) && waited < maxMs) {
            delay(3000)
            waited += 3000
        }
    }
}
