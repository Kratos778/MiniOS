package com.minios.elizierdias.apps.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    val label: String,
    val updateInterval: Long
) {
    PERFORMANCE("Desempenho", 250L),
    BALANCED("Balanceado", 500L),
    ECONOMY("Economia", 1000L)
}

private data class MediaEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String?,
    val duration: Long
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

    var mediaList by remember {
        mutableStateOf<List<MediaEntry>>(emptyList())
    }

    var selectedMedia by remember {
        mutableStateOf<MediaEntry?>(null)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var position by remember {
        mutableLongStateOf(0L)
    }

    var duration by remember {
        mutableLongStateOf(0L)
    }

    var filter by remember {
        mutableStateOf(MediaFilter.ALL)
    }

    var performanceMode by remember {
        mutableStateOf(PerformanceMode.BALANCED)
    }

    var isScanning by remember {
        mutableStateOf(false)
    }

    var volume by remember {
        mutableFloatStateOf(1f)
    }

    var showPerformanceMenu by remember {
        mutableStateOf(false)
    }

    var videoView by remember {
        mutableStateOf<VideoView?>(null)
    }

    val mediaPlayer = remember {
        MediaPlayer()
    }

    fun stopAudio() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        } catch (_: Exception) {
        }

        try {
            mediaPlayer.reset()
        } catch (_: Exception) {
        }

        isPlaying = false
        position = 0L
        duration = 0L
    }

    fun playAudio(entry: MediaEntry) {
        try {
            videoView?.stopPlayback()

            mediaPlayer.reset()
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mediaPlayer.setDataSource(context, entry.uri)

            mediaPlayer.setOnPreparedListener { player ->
                player.setVolume(volume, volume)

                duration = player.duration.toLong()
                position = 0L

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

            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun playVideo(entry: MediaEntry) {
        stopAudio()

        selectedMedia = entry
        position = 0L
        duration = entry.duration
        isPlaying = true
    }

    fun togglePlayback() {
        val current = selectedMedia ?: return

        if (current.isVideo) {
            videoView?.let { video ->
                if (video.isPlaying) {
                    video.pause()
                    isPlaying = false
                } else {
                    video.start()
                    isPlaying = true
                }
            }
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

        val audio =
            mediaList.filter { !it.isVideo }

        val index =
            audio.indexOfFirst {
                it.id == current.id
            }

        if (index > 0) {
            playAudio(audio[index - 1])
        }
    }

    fun nextTrack() {
        val current = selectedMedia ?: return

        val audio =
            mediaList.filter { !it.isVideo }

        val index =
            audio.indexOfFirst {
                it.id == current.id
            }

        if (index >= 0 && index < audio.lastIndex) {
            playAudio(audio[index + 1])
        }
    }

    fun seekTo(value: Long) {
        val current = selectedMedia ?: return

        try {
            if (current.isVideo) {
                videoView?.seekTo(value.toInt())
            } else {
                mediaPlayer.seekTo(value.toInt())
            }

            position = value
        } catch (_: Exception) {
        }
    }

    fun scanStorage(treeUri: Uri) {
        scope.launch {
            isScanning = true

            val result =
                withContext(Dispatchers.IO) {
                    scanMediaTree(
                        context = context,
                        treeUri = treeUri
                    )
                }

            mediaList =
                result.sortedBy {
                    it.title.lowercase(
                        Locale.getDefault()
                    )
                }

            val current = selectedMedia

            if (
                current != null &&
                result.none {
                    it.id == current.id
                }
            ) {
                stopAudio()
                selectedMedia = null
            }

            isScanning = false
        }
    }

    val storageLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            try {
                val flags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        flags
                    )
            } catch (_: SecurityException) {
                try {
                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                } catch (_: Exception) {
                }
            }

            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    TREE_URI_KEY,
                    uri.toString()
                )
                .apply()

            scanStorage(uri)
        }

    LaunchedEffect(Unit) {
        val stored =
            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    TREE_URI_KEY,
                    null
                )

        if (stored != null) {
            try {
                val uri = Uri.parse(stored)

                val permission =
                    context.contentResolver
                        .persistedUriPermissions
                        .any {
                            it.uri == uri &&
                                it.isReadPermission
                        }

                if (permission) {
                    scanStorage(uri)
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(
        isPlaying,
        selectedMedia,
        performanceMode
    ) {
        while (isPlaying) {
            try {
                val current =
                    selectedMedia

                if (current != null) {
                    if (current.isVideo) {
                        videoView?.let {
                            position =
                                it.currentPosition.toLong()

                            duration =
                                it.duration.toLong()
                        }
                    } else {
                        position =
                            mediaPlayer.currentPosition
                                .toLong()

                        duration =
                            mediaPlayer.duration
                                .toLong()
                    }
                }
            } catch (_: Exception) {
            }

            delay(
                performanceMode.updateInterval
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

            videoView?.stopPlayback()
        }
    }

    val filteredMedia =
        when (filter) {
            MediaFilter.ALL ->
                mediaList

            MediaFilter.MUSIC ->
                mediaList.filter {
                    !it.isVideo
                }

            MediaFilter.VIDEOS ->
                mediaList.filter {
                    it.isVideo
                }
        }

    val current =
        selectedMedia

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFF09090B)
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(
                    Color(0xFF111113)
                )
                .padding(
                    horizontal = 14.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            MediaPlayerLogo(
                modifier = Modifier.size(38.dp)
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "MediaPlayerOS",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = "Áudio + Vídeo",
                    color =
                        Color(0xFF85858B),
                    fontSize = 11.sp
                )
            }

            if (isScanning) {
                Text(
                    text = "A procurar...",
                    color =
                        Color(0xFF99999F),
                    fontSize = 11.sp
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )
            }

            IconButton(
                onClick = {
                    val stored =
                        context
                            .getSharedPreferences(
                                PREFS_NAME,
                                Context.MODE_PRIVATE
                            )
                            .getString(
                                TREE_URI_KEY,
                                null
                            )

                    if (stored != null) {
                        try {
                            scanStorage(
                                Uri.parse(stored)
                            )
                        } catch (_: Exception) {
                            storageLauncher
                                .launch(null)
                        }
                    } else {
                        storageLauncher
                            .launch(null)
                    }
                }
            ) {
                Icon(
                    imageVector =
                        Icons.Filled.Storage,
                    contentDescription =
                        "Armazenamento",
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
                        imageVector =
                            Icons.Filled.LibraryMusic,
                        contentDescription =
                            "Desempenho",
                        tint = Color.White
                    )
                }

                if (showPerformanceMenu) {
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .background(
                                Color(0xFF1B1B1E),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(
                                vertical = 6.dp
                            )
                    ) {
                        PerformanceMode.entries
                            .forEach { mode ->

                                Text(
                                    text = mode.label,
                                    color =
                                        if (
                                            mode ==
                                                performanceMode
                                        ) {
                                            Color.White
                                        } else {
                                            Color(
                                                0xFF9B9BA1
                                            )
                                        },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                performanceMode =
                                                    mode

                                                showPerformanceMenu =
                                                    false
                                            }
                                            .padding(
                                                horizontal =
                                                    12.dp,
                                                vertical =
                                                    10.dp
                                            ),
                                    fontSize = 12.sp
                                )
                            }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Color(0xFF101012)
                )
                .padding(
                    horizontal = 10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(7.dp)
        ) {

            FilterButton(
                text = "Tudo",
                selected =
                    filter == MediaFilter.ALL
            ) {
                filter = MediaFilter.ALL
            }

            FilterButton(
                text = "Música",
                selected =
                    filter == MediaFilter.MUSIC
            ) {
                filter = MediaFilter.MUSIC
            }

            FilterButton(
                text = "Vídeos",
                selected =
                    filter == MediaFilter.VIDEOS
            ) {
                filter = MediaFilter.VIDEOS
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                text =
                    "${mediaList.size} ficheiros",
                color =
                    Color(0xFF707077),
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF111113),
                        RoundedCornerShape(10.dp)
                    )
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            if (
                                filter ==
                                    MediaFilter.VIDEOS
                            ) {
                                Icons.Filled.VideoLibrary
                            } else {
                                Icons.Filled.LibraryMusic
                            },
                        contentDescription =
                            null,
                        tint =
                            Color(0xFFD0D0D4),
                        modifier =
                            Modifier.size(19.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        text = "Biblioteca",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                if (filteredMedia.isEmpty()) {

                    EmptyLibrary(
                        onOpenStorage = {
                            storageLauncher
                                .launch(null)
                        }
                    )

                } else {

                    LazyColumn(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {

                        items(
                            items = filteredMedia,
                            key = {
                                it.id
                            }
                        ) { item ->

                            MediaListItem(
                                item = item,
                                selected =
                                    current?.id ==
                                        item.id
                            ) {

                                if (item.isVideo) {
                                    playVideo(item)
                                } else {
                                    playAudio(item)
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF111113),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(16.dp)
            ) {

                if (current == null) {

                    EmptyPlayer()

                } else {

                    if (current.isVideo) {

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(
                                    RoundedCornerShape(
                                        10.dp
                                    )
                                ),
                            factory = { ctx ->

                                VideoView(ctx).apply {

                                    layoutParams =
                                        ViewGroup
                                            .LayoutParams(
                                                ViewGroup
                                                    .LayoutParams
                                                    .MATCH_PARENT,
                                                ViewGroup
                                                    .LayoutParams
                                                    .MATCH_PARENT
                                            )

                                    setVideoURI(
                                        current.uri
                                    )

                                    setOnPreparedListener {
                                        duration =
                                            it.duration
                                                .toLong()

                                        it.start()
                                        isPlaying = true
                                    }

                                    setOnCompletionListener {
                                        isPlaying = false
                                        position = 0L
                                    }

                                    videoView = this
                                }
                            },
                            update = { video ->
                                videoView = video
                            }
                        )

                    } else {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            AlbumArt(
                                uri =
                                    current.uri,
                                modifier =
                                    Modifier
                                        .size(230.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                12.dp
                                            )
                                        )
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Text(
                        text = current.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            if (
                                current.artist
                                    .isNotBlank()
                            ) {
                                current.artist
                            } else {
                                "Artista desconhecido"
                            },
                        color =
                            Color(0xFF99999F),
                        fontSize = 12.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    LinearProgressIndicator(
                        progress = {
                            if (duration > 0L) {
                                (
                                    position
                                        .toFloat() /
                                        duration
                                        .toFloat()
                                ).coerceIn(
                                    0f,
                                    1f
                                )
                            } else {
                                0f
                            }
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Slider(
                        value =
                            if (duration > 0L) {
                                position
                                    .toFloat()
                                    .coerceIn(
                                        0f,
                                        duration.toFloat()
                                    )
                            } else {
                                0f
                            },
                        onValueChange = {
                            seekTo(
                                it.toLong()
                            )
                        },
                        valueRange =
                            0f..maxOf(
                                duration.toFloat(),
                                1f
                            )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        IconButton(
                            onClick = {
                                previousTrack()
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled
                                        .SkipPrevious,
                                contentDescription =
                                    "Anterior",
                                tint =
                                    Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                togglePlayback()
                            },
                            modifier =
                                Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector =
                                    if (isPlaying) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                contentDescription =
                                    if (isPlaying) {
                                        "Pausar"
                                    } else {
                                        "Reproduzir"
                                    },
                                tint =
                                    Color.White,
                                modifier =
                                    Modifier.size(34.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                nextTrack()
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled
                                        .SkipNext,
                                contentDescription =
                                    "Próxima",
                                tint =
                                    Color.White
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.width(20.dp)
                        )

                        IconButton(
                            onClick = {
                                volume =
                                    (
                                        volume - 0.1f
                                    ).coerceIn(
                                        0f,
                                        1f
                                    )

                                mediaPlayer.setVolume(
                                    volume,
                                    volume
                                )
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled
                                        .VolumeDown,
                                contentDescription =
                                    "Diminuir volume",
                                tint =
                                    Color.White
                            )
                        }

                        Slider(
                            value = volume,
                            onValueChange = {
                                volume =
                                    it

                                try {
                                    mediaPlayer
                                        .setVolume(
                                            volume,
                                            volume
                                        )
                                } catch (_: Exception) {
                                }
                            },
                            modifier =
                                Modifier.width(
                                    130.dp
                                )
                        )

                        IconButton(
                            onClick = {
                                volume =
                                    (
                                        volume + 0.1f
                                    ).coerceIn(
                                        0f,
                                        1f
                                    )

                                mediaPlayer.setVolume(
                                    volume,
                                    volume
                                )
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled
                                        .VolumeUp,
                                contentDescription =
                                    "Aumentar volume",
                                tint =
                                    Color.White
                            )
                        }
                    }
                }
            }
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
                Color.White
            } else {
                Color(0xFF8C8C92)
            },
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(7.dp)
                )
                .background(
                    if (selected) {
                        Color(0xFF29292D)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(
                    onClick = onClick
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 7.dp
                ),
        fontSize = 11.sp,
        fontWeight =
            if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }
    )
}

@Composable
private fun MediaListItem(
    item: MediaEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            )
            .background(
                if (selected) {
                    Color(0xFF222225)
                } else {
                    Color.Transparent
                }
            )
            .padding(
                horizontal = 12.dp,
                vertical = 9.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                if (item.isVideo) {
                    Icons.Filled.VideoLibrary
                } else {
                    Icons.Filled.LibraryMusic
                },
            contentDescription = null,
            tint =
                if (selected) {
                    Color.White
                } else {
                    Color(0xFF77777D)
                },
            modifier =
                Modifier.size(25.dp)
        )

        Spacer(
            modifier =
                Modifier.width(10.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1
            )

            Text(
                text =
                    if (
                        item.artist.isNotBlank()
                    ) {
                        item.artist
                    } else {
                        if (item.isVideo) {
                            "Vídeo"
                        } else {
                            "Áudio"
                        }
                    },
                color =
                    Color(0xFF77777D),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
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
            imageVector =
                Icons.Filled.Folder,
            contentDescription = null,
            tint =
                Color(0xFF55555B),
            modifier =
                Modifier.size(42.dp)
        )

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        Text(
            text = "Biblioteca vazia",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            modifier =
                Modifier.height(5.dp)
        )

        Text(
            text =
                "Escolha uma pasta para procurar músicas e vídeos.",
            color =
                Color(0xFF77777D),
            fontSize = 11.sp
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text = "Escolher armazenamento",
            color = Color.White,
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(7.dp)
                    )
                    .background(
                        Color(0xFF29292D)
                    )
                    .clickable(
                        onClick =
                            onOpenStorage
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
private fun EmptyPlayer() {
    Column(
        modifier =
            Modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        MediaPlayerLogo(
            modifier =
                Modifier.size(90.dp)
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text = "MediaPlayerOS",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "Selecione uma música ou vídeo",
            color =
                Color(0xFF77777D),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AlbumArt(
    uri: Uri,
    modifier: Modifier
) {
    val context = LocalContext.current

    val bitmap =
        remember(uri) {
            try {
                val retriever =
                    MediaMetadataRetriever()

                retriever.setDataSource(
                    context,
                    uri
                )

                val data =
                    retriever.embeddedPicture

                retriever.release()

                if (data != null) {
                    android.graphics.BitmapFactory
                        .decodeByteArray(
                            data,
                            0,
                            data.size
                        )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap =
                bitmap.asImageBitmap(),
            contentDescription =
                "Capa do álbum",
            modifier = modifier,
            contentScale =
                ContentScale.Crop
        )
    } else {
        Box(
            modifier =
                modifier.background(
                    Color(0xFF1B1B1F)
                ),
            contentAlignment =
                Alignment.Center
        ) {
            Icon(
                imageVector =
                    Icons.Filled.LibraryMusic,
                contentDescription =
                    null,
                tint =
                    Color(0xFF55555B),
                modifier =
                    Modifier.size(70.dp)
            )
        }
    }
}

@Composable
private fun MediaPlayerLogo(
    modifier: Modifier
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
    ) {
        val centerX =
            size.width / 2f

        val centerY =
            size.height / 2f

        val radius =
            minOf(
                size.width,
                size.height
            ) * 0.42f

        drawCircle(
            color = Color.White,
            radius = radius,
            center =
                androidx.compose.ui.geometry.Offset(
                    centerX,
                    centerY
                )
        )

        drawCircle(
            color =
                Color(0xFF09090B),
            radius =
                radius * 0.72f,
            center =
                androidx.compose.ui.geometry.Offset(
                    centerX,
                    centerY
                )
        )

        val path =
            androidx.compose.ui.graphics.Path()

        path.moveTo(
            centerX - radius * 0.30f,
            centerY - radius * 0.48f
        )

        path.lineTo(
            centerX + radius * 0.48f,
            centerY
        )

        path.lineTo(
            centerX - radius * 0.30f,
            centerY + radius * 0.48f
        )

        path.close()

        drawPath(
            path = path,
            color = Color.White
        )
    }
}

private fun scanMediaTree(
    context: Context,
    treeUri: Uri
): List<MediaEntry> {

    val result =
        mutableListOf<MediaEntry>()

    val resolver =
        context.contentResolver

    fun scanDirectory(uri: Uri) {

        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    uri,
                    DocumentsContract
                        .getTreeDocumentId(uri)
                )

        val projection =
            arrayOf(
                DocumentsContract
                    .Document.COLUMN_DOCUMENT_ID,
                DocumentsContract
                    .Document.COLUMN_DISPLAY_NAME,
                DocumentsContract
                    .Document.COLUMN_MIME_TYPE,
                DocumentsContract
                    .Document.COLUMN_SIZE
            )

        try {
            resolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->

                val idIndex =
                    cursor.getColumnIndex(
                        DocumentsContract
                            .Document
                            .COLUMN_DOCUMENT_ID
                    )

                val nameIndex =
                    cursor.getColumnIndex(
                        DocumentsContract
                            .Document
                            .COLUMN_DISPLAY_NAME
                    )

                val mimeIndex =
                    cursor.getColumnIndex(
                        DocumentsContract
                            .Document
                            .COLUMN_MIME_TYPE
                    )

                while (cursor.moveToNext()) {

                    val documentId =
                        cursor.getString(
                            idIndex
                        )

                    val name =
                        cursor.getString(
                            nameIndex
                        ) ?: continue

                    val mime =
                        if (mimeIndex >= 0) {
                            cursor.getString(
                                mimeIndex
                            )
                        } else {
                            null
                        }

                    val childUri =
                        DocumentsContract
                            .buildDocumentUriUsingTree(
                                treeUri,
                                documentId
                            )

                    if (
                        mime ==
                        DocumentsContract
                            .Document
                            .MIME_TYPE_DIR
                    ) {
                        scanDirectory(
                            childUri
                        )
                    } else {

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
                            extension in
                                AUDIO_EXTENSIONS

                        val isVideo =
                            extension in
                                VIDEO_EXTENSIONS

                        if (!isAudio && !isVideo) {
                            continue
                        }

                        val metadata =
                            readMetadata(
                                context,
                                childUri,
                                isVideo
                            )

                        result += MediaEntry(
                            id =
                                childUri.toString(),
                            title =
                                metadata.first
                                    .takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: name
                                        .substringBeforeLast(
                                            '.'
                                        ),
                            artist =
                                metadata.second,
                            album =
                                metadata.third,
                            uri = childUri,
                            isVideo = isVideo,
                            mimeType = mime,
                            duration =
                                metadata.fourth
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    scanDirectory(treeUri)

    return result
}

private fun readMetadata(
    context: Context,
    uri: Uri,
    isVideo: Boolean
): Quadruple<String, String, String, Long> {

    return try {

        val retriever =
            MediaMetadataRetriever()

        retriever.setDataSource(
            context,
            uri
        )

        val title =
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_TITLE
            ).orEmpty()

        val artist =
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_ARTIST
            ).orEmpty()

        val album =
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_ALBUM
            ).orEmpty()

        val duration =
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_DURATION
            )
                ?.toLongOrNull()
                ?: 0L

        retriever.release()

        Quadruple(
            title,
            artist,
            album,
            duration
        )

    } catch (_: Exception) {
        Quadruple(
            "",
            "",
            "",
            0L
        )
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
