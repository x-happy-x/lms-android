package ru.mrcrubs.lms.core

import java.net.URI
import java.util.Locale

/** What a download is, for icons, filters and grouping (same rules as the router web UI). */
enum class FileKind(val label: String) {
    VIDEO("Видео"),
    AUDIO("Аудио"),
    IMAGE("Изображения"),
    ARCHIVE("Архивы"),
    DOCUMENT("Документы"),
    TORRENT("Торренты"),
    DISK("Образы дисков"),
    APP("Программы"),
    CODE("Данные и код"),
    OTHER("Другое");

    companion object {
        private val EXTENSIONS: Map<FileKind, List<String>> = mapOf(
            VIDEO to listOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "wmv", "flv", "ts", "m2ts", "mpg", "mpeg", "3gp"),
            AUDIO to listOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "wma", "alac"),
            IMAGE to listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff", "avif", "svg"),
            ARCHIVE to listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst"),
            DOCUMENT to listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "txt", "rtf", "epub", "fb2", "djvu", "md"),
            DISK to listOf("iso", "img", "dmg", "vhd", "vhdx", "vmdk", "qcow2"),
            APP to listOf("apk", "exe", "msi", "deb", "rpm", "appimage", "pkg"),
            CODE to listOf("json", "csv", "xml", "yaml", "yml", "sql", "db", "safetensors", "gguf", "pt", "bin"),
        )
        private val BY_EXTENSION: Map<String, FileKind> =
            EXTENSIONS.flatMap { (kind, exts) -> exts.map { it to kind } }.toMap()
        private val VIDEO_HOSTS = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "rutube.ru", "vk.com", "vkvideo.ru",
            "twitch.tv", "tiktok.com", "dailymotion.com", "ok.ru",
        )

        fun extensionOf(name: String): String {
            val clean = name.substringBefore('?').substringBefore('#')
            val dot = clean.lastIndexOf('.')
            if (dot <= 0 || dot == clean.length - 1) return ""
            return clean.substring(dot + 1).lowercase(Locale.ROOT)
        }

        fun of(job: Job): FileKind {
            val byExt = BY_EXTENSION[extensionOf(job.title)]
            // A finished torrent is its content (e.g. a video); an unfinished one is "torrent".
            if (byExt != null && !(job.type == LinkExtractor.TYPE_TORRENT && job.outputPath == null)) return byExt
            if (job.type == LinkExtractor.TYPE_TORRENT || LinkExtractor.isTorrentLink(job.url)) return TORRENT
            if (job.type == LinkExtractor.TYPE_YTDLP) return VIDEO
            val host = try {
                URI(job.url.trim()).host?.lowercase(Locale.ROOT)?.removePrefix("www.")?.removePrefix("m.")
            } catch (_: Exception) {
                null
            }
            if (host != null && VIDEO_HOSTS.any { host == it || host.endsWith(".$it") }) return VIDEO
            return byExt ?: OTHER
        }
    }
}

/** Finished photo/video the router can stream for viewing (and the node can preview). */
val Job.isViewableMedia: Boolean
    get() = status == JobStatus.DONE && outputPath != null &&
        FileKind.of(this).let { it == FileKind.VIDEO || it == FileKind.IMAGE }

/** Has an output that can be downloaded to the device. */
val Job.hasOutput: Boolean get() = status == JobStatus.DONE && outputPath != null && remoteJobId != null

/** Name the file gets on the device; folder outputs (torrents) come as a ZIP. */
val Job.downloadFileName: String
    get() {
        val name = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { id }
        return if (type == LinkExtractor.TYPE_TORRENT && FileKind.extensionOf(name).isEmpty()) "$name.zip" else name
    }
