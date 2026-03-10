package dev.jara.notebooklm.ui

import dev.jara.notebooklm.rpc.Artifact
import dev.jara.notebooklm.rpc.ChatMessage
import dev.jara.notebooklm.rpc.Note
import dev.jara.notebooklm.rpc.Notebook
import dev.jara.notebooklm.rpc.QuizQuestion
import dev.jara.notebooklm.rpc.Source

/** Navigacni stav aplikace */
sealed class Screen {
    object Login : Screen()
    object NotebookList : Screen()
    data class NotebookDetail(val notebook: Notebook) : Screen()
    data class Quiz(val questions: List<QuizQuestion>, val title: String, val sourceNotebook: Notebook) : Screen()
}

/** Tab v detailu notebooku */
enum class DetailTab { CHAT, SOURCES, ARTIFACTS, NOTES }

/** Razeni seznamu sesitu — jako Rust NotebookSort */
enum class NotebookSort(val label: String) {
    DEFAULT("⏱"),
    MODIFIED("✎"),
    CREATED("★"),
    NAME_ASC("A↓"),
    NAME_DESC("Z↓"),
    CATEGORY("◆"),
    SOURCES("⊞");

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
    val promptSuggestions: List<String> = emptyList(),
    val conversationId: String = java.util.UUID.randomUUID().toString(),
    val tab: DetailTab = DetailTab.CHAT,
    val audioPlayer: AudioPlayerState? = null,
    val downloads: Map<String, DownloadState> = emptyMap(),
)

/** PMEST facety pro notebook */
data class NotebookFacets(
    val topic: String = "",
    val format: String = "",
    val purpose: String = "",
    val domain: String = "",
    val freshness: String = "",
)

/** Aktivní filtry — každý facet může mít vybrané hodnoty (AND mezi facetami) */
data class FacetFilter(
    val topics: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val purposes: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    val freshnesses: Set<String> = emptySet(),
) {
    val activeCount: Int get() = listOf(topics, formats, purposes, domains, freshnesses).count { it.isNotEmpty() }
    val isEmpty: Boolean get() = activeCount == 0

    fun matches(facets: NotebookFacets): Boolean {
        if (topics.isNotEmpty() && facets.topic !in topics) return false
        if (formats.isNotEmpty() && facets.format !in formats) return false
        if (purposes.isNotEmpty() && facets.purpose !in purposes) return false
        if (domains.isNotEmpty() && facets.domain !in domains) return false
        if (freshnesses.isNotEmpty() && facets.freshness !in freshnesses) return false
        return true
    }
}

/** Záznam zdroje pro DB */
data class SourceRecord(
    val sourceId: String,
    val title: String,
    val type: String,      // PDF, WEB, YOUTUBE, TEXT, OTHER
    val contentHash: String?,
)

/** Stav batch skenu zdrojů */
data class SourceScanState(
    val running: Boolean = false,
    val currentNotebook: String = "",
    val progress: String = "",
    val done: Boolean = false,
    val error: String? = null,
)

/** Indikátory stavu notebooku (pro UI tečky) */
data class NotebookIndicators(
    val scanned: Boolean = false,
    val embedded: Boolean = false,
    val classified: Boolean = false,
    val deduped: Boolean = false,
)
