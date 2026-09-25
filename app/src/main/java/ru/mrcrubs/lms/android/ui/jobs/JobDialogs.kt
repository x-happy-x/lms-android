package ru.mrcrubs.lms.android.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mrcrubs.lms.android.ui.common.SpeedLimitPicker
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.NodeItem

@Composable
fun EditUrlDialog(job: Job, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(job.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить ссылку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(job.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Новая ссылка") }, modifier = Modifier.fillMaxWidth())
                Text("Загрузка продолжится с новой ссылки.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }, enabled = url.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun MoveDialog(job: Job, nodes: List<NodeItem>, onDismiss: () -> Unit, onMove: (String?, String?) -> Unit) {
    var nodeId by remember { mutableStateOf(job.nodeId ?: nodes.firstOrNull()?.id) }
    var path by remember { mutableStateOf(job.storagePath.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(job.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                nodes.forEach { node ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = nodeId == node.id, onClick = { nodeId = node.id }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = nodeId == node.id, onClick = null)
                        Text(if (node.id == job.nodeId) "${node.name} (текущая)" else node.name)
                    }
                }
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Папка (по умолчанию — папка ноды)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (nodeId != job.nodeId) {
                    Text("Файл передадут через роутер и удалят с текущей ноды.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(nodeId, path) }) { Text("Переместить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun SpeedDialog(job: Job, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var limit by remember { mutableStateOf(job.maxSpeedBytes?.takeIf { it > 0 }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ограничение скорости") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(job.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SpeedLimitPicker(value = limit, onChange = { limit = it })
                Text(
                    "HTTP-загрузки меняют скорость сразу, остальные перезапускаются и продолжают с того же места.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(limit) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun CancelDialog(job: Job, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отменить загрузку?") },
        text = { Text(job.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Отменить загрузку") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Назад") } },
    )
}
