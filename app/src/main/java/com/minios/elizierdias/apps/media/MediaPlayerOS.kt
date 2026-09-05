package com.minios.elizierdias.apps.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/* ── VLC dark palette (como nas fotos) ─────────────────────────────── */
private val Bg = Color(0xFF1A1A1D)
private val Sidebar = Color(0xFF201F23)
private val Panel = Color(0xFF242327)
private val PanelAlt = Color(0xFF2D2C31)
private val Border = Color(0xFF39383D)
private val Accent = Color(0xFFFF8A00)
private val AccentDim = Color(0xFFB35F00)
private val TextPrimary = Color(0xFFF2F2F2)
private val TextSecondary = Color(0xFF9C9BA1)
private val SelectedBg = Color(0xFF34333A)
private val PlaylistSel = Color(0xFFFF8A00)

private enum class MediaFilter { ALL, AUDIO, VIDEO }

private data class MediaEntry(
    val id: Long,
    val uri: Uri,
    val title: String,
    val displayName: String,
    val artist: String?,
    val mimeType: String?,
    val duration: Long,
    val size: Long,
    val isVideo: Boolean,
)

private val AUDIO_PROJECTION = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.Media.DISPLAY_NAME,
    MediaStore.Audio.Media.TITLE,
    MediaStore.Audio.Media.ARTIST,
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
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Áudio"
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: displayName
                val artist = cursor.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                result += MediaEntry(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    displayName = displayName,
                    artist = artist,
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
                    artist = null,
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

/* ── Miniatura real (capa de álbum / frame de vídeo) com fallback de ícone ── */
@Composable
private fun MediaThumbnail(
    entry: MediaEntry,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    val context = LocalContext.current
    var bitmap by remember(entry.id, entry.isVideo) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(entry.id, entry.isVideo) {
        bitmap = null
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(entry.uri, Size(320, 320), null)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                if (entry.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
                null,
                tint = if (entry.isVideo) Color(0xFF5B9BD5) else Accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
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
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableFloatStateOf(0f) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

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

    fun stopAudio() {
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
        } catch (_: Exception) {
        }
        isPlaying = false
    }

    fun pickNextIndex(fromIndex: Int, isVideo: Boolean): Int? {
        val candidates = library.indices.filter { library[it].isVideo == isVideo }
        if (candidates.isEmpty()) return null
        if (isShuffle) {
            return candidates.filter { it != fromIndex }.randomOrNull() ?: candidates.firstOrNull()
        }
        val pos = candidates.indexOf(fromIndex)
        val nextPos = if (pos == -1) 0 else pos + 1
        return candidates.getOrNull(nextPos)
    }

    fun pickPreviousIndex(fromIndex: Int, isVideo: Boolean): Int? {
        val candidates = library.indices.filter { library[it].isVideo == isVideo }
        if (candidates.isEmpty()) return null
        if (isShuffle) {
            return candidates.filter { it != fromIndex }.randomOrNull() ?: candidates.firstOrNull()
        }
        val pos = candidates.indexOf(fromIndex)
        val prevPos = if (pos <= 0) -1 else pos - 1
        return candidates.getOrNull(prevPos)
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
                if (isRepeat) {
                    try {
                        it.seekTo(0)
                        it.start()
                        isPlaying = true
                    } catch (_: Exception) {
                        isPlaying = false
                    }
                    return@setOnCompletionListener
                }
                isPlaying = false
                if (selectedIndex >= 0) {
                    val next = pickNextIndex(selectedIndex, isVideo = false)
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
        val prev = pickPreviousIndex(selectedIndex, isVideo = false) ?: return
        selectedIndex = prev
        playAudio(library[prev])
    }

    fun nextAudio() {
        val next = pickNextIndex(selectedIndex, isVideo = false) ?: return
        selectedIndex = next
        playAudio(library[next])
    }

    fun seekBy(deltaMs: Int) {
        try {
            val selectedIsVideo = library.getOrNull(selectedIndex)?.isVideo == true
            if (selectedIsVideo) {
                val vv = videoViewRef ?: return
                val pos = (vv.currentPosition + deltaMs).coerceIn(0, vv.duration.coerceAtLeast(0))
                vv.seekTo(pos)
                currentPosition = pos.toLong()
            } else {
                val pos = (mediaPlayer.currentPosition + deltaMs).coerceIn(0, mediaPlayer.duration.coerceAtLeast(0))
                mediaPlayer.seekTo(pos)
                currentPosition = pos.toLong()
            }
        } catch (_: Exception) {
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            val selectedIsVideo = library.getOrNull(selectedIndex)?.isVideo == true
            if (selectedIsVideo) {
                videoViewRef?.seekTo(positionMs.toInt())
            } else {
                mediaPlayer.seekTo(positionMs.toInt())
            }
            currentPosition = positionMs
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

    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            try {
                val selectedIsVideo = library.getOrNull(selectedIndex)?.isVideo == true
                currentPosition = if (selectedIsVideo) {
                    videoViewRef?.currentPosition?.toLong() ?: currentPosition
                } else {
                    mediaPlayer.currentPosition.toLong()
                }
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
                it.displayName.lowercase().contains(q) ||
                (it.artist?.lowercase()?.contains(q) == true)
        }
        .toList()

    val selected = library.getOrNull(selectedIndex)
    val effectiveDuration = if (selected != null && selected.duration > 0) selected.duration else currentDuration
    val progress = if (isUserSeeking) {
        seekPreview
    } else if (effectiveDuration > 0L) {
        (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val previewPositionMs = (progress * effectiveDuration).toLong()

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
                    .width(140.dp)
                    .fillMaxHeight()
                    .background(Sidebar)
                    .padding(vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "MediaPlayerOS",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "BIBLIOTECA",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )

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
                    label = "Tudo",
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
                /* Barra de pesquisa */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PanelAlt)
                        .border(1.dp, Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
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
                                Text("Procurar músicas, vídeos, artistas…", color = TextSecondary, fontSize = 12.sp)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = when (filter) {
                            MediaFilter.AUDIO -> "Músicas"
                            MediaFilter.VIDEO -> "Vídeos"
                            MediaFilter.ALL -> "Biblioteca"
                        },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${visibleLibrary.size} itens",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(4.dp))

                /* Conteúdo: grelha de capas OU vídeo em reprodução */
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
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
                                    setOnCompletionListener {
                                        if (isRepeat) {
                                            seekTo(0)
                                            start()
                                            isPlaying = true
                                        } else {
                                            isPlaying = false
                                            val next = pickNextIndex(selectedIndex, isVideo = true)
                                            if (next != null) {
                                                selectedIndex = next
                                            }
                                        }
                                    }
                                    setVideoURI(selected.uri)
                                    videoViewRef = this
                                    start()
                                }
                            },
                            update = { /* recriado via key no LazyColumn/parent quando muda o vídeo */ },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                        )
                    } else if (filter == MediaFilter.AUDIO || filter == MediaFilter.ALL) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 118.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(visibleLibrary, key = { "${it.id}_${it.isVideo}" }) { entry ->
                                val realIndex = library.indexOfFirst {
                                    it.id == entry.id && it.isVideo == entry.isVideo
                                }
                                val isSelected = realIndex == selectedIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) SelectedBg else Color.Transparent)
                                        .clickable {
                                            selectedIndex = realIndex
                                            if (entry.isVideo) stopAudio() else playAudio(entry)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(PanelAlt),
                                    ) {
                                        MediaThumbnail(entry = entry, modifier = Modifier.fillMaxSize(), iconSize = 18.dp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
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

                    if (visibleLibrary.isEmpty() && (selected == null || !selected.isVideo)) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                null,
                                tint = TextSecondary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (q.isNotEmpty()) "Sem resultados para \"$searchQuery\"" else "Nada encontrado nesta biblioteca",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            /* Playlist direita (estilo VLC) */
            if (showPlaylist) {
                Column(
                    modifier = Modifier
                        .width(176.dp)
                        .fillMaxHeight()
                        .background(Panel)
                        .border(width = 1.dp, color = Border),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PanelAlt)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                    .background(
                                        if (isSelected) PlaylistSel.copy(alpha = 0.18f) else Color.Transparent,
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) PlaylistSel else Color.Transparent,
                                    )
                                    .clickable {
                                        selectedIndex = realIndex
                                        if (entry.isVideo) stopAudio() else playAudio(entry)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PanelAlt),
                                ) {
                                    MediaThumbnail(entry = entry, modifier = Modifier.fillMaxSize(), iconSize = 14.dp)
                                }
                                Spacer(Modifier.width(7.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        color = if (isSelected) Accent else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        entry.artist ?: formatDuration(entry.duration),
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isSelected && isPlaying) {
                                    Icon(
                                        Icons.Filled.Pause,
                                        null,
                                        tint = Accent,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        
