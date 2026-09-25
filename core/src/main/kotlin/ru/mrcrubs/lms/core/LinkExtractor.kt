package ru.mrcrubs.lms.core

import java.net.URI
import java.net.URLDecoder

/** Finds downloadable links in shared text and suggests a job type. */
object LinkExtractor {
    private val LINK = Regex("""(magnet:\?[^\s"'<>]+|https?://[^\s"'<>]+)""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '»', '"', '\'')
    private val VIDEO_HOSTS = listOf(
        "youtube.com", "youtu.be", "vimeo.com", "rutube.ru", "vk.com", "vkvideo.ru",
        "twitch.tv", "tiktok.com", "dailymotion.com", "ok.ru", "instagram.com",
    )

    const val TYPE_DIRECT = "DIRECT"
    const val TYPE_YTDLP = "YTDLP"
    const val TYPE_ARIA2C = "ARIA2C"
    const val TYPE_TORRENT = "TORRENT"
    val ALL_TYPES = listOf(TYPE_DIRECT, TYPE_YTDLP, TYPE_ARIA2C, TYPE_TORRENT)

    /** First http(s) or magnet link in [text], without trailing punctuation. */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = LINK.find(text) ?: return null
        return trimTrailing(match.value).takeIf { it.length > 8 }
    }

    /** Drops sentence punctuation after a link; a ")" stays when it closes a "(" inside the link. */
    private fun trimTrailing(link: String): String {
        var result = link
        while (result.isNotEmpty() && result.last() in TRAILING_PUNCTUATION) {
            if (result.last() == ')' && result.count { it == '(' } >= result.count { it == ')' }) break
            result = result.dropLast(1)
        }
        return result
    }

    fun isMagnet(url: String): Boolean = url.trim().startsWith("magnet:?", ignoreCase = true)

    fun isTorrentLink(url: String): Boolean {
        if (isMagnet(url)) return true
        val path = url.substringBefore('?').substringBefore('#')
        return path.endsWith(".torrent", ignoreCase = true)
    }

    fun isSupportedUrl(url: String): Boolean {
        val trimmed = url.trim()
        return isMagnet(trimmed) || trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
    }

    /** Best guess before (or without) a router preflight. */
    fun suggestType(url: String): String {
        if (isTorrentLink(url)) return TYPE_TORRENT
        val host = hostOf(url) ?: return TYPE_DIRECT
        return if (VIDEO_HOSTS.any { host == it || host.endsWith(".$it") }) TYPE_YTDLP else TYPE_DIRECT
    }

    /** Short name for lists: magnet `dn`, else the last path segment. */
    fun displayName(url: String): String? {
        if (isMagnet(url)) {
            val dn = url.substringAfter('?').split('&')
                .firstOrNull { it.startsWith("dn=", ignoreCase = true) }
                ?.substringAfter('=')
            return dn?.let { decode(it) }?.takeIf { it.isNotBlank() }
        }
        val path = try {
            URI(url.trim()).path
        } catch (_: Exception) {
            null
        } ?: return null
        return path.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }?.let { decode(it) }
    }

    private fun hostOf(url: String): String? = try {
        URI(url.trim()).host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")
    } catch (_: Exception) {
        null
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: Exception) {
        value
    }
}
