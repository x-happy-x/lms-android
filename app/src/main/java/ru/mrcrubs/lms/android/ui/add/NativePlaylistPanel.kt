package ru.mrcrubs.lms.android.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.mrcrubs.lms.android.LocalContainer
import ru.mrcrubs.lms.android.ui.common.ChoiceField
import ru.mrcrubs.lms.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.mrcrubs.lms.android.ui.media.RemoteMediaDialog
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val inspections=Semaphore(2)
@Composable fun NativePlaylistPanel(media:MediaExtract,state:AddUiState,viewModel:AddDownloadViewModel) {
    var expanded by rememberSaveable(media.url){mutableStateOf(false)}
    var shown by rememberSaveable(media.url){mutableIntStateOf(12)}
    Card(Modifier.fillMaxWidth().animateContentSize(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable{expanded=!expanded},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(media.thumbnail!=null)AsyncImage(media.thumbnail,null,Modifier.size(64.dp),contentScale=ContentScale.Crop)
                else Icon(if(media.kind=="playlist")Icons.Default.VideoLibrary else Icons.Default.PlayCircle,null,Modifier.size(40.dp))
                Column(Modifier.weight(1f)){Text(media.title ?: "Видео",style=MaterialTheme.typography.titleLarge);Text(if(media.kind=="playlist")"${media.entryCount ?: media.entries.size} роликов · плейлист" else "Один ролик",style=MaterialTheme.typography.labelLarge)}
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"Свернуть" else "Развернуть")
            }
            ChoiceField("Качество",state.maxHeight?.toString().orEmpty(),listOf("" to "Лучшее доступное")+listOf(2160,1440,1080,720,480,360).map{it.toString() to "До ${it}p"},{viewModel.setMaxHeight(it.toIntOrNull())})
            if(media.kind=="playlist") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(state.wholePlaylist,{viewModel.setWholePlaylist(true)},label={Text("Весь плейлист")});FilterChip(!state.wholePlaylist,{viewModel.setWholePlaylist(false)},label={Text("Выбранные · ${state.selectedEntries.size}")})}
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        media.entries.take(shown).forEach { entry -> MediaEntryCard(entry,state.selectedNodeId,state.maxHeight,entry.url in state.selectedEntries,if(state.wholePlaylist)null else ({viewModel.toggleEntry(entry.url)}),state.entryHeights[entry.url],{viewModel.setEntryHeight(entry.url,it)},!state.wholePlaylist,state.networkRouteId) }
                        if(shown<media.entries.size)TextButton(onClick={shown+=12}){Text("Показать ещё ${minOf(12,media.entries.size-shown)}")}
                        if(media.truncated)Text("Показана первая часть списка. Весь плейлист скачивается полностью.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            } else if(expanded)MediaEntryCard(PlaylistEntry(media.url,media.title,media.thumbnail),state.selectedNodeId,state.maxHeight,networkRouteId=state.networkRouteId)
        }
    }
}

@Composable fun MediaEntryCard(entry:PlaylistEntry,nodeId:String?,maxHeight:Int?,selected:Boolean=true,onToggle:(()->Unit)?=null,selectedHeight:Int?=null,onHeight:(Int)->Unit={},chooseQuality:Boolean=false,networkRouteId:String?=null) {
    val container=LocalContainer.current
    var info by remember(entry.url){mutableStateOf<MediaInfo?>(null)}
    var error by remember(entry.url){mutableStateOf(false)}
    var stream by remember(entry.url){mutableStateOf<String?>(null)}
    var preparing by remember(entry.url){mutableStateOf(false)}
    var playError by remember(entry.url){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(entry.url,nodeId,networkRouteId){info=null;error=false;try{info=inspections.withPermit{withContext(Dispatchers.IO){container.api()?.mediaInfo(entry.url,nodeId,maxHeight,networkRouteId)}}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=true}}
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(entry.thumbnail!=null)AsyncImage(entry.thumbnail,null,Modifier.size(76.dp,52.dp),contentScale=ContentScale.Crop)
                else Icon(Icons.Default.PlayCircle,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){Text(entry.title ?: "Ролик",style=MaterialTheme.typography.titleSmall,maxLines=2);Text(listOfNotNull(entry.durationSeconds?.let{"${it/60}:${(it%60).toString().padStart(2,'0')}"},info?.quality?.takeIf{it.isNotBlank()},info?.sizeBytes?.let{(if(info?.sizeEstimated==true)"≈ " else "")+Format.bytes(it)}).joinToString(" · ").ifBlank{if(error)"Качество недоступно" else "Получаем качество и размер…"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Смотреть через LMS",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(enabled=!preparing,onClick={scope.launch {
                    preparing=true;playError=null
                    try { val api=container.api() ?: throw ApiException("Подключите LMS");val preview=withContext(Dispatchers.IO){api.prepareMedia(entry.url,nodeId,selectedHeight?.takeIf{it>0} ?: maxHeight,networkRouteId)};stream=api.mediaStreamUrl(preview.streamUrl) }
                    catch(e:Exception){playError=e.message}
                    finally{preparing=false}
                }}){if(preparing)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(Icons.Default.PlayArrow,"Смотреть ${entry.title ?: "ролик"}")}
            }
            playError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            if(onToggle!=null)FilterChip(selected,onToggle,label={Text(if(selected)"Выбрано" else "Выбрать")},leadingIcon={if(selected)Icon(Icons.Default.Check,null,Modifier.size(16.dp))})
            if(chooseQuality && info?.variants?.isNotEmpty()==true)ChoiceField("Качество ролика",selectedHeight?.takeIf{it>0}?.toString().orEmpty(),listOf("" to "Как в параметрах")+info!!.variants.distinctBy{it.height}.filter{it.height>0}.map{it.height.toString() to it.quality},{onHeight(it.toIntOrNull()?:0)})
        }
    }
    stream?.let{url->container.api()?.let{api->RemoteMediaDialog(entry.title ?: "Видео",url,"video/mp4",api,{stream=null})}}
}
