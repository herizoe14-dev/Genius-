package com.genius.downloader.engine

import okhttp3.FormBody
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext

/**
 * Extracteurs natifs (Kotlin) pour ce que yt-dlp ne gère pas ou mal :
 * images Pixiv, photos de tweets, diaporamas TikTok, carrousels Instagram.
 * Ils interrogent directement les sites, sans aucun serveur intermédiaire.
 */
object NativeExtractors {

    // ---------------------------------------------------------------- Pixiv
    private val PIXIV_ID = Regex("""(?:artworks/|illust_id=|/i/)(\d+)""")
    private val PIXIV_HEADERS = mapOf("Referer" to "https://www.pixiv.net/")

    fun pixiv(url: String): MediaResult {
        val id = PIXIV_ID.find(url)?.groupValues?.get(1)
            ?: throw ExtractionException("Lien Pixiv non reconnu (attendu : pixiv.net/artworks/ID)")
        val info = pixivJson("https://www.pixiv.net/ajax/illust/$id?lang=fr")
        if (info.optBoolean("error")) throw ExtractionException("Pixiv : ${info.optString("message").ifBlank { "œuvre introuvable" }}")
        val body = info.getJSONObject("body")
        val pages = pixivJson("https://www.pixiv.net/ajax/illust/$id/pages?lang=fr")
        if (pages.optBoolean("error")) throw ExtractionException("Pixiv : ${pages.optString("message").ifBlank { "contenu réservé aux membres connectés" }}")
        val items = pages.getJSONArray("body").objects().mapNotNull { p ->
            val urls = p.optJSONObject("urls") ?: return@mapNotNull null
            val original = urls.optStringOrNull("original") ?: urls.optStringOrNull("regular") ?: return@mapNotNull null
            GalleryItem(
                url = original,
                thumb = urls.optStringOrNull("small") ?: urls.optStringOrNull("regular") ?: original,
                headers = PIXIV_HEADERS,
                ext = original.substringAfterLast('.').substringBefore('?').ifBlank { "jpg" },
            )
        }
        if (items.isEmpty()) throw ExtractionException("Pixiv : aucune image (contenu réservé aux membres ?)")
        return MediaResult(
            sourceUrl = url,
            platform = Platform.PIXIV,
            title = body.optString("title").ifBlank { "Pixiv $id" },
            uploader = body.optStringOrNull("userName"),
            thumbnail = items.first().thumb,
            thumbnailHeaders = PIXIV_HEADERS,
            gallery = items,
        )
    }

    /** L'API Pixiv répond 404 avec un JSON d'erreur : on lit le corps quel que soit le code. */
    private fun pixivJson(url: String): JSONObject = Http.execute(url, PIXIV_HEADERS).use { r ->
        runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrElse { throw ExtractionException("Pixiv : HTTP ${r.code}") }
    }

    // ---------------------------------------------------------------- X / Twitter
    private val TWEET_ID = Regex("""/status(?:es)?/(\d+)""")

