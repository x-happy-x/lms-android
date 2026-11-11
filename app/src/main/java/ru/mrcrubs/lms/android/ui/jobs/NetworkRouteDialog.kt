package ru.mrcrubs.lms.android.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.android.LocalContainer
import ru.mrcrubs.lms.android.ui.common.ChoiceField
import ru.mrcrubs.lms.core.ApiException
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobStatus
import ru.mrcrubs.lms.core.NetworkRoutes

/** A route change belongs to one job and never modifies a shared HomeNet selector. */
@Composable
fun NetworkRouteDialog(
    job: Job,
    canEdit: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?, (String?) -> Unit) -> Unit,
) {
    val container = LocalContainer.current
    var selected by remember(job.id) { mutableStateOf(job.networkRouteId.orEmpty()) }
    var routes by remember(job.id) { mutableStateOf<NetworkRoutes?>(null) }
    var loading by remember(job.id) { mutableStateOf(true) }
    var error by remember(job.id) { mutableStateOf<String?>(null) }
    var retry by remember(job.id) { mutableIntStateOf(0) }
    val active = job.status.isActive && job.type in listOf("DIRECT", "YTDLP", "ARIA2C")
    LaunchedEffect(job.id, job.nodeId, selected, retry) {
        loading = true
        error = null
        try {
            val nodeId = job.nodeId ?: throw ApiException("Нода задания недоступна")
            routes = withContext(Dispatchers.IO) {
                container.api()?.networkRoutes(nodeId, job.url, selected.ifBlank { null }) ?: throw ApiException("Подключите LMS")
            }
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (failure: Exception) {
            routes = null
            error = failure.message ?: "Не удалось получить маршруты"
        } finally {
            loading = false
        }
    }
    val available = routes?.routes.orEmpty().filter { it.available && job.type in it.supportedTypes }
    val choices = listOf("" to "Автоматически · правила сети") + available.map { it.id to it.label }
    val valid = selected.isEmpty() || available.any { it.id == selected }
    val canApply = canEdit && active && !busy && !loading && routes?.canSelect == true && valid && selected != job.networkRouteId.orEmpty()
    AlertDialog(
        onDismissRequest = { if(!busy) onDismiss() },
        title = { Text("Изменить маршрут") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text("Настроено: ${job.networkRouteLabel ?: "Автоматически"}", style = MaterialTheme.typography.bodySmall)
                if(loading) LinearProgressIndicator()
                if(routes != null) {
                    ChoiceField("Новый маршрут", selected, choices, { selected = it }, enabled = canEdit && active && !busy && !loading && routes?.canSelect == true)
                    Text(routes?.planned?.label ?: "Планируемый выход не подтверждён", style = MaterialTheme.typography.bodySmall)
                    if(routes?.observed?.isNotEmpty() == true) {
                        Text("Наблюдаемые соединения", style = MaterialTheme.typography.labelLarge)
                        routes?.observed?.forEach { Text("${it.host}: ${it.chains.joinToString(" → ")}", style = MaterialTheme.typography.bodySmall) }
                    } else Text("Подтверждённых соединений сейчас нет.", style = MaterialTheme.typography.bodySmall)
                    routes?.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if(routes?.canSelect != true) Text("Смена маршрута на этой ноде недоступна.", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    when {
                        !active -> "Завершённые задания сохраняют исторический маршрут. Изменение недоступно."
                        job.status == JobStatus.PAUSED -> "Загрузка останется на паузе. Новый маршрут применится при продолжении."
                        else -> "Задание будет перезапущено с новым маршрутом. Докачка продолжится, если её поддерживает источник."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if(error != null && !busy) TextButton(enabled = !loading, onClick = { retry++ }) { Text("Обновить маршруты") }
            }
        },
        confirmButton = {
            TextButton(enabled = canApply, onClick = {
                error = null
                onSave(selected.ifBlank { null }) { failure -> if(failure == null) onDismiss() else error = failure }
            }) { Text(if(busy) "Применяется…" else if(job.status == JobStatus.PAUSED) "Сохранить маршрут" else "Сменить и перезапустить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
    )
}
