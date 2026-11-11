package ru.mrcrubs.lms.android.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import ru.mrcrubs.lms.core.RouterApi

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RemoteMediaDialog(title:String,url:String,mimeType:String,api:RouterApi,onClose:()->Unit) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Scaffold(topBar={TopAppBar(title={Text(title,maxLines=1)},navigationIcon={IconButton(onClick=onClose){Icon(Icons.Default.Close,"Закрыть просмотр")}})}) { padding ->
            if(mimeType.startsWith("image/")) AsyncImage(url,title,Modifier.padding(padding).fillMaxSize().background(Color.Black))
            else {
                val context=LocalContext.current
                val player=remember(url){ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setDefaultRequestProperties(api.headersFor(url)))).build().apply{setMediaItem(MediaItem.fromUri(url));prepare();playWhenReady=true}}
                DisposableEffect(player){onDispose{player.release()}}
                AndroidView(factory={PlayerView(it).apply{this.player=player;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}},modifier=Modifier.padding(padding).fillMaxSize().background(Color.Black))
            }
        }
    }
}
