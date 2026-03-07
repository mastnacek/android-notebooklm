package dev.jara.notebooklm.search

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * OpenRouter embedding klient.
 * Model: qwen/qwen3-embedding-8b (512 dim, $0.01/M tokenu)
 * Presna replika Rust implementace z shared/mod.rs
 */
class OpenRouterEmbedding(
    private val httpClient: HttpClient,
    private val apiKey: String,
) {
    companion object {
        private const val TAG = "OpenRouterEmbedding"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/embeddings"
        private const val MODEL = "qwen/qwen3-embedding-8b"
        private const val DIMENSIONS = 512
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Embeddne seznam textu. Vraci FloatArray pro kazdy text.
     * Batch API — posila vsechny najednou.
     */
    suspend fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val requestBody = buildJsonObject {
            put("model", MODEL)
            put("dimensions", DIMENSIONS)
            putJsonArray("input") {
                for (t in texts) add(t)
            }
        }

        Log.i(TAG, "embed: ${texts.size} textu, model=$MODEL, dim=$DIMENSIONS")

        val response = httpClient.post(ENDPOINT) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseText = response.bodyAsText()
        Log.i(TAG, "embed: HTTP ${response.status.value}, size=${responseText.length}")

        if (response.status.value != 200) {
            throw RuntimeException("OpenRouter embedding chyba ${response.status.value}: ${responseText.take(200)}")
        }

        val parsed = json.parseToJsonElement(responseText).jsonObject
        val data = parsed["data"]?.jsonArray
            ?: throw RuntimeException("OpenRouter: chybi 'data' v odpovedi")

        // Seradit podle indexu (API muze vratit v jinem poradi)
        val sorted = data.sortedBy {
            it.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: 0
        }

        return sorted.map { item ->
            val embArr = item.jsonObject["embedding"]?.jsonArray
                ?: throw RuntimeException("Chybi embedding v odpovedi")
            FloatArray(embArr.size) { i ->
                embArr[i].jsonPrimitive.float
            }
        }
    }

    /** Embeddne jeden text */
    suspend fun embedSingle(text: String): FloatArray {
        return embed(listOf(text)).firstOrNull()
            ?: throw RuntimeException("Prazdna embedding odpoved")
    }
}
