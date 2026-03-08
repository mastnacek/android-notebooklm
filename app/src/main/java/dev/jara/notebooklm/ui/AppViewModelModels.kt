package dev.jara.notebooklm.ui

import dev.jara.notebooklm.rpc.Artifact
import dev.jara.notebooklm.rpc.ChatMessage
import dev.jara.notebooklm.rpc.Note
import dev.jara.notebooklm.rpc.Notebook
import dev.jara.notebooklm.rpc.Source

/** Navigacni stav aplikace */
sealed class Screen {
    object Login : Screen()
    object NotebookList : Screen()
    data class NotebookDetail(val notebook: Notebook) : Screen()
}

/** Tab v detailu notebooku */
enum class DetailTab { CHAT, SOURCES, ARTIFACTS, NOTES }

/** Razeni seznamu sesitu — jako Rust NotebookSort */
enum class NotebookSort(val label: String) {
    DEFAULT("datum"),
    NAME_ASC("A-Z"),
    NAME_DESC("Z-A"),
    CATEGORY("kat.");

    fun next(): NotebookSort = entries[(ordinal + 1) % entries.size]
}

/** Stav audio prehravace */
data class AudioPlayerState(
    val url: String,
    val title: String,
    val cookies: String,
)

/** Stav stahovani artefaktu */
data class DownloadState(
    val artifactId: String,
    val progress: Float, // 0..1, -1 = indeterminate
    val error: String? = null,
    val done: Boolean = false,
    val filePath: String? = null,
)

/** Skupina duplikatu v jednom sesitu */
data class DuplicateGroup(
    val title: String,
    val count: Int,
    val deleteIds: List<String>, // ID zdrojů ke smazání (všechny kromě prvního)
)

/** Stav deduplikace */
data class DeduplicationState(
    val running: Boolean = false,
    val currentNotebook: String = "",
    val progress: String = "",
    val groups: List<DuplicateGroup> = emptyList(),
    val totalDeleted: Int = 0,
    val done: Boolean = false,
    val error: String? = null,
)

/** Stav AI klasifikace */
data class ClassificationState(
    val running: Boolean = false,
    val progress: String = "",
    val results: Map<String, String> = emptyMap(), // notebookId -> kategorie
    val done: Boolean = false,
    val error: String? = null,
)

data class DetailState(
    val sources: List<Source> = emptyList(),
    val summary: String? = null,
    val loading: Boolean = true,
    val artifacts: List<Artifact> = emptyList(),
    val notes: List<Note> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatAnswering: Boolean = false,
    val conversationId: String = java.util.UUID.randomUUID().toString(),
    val tab: DetailTab = DetailTab.CHAT,
    val audioPlayer: AudioPlayerState? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
)
