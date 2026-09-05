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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF1A1A1D)
private val Sidebar = Color(0xFF201F23)
private val Panel = Color(0xFF242327)
private val PanelAlt = Color(0xFF2D2C31)
private val Border = Color(0xFF39383D)
private val Accent = Color(0xFFFF8A00)
private val TextPri = Color(0xFFF2F2F2)
private val TextSec = Color(0xFF9C9BA1)
private val Sel = Color(0xFF34333A)

private enum class MediaFilter { ALL, AUDIO, VIDEO }

private data class MediaEntry(
    val id: Long,
    val uri: Uri,
    val title: String,
    val displayName: String,
    val artist: String?,
    val duration: Long,
    val isVideo: Boolean,
)

private fun queryAudio(ctx: Context): List<MediaEntry> {
    val out = mutableListOf<MediaEntry>()
    val col = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
    )
    try {
        ctx.contentResolver.query(
            col, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val name = c.getString(nameC) ?: "Audio"
                val title = c.getString(titleC)?.takeIf { it.isNotBlank() } ?: name
                val artist = c.getString(artC)?.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) }
                out += MediaEntry(id, ContentUris.withAppendedId(col, id), title, name, artist, c.getLong(durC), false)
            }
        }
    } catch (_: Exception) {}
    return out
}

private fun queryVideo(ctx: Context): List<MediaEntry> {
    val out = mutableListOf<MediaEntry>()
    val col = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DURATION,
    )
    try {
        ctx.contentResolver.query(
            col, proj, null, null,
            "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val name = c.getString(nameC) ?: "Video"
                val title = c.getString(titleC)?.takeIf { it.isNotBlank() } ?: name
                out += MediaEntry(id, ContentUris.withAppendedId(col, id), title, name, null, c.getLong(durC), true)
            }
        }
    } catch (_: Exception) {}
    return out
}

private fun loadLibrary(ctx: Context) =
    (queryAudio(ctx) + queryVideo(ctx)).sortedWith(compareBy({ it.isVideo }, { it.title.lowercase() }))

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Thumb(entry: MediaEntry, mod: Modifier = Modifier, icon: Int = 22) {
    val ctx = LocalContext.current
    var bmp by remember(entry.id, entry.isVideo) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.id, entry.isVideo) {
        bmp = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29)
                    ctx.contentResolver.loadThumbnail(entry.uri, Size(160, 160), null)
                else null
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(mod.background(PanelAlt), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                if (entry.isVideo) Icons.Filled.VideoLibrary else Icons.Filled.MusicNote,
                null,
                tint = if (entry.isVideo) Color(0xFF5B9BD5) else Accent,
                modifier = Modifier.size(icon.dp),
            )
        }
    }
}

