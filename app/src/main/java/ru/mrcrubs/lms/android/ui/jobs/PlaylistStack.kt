package ru.mrcrubs.lms.android.ui.jobs

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mrcrubs.lms.android.LocalContainer
import ru.mrcrubs.lms.android.ui.add.MediaEntryCard
import ru.mrcrubs.lms.core.*

@Composable fun PlaylistStack(jobs:List<Job>,callbacks:JobCallbacks,busyIds:Set<String>,nodes:Map<String,NodeItem>) {
    val first=jobs.first();val snapshot=first.playlist
    val key=snapshot?.id ?: first.id
    var expanded by rememberSaveable(key){mutableStateOf(false)}
    var shown by rememberSaveable(key){mutableIntStateOf(12)}
    var entries by remember(key){mutableStateOf(snapshot?.entries.orEmpty())}
    var loading by remember(key){mutableStateOf(false)}
    var error by remember(key){mutableStateOf<String?>(null)}
    val whole=jobs.any{it.ytdlpOptions?.playlist==true}
    val container=LocalContainer.current
    LaunchedEffect(expanded,key){if(expanded&&entries.isEmpty()){loading=true;try{entries=withContext(Dispatchers.IO){container.api()?.extractMedia(snapshot?.url ?: first.url,first.nodeId,first.networkRouteId)?.entries.orEmpty()}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false}}}
    Box(Modifier.fillMaxWidth().padding(bottom=10.dp)) {
        Surface(Modifier.matchParentSize().padding(horizontal=12.dp).offset(y=10.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f)){}
        Surface(Modifier.matchParentSize().padding(horizontal=6.dp).offset(y=5.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.secondaryContainer){}
        Card(Modifier.fillMaxWidth().animateContentSize(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable{expanded=!expanded},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer){if(snapshot?.thumbnail!=null)AsyncImage(snapshot.thumbnail,null,Modifier.size(78.dp,62.dp),contentScale=ContentScale.Crop)else Icon(Icons.Default.VideoLibrary,null,Modifier.padding(18.dp).size(28.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
                    Column(Modifier.weight(1f)){Text(snapshot?.title ?: "Плейлист",style=MaterialTheme.typography.titleMedium,maxLines=2);Text("${snapshot?.entryCount?.let{"$it роликов"} ?: entries.size.takeIf{it>0}?.let{"$it роликов"} ?: if(whole)"Число роликов уточняется" else "${jobs.size} роликов"} · ${if(whole)"весь плейлист" else "выбранные"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"Свернуть плейлист" else "Развернуть плейлист")
                }
                val known=jobs.mapNotNull{it.percent ?: if(it.status==JobStatus.DONE)100.0 else null}
                if(known.size==jobs.size)LinearProgressIndicator(progress={(known.average()/100).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                Text(if(whole)"Общий прогресс · ${Format.status(first.status)}${first.percent?.let{" · ${Format.percent(it)}"}.orEmpty()}" else "Готово ${jobs.count{it.status==JobStatus.DONE}} из ${jobs.size}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        jobs.forEach{job->JobRow(job,job.nodeId?.let(nodes::get),job.id in busyIds,callbacks,entries.firstOrNull{it.url==job.url}?.title ?: job.title)}
                        if(whole) {
                            Text("Ролики плейлиста",style=MaterialTheme.typography.titleSmall)
                            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                            entries.take(shown).forEach{entry->MediaEntryCard(entry,first.nodeId,first.ytdlpOptions?.maxHeight,networkRouteId=first.networkRouteId)}
                            if(shown<entries.size)TextButton(onClick={shown+=12}){Text("Показать ещё ${minOf(12,entries.size-shown)}")}
                            Text("Индивидуальный прогресс роликов появится только при отдельных заданиях.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
