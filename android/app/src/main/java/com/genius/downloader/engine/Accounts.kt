package com.genius.downloader.engine

import android.content.Context
import android.webkit.CookieManager
import java.io.File

/**
 * Comptes connectés dans l'app (WebView). Les cookies restent sur le téléphone :
 * ils servent uniquement aux requêtes directes vers le site concerné et à yt-dlp.
 */
enum class Account(val label: String, val loginUrl: String, val domain: String, val sessionCookie: String) {
    INSTAGRAM("Instagram", "https://www.instagram.com/accounts/login/", "instagram.com", "sessionid"),
    FACEBOOK("Facebook", "https://m.facebook.com/login/", "facebook.com", "c_user"),
    TIKTOK("TikTok", "https://www.tiktok.com/login", "tiktok.com", "sessionid"),
    ;

    val origin get() = "https://www.$domain"
}

object Accounts {
    private lateinit var cookieFile: File

    fun init(context: Context) {
        cookieFile = File(context.filesDir, "cookies.txt")
    }

    private fun manager(): CookieManager? = runCatching { CookieManager.getInstance() }.getOrNull()

    /** Cookies (format "a=b; c=d") pour une URL, si un compte correspondant est connecté. */
    fun cookieHeader(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return null
        val acc = Account.entries.firstOrNull { host == it.domain || host.endsWith(".${it.domain}") } ?: return null
        return manager()?.getCookie(acc.origin)?.takeIf { it.isNotBlank() }
    }

    fun cookie(acc: Account, name: String): String? =
        manager()?.getCookie(acc.origin)?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    fun isLoggedIn(acc: Account): Boolean = cookie(acc, acc.sessionCookie) != null

    fun logout(acc: Account) {
        val m = manager() ?: return
        m.getCookie(acc.origin)?.split(';')?.forEach { c ->
            val name = c.substringBefore('=').trim()
            listOf(acc.origin, "https://${acc.domain}", "https://m.${acc.domain}").forEach { u ->
                m.setCookie(u, "$name=; Max-Age=0; Domain=.${acc.domain}; Path=/")
                m.setCookie(u, "$name=; Max-Age=0; Path=/")
            }
        }
        m.flush()
        exportForYtDlp()
    }

    /** Fichier cookies au format Netscape pour yt-dlp (--cookies). Null si aucun compte. */
    fun exportForYtDlp(): File? {
        val m = manager() ?: return null
        val lines = mutableListOf("# Netscape HTTP Cookie File")
        val expiry = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
        Account.entries.forEach { acc ->
            m.getCookie(acc.origin)?.split(';')?.forEach { raw ->
                val c = raw.trim()
                if ('=' !in c) return@forEach
                lines += listOf(".${acc.domain}", "TRUE", "/", "TRUE", "$expiry", c.substringBefore('='), c.substringAfter('='))
                    .joinToString("\t")
            }
        }
        if (lines.size == 1) {
            cookieFile.delete()
            return null
        }
        cookieFile.writeText(lines.joinToString("\n", postfix = "\n"))
        return cookieFile
    }

    fun ytDlpCookieFile(): File? = if (::cookieFile.isInitialized && cookieFile.exists()) cookieFile else null
}