@Composable
fun MediaPlayerOS() {
    val ctx = LocalContext.current
    val kb = LocalSoftwareKeyboardController.current
    var library by remember { mutableStateOf(emptyList<MediaEntry>()) }
    var filter by remember { mutableStateOf(MediaFilter.AUDIO) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var showPl by remember { mutableStateOf(true) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekPrev by remember { mutableFloatStateOf(0f) }
    var vvRef by remember { mutableStateOf<VideoView?>(null) }
    var vvMp by remember { mutableStateOf<MediaPlayer?>(null) }
    val mp = remember { MediaPlayer() }
    val listState = rememberLazyListState()
    val plState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            try {
                mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
            try {
                vvRef?.stopPlayback()
            } catch (_: Exception) {
            }
        }
    }

    fun stopAudio() {
        try {
            if (mp.isPlaying) mp.pause()
        } catch (_: Exception) {
        }
        playing = false
    }

    fun candidates(video: Boolean) = library.indices.filter { library[it].isVideo == video }

    fun nextIdx(from: Int, video: Boolean): Int? {
        val c = candidates(video)
        if (c.isEmpty()) return null
        if (shuffle) return c.filter { it != from }.randomOrNull() ?: c.firstOrNull()
        val i = c.indexOf(from)
        return c.getOrNull(if (i < 0) 0 else i + 1)
    }

    fun prevIdx(from: Int, video: Boolean): Int? {
        val c = candidates(video)
        if (c.isEmpty()) return null
        if (shuffle) return c.filter { it != from }.randomOrNull() ?: c.firstOrNull()
        val i = c.indexOf(from)
        return c.getOrNull(if (i <= 0) -1 else i - 1)
    }

    fun playAudio(e: MediaEntry) {
        if (e.isVideo) return
        try {
            mp.reset()
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
            mp.setDataSource(ctx, e.uri)
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener {
                dur = it.duration.toLong()
                pos = 0
                it.start()
                playing = true
            }
            mp.setOnCompletionListener {
                if (repeat) {
                    try {
                        it.seekTo(0)
                        it.start()
                        playing = true
                    } catch (_: Exception) {
                        playing = false
                    }
                    return@setOnCompletionListener
                }
                playing = false
                nextIdx(selected, false)?.let {
                    selected = it
                    playAudio(library[it])
                }
            }
            mp.setOnErrorListener { _, _, _ ->
                playing = false
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            playing = false
        }
    }

    fun playAt(i: Int) {
        val e = library.getOrNull(i) ?: return
        selected = i
        if (e.isVideo) {
            stopAudio()
            vvRef?.setVideoURI(e.uri)
            vvRef?.start()
            playing = true
        } else {
            try {
                vvRef?.stopPlayback()
            } catch (_: Exception) {
            }
            playAudio(e)
        }
    }

    fun toggle() {
        val e = library.getOrNull(selected)
        if (e?.isVideo == true) {
            val v = vvRef ?: return
            if (v.isPlaying) {
                v.pause()
                playing = false
            } else {
                v.start()
                playing = true
            }
        } else {
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    playing = false
                } else if (e != null && !e.isVideo) {
                    try {
                        if (mp.currentPosition > 0) {
                            mp.start()
                            playing = true
                        } else playAudio(e)
                    } catch (_: Exception) {
                        playAudio(e)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun seekBy(d: Int) {
        try {
            if (library.getOrNull(selected)?.isVideo == true) {
                val v = vvRef ?: return
                val p = (v.currentPosition + d).coerceIn(0, v.duration.coerceAtLeast(0))
                v.seekTo(p)
                pos = p.toLong()
            } else {
                val p = (mp.currentPosition + d).coerceIn(0, mp.duration.coerceAtLeast(0))
                mp.seekTo(p)
                pos = p.toLong()
            }
        } catch (_: Exception) {
        }
    }

    fun seekTo(ms: Long) {
        try {
            if (library.getOrNull(selected)?.isVideo == true) vvRef?.seekTo(ms.toInt())
            else mp.seekTo(ms.toInt())
            pos = ms
        } catch (_: Exception) {
        }
    }

    fun setVol(v: Float) {
        volume = v.coerceIn(0f, 1f)
        try {
            mp.setVolume(volume, volume)
            vvMp?.setVolume(volume, volume)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        library = loadLibrary(ctx)
    }

    LaunchedEffect(playing, seeking) {
        while (playing && !seeking) {
            try {
                pos = if (library.getOrNull(selected)?.isVideo == true)
                    vvRef?.currentPosition?.toLong() ?: pos
                else mp.currentPosition.toLong()
            } catch (_: Exception) {
            }
            delay(400)
        }
    }

    val q = query.trim().lowercase()
    val visible = library.filter {
        when (filter) {
            MediaFilter.ALL -> true
            MediaFilter.AUDIO -> !it.isVideo
            MediaFilter.VIDEO -> it.isVideo
        }
    }.filter {
        q.isEmpty() || it.title.lowercase().contains(q) || it.displayName.lowercase().contains(q) ||
            (it.artist?.lowercase()?.contains(q) == true)
    }
    val cur = library.getOrNull(selected)
    val effDur = if (cur != null && cur.duration > 0) cur.duration else dur
    val progress = if (seeking) seekPrev else if (effDur > 0) (pos.toFloat() / effDur).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(Sidebar)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "MediaPlayerOS",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
                listOf(
                    Triple("Musica", MediaFilter.AUDIO, Icons.Filled.MusicNote),
                    Triple("Videos", MediaFilter.VIDEO, Icons.Filled.VideoLibrary),
                    Triple("Tudo", MediaFilter.ALL, Icons.Filled.PlaylistPlay),
                ).forEach { (label, f, icon) ->
                    val on = filter == f
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (on) Sel else Color.Transparent)
                            .clickable { filter = f }
                            .padding(10.dp, 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = if (on) Accent else TextSec, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = if (on) TextPri else TextSec, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            library = loadLibrary(ctx)
                            selected = -1
                            stopAudio()
                            try {
                                vvRef?.stopPlayback()
                            } catch (_: Exception) {
                            }
                        }
                        .padding(10.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = TextSec, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Atualizar", color = TextSec, fontSize = 12.sp)
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(PanelAlt)
                        .border(1.dp, Border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextSec, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPri, fontSize = 12.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Procurar...", color = TextSec, fontSize = 12.sp)
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear, null, tint = TextSec,
                            modifier = Modifier.size(14.dp).clickable { query = "" },
                        )
                    }
                }
                Text(
                    when (filter) {
                        MediaFilter.AUDIO -> "Musicas"
                        MediaFilter.VIDEO -> "Videos"
                        MediaFilter.ALL -> "Biblioteca"
                    } + " · ${visible.size}",
                    color = TextPri,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp)) {
                    if (cur != null && cur.isVideo) {
                        key(cur.uri) {
                            AndroidView(
                                factory = { c ->
                                    VideoView(c).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        setOnPreparedListener { p ->
                                            vvMp = p
                                            p.setVolume(volume, volume)
                                            dur = p.duration.toLong()
                                            pos = 0
                                            p.start()
                                            playing = true
                                        }
                                        setOnCompletionListener {
                                            if (repeat) {
                                                seekTo(0)
                                                start()
                                                playing = true
                                            } else {
                                                playing = false
                                                nextIdx(selected, true)?.let { playAt(it) }
                                            }
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            playing = false
                                            true
                                        }
                                        vvRef = this
                                        setVideoURI(cur.uri)
                                    }
                                },
                                update = { vvRef = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black),
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                                items(visible, key = { "${it.id}_${it.isVideo}" }) { e ->
                                    val idx = library.indexOfFirst { it.id == e.id && it.isVideo == e.isVideo }
                                    val on = idx == selected
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(if (on) Sel else Color.Transparent)
                                            .clickable { playAt(idx) }
                                            .padding(6.dp, 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Thumb(e, Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)), 16)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(e.title, color = TextPri, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(e.artist ?: fmt(e.duration), color = TextSec, fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            VerticalSb(listState)
                        }
                    }
                    if (visible.isEmpty() && (cur == null || !cur.isVideo)) {
                        Text(
                            if (q.isNotEmpty()) "Sem resultados" else "Biblioteca vazia",
                            color = TextSec,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            if (showPl) {
                Column(
                    Modifier
                        .width(160.dp)
                        .fillMaxHeight()
                        .background(Panel)
                        .border(1.dp, Border),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(PanelAlt).padding(8.dp, 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Playlist", color = TextPri, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${visible.size}", color = TextSec, fontSize = 10.sp)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(state = plState, modifier = Modifier.weight(1f)) {
                            itemsIndexed(visible, key = { _, e -> "${e.id}_${e.isVideo}" }) { _, e ->
                                val idx = library.indexOfFirst { it.id == e.id && it.isVideo == e.isVideo }
                                val on = idx == selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (on) Accent.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { playAt(idx) }
                                        .padding(6.dp, 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Thumb(e, Modifier.size(28.dp).clip(RoundedCornerShape(3.dp)), 12)
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            e.title,
                                            color = if (on) Accent else TextPri,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(fmt(e.duration), color = TextSec, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                        VerticalSb(plState)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .border(1.dp, Border)
                .padding(10.dp, 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmt(if (seeking) (seekPrev * effDur).toLong() else pos),
                    color = TextSec,
                    fontSize = 10.sp,
                    modifier = Modifier.width(36.dp),
                )
                Slider(
                    value = progress,
                    onValueChange = {
                        seeking = true
                        seekPrev = it
                    },
                    onValueChangeFinished = {
                        seekTo((seekPrev * effDur).toLong())
                        seeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Border,
                    ),
                    modifier = Modifier.weight(1f).height(18.dp),
                )
                Text(fmt(effDur), color = TextSec, fontSize = 10.sp, modifier = Modifier.width(36.dp).padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (cur != null) {
                    Thumb(cur, Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)), 18)
                } else {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)).background(PanelAlt),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, null, tint = Accent, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        cur?.title ?: "Nada a reproduzir",
                        color = TextPri,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        cur?.artist ?: cur?.displayName ?: "—",
                        color = TextSec,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton({ shuffle = !shuffle }, Modifier.size(26.dp)) {
                    Icon(Icons.Filled.Shuffle, null, tint = if (shuffle) Accent else TextSec, modifier = Modifier.size(14.dp))
                }
                IconButton({ prevIdx(selected, cur?.isVideo == true)?.let { playAt(it) } }, Modifier.size(30.dp)) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = TextPri, modifier = Modifier.size(18.dp))
                }
                IconButton({ seekBy(-10_000) }, Modifier.size(26.dp)) {
                    Icon(Icons.Filled.FastRewind, null, tint = TextSec, modifier = Modifier.size(15.dp))
                }
                IconButton({ toggle() }, Modifier.size(36.dp).clip(CircleShape).background(Accent)) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton({ seekBy(10_000) }, Modifier.size(26.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = TextSec, modifier = Modifier.size(15.dp))
                }
                IconButton({ nextIdx(selected, cur?.isVideo == true)?.let { playAt(it) } }, Modifier.size(30.dp)) {
                    Icon(Icons.Filled.SkipNext, null, tint = TextPri, modifier = Modifier.size(18.dp))
                }
                IconButton({ repeat = !repeat }, Modifier.size(26.dp)) {
                    Icon(Icons.Filled.Repeat, null, tint = if (repeat) Accent else TextSec, modifier = Modifier.size(14.dp))
                }
                IconButton({ setVol(volume - 0.1f) }, Modifier.size(24.dp)) {
                    Icon(Icons.Filled.VolumeDown, null, tint = TextSec, modifier = Modifier.size(14.dp))
                }
                Text("${(volume * 100).toInt()}%", color = TextSec, fontSize = 10.sp, modifier = Modifier.width(28.dp))
                IconButton({ setVol(volume + 0.1f) }, Modifier.size(24.dp)) {
                    Icon(Icons.Filled.VolumeUp, null, tint = TextSec, modifier = Modifier.size(14.dp))
                }
                IconButton({ showPl = !showPl }, Modifier.size(24.dp)) {
                    Icon(Icons.Filled.PlaylistPlay, null, tint = if (showPl) Accent else TextSec, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun VerticalSb(state: LazyListState) {
    val scope = rememberCoroutineScope()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return
    val vis = info.visibleItemsInfo
    if (vis.isEmpty()) return
    val vh = info.viewportSize.height.toFloat().coerceAtLeast(1f)
    val avg = vis.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val contentH = avg * total
    if (contentH <= vh) return
    val thumbF = (vh / contentH).coerceIn(0.1f, 1f)
    val first = vis.first()
    val frac = ((first.index - first.offset / avg) / (total - vis.size).coerceAtLeast(1)).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxHeight()
            .width(8.dp)
            .background(Color(0xFF2A2A2E), RoundedCornerShape(3.dp)),
    ) {
        val th = maxHeight * thumbF
        Box(
            Modifier
                .padding(top = (maxHeight - th) * frac)
                .width(8.dp)
                .height(th)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF6A6A70))
                .pointerInput(total) {
                    detectVerticalDragGestures { ch, dy ->
                        ch.consume()
                        val d = (dy / size.height * total).toInt()
                        if (d != 0) {
                            val t = (state.firstVisibleItemIndex + d).coerceIn(0, (total - 1).coerceAtLeast(0))
                            scope.launch { state.scrollToItem(t) }
                        }
                    }
                },
        )
    }
}
