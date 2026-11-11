package ru.mrcrubs.lms.android.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.android.AppContainer
import ru.mrcrubs.lms.android.ui.common.ChoiceField
import ru.mrcrubs.lms.android.ui.common.EmptyState
import ru.mrcrubs.lms.android.ui.common.ErrorBanner
import ru.mrcrubs.lms.android.ui.media.RemoteMediaDialog
import ru.mrcrubs.lms.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FilesScreen(container: AppContainer) {
    var nodes by remember { mutableStateOf<List<NodeItem>>(emptyList()) }
    var nodeId by remember { mutableStateOf("") }
    var roots by remember { mutableStateOf<List<StorageTarget>>(emptyList()) }
    var root by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<FileListing?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var newest by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<FileEntry?>(null) }
    var operation by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<FileEntry?>(null) }
    var draft by remember { mutableStateOf("") }
    var mutationError by remember { mutableStateOf<String?>(null) }
    var mutating by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val settings by container.settings.settings.collectAsState(initial = ru.mrcrubs.lms.android.data.AppSettings())
    val canEdit=settings.accountRole in listOf("admin","editor") || settings.accountLogin==null
    LaunchedEffect(settings.routerUrl, settings.bearerToken) {
        listing=null;roots=emptyList();root="";nodeId="";preview=null
        try { nodes = container.router.nodes(); if(nodes.none { it.id==nodeId }) nodeId=nodes.firstOrNull()?.id.orEmpty() }
        catch(e: kotlinx.coroutines.CancellationException){throw e}
        catch(e: Exception) { error=e.message }
    }
    LaunchedEffect(nodeId) {
        if(nodeId.isBlank()) return@LaunchedEffect
        listing=null;root="";path="";error=null
        try { val targets=container.router.storageTargets(nodeId,null);roots=targets.targets;root=targets.defaultPath.ifBlank { roots.firstOrNull()?.path.orEmpty() } }
        catch(e: kotlinx.coroutines.CancellationException){throw e}
        catch(e: Exception) { error=e.message }
    }
    LaunchedEffect(nodeId,root,path,refresh) {
        if(nodeId.isBlank()||root.isBlank())return@LaunchedEffect
        loading=true;error=null;listing=null
        try { listing=withContext(Dispatchers.IO) { container.api()?.files(nodeId,root,path) ?: throw ApiException("Подключите LMS") } }
        catch(e: kotlinx.coroutines.CancellationException){throw e}
        catch(e: Exception){error=e.message}
        finally{loading=false}
    }
    Scaffold(topBar={TopAppBar(title={Text("Файлы")},actions={if(canEdit&&root.isNotBlank())IconButton(onClick={target=null;draft="";mutationError=null;operation="mkdir"}){Icon(Icons.Default.CreateNewFolder,"Создать папку")};IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,"Обновить файлы")}})}) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Хранилище нод",style=MaterialTheme.typography.headlineMedium);Text("Папки и файлы без лишних копий",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            item { ChoiceField("Нода",nodeId,nodes.map { it.id to it.name },{nodeId=it}) }
            if(roots.isNotEmpty())item{ChoiceField("Диск",root,roots.map { it.path to "${it.path} · ${Format.bytes(it.freeBytes)} свободно" },{root=it;path=""})}
            item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={path=listing?.parentPath.orEmpty()},enabled=path.isNotEmpty()){Icon(Icons.AutoMirrored.Default.ArrowBack,"На папку выше")}
                    Column(Modifier.weight(1f)){Text(if(path.isBlank())"Корень диска" else path.substringAfterLast('/'),style=MaterialTheme.typography.titleLarge);Text("$root${if(path.isBlank())"" else "/$path"}",maxLines=2,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={newest=!newest}){Icon(Icons.Default.Sort,if(newest)"Сортировать по имени" else "Сначала новые")}
                }
            }
            if(path.isNotEmpty())item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(onClick={path=""}){Icon(Icons.Default.Home,"Корень диска")}
                path.split('/').filter{it.isNotEmpty()}.forEachIndexed { index,name ->
                    TextButton(onClick={path=path.split('/').take(index+1).joinToString("/")}){Text(name)}
                }
            } }
            item { OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Найти в этой папке")},leadingIcon={Icon(Icons.Default.Search,null)},shape=MaterialTheme.shapes.extraLarge) }
            error?.let{item{ErrorBanner(it)}}
            if(loading)item{Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
            val entries=listing?.entries.orEmpty().filter { it.name.contains(search,true) }.sortedWith(compareBy<FileEntry>{!it.directory}.thenComparator { a,b -> if(newest) b.modifiedAt.orEmpty().compareTo(a.modifiedAt.orEmpty()) else a.name.compareTo(b.name,true) })
            if(!loading&&listing!=null&&entries.isEmpty())item{EmptyState(Icons.Default.FolderOpen,"Здесь пока пусто",if(search.isEmpty())"В этой папке нет доступных файлов." else "Измените поисковый запрос.")}
            items(entries,key={it.path}) { entry ->
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().clickable { if(entry.directory)path=entry.path else if(entry.mimeType?.startsWith("video/")==true||entry.mimeType?.startsWith("audio/")==true||entry.mimeType?.startsWith("image/")==true)preview=entry }.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer){Icon(if(entry.directory)Icons.Default.Folder else Icons.Default.Description,null,Modifier.padding(12.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
                        Column(Modifier.weight(1f)){Text(entry.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall);Text(if(entry.directory)"Папка" else Format.bytes(entry.sizeBytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        if(canEdit)Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Действия с ${entry.name}")}
                            DropdownMenu(menu,{menu=false}) {
                                DropdownMenuItem(text={Text("Переименовать")},onClick={menu=false;target=entry;draft=entry.name;mutationError=null;operation="rename"},leadingIcon={Icon(Icons.Default.Edit,null)})
                                DropdownMenuItem(text={Text("Удалить",color=MaterialTheme.colorScheme.error)},onClick={menu=false;target=entry;mutationError=null;operation="delete"},leadingIcon={Icon(Icons.Default.DeleteOutline,null)})
                            }
                        }
                        if(entry.directory)Icon(Icons.Default.ChevronRight,null) else IconButton(onClick={try { container.api()?.let{api->container.deviceDownloads.enqueueFile(entry.name,api.nodeFileUrl(nodeId,root,entry.path),api)} }catch(e:Exception){error=e.message}}){Icon(Icons.Default.Download,"Скачать ${entry.name}")}
                    }
                }
            }
            if(listing?.truncated==true)item{Text("Показана первая часть папки. Уточните путь или поиск.",style=MaterialTheme.typography.bodySmall)}
            item{Spacer(Modifier.height(20.dp))}
        }
    }
    operation?.let { action ->
        AlertDialog(onDismissRequest={if(!mutating)operation=null},title={Text(when(action){"mkdir"->"Новая папка";"rename"->"Переименовать";else->"Удалить ${if(target?.directory==true)"пустую папку" else "файл"}?"})},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(action=="delete")Text("${target?.name.orEmpty()}\nУдаление необратимо. Папка должна быть пустой.")
                else OutlinedTextField(draft,{draft=it},label={Text("Имя")},singleLine=true,enabled=!mutating)
                mutationError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        },confirmButton={TextButton(enabled=!mutating&&(action=="delete"||draft.isNotBlank()),onClick={scope.launch {
            mutating=true;mutationError=null
            try{withContext(Dispatchers.IO){container.api()?.mutateFile(nodeId,action,root,if(action=="mkdir")path else target!!.path,if(action=="delete")null else draft.trim()) ?: throw ApiException("Подключите LMS")};operation=null;refresh++}
            catch(e:Exception){mutationError=e.message}
            finally{mutating=false}
        }}){Text(if(mutating)"Выполняется…" else if(action=="delete")"Удалить" else "Сохранить",color=if(action=="delete")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}},dismissButton={TextButton(enabled=!mutating,onClick={operation=null}){Text("Отмена")}})
    }
    preview?.let{entry->container.api()?.let{api->RemoteMediaDialog(entry.name,api.nodeFileUrl(nodeId,root,entry.path,true),entry.mimeType.orEmpty(),api,{preview=null})}}
}
