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
            result.jsonArray
                .getOrNull(0)?.jsonArray
                ?.getOrNull(0)?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            Log.e(TAG, "getSummary parse: ${e.message}")
            null
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
