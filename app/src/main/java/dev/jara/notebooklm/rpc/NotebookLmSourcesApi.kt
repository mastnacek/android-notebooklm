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

// ══════════════════════════════════════════════════════════════════════════════
// SOURCE DISCOVERY — hledani novych zdroju pres AI
// ══════════════════════════════════════════════════════════════════════════════

data class DiscoveredSource(val url: String, val title: String)

data class DiscoveryResult(
    val taskId: String,
    val status: String, // "in_progress" | "completed"
    val query: String,
    val sources: List<DiscoveredSource>,
    val summary: String,
)

/** Spusti fast research — AI najde relevantni zdroje pro query */
suspend fun NotebookLmApi.startDiscoverSources(notebookId: String, query: String): String? {
    val params = buildJsonArray {
        add(buildJsonArray {
            add(JsonPrimitive(query))
            add(JsonPrimitive(1)) // 1 = Web
        })
        add(JsonNull)
        add(JsonPrimitive(1))
        add(JsonPrimitive(notebookId))
    }
    val result = rpcCall(
        RpcMethod.DISCOVER_SOURCES_MANIFOLD, params,
        sourcePath = "/notebook/$notebookId"
    ) ?: return null

    // Response: [task_id, ...]
    return try {
        result.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull
            ?: result.jsonArray.getOrNull(0)?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull
    } catch (e: Exception) {
        Log.e(TAG, "startDiscoverSources: parse error: ${e.message}")
        Log.i(TAG, "startDiscoverSources raw: ${result.toString().take(500)}")
        null
    }
}

/** Polluje vysledky discovery — vraci aktualni stav */
suspend fun NotebookLmApi.pollDiscoverSources(notebookId: String): DiscoveryResult? {
    val params = buildJsonArray {
        add(JsonNull)
        add(JsonNull)
        add(JsonPrimitive(notebookId))
    }
    val result = rpcCall(
        RpcMethod.LIST_DISCOVER_SOURCES_JOB, params,
        sourcePath = "/notebook/$notebookId"
    ) ?: return null

    return try {
        Log.i(TAG, "pollDiscoverSources raw: ${result.toString().take(1000)}")
        parseDiscoveryResult(result)
    } catch (e: Exception) {
        Log.e(TAG, "pollDiscoverSources: parse error: ${e.message}")
        null
    }
}

private fun parseDiscoveryResult(data: JsonElement): DiscoveryResult? {
    // Odpoved je hluboko vnorena — hledame task list
    val outer = data.jsonArray
    // Muze byt [[[task_id, task_info]]] nebo [[task_id, task_info]]
    val taskList = outer.getOrNull(0)?.jsonArray ?: return null

    // Najdi prvni task
    val taskEntry = if (taskList.isNotEmpty() && taskList[0] is JsonArray) {
        taskList[0].jsonArray
    } else {
        taskList
    }

    val taskId = taskEntry.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return null
    val taskInfo = taskEntry.getOrNull(1)?.jsonArray ?: return null

    // taskInfo[1] = query info, taskInfo[3] = sources+summary, taskInfo[4] = status
    val query = try {
        taskInfo.getOrNull(1)?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: ""
    } catch (_: Exception) { "" }

    val statusCode = try {
        taskInfo.getOrNull(4)?.jsonPrimitive?.intOrNull ?: 1
    } catch (_: Exception) { 1 }
    val status = if (statusCode >= 2) "completed" else "in_progress"

    val sources = mutableListOf<DiscoveredSource>()
    val summary = try {
        val sourcesAndSummary = taskInfo.getOrNull(3)?.jsonArray
        val sourcesList = sourcesAndSummary?.getOrNull(0)?.jsonArray

        if (sourcesList != null) {
            for (src in sourcesList) {
                try {
                    val arr = src.jsonArray
                    val url = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: continue
                    val title = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: url
                    sources.add(DiscoveredSource(url, title))
                } catch (_: Exception) {}
            }
        }
        sourcesAndSummary?.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: ""
    } catch (_: Exception) { "" }

    return DiscoveryResult(taskId, status, query, sources, summary)
}

/** Importuje vybrane zdroje z discovery do notebooku */
suspend fun NotebookLmApi.importDiscoveredSources(
    notebookId: String,
    taskId: String,
    sources: List<DiscoveredSource>,
): Int {
    if (sources.isEmpty()) return 0

    val sourceArray = buildJsonArray {
        for (src in sources) {
            add(buildJsonArray {
                add(JsonNull); add(JsonNull)
                add(buildJsonArray {
                    add(JsonPrimitive(src.url))
                    add(JsonPrimitive(src.title))
                })
                add(JsonNull); add(JsonNull); add(JsonNull); add(JsonNull)
                add(JsonNull); add(JsonNull); add(JsonNull)
                add(JsonPrimitive(2))
            })
        }
    }

    val params = buildJsonArray {
        add(JsonNull)
        add(buildJsonArray { add(JsonPrimitive(1)) })
        add(JsonPrimitive(taskId))
        add(JsonPrimitive(notebookId))
        add(sourceArray)
    }

    val result = rpcCall(
        RpcMethod.FINISH_DISCOVER_SOURCES, params,
        sourcePath = "/notebook/$notebookId"
    )

    Log.i(TAG, "importDiscoveredSources: result=${result?.toString()?.take(500)}")
    return sources.size
}
