package ru.mrcrubs.lms.android.ui.nodes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.mrcrubs.lms.android.LocalContainer
import ru.mrcrubs.lms.android.ui.MainViewModel
import ru.mrcrubs.lms.android.ui.common.EmptyState
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.NodeItem
import ru.mrcrubs.lms.core.NodeRequest
import ru.mrcrubs.lms.core.StorageTarget

private fun statusLabel(status: String) = when (status) {
    "online" -> "В сети"
    "offline" -> "Не в сети"
    "never_seen" -> "Ещё не отвечала"
    "disabled" -> "Выключена"
    else -> "Неизвестно"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "online" -> Color(0xFF16A34A)
    "offline" -> MaterialTheme.colorScheme.error
    "never_seen" -> Color(0xFFD97706)
    else -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<NodeItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ноды") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Нода") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (state.nodes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Dns,
                        title = "Нод пока нет",
                        text = "Добавьте lms-node: адрес, client ID и HMAC-секрет из её настроек.",
                    )
                }
            }
            items(state.nodes, key = { it.id }) { node -> NodeCard(node, onEdit = { editing = node }) }
        }
    }

    if (creating || editing != null) {
        NodeDialog(
            node = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { request, onError ->
                viewModel.saveNode(editing?.id, request) { error ->
                    if (error == null) {
                        creating = false
                        editing = null
                    } else {
                        onError(error)
                    }
                }
            },
        )
    }
}

@Composable
private fun NodeCard(node: NodeItem, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(node.status)))
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(node.name, style = MaterialTheme.typography.titleMedium)
                    Text(node.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Изменить") }
            }
            Text(
                listOfNotNull(
                    statusLabel(node.status),
                    node.pingMs?.let { "$it мс" },
                    node.availableTypes.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (node.status != "online" && node.statusText.isNotBlank()) {
                Text(node.statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (node.status == "online") StorageSection(node.id)
        }
    }
}

@Composable
private fun StorageSection(nodeId: String) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var targets by remember(nodeId) { mutableStateOf<List<StorageTarget>?>(null) }
    var error by remember(nodeId) { mutableStateOf<String?>(null) }
    val current = targets
    if (current == null) {
        OutlinedButton(onClick = {
            scope.launch {
                try {
                    targets = container.router.storageTargets(nodeId, null).targets
                } catch (ex: Exception) {
                    error = ex.message
                }
            }
        }) {
            Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp))
            Text("  Показать диски")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        return
    }
    if (current.isEmpty()) Text("Нет доступных папок", style = MaterialTheme.typography.bodySmall)
    current.forEach { target ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(target.path, style = MaterialTheme.typography.bodyMedium)
            val used = if (target.totalBytes > 0) 1f - target.freeBytes.toFloat() / target.totalBytes else 0f
            LinearProgressIndicator(progress = { used }, modifier = Modifier.fillMaxWidth())
            Text(
                "${Format.bytes(target.freeBytes)} свободно из ${Format.bytes(target.totalBytes)}" + if (!target.writable) " · только чтение" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NodeDialog(node: NodeItem?, onDismiss: () -> Unit, onSave: (NodeRequest, (String) -> Unit) -> Unit) {
    var name by remember { mutableStateOf(node?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(node?.baseUrl.orEmpty()) }
    var clientId by remember { mutableStateOf(node?.clientId ?: "router-main") }
    var secret by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(node?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = name.isNotBlank() && baseUrl.isNotBlank() && clientId.isNotBlank() && (node != null || secret.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (node == null) "Новая нода" else "Нода ${node.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Адрес") }, placeholder = { Text("http://192.168.99.13:8080") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(clientId, { clientId = it }, label = { Text("Client ID (HMAC)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    secret,
                    { secret = it },
                    label = { Text(if (node == null) "Секрет" else "Секрет (пусто — не менять)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Принимает загрузки", Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    error = null
                    onSave(NodeRequest(name.trim(), baseUrl.trim(), clientId.trim(), secret, enabled)) { error = it }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
