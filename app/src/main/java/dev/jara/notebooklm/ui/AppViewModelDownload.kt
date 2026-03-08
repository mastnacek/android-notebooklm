package dev.jara.notebooklm.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.rpc.Artifact
import dev.jara.notebooklm.rpc.ArtifactType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AppViewModel"

/** Stahne artefakt (audio/video/pdf/png) s progress reportingem */
fun AppViewModel.downloadArtifact(artifact: Artifact) {
    val url = artifact.url ?: return
    val ext = when (artifact.type) {
        ArtifactType.AUDIO -> ".mp3"
        ArtifactType.VIDEO -> ".mp4"
        ArtifactType.SLIDE_DECK -> ".pdf"
        ArtifactType.INFOGRAPHIC -> ".png"
        else -> ""
    }
    val fileName = "${artifact.title}$ext".replace("/", "_")

    val downloads = _detail.value.downloads.toMutableMap()
    downloads[artifact.id] = DownloadState(artifact.id, 0f)
    _detail.value = _detail.value.copy(downloads = downloads)

    viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                // Manualni redirect follow s domain-aware cookies pres HttpURLConnection
                // (bez Ktor — ten ma timeout problemy na velkych souborech)
                val cookieMgr = CookieManager.getInstance()
                val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
                var currentUrl = url

                // 1) Resolve redirecty — najdi finalni URL
                var finalConn: java.net.HttpURLConnection? = null
                for (i in 0 until 10) {
                    val domainCookies = cookieMgr.getCookie(currentUrl) ?: ""
                    Log.i(TAG, "download: step $i, url=${currentUrl.take(80)}")
                    Log.i(TAG, "download: cookies ${domainCookies.length} chars")

                    val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Cookie", domainCookies)
                    conn.setRequestProperty("User-Agent", ua)
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 5 * 60_000 // 5 min pro velke soubory
                    conn.connect()

                    val code = conn.responseCode
                    Log.i(TAG, "download: status=$code, type=${conn.contentType}")

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
                        throw RuntimeException("HTTP $code")
                    }

                    finalConn = conn
                    break
                }

                val conn = finalConn ?: throw RuntimeException("Too many redirects")
                val contentLength = conn.contentLength.toLong()
                Log.i(TAG, "download: streaming $contentLength bytes")

                // Zkontroluj ze to neni HTML
                val contentType = conn.contentType ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    conn.disconnect()
                    throw RuntimeException("Server vratil HTML misto audio (auth problem)")
                }

                // 2) Streamuj s progress reportingem do vybrane slozky
                val savedUri = getDownloadPath()
                val outputUri: Uri
                val ctx = getApplication<Application>()

                if (savedUri.isNotEmpty()) {
                    // SAF slozka vybrana uzivatelem
                    val treeUri = Uri.parse(savedUri)
                    val dir = DocumentFile.fromTreeUri(ctx, treeUri)
                        ?: throw RuntimeException("Nelze otevrit vybranou slozku")
                    val mime = when {
                        fileName.endsWith(".mp3") -> "audio/mpeg"
                        fileName.endsWith(".mp4") -> "video/mp4"
                        fileName.endsWith(".pdf") -> "application/pdf"
                        fileName.endsWith(".png") -> "image/png"
                        else -> "application/octet-stream"
                    }
                    // Smaz existujici soubor se stejnym jmenem
                    dir.findFile(fileName)?.delete()
                    val docFile = dir.createFile(mime, fileName)
                        ?: throw RuntimeException("Nelze vytvorit soubor v $savedUri")
                    outputUri = docFile.uri
                } else {
                    // Fallback: Downloads/notebooklm
                    val downloadsDir = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ),
                        "notebooklm"
                    )
                    downloadsDir.mkdirs()
                    val outFile = java.io.File(downloadsDir, fileName)
                    outputUri = Uri.fromFile(outFile)
                }

                conn.inputStream.use { input ->
                    ctx.contentResolver.openOutputStream(outputUri)!!.use { output ->
                        val buffer = ByteArray(8192)
                        var totalRead = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            val prog = if (contentLength > 0) totalRead.toFloat() / contentLength else -1f
                            withContext(Dispatchers.Main) {
                                val d = _detail.value.downloads.toMutableMap()
                                d[artifact.id] = DownloadState(artifact.id, prog)
                                _detail.value = _detail.value.copy(downloads = d)
                            }
                        }
                    }
                }
                conn.disconnect()

                withContext(Dispatchers.Main) {
                    val d = _detail.value.downloads.toMutableMap()
                    d[artifact.id] = DownloadState(artifact.id, 1f, done = true, filePath = outputUri.toString())
                    _detail.value = _detail.value.copy(downloads = d)
                }
                Log.i(TAG, "download: done -> $outputUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "download", e)
            val d = _detail.value.downloads.toMutableMap()
            d[artifact.id] = DownloadState(artifact.id, 0f, error = e.message)
            _detail.value = _detail.value.copy(downloads = d)
        }
    }
}

/** Najde uz stazene artefakty v download slozce */
fun AppViewModel.detectDownloadedArtifacts(artifacts: List<Artifact>): Map<String, DownloadState> {
    val result = mutableMapOf<String, DownloadState>()
    val ctx = getApplication<Application>()
    val savedUri = getDownloadPath()

    for (art in artifacts) {
        if (art.url == null) continue
        val ext = when (art.type) {
            ArtifactType.AUDIO -> ".mp3"
            ArtifactType.VIDEO -> ".mp4"
            ArtifactType.SLIDE_DECK -> ".pdf"
            ArtifactType.INFOGRAPHIC -> ".png"
            else -> ""
        }
        val fileName = "${art.title}$ext".replace("/", "_")

        if (savedUri.isNotEmpty()) {
            // SAF slozka
            try {
                val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(savedUri))
                val file = dir?.findFile(fileName)
                if (file != null && file.exists() && file.length() > 0) {
                    result[art.id] = DownloadState(art.id, 1f, done = true, filePath = file.uri.toString())
                }
            } catch (e: Exception) {
                Log.w(TAG, "detectDownloads SAF: ${e.message}")
            }
        } else {
            // Fallback Downloads/notebooklm
            val file = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ),
                "notebooklm/$fileName"
            )
            if (file.exists() && file.length() > 0) {
                result[art.id] = DownloadState(art.id, 1f, done = true, filePath = Uri.fromFile(file).toString())
            }
        }
    }
    return result
}

/** Cesta ke stazenim — z SharedPreferences */
fun AppViewModel.getDownloadPath(): String = prefs.getString("download_path", "") ?: ""

/** Nastavi cestu ke stazenim */
fun AppViewModel.setDownloadPath(path: String) {
    prefs.edit().putString("download_path", path).apply()
}
