package ru.mrcrubs.lms.core

import kotlinx.serialization.Serializable

@Serializable data class YtdlpOptions(val playlist: Boolean? = null, val maxHeight: Int? = null, val maxFps: Int? = null, val audioOnly: Boolean? = null, val container: String? = null, val impersonate: String? = null)
@Serializable data class PlaylistEntry(val url: String, val title: String? = null, val thumbnail: String? = null, val durationSeconds: Long? = null)
@Serializable data class PlaylistSnapshot(val id: String, val url: String, val title: String? = null, val thumbnail: String? = null, val entryCount: Int? = null, val index: Int? = null, val entries: List<PlaylistEntry>? = null)
@Serializable data class MediaExtract(val url: String, val kind: String, val title: String? = null, val thumbnail: String? = null, val entryCount: Int? = null, val truncated: Boolean = false, val entries: List<PlaylistEntry> = emptyList(), val nodeId: String? = null, val nodeName: String? = null)
@Serializable data class MediaRequest(val url: String, val nodeId: String? = null, val maxHeight: Int? = null, val networkRouteId: String? = null)
@Serializable data class MediaVariant(val height: Int = 0, val fps: Int = 0, val quality: String = "", val sizeBytes: Long? = null, val sizeEstimated: Boolean = false)
@Serializable data class MediaInfo(val title: String? = null, val quality: String = "", val height: Int = 0, val fps: Int = 0, val sizeBytes: Long? = null, val sizeEstimated: Boolean = false, val variants: List<MediaVariant> = emptyList())
@Serializable data class MediaPreview(val streamUrl: String, val info: MediaInfo? = null)
@Serializable data class FileEntry(val name: String, val path: String, val directory: Boolean, val sizeBytes: Long? = null, val modifiedAt: String? = null, val mimeType: String? = null)
@Serializable data class FileListing(val root: String, val path: String = "", val parentPath: String? = null, val entries: List<FileEntry> = emptyList(), val truncated: Boolean = false)
@Serializable data class FileMutation(val root: String, val path: String, val name: String? = null)
@Serializable data class NetworkPlan(val state: String = "unknown", val routeId: String? = null, val label: String? = null, val scope: String = "download")
@Serializable data class NetworkObservation(val host: String = "", val chains: List<String> = emptyList(), val rule: String? = null, val startedAt: String? = null, val observedAt: String? = null)
@Serializable data class NetworkRoute(val id: String, val label: String, val available: Boolean = false, val supportedTypes: List<String> = emptyList(), val scope: String = "download")
@Serializable data class NetworkRoutes(val enabled: Boolean = false, val canSelect: Boolean = false, val nodeId: String = "", val planned: NetworkPlan = NetworkPlan(), val observed: List<NetworkObservation> = emptyList(), val routes: List<NetworkRoute> = emptyList(), val checkedAt: String? = null, val message: String? = null)
@Serializable data class AuthConfig(val enabled: Boolean = false, val accountOrigin: String = "")
@Serializable data class AccountUser(val login: String = "", val displayName: String? = null, val role: String = "viewer")
@Serializable data class AuthSession(val enabled: Boolean = false, val user: AccountUser? = null)
