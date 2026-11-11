package ru.mrcrubs.lms.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun ChoiceField(label: String, value: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { expanded = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(choices.firstOrNull { it.first == value }?.second ?: value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Icon(Icons.Default.ExpandMore, "Выбрать $label")
                }
            }
            DropdownMenu(expanded && enabled, { expanded = false }) {
                choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, trailingIcon = { if(id==value) Icon(Icons.Default.Check, null) }, onClick = { expanded = false; onSelect(id) }) }
            }
        }
    }
}
