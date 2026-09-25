package ru.mrcrubs.lms.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class StatusFilter(val label: String) {
    ALL("Все"), ACTIVE("Активные"), DONE("Готовые"), FAILED("С ошибкой");

    fun matches(job: Job): Boolean = when (this) {
        ALL -> true
        ACTIVE -> job.status.isActive
        DONE -> job.status == JobStatus.DONE
        FAILED -> job.status == JobStatus.ERROR || job.status == JobStatus.CANCELED
    }
}

enum class SortKey(val label: String) {
    NEWEST("Сначала новые"), OLDEST("Сначала старые"), NAME("По имени"),
    SIZE("По размеру"), PROGRESS("По прогрессу"), STATUS("По статусу"),
}

enum class GroupKey(val label: String) {
    NONE("Без группировки"), STATUS("По статусу"), KIND("По типу файла"), NODE("По ноде"), DAY("По дате"),
}

data class JobsQuery(
    val search: String = "",
    val status: StatusFilter = StatusFilter.ALL,
    val kinds: Set<FileKind> = emptySet(),
    val nodeId: String? = null,
    val sort: SortKey = SortKey.NEWEST,
    val group: GroupKey = GroupKey.STATUS,
) {
    val isFiltered: Boolean get() = status != StatusFilter.ALL || kinds.isNotEmpty() || nodeId != null
}

data class JobGroup(val key: String, val label: String, val jobs: List<Job>)

/** Filtering, sorting and grouping of the jobs list (mirrors the router web UI). */
object JobsView {
    private val STATUS_ORDER = listOf(
        JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.PAUSED, JobStatus.ERROR, JobStatus.DONE, JobStatus.CANCELED, JobStatus.UNKNOWN,
    )

    fun apply(
        jobs: List<Job>,
        query: JobsQuery,
        nodes: List<NodeItem>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): List<JobGroup> {
        val search = query.search.trim().lowercase(Locale.ROOT)
        val filtered = jobs.filter { job ->
            query.status.matches(job) &&
                (query.nodeId == null || job.nodeId == query.nodeId) &&
                (query.kinds.isEmpty() || FileKind.of(job) in query.kinds) &&
                (search.isEmpty() || "${job.title} ${job.url} ${job.storagePath.orEmpty()}".lowercase(Locale.ROOT).contains(search))
        }
        val sorted = filtered.sortedWith(comparator(query.sort))
        if (query.group == GroupKey.NONE) return listOf(JobGroup("all", "", sorted))

        val groups = LinkedHashMap<String, MutableList<Job>>()
        val labels = HashMap<String, String>()
        for (job in sorted) {
            val (key, label) = when (query.group) {
                GroupKey.STATUS -> job.status.name to Format.status(job.status)
                GroupKey.KIND -> FileKind.of(job).let { it.name to it.label }
                GroupKey.NODE -> (job.nodeId ?: "-") to (nodes.firstOrNull { it.id == job.nodeId }?.name ?: "Нода не выбрана")
                GroupKey.DAY -> dayOf(job.createdAt, zone, today)
                GroupKey.NONE -> "all" to ""
            }
            groups.getOrPut(key) { mutableListOf() }.add(job)
            labels[key] = label
        }
        val keys = when (query.group) {
            GroupKey.STATUS -> groups.keys.sortedBy { key -> STATUS_ORDER.indexOfFirst { it.name == key } }
            GroupKey.KIND -> groups.keys.sortedBy { key -> FileKind.entries.indexOfFirst { it.name == key } }
            GroupKey.DAY -> groups.keys.sortedDescending()
            else -> groups.keys.toList()
        }
        return keys.map { JobGroup(it, labels.getValue(it), groups.getValue(it)) }
    }

    fun counts(jobs: List<Job>): Map<StatusFilter, Int> =
        StatusFilter.entries.associateWith { filter -> jobs.count(filter::matches) }

    private fun comparator(sort: SortKey): Comparator<Job> = when (sort) {
        SortKey.NEWEST -> compareByDescending { instant(it.createdAt) }
        SortKey.OLDEST -> compareBy { instant(it.createdAt) }
        SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortKey.SIZE -> compareByDescending { it.outputSizeBytes ?: it.totalBytes ?: -1L }
        SortKey.PROGRESS -> compareByDescending { it.percent ?: if (it.status == JobStatus.DONE) 100.0 else 0.0 }
        SortKey.STATUS -> compareBy<Job> { STATUS_ORDER.indexOf(it.status) }.thenByDescending { instant(it.createdAt) }
    }

    private fun instant(value: String?): Instant =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH

    private fun dayOf(value: String?, zone: ZoneId, today: LocalDate): Pair<String, String> {
        val date = value?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDate() }.getOrNull() }
            ?: return "0000-00-00" to "Без даты"
        val label = when (val days = today.toEpochDay() - date.toEpochDay()) {
            0L -> "Сегодня"
            1L -> "Вчера"
            in 2L..6L -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("ru")))
            else -> if (days < 0) "Сегодня" else date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")))
        }
        return date.toString() to label.replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) }
    }
}
