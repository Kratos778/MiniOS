package com.minios.elizierdias.apps.media

import android.content.ContentUris
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/* ── VLC dark palette (como nas fotos) ─────────────────────────────── */
private val Bg = Color(0xFF1E1E1E)
private val Sidebar = Color(0xFF252525)
private val Panel = Color(0xFF2A2A2A)
private val PanelAlt = Color(0xFF323232)
private val Border = Color(0xFF3C3C3C)
private val Accent = Color(0xFFFF8800)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFF9B9B9B)
private val SelectedBg = Color(0xFF3A3A3A)
private val PlaylistSel = Color(0xFF3D6EA5)

private enum class MediaFilter { ALL, AUDIO, VIDEO }

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
    try {
        context.contentResolver.query(
            collection,
            AUDIO_PROJECTION,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Áudio"
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: displayName
                result += MediaEntry(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    displayName = displayName,
                    mimeType = cursor.getString(mimeCol),
                    duration = cursor.getLong(durCol),
                    size = cursor.getLong(sizeCol),
                    isVideo = false,
                )
            }
        }
    } catch (_: Exception) {
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
    try {
        context.contentResolver.query(
            collection,
            VIDEO_PROJECTION,
            null,
            null,
            "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Vídeo"
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: displayName
                result += MediaEntry(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    displayName = displayName,
                    mimeType = cursor.getString(mimeCol),
                    duration = cursor.getLong(durCol),
                    size = cursor.getLong(sizeCol),
                    isVideo = true,
                )
            }
        }
    } catch (_: Exception) {
    }
    return result
}

