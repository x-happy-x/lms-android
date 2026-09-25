package ru.mrcrubs.lms.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.core.ApiException
import ru.mrcrubs.lms.core.CreateJobRequest
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobPlan
import ru.mrcrubs.lms.core.JobPlanner
import ru.mrcrubs.lms.core.MoveJobRequest
import ru.mrcrubs.lms.core.NodeItem
import ru.mrcrubs.lms.core.NodeRequest
import ru.mrcrubs.lms.core.Profile
import ru.mrcrubs.lms.core.ProfileRequest
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
    suspend fun nodes(enabledOnly: Boolean = false): List<NodeItem> = call { nodes(enabledOnly) }
    suspend fun profiles(): List<Profile> = call { profiles(enabledOnly = false) }
    suspend fun updateJobUrl(id: String, url: String): Job = call { updateJobUrl(id, url) }
    suspend fun moveJob(id: String, request: MoveJobRequest): Job = call { moveJob(id, request) }
    suspend fun createNode(request: NodeRequest): NodeItem = call { createNode(request) }
    suspend fun updateNode(id: String, request: NodeRequest): NodeItem = call { updateNode(id, request) }
    suspend fun createProfile(request: ProfileRequest): Profile = call { createProfile(request) }

    /** Client for building file/preview URLs and auth headers (no network I/O). */
    fun api(config: RouterConfig): RouterApi = RouterApi(config, client)
    suspend fun storageTargets(nodeId: String, requiredBytes: Long?): StorageTargetsResponse =
        call { storageTargets(nodeId, requiredBytes) }
    suspend fun createJob(request: CreateJobRequest): Job = call { createJob(request) }
    suspend fun setSpeedLimit(id: String, maxSpeedBytes: Long?): Job = call { setSpeedLimit(id, maxSpeedBytes) }

    /** Adds a link without the add screen: router preflight, then the planned node and type. */
    suspend fun addQuickly(url: String, maxSpeedBytes: Long?): Pair<Job, JobPlan> = call {
        val plan = JobPlanner.plan(preflight(url), url)
        val job = createJob(
            CreateJobRequest(
                type = plan.type,
                url = url,
                storagePath = plan.storagePath,
                nodeId = plan.nodeId,
                maxSpeedBytes = maxSpeedBytes?.takeIf { it > 0 },
            ),
        )
        job to plan
    }

    /** Checks a not-yet-saved configuration; returns the router version. */
    suspend fun testConnection(config: RouterConfig): String = withContext(Dispatchers.IO) {
        val api = RouterApi(config, client)
        api.health()
        api.version().ifBlank { "?" }
    }
}
