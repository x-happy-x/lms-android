package ru.mrcrubs.lms.android.ui.jobs

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
import ru.mrcrubs.lms.core.Job

data class JobsUiState(
    val jobs: List<Job> = emptyList(),
    val activeOnly: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val configured: Boolean = true,
    val error: String? = null,
    val busyJobIds: Set<String> = emptySet(),
    val actionError: String? = null,
) {
    val visibleJobs: List<Job> get() = if (activeOnly) jobs.filter { it.status.isActive } else jobs
}

class JobsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(JobsUiState())
    val state: StateFlow<JobsUiState> = _state.asStateFlow()

    private var polling: CoroutineJob? = null

    /** Polls while the screen is visible (started/stopped from the lifecycle). */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                load()
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
            load()
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun setActiveOnly(value: Boolean) = _state.update { it.copy(activeOnly = value) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }

    fun pause(job: Job) = action(job) { container.router.pause(it) }
    fun resume(job: Job) = action(job) { container.router.resume(it) }
    fun retry(job: Job) = action(job) { container.router.retry(it) }
    fun cancel(job: Job) = action(job) { container.router.cancel(it) }

    private fun action(job: Job, block: suspend (String) -> Job) {
        viewModelScope.launch {
            _state.update { it.copy(busyJobIds = it.busyJobIds + job.id) }
            try {
                val updated = block(job.id)
                _state.update { s -> s.copy(jobs = s.jobs.map { if (it.id == updated.id) updated else it }) }
                load()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(actionError = ex.message ?: "Ошибка") }
            } finally {
                _state.update { it.copy(busyJobIds = it.busyJobIds - job.id) }
            }
        }
    }

    private suspend fun load() {
        val settings = container.settings.current()
        if (!settings.isConfigured) {
            _state.update { it.copy(configured = false, loading = false, jobs = emptyList(), error = null) }
            return
        }
        try {
            val jobs = container.router.jobs(activeOnly = false)
            _state.update { it.copy(jobs = jobs, configured = true, loading = false, error = null) }
            container.monitor.process(jobs)
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