private fun loadMediaLibrary(context: Context): List<MediaEntry> {
    return (queryAudio(context) + queryVideo(context)).sortedWith(
        compareBy({ it.isVideo }, { it.title.lowercase() }),
    )
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun MediaPlayerOS() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var library by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var filter by remember { mutableStateOf(MediaFilter.AUDIO) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var currentDuration by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var showPlaylist by remember { mutableStateOf(true) }

    val mediaPlayer = remember { MediaPlayer() }

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
        if (entry.isVideo) return
        try {
            mediaPlayer.reset()
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mediaPlayer.setDataSource(context, entry.uri)
            mediaPlayer.setVolume(volume, volume)
            mediaPlayer.setOnPreparedListener {
                currentDuration = it.duration.toLong()
                currentPosition = 0L
                it.start()
                isPlaying = true
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                if (selectedIndex >= 0) {
                    val next = (selectedIndex + 1 until library.size).firstOrNull { !library[it].isVideo }
                    if (next != null) {
                        selectedIndex = next
                        playAudio(library[next])
                    }
                }
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                isPlaying = false
                true
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            isPlaying = false
        }
    }

    fun stopAudio() {
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
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
                val entry = library.getOrNull(selectedIndex)
                if (entry != null && !entry.isVideo) {
                    try {
                        if (mediaPlayer.currentPosition > 0) {
                            mediaPlayer.start()
                            isPlaying = true
                        } else {
                            playAudio(entry)
                        }
                    } catch (_: Exception) {
                        playAudio(entry)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun previousAudio() {
        val current = library.getOrNull(selectedIndex) ?: return
        val index = library.indexOfFirst { !it.isVideo && it.id == current.id }
        val prev = (index - 1 downTo 0).firstOrNull { !library[it].isVideo }
        if (prev != null) {
            selectedIndex = prev
            playAudio(library[prev])
        }
    }

    fun nextAudio() {
        val current = library.getOrNull(selectedIndex) ?: return
        val index = library.indexOfFirst { !it.isVideo && it.id == current.id }
        val next = (index + 1 until library.size).firstOrNull { !library[it].isVideo }
        if (next != null) {
            selectedIndex = next
            playAudio(library[next])
        }
    }

    fun seekBy(deltaMs: Int) {
        try {
            val pos = (mediaPlayer.currentPosition + deltaMs).coerceIn(0, mediaPlayer.duration.coerceAtLeast(0))
            mediaPlayer.seekTo(pos)
            currentPosition = pos.toLong()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        library = loadMediaLibrary(context)
    }

    LaunchedEffect(selectedIndex, library) {
        val entry = library.getOrNull(selectedIndex)
        if (entry != null && entry.isVideo) stopAudio()
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPosition = mediaPlayer.currentPosition.toLong()
            } catch (_: Exception) {
            }
            delay(400L)
        }
    }

    val q = searchQuery.trim().lowercase()
    val visibleLibrary = library.asSequence()
        .filter {
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.AUDIO -> !it.isVideo
                MediaFilter.VIDEO -> it.isVideo
            }
        }
        .filter {
            q.isEmpty() ||
                it.title.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q)
        }
        .toList()

    val selected = library.getOrNull(selectedIndex)
    val progress = if (currentDuration > 0L) {
        (currentPosition.toFloat() / currentDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        /* ── Corpo principal: sidebar | conteúdo | playlist ─────────── */
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            /* Sidebar esquerda (estilo VLC) */
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .fillMaxHeight()
                    .background(Sidebar)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = "MediaPlayerOS",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )

                Spacer(Modifier.height(4.dp))

                SidebarItem(
                    label = "Música",
                    icon = Icons.Filled.MusicNote,
                    selected = filter == MediaFilter.AUDIO,
                    onClick = { filter = MediaFilter.AUDIO },
                )
                SidebarItem(
                    label = "Vídeos",
                    icon = Icons.Filled.VideoLibrary,
                    selected = filter == MediaFilter.VIDEO,
                    onClick = { filter = MediaFilter.VIDEO },
                )
                SidebarItem(
                    label = "Todos",
                    icon = Icons.Filled.PlaylistPlay,
                    selected = filter == MediaFilter.ALL,
                    onClick = { filter = MediaFilter.ALL },
                )

                Spacer(Modifier.weight(1f))

                SidebarItem(
                    label = "Atualizar",
                    icon = Icons.Filled.Refresh,
                    selected = false,
                    onClick = {
                        library = loadMediaLibrary(context)
                        selectedIndex = -1
                        stopAudio()
                    },
                )
            }

            /* Centro */
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Bg),
            ) {
                /* Barra de pesquisa compacta */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PanelAlt)
                        .border(1.dp, Border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Procurar…", color = TextSecondary, fontSize = 12.sp)
                            }
                            inner()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear,
                            null,
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" },
                        )
                    }
                }

                Text(
                    text = when (filter) {
                        MediaFilter.AUDIO -> "Songs"
                        MediaFilter.VIDEO -> "Videos"
                        MediaFilter.ALL -> "Library"
                    },
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )

                /* Conteúdo: grelha estilo álbum OU vídeo */
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    if (selected != null && selected.isVideo) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    setOnPreparedListener { mp ->
                                        currentDuration = mp.duration.toLong()
                                        mp.start()
                                        isPlaying = true
                                    }
                                    setOnCompletionListener { isPlaying = false }
                                    setVideoURI(selected.uri)
                                    start()
                                }
                            },
                            update = { vv ->
                                // se mudou de vídeo, o key no parent deve recriar
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black),
                        )
                    } else if (filter == MediaFilter.AUDIO || filter == MediaFilter.ALL) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(visibleLibrary.filter { !it.isVideo || filter == MediaFilter.ALL }, key = { "${it.id}_${it.isVideo}" }) { entry ->
                                val realIndex = library.indexOfFirst {
                                    it.id == entry.id && it.isVideo == entry.isVideo
                                }
                                val isSelected = realIndex == selectedIndex
                                AlbumCard(
                                    entry = entry,
                                    selected = isSelected,
                                    onClick = {
                                        selectedIndex = realIndex
                                        if (entry.isVideo) stopAudio() else playAudio(entry)
                                    },
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(visibleLibrary, key = { "${it.id}_${it.isVideo}" }) { entry ->
                                val realIndex = library.indexOfFirst {
                                    it.id == entry.id && it.isVideo == entry.isVideo
                                }
                                val isSelected = realIndex == selectedIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) SelectedBg else Color.Transparent)
                                        .clickable {
                                            selectedIndex = realIndex
                                            if (entry.isVideo) stopAudio() else playAudio(entry)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.VideoLibrary,
                                        null,
                                        tint = Color(0xFF5B9BD5),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier = Modifier.weight(1f)) {
                                        Text(
                                            entry.title,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            formatDuration(entry.duration),
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* Playlist direita (estilo VLC) */
            if (showPlaylist) {
                Column(
                    modifier = Modifier
                        .width(168.dp)
                        .fillMaxHeight()
                        .background(Panel)
                        .border(1.dp, Border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PanelAlt)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Playlist",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${visibleLibrary.size}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                        )
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(visibleLibrary, key = { _, e -> "${e.id}_${e.isVideo}" }) { _, entry ->
                            val realIndex = library.indexOfFirst {
                                it.id == entry.id && it.isVideo == entry.isVideo
                            }
                            val isSelected = realIndex == selectedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) PlaylistSel else Color.Transparent)
                                    .clickable {
                                        selectedIndex = realIndex
                                        if (entry.isVideo) stopAudio() else playAudio(entry)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (entry.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
                                    null,
                                    tint = if (isSelected) Color.White else if (entry.isVideo) Color(0xFF5B9BD5) else Accent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatDuration(entry.duration),
                                        color = if (isSelected) Color(0xFFCCDDFF) else TextSecondary,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        /* ── Barra de player inferior (estilo VLC) ──────────────────── */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel)
                .border(width = 1.dp, color = Border)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                /* Cover placeholder */
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PanelAlt),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (selected?.isVideo == true) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
                        null,
                        tint = Accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.title ?: "Nada a reproduzir",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = selected?.displayName ?: "—",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                /* Controlos */
                IconButton(onClick = { previousAudio() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { seekBy(-10_000) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.FastRewind, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = {
                        if (selected?.isVideo == true) {
                            // vídeo controlado pelo VideoView — toggle via isPlaying flag só visual
                            isPlaying = !isPlaying
                        } else {
                            toggleAudio()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        tint = Accent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = { seekBy(10_000) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { nextAudio() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.SkipNext, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        volume = (volume - 0.1f).coerceAtLeast(0f)
                        try {
                            mediaPlayer.setVolume(volume, volume)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Filled.VolumeDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                Text(
                    "${(volume * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.width(28.dp),
                )
                IconButton(
                    onClick = {
                        volume = (volume + 0.1f).coerceAtMost(1f)
                        try {
                            mediaPlayer.setVolume(volume, volume)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Filled.VolumeUp, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { showPlaylist = !showPlaylist },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Filled.PlaylistPlay, null, tint = if (showPlaylist) Accent else TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(formatDuration(currentPosition), color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(40.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Accent,
                    trackColor = Border,
                )
                Text(
                    formatDuration(if (selected != null && selected.duration > 0) selected.duration else currentDuration),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .width(40.dp)
                        .padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) SelectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) Accent else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AlbumCard(
    entry: MediaEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) SelectedBg else Panel)
            .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(PanelAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (entry.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
                null,
                tint = if (entry.isVideo) Color(0xFF5B9BD5) else Accent,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.title,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatDuration(entry.duration),
            color = TextSecondary,
            fontSize = 9.sp,
        )
    }
}
