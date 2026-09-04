package com.minios.elizierdias.apps.media

import android.content.ContentUris
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private enum class MediaFilter {
    ALL,
    AUDIO,
    VIDEO
}

private enum class PerformanceMode {
    PERFORMANCE,
    BALANCED,
    BATTERY_SAVER
}

private data class MediaEntry(
    val id: Long,
    val uri: Uri,
    val title: String,
    val displayName: String,
    val mimeType: String?,
    val duration: Long,
    val size: Long,
    val isVideo: Boolean,
)

private const val PREFS_NAME = "media_player_os"

private val AUDIO_PROJECTION = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.Media.DISPLAY_NAME,
    MediaStore.Audio.Media.TITLE,
    MediaStore.Audio.Media.MIME_TYPE,
    MediaStore.Audio.Media.DURATION,
    MediaStore.Audio.Media.SIZE,
)

private val VIDEO_PROJECTION = arrayOf(
    MediaStore.Video.Media._ID,
    MediaStore.Video.Media.DISPLAY_NAME,
    MediaStore.Video.Media.TITLE,
    MediaStore.Video.Media.MIME_TYPE,
    MediaStore.Video.Media.DURATION,
    MediaStore.Video.Media.SIZE,
)

private fun queryAudio(context: Context): List<MediaEntry> {
    val result = mutableListOf<MediaEntry>()

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

    val sortOrder =
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    try {
        context.contentResolver.query(
            collection,
            AUDIO_PROJECTION,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            val titleColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

            val mimeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            val durationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            val sizeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                val displayName =
                    cursor.getString(nameColumn)
                        ?: "Unknown audio"

                val title =
                    cursor.getString(titleColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: displayName

                val mimeType =
                    cursor.getString(mimeColumn)

                val duration =
                    cursor.getLong(durationColumn)

                val size =
                    cursor.getLong(sizeColumn)

                val uri =
                    ContentUris.withAppendedId(
                        collection,
                        id,
                    )

                result += MediaEntry(
                    id = id,
                    uri = uri,
                    title = title,
                    displayName = displayName,
                    mimeType = mimeType,
                    duration = duration,
                    size = size,
                    isVideo = false,
                )
            }
        }
    } catch (_: SecurityException) {
        // Permission not granted.
    } catch (_: Exception) {
        // MediaStore may fail for an unavailable provider.
    }

    return result
}

private fun queryVideo(context: Context): List<MediaEntry> {
    val result = mutableListOf<MediaEntry>()

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val sortOrder =
        "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"

    try {
        context.contentResolver.query(
            collection,
            VIDEO_PROJECTION,
            null,
            null,
            sortOrder,
        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)

            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            val titleColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)

            val mimeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            val durationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            val sizeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                val displayName =
                    cursor.getString(nameColumn)
                        ?: "Unknown video"

                val title =
                    cursor.getString(titleColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: displayName

                val mimeType =
                    cursor.getString(mimeColumn)

                val duration =
                    cursor.getLong(durationColumn)

                val size =
                    cursor.getLong(sizeColumn)

                val uri =
                    ContentUris.withAppendedId(
                        collection,
                        id,
                    )

                result += MediaEntry(
                    id = id,
                    uri = uri,
                    title = title,
                    displayName = displayName,
                    mimeType = mimeType,
                    duration = duration,
                    size = size,
                    isVideo = true,
                )
            }
        }
    } catch (_: SecurityException) {
        // Permission not granted.
    } catch (_: Exception) {
        // MediaStore may fail for an unavailable provider.
    }

    return result
}

