package dev.jara.notebooklm.ui

import android.util.Log
import androidx.lifecycle.viewModelScope
import dev.jara.notebooklm.rpc.NotebookLmApi
import dev.jara.notebooklm.rpc.SourceType
import dev.jara.notebooklm.rpc.deleteSource
import dev.jara.notebooklm.rpc.getSources
import dev.jara.notebooklm.rpc.getSourceFulltext
import dev.jara.notebooklm.search.OpenRouterEmbedding
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

private const val TAG = "AppViewModel"

/** Spusti deduplikaci vsech notebooku — scanuje zdroje a maze duplikaty */
fun AppViewModel.startDeduplication() {
    val tokens = authManager.loadTokens() ?: return
    val nbs = _notebooks.value.toList()
    if (nbs.isEmpty()) return

    _dedup.value = DeduplicationState(running = true)

    viewModelScope.launch {
        val api = NotebookLmApi(httpClient, tokens)
        var totalDeleted = 0

        for ((idx, nb) in nbs.withIndex()) {
            _dedup.value = _dedup.value.copy(
                currentNotebook = nb.title,
                progress = "${idx + 1}/${nbs.size}",
                groups = emptyList(),
            )

            try {
                val sources = api.getSources(nb.id)
                if (sources.size < 2) continue

                // Seskup podle nazvu
                val byTitle = sources.groupBy { it.title }
                val groups = mutableListOf<DuplicateGroup>()

                for ((title, group) in byTitle) {
                    if (group.size <= 1) continue

                    val allText = group.all { it.type == SourceType.TEXT }
                    if (allText) {
                        // Content-aware deduplikace — hash fulltext obsahu
                        val hashGroups = mutableMapOf<String, MutableList<String>>()
                        for (src in group) {
                            val hash = try {
                                val content = api.getSourceFulltext(nb.id, src.id)
                                content.hashCode().toString(16)
                            } catch (_: Exception) {
                                "unique_${src.id}"
                            }
                            hashGroups.getOrPut(hash) { mutableListOf() }.add(src.id)
                        }
                        for ((_, ids) in hashGroups) {
                            if (ids.size > 1) {
                                groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                            }
                        }
                    } else {
                        // Non-text — deduplikace jen podle nazvu
                        val ids = group.map { it.id }
                        groups.add(DuplicateGroup(title, ids.size, ids.drop(1)))
                    }
                }

                if (groups.isEmpty()) continue

                groups.sortByDescending { it.count }
                _dedup.value = _dedup.value.copy(groups = groups)

                // Smaz duplikaty
                for (g in groups) {
                    for (srcId in g.deleteIds) {
                        try {
                            api.deleteSource(nb.id, srcId)
                            totalDeleted++
                            _dedup.value = _dedup.value.copy(totalDeleted = totalDeleted)
                        } catch (e: Exception) {
                            Log.w(TAG, "dedup delete: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "dedup scan ${nb.title}: ${e.message}")
            }
        }

        _dedup.value = DeduplicationState(
            done = true,
            totalDeleted = totalDeleted,
            progress = "hotovo",
        )
    }
}

/** Davkova klasifikace notebooku pres OpenRouter LLM (ids=null -> vsechny) */
fun AppViewModel.startClassification(ids: Set<String>? = null) {
    val apiKey = getApiKey()
    if (apiKey.isBlank()) {
        _error.value = "Nastav OpenRouter API klic v nastaveni"
        return
    }
    val allNbs = _notebooks.value.toList()
    val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
    if (nbs.isEmpty()) return

    val model = getClassifyModel()
    _classify.value = ClassificationState(running = true)

    viewModelScope.launch {
        try {
            // Aktualizovany set kategorii — roste s kazdym batchem
            val knownCats = _categories.value.values.distinct().toMutableSet()
            val allResults = mutableMapOf<String, String>()

            // Zpracuj po batchich po 10
            val batches = nbs.chunked(10)
            for ((batchIdx, batch) in batches.withIndex()) {
                _classify.value = _classify.value.copy(
                    progress = "batch ${batchIdx + 1}/${batches.size}",
                )

                val nbLines = batch.mapIndexed { i, nb ->
                    val prefix = if (nb.emoji.isNotEmpty()) "${nb.emoji} " else ""
                    "${i + 1}. [${nb.id}] $prefix${nb.title}"
                }.joinToString("\n")

                val sortedCats = knownCats.sorted()
                val catsContext = if (sortedCats.isEmpty())
                    "Zatim zadne kategorie neexistuji — vytvor nove."
                else
                    "Existujici kategorie (MUSIS pouzit PRESNE tyto nazvy, zadne varianty): ${sortedCats.joinToString(", ")}"

                val prompt = """
Jsi organizacni asistent. Prirad kazdemu notebooku jednu kategorii.

PRAVIDLA:
1. Kategorie je MAXIMALNE 2 slova, cesky, strucne, lowercase (prvni pismeno velke)
2. Kategorie MUSI byt OBECNE — zastresujici temata, ne konkretni nastroje nebo produkty
   SPATNE: "Claude Code", "Gemini agent", "React hooks", "Python scripty"
   SPRAVNE: "Programovani", "Ai nastroje", "Webovy vyvoj", "Automatizace"
3. KONZISTENCE: Pokud dva notebooky patri do stejne oblasti, MUSI mit STEJNOU kategorii
   Napr. notebook o Claude a notebook o Gemini → oba "Ai nastroje", NE dve ruzne kategorie
4. MUSIS pouzit existujici kategorie pokud jen trochu sedi — novou vytvor JEN kdyz ZADNA existujici nesedi
5. Cil je 5-15 kategorii celkem pro VSECHNY notebooky, ne unikatni kategorie pro kazdy
6. Mysli v urovni "police v knihovne" — obecne tema, ne konkretni kniha

$catsContext

NOTEBOOKY:
$nbLines

Odpovez POUZE platnym JSON polem — zadny jiny text:
[{"id": "notebook_id", "category": "kategorie"}]
""".trim()

                val response = httpClient.post("https://openrouter.ai/api/v1/chat/completions") {
                    header("Authorization", "Bearer $apiKey")
                    header("Content-Type", "application/json")
                    setBody(buildJsonObject {
                        put("model", JsonPrimitive(model))
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", JsonPrimitive("user"))
                                put("content", JsonPrimitive(prompt))
                            }
                        }
                        put("max_tokens", JsonPrimitive(1000))
                        put("temperature", JsonPrimitive(0.3))
                    }.toString())
                }

                val body = response.bodyAsText()
                val json = Json.parseToJsonElement(body)
                val content = json.jsonObject["choices"]?.jsonArray
                    ?.getOrNull(0)?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull ?: continue

                // Parsuj JSON — muze byt obalene v ```json ... ```
                val clean = content.trim()
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()

                try {
                    val arr = Json.parseToJsonElement(clean).jsonArray
                    for (item in arr) {
                        val id = item.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: continue
                        val cat = item.jsonObject["category"]?.jsonPrimitive?.contentOrNull ?: continue
                        // Normalizuj — lowercase + first uppercase
                        val normalized = cat.trim().lowercase().replaceFirstChar { it.uppercase() }
                        allResults[id] = normalized
                        catPrefs.edit().putString(id, normalized).apply()
                        // Pridej do znamych kategorii pro dalsi batche
                        knownCats.add(normalized)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "classify parse: ${e.message}, raw=$clean")
                }

                _classify.value = _classify.value.copy(results = allResults.toMap())
            }

            _categories.value = catPrefs.all.mapNotNull { (k, v) ->
                if (v is String) k to v else null
            }.toMap()

            _classify.value = ClassificationState(
                done = true,
                results = allResults,
                progress = "hotovo — ${allResults.size} klasifikovano",
            )
        } catch (e: Exception) {
            Log.e(TAG, "classify", e)
            _classify.value = ClassificationState(error = e.message)
        }
    }
}

/** Embeddne vybrane notebooky (nebo vsechny, kdyz ids je null) */
fun AppViewModel.embedNotebooks(ids: Set<String>? = null) {
    val apiKey = getApiKey()
    if (apiKey.isBlank()) {
        _error.value = "Nastav OpenRouter API klic v nastaveni"
        return
    }
    val allNbs = _notebooks.value
    val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
    if (nbs.isEmpty()) return

    val tokens = authManager.loadTokens() ?: return
    _embeddingStatus.value = "Stahuji popisy ${nbs.size} sesitu..."
    viewModelScope.launch {
        try {
            val api = NotebookLmApi(httpClient, tokens)
            val embedding = OpenRouterEmbedding(httpClient, apiKey)

            // Stahni summary pro kazdy notebook (jako Rust impl)
            val descriptions = mutableMapOf<String, String>()
            for ((idx, nb) in nbs.withIndex()) {
                _embeddingStatus.value = "Popis ${idx + 1}/${nbs.size}: ${nb.title.take(20)}..."
                try {
                    val summary = api.getSummary(nb.id)
                    if (summary != null) descriptions[nb.id] = summary
                } catch (e: Exception) {
                    Log.w(TAG, "getSummary failed for ${nb.id}: ${e.message}")
                }
            }

            withContext(Dispatchers.IO) {
                // Text pro embedding = title + summary (jako Rust)
                val toEmbed = nbs.filter { nb ->
                    val desc = descriptions[nb.id] ?: ""
                    val text = if (desc.isEmpty()) nb.title else "${nb.title} $desc"
                    embeddingDb.needsUpdate(nb.id, text)
                }

                if (toEmbed.isEmpty()) {
                    _embeddingStatus.value = null
                    return@withContext
                }

                _embeddingStatus.value = "Embedduji ${toEmbed.size} sesitu..."

                // Batch po 20
                for (chunk in toEmbed.chunked(20)) {
                    val texts = chunk.map { nb ->
                        val desc = descriptions[nb.id] ?: ""
                        if (desc.isEmpty()) nb.title else "${nb.title} $desc"
                    }
                    val embeddings = embedding.embed(texts)
                    for ((i, nb) in chunk.withIndex()) {
                        val desc = descriptions[nb.id] ?: ""
                        embeddingDb.upsertEmbedding(nb.id, nb.title, desc, embeddings[i])
                    }
                }

                // Prune smazane
                embeddingDb.pruneDeleted(nbs.map { it.id }.toSet())
            }
            _embeddingStatus.value = null
            Log.i(TAG, "embedNotebooks: done, ${descriptions.size} popisů")
        } catch (e: Exception) {
            Log.e(TAG, "embedNotebooks", e)
            _embeddingStatus.value = null
            _error.value = "Embedding chyba: ${e.message}"
        }
    }
}

/** Semanticke vyhledavani — embeddne query a KNN */
fun AppViewModel.semanticSearch(query: String) {
    val apiKey = getApiKey()
    if (apiKey.isBlank()) {
        _error.value = "Nastav OpenRouter API klic"
        return
    }
    if (query.isBlank()) {
        _semanticResults.value = null
        return
    }

    _searchLoading.value = true
    viewModelScope.launch {
        try {
            val embedding = OpenRouterEmbedding(httpClient, apiKey)
            val queryEmb = embedding.embedSingle(query)
            Log.i(TAG, "semanticSearch: queryEmb dim=${queryEmb.size}, first3=${queryEmb.take(3)}")
            val results = withContext(Dispatchers.IO) {
                val count = embeddingDb.count()
                Log.i(TAG, "semanticSearch: DB ma $count embeddingu")
                embeddingDb.search(queryEmb, limit = 20)
            }
            Log.i(TAG, "semanticSearch: vraci ${results.size} IDs")
            _semanticResults.value = results.map { it.first }
            Log.i(TAG, "semanticSearch: ${results.size} vysledku")
        } catch (e: Exception) {
            Log.e(TAG, "semanticSearch", e)
            _error.value = "Search chyba: ${e.message}"
        } finally {
            _searchLoading.value = false
        }
    }
}