    fun twitter(url: String): MediaResult {
        val id = TWEET_ID.find(url)?.groupValues?.get(1) ?: throw ExtractionException("Lien de tweet non reconnu")
        val token = syndicationToken(id)
        val json = JSONObject(
            Http.get("https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=fr&token=$token")
        )
        val tweet = if (json.optString("__typename") == "TweetTombstone") {
            throw ExtractionException("Tweet indisponible (protégé ou supprimé)")
        } else json
        val media = tweet.optJSONArray("mediaDetails")?.objects().orEmpty()
        if (media.isEmpty()) throw ExtractionException("Aucun média dans ce tweet")

        val gallery = mutableListOf<GalleryItem>()
        var videoOptions = emptyList<VideoOption>()
        var audio: AudioOption? = null
        for (m in media) {
            when (m.optString("type")) {
                "photo" -> {
                    val base = m.optString("media_url_https")
                    val ext = base.substringAfterLast('.').ifBlank { "jpg" }
                    gallery += GalleryItem(
                        url = "${base.substringBeforeLast('.')}?format=$ext&name=orig",
                        thumb = "${base.substringBeforeLast('.')}?format=$ext&name=small",
                        ext = ext,
                    )
                }
                "video", "animated_gif" -> {
                    val variants = m.optJSONObject("video_info")?.optJSONArray("variants")?.objects().orEmpty()
                        .filter { it.optString("content_type") == "video/mp4" }
                    val opts = variants.map { v ->
                        val u = v.optString("url")
                        val h = Regex("""/(\d+)x(\d+)/""").find(u)?.groupValues?.get(2)?.toIntOrNull() ?: 0
                        VideoOption(
                            height = h,
                            label = if (h > 0) "${h}p" else "MP4",
                            detail = "${v.optInt("bitrate") / 1000} kbps",
                            directUrl = u,
                        )
                    }.distinctBy { it.height }.sortedByDescending { it.height }
                    val best = opts.firstOrNull()
                    if (best != null) {
                        gallery += GalleryItem(best.directUrl!!, thumb = m.optString("media_url_https"), isVideo = true, ext = "mp4")
                        if (videoOptions.isEmpty()) {
                            videoOptions = opts
                            audio = AudioOption("AAC · qualité max", ytdlpUrl = best.directUrl, ext = "m4a")
                        }
                    }
                }
            }
        }
        val user = tweet.optJSONObject("user")
        val text = tweet.optString("text").replace(Regex("""https://t\.co/\S+"""), "").trim()
        val videoCount = gallery.count { it.isVideo }
        return MediaResult(
            sourceUrl = url,
            platform = Platform.TWITTER,
            title = text.ifBlank { "Tweet $id" }.take(140),
            uploader = user?.optStringOrNull("name")?.let { n -> "$n (@${user.optString("screen_name")})" },
            thumbnail = media.first().optString("media_url_https"),
            videoOptions = if (videoCount <= 1) videoOptions else emptyList(),
            audio = if (videoCount <= 1) audio else null,
            gallery = if (gallery.size == 1 && videoCount == 1) emptyList() else gallery,
        )
    }

    /** Equivalent Kotlin de ((id / 1e15) * PI).toString(36).replace(/(0+|\.)/g, ''). */
    private fun syndicationToken(id: String): String {
        val v = (id.toDouble() / 1e15) * Math.PI
        val intPart = v.toLong()
        val sb = StringBuilder(java.lang.Long.toString(intPart, 36))
        var frac = BigDecimal(v - intPart, MathContext.DECIMAL64)
        sb.append('.')
        repeat(11) {
            frac = frac.multiply(BigDecimal(36))
            val d = frac.toInt()
            sb.append(Character.forDigit(d, 36))
            frac = frac.subtract(BigDecimal(d))
        }
        return sb.toString().replace(Regex("(0+|\\.)"), "")
    }

    // ---------------------------------------------------------------- TikTok
    private val TIKTOK_ID = Regex("""/(?:video|photo|v)/(\d+)""")
    private val TIKTOK_HEADERS = mapOf("Referer" to "https://www.tiktok.com/")

