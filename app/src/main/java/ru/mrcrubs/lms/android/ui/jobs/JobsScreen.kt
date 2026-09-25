package ru.mrcrubs.lms.android.ui.jobs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mrcrubs.lms.android.ui.MainUiState
import ru.mrcrubs.lms.android.ui.MainViewModel
import ru.mrcrubs.lms.android.ui.common.EmptyState
import ru.mrcrubs.lms.android.ui.common.ErrorBanner
import ru.mrcrubs.lms.android.ui.common.color
import ru.mrcrubs.lms.android.ui.common.icon
import ru.mrcrubs.lms.core.FileKind
import ru.mrcrubs.lms.core.GroupKey
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobsView
import ru.mrcrubs.lms.core.SortKey
import ru.mrcrubs.lms.core.StatusFilter
import androidx.compose.material.icons.filled.Download

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    viewModel: MainViewModel,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onOpenMedia: (Job) -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editUrl by remember { mutableStateOf<Job?>(null) }
    var move by remember { mutableStateOf<Job?>(null) }
    var speed by remember { mutableStateOf<Job?>(null) }
    var cancel by remember { mutableStateOf<Job?>(null) }

    val callbacks = remember(viewModel) {
        JobCallbacks(
            pause = viewModel::pause,
            resume = viewModel::resume,
            retry = viewModel::retry,
            cancel = { cancel = it },
            download = viewModel::downloadToDevice,
            open = onOpenMedia,
            editUrl = { editUrl = it },
            move = { move = it },
            speed = { speed = it },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Загрузки") },
                actions = {
                    SortMenu(state.query.sort, viewModel::setSort)
                    GroupMenu(state.query.group, viewModel::setGroup)
                    IconButton(onClick = { viewModel.setGridView(!state.gridView) }) {
                        Icon(if (state.gridView) Icons.Filled.ViewAgenda else Icons.Filled.GridView, if (state.gridView) "Список" else "Плитка")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.configured) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Загрузка") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.configured) {
                EmptyState(
                    icon = Icons.Filled.Download,
                    title = "Роутер не настроен",
                    text = "Укажите адрес веб-интерфейса LMS на роутере, например 192.168.1.1:8082.",
                    action = { Button(onClick = onSettings) { Text("Открыть настройки") } },
                )
                return@Column
            }
            Filters(state, viewModel)
            state.error?.let { ErrorBanner(it) }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                JobsList(state, callbacks, onAdd, viewModel::resetFilters)
            }
        }
    }

    editUrl?.let { job -> EditUrlDialog(job, onDismiss = { editUrl = null }) { url -> viewModel.updateUrl(job, url) { editUrl = null } } }
    move?.let { job ->
        MoveDialog(job, state.nodes, onDismiss = { move = null }) { nodeId, path -> viewModel.move(job, nodeId, path) { move = null } }
    }
    speed?.let { job ->
        SpeedDialog(job, onDismiss = { speed = null }) { limit -> viewModel.setSpeedLimit(job, limit) { speed = null } }
    }
    cancel?.let { job -> CancelDialog(job, onDismiss = { cancel = null }) { viewModel.cancel(job); cancel = null } }
}

@Composable
private fun Filters(state: MainUiState, viewModel: MainViewModel) {
    val counts = remember(state.jobs) { JobsView.counts(state.jobs) }
    val kindCounts = remember(state.jobs, state.query.status) {
        state.jobs.filter(state.query.status::matches).groupingBy { FileKind.of(it) }.eachCount()
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query.search,
            onValueChange = viewModel::setSearch,
            placeholder = { Text("Поиск по имени и ссылке") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.search.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearch("") }) { Icon(Icons.Filled.Clear, "Очистить") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.query.status == filter,
                    onClick = { viewModel.setStatus(filter) },
                    label = { Text("${filter.label} ${counts[filter] ?: 0}") },
                )
            }
            NodeFilter(state, viewModel)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileKind.entries.filter { (kindCounts[it] ?: 0) > 0 || it in state.query.kinds }.forEach { kind ->
                val color = kind.color()
                FilterChip(
                    selected = kind in state.query.kinds,
                    onClick = { viewModel.toggleKind(kind) },
                    label = { Text("${kind.label} ${kindCounts[kind] ?: 0}") },
                    leadingIcon = { Icon(kind.icon(), contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f)),
                )
            }
            if (state.query.isFiltered) {
                TextButton(onClick = viewModel::resetFilters) { Text("Сбросить") }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun NodeFilter(state: MainUiState, viewModel: MainViewModel) {
    if (state.nodes.size < 2 && state.query.nodeId == null) return
    var open by remember { mutableStateOf(false) }
    val selected = state.nodes.firstOrNull { it.id == state.query.nodeId }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected?.name ?: "Все ноды") },
            leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Все ноды") }, onClick = { viewModel.setNode(null); open = false })
            state.nodes.forEach { node ->
                DropdownMenuItem(text = { Text(node.name) }, onClick = { viewModel.setNode(node.id); open = false })
            }
        }
    }
}

@Composable
private fun SortMenu(current: SortKey, onSelect: (SortKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.SwapVert, "Сортировка") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.label) },
                    trailingIcon = { if (key == current) Icon(Icons.Filled.Check, null) },
                    onClick = { onSelect(key); open = false },
                )
            }
        }
    }
}

@Composable
private fun GroupMenu(current: GroupKey, onSelect: (GroupKey) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Layers, "Группировка") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GroupKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key.label) },
                    trailingIcon = { if (key == current) Icon(Icons.Filled.Check, null) },
                    onClick = { onSelect(key); open = false },
                )
            }
        }
    }
}

@Composable
private fun JobsList(state: MainUiState, callbacks: JobCallbacks, onAdd: () -> Unit, onReset: () -> Unit) {
    val groups = remember(state.jobs, state.query, state.nodes) { JobsView.apply(state.jobs, state.query, state.nodes) }
    val total = groups.sumOf { it.jobs.size }
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        return
    }
    val nodeById = remember(state.nodes) { state.nodes.associateBy { it.id } }
    // One grid for both modes keeps pull-to-refresh working on empty and short lists.
    LazyVerticalGrid(
        columns = if (state.gridView) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (total == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (state.jobs.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Download,
                        title = "Загрузок пока нет",
                        text = "Нажмите «Загрузка» или поделитесь ссылкой из другого приложения.",
                        action = { Button(onClick = onAdd) { Text("Новая загрузка") } },
                    )
                } else {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "Ничего не найдено",
                        text = "Измените фильтры или строку поиска.",
                        action = { TextButton(onClick = onReset) { Text("Сбросить фильтры") } },
                    )
                }
            }
        }
        groups.forEach { group ->
            if (group.label.isNotEmpty()) {
                item(key = "header-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(group.label, group.jobs.size)
                }
            }
            items(group.jobs, key = { it.id }) { job ->
                val busy = job.id in state.busyJobIds
                if (state.gridView) JobTile(job, busy, callbacks)
                else JobRow(job, job.nodeId?.let(nodeById::get), busy, callbacks)
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int) {
    Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Text("$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}
