package ru.mrcrubs.lms.core

/** Something worth a notification. */
data class JobEvent(val job: Job, val kind: Kind) {
    enum class Kind { COMPLETED, FAILED }
}

/**
 * Compares job statuses between two polls. Only transitions into DONE/ERROR from a
 * previously seen non-terminal status produce events, so the first poll (no history)
 * and jobs that were already finished never notify.
 */
object JobChangeDetector {
    fun detect(previous: Map<String, JobStatus>?, current: List<Job>): List<JobEvent> {
        if (previous == null) return emptyList()
        return current.mapNotNull { job ->
            val before = previous[job.id] ?: return@mapNotNull null
            if (before == job.status || before.isTerminal) return@mapNotNull null
            when (job.status) {
                JobStatus.DONE -> JobEvent(job, JobEvent.Kind.COMPLETED)
                JobStatus.ERROR -> JobEvent(job, JobEvent.Kind.FAILED)
                else -> null
            }
        }
    }

    fun snapshot(jobs: List<Job>): Map<String, JobStatus> = jobs.associate { it.id to it.status }

    /** Compact persisted form: "id=STATUS" lines. */
    fun encode(snapshot: Map<String, JobStatus>): String =
        snapshot.entries.joinToString("\n") { "${it.key}=${it.value.name}" }

    fun decode(value: String?): Map<String, JobStatus>? {
        if (value == null) return null
        return value.lineSequence()
            .mapNotNull { line ->
                val id = line.substringBefore('=', "")
                val status = line.substringAfter('=', "")
                if (id.isEmpty()) null
                else id to (JobStatus.entries.firstOrNull { it.name == status } ?: JobStatus.UNKNOWN)
            }
            .toMap()
    }
}
