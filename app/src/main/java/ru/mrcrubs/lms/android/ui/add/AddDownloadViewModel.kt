package ru.mrcrubs.lms.android.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.core.CreateJobRequest
import ru.mrcrubs.lms.core.LinkExtractor
import ru.mrcrubs.lms.core.StorageTarget

/** A node the job can go to, from the router preflight (or the node list for magnets). */
data class NodeChoice(
    val id: String,
    val name: String,
    val status: String,
    val statusText: String,
    val pingMs: Long?,
    val supportedTypes: List<String>,
    val recommendedType: String?,
    val sizeBytes: Long?,
    val defaultStoragePath: String?,
    val error: String?,
)

data class AddUiState(
    val url: String = "",
    val checking: Boolean = false,
    val checked: Boolean = false,
    val nodes: List<NodeChoice> = emptyList(),
    val selectedNodeId: String? = null,
    val type: String = LinkExtractor.TYPE_DIRECT,
    val storagePath: String = "",
    val storageTargets: List<StorageTarget> = emptyList(),
    val startImmediately: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val created: Boolean = false,
) {
    val selectedNode: NodeChoice? get() = nodes.firstOrNull { it.id == selectedNodeId }

    /** Types offered for the selected node; everything when the router did not say. */
    val availableTypes: List<String>
        get() = selectedNode?.supportedTypes?.takeIf { it.isNotEmpty() } ?: LinkExtractor.ALL_TYPES

    val isMagnet: Boolean get() = LinkExtractor.isMagnet(url)
    val canSubmit: Boolean get() = checked && !submitting && url.isNotBlank() && selectedNodeId != null
}

class AddDownloadViewModel(
    private val container: AppContainer,
    initialUrl: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState(url = initialUrl.orEmpty()))
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    private var checkJob: CoroutineJob? = null
    private var storageJob: CoroutineJob? = null
    /** The storage path last filled in automatically, so user edits are not overwritten. */
    private var autoStoragePath: String = ""

    init {
        if (!initialUrl.isNullOrBlank()) check()
    }

    fun setUrl(value: String) {
        checkJob?.cancel()
        _state.update {
            it.copy(url = value, checked = false, nodes = emptyList(), selectedNodeId = null, error = null, checking = false)
        }
    }

    fun setType(value: String) = _state.update { it.copy(type = value) }
    fun setStoragePath(value: String) = _state.update { it.copy(storagePath = value) }
    fun setStartImmediately(value: Boolean) = _state.update { it.copy(startImmediately = value) }

    fun check() {
        val url = LinkExtractor.extract(_state.value.url) ?: _state.value.url.trim()
        if (!LinkExtractor.isSupportedUrl(url)) {
            _state.update { it.copy(error = "Нужна ссылка http://, https:// или magnet:?") }
            return
        }
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _state.update { it.copy(url = url, checking = true, error = null) }
            try {
                val (nodes, bestNodeId) = if (LinkExtractor.isMagnet(url)) magnetNodes() else preflightNodes(url)
                if (nodes.isEmpty()) {
                    _state.update { it.copy(checking = false, error = "На роутере нет включённых нод") }
                    return@launch
                }
                val selected = bestNodeId?.takeIf { id -> nodes.any { it.id == id } } ?: nodes.first().id
                _state.update { it.copy(checking = false, checked = true, nodes = nodes) }
                selectNode(selected)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(checking = false, error = ex.message ?: "Ошибка проверки ссылки") }
            }
        }
    }

    fun selectNode(nodeId: String) {
        val node = _state.value.nodes.firstOrNull { it.id == nodeId } ?: return
        _state.update { s ->
            val type = when {
                s.type in node.supportedTypes -> s.type
                node.recommendedType != null -> node.recommendedType
                node.supportedTypes.isNotEmpty() -> node.supportedTypes.first()
                else -> LinkExtractor.suggestType(s.url)
            }
            val keepPath = s.storagePath.isNotBlank() && s.storagePath != autoStoragePath
            val path = if (keepPath) s.storagePath else node.defaultStoragePath.orEmpty()
            if (!keepPath) autoStoragePath = path
            s.copy(selectedNodeId = nodeId, type = type, storagePath = path, storageTargets = emptyList())
        }
        storageJob?.cancel()
        storageJob = viewModelScope.launch {
            try {
                val targets = container.router.storageTargets(nodeId, node.sizeBytes)
                _state.update { s ->
                    if (s.selectedNodeId != nodeId) return@update s
                    val useDefault = s.storagePath.isBlank() || s.storagePath == autoStoragePath
                    val path = if (useDefault && targets.defaultPath.isNotBlank()) targets.defaultPath else s.storagePath
                    if (useDefault) autoStoragePath = path
                    s.copy(storageTargets = targets.targets, storagePath = path)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                // Storage hints are optional; the path can still be typed in.
            }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                container.router.createJob(
                    CreateJobRequest(
                        type = s.type,
                        url = s.url.trim(),
                        storagePath = s.storagePath.trim().ifBlank { null },
                        nodeId = s.selectedNodeId,
                        startImmediately = s.startImmediately,
                    ),
                )
                _state.update { it.copy(submitting = false, created = true) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(submitting = false, error = ex.message ?: "Не удалось создать загрузку") }
            }
        }
    }

    private suspend fun preflightNodes(url: String): Pair<List<NodeChoice>, String?> {
        val response = container.router.preflight(url)
        val nodes = response.nodes.map { node ->
            NodeChoice(
                id = node.nodeId,
                name = node.nodeName.ifBlank { node.nodeId },
                status = node.status,
                statusText = node.statusText,
                pingMs = node.pingMs,
                supportedTypes = node.supportedTypes,
                recommendedType = node.recommendedType,
                sizeBytes = node.sizeBytes.takeIf { node.sizeKnown },
                defaultStoragePath = node.defaultStoragePath,
                error = node.error,
            )
        }
        return nodes to response.bestNodeId
    }

    /** The router cannot preflight magnets: offer enabled nodes and the TORRENT type. */
    private suspend fun magnetNodes(): Pair<List<NodeChoice>, String?> {
        val nodes = container.router.nodes().filter { it.enabled }.map { node ->
            NodeChoice(
                id = node.id,
                name = node.name.ifBlank { node.id },
                status = node.status,
                statusText = node.statusText,
                pingMs = node.pingMs,
                supportedTypes = listOf(LinkExtractor.TYPE_TORRENT),
                recommendedType = LinkExtractor.TYPE_TORRENT,
                sizeBytes = null,
                defaultStoragePath = null,
                error = null,
            )
        }
        val best = nodes.filter { it.status == "online" }.minByOrNull { it.pingMs ?: Long.MAX_VALUE }
        return nodes to (best ?: nodes.firstOrNull())?.id
    }
}
