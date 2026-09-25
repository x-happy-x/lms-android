package ru.mrcrubs.lms.android.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.mrcrubs.lms.android.ui.MainViewModel
import ru.mrcrubs.lms.android.ui.common.EmptyState
import ru.mrcrubs.lms.android.ui.common.JobThumb
import ru.mrcrubs.lms.core.FileKind
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.isViewableMedia

/** Finished photos and videos, newest first — the order the viewer pages through. */
fun mediaJobs(jobs: List<Job>, kind: FileKind? = null): List<Job> = jobs
    .filter { it.isViewableMedia && (kind == null || FileKind.of(it) == kind) }
    .sortedByDescending { it.finishedAt ?: it.createdAt }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(viewModel: MainViewModel, onOpen: (Job) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var kindName by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = kindName?.let(FileKind::valueOf)
    val media = remember(state.jobs, kind) { mediaJobs(state.jobs, kind) }

    Scaffold(topBar = { TopAppBar(title = { Text("Медиа") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == null, onClick = { kindName = null }, label = { Text("Всё") })
                    FilterChip(selected = kind == FileKind.VIDEO, onClick = { kindName = FileKind.VIDEO.name }, label = { Text("Видео") })
                    FilterChip(selected = kind == FileKind.IMAGE, onClick = { kindName = FileKind.IMAGE.name }, label = { Text("Фото") })
                }
            }
            if (media.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Filled.PhotoLibrary,
                        title = "Медиа пока нет",
                        text = "Здесь появятся скачанные фото и видео — их можно смотреть сразу, без скачивания на телефон.",
                    )
                }
            }
            itemsIndexed(media, key = { _, job -> job.id }) { _, job ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(job) }) {
                    JobThumb(job, size = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f), corner = 0.dp)
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(job.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        job.outputSizeBytes?.let {
                            Text(Format.bytes(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
