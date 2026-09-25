package ru.mrcrubs.lms.android.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.mrcrubs.lms.core.SpeedLimits

/**
 * Speed limit chooser: presets as chips plus "Своё" with a MB/s field.
 * [value] is bytes/s, null = unlimited.
 */
@Composable
fun SpeedLimitPicker(value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val normalized = value?.takeIf { it > 0 }
    var custom by remember { mutableStateOf(normalized != null && normalized !in SpeedLimits.PRESETS) }
    var text by remember { mutableStateOf(SpeedLimits.toMegabytesText(normalized)) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeedLimits.PRESETS.forEach { preset ->
                FilterChip(
                    selected = !custom && normalized == preset,
                    onClick = {
                        custom = false
                        error = null
                        text = SpeedLimits.toMegabytesText(preset)
                        onChange(preset)
                    },
                    label = { Text(SpeedLimits.label(preset)) },
                )
            }
            FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Своё") })
        }
        if (custom) {
            val errorText = error
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    SpeedLimits.parseMegabytes(input)
                        .onSuccess { limit ->
                            error = null
                            onChange(limit)
                        }
                        .onFailure { failure -> error = failure.message }
                },
                label = { Text("МБ/с (пусто — без лимита)") },
                singleLine = true,
                isError = errorText != null,
                supportingText = if (errorText != null) {
                    { Text(errorText) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
