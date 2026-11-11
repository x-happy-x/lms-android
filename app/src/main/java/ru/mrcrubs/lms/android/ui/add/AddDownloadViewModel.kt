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
import ru.mrcrubs.lms.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
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
    /** Bytes/s, null = unlimited; starts from the default in the settings. */
    val maxSpeedBytes: Long? = null,
    /** Set once the default limit is read, so the picker starts from it. */
    val speedDefaultLoaded: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val created: Boolean = false,
    val media: MediaExtract? = null,
    val mediaLoading: Boolean = false,
    val mediaError: String? = null,
    val selectedEntries: Set<String> = emptySet(),
    val wholePlaylist: Boolean = true,
    val maxHeight: Int? = null,
    val entryHeights: Map<String,Int> = emptyMap(),
    val routes: NetworkRoutes? = null,
    val networkRouteId: String? = null,

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
        viewModelScope.launch {
            val limit = container.settings.current().defaultSpeedLimit
            _state.update { it.copy(maxSpeedBytes = limit, speedDefaultLoaded = true) }
        }
        if (!initialUrl.isNullOrBlank()) check()
    }

    fun setUrl(value: String) {
        checkJob?.cancel()
        _state.update {
            it.copy(url = value, media = null, mediaError = null, routes = null, selectedEntries = emptySet(), checked = false, nodes = emptyList(), selectedNodeId = null, error = null, checking = false)
        }
    }

    fun setType(value: String) {
        _state.update { it.copy(type=value) }
        val state=_state.value
        if(state.networkRouteId!=null && state.routes?.routes?.any{it.id==state.networkRouteId && value in it.supportedTypes}!=true)setNetworkRoute(null)
    }
    fun setStoragePath(value: String) = _state.update { it.copy(storagePath = value) }
    fun setStartImmediately(value: Boolean) = _state.update { it.copy(startImmediately = value) }
    fun setSpeedLimit(value: Long?) = _state.update { it.copy(maxSpeedBytes = value) }

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
                // The router preflights magnets too (TORRENT on online nodes).
                val (nodes, bestNodeId) = preflightNodes(url)
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
            s.copy(selectedNodeId = nodeId, type = type, storagePath = path, storageTargets = emptyList(),routes=null,networkRouteId=null)
        }
        loadRoutes(nodeId)
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

    fun setMaxHeight(value: Int?) = _state.update { it.copy(maxHeight = value) }
    fun setEntryHeight(url: String, value: Int) = _state.update { it.copy(entryHeights = it.entryHeights + (url to value)) }
    fun setNetworkRoute(value: String?) {
        _state.update { it.copy(networkRouteId=value) }
        _state.value.selectedNodeId?.let{loadRoutes(it,value)}
    }
    fun setWholePlaylist(value: Boolean) = _state.update { it.copy(wholePlaylist = value) }
    fun toggleEntry(url: String) = _state.update { it.copy(selectedEntries = if(url in it.selectedEntries) it.selectedEntries-url else it.selectedEntries+url) }
    fun inspectMedia() { viewModelScope.launch {
        val before = _state.value
        if(before.selectedNodeId==null || before.mediaLoading)return@launch
        _state.update { it.copy(mediaLoading = true, mediaError = null) }
        try {
            val result = withContext(Dispatchers.IO) { container.api()?.extractMedia(before.url,before.selectedNodeId,before.networkRouteId) ?: throw ApiException("Подключите LMS") }
            if(_state.value.url==before.url&&_state.value.selectedNodeId==before.selectedNodeId) _state.update { it.copy(type="YTDLP",media=result,selectedEntries=result.entries.map { entry->entry.url }.toSet()) }
        } catch(error: CancellationException){throw error}
        catch(error:Exception){_state.update { it.copy(mediaError=error.message) }}
        finally{_state.update { it.copy(mediaLoading=false) }}
    } }
    private fun loadRoutes(nodeId: String,selectedRoute:String?=null) { viewModelScope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { container.api()?.networkRoutes(nodeId,_state.value.url,selectedRoute) } }.getOrNull()
        if(_state.value.selectedNodeId==nodeId&&_state.value.networkRouteId==selectedRoute)_state.update { it.copy(routes=result) }
    } }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            try {
                val media = s.media
                val playlist = media?.takeIf { it.kind=="playlist" }
                val batchId=UUID.randomUUID().toString()
                val chosen = if(playlist!=null && !s.wholePlaylist) playlist.entries.filter { it.url in s.selectedEntries } else emptyList()
                require(playlist==null || s.wholePlaylist || chosen.isNotEmpty()) { "Выберите хотя бы один ролик" }
                val urls = if(playlist!=null && !s.wholePlaylist) chosen.map { it.url } else listOf(s.url.trim())
                var completed=0
                try { for(url in urls) {
                    val index=playlist?.entries?.indexOfFirst { it.url==url }?.takeIf { it>=0 }?.plus(1)
                    container.router.createJob(CreateJobRequest(
                        type=if(media!=null)"YTDLP" else s.type,url=url,
                        storagePath=s.storagePath.trim().ifBlank { null },nodeId=s.selectedNodeId,
                        startImmediately=s.startImmediately,maxSpeedBytes=s.maxSpeedBytes?.takeIf { it>0 },networkRouteId=s.networkRouteId,
                        ytdlpOptions=if(media!=null)YtdlpOptions(playlist=playlist!=null&&s.wholePlaylist,maxHeight=s.entryHeights[url]?.takeIf { it>0 } ?: s.maxHeight)else null,
                        playlist=playlist?.let { PlaylistSnapshot(batchId,it.url,it.title,it.thumbnail,it.entryCount,index,if(s.wholePlaylist)it.entries else null) },
                    ))
                    completed++
                    _state.update { it.copy(selectedEntries=it.selectedEntries-url) }
                } } catch(error:Exception) { throw ApiException("Добавлено $completed из ${urls.size}. ${error.message.orEmpty()}") }
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
}
