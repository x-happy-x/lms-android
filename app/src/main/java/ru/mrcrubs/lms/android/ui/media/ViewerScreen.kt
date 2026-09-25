package ru.mrcrubs.lms.android.ui.media

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import ru.mrcrubs.lms.android.LocalContainer
import ru.mrcrubs.lms.android.ui.MainViewModel
import ru.mrcrubs.lms.core.FileKind
import ru.mrcrubs.lms.core.Format
import ru.mrcrubs.lms.core.Job
import ru.mrcrubs.lms.core.RouterApi

/**
 * Full-screen photos and videos streamed from the node through the router: nothing is
 * saved on the phone. Swipe between items; pinch to zoom photos; videos seek via Range.
 */
@Composable
fun ViewerScreen(viewModel: MainViewModel, jobId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Keep the list stable while viewing so polling does not shift pages.
    val media = remember { mediaJobs(state.jobs) }
    val api = LocalContainer.current.api()
    if (media.isEmpty() || api == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val start = media.indexOfFirst { it.id == jobId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { media.size }
    val current = media[pager.currentPage.coerceIn(0, media.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
            val job = media[page]
            if (FileKind.of(job) == FileKind.VIDEO) {
                VideoPage(api, job, playing = pager.currentPage == page)
            } else {
                ZoomableImage(api, job)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(current.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pager.currentPage + 1} из ${media.size}" + (current.outputSizeBytes?.let { " · ${Format.bytes(it)}" } ?: ""),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { viewModel.downloadToDevice(current) }) {
                Icon(Icons.Filled.Download, "Скачать на телефон", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(api: RouterApi, job: Job) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = api.fileUrl(job.id, inline = true),
        contentDescription = job.title,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(job.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(api: RouterApi, job: Job, playing: Boolean) {
    val context = LocalContext.current
    val player = remember(job.id) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(api.authHeaders())
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(api.fileUrl(job.id, inline = true)))
                prepare()
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    DisposableEffect(playing) {
        player.playWhenReady = playing
        if (!playing) player.pause()
        onDispose { }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
                keepScreenOn = true
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
