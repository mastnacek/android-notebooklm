package dev.jara.notebooklm.rpc

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decoder pro Google batchexecute RPC odpovedi.
 *
 * Odpoved ma format:
 * )]}'\n
 * pocet_bytu\n
 * json_chunk\n
 * pocet_bytu\n
 * json_chunk\n
 * ...
 */
object RpcDecoder {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val TAG = "RpcDecoder"

    fun decode(rawResponse: String, rpcId: String): JsonElement? {
        Log.i(TAG, "decode: rpcId=$rpcId, rawLen=${rawResponse.length}, preview=${rawResponse.take(200)}")
        val cleaned = stripAntiXssi(rawResponse)
        Log.i(TAG, "decode: cleanedLen=${cleaned.length}")
        val chunks = parseChunkedResponse(cleaned)
        Log.i(TAG, "decode: ${chunks.size} chunks parsed")
        val result = extractRpcResult(chunks, rpcId)
        Log.i(TAG, "decode: result=${result?.toString()?.take(200)}")
        return result
    }

    /** Odstrani anti-XSSI prefix )]}' */
    private fun stripAntiXssi(response: String): String {
        if (response.startsWith(")]}'")) {
            val idx = response.indexOf('\n')
            if (idx >= 0) return response.substring(idx + 1)
        }
        return response
    }

    /** Parsuje chunked response (rt=c mode) */
    private fun parseChunkedResponse(response: String): List<JsonArray> {
        if (response.isBlank()) return emptyList()

        val chunks = mutableListOf<JsonArray>()
        val lines = response.trim().split('\n')
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) { i++; continue }

            // Zkusime jestli je to pocet bytu
            val isNumber = line.toIntOrNull() != null
            if (isNumber) {
                i++
                if (i < lines.size) {
                    tryParseChunk(lines[i])?.let { chunks.add(it) }
                }
                i++
            } else {
                tryParseChunk(line)?.let { chunks.add(it) }
                i++
            }
        }
        return chunks
    }

    private fun tryParseChunk(jsonStr: String): JsonArray? {
        return try {
            json.parseToJsonElement(jsonStr).jsonArray
        } catch (_: Exception) {
            null
        }
    }

    /** Extrahuje vysledek pro dane RPC ID z chunks */
    private fun extractRpcResult(chunks: List<JsonArray>, rpcId: String): JsonElement? {
        for (chunk in chunks) {
            val items = if (chunk.isNotEmpty() && chunk[0] is JsonArray) {
                chunk.map { it.jsonArray }
            } else {
                listOf(chunk)
            }

            for (item in items) {
                if (item.size < 3) continue
                val type = item[0].jsonPrimitive.contentOrNull ?: continue
                val id = item[1].jsonPrimitive.contentOrNull ?: continue

                if (type == "wrb.fr" && id == rpcId) {
                    val resultData = item[2]
                    if (resultData is kotlinx.serialization.json.JsonNull) return null

                    // Vysledek je JSON string ktery musime znovu parsovat
                    val resultStr = resultData.jsonPrimitive.contentOrNull ?: return null
                    return try {
                        json.parseToJsonElement(resultStr)
                    } catch (_: Exception) {
                        resultData
                    }
                }

                // Error response
                if (type == "er" && id == rpcId) {
                    val errorCode = if (item.size > 2) item[2].toString() else "unknown"
                    throw RpcException("RPC error for $rpcId: code=$errorCode")
                }
            }
        }
        return null
    }
}

class RpcException(message: String) : Exception(message)
