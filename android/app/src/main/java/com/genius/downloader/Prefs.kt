package com.genius.downloader

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("genius", Context.MODE_PRIVATE)
    }

    /** Convertir l'audio en MP3 (sinon : format original, qualité max sans ré-encodage). */
    var audioMp3: Boolean
        get() = sp.getBoolean("audio_mp3", false)
        set(v) = sp.edit().putBoolean("audio_mp3", v).apply()

    /** Mettre à jour yt-dlp automatiquement (une fois par jour). */
    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    /** Coller automatiquement le lien du presse-papiers à l'ouverture. */
    var autoPaste: Boolean
        get() = sp.getBoolean("auto_paste", true)
        set(v) = sp.edit().putBoolean("auto_paste", v).apply()

    /** Connexions simultanées par téléchargement (morceaux HLS/DASH). Bas = doux pour les petits modems. */
    var connections: Int
        get() = sp.getInt("connections", 2)
        set(v) = sp.edit().putInt("connections", v).apply()

    /** Vitesse max en Ko/s (0 = illimitée). */
    var speedLimitKb: Int
        get() = sp.getInt("speed_limit", 0)
        set(v) = sp.edit().putInt("speed_limit", v).apply()

    var lastUpdate: Long
        get() = sp.getLong("last_update", 0)
        set(v) = sp.edit().putLong("last_update", v).apply()
}
