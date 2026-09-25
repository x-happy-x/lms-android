package ru.mrcrubs.lms.android.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.mrcrubs.lms.android.notify.Notifier
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobChangeDetector

/**
 * Turns job lists (from the screen's polling or the background worker) into
 * "done"/"failed" notifications, remembering the last seen statuses between runs.
 */
class JobMonitor(
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) {
    private val mutex = Mutex()

    suspend fun process(jobs: List<Job>) = mutex.withLock {
        val previous = JobChangeDetector.decode(settings.statusSnapshot())
        val events = JobChangeDetector.detect(previous, jobs)
        if (events.isNotEmpty() && settings.current().notificationsEnabled) {
            events.forEach { notifier.show(it) }
        }
        settings.saveStatusSnapshot(JobChangeDetector.encode(JobChangeDetector.snapshot(jobs)))
    }
}
