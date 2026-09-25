package ru.mrcrubs.lms.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.mrcrubs.lms.android.LocalContainer
import ru.mrcrubs.lms.core.FileKind
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.isViewableMedia

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.VIDEO -> Icons.Outlined.Movie
    FileKind.AUDIO -> Icons.Outlined.MusicNote
    FileKind.IMAGE -> Icons.Outlined.Image
    FileKind.ARCHIVE -> Icons.Outlined.FolderZip
    FileKind.DOCUMENT -> Icons.Outlined.Description
    FileKind.TORRENT -> Icons.Outlined.Hub
    FileKind.DISK -> Icons.Outlined.Album
    FileKind.APP -> Icons.Outlined.Apps
    FileKind.CODE -> Icons.Outlined.Code
    FileKind.OTHER -> Icons.Outlined.Folder
}

/** Kind colors, matching the router web UI. */
@Composable
fun FileKind.color(): Color {
    val dark = isSystemInDarkTheme()
    return when (this) {
        FileKind.VIDEO -> if (dark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        FileKind.AUDIO -> if (dark) Color(0xFFF472B6) else Color(0xFFDB2777)
        FileKind.IMAGE -> if (dark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
        FileKind.ARCHIVE -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        FileKind.DOCUMENT -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        FileKind.TORRENT -> if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
        FileKind.DISK -> if (dark) Color(0xFF94A3B8) else Color(0xFF475569)
        FileKind.APP -> if (dark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        FileKind.CODE -> if (dark) Color(0xFFFB923C) else Color(0xFFEA580C)
        FileKind.OTHER -> if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    }
}

/**
 * Thumbnail from the node for finished photos/videos, otherwise (or when the node
 * cannot make one) the file-type icon on a tinted background.
 */
@Composable
fun JobThumb(job: Job, size: Dp?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val kind = FileKind.of(job)
    val color = kind.color()
    val api = LocalContainer.current.api()
    var failed by remember(job.id) { mutableStateOf(false) }
    val sized = if (size != null) modifier.size(size) else modifier
    Box(
        sized
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (job.isViewableMedia && api != null && !failed) {
            AsyncImage(
                model = api.previewUrl(job.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
            if (kind == FileKind.VIDEO) {
                Box(
                    Modifier
                        .size(if (size != null && size < 60.dp) 20.dp else 32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
        } else {
            Icon(kind.icon(), contentDescription = kind.label, tint = color, modifier = Modifier.size(if (size != null && size < 60.dp) 24.dp else 36.dp))
        }
    }
}
