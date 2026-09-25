package ru.mrcrubs.lms.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Job statuses as reported by the router (`/api/ui/jobs`). */
@Serializable
enum class JobStatus {
    QUEUED, RUNNING, PAUSED, DONE, ERROR, CANCELED,

    /** Any status this app version does not know yet. */
    @SerialName("UNKNOWN")
    UNKNOWN;

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == PAUSED
    val isTerminal: Boolean get() = this == DONE || this == ERROR || this == CANCELED
}

@Serializable
data class Job(
    val id: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val type: String = "",
    val url: String = "",
    val status: JobStatus = JobStatus.UNKNOWN,
    val storagePath: String? = null,
    val profileId: String? = null,
    val nodeId: String? = null,
    val remoteJobId: String? = null,
    val percent: Double? = null,
    val totalBytes: Long? = null,
    val speedBytes: Long? = null,
    val etaSeconds: Long? = null,
    val message: String? = null,
    val outputPath: String? = null,
    val outputSizeBytes: Long? = null,
    val errorText: String? = null,
) {
    /** Human-friendly title: output file name, else the last URL segment, else the URL. */
    val title: String
        get() {
            outputPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { return it }
            LinkExtractor.displayName(url)?.let { return it }
            return url
        }

    val canPause: Boolean get() = status == JobStatus.QUEUED || status == JobStatus.RUNNING
    val canResume: Boolean get() = status == JobStatus.PAUSED
    val canRetry: Boolean get() = status == JobStatus.ERROR || status == JobStatus.CANCELED
    val canCancel: Boolean get() = !status.isTerminal
}

@Serializable
data class NodeItem(
    val id: String,
    val name: String = "",
    val baseUrl: String = "",
    val clientId: String = "",
    val enabled: Boolean = true,
    val status: String = "unknown",
    val statusText: String = "",
    val pingMs: Long? = null,
    val availableTypes: List<String> = emptyList(),
    val lastSeenAt: String? = null,
)

@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val type: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class StorageTarget(
    val path: String,
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    val writable: Boolean = true,
    val canFit: Boolean? = null,
)

@Serializable
data class StorageTargetsResponse(
    val nodeId: String = "",
    val nodeName: String = "",
    val defaultPath: String = "",
    val targets: List<StorageTarget> = emptyList(),
)

@Serializable
data class DownloadOption(
    val type: String,
    val supported: Boolean = false,
    val resumeSupported: Boolean = false,
    val segmentedPossible: Boolean = false,
    val message: String = "",
)

@Serializable
data class PreflightNode(
    val nodeId: String,
    val nodeName: String = "",
    val status: String = "unknown",
    val statusText: String = "",
    val pingMs: Long? = null,
    val url: String = "",
    val sizeBytes: Long? = null,
    val sizeKnown: Boolean = false,
    val recommendedType: String? = null,
    val supportedTypes: List<String> = emptyList(),
    val options: List<DownloadOption> = emptyList(),
    val defaultStoragePath: String? = null,
    val error: String? = null,
)

@Serializable
data class PreflightResponse(
    val url: String = "",
    val bestNodeId: String? = null,
    val nodes: List<PreflightNode> = emptyList(),
)

@Serializable
data class CreateJobRequest(
    val type: String,
    val url: String,
    val storagePath: String? = null,
    val profileId: String? = null,
    val nodeId: String? = null,
    val startImmediately: Boolean = true,
)
