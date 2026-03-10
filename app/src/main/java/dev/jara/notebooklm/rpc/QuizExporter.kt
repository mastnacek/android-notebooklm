package dev.jara.notebooklm.rpc

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.serialization.json.*
import java.io.File
import java.text.Normalizer

private const val TAG = "QuizExporter"

/**
 * Parsuje kvizovy HTML z NotebookLM a exportuje do BrainGate JSON formatu.
 *
 * NotebookLM format (z data-app-data atributu):
 *   { "quiz": [{ "question": "...", "answerOptions": [{"text":"...", "isCorrect": true}], "hint": "..." }] }
 *
 * BrainGate format:
 *   { "subject": "notebooklm", "version": 1, "topics": { "slug": { "label": "...", "items": [...] } } }
 */
object QuizExporter {

    private val prettyJson = Json { prettyPrint = true }

    /** Parsuje HTML a vrati seznam otazek v surove podobe */
    fun parseQuizHtml(html: String): List<QuizQuestion>? {
        // Najdi data-app-data atribut
        val regex = Regex("""data-app-data="([^"]+)"""")
        val match = regex.find(html) ?: run {
            Log.w(TAG, "data-app-data atribut nenalezen v HTML")
            return null
        }

        val encoded = match.groupValues[1]
        val decoded = htmlUnescape(encoded)

        return try {
            val json = Json.parseToJsonElement(decoded).jsonObject
            val quizArray = json["quiz"]?.jsonArray ?: run {
                Log.w(TAG, "Klic 'quiz' nenalezen v JSON")
                return null
            }

            quizArray.mapIndexed { i, el ->
                val obj = el.jsonObject
                val question = obj["question"]?.jsonPrimitive?.contentOrNull ?: ""
                val hint = obj["hint"]?.jsonPrimitive?.contentOrNull ?: ""
                val options = obj["answerOptions"]?.jsonArray?.map { opt ->
                    val optObj = opt.jsonObject
                    AnswerOption(
                        text = optObj["text"]?.jsonPrimitive?.contentOrNull ?: "",
                        isCorrect = optObj["isCorrect"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                } ?: emptyList()
                val correctIdx = options.indexOfFirst { it.isCorrect }
                Log.i(TAG, "Q${i+1}: correctIdx=$correctIdx / ${options.size} opts")
                QuizQuestion(id = "nlm-${i + 1}", question = question, options = options, hint = hint)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chyba parsovani quiz JSON: ${e.message}")
            null
        }
    }

    /** Transformuje otazky do BrainGate JSON formatu */
    fun toBrainGateJson(
        questions: List<QuizQuestion>,
        title: String,
        difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    ): String {
        val slug = slugify(title)
        val diffRank = when (difficulty) {
            QuizDifficulty.EASY -> "1"
            QuizDifficulty.MEDIUM -> "2"
            QuizDifficulty.HARD -> "3"
        }

        val items = buildJsonArray {
            for (q in questions) {
                // BrainGate vyzaduje presne 4 moznosti
                val opts = q.options.take(4).map { it.text }
                val padded = opts + List(maxOf(0, 4 - opts.size)) { "" }
                val correctIdx = q.options.indexOfFirst { it.isCorrect }.takeIf { it >= 0 } ?: 0

                add(buildJsonObject {
                    put("id", q.id)
                    put("question", q.question)
                    put("options", buildJsonArray { padded.forEach { add(JsonPrimitive(it)) } })
                    put("correctIndex", correctIdx)
                    put("explanation", q.hint.ifEmpty { q.options.firstOrNull { it.isCorrect }?.text ?: "" })
                    put("difficultyRank", diffRank)
                })
            }
        }

        val root = buildJsonObject {
            put("subject", "notebooklm")
            put("version", 1)
            put("topics", buildJsonObject {
                put(slug, buildJsonObject {
                    put("label", title)
                    put("items", items)
                })
            })
        }

        return prettyJson.encodeToString(JsonElement.serializer(), root)
    }

    /** Exportuje kviz do souboru a otevre share intent */
    fun exportAndShare(
        context: Context,
        html: String,
        artifactTitle: String,
        difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    ): Boolean {
        val questions = parseQuizHtml(html) ?: return false
        if (questions.isEmpty()) return false

        val json = toBrainGateJson(questions, artifactTitle, difficulty)
        val fileName = "quiz_${slugify(artifactTitle)}.json"

        return try {
            val file = File(context.cacheDir, fileName)
            file.writeText(json, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "BrainGate kvíz: $artifactTitle")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exportovat kvíz"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export kvizu selhal: ${e.message}")
            false
        }
    }

    private fun htmlUnescape(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
        .replace("&#39;", "'")

    private fun slugify(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return normalized.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(60)
    }
}

data class QuizQuestion(
    val id: String,
    val question: String,
    val options: List<AnswerOption>,
    val hint: String,
)

data class AnswerOption(
    val text: String,
    val isCorrect: Boolean,
)
