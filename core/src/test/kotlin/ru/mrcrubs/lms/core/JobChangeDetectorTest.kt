package ru.mrcrubs.lms.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JobChangeDetectorTest {
    private fun job(id: String, status: JobStatus) = Job(id = id, url = "https://example.com/$id.bin", status = status)

    @Test
    fun firstPollNeverNotifies() {
        assertTrue(JobChangeDetector.detect(null, listOf(job("a", JobStatus.DONE))).isEmpty())
    }

    @Test
    fun notifiesOnTransitionToDoneOrError() {
        val previous = mapOf("a" to JobStatus.RUNNING, "b" to JobStatus.QUEUED, "c" to JobStatus.PAUSED)
        val events = JobChangeDetector.detect(
            previous,
            listOf(job("a", JobStatus.DONE), job("b", JobStatus.ERROR), job("c", JobStatus.CANCELED)),
        )
        assertEquals(listOf("a" to JobEvent.Kind.COMPLETED, "b" to JobEvent.Kind.FAILED), events.map { it.job.id to it.kind })
    }

    @Test
    fun ignoresUnchangedNewAndAlreadyFinishedJobs() {
        val previous = mapOf("a" to JobStatus.DONE, "b" to JobStatus.RUNNING)
        val events = JobChangeDetector.detect(
            previous,
            listOf(job("a", JobStatus.DONE), job("b", JobStatus.RUNNING), job("new", JobStatus.DONE)),
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun retriedJobNotifiesAgain() {
        val events = JobChangeDetector.detect(mapOf("a" to JobStatus.QUEUED), listOf(job("a", JobStatus.DONE)))
        assertEquals(1, events.size)
    }

    @Test
    fun snapshotRoundTrip() {
        val snapshot = mapOf("a" to JobStatus.RUNNING, "b-2" to JobStatus.DONE)
        assertEquals(snapshot, JobChangeDetector.decode(JobChangeDetector.encode(snapshot)))
        assertEquals(emptyMap<String, JobStatus>(), JobChangeDetector.decode(""))
        assertEquals(null, JobChangeDetector.decode(null))
    }
}
