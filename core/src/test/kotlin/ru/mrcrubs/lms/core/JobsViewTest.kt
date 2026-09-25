package ru.mrcrubs.lms.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class JobsViewTest {
    private fun job(
        id: String,
        url: String,
        status: JobStatus = JobStatus.DONE,
        type: String = "DIRECT",
        output: String? = null,
        created: String = "2026-09-25T10:00:00Z",
        size: Long? = null,
        node: String? = "n1",
    ) = Job(id = id, url = url, status = status, type = type, outputPath = output, createdAt = created, outputSizeBytes = size, nodeId = node, remoteJobId = "r-$id")

    private val jobs = listOf(
        job("v", "https://example.com/trip.mp4", output = "/dl/trip.mp4", size = 500, created = "2026-09-25T09:00:00Z"),
        job("i", "https://example.com/a.JPG", output = "/dl/a.JPG", size = 10, created = "2026-09-24T09:00:00Z"),
        job("t", "magnet:?xt=urn:btih:abc&dn=Show.S01", status = JobStatus.RUNNING, type = "TORRENT", created = "2026-09-25T11:00:00Z"),
        job("t2", "magnet:?xt=urn:btih:def", type = "TORRENT", output = "/dl/Movie.mkv", created = "2026-09-20T09:00:00Z"),
        job("y", "https://youtu.be/xyz", status = JobStatus.ERROR, type = "YTDLP", node = "n2"),
        job("z", "https://example.com/backup.tar.gz", status = JobStatus.PAUSED),
    )

    @Test
    fun kindsFollowExtensionTypeAndHost() {
        val kinds = jobs.associate { it.id to FileKind.of(it) }
        assertEquals(FileKind.VIDEO, kinds["v"])
        assertEquals(FileKind.IMAGE, kinds["i"])
        assertEquals(FileKind.TORRENT, kinds["t"])
        assertEquals(FileKind.VIDEO, kinds["t2"], "finished torrent is its content")
        assertEquals(FileKind.VIDEO, kinds["y"])
        assertEquals(FileKind.ARCHIVE, kinds["z"])
    }

    @Test
    fun viewableMediaAndDownloadNames() {
        assertTrue(jobs.first { it.id == "v" }.isViewableMedia)
        assertFalse(jobs.first { it.id == "t" }.isViewableMedia)
        val folder = job("f", "magnet:?xt=urn:btih:1", type = "TORRENT", output = "/dl/Some Show")
        assertEquals("Some Show.zip", folder.downloadFileName)
        assertEquals("trip.mp4", jobs.first { it.id == "v" }.downloadFileName)
    }

    @Test
    fun filtersBySearchStatusKindAndNode() {
        fun ids(query: JobsQuery) = JobsView.apply(jobs, query.copy(group = GroupKey.NONE), emptyList()).single().jobs.map { it.id }.toSet()
        assertEquals(setOf("t", "z"), ids(JobsQuery(status = StatusFilter.ACTIVE)))
        assertEquals(setOf("v", "t2", "y"), ids(JobsQuery(kinds = setOf(FileKind.VIDEO))))
        assertEquals(setOf("y"), ids(JobsQuery(nodeId = "n2")))
        assertEquals(setOf("t"), ids(JobsQuery(search = "show.s01")))
    }

    @Test
    fun sortsAndGroups() {
        val bySize = JobsView.apply(jobs, JobsQuery(sort = SortKey.SIZE, group = GroupKey.NONE), emptyList()).single().jobs
        assertEquals(listOf("v", "i"), bySize.take(2).map { it.id })

        val byStatus = JobsView.apply(jobs, JobsQuery(group = GroupKey.STATUS), emptyList())
        assertEquals(listOf("Загружается", "Пауза", "Ошибка", "Готово"), byStatus.map { it.label })

        val byDay = JobsView.apply(
            jobs, JobsQuery(group = GroupKey.DAY), emptyList(),
            zone = ZoneOffset.UTC, today = LocalDate.parse("2026-09-25"),
        )
        assertEquals(listOf("Сегодня", "Вчера"), byDay.take(2).map { it.label })
        assertEquals(listOf("t", "y", "z", "v"), byDay.first().jobs.map { it.id }, "newest first within a day")

        val byNode = JobsView.apply(jobs, JobsQuery(group = GroupKey.NODE), listOf(NodeItem(id = "n1", name = "s1")))
        assertEquals(listOf("s1", "Нода не выбрана"), byNode.map { it.label })
    }

    @Test
    fun countsByStatus() {
        val counts = JobsView.counts(jobs)
        assertEquals(6, counts[StatusFilter.ALL])
        assertEquals(2, counts[StatusFilter.ACTIVE])
        assertEquals(1, counts[StatusFilter.FAILED])
    }
}