    fun tiktok(url: String): MediaResult {
        val id = TIKTOK_ID.find(url)?.groupValues?.get(1) ?: throw ExtractionException("Lien TikTok non reconnu")
        // La page /photo/ ne contient pas les données : la même ID via /video/ les contient (diaporamas inclus).
        val user = Regex("""tiktok\.com/(@[^/?#]+)""").find(url)?.groupValues?.get(1) ?: "@i"
        val page = Http.get("https://www.tiktok.com/$user/video/$id")
        val item = tiktokItem(page, id) ?: throw ExtractionException("TikTok : données introuvables (réessayez)")

        val author = item.optJSONObject("author")
        val title = item.optString("desc").ifBlank { "TikTok $id" }
        val music = item.optJSONObject("music")
        val musicUrl = music?.optStringOrNull("playUrl")
        val audio = musicUrl?.let {
            AudioOption("Son original · ${music.optString("title").take(40)}", ytdlpUrl = it, headers = TIKTOK_HEADERS)
        }

        val imagePost = item.optJSONObject("imagePost")
        if (imagePost != null) {
            val images = imagePost.optJSONArray("images")?.objects().orEmpty().mapNotNull { img ->
                val list = img.optJSONObject("imageURL")?.optJSONArray("urlList") ?: return@mapNotNull null
                val urls = (0 until list.length()).map { list.optString(it) }
                val u = urls.firstOrNull { ".jpeg" in it || ".jpg" in it } ?: urls.firstOrNull() ?: return@mapNotNull null
                GalleryItem(u, headers = TIKTOK_HEADERS, ext = if (".webp" in u) "webp" else "jpg")
            }
            if (images.isEmpty()) throw ExtractionException("TikTok : aucune image trouvée")
            return MediaResult(
                sourceUrl = url, platform = Platform.TIKTOK, title = title,
                uploader = author?.optStringOrNull("nickname") ?: author?.optStringOrNull("uniqueId"),
                thumbnail = images.first().url, thumbnailHeaders = TIKTOK_HEADERS,
                audio = audio, gallery = images,
            )
        }

        val video = item.optJSONObject("video") ?: throw ExtractionException("TikTok : vidéo introuvable")
        val options = mutableListOf<VideoOption>()
        video.optJSONArray("bitrateInfo")?.objects()?.forEach { b ->
            val list = b.optJSONObject("PlayAddr")?.optJSONArray("UrlList") ?: return@forEach
            val u = (0 until list.length()).map { list.optString(it) }.lastOrNull { it.isNotBlank() } ?: return@forEach
            val h = minOf(b.optJSONObject("PlayAddr")?.optInt("Height") ?: 0, b.optJSONObject("PlayAddr")?.optInt("Width") ?: 0)
            options += VideoOption(h, "${h}p", "${b.optString("CodecType").uppercase()} · ${b.optInt("Bitrate") / 1000} kbps",
                directUrl = u, headers = TIKTOK_HEADERS)
        }
        if (options.isEmpty()) video.optStringOrNull("playAddr")?.let {
            options += VideoOption(video.optInt("height"), "Original", "sans filigrane", directUrl = it, headers = TIKTOK_HEADERS)
        }
        if (options.isEmpty()) throw ExtractionException("TikTok : aucun flux vidéo")
        return MediaResult(
            sourceUrl = url, platform = Platform.TIKTOK, title = title,
            uploader = author?.optStringOrNull("nickname"),
            thumbnail = video.optStringOrNull("cover") ?: video.optStringOrNull("originCover"),
            durationSec = video.optInt("duration"),
            videoOptions = options.distinctBy { it.height }.sortedByDescending { it.height },
            audio = audio ?: AudioOption("Qualité max", ytdlpUrl = url),
        )
    }

    private fun tiktokItem(html: String, id: String): JSONObject? {
        scriptJson(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")?.let { root ->
            root.optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.video-detail")
                ?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")?.let { return it }
            root.optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.reflow.video.detail")
                ?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")?.let { return it }
        }
        scriptJson(html, "SIGI_STATE")?.optJSONObject("ItemModule")?.optJSONObject(id)?.let { return it }
        return null
    }

