package dev.jara.notebooklm.ui

import android.util.Log
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.rpc.NotebookLmApi
import dev.jara.notebooklm.rpc.SourceType
import dev.jara.notebooklm.rpc.addSourceText
import dev.jara.notebooklm.rpc.addSourceUrl
import dev.jara.notebooklm.rpc.addSourceYoutube
import dev.jara.notebooklm.rpc.deleteSource
import dev.jara.notebooklm.rpc.getSources
import dev.jara.notebooklm.rpc.getSourceFulltext
import kotlinx.coroutines.launch

private const val TAG = "AppViewModel"

/** Prida zdroj do aktualniho notebooku (url, youtube, text) */
fun AppViewModel.addSource(type: String, value: String, title: String = "") {
    val tokens = authManager.loadTokens() ?: return
    val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
    viewModelScope.launch {
        try {
            val api = NotebookLmApi(httpClient, tokens)
            val sourceId = with(api) {
                when (type) {
                    "url" -> addSourceUrl(nb.id, value)
                    "youtube" -> addSourceYoutube(nb.id, value)
                    "text" -> addSourceText(nb.id, title.ifBlank { "Poznámka" }, value)
                    else -> null
                }
            }
            if (sourceId != null) {
                // Refresh sources
                val sources = with(api) { getSources(nb.id) }
                _detail.value = _detail.value.copy(sources = sources)
                _error.value = "Zdroj přidán"
            }
        } catch (e: Exception) {
            Log.e(TAG, "addSource", e)
            _error.value = "Chyba přidání zdroje: ${e.message}"
        }
    }
}

/** Smaze jeden zdroj z aktualniho notebooku */
fun AppViewModel.deleteSource(sourceId: String) {
    val tokens = authManager.loadTokens() ?: return
    val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
    viewModelScope.launch {
        try {
            val api = NotebookLmApi(httpClient, tokens)
            with(api) { deleteSource(nb.id, sourceId) }
            _detail.value = _detail.value.copy(
                sources = _detail.value.sources.filter { it.id != sourceId }
            )
        } catch (e: Exception) {
            Log.e(TAG, "deleteSource", e)
            _error.value = "Chyba: ${e.message}"
        }
    }
}

/** Smaze vice zdroju najednou */
fun AppViewModel.deleteSources(sourceIds: Set<String>) {
    val tokens = authManager.loadTokens() ?: return
    val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
    viewModelScope.launch {
        val api = NotebookLmApi(httpClient, tokens)
        for (id in sourceIds) {
            try {
                with(api) { deleteSource(nb.id, id) }
                _detail.value = _detail.value.copy(
                    sources = _detail.value.sources.filter { it.id != id }
                )
            } catch (e: Exception) {
                Log.e(TAG, "deleteSources: $id", e)
            }
        }
    }
}

/** Deduplikace zdroju v aktualnim notebooku — najde a smaze duplikaty */
fun AppViewModel.dedupCurrentNotebook() {
    val tokens = authManager.loadTokens() ?: return
    val nb = (_screen.value as? Screen.NotebookDetail)?.notebook ?: return
    _detailDedup.value = DeduplicationState(running = true, currentNotebook = nb.title, progress = "1/1")
    viewModelScope.launch {
        try {
            val api = NotebookLmApi(httpClient, tokens)
            val sources = with(api) { getSources(nb.id) }
            if (sources.size < 2) {
                _detailDedup.value = DeduplicationState(done = true, totalDeleted = 0, progress = "hotovo")
                return@launch
            }
            val byTitle = sources.groupBy { it.title }
            val groups = mutableListOf<DuplicateGroup>()
            var totalDeleted = 0

            for ((title, group) in byTitle) {
                if (group.size <= 1) continue
                val allText = group.all { it.type == SourceType.TEXT }
                if (allText) {
                    val hashGroups = mutableMapOf<String, MutableList<String>>()
                    for (src in group) {
                        val hash = try {
                            with(api) { getSourceFulltext(nb.id, src.id) }.hashCode().toString(16)
                        } catch (_: Exception) { "unique_${src.id}" }
                        hashGroups.getOrPut(hash) { mutableListOf() }.add(src.id)
                    }
                    for ((_, ids) in hashGroups) {
                        if (ids.size > 1) groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                    }
                } else {
                    val ids = group.map { it.id }
                    groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                }
            }

            if (groups.isEmpty()) {
                _detailDedup.value = DeduplicationState(done = true, totalDeleted = 0, progress = "hotovo")
                return@launch
            }

            _detailDedup.value = _detailDedup.value.copy(groups = groups)
            for (g in groups) {
                for (srcId in g.deleteIds) {
                    try {
                        with(api) { deleteSource(nb.id, srcId) }
                        totalDeleted++
                        _detailDedup.value = _detailDedup.value.copy(totalDeleted = totalDeleted)
                        _detail.value = _detail.value.copy(
                            sources = _detail.value.sources.filter { it.id != srcId }
                        )
                    } catch (_: Exception) {}
                }
            }
            _detailDedup.value = DeduplicationState(done = true, totalDeleted = totalDeleted, progress = "hotovo")
        } catch (e: Exception) {
            _detailDedup.value = DeduplicationState(error = "Chyba: ${e.message}")
        }
    }
}
