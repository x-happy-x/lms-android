package ru.mrcrubs.lms.android.ui.jobs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.android.ui.factory
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    container: AppContainer,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: JobsViewModel = viewModel(factory = factory { JobsViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmCancel by remember { mutableStateOf<Job?>(null) }

    // Poll only while the screen is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startPolling()
                Lifecycle.Event.ON_STOP -> viewModel.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPolling()
        }
    }

    LaunchedEffect(state.actionError) {
        val message = state.actionError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissActionError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Загрузки") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.configured) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Добавить") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!state.configured) {
                NotConfigured(onSettings)
                return@Column
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !state.activeOnly,
                    onClick = { viewModel.setActiveOnly(false) },
                    label = { Text("Все (${state.jobs.size})") },
                )
                FilterChip(
                    selected = state.activeOnly,
                    onClick = { viewModel.setActiveOnly(true) },
                    label = { Text("Активные (${state.jobs.count { it.status.isActive }})") },
                )
            }
            state.error?.let { ErrorBanner(it) }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.visibleJobs.isEmpty() -> EmptyState(state.activeOnly)
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.visibleJobs, key = { it.id }) { job ->
                            JobCard(
                                job = job,
                                busy = job.id in state.busyJobIds,
                                onPause = { viewModel.pause(job) },
                                onResume = { viewModel.resume(job) },
                                onRetry = { viewModel.retry(job) },
                                onCancel = { confirmCancel = job },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmCancel?.let { job ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text("Отменить загрузку?") },
            text = { Text(job.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel(job)
                    confirmCancel = null
                }) { Text("Отменить загрузку") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = null }) { Text("Назад") }
            },
        )
    }
}

@Composable
private fun JobCard(
    job: Job,
    busy: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        job.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(job.status)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            job.type,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    JobActions(job, onPause, onResume, onRetry, onCancel)
                }
            }
            val percent = job.percent
            if (job.status == JobStatus.RUNNING || job.status == JobStatus.PAUSED || job.status == JobStatus.QUEUED) {
                Spacer(Modifier.height(8.dp))
                if (percent != null) {
                    LinearProgressIndicator(
                        progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    )
                } else if (job.status == JobStatus.RUNNING) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 12.dp))
                }
            }
            val details = listOfNotNull(
                Format.percent(percent).takeIf { job.status != JobStatus.DONE },
                progressSize(job),
                Format.speed(job.speedBytes).takeIf { job.status == JobStatus.RUNNING },
                Format.eta(job.etaSeconds)?.takeIf { job.status == JobStatus.RUNNING }?.let { "осталось $it" },
            )
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            val note = if (job.status == JobStatus.ERROR) job.errorText ?: job.message else job.message
            if (!note.isNullOrBlank() && job.status != JobStatus.RUNNING) {
                Spacer(Modifier.height(4.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (job.status == JobStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun progressSize(job: Job): String? {
    if (job.status == JobStatus.DONE) return job.outputSizeBytes?.let { Format.bytes(it) }
    val total = job.totalBytes ?: return null
    val done = job.percent?.let { (total * it / 100).toLong() }
    return if (done != null) "${Format.bytes(done)} из ${Format.bytes(total)}" else Format.bytes(total)
}

@Composable
private fun JobActions(
    job: Job,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Row {
        if (job.canPause) {
            IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, contentDescription = "Пауза") }
        }
        if (job.canResume) {
            IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, contentDescription = "Продолжить") }
        }
        if (job.canRetry) {
            IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "Повторить") }
        }
        if (job.canCancel) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Отменить") }
        }
    }
}

@Composable
private fun StatusBadge(status: JobStatus) {
    val color = when (status) {
        JobStatus.RUNNING -> MaterialTheme.colorScheme.primary
        JobStatus.DONE -> Color(0xFF2E7D32)
        JobStatus.ERROR -> MaterialTheme.colorScheme.error
        JobStatus.PAUSED, JobStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.15f), contentColor = color, shape = MaterialTheme.shapes.small) {
        Text(
            Format.status(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(activeOnly: Boolean) {
    // LazyColumn keeps pull-to-refresh working on an empty list.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                if (activeOnly) "Нет активных загрузок" else "Загрузок пока нет.\nНажмите «Добавить» или поделитесь ссылкой из другого приложения.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}

@Composable
private fun NotConfigured(onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Роутер не настроен", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Укажите адрес веб-интерфейса LMS на роутере, например 192.168.1.1:8082.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSettings) { Text("Открыть настройки") }
    }
}
