package dev.jara.notebooklm.rpc

import android.util.Log
import kotlinx.serialization.json.*

// ── Sources API — extension functions na NotebookLmApi ──

private const val TAG = NotebookLmApi.TAG

/** Rust: api::get_notebook_sources — RPC rLM1Ne */
suspend fun NotebookLmApi.getSources(notebookId: String): List<Source> {
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

/** Rust: api::delete_source — RPC tGMBJ */
suspend fun NotebookLmApi.deleteSource(notebookId: String, sourceId: String) {
    val params = buildJsonArray {
        add(buildJsonArray {
            add(buildJsonArray { add(JsonPrimitive(sourceId)) })
        })
    }
    rpcCall(RpcMethod.DELETE_SOURCE, params, sourcePath = "/notebook/$notebookId")
}

/** Rust: api::get_source_fulltext — RPC hizoJc
 * Vraci textovy obsah zdroje pro hash porovnani pri deduplikaci */
suspend fun NotebookLmApi.getSourceFulltext(notebookId: String, sourceId: String): String {
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
internal fun extractAllText(value: JsonElement, depth: Int = 50): String {
    if (depth <= 0) return ""
    return when {
        value is JsonPrimitive && value.isString -> value.content
        value is JsonArray -> value.joinToString("\n") { extractAllText(it, depth - 1) }
        else -> ""
    }
}

/** Prida URL zdroj do notebooku */
suspend fun NotebookLmApi.addSourceUrl(notebookId: String, url: String): String? {
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
suspend fun NotebookLmApi.addSourceText(notebookId: String, title: String, content: String): String? {
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
suspend fun NotebookLmApi.addSourceYoutube(notebookId: String, url: String): String? {
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
