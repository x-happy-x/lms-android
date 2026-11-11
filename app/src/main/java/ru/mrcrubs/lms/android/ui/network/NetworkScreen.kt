package ru.mrcrubs.lms.android.ui.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.android.ui.common.ChoiceField
import ru.mrcrubs.lms.android.ui.common.ErrorBanner
import ru.mrcrubs.lms.core.*

@Composable fun NetworkRoutePicker(routes:NetworkRoutes,selected:String?,engine:String,onSelect:(String?)->Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Маршрут загрузки",style=MaterialTheme.typography.titleMedium)
            Text(routes.planned.label ?: "Маршрут ещё не подтверждён",style=MaterialTheme.typography.bodyMedium)
            if(routes.canSelect)ChoiceField("Выход в сеть",selected.orEmpty(),listOf("" to "Автоматически · правила сети")+routes.routes.filter{it.available && engine in it.supportedTypes}.map{it.id to it.label},{onSelect(it.ifBlank{null})})
            routes.message?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text("Выбор относится к новой загрузке. Текущие соединения не переключаются.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NetworkScreen(container:AppContainer) {
    var nodes by remember{mutableStateOf<List<NodeItem>>(emptyList())}
    var node by remember{mutableStateOf("")}
    var data by remember{mutableStateOf<NetworkRoutes?>(null)}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var refresh by remember{mutableIntStateOf(0)}
    val settings by container.settings.settings.collectAsState(initial=ru.mrcrubs.lms.android.data.AppSettings())
    LaunchedEffect(settings.routerUrl,settings.bearerToken){data=null;nodes=emptyList();node="";try{nodes=container.router.nodes();node=nodes.firstOrNull()?.id.orEmpty()}catch(e:Exception){error=e.message}}
    LaunchedEffect(node,refresh){if(node.isBlank())return@LaunchedEffect;loading=true;error=null;try{data=withContext(Dispatchers.IO){container.api()?.networkRoutes(node)}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false}}
    Scaffold(topBar={TopAppBar(title={Text("HomeNet")},actions={IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,"Обновить маршрут")}})}){padding->
        LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item{Icon(Icons.Default.Shield,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(12.dp));Text("Прозрачный маршрут",style=MaterialTheme.typography.headlineMedium);Text("Запланированный выход и подтверждённые соединения",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            item{ChoiceField("Нода",node,nodes.map{it.id to it.name},{node=it})}
            error?.let{item{ErrorBanner(it)}}
            if(loading)item{CircularProgressIndicator()}
            data?.let{routes->
                item{Card{Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Для новых загрузок",style=MaterialTheme.typography.labelLarge);Text(routes.planned.label ?: "Не подтверждён",style=MaterialTheme.typography.headlineSmall);Text(when(routes.planned.state){"configured"->"Выделенный маршрут задания";"automatic"->"Выбор по правилам HomeNet";else->"Нет подтверждённых данных о маршруте"},color=MaterialTheme.colorScheme.onSurfaceVariant);routes.message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}}}}
                item{Text("Активные соединения",style=MaterialTheme.typography.titleLarge);if(routes.observed.isEmpty())Text("Совпадающих соединений сейчас нет. Название VPN не угадывается по настройке группы.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                routes.observed.forEach{observed->item{ListItem(headlineContent={Text(observed.host)},supportingContent={Text(observed.chains.joinToString(" → ").ifBlank{"Выход не подтверждён"})},leadingContent={Icon(Icons.Default.Route,null)})}}
                item{Text("Доступные выходы",style=MaterialTheme.typography.titleLarge);Text("Маршрут можно выбрать при добавлении загрузки.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                routes.routes.forEach{route->item{ListItem(headlineContent={Text(route.label)},supportingContent={Text(if(route.available)route.supportedTypes.joinToString(" · ") else "Недоступен")},leadingContent={Icon(if(route.available)Icons.Default.VpnLock else Icons.Default.CloudOff,null)})}}
            }
        }
    }
}
