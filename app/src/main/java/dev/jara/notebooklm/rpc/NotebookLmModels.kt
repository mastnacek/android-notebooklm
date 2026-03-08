package dev.jara.notebooklm.rpc

// Datove modely a enumy pro NotebookLM API — extrahované z NotebookLmApi tridy

data class Notebook(
    val id: String,
    val title: String,
    val emoji: String,
    val sourceCount: Int,
)

data class Source(
    val id: String,
    val title: String,
    val type: SourceType,
)

enum class SourceType(val icon: String) {
    PDF("\uD83D\uDCC4"),
    WEB("\uD83C\uDF10"),
    YOUTUBE("\uD83C\uDFA5"),
    TEXT("\uD83D\uDCDD"),
    OTHER("\uD83D\uDCC1");

    companion object {
        fun fromCode(code: Int): SourceType = when (code) {
            3 -> PDF
            4 -> TEXT
            5 -> WEB
            9 -> YOUTUBE
            else -> OTHER
        }
    }
}

// ── Artifacts ──

enum class ArtifactType(val code: Int, val icon: String, val label: String) {
    AUDIO(1, "\uD83C\uDFA7", "Audio"),
    REPORT(2, "\uD83D\uDCC4", "Report"),
    VIDEO(3, "\uD83C\uDFA5", "Video"),
    QUIZ(4, "\u2753", "Quiz"),
    MIND_MAP(5, "\uD83E\uDDE0", "Mind Map"),
    INFOGRAPHIC(7, "\uD83D\uDDBC\uFE0F", "Infographic"),
    SLIDE_DECK(8, "\uD83D\uDCCA", "Prezentace"),
    DATA_TABLE(9, "\uD83D\uDCCB", "Tabulka");

    companion object {
        fun fromCode(code: Int): ArtifactType = entries.find { it.code == code } ?: REPORT
    }
}

enum class ArtifactStatus(val label: String) {
    PROCESSING("generuji..."),
    PENDING("ve fronte"),
    COMPLETED("hotovo"),
    FAILED("chyba");

    companion object {
        fun fromCode(code: Int): ArtifactStatus = when (code) {
            1 -> PROCESSING
            2 -> PENDING
            3 -> COMPLETED
            4 -> FAILED
            else -> FAILED
        }
    }
}

data class Artifact(
    val id: String,
    val title: String,
    val type: ArtifactType,
    val status: ArtifactStatus,
    val url: String? = null,
)

// ── Chat ──

data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

enum class ChatRole { USER, ASSISTANT }

// ── Notes ──

data class Note(val id: String, val title: String, val content: String)

// ── Artifact generation ──

/** Typy artefaktu ktere lze generovat */
enum class GenerateType(val code: Int, val label: String) {
    AUDIO(1, "Audio"),
    VIDEO(3, "Video"),
    QUIZ(4, "Kvíz"),
    MIND_MAP(5, "Mapa"),
    INFOGRAPHIC(7, "Infografika"),
    SLIDE_DECK(8, "Prezentace"),
    DATA_TABLE(9, "Tabulka"),
}

enum class AudioFormat(val code: Int, val label: String) {
    DEEP_DIVE(1, "Hloubkový"),
    BRIEF(2, "Stručný"),
    CRITIQUE(3, "Kritika"),
    DEBATE(4, "Debata"),
}

enum class AudioLength(val code: Int, val label: String) {
    SHORT(1, "Krátký"),
    DEFAULT(2, "Střední"),
    LONG(3, "Dlouhý"),
}

enum class VideoFormat(val code: Int, val label: String) {
    EXPLAINER(1, "Vysvětlení"),
    BRIEF(2, "Stručný"),
}

enum class VideoStyle(val code: Int, val label: String) {
    AUTO(1, "Auto"),
    CLASSIC(3, "Klasik"),
    WHITEBOARD(4, "Tabule"),
    KAWAII(5, "Kawaii"),
    ANIME(6, "Anime"),
    WATERCOLOR(7, "Akvarel"),
    RETRO(8, "Retro"),
    HERITAGE(9, "Heritage"),
    PAPER(10, "Papír"),
}

enum class QuizDifficulty(val code: Int, val label: String) {
    EASY(1, "Lehký"),
    MEDIUM(2, "Střední"),
    HARD(3, "Těžký"),
}

enum class QuizQuantity(val code: Int, val label: String) {
    FEWER(1, "Méně"),
    STANDARD(2, "Střední"),
    MORE(3, "Více"),
}

enum class InfographicOrientation(val code: Int, val label: String) {
    LANDSCAPE(1, "Na šířku"),
    PORTRAIT(2, "Na výšku"),
    SQUARE(3, "Čtverec"),
}

enum class InfographicDetail(val code: Int, val label: String) {
    CONCISE(1, "Stručný"),
    STANDARD(2, "Střední"),
    DETAILED(3, "Detailní"),
}

enum class SlideDeckFormat(val code: Int, val label: String) {
    DETAILED(1, "Detailní"),
    PRESENTER(2, "Prezentační"),
}

enum class SlideDeckLength(val code: Int, val label: String) {
    DEFAULT(1, "Střední"),
    SHORT(2, "Krátká"),
}

/** Parametry pro generovani artefaktu */
data class GenerateOptions(
    val instructions: String? = null,
    val language: String = "cs",
    val audioFormat: AudioFormat = AudioFormat.DEEP_DIVE,
    val audioLength: AudioLength = AudioLength.DEFAULT,
    val videoFormat: VideoFormat = VideoFormat.EXPLAINER,
    val videoStyle: VideoStyle = VideoStyle.AUTO,
    val quizDifficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    val quizQuantity: QuizQuantity = QuizQuantity.STANDARD,
    val infographicOrientation: InfographicOrientation = InfographicOrientation.PORTRAIT,
    val infographicDetail: InfographicDetail = InfographicDetail.STANDARD,
    val slideDeckFormat: SlideDeckFormat = SlideDeckFormat.DETAILED,
    val slideDeckLength: SlideDeckLength = SlideDeckLength.DEFAULT,
)
