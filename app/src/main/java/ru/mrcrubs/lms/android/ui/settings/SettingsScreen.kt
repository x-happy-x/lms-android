package ru.mrcrubs.lms.android.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.android.data.AppSettings
import ru.mrcrubs.lms.android.ui.common.SpeedLimitPicker
import ru.mrcrubs.lms.android.ui.factory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: (() -> Unit)?) {
    val viewModel: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.edit { it.copy(notificationsEnabled = false) }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack?.invoke()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val form = state.form
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Роутер", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = form.routerUrl,
                onValueChange = { value -> viewModel.edit { it.copy(routerUrl = value) } },
                label = { Text("Адрес веб-интерфейса LMS") },
                placeholder = { Text("192.168.1.1:8082") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "API роутера без авторизации: вне дома используйте VPN или прокси с паролем (логин ниже).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = form.username,
                onValueChange = { value -> viewModel.edit { it.copy(username = value) } },
                label = { Text("Логин (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = { value -> viewModel.edit { it.copy(password = value) } },
                label = { Text("Пароль (необязательно)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !state.testing && form.routerUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Проверить подключение")
            }
            state.testResult?.let {
                Text(
                    it,
                    color = if (state.testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text("Уведомления", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("О завершении и ошибках", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Пока приложение открыто — сразу, в фоне — с выбранным интервалом.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.edit { it.copy(notificationsEnabled = enabled) }
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            if (form.notificationsEnabled) {
                Text("Проверять в фоне каждые", style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSettings.INTERVAL_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = form.backgroundIntervalMinutes == minutes,
                            onClick = { viewModel.edit { it.copy(backgroundIntervalMinutes = minutes) } },
                            label = { Text(if (minutes < 60) "$minutes мин" else "${minutes / 60} ч") },
                        )
                    }
                }
            }

            Text("Загрузки", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Добавлять без подтверждения", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ссылки из «Поделиться» и «Открыть с помощью» сразу уходят на лучшую ноду, без экрана добавления.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = form.quickAdd, onCheckedChange = { value -> viewModel.edit { it.copy(quickAdd = value) } })
            }
            Text("Ограничение скорости для новых загрузок", style = MaterialTheme.typography.bodyMedium)
            SpeedLimitPicker(
                value = form.defaultSpeedLimit,
                onChange = { limit -> viewModel.edit { it.copy(defaultSpeedLimit = limit) } },
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить")
            }
            if (state.saved && onBack == null) {
                Text("Сохранено", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
