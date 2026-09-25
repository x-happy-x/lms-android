package ru.mrcrubs.lms.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkExtractorTest {
    @Test
    fun extractsFirstLinkFromSharedText() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            LinkExtractor.extract("Посмотри это видео: https://youtu.be/dQw4w9WgXcQ."),
        )
        assertEquals(
            "https://example.com/a(1).zip",
            LinkExtractor.extract("(ссылка https://example.com/a(1).zip)"),
        )
        assertNull(LinkExtractor.extract("тут нет ссылок"))
        assertNull(LinkExtractor.extract(null))
    }

    @Test
    fun extractsMagnet() {
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Some%20Show"
        assertEquals(magnet, LinkExtractor.extract("вот $magnet"))
        assertTrue(LinkExtractor.isMagnet(magnet))
        assertEquals("Some Show", LinkExtractor.displayName(magnet))
    }

    @Test
    fun suggestsTypeByLink() {
        assertEquals("TORRENT", LinkExtractor.suggestType("magnet:?xt=urn:btih:abc"))
        assertEquals("TORRENT", LinkExtractor.suggestType("https://tracker.example/dl/file.torrent?key=1"))
        assertEquals("YTDLP", LinkExtractor.suggestType("https://www.youtube.com/watch?v=abc"))
        assertEquals("YTDLP", LinkExtractor.suggestType("https://m.vk.com/video-1_2"))
        assertEquals("DIRECT", LinkExtractor.suggestType("https://example.com/archive.tar.gz"))
    }

    @Test
    fun validatesSchemes() {
        assertTrue(LinkExtractor.isSupportedUrl("HTTPS://example.com/x"))
        assertFalse(LinkExtractor.isSupportedUrl("ftp://example.com/x"))
    }

    @Test
    fun displayNameUsesLastPathSegment() {
        assertEquals("my file.iso", LinkExtractor.displayName("https://example.com/dir/my%20file.iso?x=1"))
        assertNull(LinkExtractor.displayName("https://example.com/"))
    }
}
