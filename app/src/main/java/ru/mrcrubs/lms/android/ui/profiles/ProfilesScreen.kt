package ru.mrcrubs.lms.android.ui.profiles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mrcrubs.lms.android.ui.MainViewModel
import ru.mrcrubs.lms.android.ui.common.EmptyState
import ru.mrcrubs.lms.core.LinkExtractor
import ru.mrcrubs.lms.core.ProfileRequest

private fun typeLabel(type: String) = when (type) {
    "DIRECT" -> "HTTP"
    "YTDLP" -> "Видео (yt-dlp)"
    "ARIA2C" -> "aria2c"
    "TORRENT" -> "Торрент"
    else -> type
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Профили") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Профиль") })
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (state.profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Tune,
                        title = "Профилей пока нет",
                        text = "Профиль — именованный набор настроек, который выбирают при добавлении загрузки.",
                    )
                }
            }
            items(state.profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text(typeLabel(profile.type) + if (profile.enabled) "" else " · выключен", style = MaterialTheme.typography.bodyMedium)
                        profile.outputTemplate?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        profile.extraArgsJson?.let { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(LinkExtractor.TYPE_YTDLP) }
        var template by remember { mutableStateOf("") }
        var args by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Новый профиль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinkExtractor.ALL_TYPES.forEach { value ->
                            FilterChip(selected = type == value, onClick = { type = value }, label = { Text(typeLabel(value)) })
                        }
                    }
                    OutlinedTextField(template, { template = it }, label = { Text("Шаблон имени") }, placeholder = { Text("%(title)s.%(ext)s") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(args, { args = it }, label = { Text("Доп. аргументы, JSON") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Включён", Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.createProfile(
                        ProfileRequest(
                            name = name.trim(),
                            type = type,
                            enabled = enabled,
                            outputTemplate = template.trim().ifBlank { null },
                            extraArgsJson = args.trim().ifBlank { null },
                        ),
                    ) { result -> if (result == null) creating = false else error = result }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Отмена") } },
        )
    }
}
