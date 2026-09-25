package ru.mrcrubs.lms.android.ui.add

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.android.ui.factory
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.LinkExtractor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadScreen(
    container: AppContainer,
    initialUrl: String?,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    // Keyed by the URL so a newly shared link gets a fresh form.
    val viewModel: AddDownloadViewModel = viewModel(
        key = "add:${initialUrl.orEmpty()}",
        factory = factory { AddDownloadViewModel(container, initialUrl) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новая загрузка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::setUrl,
                label = { Text("Ссылка или magnet") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                trailingIcon = {
                    IconButton(onClick = {
                        val text = clipboard.getText()?.text
                        val link = LinkExtractor.extract(text) ?: text
                        if (!link.isNullOrBlank()) {
                            viewModel.setUrl(link)
                            viewModel.check()
                        }
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Вставить из буфера")
                    }
                },
            )

            if (!state.checked) {
                Button(
                    onClick = viewModel::check,
                    enabled = !state.checking && state.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Проверить ссылку")
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.checked) {
                if (state.isMagnet) {
                    Text(
                        "Magnet-ссылка: размер станет известен после получения метаданных.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionTitle("Нода")
                state.nodes.forEach { node ->
                    NodeRow(node, selected = node.id == state.selectedNodeId, onClick = { viewModel.selectNode(node.id) })
                }

                SectionTitle("Способ загрузки")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.availableTypes.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            label = { Text(typeLabel(type)) },
                        )
                    }
                }
                state.selectedNode?.let { node ->
                    val size = node.sizeBytes?.let { "Размер: ${Format.bytes(it)}" }
                    if (size != null) Text(size, style = MaterialTheme.typography.bodySmall)
                }

                SectionTitle("Папка на ноде")
                StoragePathField(state, viewModel::setStoragePath)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Начать сразу", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.startImmediately, onCheckedChange = viewModel::setStartImmediately)
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (state.startImmediately) "Скачать" else "Добавить на паузе")
                    }
                }
                OutlinedButton(onClick = viewModel::check, modifier = Modifier.fillMaxWidth(), enabled = !state.checking) {
                    Text("Проверить заново")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NodeRow(node: NodeChoice, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.bodyLarge)
                val status = listOfNotNull(
                    nodeStatusLabel(node.status),
                    node.pingMs?.let { "$it мс" },
                ).joinToString(" · ")
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                node.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StoragePathField(state: AddUiState, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = state.storagePath,
            onValueChange = onChange,
            label = { Text("По умолчанию — папка ноды") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (state.storageTargets.isNotEmpty()) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Выбрать папку")
                    }
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.storageTargets.forEach { target ->
                val fits = target.canFit != false && target.writable
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(target.path)
                            Text(
                                "свободно ${Format.bytes(target.freeBytes)} из ${Format.bytes(target.totalBytes)}" +
                                    if (!fits) " · не поместится" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    onClick = {
                        onChange(target.path)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    LinkExtractor.TYPE_DIRECT -> "HTTP"
    LinkExtractor.TYPE_YTDLP -> "Видео (yt-dlp)"
    LinkExtractor.TYPE_ARIA2C -> "aria2c"
    LinkExtractor.TYPE_TORRENT -> "Торрент"
    else -> type
}

private fun nodeStatusLabel(status: String): String = when (status) {
    "online" -> "в сети"
    "offline" -> "не в сети"
    "disabled" -> "выключена"
    "never_seen" -> "ещё не отвечала"
    else -> status
}
