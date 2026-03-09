package dev.jara.notebooklm.rpc

import android.util.Log
import dev.jara.notebooklm.auth.AuthManager.AuthTokens
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class NotebookLmApi(
    internal val httpClient: HttpClient,
    internal val auth: AuthTokens,
) {
    companion object {
        internal const val TAG = "NotebookLmApi"
    }


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
    internal fun findFirstString(element: JsonElement): String? {
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

    /** Získá info o účtu + AI model */
    suspend fun getAccountInfo(): AccountInfo? {
        val accountResult = rpcCall(RpcMethod.GET_OR_CREATE_ACCOUNT, buildJsonArray {})
        val modelResult = rpcCall(RpcMethod.LIST_MODEL_OPTIONS, buildJsonArray {})

        return try {
            val acc = accountResult?.jsonArray?.getOrNull(0)?.jsonArray ?: return null
            val limits = acc.getOrNull(1)?.jsonArray ?: return null
            val tierCode = limits.getOrNull(0)?.jsonPrimitive?.intOrNull ?: 1
            val dailyLimit = limits.getOrNull(1)?.jsonPrimitive?.intOrNull ?: 0
            val sourceLimit = limits.getOrNull(2)?.jsonPrimitive?.intOrNull ?: 0
            val contextLimit = limits.getOrNull(3)?.jsonPrimitive?.intOrNull ?: 0
            val locale = try {
                acc.getOrNull(2)?.jsonArray?.getOrNull(4)?.jsonArray
                    ?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: "en"
            } catch (_: Exception) { "en" }

            val modelName = try {
                modelResult?.jsonArray?.getOrNull(1)?.jsonPrimitive?.contentOrNull
                    ?: "unknown"
            } catch (_: Exception) { "unknown" }

            AccountInfo(
                tier = AccountTier.fromCode(tierCode),
                dailyLimit = dailyLimit,
                sourceLimit = sourceLimit,
                contextLimit = contextLimit,
                locale = locale,
                modelName = modelName,
            )
        } catch (e: Exception) {
            Log.w(TAG, "getAccountInfo parse: ${e.message}")
            null
        }
    }

    internal suspend fun rpcCall(
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