    private fun scriptJson(html: String, scriptId: String): JSONObject? {
        val m = Regex("""<script[^>]+id="$scriptId"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: return null
        return runCatching { JSONObject(m.groupValues[1]) }.getOrNull()
    }

    // ---------------------------------------------------------------- Instagram
    private val IG_CODE = Regex("""instagram\.com/(?:[^/?#]+/)?(?:p|reels?|tv)/([A-Za-z0-9_-]+)""")
    private const val IG_APP_ID = "936619743392459"

    fun instagram(url: String): MediaResult {
        val code = IG_CODE.find(url)?.groupValues?.get(1) ?: throw ExtractionException("Lien Instagram non reconnu")
        val errors = mutableListOf<String>()
        if (Accounts.isLoggedIn(Account.INSTAGRAM)) {
            runCatching { return instagramApi(url, code) }.onFailure { errors += it.message.orEmpty() }
        }
        runCatching { return instagramGraphql(url, code) }.onFailure { errors += it.message.orEmpty() }
        throw ExtractionException(
            if (Accounts.isLoggedIn(Account.INSTAGRAM)) "Instagram : ${errors.first()}"
            else "Instagram bloque l'accès sans compte. Connectez-vous à Instagram dans le navigateur Genius, puis réessayez.",
        )
    }

    /** shortcode -> identifiant numérique du média. */
    private fun igMediaId(code: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var id = java.math.BigInteger.ZERO
        code.take(11).forEach { id = id.multiply(java.math.BigInteger.valueOf(64)).add(java.math.BigInteger.valueOf(alphabet.indexOf(it).toLong())) }
        return id.toString()
    }

    private fun igHeaders(code: String) = buildMap {
        put("X-IG-App-ID", IG_APP_ID)
        put("X-Requested-With", "XMLHttpRequest")
        put("Referer", "https://www.instagram.com/p/$code/")
        put("Origin", "https://www.instagram.com")
        Accounts.cookie(Account.INSTAGRAM, "csrftoken")?.let { put("X-CSRFToken", it) }
    }

    /** API web d'Instagram avec le compte connecté : fiable pour photos, carrousels et reels. */
    private fun instagramApi(url: String, code: String): MediaResult {
        val body = Http.execute("https://www.instagram.com/api/v1/media/${igMediaId(code)}/info/", igHeaders(code)).use { r ->
            if (r.code == 401 || r.code == 403) throw ExtractionException("session expirée, reconnectez-vous")
            r.body?.string().orEmpty()
        }
        val item = runCatching { JSONObject(body) }.getOrNull()?.optJSONArray("items")?.optJSONObject(0)
            ?: throw ExtractionException("publication introuvable")
        val nodes = item.optJSONArray("carousel_media")?.objects()?.ifEmpty { null } ?: listOf(item)

        fun bestImage(n: JSONObject) = n.optJSONObject("image_versions2")?.optJSONArray("candidates")?.objects()
            ?.maxByOrNull { it.optInt("width") * it.optInt("height") }?.optString("url").orEmpty()
        fun videos(n: JSONObject) = n.optJSONArray("video_versions")?.objects().orEmpty()

        val gallery = nodes.map { n ->
            val v = videos(n).maxByOrNull { it.optInt("width") * it.optInt("height") }
            if (v != null) GalleryItem(v.optString("url"), thumb = bestImage(n), isVideo = true, ext = "mp4")
            else GalleryItem(bestImage(n), ext = "jpg")
        }.filter { it.url.isNotBlank() }
        if (gallery.isEmpty()) throw ExtractionException("aucun média")

        val singleVideo = nodes.size == 1 && videos(nodes[0]).isNotEmpty()
        val videoOptions = if (singleVideo) videos(nodes[0])
            .map { v ->
                val h = minOf(v.optInt("height"), v.optInt("width")).takeIf { it > 0 } ?: v.optInt("height")
                VideoOption(h, "${h}p", "sans filigrane", directUrl = v.optString("url"))
            }.distinctBy { it.height }.sortedByDescending { it.height } else emptyList()
        val caption = item.optJSONObject("caption")?.optString("text").orEmpty()
        return MediaResult(
            sourceUrl = url,
            platform = Platform.INSTAGRAM,
            title = caption.lineSequence().firstOrNull()?.take(120)?.ifBlank { null } ?: "Instagram $code",
            uploader = item.optJSONObject("user")?.optStringOrNull("username")?.let { "@$it" },
            thumbnail = bestImage(nodes[0]),
            durationSec = item.optDouble("video_duration", 0.0).toInt(),
            videoOptions = videoOptions,
            audio = if (singleVideo) AudioOption("AAC · qualité max", ytdlpUrl = videoOptions.first().directUrl) else null,
            gallery = if (singleVideo) emptyList() else gallery,
        )
    }

    /** GraphQL public (sans compte) : marche parfois selon le réseau. */
    private fun instagramGraphql(url: String, code: String): MediaResult {
        // Récupère le cookie csrftoken (stocké dans le cookie jar).
        runCatching { Http.execute("https://www.instagram.com/p/$code/").close() }
        val variables = JSONObject()
            .put("shortcode", code)
            .put("fetch_tagged_user_count", JSONObject.NULL)
            .put("hoisted_comment_id", JSONObject.NULL)
            .put("hoisted_reply_id", JSONObject.NULL)
        val form = FormBody.Builder()
            .add("lsd", "AVqbxe3J_YA")
            .add("variables", variables.toString())
            .add("doc_id", "8845758582119845")
            .build()
        val reqB = Http.request("https://www.instagram.com/graphql/query").newBuilder()
            .post(form)
            .header("X-FB-LSD", "AVqbxe3J_YA")
            .header("X-ASBD-ID", "129477")
            .header("Sec-Fetch-Site", "same-origin")
        igHeaders(code).forEach { (k, v) -> reqB.header(k, v) }
        val body = Http.client.newCall(reqB.build()).execute().use { it.body?.string().orEmpty() }
        val media = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("data")?.optJSONObject("xdt_shortcode_media")
            ?: throw ExtractionException("publication privée ou indisponible")

        val nodes = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")?.objects()
            ?.mapNotNull { it.optJSONObject("node") }
            ?.ifEmpty { null } ?: listOf(media)

        val gallery = nodes.map { n ->
            val img = bestIgImage(n)
            if (n.optBoolean("is_video")) GalleryItem(n.optString("video_url"), thumb = img, isVideo = true, ext = "mp4")
            else GalleryItem(img, ext = "jpg")
        }
        val caption = media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)
            ?.optJSONObject("node")?.optString("text").orEmpty()
        val singleVideo = nodes.size == 1 && nodes[0].optBoolean("is_video")
        val videoOptions = if (singleVideo) {
            val n = nodes[0]
            val h = n.optJSONObject("dimensions")?.optInt("height") ?: 0
            listOf(VideoOption(h, if (h > 0) "${h}p" else "Original", "sans filigrane", directUrl = n.optString("video_url")))
        } else emptyList()
        return MediaResult(
            sourceUrl = url,
            platform = Platform.INSTAGRAM,
            title = caption.lineSequence().firstOrNull()?.take(120)?.ifBlank { null } ?: "Instagram $code",
            uploader = media.optJSONObject("owner")?.optStringOrNull("username")?.let { "@$it" },
            thumbnail = bestIgImage(nodes[0]),
            durationSec = nodes[0].optDouble("video_duration", 0.0).toInt(),
            videoOptions = videoOptions,
            audio = if (singleVideo) AudioOption("AAC · qualité max", ytdlpUrl = nodes[0].optString("video_url")) else null,
            gallery = if (singleVideo) emptyList() else gallery,
        )
    }

    private fun bestIgImage(n: JSONObject): String =
        n.optJSONArray("display_resources")?.objects()?.maxByOrNull { it.optInt("config_width") }?.optStringOrNull("src")
            ?: n.optString("display_url")

    // ---------------------------------------------------------------- Facebook (photos)
    private val FB_IMAGE = Regex(""""(?:viewer_image|photo_image|full_image)"\s*:\s*\{[^{}]*?"uri"\s*:\s*"((?:[^"\\]|\\.)+)"""")

    /** Photos d'une publication Facebook (meilleure résolution trouvée dans la page). */
    fun facebookPhotos(url: String): MediaResult {
        val html = Http.get(url)
        val images = FB_IMAGE.findAll(html)
            .mapNotNull { m -> runCatching { JSONObject("{\"u\":\"${m.groupValues[1]}\"}").getString("u") }.getOrNull() }
            .filter { "scontent" in it || "fbcdn" in it }
            .distinctBy { it.substringBefore('?').substringAfterLast('/') }
            .toList()
        if (images.isEmpty()) {
            return runCatching { openGraph(url) }.getOrElse {
                throw ExtractionException(
                    if (Accounts.isLoggedIn(Account.FACEBOOK)) "Facebook : aucun média trouvé"
                    else "Facebook bloque l'accès sans compte. Connectez-vous à Facebook dans le navigateur Genius, puis réessayez.",
                )
            }
        }
        val title = Regex("""<title>([^<]*)</title>""").find(html)?.groupValues?.get(1)?.trim().orEmpty()
        return MediaResult(
            sourceUrl = url,
            platform = Platform.FACEBOOK,
            title = title.ifBlank { "Facebook" },
            thumbnail = images.first(),
            gallery = images.map { GalleryItem(it, ext = "jpg") },
        )
    }

    // ---------------------------------------------------------------- OpenGraph (secours)
    fun openGraph(url: String): MediaResult {
        val html = Http.get(url, ua = Http.UA_MOBILE)
        fun meta(prop: String): List<String> =
            Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .findAll(html).map { it.groupValues[1].replace("&amp;", "&") }.toList() +
                Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE)
                    .findAll(html).map { it.groupValues[1].replace("&amp;", "&") }.toList()

        val images = (meta("og:image") + meta("twitter:image")).distinct()
        val videos = (meta("og:video:secure_url") + meta("og:video") + meta("og:video:url")).distinct()
            .filter { it.startsWith("http") }
        if (images.isEmpty() && videos.isEmpty()) throw ExtractionException("Aucun média trouvé sur cette page")
        val title = meta("og:title").firstOrNull() ?: meta("twitter:title").firstOrNull() ?: url
        return MediaResult(
            sourceUrl = url,
            platform = Platform.of(url),
            title = title,
            thumbnail = images.firstOrNull(),
            videoOptions = videos.take(1).map { VideoOption(0, "Original", "fichier direct", directUrl = it) },
            audio = videos.firstOrNull()?.let { AudioOption("Qualité max", ytdlpUrl = it) },
            gallery = images.map { GalleryItem(it, ext = it.substringAfterLast('.').substringBefore('?').take(4).ifBlank { "jpg" }) },
        )
    }
}
