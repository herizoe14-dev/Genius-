package com.genius.downloader.engine

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Http {
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** Cookies en mémoire (TikTok exige les cookies de la page pour servir la vidéo). */
    private val cookieJar = object : CookieJar {
        private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { c -> store.getOrPut(c.domain) { ConcurrentHashMap() }[c.name] = c }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val own = store.values.flatMap { it.values }.filter { it.matches(url) && it.expiresAt > now }
            // Cookies du compte connecté dans l'app (Instagram, Facebook…) : prioritaires.
            val account = Accounts.cookieHeader(url.toString())?.split(';')?.mapNotNull { raw ->
                val c = raw.trim()
                if ('=' !in c) null
                else runCatching {
                    Cookie.Builder().domain(url.host).path("/")
                        .name(c.substringBefore('=')).value(c.substringAfter('=')).build()
                }.getOrNull()
            }.orEmpty()
            val names = account.map { it.name }.toSet()
            return account + own.filterNot { it.name in names }
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun request(url: String, headers: Map<String, String> = emptyMap(), ua: String = UA_DESKTOP): Request {
        val b = Request.Builder().url(url).header("User-Agent", ua)
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    fun get(url: String, headers: Map<String, String> = emptyMap(), ua: String = UA_DESKTOP): String {
        client.newCall(request(url, headers, ua)).execute().use { r ->
            if (!r.isSuccessful) throw ExtractionException("HTTP ${r.code} pour $url")
            return r.body?.string().orEmpty()
        }
    }

    fun execute(url: String, headers: Map<String, String> = emptyMap(), ua: String = UA_DESKTOP): Response =
        client.newCall(request(url, headers, ua)).execute()

    /** Suit les redirections (liens courts vm.tiktok.com, t.co, fb.watch…) et renvoie l'URL finale. */
    fun resolve(url: String): String = try {
        client.newCall(request(url, ua = UA_MOBILE)).execute().use { it.request.url.toString() }
    } catch (e: Exception) {
        url
    }
}
