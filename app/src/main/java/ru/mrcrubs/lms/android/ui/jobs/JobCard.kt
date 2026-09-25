package ru.mrcrubs.lms.android.ui.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.mrcrubs.lms.android.ui.common.JobThumb
import ru.mrcrubs.lms.android.ui.common.color
import ru.mrcrubs.lms.core.FileKind
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.JobStatus
import ru.mrcrubs.lms.core.NodeItem
import ru.mrcrubs.lms.core.SpeedLimits
import ru.mrcrubs.lms.core.hasOutput
import ru.mrcrubs.lms.core.isViewableMedia

/** Callbacks for everything a job card can do. */
data class JobCallbacks(
    val pause: (Job) -> Unit,
    val resume: (Job) -> Unit,
    val retry: (Job) -> Unit,
    val cancel: (Job) -> Unit,
    val download: (Job) -> Unit,
    val open: (Job) -> Unit,
    val editUrl: (Job) -> Unit,
    val move: (Job) -> Unit,
    val speed: (Job) -> Unit,
)

@Composable
fun statusColor(status: JobStatus): Color = when (status) {
    JobStatus.RUNNING -> Color(0xFF16A34A)
    JobStatus.QUEUED, JobStatus.PAUSED -> Color(0xFFD97706)
    JobStatus.ERROR -> MaterialTheme.colorScheme.error
    JobStatus.DONE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun StatusBadge(status: JobStatus) {
    val color = statusColor(status)
    Surface(color = color.copy(alpha = 0.14f), contentColor = color, shape = MaterialTheme.shapes.small) {
        Text(Format.status(status), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

private fun typeLabel(type: String) = when (type) {
    "DIRECT" -> "HTTP"
    "YTDLP" -> "yt-dlp"
    "ARIA2C" -> "aria2c"
    "TORRENT" -> "Торрент"
    else -> type
}

private fun details(job: Job, node: NodeItem?): String {
    val parts = mutableListOf<String>()
    val total = job.totalBytes
    if (job.status == JobStatus.RUNNING) {
        val percent = job.percent
        if (total != null && percent != null) parts += "${Format.bytes((total * percent / 100).toLong())} из ${Format.bytes(total)}"
        else if (total != null) parts += Format.bytes(total)
        Format.speed(job.speedBytes)?.let { parts += it }
        Format.eta(job.etaSeconds)?.let { parts += "осталось $it" }
    } else {
        (job.outputSizeBytes ?: total)?.let { parts += Format.bytes(it) }
    }
    if (job.status.isActive) job.maxSpeedBytes?.takeIf { it > 0 }?.let { parts += "до ${SpeedLimits.label(it)}" }
    parts += typeLabel(job.type)
    node?.let { parts += it.name }
    return parts.joinToString(" · ")
}

@Composable
private fun QuickActions(job: Job, callbacks: JobCallbacks) {
    when {
        job.canPause -> FilledTonalIconButton(onClick = { callbacks.pause(job) }) { Icon(Icons.Filled.Pause, "Пауза") }
        job.canResume -> FilledTonalIconButton(onClick = { callbacks.resume(job) }) { Icon(Icons.Filled.PlayArrow, "Продолжить") }
        job.canRetry -> IconButton(onClick = { callbacks.retry(job) }) { Icon(Icons.Filled.Refresh, "Повторить") }
        job.hasOutput -> FilledTonalIconButton(onClick = { callbacks.download(job) }) { Icon(Icons.Filled.Download, "Скачать на телефон") }
    }
}

@Composable
private fun MenuAction(text: String, icon: ImageVector, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            close()
            action()
        },
    )
}

@Composable
private fun JobMenu(job: Job, callbacks: JobCallbacks) {
    var open by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            if (job.hasOutput) MenuAction("Скачать на телефон", Icons.Filled.Download, close) { callbacks.download(job) }
            if (job.isViewableMedia) MenuAction("Открыть", Icons.Filled.Visibility, close) { callbacks.open(job) }
            if (job.remoteJobId != null) MenuAction("Переместить", Icons.Filled.SwapHoriz, close) { callbacks.move(job) }
            if (job.status.isActive) {
                val label = job.maxSpeedBytes?.takeIf { it > 0 }?.let { "Скорость: ${SpeedLimits.label(it)}" } ?: "Ограничить скорость"
                MenuAction(label, Icons.Filled.Speed, close) { callbacks.speed(job) }
            }
            if (job.status != JobStatus.DONE) MenuAction("Изменить ссылку", Icons.Filled.Edit, close) { callbacks.editUrl(job) }
            MenuAction("Копировать ссылку", Icons.Filled.ContentCopy, close) { clipboard.setText(AnnotatedString(job.url)) }
            if (job.canCancel) MenuAction("Отменить", Icons.Filled.Close, close) { callbacks.cancel(job) }
        }
    }
}

@Composable
fun JobRow(job: Job, node: NodeItem?, busy: Boolean, callbacks: JobCallbacks) {
    val kind = FileKind.of(job)
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbModifier = if (job.isViewableMedia) Modifier.clickable { callbacks.open(job) } else Modifier
            JobThumb(job, size = 52.dp, modifier = thumbModifier)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(job.status)
                    if (job.status.isActive) job.percent?.let { Text(Format.percent(it).orEmpty(), style = MaterialTheme.typography.labelLarge) }
                    Text(kind.label, style = MaterialTheme.typography.labelMedium, color = kind.color())
                }
                if (job.status.isActive) {
                    val percent = job.percent
                    if (percent != null) {
                        LinearProgressIndicator(progress = { (percent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
                    } else if (job.status == JobStatus.RUNNING) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp))
                    }
                }
                Text(details(job, node), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val note = if (job.status == JobStatus.ERROR) job.errorText ?: job.message else null
                if (!note.isNullOrBlank()) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QuickActions(job, callbacks)
                    JobMenu(job, callbacks)
                }
            }
        }
    }
}

@Composable
fun JobTile(job: Job, busy: Boolean, callbacks: JobCallbacks) {
    Card(Modifier.fillMaxWidth()) {
        Box {
            val thumbModifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                .let { if (job.isViewableMedia) it.clickable { callbacks.open(job) } else it }
            JobThumb(job, size = null, modifier = thumbModifier, corner = 0.dp)
            Row(Modifier.align(Alignment.TopEnd)) {
                if (!busy) JobMenu(job, callbacks)
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(job.status)
                if (job.status.isActive) job.percent?.let { Text(Format.percent(it).orEmpty(), style = MaterialTheme.typography.labelMedium) }
            }
            if (job.status.isActive) {
                job.percent?.let { percent ->
                    LinearProgressIndicator(progress = { (percent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (job.outputSizeBytes ?: job.totalBytes)?.let { Format.bytes(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else QuickActions(job, callbacks)
            }
        }
    }
}
