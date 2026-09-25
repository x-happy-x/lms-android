package ru.mrcrubs.lms.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.core.FileKind
import ru.mrcrubs.lms.core.GroupKey
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobsQuery
import ru.mrcrubs.lms.core.MoveJobRequest
import ru.mrcrubs.lms.core.NodeItem
import ru.mrcrubs.lms.core.NodeRequest
import ru.mrcrubs.lms.core.Profile
import ru.mrcrubs.lms.core.ProfileRequest
import ru.mrcrubs.lms.core.SortKey
import ru.mrcrubs.lms.core.StatusFilter
import ru.mrcrubs.lms.core.downloadFileName

data class MainUiState(
    val configured: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val jobs: List<Job> = emptyList(),
    val nodes: List<NodeItem> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val error: String? = null,
    val busyJobIds: Set<String> = emptySet(),
    val query: JobsQuery = JobsQuery(),
    val gridView: Boolean = false,
)

/**
 * State shared by all screens: jobs, nodes and profiles from the router, polled while the
 * app is visible, plus the job list filters. Messages for the snackbar go to [messages].
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private var polling: CoroutineJob? = null
    private var polls = 0

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            _state.update {
                it.copy(
                    query = it.query.copy(
                        sort = SortKey.entries.firstOrNull { key -> key.name == settings.sort } ?: SortKey.NEWEST,
                        group = GroupKey.entries.firstOrNull { key -> key.name == settings.group } ?: GroupKey.STATUS,
                    ),
                    gridView = settings.gridView,
                )
            }
        }
    }

    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                load(full = polls % 5 == 0)
                polls++
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            load(full = true)
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun consumeMessage() {
        _messages.value = null
    }

    // Filters
    fun setSearch(value: String) = updateQuery { it.copy(search = value) }
    fun setStatus(value: StatusFilter) = updateQuery { it.copy(status = value) }
    fun toggleKind(kind: FileKind) = updateQuery { q -> q.copy(kinds = if (kind in q.kinds) q.kinds - kind else q.kinds + kind) }
    fun setNode(nodeId: String?) = updateQuery { it.copy(nodeId = nodeId) }
    fun resetFilters() = updateQuery { it.copy(status = StatusFilter.ALL, kinds = emptySet(), nodeId = null, search = "") }

    fun setSort(value: SortKey) {
        updateQuery { it.copy(sort = value) }
        viewModelScope.launch { container.settings.update { it.copy(sort = value.name) } }
    }

    fun setGroup(value: GroupKey) {
        updateQuery { it.copy(group = value) }
        viewModelScope.launch { container.settings.update { it.copy(group = value.name) } }
    }

    fun setGridView(value: Boolean) {
        _state.update { it.copy(gridView = value) }
        viewModelScope.launch { container.settings.update { it.copy(gridView = value) } }
    }

    private fun updateQuery(transform: (JobsQuery) -> JobsQuery) = _state.update { it.copy(query = transform(it.query)) }

    // Job actions
    fun pause(job: Job) = jobAction(job) { container.router.pause(it) }
    fun resume(job: Job) = jobAction(job) { container.router.resume(it) }
    fun retry(job: Job) = jobAction(job) { container.router.retry(it) }
    fun cancel(job: Job) = jobAction(job) { container.router.cancel(it) }

    fun updateUrl(job: Job, url: String, onDone: () -> Unit) =
        jobAction(job, onDone) { container.router.updateJobUrl(it, url.trim()) }

    fun move(job: Job, nodeId: String?, storagePath: String?, onDone: () -> Unit) = jobAction(job, onDone) {
        container.router.moveJob(it, MoveJobRequest(targetNodeId = nodeId, storagePath = storagePath?.trim()?.ifBlank { null }))
    }

    fun downloadToDevice(job: Job) {
        val api = container.api() ?: return
        try {
            container.deviceDownloads.enqueue(job, api)
            _messages.value = "Скачивается в ${container.deviceDownloads.locationHint}: ${job.downloadFileName}"
        } catch (ex: Exception) {
            _messages.value = ex.message ?: "Не удалось начать скачивание"
        }
    }

    // Nodes and profiles
    fun saveNode(id: String?, request: NodeRequest, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                if (id == null) container.router.createNode(request) else container.router.updateNode(id, request)
                onResult(null)
                load(full = true)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                onResult(ex.message ?: "Ошибка")
            }
        }
    }

    fun createProfile(request: ProfileRequest, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.router.createProfile(request)
                onResult(null)
                load(full = true)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                onResult(ex.message ?: "Ошибка")
            }
        }
    }

    private fun jobAction(job: Job, onDone: () -> Unit = {}, block: suspend (String) -> Job) {
        viewModelScope.launch {
            _state.update { it.copy(busyJobIds = it.busyJobIds + job.id) }
            try {
                val updated = block(job.id)
                _state.update { s -> s.copy(jobs = s.jobs.map { if (it.id == updated.id) updated else it }) }
                onDone()
                load(full = false)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _messages.value = ex.message ?: "Ошибка"
            } finally {
                _state.update { it.copy(busyJobIds = it.busyJobIds - job.id) }
            }
        }
    }

    private suspend fun load(full: Boolean) {
        val settings = container.settings.current()
        if (!settings.isConfigured) {
            _state.update { it.copy(configured = false, loading = false, jobs = emptyList(), nodes = emptyList(), error = null) }
            return
        }
        try {
            val jobs = container.router.jobs(activeOnly = false)
            _state.update { it.copy(jobs = jobs, configured = true, loading = false, error = null) }
            container.monitor.process(jobs)
            if (full || _state.value.nodes.isEmpty()) {
                val nodes = container.router.nodes()
                val profiles = container.router.profiles()
                _state.update { it.copy(nodes = nodes, profiles = profiles) }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            _state.update { it.copy(configured = true, loading = false, error = ex.message ?: "Ошибка") }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
