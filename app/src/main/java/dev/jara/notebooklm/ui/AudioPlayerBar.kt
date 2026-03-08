package dev.jara.notebooklm.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val player = remember(url) { createPlayer(context, url) }
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var positionText by remember { mutableStateOf("0:00") }
    var durationText by remember { mutableStateOf("--:--") }
    var buffering by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
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
        }
    }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Term.surfaceLight)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (errorMsg != null) "[err]"
                       else if (buffering) "[...]"
                       else if (isPlaying) "[pause]"
                       else "[play]",
                color = if (errorMsg != null) Term.red else Term.green,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        if (errorMsg == null) {
                            if (isPlaying) player.pause() else player.play()
                        }
                    }
                    .padding(end = 8.dp),
            )
            Text(
                text = errorMsg ?: title,
                color = if (errorMsg != null) Term.red else Term.white,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (errorMsg == null) {
                Text(
                    text = "$positionText / $durationText",
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = Term.fontSize,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Text(
                text = "[x]",
                color = Term.textDim,
                fontFamily = Term.font,
                fontSize = Term.fontSize,
                modifier = Modifier.clickable { onClose() },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Term.green,
            trackColor = Term.border,
        )
    }
}

/**
 * Custom DataSource ktery pro kazdy request pouziva cookies z WebView CookieManager.
 * Tohle resi problem s Google CDN redirecty mezi domenami — kazda domena
 * dostane sve cookies (jako Rust cookies_for_url).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class CookieAwareDataSource : BaseDataSource(/* isNetwork = */ true) {

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
