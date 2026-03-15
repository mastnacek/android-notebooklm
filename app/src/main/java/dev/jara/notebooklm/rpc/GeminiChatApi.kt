package dev.jara.notebooklm.rpc

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * API klient pro Gemini chat (gemini.google.com).
 *
 * Používá stejný batchexecute protokol jako NotebookLM, ale s jiným
 * base URL a RPC ID. Autentizace sdílí Google cookies.
 *
 * RPC IDs (reverse-engineered z gemini-webapi):
 * - MaZiqc = LIST_CHATS
 * - hNvQHb = READ_CHAT
 * - GzXR5e = DELETE_CHAT
 */
class GeminiChatApi(
    private val httpClient: HttpClient,
    private val cookies: String,
    private val csrfToken: String,
    private val sessionId: String = "",
) {
    companion object {
        private const val TAG = "GeminiChatApi"
        private const val BATCHEXECUTE_URL =
            "https://gemini.google.com/_/BardChatUi/data/batchexecute"

        // RPC IDs
        private const val LIST_CHATS = "MaZiqc"
        private const val READ_CHAT = "hNvQHb"
        private const val DELETE_CHAT = "GzXR5e"

        /** Načte CSRF a session tokeny z Gemini homepage */
        suspend fun fetchGeminiTokens(
            httpClient: HttpClient,
            cookies: String,
        ): GeminiTokens? {
            try {
                val response = httpClient.get("https://gemini.google.com/app") {
                    header("Cookie", cookies)
                    header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                }
                val html = response.bodyAsText()

                val csrfMatch = Regex(""""SNlM0e"\s*:\s*"([^"]+)"""").find(html)
                    ?: return null
                val sessionMatch = Regex(""""FdrFJe"\s*:\s*"([^"]+)"""").find(html)

                return GeminiTokens(
                    csrfMatch.groupValues[1],
                    sessionMatch?.groupValues?.get(1) ?: "",
                )
            } catch (e: Exception) {
                Log.e(TAG, "fetchGeminiTokens", e)
                return null
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Data class pro Gemini chat konverzaci */
    data class GeminiChat(
        val id: String,
        val title: String,
        val lastMessage: String = "",
        val timestamp: Long = 0L,  // epoch seconds
        val turnCount: Int = 0,
    )

    /** Data class pro turn v Gemini chatu */
    data class GeminiTurn(
        val userPrompt: String,
        val assistantResponse: String,
        val timestamp: Long = 0L,
    )

    /**
     * Načte tokeny (CSRF + session ID) z gemini.google.com homepage.
     * Sdílí cookies z NotebookLM loginu.
     */
    data class GeminiTokens(
        val csrfToken: String,
        val sessionId: String,
    )

    /** Seznam všech Gemini chatů */
    suspend fun listChats(): List<GeminiChat> {
        val result = rpcCall(LIST_CHATS, buildJsonArray {})
        if (result == null) {
            Log.w(TAG, "listChats: null result")
            return emptyList()
        }
        return parseChats(result)
    }

    /** Přečte historii jednoho chatu */
    suspend fun readChat(chatId: String, maxTurns: Int = 100): List<GeminiTurn> {
        val params = buildJsonArray {
            add(JsonPrimitive(chatId))
            add(JsonPrimitive(maxTurns))
            add(JsonNull)
            add(JsonPrimitive(1))
            add(buildJsonArray { add(JsonPrimitive(0)) })
            add(buildJsonArray { add(JsonPrimitive(4)) })
            add(JsonNull)
            add(JsonPrimitive(1))
        }
        val result = rpcCall(READ_CHAT, params)
        if (result == null) {
            Log.w(TAG, "readChat: null result for $chatId")
            return emptyList()
        }
        return parseTurns(result)
    }

    /** Smaže chat */
    suspend fun deleteChat(chatId: String) {
        val params = buildJsonArray { add(JsonPrimitive(chatId)) }
        rpcCall(DELETE_CHAT, params)
    }

    // ── Parsing ──

    private fun parseChats(data: JsonElement): List<GeminiChat> {
        val chats = mutableListOf<GeminiChat>()
        try {
            val outer = data.jsonArray
            if (outer.isEmpty()) return emptyList()

            // Response structure: [[chat1, chat2, ...], ...]
            // Each chat: [[cid, rid], ...metadata..., [user_text], [assistant_response], [timestamp_sec, ns]]
            val chatList = outer[0].jsonArray

            for (chatItem in chatList) {
                try {
                    val item = chatItem.jsonArray

                    // ID z [0][0]
                    val id = item.getOrNull(0)?.jsonArray?.getOrNull(0)
                        ?.jsonPrimitive?.contentOrNull ?: continue

                    // Timestamp z [4][0] (epoch seconds)
                    val timestamp = item.getOrNull(4)?.jsonArray?.getOrNull(0)
                        ?.jsonPrimitive?.longOrNull ?: 0L

                    // User prompt z [2][0][0]
                    val userText = item.getOrNull(2)?.jsonArray?.getOrNull(0)
                        ?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: ""

                    // Assistant response text z [3][0][0][1][0]
                    val assistantText = item.getOrNull(3)?.jsonArray?.getOrNull(0)
                        ?.jsonArray?.getOrNull(0)?.jsonArray?.getOrNull(1)
                        ?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: ""

                    // Title = user prompt (first ~60 chars)
                    val title = userText.take(80).ifEmpty { "Chat" }

                    chats.add(
                        GeminiChat(
                            id = id,
                            title = title,
                            lastMessage = assistantText.take(200),
                            timestamp = timestamp,
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "parseChats: skip item: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseChats: ${e.message}")
        }
        return chats
    }

    private fun parseTurns(data: JsonElement): List<GeminiTurn> {
        val turns = mutableListOf<GeminiTurn>()
        try {
            val outer = data.jsonArray
            if (outer.isEmpty()) return emptyList()
            val turnList = outer[0].jsonArray

            for (turnItem in turnList) {
                try {
                    val item = turnItem.jsonArray

                    // User prompt: [2][0][0]
                    val userPrompt = item.getOrNull(2)?.jsonArray?.getOrNull(0)
                        ?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: ""

                    // Assistant response: [3][0][0][1][0]
                    val response = item.getOrNull(3)?.jsonArray?.getOrNull(0)
                        ?.jsonArray?.getOrNull(0)?.jsonArray?.getOrNull(1)
                        ?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: ""

                    // Timestamp: [4][0]
                    val timestamp = item.getOrNull(4)?.jsonArray?.getOrNull(0)
                        ?.jsonPrimitive?.longOrNull ?: 0L

                    if (userPrompt.isNotEmpty() || response.isNotEmpty()) {
                        turns.add(GeminiTurn(userPrompt, response, timestamp))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "parseTurns: skip: ${e.message}")
                }
            }
            // Server vrací newest-first, chceme chronologicky
            turns.reverse()
        } catch (e: Exception) {
            Log.e(TAG, "parseTurns: ${e.message}")
        }
        return turns
    }

    // ── RPC infra ──

    private suspend fun rpcCall(rpcId: String, params: JsonArray): JsonElement? {
        val url = buildUrl(rpcId)
        val body = buildRequestBody(rpcId, params)

        Log.i(TAG, "rpcCall: rpcId=$rpcId, url=${url.take(100)}")

        val response = httpClient.post(url) {
            header("Cookie", cookies)
            header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            )
            header("X-Same-Domain", "1")
            header("Origin", "https://gemini.google.com")
            header("Referer", "https://gemini.google.com/")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(body)
        }

        val raw = response.bodyAsText()
        Log.i(TAG, "rpcCall: status=${response.status}, responseLen=${raw.length}")

        if (response.status != HttpStatusCode.OK) {
            throw RpcException("Gemini RPC $rpcId failed: ${response.status}")
        }

        return RpcDecoder.decode(raw, rpcId)
    }

    private fun buildUrl(rpcId: String): String {
        val params = mapOf(
            "rpcids" to rpcId,
            "source-path" to "/app",
            "f.sid" to sessionId,
            "rt" to "c",
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        return "$BATCHEXECUTE_URL?$query"
    }

    private fun buildRequestBody(rpcId: String, params: JsonArray): String {
        val paramsJson = json.encodeToString(JsonArray.serializer(), params)
        val inner = buildJsonArray {
            add(JsonPrimitive(rpcId))
            add(JsonPrimitive(paramsJson))
            add(JsonNull)
            add(JsonPrimitive("generic"))
        }
        val fReq = buildJsonArray {
            add(buildJsonArray { add(inner) })
        }
        val fReqJson = json.encodeToString(JsonArray.serializer(), fReq)
        val encodedReq = java.net.URLEncoder.encode(fReqJson, "UTF-8")
        val encodedToken = java.net.URLEncoder.encode(csrfToken, "UTF-8")
        return "f.req=$encodedReq&at=$encodedToken&"
    }
}
