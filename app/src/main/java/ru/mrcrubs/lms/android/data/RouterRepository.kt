package ru.mrcrubs.lms.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.core.ApiException
import ru.mrcrubs.lms.core.CreateJobRequest
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.NodeItem
import ru.mrcrubs.lms.core.PreflightResponse
import ru.mrcrubs.lms.core.RouterApi
import ru.mrcrubs.lms.core.RouterConfig
import ru.mrcrubs.lms.core.StorageTargetsResponse

/** Suspending access to the router API built from the current settings. */
class RouterRepository(private val settings: SettingsRepository) {
    private val client = RouterApi.defaultClient()

    private suspend fun <T> call(block: RouterApi.() -> T): T {
        val config = settings.current().routerConfig()
            ?: throw ApiException("Укажите адрес роутера в настройках")
        return withContext(Dispatchers.IO) { RouterApi(config, client).block() }
    }

    suspend fun jobs(activeOnly: Boolean = false): List<Job> = call { jobs(activeOnly) }
    suspend fun pause(id: String): Job = call { pause(id) }
    suspend fun resume(id: String): Job = call { resume(id) }
    suspend fun cancel(id: String): Job = call { cancel(id) }
    suspend fun retry(id: String): Job = call { retry(id) }
    suspend fun preflight(url: String): PreflightResponse = call { preflight(url) }
    suspend fun nodes(): List<NodeItem> = call { nodes(enabledOnly = true) }
    suspend fun storageTargets(nodeId: String, requiredBytes: Long?): StorageTargetsResponse =
        call { storageTargets(nodeId, requiredBytes) }
    suspend fun createJob(request: CreateJobRequest): Job = call { createJob(request) }

    /** Checks a not-yet-saved configuration; returns the router version. */
    suspend fun testConnection(config: RouterConfig): String = withContext(Dispatchers.IO) {
        val api = RouterApi(config, client)
        api.health()
        api.version().ifBlank { "?" }
    }
}
