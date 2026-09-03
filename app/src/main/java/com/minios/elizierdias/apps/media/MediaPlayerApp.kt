package com.minios.elizierdias.apps.media

import android.content.ContentUris
import android.media.MediaPlayer
import android.provider.MediaStore
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class MediaItem(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: android.net.Uri,
    val isVideo: Boolean,
    val duration: Long
)

private enum class MediaFilter {
    ALL,
    MUSIC,
    VIDEOS
}

@Composable
fun MediaPlayerApp() {
    val context = LocalContext.current

    var mediaItems by remember {
        mutableStateOf<List<MediaItem>>(emptyList())
    }

    var filter by remember {
        mutableStateOf(MediaFilter.ALL)
    }

    var selectedItem by remember {
        mutableStateOf<MediaItem?>(null)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var progress by remember {
        mutableLongStateOf(0L)
    }

    var duration by remember {
        mutableLongStateOf(0L)
    }

    var playerPrepared by remember {
        mutableStateOf(false)
    }

    val audioPlayer = remember {
        MediaPlayer()
    }

    fun loadMedia() {
        val result = mutableListOf<MediaItem>()

        // -------------------------
        // MUSIC
        // -------------------------

        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

                val titleColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                val artistColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)

                    result += MediaItem(
                        id = id,
                        title = cursor.getString(titleColumn)
                            ?: "Sem título",
                        artist = cursor.getString(artistColumn)
                            ?: "Artista desconhecido",
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        ),
                        isVideo = false,
                        duration = cursor.getLong(durationColumn)
                    )
                }
            }
        } catch (_: Exception) {
            // Permission or MediaStore failure.
        }

        // -------------------------
        // VIDEOS
        // -------------------------

        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION
        )

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { cursor ->

                val idColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)

                val titleColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)

                val durationColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)

                    result += MediaItem(
                        id = id,
                        title = cursor.getString(titleColumn)
                            ?: "Vídeo",
                        artist = "Vídeo",
                        uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        ),
                        isVideo = true,
                        duration = cursor.getLong(durationColumn)
                    )
                }
            }
        } catch (_: Exception) {
            // Permission or MediaStore failure.
        }

        mediaItems = result
    }

    fun playAudio(item: MediaItem) {
        if (item.isVideo) return

        try {
            audioPlayer.reset()
            playerPrepared = false
            isPlaying = false
            progress = 0L
            duration = item.duration

            audioPlayer.setDataSource(
                context,
                item.uri
            )

            audioPlayer.setOnPreparedListener { player ->
                playerPrepared = true
                duration = player.duration.toLong()
                player.start()
                isPlaying = true
            }

            audioPlayer.setOnCompletionListener {
                isPlaying = false
                progress = 0L
            }

            audioPlayer.setOnErrorListener { _, _, _ ->
                isPlaying = false
                playerPrepared = false
                true
            }

            selectedItem = item

            audioPlayer.prepareAsync()

        } catch (_: Exception) {
            isPlaying = false
            playerPrepared = false
        }
    }

    fun togglePlayback() {
        if (!playerPrepared) return

        try {
            if (audioPlayer.isPlaying) {
                audioPlayer.pause()
                isPlaying = false
            } else {
                audioPlayer.start()
                isPlaying = true
            }
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    LaunchedEffect(Unit) {
        loadMedia()
    }

    LaunchedEffect(isPlaying, selectedItem) {
        while (isPlaying) {
            try {
                if (playerPrepared && audioPlayer.isPlaying) {
                    progress = audioPlayer.currentPosition.toLong()
                }
            } catch (_: Exception) {
                break
            }

            kotlinx.coroutines.delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                audioPlayer.stop()
            } catch (_: Exception) {
            }

            audioPlayer.release()
        }
    }

    val visibleItems = when (filter) {
        MediaFilter.ALL -> mediaItems
        MediaFilter.MUSIC ->
            mediaItems.filter { !it.isVideo }

        MediaFilter.VIDEOS ->
            mediaItems.filter { it.isVideo }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {

        // --------------------------------
        // HEADER
        // --------------------------------

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(0xFF161B22))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = null,
                tint = Color(0xFF58A6FF),
                modifier = Modifier.size(19.dp)
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text = "MediaPlayerOS",
                color = Color(0xFFE6EDF3),
                fontSize = 14.sp
            )

            Spacer(
                modifier = Modifier.width(18.dp)
            )

            FilterButton(
                text = "Todos",
                selected = filter == MediaFilter.ALL
            ) {
                filter = MediaFilter.ALL
            }

            FilterButton(
                text = "Música",
                selected = filter == MediaFilter.MUSIC
            ) {
                filter = MediaFilter.MUSIC
            }

            FilterButton(
                text = "Vídeos",
                selected = filter == MediaFilter.VIDEOS
            ) {
                filter = MediaFilter.VIDEOS
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    loadMedia()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Atualizar biblioteca",
                    tint = Color(0xFF8B949E),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // --------------------------------
        // CONTENT
        // --------------------------------

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {

            // --------------------------------
            // LIBRARY
            // --------------------------------

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {

                if (visibleItems.isEmpty()) {

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(60.dp)
                            )

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            Text(
                                text = "MediaPlayerOS",
                                color = Color(0xFFE6EDF3),
                                fontSize = 20.sp
                            )

                            Spacer(
                                modifier = Modifier.height(5.dp)
                            )

                            Text(
                                text = "Nenhum ficheiro multimédia encontrado",
                                color = Color(0xFF8B949E),
                                fontSize = 12.sp
                            )
                        }
                    }

                } else {

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {

                        items(
                            items = visibleItems,
                            key = {
                                it.uri.toString()
                            }
                        ) { item ->

                            MediaRow(
                                item = item,
                                selected =
                                    selectedItem?.uri == item.uri,
                                onClick = {

                                    if (item.isVideo) {
                                        selectedItem = item
                                        isPlaying = false
                                        progress = 0L
                                        duration = item.duration
                                    } else {
                                        playAudio(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            // --------------------------------
            // PLAYER
            // --------------------------------

            PlayerPanel(
                item = selectedItem,
                isPlaying = isPlaying,
                progress = progress,
                duration = duration,
                onPlayPause = {
                    togglePlayback()
                },
                onPrevious = {

                    val index =
                        visibleItems.indexOf(selectedItem)

                    if (index > 0) {

                        val previous =
                            visibleItems[index - 1]

                        if (!previous.isVideo) {
                            playAudio(previous)
                        }
                    }
                },
                onNext = {

                    val index =
                        visibleItems.indexOf(selectedItem)

                    if (
                        index >= 0 &&
                        index < visibleItems.lastIndex
                    ) {

                        val next =
                            visibleItems[index + 1]

                        if (!next.isVideo) {
                            playAudio(next)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun FilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color =
            if (selected) {
                Color(0xFFE6EDF3)
            } else {
                Color(0xFF8B949E)
            },
        fontSize = 11.sp,
        modifier = Modifier
            .clip(
                RoundedCornerShape(5.dp)
            )
            .background(
                if (selected) {
                    Color(0xFF21262D)
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                onClick = onClick
            )
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp
            )
    )
}

@Composable
private fun MediaRow(
    item: MediaItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(6.dp)
            )
            .background(
                if (selected) {
                    Color(0xFF21262D)
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                onClick = onClick
            )
            .padding(
                horizontal = 10.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                if (item.isVideo) {
                    Icons.Filled.VideoLibrary
                } else {
                    Icons.Filled.MusicNote
                },
            contentDescription = null,
            tint =
                if (item.isVideo) {
                    Color(0xFFBC8CFF)
                } else {
                    Color(0xFF58A6FF)
                },
            modifier = Modifier.size(20.dp)
        )

        Spacer(
            modifier = Modifier.width(10.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = item.title,
                color = Color(0xFFE6EDF3),
                fontSize = 13.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text = item.artist,
                color = Color(0xFF8B949E),
                fontSize = 10.sp,
                maxLines = 1
            )
        }

        Text(
            text = formatDuration(item.duration),
            color = Color(0xFF8B949E),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PlayerPanel(
    item: MediaItem?,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(
                Color(0xFF161B22)
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (item?.isVideo == true) {

            AndroidView(
                factory = { context ->
                    VideoView(context)
                },
                update = { videoView ->

                    if (
                        videoView.tag != item.uri.toString()
                    ) {

                        videoView.tag =
                            item.uri.toString()

                        videoView.setVideoURI(
                            item.uri
                        )

                        videoView.setOnPreparedListener {
                            it.isLooping = false
                            videoView.start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(
                        RoundedCornerShape(6.dp)
                    )
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = item.title,
                color = Color(0xFFE6EDF3),
                fontSize = 13.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = "Vídeo",
                color = Color(0xFF8B949E),
                fontSize = 10.sp
            )

        } else {

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Icon(
               
