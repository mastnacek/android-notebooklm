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

        Log.i(TAG, "decoded length: ${decoded.length}")
        // Debug: uloz decoded JSON na disk pro analyzu
        try {
            val debugFile = java.io.File("/data/data/dev.jara.notebooklm/cache/quiz_debug.json")
            debugFile.writeText(decoded, Charsets.UTF_8)
            Log.i(TAG, "decoded saved to ${debugFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "failed to save debug: ${e.message}")
        }

        // NotebookLM obcas generuje malformovany JSON — chybi klic "rationale":
        // Pattern: "text": "...", \n "neco bez klice", \n "isCorrect": ...
        // Oprava: vloz chybejici "rationale": pred orphan string
        val fixed = fixMalformedQuizJson(decoded)

        return try {
            val json = Json.parseToJsonElement(fixed).jsonObject
            val quizArray = json["quiz"]?.jsonArray ?: run {
                Log.w(TAG, "Klic 'quiz' nenalezen v JSON")
                return null
            }

            quizArray.mapIndexed { i, el ->
                val obj = el.jsonObject
                val question = cleanLatex(obj["question"]?.jsonPrimitive?.contentOrNull ?: "")
                val hint = cleanLatex(obj["hint"]?.jsonPrimitive?.contentOrNull ?: "")
                val options = obj["answerOptions"]?.jsonArray?.map { opt ->
                    val optObj = opt.jsonObject
                    AnswerOption(
                        text = cleanLatex(optObj["text"]?.jsonPrimitive?.contentOrNull ?: ""),
                        isCorrect = optObj["isCorrect"]?.jsonPrimitive?.booleanOrNull ?: false,
                        rationale = cleanLatex(optObj["rationale"]?.jsonPrimitive?.contentOrNull ?: ""),
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
                    put("explanation", q.hint.ifEmpty { q.options.firstOrNull { it.isCorrect }?.rationale ?: "" })
                    put("hint", q.hint)
                    put("rationales", buildJsonArray {
                        q.options.take(4).forEach { add(JsonPrimitive(it.rationale)) }
                    })
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

    /**
     * Vycisti LaTeX escape sekvence uvnitr $...$ znacek, ale ponecha $...$ jako markery
     * pro syntax highlighting v UI.
     * $Church \\, of \\, Satan$ → $Church of Satan$
     */
    private fun cleanLatex(s: String): String =
        s.replace(Regex("""\$([^$]*)\$""")) { match ->
            val cleaned = match.groupValues[1]
                .replace("\\\\,", " ")
                .replace("\\,", " ")
                .replace("\\\\", "")
                .replace("\\{", "{")
                .replace("\\}", "}")
                .replace("\\[", "[")
                .replace("\\]", "]")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace(Regex("""\s+"""), " ")
                .trim()
            "\$$cleaned\$"
        }

    /**
     * Opravi malformovany JSON z NotebookLM kde obcas chybi klic "rationale":
     * Napr.: "text": "...",\n "orphan string",\n "isCorrect": false
     * →      "text": "...",\n "rationale": "orphan string",\n "isCorrect": false
     */
    private fun fixMalformedQuizJson(json: String): String {
        // Hledame: po "text": "..." a carce, string ktery NENI nasledovan dvojteckou
        // (tj. je to hodnota bez klice — orphan string)
        return Regex(
            """("text"\s*:\s*"(?:[^"\\]|\\.)*"\s*,\s*"(?:[^"\\]|\\.)*"\s*,\s*\n\s*)("isCorrect")"""
        ).replace(json) { match ->
            // Tenhle pattern nematchne spravne, zkusim jiny pristup
            match.value
        }.let { _ ->
            // Jednoduchy pristup: najdi orphan stringy (string, string, "isCorrect")
            // v answerOptions bloku a vloz "rationale":
            val lines = json.lines().toMutableList()
            for (i in lines.indices) {
                val trimmed = lines[i].trim()
                // Orphan string = radek ktery je "neco...", a predchozi radek je "text": "...",
                // a nasledujici radek je "isCorrect":
                if (i > 0 && i < lines.size - 1 &&
                    trimmed.startsWith("\"") && trimmed.endsWith(",") &&
                    !trimmed.contains(":") &&
                    lines[i + 1].trim().startsWith("\"isCorrect\"")
                ) {
                    val indent = lines[i].takeWhile { it.isWhitespace() }
                    val value = trimmed.removeSuffix(",")
                    lines[i] = "$indent\"rationale\": $value,"
                    Log.i(TAG, "fixMalformedQuizJson: opravena radka $i")
                }
            }
            lines.joinToString("\n")
        }
    }

    private fun htmlUnescape(s: String): String {
        var result = s
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
        // Numericke entity: &#123; nebo &#x1F;
        result = Regex("""&#x([0-9a-fA-F]+);""").replace(result) {
            it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value
        }
        result = Regex("""&#(\d+);""").replace(result) {
            it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value
        }
        // &amp; az nakonec (jinak rozbije &amp;quot; atd.)
        result = result.replace("&amp;", "&")
        return result
    }

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
    val rationale: String = "",
)
