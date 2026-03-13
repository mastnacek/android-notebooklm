package dev.jara.notebooklm.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.delay
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app audio prehravac — streamuje z URL pres ExoPlayer.
 * Pouziva domain-aware cookies z WebView CookieManager pro autentizaci.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AudioPlayerBar(
    url: String,
    title: String,
    cookies: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var positionText by remember { mutableStateOf("0:00") }
    var durationText by remember { mutableStateOf("--:--") }
    var buffering by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Player s explicitním lifecycle — starý player se uvolní při změně URL
    var currentPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(url) {
        // Uvolni predchozi player
        currentPlayer?.release()

        // Reset state
        isPlaying = true
        progress = 0f
        positionText = "0:00"
        durationText = "--:--"
        buffering = true
        errorMsg = null

        val player = createPlayer(context, url)
        currentPlayer = player

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) isPlaying = false
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMsg = error.message ?: "Chyba prehravani"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            currentPlayer = null
        }
    }

    val player = currentPlayer ?: return

    LaunchedEffect(player) {
        while (true) {
            delay(500)
            val dur = player.duration
            val pos = player.currentPosition
            if (dur > 0) {
                progress = pos.toFloat() / dur
                positionText = formatMs(pos)
                durationText = formatMs(dur)
            }
        }
    }

    val shape = RoundedCornerShape(DS.cardRadius)
    val playerBg = Term.green.copy(alpha = 0.08f)
    val playerBorder = Term.green.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(playerBg)
            .border(1.5.dp, playerBorder, shape)
            .padding(12.dp),
    ) {
        // ── Titulek + close ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = null,
                tint = Term.green,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = errorMsg ?: title,
                color = if (errorMsg != null) Term.red else Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconMicroAction(Icons.Filled.Close, Term.textDim, "Zavřít") { onClose() }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Seekbar (Slider) ──
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                progress = newProgress
                val dur = player.duration
                if (dur > 0) {
                    player.seekTo((newProgress * dur).toLong())
                }
            },
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Term.green,
                activeTrackColor = Term.green,
                inactiveTrackColor = Term.border,
            ),
        )

        // ── Časy ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = positionText,
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = 11.sp,
            )
            Text(
                text = durationText,
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = 11.sp,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Ovládací tlačítka ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // -15s
            IconButton(
                onClick = { player.seekTo((player.currentPosition - 15000).coerceAtLeast(0)) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = "−15s",
                    tint = Term.textDim,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play/Pause — hlavní tlačítko
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Term.green)
                    .clickable {
                        if (errorMsg == null) {
                            if (isPlaying) player.pause() else player.play()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (buffering) {
                    CircularProgressIndicator(
                        color = Term.bg,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pauza" else "Přehrát",
                        tint = Term.bg,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // +15s
            IconButton(
                onClick = {
                    val dur = player.duration
                    player.seekTo((player.currentPosition + 15000).coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE))
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward30,
                    contentDescription = "+15s",
                    tint = Term.textDim,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Custom DataSource ktery pro kazdy request pouziva cookies z WebView CookieManager.
 * Tohle resi problem s Google CDN redirecty mezi domenami — kazda domena
 * dostane sve cookies (jako Rust cookies_for_url).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CookieAwareDataSource : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        private const val TAG = "CookieAwareDS"
        private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }

    private var connection: HttpURLConnection? = null
    private var inputStream: InputStream? = null
    private var bytesRead: Long = 0
    private var dataSpec: DataSpec? = null

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        val cookieMgr = CookieManager.getInstance()
        var currentUrl = dataSpec.uri.toString()

        // Manualni redirect follow s domain-aware cookies
        for (i in 0 until 10) {
            val domainCookies = cookieMgr.getCookie(currentUrl) ?: ""
            Log.i(TAG, "open: step $i, url=${currentUrl.take(80)}, cookies=${domainCookies.length} chars")

            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Cookie", domainCookies)
            conn.setRequestProperty("User-Agent", UA)

            if (dataSpec.position > 0) {
                conn.setRequestProperty("Range", "bytes=${dataSpec.position}-")
            }

            conn.connect()
            val code = conn.responseCode
            Log.i(TAG, "open: status=$code, type=${conn.contentType}")

            if (code in 301..303 || code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    currentUrl = if (location.startsWith("http")) location
                    else {
                        val base = currentUrl.substringBefore("://") + "://" +
                            currentUrl.substringAfter("://").substringBefore("/")
                        "$base$location"
                    }
                    continue
                }
            }

            if (code !in 200..299) {
                conn.disconnect()
                throw HttpDataSource.InvalidResponseCodeException(
                    code, conn.responseMessage, null,
                    emptyMap(), dataSpec, ByteArray(0)
                )
            }

            connection = conn
            inputStream = conn.inputStream
            bytesRead = 0
            transferStarted(dataSpec)

            val contentLength = conn.contentLength.toLong()
            return if (contentLength > 0) contentLength else -1L
        }

        throw HttpDataSource.HttpDataSourceException(
            "Too many redirects",
            dataSpec,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            HttpDataSource.HttpDataSourceException.TYPE_OPEN
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = inputStream ?: return -1
        val read = stream.read(buffer, offset, length)
        if (read == -1) return -1
        bytesRead += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            connection?.disconnect()
        } catch (_: Exception) {}
        inputStream = null
        connection = null
        if (dataSpec != null) transferEnded()
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): CookieAwareDataSource = CookieAwareDataSource()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun createPlayer(context: Context, url: String): ExoPlayer {
    val dataSourceFactory = CookieAwareDataSource.Factory()

    // Google audio URL muze byt WebM (=m140) nebo MP4 (=m18)
    val mimeType = when {
        url.contains("=m18") -> "audio/mp4"
        url.contains("=m140") -> "audio/webm"
        else -> "audio/mpeg"
    }

    val mediaItem = MediaItem.Builder()
        .setUri(url)
        .setMimeType(mimeType)
        .build()

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}
