package com.minios.elizierdias.apps.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class MediaFilter {
    ALL,
    MUSIC,
    VIDEOS
}

private enum class PerformanceMode(
    val label: String
) {
    PERFORMANCE("Desempenho"),
    BALANCED("Balanceado"),
    ECONOMY("Economia")
}

private data class MediaEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String?,
    val duration: Long = 0L
)

private const val PREFS_NAME = "media_player_os"
private const val TREE_URI_KEY = "media_tree_uri"

private val AUDIO_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "wav",
    "ogg",
    "oga",
    "opus",
    "flac",
    "amr",
    "3gp"
)

private val VIDEO_EXTENSIONS = setOf(
    "mp4",
    "mkv",
    "webm",
    "avi",
    "mov",
    "3gp",
    "m4v"
)

@Composable
fun MediaPlayerOS() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mediaList by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var selectedMedia by remember { mutableStateOf<MediaEntry?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOfCompat(0L) }
    var duration by remember { mutableLongStateOfCompat(0L) }
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var performanceMode by remember { mutableStateOf(PerformanceMode.BALANCED) }
    var isScanning by remember { mutableStateOf(false) }
    var showPerformanceMenu by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(1f) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
    }

    fun scanStorage(treeUri: Uri) {
        scope.launch {
            isScanning = true

            val result = withContext(Dispatchers.IO) {
                scanMediaTree(
                    context = context,
                    treeUri = treeUri
                )
            }

            mediaList = result.sortedBy {
                it.title.lowercase(Locale.getDefault())
            }

            if (selectedMedia != null &&
                result.none { it.id == selectedMedia?.id }
            ) {
                selectedMedia = null
                isPlaying = false
                position = 0L
                duration = 0L
            }

            isScanning = false
        }
    }

    val storageLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            try {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver.takePersistableUriPermission(
                    uri,
                    flags
                )
            } catch (_: SecurityException) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }

            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(TREE_URI_KEY, uri.toString())
                .apply()

            scanStorage(uri)
        }

    fun stopCurrent() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        } catch (_: Exception) {
        }

        mediaPlayer.reset()
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

        isPlaying = false
        position = 0L
        duration = 0L
    }

    fun playAudio(entry: MediaEntry) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mediaPlayer.setDataSource(context, entry.uri)

            mediaPlayer.setOnPreparedListener { player ->
                player.setVolume(volume, volume)
                duration = player.duration.toLong()
                player.start()
                isPlaying = true
            }

            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                position = 0L
            }

            mediaPlayer.setOnErrorListener { _, _, _ ->
                isPlaying = false
                true
            }

            selectedMedia = entry
            position = 0L

            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun togglePlayback() {
        val current = selectedMedia ?: return

        if (current.isVideo) {
            return
        }

        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                mediaPlayer.start()
                isPlaying = true
            }
        } catch (_: Exception) {
        }
    }

    fun previousTrack() {
        val current = selectedMedia ?: return
        val audio = mediaList.filter { !it.isVideo }

        val index = audio.indexOfFirst { it.id == current.id }

        if (index > 0) {
            playAudio(audio[index - 1])
        }
    }

    fun nextTrack() {
        val current = selectedMedia ?: return
        val audio = mediaList.filter { !it.isVideo }

        val index = audio.indexOfFirst { it.id == current.id }

        if (index >= 0 && index < audio.lastIndex) {
            playAudio(audio[index + 1])
        }
    }

    fun seekTo(value: Long) {
        try {
            mediaPlayer.seekTo(value.toInt())
            position = value
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        val stored =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
                .getString(TREE_URI_KEY, null)

        if (stored != null) {
            try {
                val uri = Uri.parse(stored)

                val persisted =
                    context.contentResolver.persistedUriPermissions
                        .any { it.uri == uri && it.isReadPermission }

                if (persisted) {
                    scanStorage(uri)
                } else {
                    context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                        .edit()
                        .remove(TREE_URI_KEY)
                        .apply()
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (!selectedMedia?.isVideo.orFalse()) {
                    position = mediaPlayer.currentPosition.toLong()
                    duration = mediaPlayer.duration.toLong()
                }
            } catch (_: Exception) {
            }

            delay(
                when (performanceMode) {
                    PerformanceMode.PERFORMANCE -> 250L
                    PerformanceMode.BALANCED -> 500L
                    PerformanceMode.ECONOMY -> 1000L
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.stop()
            } catch (_: Exception) {
            }

            mediaPlayer.release()
        }
    }

    val filteredMedia =
        when (filter) {
            MediaFilter.ALL -> mediaList
            MediaFilter.MUSIC -> mediaList.filter { !it.isVideo }
            MediaFilter.VIDEOS -> mediaList.filter { it.isVideo }
        }

    val current = selectedMedia

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0C))
    ) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(Color(0xFF111113))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            MediaPlayerLogo(
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "MediaPlayerOS",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Áudio + Vídeo",
                    color = Color(0xFF8B8B91),
                    fontSize = 11.sp
                )
            }

            if (isScanning) {
                Text(
                    text = "A procurar...",
                    color = Color(0xFFAAAAAF),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.width(10.dp))
            }

            IconButton(
                onClick = {
                    val stored =
                        context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                        )
                            .getString(TREE_URI_KEY, null)

                    if (stored != null) {
                        try {
                            scanStorage(Uri.parse(stored))
                        } catch (_: Exception) {
                            storageLauncher.launch(null)
                        }
                    } else {
                        storageLauncher.launch(null)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = "Armazenamento",
                    tint = Color.White
                )
            }

            Box {
                IconButton(
                    onClick = {
                        showPerformanceMenu =
                            !showPerformanceMenu
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = "Modo de desempenho",
                        tint = Color.White
                    )
                }

                if (showPerformanceMenu) {
                    Column(
                        modifier = Modifier
                            .width(170.dp)
                            .background(
                                Color(0xFF1A1A1D),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        PerformanceMode.entries.forEach { mode ->
                            Text(
                                text = mode.label,
                                color =
                                    if (performanceMode == mode)
                                        Color.White
                                    else
                                        Color(0xFFAAAAAF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        performanceMode = mode
                                        showPerformanceMenu = false
                                    }
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 10.dp
                                    ),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Filtros
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0xFF101012))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterButton(
                text = "Tudo",
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

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "${mediaList.size} ficheiros",
                color = Color(0xFF77777D),
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Lista
            Column(
                modifier = Modifier
                    .width(330.dp)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF111113),
                        RoundedCornerShape(10.dp)
                    )
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 14.dp,
                            vertical = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector =
                            if (filter == MediaFilter.VIDEOS)
                                Icons.Filled.VideoLibrary
                            else
                                Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = Color(0xFFCCCCD0),
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Biblioteca",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                if (filteredMedia.isEmpty()) {
                    EmptyLibrary(
                        onOpenStorage = {
                            storageLauncher.launch(null)
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredMedia,
                            key = { it.id }
                        ) { item ->

                            MediaListItem(
                                item = item,
                                selected =
                                    current?.id == item.id
                            ) {
                                if (item.isVideo) {
                                    stopCurrent()
                                    selectedMedia = item
                                } else {
                                    playAudio(item)
                                }
                            }
                        }
                    }
                }
            }

            // Área principal
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF111113),
                        RoundedCornerShape(10.dp)
                    )
                    @Composable