private fun loadMediaLibrary(
    context: Context,
): List<MediaEntry> {
    val audio = queryAudio(context)
    val video = queryVideo(context)

    return (audio + video)
        .sortedWith(
            compareBy(
                { it.isVideo },
                { it.title.lowercase() },
            )
        )
}

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) {
        return "00:00"
    }

    val totalSeconds =
        milliseconds / 1000L

    val hours =
        totalSeconds / 3600L

    val minutes =
        (totalSeconds % 3600L) / 60L

    val seconds =
        totalSeconds % 60L

    return if (hours > 0L) {
        "%02d:%02d:%02d".format(
            hours,
            minutes,
            seconds,
        )
    } else {
        "%02d:%02d".format(
            minutes,
            seconds,
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) {
        return "0 B"
    }

    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        bytes >= gb ->
            "%.2f GB".format(bytes / gb)

        bytes >= mb ->
            "%.2f MB".format(bytes / mb)

        bytes >= kb ->
            "%.2f KB".format(bytes / kb)

        else ->
            "$bytes B"
    }
}

@Composable
fun MediaPlayerOS() {
    val context = LocalContext.current

    var library by remember {
        mutableStateOf<List<MediaEntry>>(emptyList())
    }

    var filter by remember {
        mutableStateOf(MediaFilter.ALL)
    }

    var selectedIndex by remember {
        mutableIntStateOf(-1)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var currentPosition by remember {
        mutableLongStateOf(0L)
    }

    var currentDuration by remember {
        mutableLongStateOf(0L)
    }

    var volume by remember {
        mutableFloatStateOf(1f)
    }

    var performanceMode by remember {
        mutableStateOf(PerformanceMode.BALANCED)
    }

    var videoPlaying by remember {
        mutableStateOf(false)
    }

    val audioManager =
        remember {
            context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager
        }

    val mediaPlayer =
        remember {
            MediaPlayer()
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

    fun playAudio(entry: MediaEntry) {
        if (entry.isVideo) {
            return
        }

        try {
            mediaPlayer.reset()

            mediaPlayer.setAudioStreamType(
                AudioManager.STREAM_MUSIC
            )

            mediaPlayer.setDataSource(
                context,
                entry.uri,
            )

            mediaPlayer.setVolume(
                volume,
                volume,
            )

            mediaPlayer.setOnPreparedListener {
                currentDuration =
                    it.duration.toLong()

                currentPosition = 0L

                it.start()

                isPlaying = true
            }

            mediaPlayer.setOnCompletionListener {
                isPlaying = false

                if (selectedIndex >= 0) {
                    val next =
                        selectedIndex + 1

                    if (
                        next < library.size &&
                        !library[next].isVideo
                    ) {
                        selectedIndex = next
                    }
                }
            }

            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun stopAudio() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            }
        } catch (_: Exception) {
        }

        isPlaying = false
    }

    fun toggleAudio() {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else if (selectedIndex >= 0) {
                mediaPlayer.start()
                isPlaying = true
            }
        } catch (_: Exception) {
        }
    }

    fun previousAudio() {
        val current =
            library.getOrNull(selectedIndex)

        if (current == null) {
            return
        }

        val index =
            library
                .indexOfFirst {
                    !it.isVideo &&
                        it.id == current.id
                }

        val previous =
            (index - 1 downTo 0)
                .firstOrNull {
                    !library[it].isVideo
                }

        if (previous != null) {
            selectedIndex = previous
            playAudio(library[previous])
        }
    }

    fun nextAudio() {
        val current =
            library.getOrNull(selectedIndex)

        if (current == null) {
            return
        }

        val index =
            library
                .indexOfFirst {
                    !it.isVideo &&
                        it.id == current.id
                }

        val next =
            (index + 1 until library.size)
                .firstOrNull {
                    !library[it].isVideo
                }

        if (next != null) {
            selectedIndex = next
            playAudio(library[next])
        }
    }

    LaunchedEffect(Unit) {
        library =
            loadMediaLibrary(context)
    }

    LaunchedEffect(
        selectedIndex,
        library,
    ) {
        val entry =
            library.getOrNull(selectedIndex)

        if (entry != null && entry.isVideo) {
            videoPlaying = true
            stopAudio()
        } else {
            videoPlaying = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPosition =
                    mediaPlayer.currentPosition.toLong()
            } catch (_: Exception) {
            }

            delay(
                when (performanceMode) {
                    PerformanceMode.PERFORMANCE -> 250L
                    PerformanceMode.BALANCED -> 500L
                    PerformanceMode.BATTERY_SAVER -> 1000L
                }
            )
        }
    }

    val visibleLibrary =
        when (filter) {
            MediaFilter.ALL ->
                library

            MediaFilter.AUDIO ->
                library.filter { !it.isVideo }

            MediaFilter.VIDEO ->
                library.filter { it.isVideo }
        }

    val selected =
        library.getOrNull(selectedIndex)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF101114),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier.weight(1f),
                ) {
                    Text(
                        text = "MediaPlayerOS",
                        style =
                            MaterialTheme.typography
                                .headlineSmall,
                    )

                    Text(
                        text =
                            "${library.size} arquivos encontrados automaticamente",
                        style =
                            MaterialTheme.typography
                                .bodySmall,
                    )
                }

                IconButton(
                    onClick = {
                        library =
                            loadMediaLibrary(context)

                        selectedIndex = -1
                        stopAudio()
                    },
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Refresh,
                        contentDescription =
                            "Atualizar biblioteca",
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = {
                        filter = MediaFilter.ALL
                    },
                ) {
                    Text("Todos")
                }

                OutlinedButton(
                    onClick = {
                        filter = MediaFilter.AUDIO
                    },
                ) {
                    Text("Música")
                }

                OutlinedButton(
                    onClick = {
                        filter = MediaFilter.VIDEO
                    },
                ) {
                    Text("Vídeos")
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp),
            ) {
                PerformanceMode.entries.forEach { mode ->
                    OutlinedButton(
                        onClick = {
                            performanceMode = mode
                        },
                    ) {
                        Text(
                            when (mode) {
                                PerformanceMode.PERFORMANCE ->
                                    "Performance"

                                PerformanceMode.BALANCED ->
                                    "Equilibrado"

                                PerformanceMode.BATTERY_SAVER ->
                                    "Bateria"
                            }
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(0.45f)
                            .fillMaxSize(),
                ) {
                    items(
                        items = visibleLibrary,
                        key = {
                            "${it.id}_${it.isVideo}"
                        },
                    ) { entry ->

                        val realIndex =
                            library.indexOfFirst {
                                it.id == entry.id &&
                                    it.isVideo == entry.isVideo
                            }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(
                                        RoundedCornerShape(
                                            8.dp
                                        )
                                    )
                                    .clickable {
                                        selectedIndex =
                                            realIndex

                                        if (entry.isVideo) {
                                            stopAudio()
                                        } else {
                                            playAudio(entry)
                                        }
                                    }
                                    .padding(10.dp),
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector =
                                    if (entry.isVideo) {
                                        Icons.Filled.VideoLibrary
                                    } else {
                                        Icons.Filled.Folder
                                    },
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(30.dp),
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(10.dp)
                            )

                            Column(
                                modifier =
                                    Modifier.weight(1f),
                            ) {
                                Text(
                                    text = entry.title,
                                    maxLines = 1,
                                )

                                Text(
                                    text =
                                        "${entry.mimeType ?: "media"} • ${formatSize(entry.size)} • ${formatDuration(entry.duration)}",
                                    style =
                                        MaterialTheme.typography
                                            .bodySmall,
                                    maxLines = 1,
                                )
                            }

                            if (
                                realIndex ==
                                selectedIndex
                            ) {
                                Icon(
                                    imageVector =
                                        if (isPlaying) {
                                            Icons.Filled.Pause
                                        } else {
                                            Icons.Filled.PlayArrow
                                        },
                                    contentDescription =
                                        null,
                                )
                            }
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.width(10.dp)
                )

                Column(
                    modifier =
                        Modifier
                            .weight(0.55f)
                            .fillMaxSize()
                            .clip(
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                Color(0xFF181A1F)
                            )
                            .padding(12.dp),
                ) {
                    if (
                        selected != null &&
                        selected.isVideo
                    ) {
                        AndroidView(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    layoutParams =
                                        ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                }
                            },
                            update = { videoView ->
                                if (
                                    selected.uri !=
                                    videoView.tag
                                ) {
                                    videoView.tag =
                                        selected.uri

                                    videoView.setVideoURI(
                                        selected.uri
                                    )

                                    videoView.setOnPreparedListener {
                                        it.isLooping = false
                                    }

                                    videoView.start()
                                }
                            },
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Text(
                            text = selected.title,
                            maxLines = 1,
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(
                                        rememberScrollState()
                                    ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.Center,
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled.PlayArrow,
                                contentDescription =
                                    null,
                                modifier =
                                    Modifier.size(72.dp),
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(12.dp)
                            )

                            Text(
                                text =
                                    selected?.title
                                        ?: "Nenhuma mídia selecionada",
                                style =
                                    MaterialTheme.typography
                                        .titleMedium,
                            )

                            if (selected != null) {
                                Spacer(
                                    modifier =
                                        Modifier.height(4.dp)
                                )

                                Text(
                                    text =
                                        selected.displayName,
                                    style =
                                        MaterialTheme.typography
                                            .bodySmall,
                                )
                            }
                        }
                    }

                    if (
                        selected != null &&
                        !selected.isVideo
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (
                                    currentDuration > 0L
                                ) {
                                    (
                                        currentPosition
                                            .toFloat() /
                                            currentDuration
                                    ).coerceIn(
                                        0f,
                                        1f,
                                    )
                                } else {
                                    0f
                                }
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                "${formatDuration(currentPosition)} / ${formatDuration(currentDuration)}",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.Center,
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    previousAudio()
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Filled.SkipPrevious,
                                    contentDescription =
                                        "Anterior",
                                )
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val position =
                                            (
                                                mediaPlayer
                                                    .currentPosition -
                                                    10000
                                            ).coerceAtLeast(0)

                                        mediaPlayer.seekTo(
                                            position
                                        )

                                        currentPosition =
                                            position.toLong()
                                    } catch (_: Exception) {
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Filled.FastRewind,
                                    contentDescription =
                                        "Retroceder",
                                )
                            }

                            IconButton(
                                onClick = {
                                    toggleAudio()
                                },
                                modifier =
                                    Modifier.size(64.dp),
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
                                    modifier =
                                        Modifier.size(40.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val position =
                                            (
                                                mediaPlayer
                                                    .currentPosition +
                                                    10000
                                            ).coerceAtMost(
                                                mediaPlayer.duration
                                            )

                                        mediaPlayer.seekTo(
                                            position
                                        )

                                        currentPosition =
                                            position.toLong()
                                    } catch (_: Exception) {
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Filled.FastForward,
                                    contentDescription =
                                        "Avançar",
                                )
                            }

                            IconButton(
                                onClick = {
                                    nextAudio()
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Filled.SkipNext,
                                    contentDescription =
                                        "Próximo",
                                )
                            }
                        }

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.Center,
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    volume =
                                        (
                                            volume - 0.1f
                                        ).coerceAtLeast(0f)

                                    try {
                                        mediaPlayer.setVolume(
                                            volume,
                                            volume,
                                        )
                                    } catch (_: Exception) {
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Filled.VolumeDown,
                                    contentDescription =
                                        "Diminuir volume",
                                )
                            }

                            Text(
                                text =
                                    "${(volume * 100).toInt()}%",
                            )

                            IconButton(
                                onClick = {
                                    volume =
                                        (
                                            volume + 0.1f
                                        ).coerceAtMost(1f)

                                    try {
                                        mediaPlayer.setVolume(
                                            volume,
                                            volume,
                                        )
                                    } catch (_: Exception) {
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Filled.VolumeUp,
                                    contentDescription =
                                        "Aumentar volume",
                                )
                            }
                        }
                    } else if (
                        selected == null
                    ) {
                        Text(
                            text =
                                "O MediaPlayerOS procura automaticamente as músicas e vídeos disponíveis no Android.",
                            style =
                                MaterialTheme.typography
                                    .bodySmall,
                        )
                    }
                }
            }
        }
    }
}
