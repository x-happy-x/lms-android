package ru.mrcrubs.lms.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MoreScreen(onOpen:(String)->Unit) {
    Scaffold(topBar={TopAppBar(title={Text("Мой LMS")})}){padding->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Text("Всё под рукой",style=MaterialTheme.typography.headlineLarge);Text("Ваши устройства, сеть и настройки",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            listOf(Triple("network","HomeNet",Icons.Default.Shield),Triple("nodes","Ноды и диски",Icons.Default.Dns),Triple("profiles","Профили загрузок",Icons.Default.Tune),Triple("settings","Настройки и аккаунт",Icons.Default.Settings)).forEach{(route,title,icon)->
                item{Card(Modifier.fillMaxWidth().clickable{onOpen(route)}){ListItem(headlineContent={Text(title,style=MaterialTheme.typography.titleMedium)},leadingContent={Icon(icon,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)},trailingContent={Icon(Icons.Default.ChevronRight,null)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow))}}
            }
        }
    }
}