private fun AudioPlayerArea(
    entry: MediaEntry,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    volume: Float,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.weight(0.7f))

        AlbumArt(
            context = LocalContext.current,
            uri = entry.uri,
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = entry.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = entry.artist.ifBlank {
                "Artista desconhecido"
            },
            color = Color(0xFF8C8C92),
            fontSize = 13.sp
        )

        if (entry.album.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = entry.album,
                color = Color(0xFF65656B),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Slider(
            value = if (duration > 0L) {
                position
                    .coerceIn(0L, duration)
                    .toFloat()
            } else {
                0f
            },
            onValueChange = {
                onSeek(it.toLong())
            },
            valueRange = 0f..duration
                .coerceAtLeast(1L)
                .toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(position),
                color = Color(0xFF77777D),
                fontSize = 10.sp
            )

            Text(
                text = formatTime(duration),
                color = Color(0xFF77777D),
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            IconButton(
                onClick = onPrevious
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable {
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (isPlaying) {
                        "Pausar"
                    } else {
                        "Reproduzir"
                    },
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            IconButton(
                onClick = onNext
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Seguinte",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.width(240.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeDown,
                contentDescription = null,
                tint = Color(0xFF88888E),
                modifier = Modifier.size(18.dp)
            )

            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = null,
                tint = Color(0xFF88888E),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VideoPlayerArea(
    entry: MediaEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var videoView by remember {
        mutableStateOf<VideoView?>(null)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var position by remember {
        mutableStateOf(0L)
    }

    var duration by remember {
        mutableStateOf(0L)
    }

    DisposableEffect(entry.uri) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {

            AndroidView(
                factory = {
                    VideoView(context).apply {

                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                        setVideoURI(entry.uri)

                        setOnPreparedListener { player ->
                            duration =
                                player.duration
                                    .coerceAtLeast(0)
                                    .toLong()

                            start()
                            isPlaying = true
                        }

                        setOnCompletionListener {
                            isPlaying = false
                            position = 0L
                        }

                        setOnErrorListener { _, _, _ ->
                            isPlaying = false
                            true
                        }

                        videoView = this
                    }
                },
                update = { view ->
                    videoView = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        LaunchedEffect(
            isPlaying,
            entry.uri
        ) {
            while (isPlaying) {
                videoView?.let { view ->
                    position =
                        view.currentPosition
                            .coerceAtLeast(0)
                            .toLong()

                    duration =
                        view.duration
                            .coerceAtLeast(0)
                            .toLong()
                }

                delay(500L)
            }
        }

        LinearProgressIndicator(
            progress = {
                if (duration > 0L) {
                    (
                        position
                            .coerceIn(0L, duration)
                            .toFloat() /
                            duration.toFloat()
                    )
                } else {
                    0f
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111113))
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {

            IconButton(
                onClick = {
                    videoView?.let { view ->
                        val target =
                            (
                                view.currentPosition -
                                    10_000
                            ).coerceAtLeast(0)

                        view.seekTo(target)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription =
                        "Voltar 10 segundos",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = {
                    videoView?.let { view ->
                        if (view.isPlaying) {
                            view.pause()
                            isPlaying = false
                        } else {
                            view.start()
                            isPlaying = true
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (isPlaying) {
                        "Pausar vídeo"
                    } else {
                        "Reproduzir vídeo"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = {
                    videoView?.let { view ->
                        val target =
                            (
                                view.currentPosition +
                                    10_000
                            ).coerceAtMost(
                                view.duration
                                    .coerceAtLeast(0)
                            )

                        view.seekTo(target)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription =
                        "Avançar 10 segundos",
                    tint = Color.White
                )
            }

            Spacer(
                modifier = Modifier.width(15.dp)
            )

            Text(
                text =
                    "${formatTime(position)} / " +
                        formatTime(duration),
                color = Color(0xFF99999F),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AlbumArt(
    context: Context,
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<
        android.graphics.Bitmap?
    >(
        initialValue = null,
        key1 = uri
    ) {
        value = withContext(Dispatchers.IO) {
            extractAlbumArt(
                context = context,
                uri = uri
            )
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF18181B)),
        contentAlignment = Alignment.Center
    ) {

        val art = bitmap

        if (art != null) {
            androidx.compose.foundation.Image(
                bitmap = art.asImageBitmap(),
                contentDescription =
                    "Capa do álbum",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector =
                    Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = Color(0xFF55555B),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

private fun extractAlbumArt(
    context: Context,
    uri: Uri
): android.graphics.Bitmap? {

    val retriever =
        MediaMetadataRetriever()

    return try {
        retriever.setDataSource(
            context,
            uri
        )

        val picture =
            retriever.embeddedPicture

        if (picture != null) {
            BitmapFactory.decodeByteArray(
                picture,
                0,
                picture.size
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

private fun extractMetadata(
    context: Context,
    uri: Uri
): Triple<String, String, String> {

    val retriever =
        MediaMetadataRetriever()

    return try {
        retriever.setDataSource(
            context,
            uri
        )

        Triple(
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_TITLE
            ).orEmpty(),

            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_ARTIST
            ).orEmpty(),

            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_ALBUM
            ).orEmpty()
        )
    } catch (_: Exception) {
        Triple(
            "",
            "",
            ""
        )
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

private fun scanMediaTree(
    context: Context,
    treeUri: Uri
): List<MediaEntry> {

    val result =
        mutableListOf<MediaEntry>()

    fun scanDocument(
        documentId: String
    ) {

        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    treeUri,
                    documentId
                )

        val projection = arrayOf(
            DocumentsContract.Document
                .COLUMN_DOCUMENT_ID,

            DocumentsContract.Document
                .COLUMN_DISPLAY_NAME,

            DocumentsContract.Document
                .COLUMN_MIME_TYPE
        )

        context.contentResolver.query(
            childrenUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->

            val idIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document
                        .COLUMN_DOCUMENT_ID
                )

            val nameIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document
                        .COLUMN_DISPLAY_NAME
                )

            val mimeIndex =
                cursor.getColumnIndex(
                    DocumentsContract.Document
                        .COLUMN_MIME_TYPE
                )

            if (
                idIndex < 0 ||
                nameIndex < 0 ||
                mimeIndex < 0
            ) {
                return@use
            }

            while (cursor.moveToNext()) {

                val id =
                    cursor.getString(idIndex)

                val name =
                    cursor.getString(nameIndex)

                val mime =
                    cursor.getString(mimeIndex)

                if (
                    mime ==
                    DocumentsContract.Document
                        .MIME_TYPE_DIR
                ) {
                    scanDocument(id)
                    continue
                }

                val extension =
                    name
                        .substringAfterLast(
                            '.',
                            ""
                        )
                        .lowercase(
                            Locale.getDefault()
                        )

                val isAudio =
                    extension in AUDIO_EXTENSIONS ||
                        mime.startsWith("audio/")

                val isVideo =
                    extension in VIDEO_EXTENSIONS ||
                        mime.startsWith("video/")

                if (!isAudio && !isVideo) {
                    continue
                }

                val uri =
                    DocumentsContract
                        .buildDocumentUriUsingTree(
                            treeUri,
                            id
                        )

                val metadata =
                    extractMetadata(
                        context,
                        uri
                    )

                val title =
                    metadata.first.ifBlank {
                        name.substringBeforeLast(
                            '.',
                            name
                        )
                    }

                result += MediaEntry(
                    id = uri.toString(),
                    title = title,
                    artist = metadata.second,
                    album = metadata.third,
                    uri = uri,
                    isVideo = isVideo,
                    mimeType = mime
                )
            }
        }
    }

    val rootId =
        DocumentsContract.getTreeDocumentId(
            treeUri
        )

    scanDocument(rootId)

    return result
}

@Composable
private fun EmptyLibrary(
    onOpenStorage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = Color(0xFF4F4F55),
            modifier = Modifier.size(42.dp)
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text =
                "Nenhum ficheiro multimédia",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text =
                "Escolha uma pasta para procurar " +
                    "músicas e vídeos.",
            color = Color(0xFF77777D),
            fontSize = 11.sp
        )

        Spacer(
            modifier = Modifier.height(15.dp)
        )

        Text(
            text = "Abrir armazenamento",
            color = Color.White,
            modifier = Modifier
                .clip(
                    RoundedCornerShape(7.dp)
                )
                .background(
                    Color(0xFF242427)
                )
                .clickable(
                    onClick = onOpenStorage
                )
                .padding(
                    horizontal = 14.dp,
                    vertical = 9.dp
                ),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PlayerEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFF111113)
            ),
        horizontalAlignme
                    
