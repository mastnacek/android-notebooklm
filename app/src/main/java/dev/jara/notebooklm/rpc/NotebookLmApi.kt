package dev.jara.notebooklm.rpc

import android.util.Log
import dev.jara.notebooklm.auth.AuthManager.AuthTokens
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class NotebookLmApi(
    private val httpClient: HttpClient,
    private val auth: AuthTokens,
) {
    companion object {
        private const val TAG = "NotebookLmApi"
    }

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

    suspend fun listNotebooks(): List<Notebook> {
        val params = buildJsonArray {
            add(JsonNull)
            add(JsonPrimitive(1))
            add(JsonNull)
            add(buildJsonArray { add(JsonPrimitive(2)) })
        }
        val result = rpcCall(RpcMethod.LIST_NOTEBOOKS, params)

        if (result == null) {
            Log.w(TAG, "listNotebooks: RPC result is null")
            return emptyList()
        }

        Log.i(TAG, "listNotebooks raw result: ${result.toString().take(500)}")
        return parseNotebooks(result)
    }

    private fun parseNotebooks(data: JsonElement): List<Notebook> {
        val notebooks = mutableListOf<Notebook>()

        try {
            val outer = data.jsonArray
            Log.i(TAG, "parseNotebooks: outer array size=${outer.size}")
            if (outer.isEmpty()) return emptyList()

            val list = outer[0].jsonArray
            Log.i(TAG, "parseNotebooks: notebook list size=${list.size}")

            for ((idx, item) in list.withIndex()) {
                try {
                    val arr = item.jsonArray
                    Log.i(TAG, "parseNotebooks[$idx]: arr size=${arr.size}, first 200 chars: ${arr.toString().take(200)}")
                    // Rust reference: title=[0], id=[2], emoji=[3]
                    val rawTitle = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: "Untitled"
                    val title = if (rawTitle.startsWith("thought\n")) {
                        rawTitle.removePrefix("thought\n").trim()
                    } else rawTitle
                    val id = arr.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: continue
                    val emoji = arr.getOrNull(3)?.jsonPrimitive?.contentOrNull ?: ""
                    val sourceCount = extractSourceCount(arr)
                    Log.i(TAG, "parseNotebooks[$idx]: id=$id, emoji=$emoji, title=$title")
                    notebooks.add(Notebook(id, title, emoji, sourceCount))
                } catch (e: Exception) {
                    Log.e(TAG, "parseNotebooks[$idx]: failed to parse item: ${e.message}")
                    continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseNotebooks: failed to parse outer: ${e.message}")
        }

        return notebooks
    }

    /** Rust: api::get_notebook_sources — RPC rLM1Ne */
    suspend fun getSources(notebookId: String): List<Source> {
        val params = buildJsonArray {
            add(JsonPrimitive(notebookId))
            add(JsonNull)
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(JsonNull)
            add(JsonPrimitive(0))
        }
        val result = rpcCall(
            RpcMethod.GET_NOTEBOOK, params,
            sourcePath = "/notebook/$notebookId"
        ) ?: return emptyList()

        return parseSources(result)
    }

    private fun parseSources(data: JsonElement): List<Source> {
        val sources = mutableListOf<Source>()
        try {
            // Rust: inner[0][1] = array of sources
            val list = data.jsonArray
                .getOrNull(0)?.jsonArray
                ?.getOrNull(1)?.jsonArray
                ?: return emptyList()

            for (src in list) {
                try {
                    val arr = src.jsonArray
                    val id = arr.getOrNull(0)?.jsonArray
                        ?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                    val title = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "(bez nazvu)"
                    val typeCode = arr.getOrNull(2)?.jsonArray
                        ?.getOrNull(4)?.jsonPrimitive?.intOrNull ?: 0
                    sources.add(Source(id, title, SourceType.fromCode(typeCode)))
                } catch (e: Exception) {
                    Log.w(TAG, "parseSources: skip item: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseSources: ${e.message}")
        }
        return sources
    }

    /** Rust: api::get_notebook_summary — RPC VfAZjd */
    suspend fun getSummary(notebookId: String): String? {
        val params = buildJsonArray {
            add(JsonPrimitive(notebookId))
            add(buildJsonArray { add(JsonPrimitive(2)) })
        }
        val result = rpcCall(
            RpcMethod.SUMMARIZE, params,
            sourcePath = "/notebook/$notebookId"
        ) ?: return null

        return try {
            val inner = result.jsonArray.getOrNull(0)?.jsonArray?.getOrNull(0)
            // Muze byt string nebo dalsi array — zkus oba
            inner?.jsonPrimitive?.contentOrNull
                ?: inner?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            Log.e(TAG, "getSummary parse: ${e.message}")
            // Fallback — zkus najit prvni string v nested strukture
            try {
                findFirstString(result)
            } catch (_: Exception) { null }
        }
    }

    /** Rust: api::list_artifacts — RPC gArtLc */
    suspend fun listArtifacts(notebookId: String): List<Artifact> {
        val params = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(JsonPrimitive(notebookId))
            add(JsonPrimitive("NOT artifact.status = \"ARTIFACT_STATUS_SUGGESTED\""))
        }
        val result = rpcCall(
            RpcMethod.LIST_ARTIFACTS, params,
            sourcePath = "/notebook/$notebookId"
        ) ?: return emptyList()

        return parseArtifacts(result)
    }

    /** Rust: api::delete_source — RPC tGMBJ */
    suspend fun deleteSource(notebookId: String, sourceId: String) {
        val params = buildJsonArray {
            add(buildJsonArray {
                add(buildJsonArray { add(JsonPrimitive(sourceId)) })
            })
        }
        rpcCall(RpcMethod.DELETE_SOURCE, params, sourcePath = "/notebook/$notebookId")
    }

    /** Rust: api::delete_artifact — RPC V5N4be */
    suspend fun deleteArtifact(artifactId: String) {
        val params = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(JsonPrimitive(artifactId))
        }
        rpcCall(RpcMethod.DELETE_ARTIFACT, params, sourcePath = "/")
    }

    /** Rust: api::get_source_fulltext — RPC hizoJc
     * Vraci textovy obsah zdroje pro hash porovnani pri deduplikaci */
    suspend fun getSourceFulltext(notebookId: String, sourceId: String): String {
        val params = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(sourceId)) })
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(buildJsonArray { add(JsonPrimitive(2)) })
        }
        val result = rpcCall(
            RpcMethod.GET_SOURCE, params,
            sourcePath = "/notebook/$notebookId"
        ) ?: return ""

        // Content blocks na result[3][0] — rekurzivne extrahuj stringy
        return try {
            val blocks = result.jsonArray.getOrNull(3)?.jsonArray?.getOrNull(0)
            if (blocks != null) extractAllText(blocks) else ""
        } catch (_: Exception) { "" }
    }

    /** Rekurzivne extrahuje vsechny textove stringy z vnorenych poli */
    private fun extractAllText(value: JsonElement, depth: Int = 50): String {
        if (depth <= 0) return ""
        return when {
            value is kotlinx.serialization.json.JsonPrimitive && value.isString ->
                value.content
            value is JsonArray ->
                value.joinToString("\n") { extractAllText(it, depth - 1) }
            else -> ""
        }
    }

    private fun parseArtifacts(data: JsonElement): List<Artifact> {
        val artifacts = mutableListOf<Artifact>()
        try {
            Log.i(TAG, "parseArtifacts raw: ${data.toString().take(500)}")
            val list = data.jsonArray.getOrNull(0)?.jsonArray ?: data.jsonArray
            for (art in list) {
                try {
                    val arr = art.jsonArray
                    val id = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                    val title = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "(bez nazvu)"
                    val typeCode = arr.getOrNull(2)?.jsonPrimitive?.intOrNull ?: 0
                    val statusCode = arr.getOrNull(4)?.jsonPrimitive?.intOrNull ?: 4
                    val url = extractArtifactUrl(arr, typeCode)
                    Log.i(TAG, "parseArtifact: id=${id.take(8)}, type=$typeCode, status=$statusCode, url=${url?.take(80)}")
                    // Loguj strukturu pro debug
                    if (typeCode == 1) {
                        Log.i(TAG, "parseArtifact AUDIO raw[6]: ${arr.getOrNull(6)?.toString()?.take(300)}")
                    } else {
                        // Loguj cely artefakt pro non-audio typy (hledame kde je URL)
                        Log.i(TAG, "parseArtifact type=$typeCode raw: ${arr.toString().take(800)}")
                    }
                    artifacts.add(Artifact(id, title, ArtifactType.fromCode(typeCode), ArtifactStatus.fromCode(statusCode), url))
                } catch (e: Exception) {
                    Log.w(TAG, "parseArtifacts: skip: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseArtifacts: ${e.message}")
        }
        return artifacts
    }

    /** Chat — GenerateFreeFormStreamed (specialni endpoint, ne batchexecute) */
    suspend fun sendChat(
        notebookId: String,
        sources: List<Source>,
        question: String,
        history: List<ChatMessage>,
        conversationId: String,
    ): String {
        // sources array: [[[sid1]], [[sid2]], ...]
        val sourcesArray = buildJsonArray {
            for (s in sources) {
                add(buildJsonArray { add(buildJsonArray { add(JsonPrimitive(s.id)) }) })
            }
        }

        val convHistory = buildConversationHistory(history)

        val params = buildJsonArray {
            add(sourcesArray)
            add(JsonPrimitive(question))
            add(convHistory)
            add(buildJsonArray {
                add(JsonPrimitive(2))
                add(JsonNull)
                add(buildJsonArray { add(JsonPrimitive(1)) })
            })
            add(JsonPrimitive(conversationId))
        }

        val fReqInner = buildJsonArray {
            add(JsonNull)
            add(JsonPrimitive(params.toString()))
        }
        val freq = fReqInner.toString()
        val encodedFreq = java.net.URLEncoder.encode(freq, "UTF-8")
        val encodedCsrf = java.net.URLEncoder.encode(auth.csrfToken, "UTF-8")
        val body = "f.req=$encodedFreq&at=$encodedCsrf&"

        val url = "https://notebooklm.google.com/_/LabsTailwindUi/data/" +
            "google.internal.labs.tailwind.orchestration.v1." +
            "LabsTailwindOrchestrationService/GenerateFreeFormStreamed" +
            "?bl=boq_labs-tailwind-frontend_20251221.14_p0" +
            "&hl=en&rt=c&f.sid=${java.net.URLEncoder.encode(auth.sessionId, "UTF-8")}"

        Log.i(TAG, "sendChat: url=${url.take(100)}, question=${question.take(50)}")

        val response = httpClient.post(url) {
            header("Cookie", auth.cookies)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
            contentType(ContentType.parse("application/x-www-form-urlencoded;charset=UTF-8"))
            setBody(body)
        }

        val responseText = response.bodyAsText()
        Log.i(TAG, "sendChat: HTTP ${response.status.value}, size=${responseText.length}")

        return parseChatResponse(responseText)
    }

    private fun parseChatResponse(body: String): String {
        var bestAnswer = ""
        for (line in body.lines()) {
            if (!line.contains("wrb.fr")) continue
            val parsed = try { Json.parseToJsonElement(line) } catch (_: Exception) { continue }

            val items = if (parsed.jsonArray.getOrNull(0)?.let {
                    try { it.jsonArray; true } catch (_: Exception) { false }
                } == true) {
                parsed.jsonArray.toList()
            } else {
                listOf(parsed)
            }

            for (item in items) {
                try {
                    val arr = item.jsonArray
                    if (arr.getOrNull(0)?.jsonPrimitive?.contentOrNull != "wrb.fr") continue
                    val innerStr = arr.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: continue
                    val inner = Json.parseToJsonElement(innerStr)
                    val text = inner.jsonArray.getOrNull(0)?.jsonArray
                        ?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                    if (text.length > bestAnswer.length) {
                        bestAnswer = text
                    }
                } catch (_: Exception) { continue }
            }
        }
        if (bestAnswer.isEmpty()) throw RuntimeException("Chat odpoved nenalezena")
        return bestAnswer
    }

    private fun buildConversationHistory(messages: List<ChatMessage>): JsonElement {
        if (messages.isEmpty()) return JsonNull
        val history = buildJsonArray {
            var i = 0
            while (i + 1 < messages.size) {
                if (messages[i].role == ChatRole.USER && messages[i + 1].role == ChatRole.ASSISTANT) {
                    // answer PRED query (reverse chronological per pair)
                    add(buildJsonArray {
                        add(JsonPrimitive(messages[i + 1].text))
                        add(JsonNull)
                        add(JsonPrimitive(2))
                    })
                    add(buildJsonArray {
                        add(JsonPrimitive(messages[i].text))
                        add(JsonNull)
                        add(JsonPrimitive(1))
                    })
                    i += 2
                } else {
                    i++
                }
            }
        }
        return if (history.isEmpty()) JsonNull else history
    }

    /** Najde prvni neprazdny string v nested JSON strukture */
    private fun findFirstString(element: JsonElement): String? {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.length > 20 }
            is kotlinx.serialization.json.JsonArray -> {
                for (item in element) {
                    val found = findFirstString(item)
                    if (found != null) return found
                }
                null
            }
            else -> null
        }
    }

    /** Extrahuje URL z artifact dat */
    private fun extractArtifactUrl(arr: JsonArray, typeCode: Int): String? {
        return try {
            when (typeCode) {
                // Audio: hledame URL v art[6] — je to pole kde stringy jsou URLs
                // Format: [null, [...], "url_m140", "url_m18", ...]
                // Preferujeme URL bez =m140 suffix (lepsi kompatibilita), nebo prvni URL
                1 -> {
                    val inner = arr.getOrNull(6)?.jsonArray ?: return null
                    // Najdi vsechny URL stringy
                    val urls = inner.mapNotNull { el ->
                        try { el.jsonPrimitive.contentOrNull }
                        catch (_: Exception) { null }
                    }.filter { it.startsWith("https://") }
                    Log.i(TAG, "extractArtifactUrl AUDIO: found ${urls.size} urls")
                    urls.forEach { Log.i(TAG, "  url: ${it.takeLast(30)}") }
                    // Preferuj URL s =m18 (MP4/AAC), pak =m140 (WebM), pak cokoliv
                    urls.firstOrNull { it.contains("=m18") }
                        ?: urls.firstOrNull()
                }

                // Video: art[8] item s "video/mp4"
                3 -> arr.getOrNull(8)?.jsonArray?.firstNotNullOfOrNull { item ->
                    try {
                        val mime = item.jsonArray.getOrNull(2)?.jsonPrimitive?.contentOrNull
                        if (mime == "video/mp4") item.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull
                        else null
                    } catch (_: Exception) { null }
                }

                // Infographic: hledame URL v hluboke strukture
                7 -> arr.toList().asReversed().firstNotNullOfOrNull { item ->
                    try {
                        item.jsonArray.getOrNull(2)?.jsonArray
                            ?.getOrNull(0)?.jsonArray
                            ?.getOrNull(1)?.jsonArray
                            ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
                            ?.takeIf { it.startsWith("http") }
                    } catch (_: Exception) { null }
                }

                // SlideDeck/prezentace: art[16][3] = PDF URL
                8 -> arr.getOrNull(16)?.jsonArray
                    ?.getOrNull(3)?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("http") }

                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun extractSourceCount(arr: JsonArray): Int {
        return try {
            if (arr.size > 7) {
                arr[7].jsonPrimitive.int
            } else 0
        } catch (_: Exception) {
            0
        }
    }

    // ── Notes API ──

    data class Note(val id: String, val title: String, val content: String)

    /** Vytvori poznamku a hned ji naplni obsahem (Google ignoruje title v create) */
    suspend fun createNote(notebookId: String, title: String, content: String): String? {
        val createParams = buildJsonArray {
            add(JsonPrimitive(notebookId))
            add(JsonPrimitive(""))
            add(buildJsonArray { add(JsonPrimitive(1)) })
            add(kotlinx.serialization.json.JsonNull)
            add(JsonPrimitive("New Note"))
        }
        val result = rpcCall(RpcMethod.CREATE_NOTE, createParams, sourcePath = "/notebook/$notebookId")

        // Extrahuj note ID z odpovedi
        val noteId = try {
            val arr = result?.jsonArray
            when {
                arr != null && arr.size > 0 && arr[0] is JsonArray ->
                    arr[0].jsonArray[0].jsonPrimitive.contentOrNull
                arr != null && arr.size > 0 ->
                    arr[0].jsonPrimitive.contentOrNull
                else -> null
            }
        } catch (_: Exception) { null }

        if (noteId != null) {
            // Update s obsahem a titulem
            val updateParams = buildJsonArray {
                add(JsonPrimitive(notebookId))
                add(JsonPrimitive(noteId))
                add(buildJsonArray {
                    add(buildJsonArray {
                        add(buildJsonArray {
                            add(JsonPrimitive(content))
                            add(JsonPrimitive(title))
                            add(buildJsonArray {})
                            add(JsonPrimitive(0))
                        })
                    })
                })
            }
            rpcCall(RpcMethod.UPDATE_NOTE, updateParams, sourcePath = "/notebook/$notebookId")
        }

        return noteId
    }

    /** Vypise poznamky v notebooku (filtruje mind mapy) */
    suspend fun listNotes(notebookId: String): List<Note> {
        val params = buildJsonArray { add(JsonPrimitive(notebookId)) }
        val result = rpcCall(RpcMethod.GET_NOTES, params, sourcePath = "/notebook/$notebookId")
            ?: return emptyList()

        val notes = mutableListOf<Note>()
        try {
            val items = result.jsonArray.getOrNull(0)?.jsonArray ?: return emptyList()
            for (item in items) {
                val arr = item.jsonArray
                val id = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                // Preskoc smazane (status=2): [id, null, 2]
                if (arr.size >= 3 && arr.getOrNull(1) == kotlinx.serialization.json.JsonNull
                    && arr.getOrNull(2)?.jsonPrimitive?.intOrNull == 2) continue

                var content = ""
                var title = ""
                val data = arr.getOrNull(1)
                if (data is JsonArray && data.size > 1) {
                    content = data.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: ""
                    title = if (data.size > 4) data.getOrNull(4)?.jsonPrimitive?.contentOrNull ?: "" else ""
                } else if (data is kotlinx.serialization.json.JsonPrimitive) {
                    content = data.contentOrNull ?: ""
                }

                // Preskoc mind mapy
                if (content.contains("\"children\":") || content.contains("\"nodes\":")) continue

                notes.add(Note(id, title, content))
            }
        } catch (e: Exception) {
            Log.w(TAG, "listNotes parse: ${e.message}")
        }
        return notes
    }

    /** Smaze poznamku */
    suspend fun deleteNote(notebookId: String, noteId: String) {
        val params = buildJsonArray {
            add(JsonPrimitive(notebookId))
            add(kotlinx.serialization.json.JsonNull)
            add(buildJsonArray { add(JsonPrimitive(noteId)) })
        }
        rpcCall(RpcMethod.DELETE_NOTE, params, sourcePath = "/notebook/$notebookId")
    }

    // ── Notebook management ──

    /** Vytvori novy notebook s nazvem a emoji */
    suspend fun createNotebook(title: String, emoji: String = ""): Notebook? {
        val params = buildJsonArray {
            add(JsonPrimitive(title))
            add(JsonPrimitive(emoji))
        }
        val result = rpcCall(RpcMethod.CREATE_NOTEBOOK, params) ?: return null
        return try {
            val arr = result.jsonArray
            val id = arr.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: return null
            val t = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: title
            val e = arr.getOrNull(3)?.jsonPrimitive?.contentOrNull ?: emoji
            Notebook(id, t, e, 0)
        } catch (e: Exception) {
            Log.w(TAG, "createNotebook parse: ${e.message}")
            null
        }
    }

    /** Smaze notebook */
    suspend fun deleteNotebook(notebookId: String) {
        val params = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(notebookId)) })
            add(buildJsonArray { add(JsonPrimitive(2)) })
        }
        rpcCall(RpcMethod.DELETE_NOTEBOOK, params)
    }

    // ── Sources management ──

    /** Prida URL zdroj do notebooku */
    suspend fun addSourceUrl(notebookId: String, url: String): String? {
        val params = buildJsonArray {
            add(buildJsonArray {
                add(buildJsonArray {
                    add(JsonNull)  // 0
                    add(JsonNull)  // 1
                    add(buildJsonArray { add(JsonPrimitive(url)) })  // 2 - URL
                    add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                })
            })
            add(JsonPrimitive(notebookId))
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(JsonNull)
            add(JsonNull)
        }
        val result = rpcCall(RpcMethod.ADD_SOURCE, params, sourcePath = "/notebook/$notebookId")
        return extractSourceId(result)
    }

    /** Prida textovy zdroj do notebooku */
    suspend fun addSourceText(notebookId: String, title: String, content: String): String? {
        val params = buildJsonArray {
            add(buildJsonArray {
                add(buildJsonArray {
                    add(JsonNull)  // 0
                    add(buildJsonArray {  // 1 - text
                        add(JsonPrimitive(title))
                        add(JsonPrimitive(content))
                    })
                    add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                })
            })
            add(JsonPrimitive(notebookId))
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(JsonNull)
            add(JsonNull)
        }
        val result = rpcCall(RpcMethod.ADD_SOURCE, params, sourcePath = "/notebook/$notebookId")
        return extractSourceId(result)
    }

    /** Prida YouTube zdroj do notebooku */
    suspend fun addSourceYoutube(notebookId: String, url: String): String? {
        val params = buildJsonArray {
            add(buildJsonArray {
                add(buildJsonArray {
                    add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                    add(JsonNull); add(JsonNull); add(JsonNull)
                    add(buildJsonArray { add(JsonPrimitive(url)) })  // 7 - YouTube URL
                    add(JsonNull); add(JsonNull)
                    add(JsonPrimitive(1))
                })
            })
            add(JsonPrimitive(notebookId))
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(buildJsonArray {
                add(JsonPrimitive(1)); add(JsonNull); add(JsonNull); add(JsonNull)
                add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                add(buildJsonArray { add(JsonPrimitive(1)) })
            })
        }
        val result = rpcCall(RpcMethod.ADD_SOURCE, params, sourcePath = "/notebook/$notebookId")
        return extractSourceId(result)
    }

    private fun extractSourceId(result: JsonElement?): String? {
        return try {
            result?.jsonArray?.getOrNull(0)?.jsonArray
                ?.getOrNull(0)?.jsonArray
                ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) { null }
    }

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

    /** Spusti generovani artefaktu */
    suspend fun generateArtifact(
        notebookId: String,
        sources: List<Source>,
        type: GenerateType,
        options: GenerateOptions = GenerateOptions(),
    ): String? {
        val sourcesTriple = buildJsonArray {
            for (s in sources) {
                add(buildJsonArray { add(buildJsonArray { add(JsonPrimitive(s.id)) }) })
            }
        }
        val sourcesDouble = buildJsonArray {
            for (s in sources) {
                add(buildJsonArray { add(JsonPrimitive(s.id)) })
            }
        }

        val inst = if (options.instructions != null) JsonPrimitive(options.instructions) else JsonNull

        // Kazdy typ ma svuj specificky payload tvar (z Python reference)
        val innerArray = when (type) {
            GenerateType.AUDIO -> buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
                add(JsonNull); add(JsonNull)
                add(buildJsonArray {
                    add(JsonNull)
                    add(buildJsonArray {
                        add(inst)
                        add(JsonPrimitive(options.audioLength.code))
                        add(JsonNull)
                        add(sourcesDouble)
                        add(JsonPrimitive(options.language))
                        add(JsonNull)
                        add(JsonPrimitive(options.audioFormat.code))
                    })
                })
            }
            GenerateType.VIDEO -> buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
                add(JsonNull); add(JsonNull)
                add(buildJsonArray {
                    add(JsonNull); add(JsonNull); add(JsonNull)
                    add(buildJsonArray {
                        add(inst); add(JsonNull); add(sourcesDouble)
                        add(JsonPrimitive(options.language)); add(JsonNull)
                        add(JsonPrimitive(options.videoFormat.code))
                        add(JsonPrimitive(options.videoStyle.code))
                    })
                })
            }
            GenerateType.QUIZ -> buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
                add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                add(buildJsonArray {
                    add(JsonNull)
                    add(buildJsonArray {
                        add(JsonPrimitive(2)) // variant: quiz
                        add(JsonNull); add(inst); add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                        add(buildJsonArray {
                            add(JsonPrimitive(options.quizQuantity.code))
                            add(JsonPrimitive(options.quizDifficulty.code))
                        })
                    })
                })
            }
            GenerateType.INFOGRAPHIC -> buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
                // Padding do pozice [14]
                repeat(10) { add(JsonNull) }
                add(buildJsonArray {
                    add(buildJsonArray {
                        add(inst)
                        add(JsonPrimitive(options.language))
                        add(JsonNull)
                        add(JsonPrimitive(options.infographicOrientation.code))
                        add(JsonPrimitive(options.infographicDetail.code))
                    })
                })
            }
            GenerateType.SLIDE_DECK -> buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
                // Padding do pozice [16]
                repeat(12) { add(JsonNull) }
                add(buildJsonArray {
                    add(buildJsonArray {
                        add(inst)
                        add(JsonPrimitive(options.language))
                        add(JsonPrimitive(options.slideDeckFormat.code))
                        add(JsonPrimitive(options.slideDeckLength.code))
                    })
                })
            }
            else -> buildJsonArray {
                add(JsonNull); add(JsonNull); add(JsonPrimitive(type.code)); add(sourcesTriple)
            }
        }

        val params = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(2)) })
            add(JsonPrimitive(notebookId))
            add(innerArray)
        }

        val result = rpcCall(RpcMethod.GENERATE_ARTIFACT, params, sourcePath = "/notebook/$notebookId")
        return try {
            result?.jsonArray?.getOrNull(0)?.jsonArray
                ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) { null }
    }

    /** Ziska interaktivni HTML kvizu nebo flashcards */
    suspend fun getInteractiveHtml(notebookId: String, artifactId: String): String? {
        val params = buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(artifactId)) })
        }
        val result = rpcCall(RpcMethod.GET_INTERACTIVE_HTML, params, sourcePath = "/notebook/$notebookId")
        return try {
            findFirstString(result ?: return null)
        } catch (_: Exception) { null }
    }

    /** Nacte historii konverzace */
    suspend fun getConversationTurns(
        notebookId: String,
        conversationId: String,
        limit: Int = 50,
    ): List<ChatMessage> {
        val params = buildJsonArray {
            add(buildJsonArray {})
            add(JsonNull)
            add(JsonNull)
            add(JsonPrimitive(conversationId))
            add(JsonPrimitive(limit))
        }
        val result = rpcCall(RpcMethod.GET_CONVERSATION_TURNS, params, sourcePath = "/notebook/$notebookId")
            ?: return emptyList()

        val messages = mutableListOf<ChatMessage>()
        try {
            val turns = result.jsonArray.getOrNull(0)?.jsonArray ?: return emptyList()
            for (turn in turns) {
                val arr = turn.jsonArray
                val roleCode = arr.getOrNull(2)?.jsonPrimitive?.intOrNull ?: continue
                val text = when (roleCode) {
                    1 -> arr.getOrNull(3)?.jsonPrimitive?.contentOrNull  // user
                    2 -> arr.getOrNull(4)?.jsonArray                     // AI
                        ?.getOrNull(0)?.jsonArray
                        ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
                    else -> null
                } ?: continue
                messages.add(ChatMessage(
                    role = if (roleCode == 1) ChatRole.USER else ChatRole.ASSISTANT,
                    text = text,
                ))
            }
        } catch (e: Exception) {
            Log.w(TAG, "getConversationTurns parse: ${e.message}")
        }
        return messages
    }

    private suspend fun rpcCall(
        method: RpcMethod,
        params: JsonArray,
        sourcePath: String = "/",
    ): JsonElement? {
        val url = RpcEncoder.buildUrl(method, auth.sessionId, sourcePath)
        val body = RpcEncoder.buildRequestBody(method, params, auth.csrfToken)

        Log.i(TAG, "rpcCall: method=${method.id}, url=${url.take(100)}")
        Log.i(TAG, "rpcCall: cookies length=${auth.cookies.length}")
        // Logujeme jmena cookies (bez hodnot pro bezpecnost)
        val cookieNames = auth.cookies.split(";").map { it.trim().substringBefore("=") }
        Log.i(TAG, "rpcCall: cookie names=$cookieNames")

        val response = httpClient.post(url) {
            header("Cookie", auth.cookies)
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }

        val responseText = response.bodyAsText()
        Log.i(TAG, "rpcCall: HTTP ${response.status.value}, response size=${responseText.length}")
        Log.i(TAG, "rpcCall: response preview: ${responseText.take(300)}")

        return RpcDecoder.decode(responseText, method.id)
    }
}
