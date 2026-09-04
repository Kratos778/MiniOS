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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/* ── VLC-like dark palette ─────────────────────────────────────────── */
private val Bg = Color(0xFF1A1A1A)
private val Panel = Color(0xFF242424)
private val PanelAlt = Color(0xFF2A2A2A)
private val Border = Color(0xFF3A3A3A)
private val Accent = Color(0xFFFF6B00)
private val TextPrimary = Color(0xFFE8E8E8)
private val TextSecondary = Color(0xFF9A9A9A)
private val SelectedBg = Color(0xFF333333)

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
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var currentDuration by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }

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

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        Column(modifier = Modifier.fillMaxSize()) {

            /* ── Top bar ──────────────────────────────────────────── */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MediaPlayerOS",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${library.size} ficheiros",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        library = loadMediaLibrary(context)
                        selectedIndex = -1
                        stopAudio()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Filled.Refresh, "Atualizar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            /* ── Search ───────────────────────────────────────────── */
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(44.dp),
                singleLine = true,
                placeholder = {
                    Text("Procurar…", color = TextSecondary, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Clear, "Limpar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                    focusedContainerColor = PanelAlt,
                    unfocusedContainerColor = PanelAlt,
                ),
                shape = RoundedCornerShape(6.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            )

            /* ── Filters ──────────────────────────────────────────── */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    MediaFilter.ALL to "Todos",
                    MediaFilter.AUDIO to "Música",
                    MediaFilter.VIDEO to "Vídeos",
                ).forEach { (mode, label) ->
                    val active = filter == mode
                    TextButton(
                        onClick = { filter = mode },
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (active) Accent else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            /* ── Body: list + player ──────────────────────────────── */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                /* Playlist */
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Panel),
                ) {
                    Text(
                        text = if (q.isEmpty()) "${visibleLibrary.size} itens" else "${visibleLibrary.size} resultados",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )

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
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (entry.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = if (entry.isVideo) Color(0xFF5B9BD5) else Accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatDuration(entry.duration),
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                /* Player panel */
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Panel)
                        .padding(10.dp),
                ) {
                    if (selected != null && selected.isVideo) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp)),
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                }
                            },
                            update = { vv ->
                                if (selected.uri != vv.tag) {
                                    vv.tag = selected.uri
                                    vv.setVideoURI(selected.uri)
                                    vv.setOnPreparedListener { it.isLooping = false }
                                    vv.start()
                                }
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = selected.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    null,
                                    tint = Accent.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = selected?.title ?: "Nenhuma mídia selecionada",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (selected != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = formatDuration(selected.duration),
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                } else {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Pesquisa automática · escreve o nome acima",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }

                    /* Controls (audio) */
                    if (selected != null && !selected.isVideo) {
                        Spacer(Modifier.height(6.dp))

                        val progress = if (currentDuration > 0L) {
                            (currentPosition.toFloat() / currentDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Accent,
                            trackColor = Border,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(formatDuration(currentPosition), color = TextSecondary, fontSize = 10.sp)
                            Text(formatDuration(currentDuration), color = TextSecondary, fontSize = 10.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { previousAudio() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.SkipPrevious, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { seekBy(-10_000) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.FastRewind, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { toggleAudio() }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    null,
                                    tint = Accent,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            IconButton(onClick = { seekBy(10_000) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.FastForward, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { nextAudio() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.SkipNext, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    volume = (volume - 0.1f).coerceAtLeast(0f)
                                    try {
                                        mediaPlayer.setVolume(volume, volume)
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(Icons.Filled.VolumeDown, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${(volume * 100).toInt()}%",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.width(36.dp),
                            )
                            IconButton(
                                onClick = {
                                    volume = (volume + 0.1f).coerceAtMost(1f)
                                    try {
                                        mediaPlayer.setVolume(volume, volume)
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(Icons.Filled.VolumeUp, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
