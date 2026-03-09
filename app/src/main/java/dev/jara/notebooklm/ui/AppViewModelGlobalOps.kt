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

        for (nb in nbs) {
            withContext(Dispatchers.IO) { embeddingDb.markDedupDone(nb.id) }
        }
        refreshIndicators()
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
            // Aktualizovane sety facet — rostou s kazdym batchem
            val knownTopics = embeddingDb.getDistinctFacetValues("topic").toMutableSet()
            val knownFormats = embeddingDb.getDistinctFacetValues("format").toMutableSet()
            val knownPurposes = embeddingDb.getDistinctFacetValues("purpose").toMutableSet()
            val knownDomains = embeddingDb.getDistinctFacetValues("domain").toMutableSet()
            val knownFreshness = embeddingDb.getDistinctFacetValues("freshness").toMutableSet()
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

                val prompt = """
Jsi organizacni asistent. Prirad kazdemu notebooku 5 facet (PMEST model).

FACETY:
1. topic — Obecne tema (max 2 slova, cesky): Programovani, Ai nastroje, Finance, Design...
2. format — Typ obsahu: Tutorial, Reference, Poznamky, Vyzkum, Projekt, Clanek, Kurz
3. purpose — Ucel: Uceni, Projekt, Archiv, Inspirace, Prace, Osobni
4. domain — Konkretni oblast/technologie: Android, Python, Web, Hardware, Obecne...
5. freshness — Aktuálnost: Aktivni, Archivni, Sezonni

PRAVIDLA:
1. Hodnoty MAXIMALNE 2 slova, cesky, lowercase (prvni pismeno velke)
2. KONZISTENCE — pouzij existujici hodnoty pokud sedi:
   topic: ${knownTopics.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   format: ${knownFormats.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   purpose: ${knownPurposes.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   domain: ${knownDomains.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   freshness: ${knownFreshness.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
3. Novou hodnotu vytvor JEN kdyz ZADNA existujici nesedi
4. Cil je 5-15 hodnot per facet, ne unikatni pro kazdy notebook

NOTEBOOKY:
$nbLines

Odpovez POUZE platnym JSON polem:
[{"id": "notebook_id", "topic": "...", "format": "...", "purpose": "...", "domain": "...", "freshness": "..."}]
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
                        val obj = item.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                        val normalize = { s: String? -> s?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "" }
                        val facets = NotebookFacets(
                            topic = normalize(obj["topic"]?.jsonPrimitive?.contentOrNull),
                            format = normalize(obj["format"]?.jsonPrimitive?.contentOrNull),
                            purpose = normalize(obj["purpose"]?.jsonPrimitive?.contentOrNull),
                            domain = normalize(obj["domain"]?.jsonPrimitive?.contentOrNull),
                            freshness = normalize(obj["freshness"]?.jsonPrimitive?.contentOrNull),
                        )
                        embeddingDb.upsertFacets(id, facets)
                        allResults[id] = facets.topic
                        // Pridej do znamych hodnot pro dalsi batche
                        if (facets.topic.isNotEmpty()) knownTopics.add(facets.topic)
                        if (facets.format.isNotEmpty()) knownFormats.add(facets.format)
                        if (facets.purpose.isNotEmpty()) knownPurposes.add(facets.purpose)
                        if (facets.domain.isNotEmpty()) knownDomains.add(facets.domain)
                        if (facets.freshness.isNotEmpty()) knownFreshness.add(facets.freshness)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "classify parse: ${e.message}, raw=$clean")
                }

                _classify.value = _classify.value.copy(results = allResults.toMap())
            }

            // Aktualizuj facety a zpetne kompatibilni kategorie
            _facets.value = embeddingDb.getAllFacets()
            _categories.value = _facets.value.mapValues { it.value.topic }.filterValues { it.isNotEmpty() }
            refreshIndicators()

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
            refreshIndicators()
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

/** Batch sken zdrojů — stáhne zdroje, zahashuje obsah, uloží do DB */
fun AppViewModel.scanSources(ids: Set<String>? = null) {
    val tokens = authManager.loadTokens() ?: return
    val allNbs = _notebooks.value.toList()
    val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
    if (nbs.isEmpty()) return

    _sourceScan.value = SourceScanState(running = true)

    viewModelScope.launch {
        val api = NotebookLmApi(httpClient, tokens)

        for ((idx, nb) in nbs.withIndex()) {
            _sourceScan.value = _sourceScan.value.copy(
                currentNotebook = nb.title,
                progress = "${idx + 1}/${nbs.size}",
            )

            try {
                val sources = api.getSources(nb.id)
                val records = mutableListOf<SourceRecord>()

                for (src in sources) {
                    val hash = try {
                        when (src.type) {
                            SourceType.TEXT -> {
                                val content = api.getSourceFulltext(nb.id, src.id)
                                sha256(content)
                            }
                            SourceType.PDF -> {
                                val content = api.getSourceFulltext(nb.id, src.id)
                                val firstPage = content.split('\u000C').firstOrNull()
                                    ?: content.take(3000)
                                sha256("${src.title}\n$firstPage")
                            }
                            else -> sha256(src.title)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "hash ${src.title}: ${e.message}")
                        sha256(src.title)
                    }

                    records.add(SourceRecord(
                        sourceId = src.id,
                        title = src.title,
                        type = src.type.name,
                        contentHash = hash,
                    ))
                }

                withContext(Dispatchers.IO) {
                    embeddingDb.upsertSources(nb.id, records)
                    embeddingDb.markSourcesScanned(nb.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "scanSources ${nb.title}: ${e.message}")
            }
        }

        refreshIndicators()
        _sourceScan.value = SourceScanState(done = true, progress = "hotovo")
    }
}

private fun sha256(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}
